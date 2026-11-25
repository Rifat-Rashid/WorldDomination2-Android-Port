"""Integrity checks shared by the reviewed exporter and training entry points."""
import hashlib
import json

ORIGINAL_COUNTS = {"train": 1600, "validation": 200, "test": 200}


def expected_counts(report):
    targets = report.get("target_counts", ORIGINAL_COUNTS)
    if (set(targets) != set(ORIGINAL_COUNTS)
            or targets["validation"] != 200 or targets["test"] != 200
            or type(targets["train"]) is not int or targets["train"] < 1600):
        raise ValueError("Invalid reviewed corpus target counts")
    return targets


def verify_dataset(directory):
    report = json.loads((directory / "report.json").read_text(encoding="utf8"))
    for split, target in expected_counts(report).items():
        data = (directory / (split + ".jsonl")).read_bytes()
        if (report["counts"].get(split) != target or len(data.splitlines()) != target
                or hashlib.sha256(data).hexdigest() != report["sha256"].get(split)):
            raise ValueError(f"Reviewed {split} split does not pass its count and checksum gates")
    if report.get("complete") is not True:
        raise ValueError("A complete reviewed export is required")
    return report


def frozen_decisions(directory):
    report = verify_dataset(directory)
    by_split = {split: [] for split in expected_counts(report)}
    for line in (directory / "provenance.jsonl").read_text(encoding="utf8").splitlines():
        row = json.loads(line)
        rows = by_split[row["split"]]
        if row["row"] != len(rows) + 1 or report["families"][row["family"]] != row["split"]:
            raise ValueError("Parent provenance does not match its split or row number")
        rows.append(row)
    for split, rows in by_split.items():
        records = (directory / (split + ".jsonl")).read_bytes().splitlines()
        if (len(rows) != len(records) or
                any(hashlib.sha256(line).hexdigest() != row["row_sha256"]
                    for row, line in zip(rows, records))):
            raise ValueError("Parent provenance does not match the frozen messages")
    return report, by_split
