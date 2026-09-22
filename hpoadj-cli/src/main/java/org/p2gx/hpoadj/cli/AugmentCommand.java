package org.p2gx.hpoadj.cli;

import org.p2gx.hpoadj.augment.AugmentedHpoa;
import org.p2gx.hpoadj.augment.HpoaAugmenter;
import org.p2gx.hpoadj.cohort.PhenopacketCohort;
import org.p2gx.hpoadj.hpoa.HpoaFile;
import org.p2gx.hpoadj.report.SummaryWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.LocalDate;

@CommandLine.Command(name = "augment",
        mixinStandardHelpOptions = true,
        description = "Add the annotations of a phenopacket cohort to an HPOA file, one line per disease "
                + "and observed term with the frequency pooled over all cohort publications.")
public class AugmentCommand extends BaseCommand {

    public static final String OUTPUT_FILE = "phenotype_augmented.hpoa";

    private static final Logger LOGGER = LoggerFactory.getLogger(AugmentCommand.class);

    @CommandLine.Option(names = {"--biocuration"},
            description = "biocuration tag written to generated annotations, today's date is appended "
                    + "(default: ${DEFAULT-VALUE})")
    String biocurationTag = "HPO:hpoadj";

    @Override
    int execute(HpoaFile hpoa, PhenopacketCohort cohort) {
        String biocuration = biocurationTag + "[" + LocalDate.now() + "]";
        AugmentedHpoa augmented = new HpoaAugmenter(cohort, biocuration).augment(hpoa);
        Path output = outputDirectory.resolve(OUTPUT_FILE);
        augmented.hpoa().write(output);
        Path summary = outputDirectory.resolve(SummaryWriter.AUGMENTATION_SUMMARY);
        SummaryWriter.writeAugmentationSummary(augmented.augmentations(), summary);
        LOGGER.info("Wrote augmented HPOA with {} annotation lines to {}", augmented.hpoa().annotationCount(), output);
        LOGGER.info("Wrote augmentation summary to {}", summary);
        return 0;
    }
}
