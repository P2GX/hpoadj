package org.p2gx.hpoadj.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainTest {

    private static Path resource(String name) throws Exception {
        return Path.of(MainTest.class.getResource(name).toURI());
    }

    @Test
    public void augmentThenLeaveOneOut(@TempDir Path tempDir) throws Exception {
        Path augmentDir = tempDir.resolve("augment");
        int exit = Main.run("augment",
                "-a", resource("/small_phenotype.hpoa").toString(),
                "--hpo", resource("/small_hp.json").toString(),
                "-p", resource("/phenopackets").toString(),
                "-o", augmentDir.toString(),
                "--biocuration", "HPO:test");
        assertEquals(0, exit);

        Path augmented = augmentDir.resolve(AugmentCommand.OUTPUT_FILE);
        List<String> lines = Files.readAllLines(augmented);
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("#cohort-augmented: OMIM:615513,OMIM:616576")));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("OMIM:615513\tImmunodeficiency 14\t\tHP:0005403\tPMID:24136356\tPCS\t\t1/1")
                && line.contains("HPO:test[")));
        assertTrue(lines.stream().noneMatch(line -> line.contains("HP:0002205")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("HP:0005425\tPMID:24165795")));
        List<String> summary = Files.readAllLines(augmentDir.resolve("augmentation_summary.tsv"));
        assertEquals("OMIM:615513\t1\tPMID:24136356\t1\t0\t2", summary.get(1));
        assertEquals("OMIM:616576\t1\tPMID:29403474\t1\t0\t1", summary.get(2));

        Path looDir = tempDir.resolve("loo");
        exit = Main.run("loo",
                "-a", augmented.toString(),
                "--hpo", resource("/small_hp.json").toString(),
                "-p", resource("/phenopackets").toString(),
                "-o", looDir.toString());
        assertEquals(0, exit);

        assertTrue(Files.isRegularFile(looDir.resolve("phenotype_PMID_24136356.hpoa")));
        assertTrue(Files.isRegularFile(looDir.resolve("phenotype_PMID_29403474.hpoa")));
        List<String> adjustment = Files.readAllLines(looDir.resolve("adjustment_summary.tsv"));
        assertEquals(3, adjustment.size());
        assertTrue(adjustment.get(1).startsWith("PMID_24136356_P10\tPMID:24136356\tOMIM:615513\tADJUSTED\t2\t0\t2\t0\t0\t1\t"));
        assertTrue(adjustment.get(2).startsWith("PMID_29403474_Fam089_I1\tPMID:29403474\tOMIM:616576\tINSUFFICIENT_DATA\t2\t0\t2\t0\t0\t0\t"));
    }

    @Test
    public void missingInputFails(@TempDir Path tempDir) throws Exception {
        int exit = Main.run("loo",
                "-a", tempDir.resolve("missing.hpoa").toString(),
                "--hpo", resource("/small_hp.json").toString(),
                "-p", resource("/phenopackets").toString());
        assertEquals(1, exit);
    }
}
