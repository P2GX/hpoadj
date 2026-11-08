# Changelog

All notable changes to hpoadj are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.0.2] - 2026-11-08

### Changed

- `loo` reports dropped lines in two classes: `sole_support_removed_total` (the publication is the
  only reference, or a pooled count reaches zero without it) and `undecomposable_removed_total`
  (multi-reference lines that could not be decomposed). The former `undecomposable_lines_removed`
  column counted sole-support lines as undecomposable.
- `loo` subtracts cohort counts only from lines that hpoadj pooled, recognised by the new
  `#cohort-biocuration:` header; curated multi-reference lines are dropped instead of being
  decomposed with a count base they were not built from.
- `augment` pools every poolable pre-existing line of a term, not only the first one.
- `augment` no longer pools a `NOT` annotation as `0/1`; negated, percentage and frequency-term
  lines for an observed term are kept beside the cohort line, counted in the new
  `annotations_retained_beside_cohort` column and reported as warnings.
- A phenopacket term unknown to the ontology is reported once instead of being silently counted
  without ancestors.

### Added

- `augment --date` fixes the date in the biocuration tag for reproducible output.
- `loo --multi-reference subtract|drop|keep` for lines citing the publication together with other
  references; `drop` and `keep` make hpoadj usable as a plain PMID filter on a released HPOA.
- `LeaveOneOutAdjuster.adjustFor` caches one adjustment per publication for in-memory use.
- Round-trip test: leaving every cohort publication out again restores the released counts.
- `CITATION.cff`, `.zenodo.json` and a release workflow that builds the jar on `v*` tags.

## [0.0.1] - 2026-09-22

Initial release: `augment` and `loo` as used in the ESID4HPO analysis.
