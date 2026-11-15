#!/usr/bin/env python3
"""
MT103 to ISO 20022 Batch Sample Verification Runner
Processes all synthetic MT103 test samples against the running /convert API
and reports each conversion outcome and operational risk verdict.
"""

import sys
import os
import glob
import urllib.request
import urllib.error
import json
from pathlib import Path

DEFAULT_BASE_URL = "http://localhost:8080"

def get_samples_dir():
    # Attempt relative to script directory first, then current working directory
    script_dir = Path(__file__).resolve().parent
    repo_root = script_dir.parent
    sample_dir = repo_root / "src" / "test" / "resources" / "samples" / "mt103"
    if sample_dir.is_dir():
        return sample_dir
    # Fallback to local cwd
    local_dir = Path("src/test/resources/samples/mt103")
    if local_dir.is_dir():
        return local_dir
    raise FileNotFoundError("Could not locate samples directory at src/test/resources/samples/mt103")

def run_conversion(base_url, file_path):
    url = f"{base_url.rstrip('/')}/convert"
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    req = urllib.request.Request(
        url,
        data=content.encode("utf-8"),
        headers={"Content-Type": "text/plain; charset=utf-8"},
        method="POST"
    )

    try:
        with urllib.request.urlopen(req) as resp:
            status_code = resp.getcode()
            body = resp.read().decode("utf-8")
            data = json.loads(body)
            report = data.get("report", {})
            verdict = report.get("verdict", "UNKNOWN")
            rule = report.get("verdictRule", "")
            status = data.get("status", "SUCCESS")
            return status_code, status, verdict, rule
    except urllib.error.HTTPError as e:
        status_code = e.code
        body = e.read().decode("utf-8")
        try:
            data = json.loads(body)
            error_status = data.get("status", "ERROR")
            msg = data.get("message", "Validation/Parse failure")
            tag = data.get("tag")
            detail = f"Tag {tag}: {msg}" if tag else msg
            return status_code, error_status, "PARSE_ERROR" if status_code == 400 else "ERROR", detail
        except Exception:
            return status_code, "ERROR", "HTTP_ERROR", str(e)
    except urllib.error.URLError as e:
        return None, "CONNECTION_FAILED", "OFFLINE", str(e.reason)

def main():
    base_url = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_BASE_URL
    print(f"================================================================================")
    print(f" MT103 to ISO 20022 Mapper - Synthetic Batch Sample Verification")
    print(f" Target Endpoint: {base_url}/convert")
    print(f"================================================================================\n")

    try:
        samples_dir = get_samples_dir()
    except FileNotFoundError as e:
        print(f"Error: {e}")
        sys.exit(1)

    sample_files = sorted(glob.glob(str(samples_dir / "*.txt")))
    if not sample_files:
        print(f"No sample files found in {samples_dir}")
        sys.exit(1)

    results = []
    verdict_counts = {}

    print(f"{'Sample File':<32} | {'HTTP':<5} | {'Verdict':<12} | {'Summary / Decision Rule'}")
    print(f"{'-'*32}-|-{'-'*5}-|-{'-'*12}-|-{'-'*45}")

    for file_path in sample_files:
        filename = os.path.basename(file_path)
        status_code, status, verdict, detail = run_conversion(base_url, file_path)

        if status_code is None:
            print(f"\n[ERROR] Could not connect to API server at {base_url}.")
            print("Please ensure the service is running via:")
            print("  - Docker:  docker compose up -d")
            print("  - Local:   mvn spring-boot:run\n")
            sys.exit(2)

        verdict_counts[verdict] = verdict_counts.get(verdict, 0) + 1
        results.append((filename, status_code, verdict, detail))

        # Truncate detail for tabular presentation if necessary
        clean_detail = detail.split("\n")[0]
        if len(clean_detail) > 65:
            clean_detail = clean_detail[:62] + "..."

        print(f"{filename:<32} | {status_code:<5} | {verdict:<12} | {clean_detail}")

    print(f"\n{'-'*80}")
    print(" SUMMARY OF OPERATIONAL VERDICTS")
    print(f"{'-'*80}")
    print(f" Total Samples Evaluated: {len(sample_files)}")
    for verdict, count in sorted(verdict_counts.items()):
        print(f"  - {verdict:<14}: {count}")
    print(f"{'='*80}\n")

if __name__ == "__main__":
    main()

