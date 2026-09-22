package org.p2gx.hpoadj.cohort;

import com.google.protobuf.util.JsonFormat;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.HpoadjException;
import org.phenopackets.schema.v2.Phenopacket;
import org.phenopackets.schema.v2.core.Disease;
import org.phenopackets.schema.v2.core.ExternalReference;
import org.phenopackets.schema.v2.core.Interpretation;
import org.phenopackets.schema.v2.core.OntologyClass;
import org.phenopackets.schema.v2.core.PhenotypicFeature;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record PhenopacketCase(String phenopacketId,
                              TermId diseaseId,
                              String diseaseName,
                              TermId pmid,
                              Set<TermId> observedTerms,
                              Path source) {

    private static final String PMID_PREFIX = "PMID:";

    public static PhenopacketCase fromFile(Path path) {
        return of(read(path), path);
    }

    public static PhenopacketCase of(Phenopacket phenopacket, Path source) {
        TermId pmid = pmidOf(phenopacket)
                .orElseThrow(() -> new HpoadjException("No PMID external reference in " + source));
        OntologyClass disease = diseaseOf(phenopacket)
                .orElseThrow(() -> new HpoadjException("No disease diagnosis in " + source));
        Set<TermId> observed = phenopacket.getPhenotypicFeaturesList().stream()
                .filter(feature -> !feature.getExcluded())
                .map(PhenotypicFeature::getType)
                .map(type -> TermId.of(type.getId()))
                .collect(Collectors.toUnmodifiableSet());
        return new PhenopacketCase(phenopacket.getId(), TermId.of(disease.getId()), disease.getLabel(),
                pmid, observed, source);
    }

    private static Phenopacket read(Path path) {
        Phenopacket.Builder builder = Phenopacket.newBuilder();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            JsonFormat.parser().ignoringUnknownFields().merge(reader, builder);
        } catch (IOException e) {
            throw new HpoadjException("Could not parse phenopacket " + path, e);
        }
        return builder.build();
    }

    private static Optional<TermId> pmidOf(Phenopacket phenopacket) {
        return phenopacket.getMetaData().getExternalReferencesList().stream()
                .map(ExternalReference::getId)
                .filter(id -> id.startsWith(PMID_PREFIX))
                .findFirst()
                .map(TermId::of);
    }

    private static Optional<OntologyClass> diseaseOf(Phenopacket phenopacket) {
        Optional<OntologyClass> diagnosed = phenopacket.getInterpretationsList().stream()
                .map(Interpretation::getDiagnosis)
                .map(diagnosis -> diagnosis.getDisease())
                .filter(disease -> !disease.getId().isEmpty())
                .findFirst();
        if (diagnosed.isPresent()) {
            return diagnosed;
        }
        return phenopacket.getDiseasesList().stream()
                .filter(disease -> !disease.getExcluded())
                .map(Disease::getTerm)
                .filter(term -> !term.getId().isEmpty())
                .findFirst();
    }
}
