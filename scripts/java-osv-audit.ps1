$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$baselinePath = Join-Path $repo "security\osv-baseline.txt"

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
    throw "Java 8 was not found. Set JAVA_HOME_8."
}

$baseline = if (Test-Path $baselinePath) {
    Get-Content $baselinePath | Where-Object { $_ -and -not $_.StartsWith("#") }
} else {
    @()
}

$env:JAVA_HOME = Find-Java8
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

Push-Location (Join-Path $repo "platform")
try {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $lines = & .\gradlew.bat --no-daemon --quiet runtimeDependencies 2>&1
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle dependency inventory failed: $($lines -join "`n")"
    }
} finally {
    Pop-Location
}

$coordinates = @($lines | ForEach-Object {
    $text = [string]$_
    if ($text -match "^([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([^:\s]+)$") {
        [pscustomobject]@{
            Coordinate = $Matches[0]
            Name = "$($Matches[1]):$($Matches[2])"
            Version = $Matches[3]
        }
    }
}) | Sort-Object Coordinate -Unique

if ($coordinates.Count -eq 0) {
    throw "No runtime dependencies were discovered."
}

$queries = @($coordinates | ForEach-Object {
    @{
        package = @{ ecosystem = "Maven"; name = $_.Name }
        version = $_.Version
    }
})
$body = @{ queries = $queries } | ConvertTo-Json -Depth 6 -Compress
$response = Invoke-RestMethod -Method Post -Uri "https://api.osv.dev/v1/querybatch" `
    -ContentType "application/json" -Body $body -TimeoutSec 60

$known = New-Object System.Collections.Generic.List[string]
$new = New-Object System.Collections.Generic.List[string]
for ($index = 0; $index -lt $coordinates.Count; $index++) {
    $coordinate = $coordinates[$index]
    $advisories = @($response.results[$index].vulns | Where-Object { $_ -and $_.id })
    foreach ($advisory in $advisories) {
        $line = "$($coordinate.Coordinate) $($advisory.id)"
        if ($baseline -contains $line) {
            $known.Add($line)
        } else {
            $new.Add($line)
        }
    }
}

Write-Host "Java runtime dependencies scanned: $($coordinates.Count)"
Write-Host "Reviewed baseline advisories: $($known.Count)"
foreach ($line in $known) {
    Write-Host "BASELINE $line"
}
if ($new.Count -gt 0) {
    foreach ($line in $new) {
        Write-Host "NEW $line"
    }
    throw "Java dependency audit found $($new.Count) unreviewed advisories."
}
Write-Host "java-osv-audit ok"
