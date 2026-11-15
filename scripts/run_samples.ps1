# MT103 to ISO 20022 Batch Sample Verification Runner (PowerShell)
param(
    [string]$BaseUrl = "http://localhost:8080"
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$samplesDir = Join-Path $scriptDir "..\src\test\resources\samples\mt103"

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host " MT103 to ISO 20022 Mapper - Synthetic Batch Sample Verification" -ForegroundColor Cyan
Write-Host " Target Endpoint: $BaseUrl/convert" -ForegroundColor Cyan
Write-Host "================================================================================`n" -ForegroundColor Cyan

if (-not (Test-Path $samplesDir)) {
    Write-Error "Samples directory not found at $samplesDir"
    exit 1
}

$samples = Get-ChildItem -Path $samplesDir -Filter "*.txt" | Sort-Object Name
$counts = @{}

Write-Host ("{0,-32} | {1,-5} | {2,-12} | {3}" -f "Sample File", "HTTP", "Verdict", "Summary / Decision Rule")
Write-Host ("{0} | {1} | {2} | {3}" -f ("-" * 32), ("-" * 5), ("-" * 12), ("-" * 45))

foreach ($file in $samples) {
    $content = Get-Content -Raw -Path $file.FullName
    $statusCode = 0
    $verdict = "UNKNOWN"
    $detail = ""

    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/convert" -Method Post -Body $content -ContentType "text/plain; charset=utf-8"
        $statusCode = 200
        $verdict = $response.report.verdict
        $detail = $response.report.verdictRule
    }
    catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $errBody = $reader.ReadToEnd() | ConvertFrom-Json
                $verdict = if ($statusCode -eq 400) { "PARSE_ERROR" } else { "HTTP_$statusCode" }
                $detail = $errBody.message
            }
            catch {
                $verdict = "HTTP_$statusCode"
                $detail = $_.Exception.Message
            }
        }
        else {
            Write-Host "`n[ERROR] Could not connect to API server at $BaseUrl." -ForegroundColor Red
            Write-Host "Please ensure the service is running (e.g., docker compose up -d or mvn spring-boot:run)`n"
            exit 2
        }
    }

    if (-not $counts.ContainsKey($verdict)) { $counts[$verdict] = 0 }
    $counts[$verdict]++

    $displayDetail = if ($detail.Length -gt 65) { $detail.Substring(0, 62) + "..." } else { $detail }
    
    $color = switch ($verdict) {
        "SAFE" { "Green" }
        "REVIEW" { "Yellow" }
        "BLOCK" { "Red" }
        "PARSE_ERROR" { "Magenta" }
        Default { "White" }
    }

    Write-Host ("{0,-32} | {1,-5} | " -f $file.Name, $statusCode) -NoNewline
    Write-Host ("{0,-12}" -f $verdict) -ForegroundColor $color -NoNewline
    Write-Host (" | {0}" -f $displayDetail)
}

Write-Host "`n--------------------------------------------------------------------------------"
Write-Host " SUMMARY OF OPERATIONAL VERDICTS"
Write-Host "--------------------------------------------------------------------------------"
Write-Host (" Total Samples Evaluated: {0}" -f $samples.Count)
foreach ($key in ($counts.Keys | Sort-Object)) {
    Write-Host ("  - {0,-14}: {1}" -f $key, $counts[$key])
}
Write-Host "================================================================================`n"

