package org.p2gx.hpoadj.augment;

import org.p2gx.hpoadj.hpoa.HpoaFile;

import java.util.List;

public record AugmentedHpoa(HpoaFile hpoa, List<DiseaseAugmentation> augmentations) {
}
