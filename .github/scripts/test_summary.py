#!/usr/bin/env python3
"""Prints how many tests ran and passed, from the JUnit XML files Gradle leaves behind.

Usage: test_summary.py GLOB [GLOB ...]

Exits 1 if no results are found, so a job cannot go green by running no tests at all.
"""
import glob
import sys
import xml.etree.ElementTree as ET

total = failed = skipped = 0
suites = []
for pattern in sys.argv[1:]:
    for path in sorted(glob.glob(pattern, recursive=True)):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for suite in [root] if root.tag == "testsuite" else root.iter("testsuite"):
            tests = int(suite.get("tests", 0))
            if tests == 0:
                continue
            bad = int(suite.get("failures", 0)) + int(suite.get("errors", 0))
            total += tests
            failed += bad
            skipped += int(suite.get("skipped", 0))
            suites.append((suite.get("name", path).rsplit(".", 1)[-1], tests, bad))

for name, tests, bad in suites:
    print(f"  {'FAIL' if bad else 'ok  '} {name}: {tests} tests" + (f", {bad} failed" if bad else ""))
print(f"{total} tests, {failed} failed, {skipped} skipped, in {len(suites)} classes.")
sys.exit(1 if total == 0 or failed else 0)
