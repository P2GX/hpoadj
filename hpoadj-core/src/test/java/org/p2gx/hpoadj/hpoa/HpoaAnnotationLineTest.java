package org.p2gx.hpoadj.hpoa;

import org.junit.jupiter.api.Test;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.HpoadjException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HpoaAnnotationLineTest {

    private static final String LINE = "OMIM:615513\tImmunodeficiency 14\t\tHP:0002205\tPMID:24136356;PMID:24165795\tPCS\t\t15/17\t\t\tP\tHPO:probinson[2014-02-03]";

    @Test
    public void parsesFieldsAndKeepsRawLine() {
        HpoaAnnotationLine line = HpoaAnnotationLine.of(LINE);
        assertEquals(TermId.of("OMIM:615513"), line.diseaseId());
        assertEquals(TermId.of("HP:0002205"), line.hpoId());
        assertEquals(List.of(TermId.of("PMID:24136356"), TermId.of("PMID:24165795")), line.references());
        assertEquals(Optional.of(new Ratio(15, 17)), line.frequencyRatio());
        assertTrue(line.isPhenotypeAnnotation());
        assertEquals(LINE, line.raw());
    }

    @Test
    public void rewritingReferencesAndFrequencyTouchesOnlyThoseFields() {
        HpoaAnnotationLine line = HpoaAnnotationLine.of(LINE)
                .withoutReference(TermId.of("PMID:24136356"), new Ratio(3, 3));
        assertEquals("OMIM:615513\tImmunodeficiency 14\t\tHP:0002205\tPMID:24165795\tPCS\t\t3/3\t\t\tP\tHPO:probinson[2014-02-03]",
                line.raw());
    }

    @Test
    public void lineWithoutFrequencyPoolsAsSingleCase() {
        HpoaAnnotationLine observed = HpoaAnnotationLine.of(LINE.replace("15/17", ""));
        HpoaAnnotationLine negated = HpoaAnnotationLine.of(LINE.replace("15/17", "").replace("\t\tHP:", "\tNOT\tHP:"));
        HpoaAnnotationLine percent = HpoaAnnotationLine.of(LINE.replace("15/17", "88%"));
        assertEquals(Optional.empty(), observed.frequencyRatio());
        assertEquals(Optional.of(new Ratio(1, 1)), observed.poolableRatio());
        assertEquals(Optional.of(new Ratio(0, 1)), negated.poolableRatio());
        assertEquals(Optional.empty(), percent.poolableRatio());
    }

    @Test
    public void rejectsWrongFieldCount() {
        assertThrows(HpoadjException.class, () -> HpoaAnnotationLine.of("OMIM:1\tname\tHP:1"));
    }
}
