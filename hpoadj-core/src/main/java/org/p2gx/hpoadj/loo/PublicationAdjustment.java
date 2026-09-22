package org.p2gx.hpoadj.loo;

import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.hpoa.HpoaAnnotationLine;
import org.p2gx.hpoadj.hpoa.HpoaFile;

import java.util.List;

public record PublicationAdjustment(TermId pmid,
                                    HpoaFile adjusted,
                                    List<HpoaAnnotationLine> removedLines,
                                    List<HpoaAnnotationLine> subtractedLines,
                                    int undecomposableRemoved) {

    public boolean changesHpoa() {
        return !removedLines.isEmpty() || !subtractedLines.isEmpty();
    }

    public String fileName() {
        return "phenotype_" + pmid.getValue().replace(':', '_') + ".hpoa";
    }
}
