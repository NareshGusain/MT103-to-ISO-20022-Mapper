#!/usr/bin/env bash
# MT103 to ISO 20022 Batch Sample Verification Runner (Bash / curl)

set -e

BASE_URL="${1:-http://localhost:8080}"
SAMPLES_DIR="$(dirname "$0")/../src/test/resources/samples/mt103"

echo "================================================================================"
echo " MT103 to ISO 20022 Mapper - Synthetic Batch Sample Verification"
echo " Target Endpoint: ${BASE_URL}/convert"
echo "================================================================================"
echo ""

if [ ! -d "$SAMPLES_DIR" ]; then
    echo "Error: Samples directory not found at $SAMPLES_DIR"
    exit 1
fi

printf "%-32s | %-5s | %-12s | %-45s\n" "Sample File" "HTTP" "Verdict" "Summary / Decision Rule"
printf "%s-|-%s-|-%s-|-%s\n" "--------------------------------" "-----" "------------" "---------------------------------------------"

for sample_file in "$SAMPLES_DIR"/*.txt; do
    [ -f "$sample_file" ] || continue
    filename=$(basename "$sample_file")

    http_code=$(curl -s -o /tmp/resp.json -w "%{http_code}" -X POST "${BASE_URL}/convert" \
        -H "Content-Type: text/plain" \
        --data-binary @"$sample_file") || {
        echo "[ERROR] Could not connect to API server at ${BASE_URL}."
        exit 2
    }

    if [ "$http_code" -eq 200 ]; then
        verdict=$(python3 -c "import json; d=json.load(open('/tmp/resp.json')); print(d.get('report', {}).get('verdict', 'UNKNOWN'))" 2>/dev/null || echo "SUCCESS")
        detail=$(python3 -c "import json; d=json.load(open('/tmp/resp.json')); print(d.get('report', {}).get('verdictRule', '')[:60])" 2>/dev/null || echo "Mapped successfully")
    elif [ "$http_code" -eq 400 ]; then
        verdict="PARSE_ERROR"
        detail=$(python3 -c "import json; d=json.load(open('/tmp/resp.json')); print(d.get('message', 'Parse failure')[:60])" 2>/dev/null || echo "Syntax error")
    else
        verdict="HTTP_${http_code}"
        detail="HTTP status error"
    fi

    printf "%-32s | %-5s | %-12s | %-45s\n" "$filename" "$http_code" "$verdict" "$detail"
done

echo ""
echo "Batch run completed."

