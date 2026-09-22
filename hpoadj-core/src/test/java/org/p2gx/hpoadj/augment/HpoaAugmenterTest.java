package org.p2gx.hpoadj.augment;

import org.junit.jupiter.api.Test;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.TestData;
import org.p2gx.hpoadj.cohort.OntologyAncestors;
import org.p2gx.hpoadj.cohort.PhenopacketCase;
import org.p2gx.hpoadj.cohort.PhenopacketCohort;
import org.p2gx.hpoadj.hpoa.HpoaAnnotationLine;
import org.p2gx.hpoadj.hpoa.HpoaFile;
import org.p2gx.hpoadj.hpoa.Ratio;
import org.p2gx.hpoadj.loo.LeaveOneOutAdjuster;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.p2gx.hpoadj.TestData.APDS;
import static org.p2gx.hpoadj.TestData.GROWTH_DELAY;
import static org.p2gx.hpoadj.TestData.RECURRENT_RESPIRATORY_INFECTIONS;
import static org.p2gx.hpoadj.TestData.RECURRENT_SINOPULMONARY_INFECTIONS;

public class HpoaAugmenterTest {

    private static final String BIOCURATION = "HPO:test[2026-09-21]";
    private static final HpoaFile BASE = TestData.smallHpoa();

    private static PhenopacketCohort cohortOf(PhenopacketCase... cases) {
        return PhenopacketCohort.of(List.of(cases), OntologyAncestors.selfOnly());
    }

    private static Optional<HpoaAnnotationLine> apdsLine(HpoaFile hpoa, TermId hpoId) {
        return hpoa.annotationLines().stream()
                .filter(line -> line.diseaseId().equals(APDS) && line.hpoId().equals(hpoId))
                .findFirst();
    }

    @Test
    public void cohortAnnotationsCarryPooledFrequencyAndAllPmids() {
        AugmentedHpoa augmented = new HpoaAugmenter(cohortOf(
                TestData.apdsCase("A", "PMID:16984281", GROWTH_DELAY),
                TestData.apdsCase("B", "PMID:16984281"),
                TestData.apdsCase("C", "PMID:27379089", GROWTH_DELAY)), BIOCURATION).augment(BASE);

        HpoaAnnotationLine line = apdsLine(augmented.hpoa(), GROWTH_DELAY).orElseThrow();
        assertEquals(Optional.of(new Ratio(2, 3)), line.frequencyRatio());
        assertEquals(List.of(TermId.of("PMID:16984281"), TermId.of("PMID:27379089")), line.references());
        assertTrue(line.raw().endsWith("\tP\t" + BIOCURATION));
        assertTrue(augmented.hpoa().headerLines().contains(HpoaAugmenter.PROVENANCE_HEADER + "OMIM:615513"));
    }

    @Test
    public void annotationsFromCohortPublicationsAreSuperseded() {
        AugmentedHpoa augmented = new HpoaAugmenter(cohortOf(
                TestData.apdsCase("A", "PMID:24136356", RECURRENT_SINOPULMONARY_INFECTIONS)), BIOCURATION).augment(BASE);

        DiseaseAugmentation report = augmented.augmentations().get(0);
        assertEquals(APDS, report.diseaseId());
        assertEquals(2, report.linesSuperseded());
        assertEquals(1, report.linesAdded());
        assertEquals(0, report.linesPooled());
        assertEquals(Optional.of(new Ratio(1, 1)),
                apdsLine(augmented.hpoa(), RECURRENT_SINOPULMONARY_INFECTIONS).orElseThrow().frequencyRatio());
        assertTrue(apdsLine(augmented.hpoa(), RECURRENT_RESPIRATORY_INFECTIONS).isEmpty());
        assertEquals(BASE.phenotypeLineCount(TestData.NFKB1), augmented.hpoa().phenotypeLineCount(TestData.NFKB1));
    }

    @Test
    public void annotationsFromOtherPublicationsArePooled() {
        PhenopacketCohort cohort = cohortOf(
                TestData.apdsCase("A", "PMID:16984281", RECURRENT_RESPIRATORY_INFECTIONS),
                TestData.apdsCase("B", "PMID:16984281", RECURRENT_RESPIRATORY_INFECTIONS));
        HpoaFile augmented = new HpoaAugmenter(cohort, BIOCURATION).augment(BASE).hpoa();

        HpoaAnnotationLine line = apdsLine(augmented, RECURRENT_RESPIRATORY_INFECTIONS).orElseThrow();
        assertEquals(Optional.of(new Ratio(17, 19)), line.frequencyRatio());
        assertEquals(List.of(TermId.of("PMID:16984281"), TermId.of("PMID:24136356"), TermId.of("PMID:24165795")),
                line.references());

        HpoaFile leftOut = new LeaveOneOutAdjuster(augmented, cohort).adjustFor(TermId.of("PMID:16984281")).adjusted();
        assertEquals(Optional.of(new Ratio(15, 17)), apdsLine(leftOut, RECURRENT_RESPIRATORY_INFECTIONS).orElseThrow().frequencyRatio());
    }

    @Test
    public void lineWithoutFrequencyIsPooledAsOneCase() {
        HpoaFile base = new HpoaFile(List.of(), List.of(
                HpoaAnnotationLine.of("OMIM:615513\tImmunodeficiency 14\t\tHP:0001510\tPMID:99999999\tPCS\t\t\t\t\tP\tHPO:x[2020-01-01]")));
        HpoaFile augmented = new HpoaAugmenter(cohortOf(
                TestData.apdsCase("A", "PMID:16984281", GROWTH_DELAY),
                TestData.apdsCase("B", "PMID:16984281")), BIOCURATION).augment(base).hpoa();

        HpoaAnnotationLine line = apdsLine(augmented, GROWTH_DELAY).orElseThrow();
        assertEquals(Optional.of(new Ratio(2, 3)), line.frequencyRatio());
        assertEquals(List.of(TermId.of("PMID:16984281"), TermId.of("PMID:99999999")), line.references());
        assertEquals(1, augmented.annotationCount());
    }
}
