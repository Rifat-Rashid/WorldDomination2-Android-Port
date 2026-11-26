"""Integrity checks shared by the reviewed exporter and training entry points."""
from contextlib import ExitStack
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
        checksum = hashlib.sha256()
        count = 0
        with (directory / (split + ".jsonl")).open("rb") as data:
            for line in data:
                checksum.update(line)
                count += 1
        if (report["counts"].get(split) != target or count != target
                or checksum.hexdigest() != report["sha256"].get(split)):
            raise ValueError(f"Reviewed {split} split does not pass its count and checksum gates")
    if report.get("complete") is not True:
        raise ValueError("A complete reviewed export is required")
    return report


def frozen_decisions(directory):
    report = verify_dataset(directory)
    by_split = {split: [] for split in expected_counts(report)}
    with ExitStack() as resources:
        records = {split: resources.enter_context((directory / (split + ".jsonl")).open("rb"))
                   for split in by_split}
        provenance = resources.enter_context((directory / "provenance.jsonl").open(encoding="utf8"))
        for line in provenance:
            row = json.loads(line)
            split = row["split"]
            rows = by_split[split]
            if row["row"] != len(rows) + 1 or report["families"][row["family"]] != split:
                raise ValueError("Parent provenance does not match its split or row number")
            message = records[split].readline()
            if not message or hashlib.sha256(message.rstrip(b"\r\n")).hexdigest() != row["row_sha256"]:
                raise ValueError("Parent provenance does not match the frozen messages")
            rows.append(row)
        if any(stream.readline() for stream in records.values()):
            raise ValueError("Parent provenance does not cover every frozen message")
    return report, by_split
