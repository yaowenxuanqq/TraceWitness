$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$python = Join-Path $repo "ai-service\.venv\Scripts\python.exe"
$sbomDir = Join-Path $repo "build\reports\sbom"

function Find-Java8 {
    if ($env:JAVA_HOME_8 -and (Test-Path (Join-Path $env:JAVA_HOME_8 "bin\java.exe"))) {
        return $env:JAVA_HOME_8
    }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $version = & (Join-Path $env:JAVA_HOME "bin\java.exe") -version 2>&1
        } finally {
            $ErrorActionPreference = $previousPreference
        }
        if (($version -join "`n") -match "1\.8\.0") {
            return $env:JAVA_HOME
        }
    }
    $adopt = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-8*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($adopt) { return $adopt.FullName }
    $oracle = Get-ChildItem "C:\Program Files\Java" -Directory -Filter "jdk1.8*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($oracle) { return $oracle.FullName }
    throw "Java 8 was not found. Set JAVA_HOME_8."
}

function Assert-Exit([string]$step, [int]$code) {
    if ($code -ne 0) {
        throw "$step failed with exit code $code"
    }
    Write-Host "PASS $step"
}

$env:JAVA_HOME = Find-Java8
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:PYTHONUTF8 = "1"
$env:PYTHONDONTWRITEBYTECODE = "1"

if (-not (Test-Path $python)) {
    Write-Host "Creating Python virtual environment"
    & py -3.13 -m venv (Join-Path $repo "ai-service\.venv")
    Assert-Exit "create Python venv" $LASTEXITCODE
}

& $python -m pip install --disable-pip-version-check -r (Join-Path $repo "ai-service\requirements-dev.txt")
Assert-Exit "install Python dependencies" $LASTEXITCODE

Push-Location (Join-Path $repo "platform")
try {
    & .\gradlew.bat --no-daemon clean check cyclonedxBom
    Assert-Exit "Java build, tests, coverage, SBOM" $LASTEXITCODE
} finally {
    Pop-Location
}

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "java-osv-audit.ps1")
Assert-Exit "Java dependency audit" $LASTEXITCODE

Push-Location (Join-Path $repo "ai-service")
try {
    & $python -m pytest tests --cov=app --cov-report=term --cov-report=xml:"$repo\build\reports\coverage-python.xml" --cov-fail-under=70
    Assert-Exit "Python tests and coverage" $LASTEXITCODE

    & $python -m ruff check app tests
    Assert-Exit "Python lint" $LASTEXITCODE

    & $python -m bandit -r app -q -ll
    Assert-Exit "Python security scan" $LASTEXITCODE

    & $python -m pip_audit -r requirements.txt
    Assert-Exit "Python dependency audit" $LASTEXITCODE
} finally {
    Pop-Location
}

New-Item -ItemType Directory -Path $sbomDir -Force | Out-Null
Push-Location (Join-Path $repo "ai-service")
try {
    & $python -m cyclonedx_py requirements requirements.txt --of JSON --output-reproducible `
        -o (Join-Path $sbomDir "tracewitness-python-sbom.json")
    Assert-Exit "Python SBOM" $LASTEXITCODE
} finally {
    Pop-Location
}

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "http-audit.ps1")
Assert-Exit "HTTP integration audit" $LASTEXITCODE

Write-Host "quality-gate ok"
