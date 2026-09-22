package org.p2gx.hpoadj.cohort;

import org.junit.jupiter.api.Test;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.TestData;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.p2gx.hpoadj.TestData.APDS;
import static org.p2gx.hpoadj.TestData.GROWTH_DELAY;
import static org.p2gx.hpoadj.TestData.NFKB1;
import static org.p2gx.hpoadj.TestData.RECURRENT_INFECTIONS;
import static org.p2gx.hpoadj.TestData.RECURRENT_RESPIRATORY_INFECTIONS;
import static org.p2gx.hpoadj.TestData.RECURRENT_SINOPULMONARY_INFECTIONS;

public class PhenopacketCohortTest {

    @Test
    public void loadsDirectoryAndSkipsUnusablePhenopackets() {
        PhenopacketCohort cohort = PhenopacketCohort.load(TestData.resource("/phenopackets"), OntologyAncestors.selfOnly());
        assertEquals(2, cohort.cases().size());
        assertEquals(List.of(APDS, NFKB1), cohort.diseases());

        PhenopacketCase apds = cohort.casesOf(APDS).get(0);
        assertEquals("PMID_24136356_P10", apds.phenopacketId());
        assertEquals(TermId.of("PMID:24136356"), apds.pmid());
        assertEquals(Set.of(RECURRENT_SINOPULMONARY_INFECTIONS), apds.observedTerms());

        PhenopacketCase nfkb1 = cohort.casesOf(NFKB1).get(0);
        assertEquals("Immunodeficiency 45", nfkb1.diseaseName());
        assertEquals(TermId.of("PMID:29403474"), nfkb1.pmid());
    }

    @Test
    public void countsPropagateToAncestors() {
        PhenopacketCohort cohort = PhenopacketCohort.of(List.of(
                TestData.apdsCase("A", "PMID:1", RECURRENT_SINOPULMONARY_INFECTIONS),
                TestData.apdsCase("B", "PMID:1", RECURRENT_RESPIRATORY_INFECTIONS, GROWTH_DELAY),
                TestData.apdsCase("C", "PMID:2", GROWTH_DELAY)),
                OntologyAncestors.withSelf(TestData.smallOntology()));

        CohortCounts counts = cohort.countsOf(APDS);
        assertEquals(3, counts.cohortSize());
        assertEquals(1, counts.countOf(RECURRENT_SINOPULMONARY_INFECTIONS));
        assertEquals(2, counts.countOf(RECURRENT_RESPIRATORY_INFECTIONS));
        assertEquals(2, counts.countOf(RECURRENT_INFECTIONS));
        assertEquals(2, counts.countOf(GROWTH_DELAY));
        assertEquals(3, counts.countOf(TermId.of("HP:0000118")));

        CohortCounts fromPmid1 = cohort.lookup(APDS, TermId.of("PMID:1")).orElseThrow();
        assertEquals(2, fromPmid1.cohortSize());
        assertEquals(1, fromPmid1.countOf(GROWTH_DELAY));
        assertTrue(cohort.lookup(APDS, TermId.of("PMID:3")).isEmpty());
    }
}
