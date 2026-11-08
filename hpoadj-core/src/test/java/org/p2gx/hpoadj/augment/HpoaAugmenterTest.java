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

    private static HpoaFile baseWith(String... lines) {
        return new HpoaFile(List.of(), List.of(lines).stream().map(HpoaAnnotationLine::of).toList());
    }

    private static List<HpoaAnnotationLine> apdsLines(HpoaFile hpoa, TermId hpoId) {
        return hpoa.annotationLines().stream()
                .filter(line -> line.diseaseId().equals(APDS) && line.hpoId().equals(hpoId))
                .toList();
    }

    private static Optional<HpoaAnnotationLine> apdsLine(HpoaFile hpoa, TermId hpoId) {
        return apdsLines(hpoa, hpoId).stream().findFirst();
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
        assertEquals(BIOCURATION, line.biocuration());
        assertTrue(augmented.hpoa().headerLines().contains(HpoaFile.AUGMENTED_DISEASES_HEADER + "OMIM:615513"));
        assertEquals(Optional.of(BIOCURATION), augmented.hpoa().cohortBiocuration());
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
        assertEquals(0, report.linesRetainedBesideCohort());
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
        HpoaFile base = baseWith(
                "OMIM:615513\tImmunodeficiency 14\t\tHP:0001510\tPMID:99999999\tPCS\t\t\t\t\tP\tHPO:x[2020-01-01]");
        HpoaFile augmented = new HpoaAugmenter(cohortOf(
                TestData.apdsCase("A", "PMID:16984281", GROWTH_DELAY),
                TestData.apdsCase("B", "PMID:16984281")), BIOCURATION).augment(base).hpoa();

        HpoaAnnotationLine line = apdsLine(augmented, GROWTH_DELAY).orElseThrow();
        assertEquals(Optional.of(new Ratio(2, 3)), line.frequencyRatio());
        assertEquals(List.of(TermId.of("PMID:16984281"), TermId.of("PMID:99999999")), line.references());
        assertEquals(1, augmented.annotationCount());
    }

    @Test
    public void everyPoolableLineOfATermIsPooled() {
        HpoaFile base = baseWith(
                "OMIM:615513\tImmunodeficiency 14\t\tHP:0001510\tPMID:11111111\tPCS\t\t3/4\t\t\tP\tHPO:x[2020-01-01]",
                "OMIM:615513\tImmunodeficiency 14\t\tHP:0001510\tPMID:22222222\tPCS\t\t\t\t\tP\tHPO:y[2021-01-01]");
        AugmentedHpoa augmented = new HpoaAugmenter(cohortOf(
                TestData.apdsCase("A", "PMID:16984281", GROWTH_DELAY)), BIOCURATION).augment(base);

        List<HpoaAnnotationLine> lines = apdsLines(augmented.hpoa(), GROWTH_DELAY);
        assertEquals(1, lines.size());
        assertEquals(Optional.of(new Ratio(5, 6)), lines.get(0).frequencyRatio());
        assertEquals(List.of(TermId.of("PMID:16984281"), TermId.of("PMID:11111111"), TermId.of("PMID:22222222")),
                lines.get(0).references());
        assertEquals(2, augmented.augmentations().get(0).linesPooled());
    }

    @Test
    public void negatedAndNonCountLinesStayBesideTheCohortLine() {
        HpoaFile base = baseWith(
                "OMIM:615513\tImmunodeficiency 14\tNOT\tHP:0001510\tPMID:11111111\tPCS\t\t\t\t\tP\tHPO:x[2020-01-01]",
                "OMIM:615513\tImmunodeficiency 14\t\tHP:0002205\tPMID:22222222\tPCS\t\tHP:0040281\t\t\tP\tHPO:y[2021-01-01]",
                "OMIM:615513\tImmunodeficiency 14\t\tHP:0002719\tPMID:33333333\tPCS\t\t40%\t\t\tP\tHPO:z[2021-01-01]");
        AugmentedHpoa augmented = new HpoaAugmenter(cohortOf(
                TestData.apdsCase("A", "PMID:16984281", GROWTH_DELAY, RECURRENT_RESPIRATORY_INFECTIONS)),
                BIOCURATION).augment(base);

        assertEquals(5, augmented.hpoa().annotationCount());
        List<HpoaAnnotationLine> growthDelay = apdsLines(augmented.hpoa(), GROWTH_DELAY);
        assertEquals(2, growthDelay.size());
        assertTrue(growthDelay.get(0).negated());
        assertEquals(Optional.of(new Ratio(1, 1)), growthDelay.get(1).frequencyRatio());
        assertEquals(2, apdsLines(augmented.hpoa(), RECURRENT_RESPIRATORY_INFECTIONS).size());
        DiseaseAugmentation report = augmented.augmentations().get(0);
        assertEquals(2, report.linesAdded());
        assertEquals(0, report.linesPooled());
        assertEquals(2, report.linesRetainedBesideCohort());
    }
}
