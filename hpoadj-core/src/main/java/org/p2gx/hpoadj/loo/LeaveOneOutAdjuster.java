package org.p2gx.hpoadj.loo;

import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.HpoadjException;
import org.p2gx.hpoadj.cohort.CohortCounts;
import org.p2gx.hpoadj.cohort.CohortSource;
import org.p2gx.hpoadj.cohort.PhenopacketCase;
import org.p2gx.hpoadj.hpoa.HpoaAnnotationLine;
import org.p2gx.hpoadj.hpoa.HpoaFile;
import org.p2gx.hpoadj.hpoa.Ratio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Removes the contribution of one publication from an HPOA file; one adjustment is computed per publication. */
public class LeaveOneOutAdjuster {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaveOneOutAdjuster.class);

    private final HpoaFile hpoa;
    private final CohortSource cohortSource;
    private final MultiReferencePolicy policy;
    private final Map<TermId, PublicationAdjustment> adjustments = new HashMap<>();

    public LeaveOneOutAdjuster(HpoaFile hpoa) {
        this(hpoa, CohortSource.none(), MultiReferencePolicy.SUBTRACT);
    }

    public LeaveOneOutAdjuster(HpoaFile hpoa, CohortSource cohortSource) {
        this(hpoa, cohortSource, MultiReferencePolicy.SUBTRACT);
    }

    public LeaveOneOutAdjuster(HpoaFile hpoa, CohortSource cohortSource, MultiReferencePolicy policy) {
        this.hpoa = hpoa;
        this.cohortSource = cohortSource;
        this.policy = policy;
    }

    public PublicationAdjustment adjustFor(TermId pmid) {
        return adjustments.computeIfAbsent(pmid, this::adjust);
    }

    /** One adjusted HPOA per publication cited by the cases, written to the output directory. */
    public List<CaseResult> adjustAll(List<PhenopacketCase> cases, Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);
        } catch (IOException e) {
            throw new HpoadjException("Could not create output directory " + outputDirectory, e);
        }
        Map<TermId, List<PhenopacketCase>> casesByPmid = cases.stream()
                .collect(Collectors.groupingBy(PhenopacketCase::pmid, LinkedHashMap::new, Collectors.toList()));
        List<CaseResult> results = new ArrayList<>();
        for (Map.Entry<TermId, List<PhenopacketCase>> entry : casesByPmid.entrySet()) {
            TermId pmid = entry.getKey();
            PublicationAdjustment adjustment = adjustFor(pmid);
            Optional<Path> outputFile = Optional.empty();
            if (adjustment.changesHpoa()) {
                Path path = outputDirectory.resolve(adjustment.fileName());
                adjustment.adjusted().write(path);
                outputFile = Optional.of(path);
                LOGGER.info("{}: dropped {} sole-support and {} undecomposable lines, subtracted cohort counts from {} lines, wrote {}",
                        pmid, adjustment.soleSupportLines().size(), adjustment.undecomposableLines().size(),
                        adjustment.subtractedLines().size(), path);
            } else {
                LOGGER.info("{} is not cited in the HPOA, no adjusted file needed", pmid);
            }
            for (PhenopacketCase phenopacketCase : entry.getValue()) {
                results.add(evaluateCase(phenopacketCase, adjustment, outputFile));
            }
        }
        return results;
    }

    private PublicationAdjustment adjust(TermId pmid) {
        List<HpoaAnnotationLine> retained = new ArrayList<>();
        List<HpoaAnnotationLine> soleSupport = new ArrayList<>();
        List<HpoaAnnotationLine> undecomposable = new ArrayList<>();
        List<HpoaAnnotationLine> subtracted = new ArrayList<>();
        for (HpoaAnnotationLine line : hpoa.annotationLines()) {
            if (!line.cites(pmid)) {
                retained.add(line);
            } else if (!line.hasMultipleReferences()) {
                soleSupport.add(line);
            } else if (policy == MultiReferencePolicy.KEEP) {
                retained.add(line);
            } else if (policy == MultiReferencePolicy.DROP) {
                undecomposable.add(line);
            } else {
                Optional<Ratio> remaining = subtractCohort(line, pmid);
                if (remaining.isEmpty()) {
                    undecomposable.add(line);
                } else if (remaining.get().isZero()) {
                    soleSupport.add(line);
                } else {
                    retained.add(line.withoutReference(pmid, remaining.get()));
                    subtracted.add(line);
                }
            }
        }
        return new PublicationAdjustment(pmid, new HpoaFile(hpoa.headerLines(), retained),
                soleSupport, undecomposable, subtracted);
    }

    private Optional<Ratio> subtractCohort(HpoaAnnotationLine line, TermId pmid) {
        Optional<String> pooledBiocuration = hpoa.cohortBiocuration();
        if (pooledBiocuration.isEmpty() || !pooledBiocuration.get().equals(line.biocuration())) {
            return Optional.empty();
        }
        Optional<Ratio> frequency = line.frequencyRatio();
        Optional<CohortCounts> counts = cohortSource.lookup(line.diseaseId(), pmid);
        if (frequency.isEmpty() || counts.isEmpty()) {
            return Optional.empty();
        }
        Ratio remaining = frequency.get().minus(counts.get().ratioOf(line.hpoId()));
        if (!remaining.isConsistent()) {
            LOGGER.warn("{} {}: cohort counts of {} do not fit the pooled frequency {}, dropping the line",
                    line.diseaseId(), line.hpoId(), pmid, line.frequency());
            return Optional.empty();
        }
        return Optional.of(remaining);
    }

    private static CaseResult evaluateCase(PhenopacketCase phenopacketCase,
                                           PublicationAdjustment adjustment,
                                           Optional<Path> outputFile) {
        TermId diseaseId = phenopacketCase.diseaseId();
        List<HpoaAnnotationLine> removed = adjustment.removedLines();
        int removedForDisease = countForDisease(removed, diseaseId);
        int subtractedForDisease = countForDisease(adjustment.subtractedLines(), diseaseId);
        long remainingPhenotypeLines = adjustment.adjusted().phenotypeLineCount(diseaseId);
        CaseStatus status;
        if (removedForDisease == 0 && subtractedForDisease == 0) {
            status = CaseStatus.NO_PMID_EVIDENCE;
        } else if (remainingPhenotypeLines == 0) {
            status = CaseStatus.INSUFFICIENT_DATA;
            LOGGER.warn("{}: no phenotype annotations left for {} after removing {}",
                    phenopacketCase.phenopacketId(), diseaseId, phenopacketCase.pmid());
        } else {
            status = CaseStatus.ADJUSTED;
        }
        return new CaseResult(phenopacketCase, status, removedForDisease, subtractedForDisease,
                removed.size(), adjustment.subtractedLines().size(),
                adjustment.soleSupportLines().size(), adjustment.undecomposableLines().size(),
                remainingPhenotypeLines, outputFile);
    }

    private static int countForDisease(List<HpoaAnnotationLine> lines, TermId diseaseId) {
        return (int) lines.stream()
                .filter(line -> line.diseaseId().equals(diseaseId))
                .count();
    }
}
