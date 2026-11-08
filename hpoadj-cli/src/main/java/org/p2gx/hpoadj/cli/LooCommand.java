package org.p2gx.hpoadj.cli;

import org.p2gx.hpoadj.cohort.PhenopacketCohort;
import org.p2gx.hpoadj.hpoa.HpoaFile;
import org.p2gx.hpoadj.loo.CaseResult;
import org.p2gx.hpoadj.loo.CaseStatus;
import org.p2gx.hpoadj.loo.LeaveOneOutAdjuster;
import org.p2gx.hpoadj.loo.MultiReferencePolicy;
import org.p2gx.hpoadj.report.SummaryWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(name = "loo",
        mixinStandardHelpOptions = true,
        description = "Leave one publication out: for every publication the phenopackets come from, write a copy "
                + "of the HPOA without that publication's contribution.")
public class LooCommand extends BaseCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(LooCommand.class);

    @CommandLine.Option(names = {"--multi-reference"},
            description = "handling of lines that cite the publication together with other references: "
                    + "${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
    MultiReferencePolicy multiReferencePolicy = MultiReferencePolicy.SUBTRACT;

    @Override
    int execute(HpoaFile hpoa, PhenopacketCohort cohort) {
        LeaveOneOutAdjuster adjuster = new LeaveOneOutAdjuster(hpoa, cohort, multiReferencePolicy);
        List<CaseResult> results = adjuster.adjustAll(cohort.cases(), outputDirectory);
        Path summary = outputDirectory.resolve(SummaryWriter.ADJUSTMENT_SUMMARY);
        SummaryWriter.writeAdjustmentSummary(results, summary);
        LOGGER.info("Wrote adjustment summary to {}", summary);
        for (CaseStatus status : CaseStatus.values()) {
            LOGGER.info("{}: {} case(s)", status, results.stream().filter(result -> result.status() == status).count());
        }
        return 0;
    }
}
