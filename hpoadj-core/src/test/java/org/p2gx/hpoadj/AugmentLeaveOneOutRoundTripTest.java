package org.p2gx.hpoadj;

import org.junit.jupiter.api.Test;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.augment.HpoaAugmenter;
import org.p2gx.hpoadj.cohort.OntologyAncestors;
import org.p2gx.hpoadj.cohort.PhenopacketCohort;
import org.p2gx.hpoadj.hpoa.HpoaAnnotationLine;
import org.p2gx.hpoadj.hpoa.HpoaFile;
import org.p2gx.hpoadj.hpoa.Ratio;
import org.p2gx.hpoadj.loo.LeaveOneOutAdjuster;
import org.p2gx.hpoadj.loo.PublicationAdjustment;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.p2gx.hpoadj.TestData.APDS;
import static org.p2gx.hpoadj.TestData.GROWTH_DELAY;
import static org.p2gx.hpoadj.TestData.RECURRENT_RESPIRATORY_INFECTIONS;

public class AugmentLeaveOneOutRoundTripTest {

    private static final TermId PMID_A = TermId.of("PMID:16984281");
    private static final TermId PMID_B = TermId.of("PMID:27379089");
    private static final HpoaFile BASE = TestData.smallHpoa();
    private static final PhenopacketCohort COHORT = PhenopacketCohort.of(List.of(
            TestData.apdsCase("A1", PMID_A.getValue(), RECURRENT_RESPIRATORY_INFECTIONS, GROWTH_DELAY),
            TestData.apdsCase("A2", PMID_A.getValue(), RECURRENT_RESPIRATORY_INFECTIONS),
            TestData.apdsCase("B1", PMID_B.getValue(), GROWTH_DELAY)),
            OntologyAncestors.selfOnly());

    private static Optional<HpoaAnnotationLine> apdsLine(HpoaFile hpoa, TermId hpoId) {
        return hpoa.annotationLines().stream()
                .filter(line -> line.diseaseId().equals(APDS) && line.hpoId().equals(hpoId))
                .findFirst();
    }

    @Test
    public void leavingOnePublicationOutLeavesTheOthersCounts() {
        HpoaFile augmented = new HpoaAugmenter(COHORT, "HPO:test[2026-09-21]").augment(BASE).hpoa();
        assertEquals(Optional.of(new Ratio(17, 20)), apdsLine(augmented, RECURRENT_RESPIRATORY_INFECTIONS).orElseThrow().frequencyRatio());
        assertEquals(Optional.of(new Ratio(2, 3)), apdsLine(augmented, GROWTH_DELAY).orElseThrow().frequencyRatio());

        LeaveOneOutAdjuster adjuster = new LeaveOneOutAdjuster(augmented, COHORT);
        HpoaFile withoutA = adjuster.adjustFor(PMID_A).adjusted();
        assertEquals(Optional.of(new Ratio(15, 18)), apdsLine(withoutA, RECURRENT_RESPIRATORY_INFECTIONS).orElseThrow().frequencyRatio());
        assertEquals(Optional.of(new Ratio(1, 1)), apdsLine(withoutA, GROWTH_DELAY).orElseThrow().frequencyRatio());
        assertEquals(List.of(PMID_B), apdsLine(withoutA, GROWTH_DELAY).orElseThrow().references());

        HpoaFile withoutB = adjuster.adjustFor(PMID_B).adjusted();
        assertEquals(Optional.of(new Ratio(17, 19)), apdsLine(withoutB, RECURRENT_RESPIRATORY_INFECTIONS).orElseThrow().frequencyRatio());
        assertEquals(Optional.of(new Ratio(1, 2)), apdsLine(withoutB, GROWTH_DELAY).orElseThrow().frequencyRatio());
    }

    @Test
    public void leavingEveryPublicationOutRestoresTheReleasedCounts() {
        HpoaFile augmented = new HpoaAugmenter(COHORT, "HPO:test[2026-09-21]").augment(BASE).hpoa();

        PublicationAdjustment withoutA = new LeaveOneOutAdjuster(augmented, COHORT).adjustFor(PMID_A);
        PublicationAdjustment withoutBoth = new LeaveOneOutAdjuster(withoutA.adjusted(), COHORT).adjustFor(PMID_B);
        assertTrue(withoutA.undecomposableLines().isEmpty());
        assertTrue(withoutBoth.undecomposableLines().isEmpty());

        HpoaAnnotationLine released = apdsLine(BASE, RECURRENT_RESPIRATORY_INFECTIONS).orElseThrow();
        HpoaAnnotationLine restored = apdsLine(withoutBoth.adjusted(), RECURRENT_RESPIRATORY_INFECTIONS).orElseThrow();
        assertEquals(released.frequencyRatio(), restored.frequencyRatio());
        assertEquals(released.references(), restored.references());
        assertTrue(apdsLine(withoutBoth.adjusted(), GROWTH_DELAY).isEmpty());
        assertEquals(1, withoutBoth.soleSupportLines().size());
        assertEquals(BASE.annotationCount(), withoutBoth.adjusted().annotationCount());
    }
}
