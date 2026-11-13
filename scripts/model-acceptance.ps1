$ErrorActionPreference = "Stop"

function Fail-Closed([string]$reason) {
    Write-Host "P0-01 remains open: $reason"
    Write-Host "No acceptance report was written. Mock results are not model metrics."
    exit 2
}

$base = $env:TRACEWITNESS_MODEL_BASE_URL
$name = $env:TRACEWITNESS_MODEL_NAME
$key = $env:TRACEWITNESS_MODEL_API_KEY
$allowEmpty = $env:TRACEWITNESS_MODEL_ALLOW_EMPTY_KEY -eq "1"

if (-not $base -or -not $name -or ((-not $key) -and -not $allowEmpty)) {
    Fail-Closed "TRACEWITNESS_MODEL_BASE_URL, TRACEWITNESS_MODEL_NAME, and TRACEWITNESS_MODEL_API_KEY are required. An empty key is accepted only when TRACEWITNESS_MODEL_ALLOW_EMPTY_KEY=1."
}

try {
    $uri = [Uri]$base
} catch {
    Fail-Closed "model base URL is not a valid URL."
}

if ($uri.Scheme -ne "http" -and $uri.Scheme -ne "https") {
    Fail-Closed "model base URL must use http or https."
}

Fail-Closed "configuration is present, but the fixed 30 positive and 20 negative task run has not been executed. This script does not mark the model gate as passed."
