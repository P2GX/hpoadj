package org.p2gx.hpoadj.cohort;

import org.monarchinitiative.phenol.ontology.data.TermId;
import org.p2gx.hpoadj.HpoadjException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PhenopacketCohort implements CohortSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(PhenopacketCohort.class);

    private final List<PhenopacketCase> cases;
    private final Function<TermId, Set<TermId>> ancestorsWithSelf;
    private final Map<TermId, CohortCounts> countsByDisease = new HashMap<>();
    private final Map<TermId, Map<TermId, Optional<CohortCounts>>> countsByDiseaseAndPmid = new HashMap<>();

    private PhenopacketCohort(List<PhenopacketCase> cases, Function<TermId, Set<TermId>> ancestorsWithSelf) {
        this.cases = List.copyOf(cases);
        this.ancestorsWithSelf = ancestorsWithSelf;
    }

    public static PhenopacketCohort of(List<PhenopacketCase> cases, Function<TermId, Set<TermId>> ancestorsWithSelf) {
        return new PhenopacketCohort(cases, ancestorsWithSelf);
    }

    /** Loads one phenopacket file or every *.json file below a directory; unusable files are skipped. */
    public static PhenopacketCohort load(Path path, Function<TermId, Set<TermId>> ancestorsWithSelf) {
        List<PhenopacketCase> cases = new ArrayList<>();
        int skipped = 0;
        for (Path file : jsonFiles(path)) {
            try {
                cases.add(PhenopacketCase.fromFile(file));
            } catch (HpoadjException e) {
                LOGGER.warn("Skipping {}: {}", file, e.getMessage());
                skipped++;
            }
        }
        LOGGER.info("Loaded {} phenopackets from {}{}", cases.size(), path,
                skipped == 0 ? "" : " (" + skipped + " skipped)");
        return new PhenopacketCohort(cases, ancestorsWithSelf);
    }

    private static List<Path> jsonFiles(Path path) {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        if (!Files.isDirectory(path)) {
            throw new HpoadjException("Not a file or directory: " + path);
        }
        try (Stream<Path> paths = Files.walk(path)) {
            return paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new HpoadjException("Could not list " + path, e);
        }
    }

    public List<PhenopacketCase> cases() {
        return cases;
    }

    public boolean isEmpty() {
        return cases.isEmpty();
    }

    public List<TermId> diseases() {
        return cases.stream()
                .map(PhenopacketCase::diseaseId)
                .distinct()
                .sorted()
                .toList();
    }

    public List<PhenopacketCase> casesOf(TermId diseaseId) {
        return cases.stream()
                .filter(phenopacketCase -> phenopacketCase.diseaseId().equals(diseaseId))
                .toList();
    }

    public Set<TermId> pmidsOf(TermId diseaseId) {
        return casesOf(diseaseId).stream()
                .map(PhenopacketCase::pmid)
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<TermId> observedTermsOf(TermId diseaseId) {
        return casesOf(diseaseId).stream()
                .flatMap(phenopacketCase -> phenopacketCase.observedTerms().stream())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public String diseaseNameOf(TermId diseaseId) {
        return casesOf(diseaseId).stream()
                .map(PhenopacketCase::diseaseName)
                .filter(name -> !name.isEmpty())
                .findFirst()
                .orElse(diseaseId.getValue());
    }

    public CohortCounts countsOf(TermId diseaseId) {
        return countsByDisease.computeIfAbsent(diseaseId, id -> countsFor(casesOf(id)));
    }

    @Override
    public Optional<CohortCounts> lookup(TermId diseaseId, TermId pmid) {
        return countsByDiseaseAndPmid
                .computeIfAbsent(diseaseId, id -> new HashMap<>())
                .computeIfAbsent(pmid, id -> {
                    List<PhenopacketCase> fromPmid = casesOf(diseaseId).stream()
                            .filter(phenopacketCase -> phenopacketCase.pmid().equals(pmid))
                            .toList();
                    return fromPmid.isEmpty() ? Optional.empty() : Optional.of(countsFor(fromPmid));
                });
    }

    private CohortCounts countsFor(List<PhenopacketCase> cases) {
        return CohortCounts.of(cases.stream().map(PhenopacketCase::observedTerms).toList(), ancestorsWithSelf);
    }
}
