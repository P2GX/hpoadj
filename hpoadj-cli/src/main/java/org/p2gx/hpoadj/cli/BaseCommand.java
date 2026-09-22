package org.p2gx.hpoadj.cli;

import org.monarchinitiative.phenol.io.OntologyLoader;
import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.p2gx.hpoadj.HpoadjException;
import org.p2gx.hpoadj.cohort.OntologyAncestors;
import org.p2gx.hpoadj.cohort.PhenopacketCohort;
import org.p2gx.hpoadj.hpoa.HpoaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

abstract class BaseCommand implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseCommand.class);

    @CommandLine.Option(names = {"-a", "--hpoa"},
            required = true,
            description = "path to phenotype.hpoa")
    Path hpoaPath;

    @CommandLine.Option(names = {"--hpo"},
            required = true,
            description = "path to hp.json (used for ancestor-aware cohort counts)")
    Path hpoPath;

    @CommandLine.Option(names = {"-p", "--phenopackets"},
            required = true,
            description = "phenopacket JSON file or directory of phenopackets")
    Path phenopacketPath;

    @CommandLine.Option(names = {"-o", "--outdir"},
            description = "output directory (default: ${DEFAULT-VALUE})")
    Path outputDirectory = Path.of("hpoadj-out");

    @Override
    public Integer call() {
        try {
            requireFile(hpoaPath);
            requireFile(hpoPath);
            Ontology ontology = OntologyLoader.loadOntology(hpoPath.toFile());
            PhenopacketCohort cohort = PhenopacketCohort.load(phenopacketPath, OntologyAncestors.withSelf(ontology));
            if (cohort.isEmpty()) {
                LOGGER.error("No usable phenopackets found at {}", phenopacketPath);
                return 1;
            }
            HpoaFile hpoa = HpoaFile.parse(hpoaPath);
            LOGGER.info("Parsed {} annotation lines from {}", hpoa.annotationCount(), hpoaPath);
            Files.createDirectories(outputDirectory);
            return execute(hpoa, cohort);
        } catch (HpoadjException | IOException e) {
            LOGGER.error(e.getMessage());
            return 1;
        }
    }

    abstract int execute(HpoaFile hpoa, PhenopacketCohort cohort);

    private static void requireFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new HpoadjException("Not a file: " + path);
        }
    }
}
