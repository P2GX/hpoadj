# hpoadj

[![build](https://github.com/P2GX/hpoadj/actions/workflows/build.yml/badge.svg)](https://github.com/P2GX/hpoadj/actions/workflows/build.yml)
[![GitHub release](https://img.shields.io/github/v/release/P2GX/hpoadj?include_prereleases)](https://github.com/P2GX/hpoadj/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![HPO](https://img.shields.io/badge/HPO-Human%20Phenotype%20Ontology-purple.svg)](https://hpo.jax.org/)
[![GA4GH](https://img.shields.io/badge/GA4GH-Phenopackets%20v2-orange.svg)](https://phenopacket-schema.readthedocs.io)
[![LIRICAL](https://img.shields.io/badge/benchmarks-LIRICAL-lightgrey.svg)](https://github.com/TheJacksonLaboratory/LIRICAL)
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.24054540.svg)](https://doi.org/10.5281/zenodo.24054540)

Adjusts HPO annotation files (`phenotype.hpoa`) for phenopacket-based benchmarking of
phenotype-driven diagnostic tools such as [LIRICAL](https://github.com/TheJacksonLaboratory/LIRICAL).

When a cohort of curated [GA4GH phenopackets](https://phenopacket-schema.readthedocs.io) is used
to benchmark a tool, the annotations of the diseases in the cohort should reflect the curation
(`augment`), and the publication a benchmarked case comes from must not contribute to the
annotations the tool sees for that case (`loo`, leave one publication out).

hpoadj is a Java 21 library (`hpoadj-core`) with a command line interface (`hpoadj-cli`), built
on [phenol](https://github.com/monarch-initiative/phenol).

## Build

```
mvn package
```

produces the runnable `hpoadj-cli/target/hpoadj-0.0.2.jar`. Pre-built jars are attached to the
[releases](https://github.com/P2GX/hpoadj/releases).

## Usage

Both commands take the same inputs: an HPOA file, the `hp.json` release in which the phenopacket
terms are defined (used for ancestor-aware cohort counting) and one phenopacket JSON file or a
directory of phenopackets. Phenopackets without a `PMID:` external reference or without a disease
diagnosis are skipped with a warning; a phenopacket term that is not in the ontology is counted
without its ancestors and reported once.

### `augment`

```
java -jar hpoadj-0.0.2.jar augment \
    -a phenotype.hpoa --hpo hp.json -p cohorts/ -o hpoa-augmented/ \
    [--biocuration HPO:mytag] [--date 2026-11-08]
```

For every disease in the cohort, one phenotype annotation line is written per **directly
observed** HPO term. Its frequency is `n/m`, where `n` counts the cohort individuals carrying that
term or any of its descendants (true-path rule) and `m` is the cohort size, pooled over all cohort
publications; every cohort PMID is listed in the reference field. Ancestor terms that are not
themselves observed receive no line; their counts are implied by the ontology. Excluded
(negated) phenopacket features are ignored: a `NOT` annotation asserts absence in the disease as a
whole, which cannot be inferred from exclusion in some individuals.

Pre-existing **phenotype** annotations (aspect `P`) of the same disease are

* superseded (dropped) when they cite a publication the cohort re-curates,
* pooled into the cohort line when the term is observed in the cohort and the line carries a
  count `n/m` or no frequency at all (read as `1/1`, as phenol does for an empty frequency field),
  with all such lines of a term pooled and their PMIDs appended to the reference field,
* kept unchanged otherwise. A `NOT` annotation, a percentage or an HPO frequency term for an
  observed term is kept beside the cohort line and reported as a warning; phenol sums the counts
  of several lines for one term, converting percentages and frequency terms with an assumed
  cohort size of 5.

Onset (aspect `C`) and inheritance (aspect `I`) annotations are never changed. The denominator is
the cohort size because phenopackets record observed and excluded features but not which features
were assessed; features that are not mentioned therefore count as absent.

Generated lines carry `<biocuration tag>[<date>]` in the biocuration field (default tag
`HPO:hpoadj`, date defaults to today; fix `--date` for byte-identical reruns). Two header lines
record the provenance: `#cohort-augmented:` lists the augmented diseases and
`#cohort-biocuration:` the biocuration value of the generated lines, which `loo` uses to recognise
them.

Output: `phenotype_augmented.hpoa` and `augmentation_summary.tsv` with, per disease, the cohort
size, the cohort PMIDs and the number of lines added, pooled, superseded and retained beside a
cohort line.

### `loo`

```
java -jar hpoadj-0.0.2.jar loo \
    -a hpoa-augmented/phenotype_augmented.hpoa --hpo hp.json -p cohorts/ -o hpoa-loo/ \
    [--multi-reference subtract|drop|keep]
```

For every publication the phenopackets come from, a copy of the HPOA is written in which that
publication's contribution is removed. A line whose only reference is the publication is dropped.
A line that cites it together with other references is handled according to `--multi-reference`:

* `subtract` (default): if the line was pooled by hpoadj (its biocuration matches the
  `#cohort-biocuration:` header) and the publication's cohort counts are known, the counts are
  subtracted from numerator and denominator and the PMID is removed from the reference field; a
  count that reaches zero drops the line as sole support. Any other multi-reference line, in
  particular a curated line citing several publications, cannot be decomposed and is dropped.
* `drop`: every line citing the publication is dropped.
* `keep`: multi-reference lines are kept unchanged.

The subtraction is exact: leaving every cohort publication out again restores the released counts
and references of a pooled line (see `AugmentLeaveOneOutRoundTripTest`).

Output: one `phenotype_PMID_<id>.hpoa` per publication that is cited in the HPOA, and
`adjustment_summary.tsv` with one row per phenopacket:

| column | meaning |
| --- | --- |
| `status` | `ADJUSTED`, `NO_PMID_EVIDENCE` (publication not cited, the unchanged HPOA applies) or `INSUFFICIENT_DATA` (no phenotype annotation of the disease is left) |
| `lines_removed_disease`, `lines_subtracted_disease` | lines of the case's disease dropped or reduced |
| `lines_removed_total`, `lines_subtracted_total` | the same over the whole file |
| `sole_support_removed_total` | dropped lines supported by the publication alone (only reference, or pooled count reaching zero) |
| `undecomposable_removed_total` | dropped multi-reference lines that could not be decomposed (or all of them with `--multi-reference drop`) |
| `remaining_phenotype_lines` | phenotype annotations of the disease left in the adjusted file |
| `adjusted_hpoa` | path of the adjusted file, `-` if the publication is not cited |

`loo` accepts a released HPOA as well; with the default policy every multi-reference line citing
the publication is then dropped, since none was pooled by hpoadj.

## Library

```java
Ontology hpo = OntologyLoader.loadOntology(new File("hp.json"));
PhenopacketCohort cohort = PhenopacketCohort.load(Path.of("cohorts"), OntologyAncestors.withSelf(hpo));
HpoaFile hpoa = HpoaFile.parse(Path.of("phenotype.hpoa"));

AugmentedHpoa augmented = new HpoaAugmenter(cohort, "HPO:mytag[2026-11-08]").augment(hpoa);

LeaveOneOutAdjuster adjuster = new LeaveOneOutAdjuster(augmented.hpoa(), cohort);
PublicationAdjustment leftOut = adjuster.adjustFor(TermId.of("PMID:24136356"));
leftOut.adjusted().write(Path.of("phenotype_PMID_24136356.hpoa"));
```

`adjustFor` computes each publication once and keeps the result in memory, so a benchmark over
many phenopackets can hold one adjuster and look up the HPOA for each case's PMID without writing
files. `CohortSource` abstracts where per-publication counts come from, so a cohort that is not
available as phenopackets can be plugged into `LeaveOneOutAdjuster` as well; without a cohort
source (`new LeaveOneOutAdjuster(hpoa)`) hpoadj filters a released HPOA by PMID with the chosen
`MultiReferencePolicy`.

## Citing

See `CITATION.cff`. hpoadj was developed for the ESID4HPO evaluation of expert HPO curation in
inborn errors of immunity; the analysis that uses it is in
[esid4hpo-analysis](https://github.com/P2GX/esid4hpo-analysis).

## License

Apache License 2.0
