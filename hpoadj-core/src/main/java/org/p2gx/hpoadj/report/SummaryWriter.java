package org.p2gx.hpoadj.report;

import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.HpoadjException;
import org.p2gx.hpoadj.augment.DiseaseAugmentation;
import org.p2gx.hpoadj.loo.CaseResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class SummaryWriter {

    public static final String AUGMENTATION_SUMMARY = "augmentation_summary.tsv";
    public static final String ADJUSTMENT_SUMMARY = "adjustment_summary.tsv";

    private SummaryWriter() {
    }

    public static void writeAugmentationSummary(List<DiseaseAugmentation> augmentations, Path path) {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writeRow(writer, "disease_id", "cohort_size", "cohort_pmids",
                    "annotations_added", "annotations_pooled", "annotations_superseded");
            for (DiseaseAugmentation augmentation : augmentations) {
                writeRow(writer,
                        augmentation.diseaseId().getValue(),
                        String.valueOf(augmentation.cohortSize()),
                        augmentation.cohortPmids().stream().map(TermId::getValue).collect(Collectors.joining(";")),
                        String.valueOf(augmentation.linesAdded()),
                        String.valueOf(augmentation.linesPooled()),
                        String.valueOf(augmentation.linesSuperseded()));
            }
        } catch (IOException e) {
            throw new HpoadjException("Could not write " + path, e);
        }
    }

    public static void writeAdjustmentSummary(List<CaseResult> results, Path path) {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writeRow(writer, "phenopacket_id", "pmid", "disease_id", "status",
                    "lines_removed_disease", "lines_subtracted_disease", "lines_removed_total",
                    "lines_subtracted_total", "undecomposable_lines_removed",
                    "remaining_phenotype_lines", "adjusted_hpoa");
            for (CaseResult result : results) {
                writeRow(writer,
                        result.phenopacketCase().phenopacketId(),
                        result.phenopacketCase().pmid().getValue(),
                        result.phenopacketCase().diseaseId().getValue(),
                        result.status().name(),
                        String.valueOf(result.linesRemovedForDisease()),
                        String.valueOf(result.linesSubtractedForDisease()),
                        String.valueOf(result.linesRemovedTotal()),
                        String.valueOf(result.linesSubtractedTotal()),
                        String.valueOf(result.undecomposableLinesRemoved()),
                        String.valueOf(result.remainingPhenotypeLines()),
                        result.adjustedHpoa().map(Path::toString).orElse("-"));
            }
        } catch (IOException e) {
            throw new HpoadjException("Could not write " + path, e);
        }
    }

    private static void writeRow(BufferedWriter writer, String... fields) throws IOException {
        writer.write(String.join("\t", fields));
        writer.newLine();
    }
}
