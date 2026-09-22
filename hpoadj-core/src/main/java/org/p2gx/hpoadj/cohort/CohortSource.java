package org.p2gx.hpoadj.cohort;

import org.monarchinitiative.phenol.ontology.data.TermId;

import java.util.Optional;

/** Supplies the counts a publication contributed to a disease, so that they can be subtracted again. */
@FunctionalInterface
public interface CohortSource {

    Optional<CohortCounts> lookup(TermId diseaseId, TermId pmid);

    static CohortSource none() {
        return (diseaseId, pmid) -> Optional.empty();
    }
}
