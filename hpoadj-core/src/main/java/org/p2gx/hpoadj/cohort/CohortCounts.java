package org.p2gx.hpoadj.cohort;

import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.hpoa.Ratio;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Individuals per term, each individual counted once for every ancestor of every term it carries. */
public record CohortCounts(Map<TermId, Integer> countsByTerm, int cohortSize) {

    public static CohortCounts of(List<Set<TermId>> individualTermSets, Function<TermId, Set<TermId>> ancestorsWithSelf) {
        Map<TermId, Integer> counts = new HashMap<>();
        for (Set<TermId> observedTerms : individualTermSets) {
            observedTerms.stream()
                    .flatMap(term -> ancestorsWithSelf.apply(term).stream())
                    .distinct()
                    .forEach(term -> counts.merge(term, 1, Integer::sum));
        }
        return new CohortCounts(Map.copyOf(counts), individualTermSets.size());
    }

    public int countOf(TermId termId) {
        return countsByTerm.getOrDefault(termId, 0);
    }

    public Ratio ratioOf(TermId termId) {
        return new Ratio(countOf(termId), cohortSize);
    }
}
