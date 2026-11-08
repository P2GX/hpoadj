package org.p2gx.hpoadj.cohort;

import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public final class OntologyAncestors {

    private static final Logger LOGGER = LoggerFactory.getLogger(OntologyAncestors.class);

    private OntologyAncestors() {
    }

    /** Ancestors including the term itself; a term unknown to the ontology maps to itself. */
    public static Function<TermId, Set<TermId>> withSelf(Ontology ontology) {
        Set<TermId> unknown = new HashSet<>();
        return termId -> {
            if (ontology.containsTerm(termId)) {
                return ontology.getAncestorTermIds(termId, true);
            }
            if (unknown.add(termId)) {
                LOGGER.warn("{} is not in the ontology; counted without ancestors", termId);
            }
            return Set.of(termId);
        };
    }

    public static Function<TermId, Set<TermId>> selfOnly() {
        return Set::of;
    }
}
