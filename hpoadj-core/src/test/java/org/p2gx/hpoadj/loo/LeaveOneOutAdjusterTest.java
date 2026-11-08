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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.p2gx.hpoadj.TestData.APDS;
import static org.p2gx.hpoadj.TestData.NFKB1;
import static org.p2gx.hpoadj.TestData.RECURRENT_RESPIRATORY_INFECTIONS;

public class LeaveOneOutAdjusterTest {

    private static final HpoaFile HPOA = TestData.smallHpoa();
    private static final TermId PMID = TermId.of("PMID:24136356");
    private static final String POOLED_BIOCURATION = "HPO:test[2026-09-21]";
    private static final HpoaFile POOLED = new HpoaFile(
            List.of(HpoaFile.COHORT_BIOCURATION_HEADER + POOLED_BIOCURATION),
            List.of(HpoaAnnotationLine.of("OMIM:615513\tImmunodeficiency 14\t\tHP:0002205\tPMID:24136356;PMID:24165795\tPCS\t\t15/17\t\t\tP\t" + POOLED_BIOCURATION)));

    private static PhenopacketCase caseOf(String id, TermId diseaseId, String pmid) {
        return new PhenopacketCase(id, diseaseId, "", TermId.of(pmid), Set.of(), Path.of(id + ".json"));
    }

    private static CohortSource countsOf(int observed, int size) {
        return (diseaseId, pmid) -> diseaseId.equals(APDS) && pmid.equals(PMID)
                ? Optional.of(new CohortCounts(Map.of(RECURRENT_RESPIRATORY_INFECTIONS, observed), size))
                : Optional.empty();
    }

    private static HpoaAnnotationLine onlyLine(PublicationAdjustment adjustment) {
        assertEquals(1, adjustment.adjusted().annotationCount());
        return adjustment.adjusted().annotationLines().get(0);
    }

    @Test
    public void linesOfAReleasedHpoaAreDroppedAndTheFileIsWritten(@TempDir Path tempDir) throws Exception {
        List<CaseResult> results = new LeaveOneOutAdjuster(HPOA).adjustAll(List.of(
                caseOf("P10", APDS, "PMID:24136356"),
                caseOf("P11", APDS, "PMID:24136356")), tempDir);

        assertEquals(2, results.size());
        CaseResult result = results.get(0);
        assertEquals(CaseStatus.ADJUSTED, result.status());
        assertEquals(3, result.linesRemovedForDisease());
        assertEquals(0, result.linesSubtractedForDisease());
        assertEquals(2, result.soleSupportRemovedTotal());
        assertEquals(1, result.undecomposableRemovedTotal());
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
    public void curatedMultiReferenceLineIsNotDecomposedEvenWhenCountsAreKnown() {
        PublicationAdjustment adjustment = new LeaveOneOutAdjuster(HPOA, countsOf(12, 14)).adjustFor(PMID);

        assertEquals(2, adjustment.soleSupportLines().size());
        assertEquals(1, adjustment.undecomposableLines().size());
        assertEquals(0, adjustment.subtractedLines().size());
        assertTrue(adjustment.adjusted().linesCiting(PMID).isEmpty());
    }

    @Test
    public void pooledLineIsSubtracted() {
        PublicationAdjustment adjustment = new LeaveOneOutAdjuster(POOLED, countsOf(12, 14)).adjustFor(PMID);

        assertEquals(1, adjustment.subtractedLines().size());
        assertTrue(adjustment.removedLines().isEmpty());
        HpoaAnnotationLine line = onlyLine(adjustment);
        assertEquals(Optional.of(new Ratio(3, 3)), line.frequencyRatio());
        assertEquals(List.of(TermId.of("PMID:24165795")), line.references());
        assertEquals(POOLED_BIOCURATION, line.biocuration());
    }

    @Test
    public void pooledLineThatReachesZeroIsSoleSupport() {
        PublicationAdjustment adjustment = new LeaveOneOutAdjuster(POOLED, countsOf(15, 17)).adjustFor(PMID);

        assertEquals(1, adjustment.soleSupportLines().size());
        assertEquals(0, adjustment.undecomposableLines().size());
        assertEquals(0, adjustment.adjusted().annotationCount());
    }

    @Test
    public void pooledLineWithUnknownOrInconsistentCountsIsUndecomposable() {
        assertEquals(1, new LeaveOneOutAdjuster(POOLED).adjustFor(PMID).undecomposableLines().size());
        assertEquals(1, new LeaveOneOutAdjuster(POOLED, countsOf(16, 16)).adjustFor(PMID).undecomposableLines().size());
    }

    @Test
    public void dropAndKeepPolicies() {
        PublicationAdjustment dropped = new LeaveOneOutAdjuster(POOLED, countsOf(12, 14), MultiReferencePolicy.DROP).adjustFor(PMID);
        assertEquals(1, dropped.undecomposableLines().size());
        assertEquals(0, dropped.adjusted().annotationCount());

        PublicationAdjustment kept = new LeaveOneOutAdjuster(POOLED, countsOf(12, 14), MultiReferencePolicy.KEEP).adjustFor(PMID);
        assertTrue(kept.removedLines().isEmpty());
        assertTrue(kept.subtractedLines().isEmpty());
        assertEquals(POOLED.annotationLines(), kept.adjusted().annotationLines());
    }

    @Test
    public void adjustmentIsComputedOncePerPublication() {
        LeaveOneOutAdjuster adjuster = new LeaveOneOutAdjuster(HPOA);
        assertSame(adjuster.adjustFor(PMID), adjuster.adjustFor(PMID));
    }
}
