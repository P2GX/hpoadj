# hpoadj

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

produces the runnable `hpoadj-cli/target/hpoadj-0.0.1.jar`.

## Usage

Both commands take the same inputs: an HPOA file, the matching `hp.json` release (used for
ancestor-aware cohort counting) and one phenopacket JSON file or a directory of phenopackets.
Phenopackets without a `PMID:` external reference or without a disease diagnosis are skipped
with a warning.

### `augment`

```
java -jar hpoadj-0.0.1.jar augment \
    -a phenotype.hpoa --hpo hp.json -p cohorts/ -o hpoa-augmented/ [--biocuration HPO:mytag]
```

For every disease in the cohort, one annotation line per observed HPO term is written with the
frequency `n/m` pooled over all cohort individuals, counting an individual once for every
ancestor of every term it carries, and every cohort PMID in the reference field. Existing
annotations of the same disease are

* superseded (dropped) when they cite a publication the cohort re-curates,
* pooled into the cohort line when they carry a count `n/m` or no frequency at all (read as
  `1/1`, or `0/1` for a `NOT` annotation) and the term is observed in the cohort,
* kept unchanged otherwise.

Output: `phenotype_augmented.hpoa` (with a `#cohort-augmented:` header line) and
`augmentation_summary.tsv` with the per-disease counts of added, pooled and superseded lines.

### `loo`

```
java -jar hpoadj-0.0.1.jar loo \
    -a hpoa-augmented/phenotype_augmented.hpoa --hpo hp.json -p cohorts/ -o hpoa-loo/
```

For every publication the phenopackets come from, a copy of the HPOA is written in which that
publication's contribution is removed: a line citing only that PMID is dropped; a line with
several references has the publication's cohort counts subtracted from its `n/m` frequency when
the cohort is known and the result is still informative, and is dropped otherwise.

Output: one `phenotype_PMID_<id>.hpoa` per publication that is cited in the HPOA, and
`adjustment_summary.tsv` with one row per phenopacket. The `status` column is `ADJUSTED`,
`NO_PMID_EVIDENCE` (the publication is not cited, the unchanged HPOA can be used) or
`INSUFFICIENT_DATA` (no phenotype annotation of the disease is left).

`loo` accepts the stock HPOA as well; the augmented file is simply the usual input when both
steps are combined.

## Library

```java
Ontology hpo = OntologyLoader.loadOntology(new File("hp.json"));
PhenopacketCohort cohort = PhenopacketCohort.load(Path.of("cohorts"), OntologyAncestors.withSelf(hpo));
HpoaFile hpoa = HpoaFile.parse(Path.of("phenotype.hpoa"));

AugmentedHpoa augmented = new HpoaAugmenter(cohort, "HPO:mytag[2026-09-21]").augment(hpoa);
PublicationAdjustment leftOut = new LeaveOneOutAdjuster(augmented.hpoa(), cohort).adjustFor(TermId.of("PMID:24136356"));
leftOut.adjusted().write(Path.of("phenotype_PMID_24136356.hpoa"));
```

`CohortSource` abstracts where per-publication counts come from, so a cohort that is not
available as phenopackets can be plugged into `LeaveOneOutAdjuster` as well.

## License

Apache License 2.0
