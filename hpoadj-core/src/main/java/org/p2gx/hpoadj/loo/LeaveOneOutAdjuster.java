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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Removes the contribution of one publication from an HPOA file. A line that cites only that
 * publication is dropped; a line with several references has the publication's cohort counts
 * subtracted when they are known and the frequency stays informative, and is dropped otherwise.
 */
public class LeaveOneOutAdjuster {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaveOneOutAdjuster.class);

    private final HpoaFile hpoa;
    private final CohortSource cohortSource;

    public LeaveOneOutAdjuster(HpoaFile hpoa) {
        this(hpoa, CohortSource.none());
    }

    public LeaveOneOutAdjuster(HpoaFile hpoa, CohortSource cohortSource) {
        this.hpoa = hpoa;
        this.cohortSource = cohortSource;
    }

    public PublicationAdjustment adjustFor(TermId pmid) {
        List<HpoaAnnotationLine> retained = new ArrayList<>();
        List<HpoaAnnotationLine> removed = new ArrayList<>();
        List<HpoaAnnotationLine> subtracted = new ArrayList<>();
        int undecomposable = 0;
        for (HpoaAnnotationLine line : hpoa.annotationLines()) {
            if (!line.cites(pmid)) {
                retained.add(line);
                continue;
            }
            if (!line.hasMultipleReferences()) {
                removed.add(line);
                continue;
            }
            Optional<HpoaAnnotationLine> decomposed = subtractCohort(line, pmid);
            if (decomposed.isPresent()) {
                retained.add(decomposed.get());
                subtracted.add(line);
            } else {
                removed.add(line);
                undecomposable++;
            }
        }
        return new PublicationAdjustment(pmid, new HpoaFile(hpoa.headerLines(), retained),
                removed, subtracted, undecomposable);
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
                LOGGER.info("{}: removed {} lines, subtracted cohort counts from {} lines, wrote {}",
                        pmid, adjustment.removedLines().size(), adjustment.subtractedLines().size(), path);
            } else {
                LOGGER.info("{} is not cited in the HPOA, no adjusted file needed", pmid);
            }
            for (PhenopacketCase phenopacketCase : entry.getValue()) {
                results.add(evaluateCase(phenopacketCase, adjustment, outputFile));
            }
        }
        return results;
    }

    private Optional<HpoaAnnotationLine> subtractCohort(HpoaAnnotationLine line, TermId pmid) {
        Optional<Ratio> frequency = line.frequencyRatio();
        Optional<CohortCounts> counts = cohortSource.lookup(line.diseaseId(), pmid);
        if (frequency.isEmpty() || counts.isEmpty()) {
            return Optional.empty();
        }
        Ratio adjusted = frequency.get().minus(counts.get().ratioOf(line.hpoId()));
        if (!adjusted.isInformative()) {
            return Optional.empty();
        }
        return Optional.of(line.withoutReference(pmid, adjusted));
    }

    private static CaseResult evaluateCase(PhenopacketCase phenopacketCase,
                                           PublicationAdjustment adjustment,
                                           Optional<Path> outputFile) {
        TermId diseaseId = phenopacketCase.diseaseId();
        int removedForDisease = countForDisease(adjustment.removedLines(), diseaseId);
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
                adjustment.removedLines().size(), adjustment.subtractedLines().size(),
                adjustment.undecomposableRemoved(), remainingPhenotypeLines, outputFile);
    }

    private static int countForDisease(List<HpoaAnnotationLine> lines, TermId diseaseId) {
        return (int) lines.stream()
                .filter(line -> line.diseaseId().equals(diseaseId))
                .count();
    }
}
