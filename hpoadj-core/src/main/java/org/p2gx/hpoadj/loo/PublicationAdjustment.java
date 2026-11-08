package org.p2gx.hpoadj.loo;

import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.hpoa.HpoaAnnotationLine;
import org.p2gx.hpoadj.hpoa.HpoaFile;

import java.util.ArrayList;
import java.util.List;

public record PublicationAdjustment(TermId pmid,
                                    HpoaFile adjusted,
                                    List<HpoaAnnotationLine> soleSupportLines,
                                    List<HpoaAnnotationLine> undecomposableLines,
                                    List<HpoaAnnotationLine> subtractedLines) {

    public List<HpoaAnnotationLine> removedLines() {
        List<HpoaAnnotationLine> removed = new ArrayList<>(soleSupportLines);
        removed.addAll(undecomposableLines);
        return removed;
    }

    public boolean changesHpoa() {
        return !soleSupportLines.isEmpty() || !undecomposableLines.isEmpty() || !subtractedLines.isEmpty();
    }

    public String fileName() {
        return "phenotype_" + pmid.getValue().replace(':', '_') + ".hpoa";
    }
}
