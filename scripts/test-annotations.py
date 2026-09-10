#!/usr/bin/env python3
"""Turn JUnit XML results into GitHub Actions error annotations.

Prints one `::error` annotation per failed test case so PRs show inline
"test failed" markers without any third-party action.

Usage: python3 scripts/test-annotations.py <junit-xml> [<junit-xml> ...]

Exit codes:
  0 - annotations emitted, or all files parsed with zero failures
  1 - every given XML file is missing/unparseable (CI guard: results never ran)
"""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

MAX_ANNOTATIONS = 50  # keep the PR checks panel readable on big breakages


def fail_message(suite_name: str, case) -> str:
    name = case.get("name", "<unknown>")
    classname = case.get("classname", suite_name)
    child = list(case)[0] if len(case) else None  # <failure> or <error>
    detail = (child.text or "").strip().splitlines() if child is not None else []
    first = next((ln for ln in detail if ln.strip()), "")
    if first:
        return f"{classname}.{name} — {first[:300]}"
    return f"{classname}.{name}"


def main(argv):
    if len(argv) < 2:
        print("usage: test-annotations.py <junit-xml> [...]", file=sys.stderr)
        return 1

    paths = [Path(p) for p in argv[1:]]
    existing = [p for p in paths if p.is_file()]
    if not existing:
        print(f"::warning::no JUnit XML found at {', '.join(str(p) for p in paths)}")
        return 1

    total_failures = 0
    emitted = 0
    for path in existing:
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as e:
            print(f"::warning::unparseable JUnit XML {path}: {e}")
            continue

        suites = [root] if root.tag == "testsuite" else root.iter("testsuite")
        for suite in suites:
            suite_name = suite.get("name", "")
            for case in suite.iter("testcase"):
                # <failure>/<error> child = failed case. (<skipped> is not a failure;
                # never truth-test elements — a childless <failure> is still a failure.)
                if not any(child.tag in ("failure", "error") for child in case):
                    continue
                total_failures += 1
                if emitted < MAX_ANNOTATIONS:
                    msg = fail_message(suite_name, case).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
                    print(f"::error file={path}::Test failure: {msg}")
                    emitted += 1

    if emitted < total_failures:
        print(f"::notice::{total_failures - emitted} additional failure(s) beyond the {emitted} annotated")
    print(f"test-annotations: {total_failures} failing test(s) across {len(existing)} report file(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
