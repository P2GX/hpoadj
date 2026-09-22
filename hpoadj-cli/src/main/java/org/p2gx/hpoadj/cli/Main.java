package org.p2gx.hpoadj.cli;

import picocli.CommandLine;

@CommandLine.Command(name = "hpoadj",
        mixinStandardHelpOptions = true,
        versionProvider = Main.VersionProvider.class,
        description = "Adjusts HPO annotation files (phenotype.hpoa) for phenopacket-based benchmarking.",
        subcommands = {AugmentCommand.class, LooCommand.class, CommandLine.HelpCommand.class})
public class Main implements Runnable {

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String... args) {
        return new CommandLine(new Main())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            String version = Main.class.getPackage().getImplementationVersion();
            return new String[]{"hpoadj " + (version == null ? "development" : version)};
        }
    }
}
