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
 * Adds the annotations implied by a phenopacket cohort to an HPOA file: one line per disease and
 * observed term, with the frequency pooled over all cohort publications (ancestor-aware counting)
 * and every contributing PMID in the reference field. Existing annotations of the same disease are
 * superseded when they cite a publication the cohort re-curates, pooled into the cohort line when
 * they carry a poolable count, and kept otherwise.
 */
public class HpoaAugmenter {

    public static final String PROVENANCE_HEADER = "#cohort-augmented: ";

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
        String provenance = PROVENANCE_HEADER + diseases.stream()
                .map(TermId::getValue)
                .collect(Collectors.joining(","));
        return new AugmentedHpoa(new HpoaFile(base.headerLines(), lines).withHeaderLine(provenance), report);
    }

    private DiseaseAugmentation augmentDisease(TermId diseaseId,
                                               List<HpoaAnnotationLine> existing,
                                               List<HpoaAnnotationLine> generated,
                                               List<HpoaAnnotationLine> retained) {
        Set<TermId> cohortPmids = cohort.pmidsOf(diseaseId);
        CohortCounts counts = cohort.countsOf(diseaseId);
        String diseaseName = existing.stream()
                .map(HpoaAnnotationLine::diseaseName)
                .filter(name -> !name.isEmpty())
                .findFirst()
                .orElseGet(() -> cohort.diseaseNameOf(diseaseId));

        Map<TermId, HpoaAnnotationLine> poolable = new LinkedHashMap<>();
        int superseded = 0;
        for (HpoaAnnotationLine line : existing) {
            if (!Collections.disjoint(line.references(), cohortPmids)) {
                if (!cohortPmids.containsAll(line.references())) {
                    LOGGER.warn("{} {}: dropping annotation with mixed provenance {}",
                            diseaseId, line.hpoId(), line.references());
                }
                superseded++;
            } else if (line.poolableRatio().isPresent() && !poolable.containsKey(line.hpoId())) {
                poolable.put(line.hpoId(), line);
            } else {
                retained.add(line);
            }
        }

        int added = 0;
        int pooled = 0;
        for (TermId hpoId : cohort.observedTermsOf(diseaseId)) {
            Ratio frequency = counts.ratioOf(hpoId);
            Set<TermId> references = new LinkedHashSet<>(cohortPmids);
            HpoaAnnotationLine existingLine = poolable.remove(hpoId);
            if (existingLine != null) {
                frequency = frequency.plus(existingLine.poolableRatio().orElseThrow());
                references.addAll(existingLine.references());
                pooled++;
            } else {
                added++;
            }
            generated.add(HpoaAnnotationLine.phenotypeAnnotation(diseaseId, diseaseName, hpoId,
                    references, frequency, biocuration));
        }
        retained.addAll(poolable.values());

        LOGGER.info("{}: {} cohort phenopackets from {} publications, {} annotations added, {} pooled, {} superseded",
                diseaseId, counts.cohortSize(), cohortPmids.size(), added, pooled, superseded);
        return new DiseaseAugmentation(diseaseId, counts.cohortSize(), List.copyOf(cohortPmids),
                added, pooled, superseded);
    }
}
