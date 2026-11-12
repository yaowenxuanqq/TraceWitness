$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$aiPort = 18002
$webPort = 18082
$root = "http://127.0.0.1:$webPort"
$aiRoot = "http://127.0.0.1:$aiPort"
$temp = Join-Path $env:TEMP ("tw-browser-audit-" + [guid]::NewGuid().ToString("N"))
$report = Join-Path $repo "build\reports\browser"
New-Item -ItemType Directory -Path $temp, $report -Force | Out-Null
$ai = $null
$platform = $null
$session = "tw-browser-" + [guid]::NewGuid().ToString("N")

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

function Wait-Url([string]$url) {
    for ($i = 0; $i -lt 90; $i++) {
        try {
            Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2 | Out-Null
            return
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    throw "Timed out waiting for $url"
}

function Invoke-Browser {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & agent-browser --session $session @Arguments 2>&1
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($LASTEXITCODE -ne 0) {
        throw "agent-browser $($Arguments -join ' ') failed: $($output -join "`n")"
    }
    return ($output -join "`n").Trim()
}

function Assert-Browser([string]$label, [bool]$ok, [string]$detail = "") {
    if (-not $ok) {
        throw "$label failed :: $detail"
    }
    Write-Host "PASS $label"
}

try {
    if (-not (Get-Command agent-browser -ErrorAction SilentlyContinue)) {
        throw "agent-browser is not installed."
    }
    $python = Join-Path $repo "ai-service\.venv\Scripts\python.exe"
    if (-not (Test-Path $python)) {
        throw "Missing $python. Run the quality gate first."
    }
    $env:JAVA_HOME = Find-Java8
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    $env:TRACEWITNESS_MODE = "MOCK"
    $env:TRACEWITNESS_DB = "jdbc:h2:file:$($temp -replace '\\','/')/tracewitness"
    $env:TRACEWITNESS_AI_BASE_URL = $aiRoot
    $ai = Start-Process -FilePath $python -ArgumentList @("-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "$aiPort") -WorkingDirectory (Join-Path $repo "ai-service") -PassThru -WindowStyle Hidden
    Wait-Url "$aiRoot/health"
    $gradleArgs = "gradlew.bat bootRun --args=`"--server.port=$webPort --tracewitness.media-dir=$($temp -replace '\\','/')/media`""
    $platform = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $gradleArgs) -WorkingDirectory (Join-Path $repo "platform") -PassThru -WindowStyle Hidden
    Wait-Url "$root/api/health"

    Invoke-Browser open $root | Out-Null
    Invoke-Browser wait --load domcontentloaded | Out-Null
    Invoke-Browser wait "#conclusion" | Out-Null
    Invoke-Browser wait --fn "document.querySelector('#conclusion')?.textContent.trim().length > 0" | Out-Null
    Assert-Browser "page title" ((Invoke-Browser get title) -eq "TraceWitness")
    Assert-Browser "dashboard rendered" ((Invoke-Browser eval "document.querySelectorAll('section').length >= 15") -eq "true")
    Invoke-Browser errors --clear | Out-Null

    Invoke-Browser click "button[data-kind='verify']" | Out-Null
    Invoke-Browser wait --fn "document.querySelector('button[data-kind=verify]')?.nextSibling?.textContent.includes('\u6821\u9a8c\u901a\u8fc7')" | Out-Null
    Assert-Browser "evidence verify flow" $true
    Invoke-Browser click "button[data-kind='tamper']" | Out-Null
    Invoke-Browser wait --fn "document.querySelector('button[data-kind=tamper]')?.nextSibling?.textContent.includes('\u6821\u9a8c\u5931\u8d25')" | Out-Null
    Assert-Browser "evidence tamper flow" $true

    $pageErrorsText = Invoke-Browser errors --json
    $pageErrors = $pageErrorsText | ConvertFrom-Json
    $errorItems = if ($pageErrors.data) { @($pageErrors.data.errors) } else { @() }
    Assert-Browser "page errors empty" ($pageErrors.success -eq $true -and $errorItems.Count -eq 0) $pageErrorsText

    $a11yText = Invoke-Browser a11y --json
    $a11y = $a11yText | ConvertFrom-Json
    Assert-Browser "axe violations empty" (@($a11y.data.violations).Count -eq 0) ($a11yText | ConvertTo-Json -Compress -Depth 8)

    Invoke-Browser set viewport 390 844 | Out-Null
    Invoke-Browser reload | Out-Null
    Invoke-Browser wait "#conclusion" | Out-Null
    $overflow = Invoke-Browser eval "document.documentElement.scrollWidth > document.documentElement.clientWidth"
    Assert-Browser "390px no horizontal overflow" ($overflow -eq "false") $overflow
    Invoke-Browser screenshot (Join-Path $report "mobile-390.png") | Out-Null

    Invoke-Browser set viewport 1440 900 | Out-Null
    Invoke-Browser reload | Out-Null
    Invoke-Browser wait "#conclusion" | Out-Null
    Invoke-Browser screenshot (Join-Path $report "desktop-1440.png") | Out-Null
    Write-Host "browser-audit ok"
} finally {
    & agent-browser --session $session close 2>$null | Out-Null
    if ($platform -ne $null) { Stop-Process -Id $platform.Id -Force -ErrorAction SilentlyContinue }
    if ($ai -ne $null) { Stop-Process -Id $ai.Id -Force -ErrorAction SilentlyContinue }
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like "*$webPort*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    $resolvedTemp = [IO.Path]::GetFullPath($temp)
    $resolvedBase = [IO.Path]::GetFullPath($env:TEMP)
    if ($resolvedTemp.StartsWith($resolvedBase, [StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force -ErrorAction SilentlyContinue
    }
}
