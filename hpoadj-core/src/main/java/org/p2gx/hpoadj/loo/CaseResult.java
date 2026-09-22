package org.p2gx.hpoadj.loo;

import org.p2gx.hpoadj.cohort.PhenopacketCase;

import java.nio.file.Path;
import java.util.Optional;

public record CaseResult(PhenopacketCase phenopacketCase,
                         CaseStatus status,
                         int linesRemovedForDisease,
                         int linesSubtractedForDisease,
                         int linesRemovedTotal,
                         int linesSubtractedTotal,
                         int undecomposableLinesRemoved,
                         long remainingPhenotypeLines,
                         Optional<Path> adjustedHpoa) {
}
