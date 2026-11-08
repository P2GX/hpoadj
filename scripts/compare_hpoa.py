#!/usr/bin/env python3
"""Compare the annotation content of two HPOA output trees.

Pairs every *.hpoa file below the two directories by relative path and compares the
annotation lines as a multiset, ignoring header lines, line order and the biocuration field
(which carries the run date). Exit status 1 if any pair differs or a file is missing.

    python compare_hpoa.py hpoa_adjusted_v001 hpoa_adjusted
"""

import sys
from collections import Counter
from pathlib import Path

BIOCURATION = 11


def annotations(path: Path) -> Counter:
    counts = Counter()
    with open(path) as handle:
        for line in handle:
            if line.startswith("#") or line.startswith("database_id") or not line.strip():
                continue
            fields = line.rstrip("\n").split("\t")
            fields[BIOCURATION] = ""
            counts["\t".join(fields)] += 1
    return counts


def hpoa_files(root: Path) -> dict:
    return {path.relative_to(root): path for path in root.rglob("*.hpoa")}


def main(left: Path, right: Path) -> int:
    left_files, right_files = hpoa_files(left), hpoa_files(right)
    status = 0
    for relative in sorted(set(left_files) | set(right_files)):
        if relative not in left_files or relative not in right_files:
            print(f"MISSING  {relative} (only in {'left' if relative in left_files else 'right'})")
            status = 1
            continue
        a, b = annotations(left_files[relative]), annotations(right_files[relative])
        if a == b:
            print(f"same     {relative} ({sum(a.values())} lines)")
        else:
            only_a, only_b = a - b, b - a
            print(f"DIFFERS  {relative}: {sum(only_a.values())} lines only left, {sum(only_b.values())} only right")
            for line in list(only_a)[:3]:
                print(f"  < {line}")
            for line in list(only_b)[:3]:
                print(f"  > {line}")
            status = 1
    return status


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    sys.exit(main(Path(sys.argv[1]), Path(sys.argv[2])))
