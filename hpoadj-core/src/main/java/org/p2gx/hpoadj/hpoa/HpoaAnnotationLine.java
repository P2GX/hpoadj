package org.p2gx.hpoadj.hpoa;

import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.HpoadjException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record HpoaAnnotationLine(String raw,
                                 TermId diseaseId,
                                 String diseaseName,
                                 boolean negated,
                                 TermId hpoId,
                                 List<TermId> references,
                                 String frequency,
                                 String aspect) {

    public static final String PHENOTYPE_ASPECT = "P";
    public static final String NEGATION_QUALIFIER = "NOT";

    private static final int FIELD_COUNT = 12;
    private static final int DISEASE_ID = 0;
    private static final int DISEASE_NAME = 1;
    private static final int QUALIFIER = 2;
    private static final int HPO_ID = 3;
    private static final int REFERENCE = 4;
    private static final int EVIDENCE = 5;
    private static final int FREQUENCY = 7;
    private static final int ASPECT = 10;
    private static final int BIOCURATION = 11;

    public static HpoaAnnotationLine of(String line) {
        String[] fields = line.split("\t", -1);
        if (fields.length != FIELD_COUNT) {
            throw new HpoadjException(String.format("Expected %d fields but got %d: %s",
                    FIELD_COUNT, fields.length, line));
        }
        List<TermId> references = fields[REFERENCE].isEmpty()
                ? List.of()
                : Arrays.stream(fields[REFERENCE].split(";")).map(TermId::of).toList();
        return new HpoaAnnotationLine(line,
                TermId.of(fields[DISEASE_ID]),
                fields[DISEASE_NAME],
                NEGATION_QUALIFIER.equals(fields[QUALIFIER]),
                TermId.of(fields[HPO_ID]),
                references,
                fields[FREQUENCY],
                fields[ASPECT]);
    }

    public static HpoaAnnotationLine phenotypeAnnotation(TermId diseaseId,
                                                        String diseaseName,
                                                        TermId hpoId,
                                                        Collection<TermId> references,
                                                        Ratio frequency,
                                                        String biocuration) {
        String[] fields = new String[FIELD_COUNT];
        Arrays.fill(fields, "");
        fields[DISEASE_ID] = diseaseId.getValue();
        fields[DISEASE_NAME] = diseaseName;
        fields[HPO_ID] = hpoId.getValue();
        fields[REFERENCE] = joinReferences(references);
        fields[EVIDENCE] = "PCS";
        fields[FREQUENCY] = frequency.format();
        fields[ASPECT] = PHENOTYPE_ASPECT;
        fields[BIOCURATION] = biocuration;
        return of(String.join("\t", fields));
    }

    public boolean cites(TermId reference) {
        return references.contains(reference);
    }

    public boolean hasMultipleReferences() {
        return references.size() > 1;
    }

    public boolean isPhenotypeAnnotation() {
        return PHENOTYPE_ASPECT.equals(aspect);
    }

    public Optional<Ratio> frequencyRatio() {
        return Ratio.parse(frequency);
    }

    /**
     * The count this line contributes when pooled with cohort counts: the explicit n/m, or 1/1
     * (0/1 when negated) for a line without a frequency, which the HPOA convention and the phenol
     * loader both read as a single case report. Percentages and HPO frequency terms cannot be pooled.
     */
    public Optional<Ratio> poolableRatio() {
        if (frequency.isEmpty()) {
            return Optional.of(new Ratio(negated ? 0 : 1, 1));
        }
        return frequencyRatio();
    }

    public HpoaAnnotationLine withReferencesAndFrequency(Collection<TermId> newReferences, Ratio newFrequency) {
        String[] fields = raw.split("\t", -1);
        fields[REFERENCE] = joinReferences(newReferences);
        fields[FREQUENCY] = newFrequency.format();
        return of(String.join("\t", fields));
    }

    public HpoaAnnotationLine withoutReference(TermId reference, Ratio adjustedFrequency) {
        List<TermId> remaining = references.stream()
                .filter(existing -> !existing.equals(reference))
                .toList();
        return withReferencesAndFrequency(remaining, adjustedFrequency);
    }

    private static String joinReferences(Collection<TermId> references) {
        return references.stream()
                .map(TermId::getValue)
                .collect(Collectors.joining(";"));
    }
}
