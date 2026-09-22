package org.p2gx.hpoadj;

import org.monarchinitiative.phenol.io.OntologyLoader;
import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.cohort.PhenopacketCase;
import org.p2gx.hpoadj.hpoa.HpoaFile;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;

public final class TestData {

    public static final TermId APDS = TermId.of("OMIM:615513");
    public static final TermId NFKB1 = TermId.of("OMIM:616576");
    public static final TermId RECURRENT_INFECTIONS = TermId.of("HP:0002719");
    public static final TermId RECURRENT_RESPIRATORY_INFECTIONS = TermId.of("HP:0002205");
    public static final TermId RECURRENT_SINOPULMONARY_INFECTIONS = TermId.of("HP:0005403");
    public static final TermId GROWTH_DELAY = TermId.of("HP:0001510");

    private TestData() {
    }

    public static Path resource(String name) {
        try {
            return Path.of(TestData.class.getResource(name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static HpoaFile smallHpoa() {
        return HpoaFile.parse(resource("/small_phenotype.hpoa"));
    }

    public static Ontology smallOntology() {
        return OntologyLoader.loadOntology(resource("/small_hp.json").toFile());
    }

    public static PhenopacketCase apdsCase(String id, String pmid, TermId... terms) {
        return new PhenopacketCase(id, APDS, "Immunodeficiency 14", TermId.of(pmid), Set.of(terms), Path.of(id + ".json"));
    }
}
