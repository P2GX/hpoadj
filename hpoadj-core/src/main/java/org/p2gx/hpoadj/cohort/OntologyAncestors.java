package org.p2gx.hpoadj.cohort;

import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.monarchinitiative.phenol.ontology.data.TermId;

import java.util.Set;
import java.util.function.Function;

public final class OntologyAncestors {

    private OntologyAncestors() {
    }

    /** Ancestors including the term itself; a term unknown to the ontology maps to itself. */
    public static Function<TermId, Set<TermId>> withSelf(Ontology ontology) {
        return termId -> ontology.containsTerm(termId)
                ? ontology.getAncestorTermIds(termId, true)
                : Set.of(termId);
    }

    public static Function<TermId, Set<TermId>> selfOnly() {
        return Set::of;
    }
}
