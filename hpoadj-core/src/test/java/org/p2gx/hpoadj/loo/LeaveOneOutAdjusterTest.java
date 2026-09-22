package org.p2gx.hpoadj.loo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.TestData;
import org.p2gx.hpoadj.cohort.CohortCounts;
import org.p2gx.hpoadj.cohort.CohortSource;
import org.p2gx.hpoadj.cohort.PhenopacketCase;
import org.p2gx.hpoadj.hpoa.HpoaAnnotationLine;
import org.p2gx.hpoadj.hpoa.HpoaFile;
import org.p2gx.hpoadj.hpoa.Ratio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.p2gx.hpoadj.TestData.APDS;
import static org.p2gx.hpoadj.TestData.NFKB1;
import static org.p2gx.hpoadj.TestData.RECURRENT_RESPIRATORY_INFECTIONS;

public class LeaveOneOutAdjusterTest {

    private static final HpoaFile HPOA = TestData.smallHpoa();
    private static final TermId PMID = TermId.of("PMID:24136356");

    private static PhenopacketCase caseOf(String id, TermId diseaseId, String pmid) {
        return new PhenopacketCase(id, diseaseId, "", TermId.of(pmid), Set.of(), Path.of(id + ".json"));
    }

    @Test
    public void singleReferenceLinesAreRemovedAndFileIsWritten(@TempDir Path tempDir) throws Exception {
        List<CaseResult> results = new LeaveOneOutAdjuster(HPOA).adjustAll(List.of(
                caseOf("P10", APDS, "PMID:24136356"),
                caseOf("P11", APDS, "PMID:24136356")), tempDir);

        assertEquals(2, results.size());
        CaseResult result = results.get(0);
        assertEquals(CaseStatus.ADJUSTED, result.status());
        assertEquals(3, result.linesRemovedForDisease());
        assertEquals(1, result.undecomposableLinesRemoved());
        assertEquals(1, result.remainingPhenotypeLines());
        assertEquals(results.get(1).adjustedHpoa(), result.adjustedHpoa());
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of(tempDir.resolve("phenotype_PMID_24136356.hpoa")), files.toList());
        }
        HpoaFile reloaded = HpoaFile.parse(result.adjustedHpoa().orElseThrow());
        assertTrue(reloaded.linesCiting(PMID).isEmpty());
        assertEquals(5, reloaded.annotationCount());
    }

    @Test
    public void statusesForUncitedAndExhaustedDiseases(@TempDir Path tempDir) {
        List<CaseResult> results = new LeaveOneOutAdjuster(HPOA).adjustAll(List.of(
                caseOf("P3", APDS, "PMID:16984281"),
                caseOf("I1", NFKB1, "PMID:29403474")), tempDir);

        assertEquals(CaseStatus.NO_PMID_EVIDENCE, results.get(0).status());
        assertTrue(results.get(0).adjustedHpoa().isEmpty());
        assertEquals(CaseStatus.INSUFFICIENT_DATA, results.get(1).status());
        assertEquals(0, results.get(1).remainingPhenotypeLines());
    }

    @Test
    public void multiReferenceLineIsSubtractedWhenCohortCountsAreKnown() {
        CohortSource source = (diseaseId, pmid) -> diseaseId.equals(APDS) && pmid.equals(PMID)
                ? Optional.of(new CohortCounts(Map.of(RECURRENT_RESPIRATORY_INFECTIONS, 12), 14))
                : Optional.empty();
        PublicationAdjustment adjustment = new LeaveOneOutAdjuster(HPOA, source).adjustFor(PMID);

        assertEquals(2, adjustment.removedLines().size());
        assertEquals(1, adjustment.subtractedLines().size());
        assertEquals(0, adjustment.undecomposableRemoved());
        HpoaAnnotationLine line = adjustment.adjusted().annotationLines().stream()
                .filter(candidate -> candidate.hpoId().equals(RECURRENT_RESPIRATORY_INFECTIONS))
                .findFirst()
                .orElseThrow();
        assertEquals(Optional.of(new Ratio(3, 3)), line.frequencyRatio());
        assertEquals(List.of(TermId.of("PMID:24165795")), line.references());
    }

    @Test
    public void subtractionThatZeroesTheLineRemovesIt() {
        CohortSource source = (diseaseId, pmid) ->
                Optional.of(new CohortCounts(Map.of(RECURRENT_RESPIRATORY_INFECTIONS, 15), 17));
        PublicationAdjustment adjustment = new LeaveOneOutAdjuster(HPOA, source).adjustFor(PMID);

        assertEquals(3, adjustment.removedLines().size());
        assertEquals(1, adjustment.undecomposableRemoved());
        assertTrue(adjustment.adjusted().linesCiting(PMID).isEmpty());
    }
}
