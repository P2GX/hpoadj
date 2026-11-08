package org.p2gx.hpoadj.augment;

import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.cohort.CohortCounts;
import org.p2gx.hpoadj.cohort.PhenopacketCohort;
import org.p2gx.hpoadj.hpoa.HpoaAnnotationLine;
import org.p2gx.hpoadj.hpoa.HpoaFile;
import org.p2gx.hpoadj.hpoa.Ratio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adds one phenotype annotation per disease and observed term of a phenopacket cohort to an HPOA
 * file, superseding or pooling the pre-existing phenotype annotations of those diseases.
 */
public class HpoaAugmenter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HpoaAugmenter.class);

    private final PhenopacketCohort cohort;
    private final String biocuration;

    public HpoaAugmenter(PhenopacketCohort cohort, String biocuration) {
        this.cohort = cohort;
        this.biocuration = biocuration;
    }

    public AugmentedHpoa augment(HpoaFile base) {
        List<TermId> diseases = cohort.diseases();
        Set<TermId> augmentedDiseases = new LinkedHashSet<>(diseases);
        List<HpoaAnnotationLine> retained = new ArrayList<>();
        Map<TermId, List<HpoaAnnotationLine>> existingByDisease = new LinkedHashMap<>();
        for (HpoaAnnotationLine line : base.annotationLines()) {
            if (augmentedDiseases.contains(line.diseaseId()) && line.isPhenotypeAnnotation()) {
                existingByDisease.computeIfAbsent(line.diseaseId(), id -> new ArrayList<>()).add(line);
            } else {
                retained.add(line);
            }
        }

        List<DiseaseAugmentation> report = new ArrayList<>();
        List<HpoaAnnotationLine> generated = new ArrayList<>();
        for (TermId diseaseId : diseases) {
            List<HpoaAnnotationLine> existing = existingByDisease.getOrDefault(diseaseId, List.of());
            report.add(augmentDisease(diseaseId, existing, generated, retained));
        }

        List<HpoaAnnotationLine> lines = new ArrayList<>(retained);
        lines.addAll(generated);
        String augmented = HpoaFile.AUGMENTED_DISEASES_HEADER + diseases.stream()
                .map(TermId::getValue)
                .collect(Collectors.joining(","));
        HpoaFile hpoa = new HpoaFile(base.headerLines(), lines)
                .withHeaderLine(HpoaFile.COHORT_BIOCURATION_HEADER + biocuration)
                .withHeaderLine(augmented);
        return new AugmentedHpoa(hpoa, report);
    }

    private DiseaseAugmentation augmentDisease(TermId diseaseId,
                                               List<HpoaAnnotationLine> existing,
                                               List<HpoaAnnotationLine> generated,
                                               List<HpoaAnnotationLine> retained) {
        Set<TermId> cohortPmids = cohort.pmidsOf(diseaseId);
        Set<TermId> observed = cohort.observedTermsOf(diseaseId);
        CohortCounts counts = cohort.countsOf(diseaseId);
        String diseaseName = existing.stream()
                .map(HpoaAnnotationLine::diseaseName)
                .filter(name -> !name.isEmpty())
                .findFirst()
                .orElseGet(() -> cohort.diseaseNameOf(diseaseId));

        Map<TermId, List<HpoaAnnotationLine>> poolable = new LinkedHashMap<>();
        int superseded = 0;
        int retainedBesideCohort = 0;
        for (HpoaAnnotationLine line : existing) {
            if (!Collections.disjoint(line.references(), cohortPmids)) {
                if (!cohortPmids.containsAll(line.references())) {
                    LOGGER.warn("{} {}: superseding annotation with mixed provenance {}",
                            diseaseId, line.hpoId(), line.references());
                }
                superseded++;
            } else if (!observed.contains(line.hpoId())) {
                retained.add(line);
            } else if (line.poolableRatio().isPresent()) {
                poolable.computeIfAbsent(line.hpoId(), id -> new ArrayList<>()).add(line);
            } else {
                LOGGER.warn("{} {}: keeping {} annotation '{}' from {} beside the cohort annotation",
                        diseaseId, line.hpoId(), line.negated() ? "negated" : "non-count",
                        line.frequency(), line.references());
                retained.add(line);
                retainedBesideCohort++;
            }
        }

        int added = 0;
        int pooled = 0;
        for (TermId hpoId : observed) {
            Ratio frequency = counts.ratioOf(hpoId);
            Set<TermId> references = new LinkedHashSet<>(cohortPmids);
            List<HpoaAnnotationLine> existingLines = poolable.getOrDefault(hpoId, List.of());
            for (HpoaAnnotationLine existingLine : existingLines) {
                frequency = frequency.plus(existingLine.poolableRatio().orElseThrow());
                references.addAll(existingLine.references());
            }
            if (existingLines.isEmpty()) {
                added++;
            } else {
                pooled += existingLines.size();
            }
            generated.add(HpoaAnnotationLine.phenotypeAnnotation(diseaseId, diseaseName, hpoId,
                    references, frequency, biocuration));
        }

        LOGGER.info("{}: {} cohort phenopackets from {} publications, {} annotations added, {} pooled, "
                        + "{} superseded, {} retained beside cohort annotations",
                diseaseId, counts.cohortSize(), cohortPmids.size(), added, pooled, superseded, retainedBesideCohort);
        return new DiseaseAugmentation(diseaseId, counts.cohortSize(), List.copyOf(cohortPmids),
                added, pooled, superseded, retainedBesideCohort);
    }
}
