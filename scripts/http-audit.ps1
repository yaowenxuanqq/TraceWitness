$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$aiPort = 18001
$webPort = 18080
$token = "http-audit-token"
$root = "http://127.0.0.1:$webPort"
$temp = Join-Path $env:TEMP ("tw-http-audit-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $temp | Out-Null
$db = (Join-Path $temp "tracewitness") -replace "\\", "/"
$media = (Join-Path $temp "media") -replace "\\", "/"
$ai = $null
$platform = $null

function Find-Java8 {
    if ($env:JAVA_HOME_8 -and (Test-Path (Join-Path $env:JAVA_HOME_8 "bin\java.exe"))) {
        return $env:JAVA_HOME_8
    }
    $adopt = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-8*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($adopt) { return $adopt.FullName }
    $oracle = Get-ChildItem "C:\Program Files\Java" -Directory -Filter "jdk1.8*" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($oracle) { return $oracle.FullName }
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

function Invoke-Api([string]$method, [string]$url, [string]$body, [bool]$auth) {
    $headers = @{}
    if ($auth) { $headers["X-Trace-Token"] = $token }
    $params = @{ Method = $method; Uri = $url; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 20 }
    if ($method -eq "POST" -and $null -ne $body) {
        $headers["Content-Type"] = "application/json"
        $params.Body = $body
    }
    try {
        $response = Invoke-WebRequest @params
        return @{ Status = [int]$response.StatusCode; Body = $response.Content }
    } catch {
        $response = $_.Exception.Response
        if ($response -eq $null) { throw }
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        $text = $reader.ReadToEnd()
        return @{ Status = [int]$response.StatusCode; Body = $text }
    }
}

function Assert-Status($result, [int]$expected, [string]$label) {
    if ($result.Status -ne $expected) {
        throw "$label expected $expected but got $($result.Status) $($result.Body)"
    }
}

function Wait-OverviewSeed([string]$url) {
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $overview = (Invoke-Api "GET" $url $null $false).Body | ConvertFrom-Json
            $ticket = @($overview.tickets | Where-Object { $_.status -eq "pending_human" -and $_.ticketKind -eq "merge" } | Select-Object -First 1)
            $passage = @($overview.passages | Where-Object { $_.alerted -eq $true -and $_.status -eq "pending_human" } | Select-Object -First 1)
            $hard = @($overview.hardCases | Where-Object { $_.status -eq "open" } | Select-Object -First 1)
            if ($ticket[0].id -and $passage[0].id -and $hard[0].id) {
                return $overview
            }
        } catch {
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for seeded overview"
}

try {
    $python = Join-Path $repo "ai-service\.venv\Scripts\python.exe"
    if (-not (Test-Path $python)) { throw "Missing $python. Run run_mock.bat once to create the virtualenv." }
    $env:JAVA_HOME = Find-Java8
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    $env:TRACEWITNESS_MODE = "MOCK"
    $env:TRACEWITNESS_API_TOKEN = $token
    $env:TRACEWITNESS_DB = "jdbc:h2:file:$db"
    $env:TRACEWITNESS_AI_BASE_URL = "http://127.0.0.1:$aiPort"
    $ai = Start-Process -FilePath $python -ArgumentList @("-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "$aiPort") -WorkingDirectory (Join-Path $repo "ai-service") -PassThru -WindowStyle Hidden
    Wait-Url "http://127.0.0.1:$aiPort/health"
    $args = "gradlew.bat bootRun --args=`"--server.port=$webPort --tracewitness.media-dir=$media`""
    $platform = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $args) -WorkingDirectory (Join-Path $repo "platform") -PassThru -WindowStyle Hidden
    Wait-Url "$root/api/health"

    Assert-Status (Invoke-Api "POST" "$root/api/plugins/rollback" '{"actor":"investigator"}' $false) 401 "missing token"
    $overview = Wait-OverviewSeed "$root/api/overview"
    $ticket = @($overview.tickets | Where-Object { $_.status -eq "pending_human" -and $_.ticketKind -eq "merge" } | Select-Object -First 1)
    $passage = @($overview.passages | Where-Object { $_.alerted -eq $true -and $_.status -eq "pending_human" } | Select-Object -First 1)
    $hard = @($overview.hardCases | Where-Object { $_.status -eq "open" } | Select-Object -First 1)
    if (-not $ticket[0].id -or -not $passage[0].id -or -not $hard[0].id) { throw "seed data is incomplete" }

    $decision = "$root/api/profiles/tickets/$($ticket[0].id)/decision"
    Assert-Status (Invoke-Api "POST" $decision '{"decision":"approve","actor":"investigator","extra":1}' $true) 400 "extra field"
    Assert-Status (Invoke-Api "POST" $decision '{"decision":"approve"}' $true) 400 "missing actor"
    Assert-Status (Invoke-Api "POST" $decision '{"decision":1,"actor":"investigator"}' $true) 400 "wrong type"
    Assert-Status (Invoke-Api "POST" $decision '{' $true) 400 "illegal json"
    Assert-Status (Invoke-Api "POST" "$root/api/profiles/tickets/tw.ticket.missing.000001/decision" '{"decision":"approve","actor":"investigator"}' $true) 404 "missing id"
    Assert-Status (Invoke-Api "POST" $decision '{"decision":"approve","actor":"investigator"}' $true) 200 "approve ticket"
    Assert-Status (Invoke-Api "POST" $decision '{"decision":"approve","actor":"investigator"}' $true) 409 "repeat ticket"
    $alert = "$root/api/alerts/$($passage[0].id)/decision"
    Assert-Status (Invoke-Api "POST" $alert '{"decision":"approve","actor":"investigator"}' $true) 200 "confirm alert"
    Assert-Status (Invoke-Api "POST" $alert '{"decision":"approve","actor":"investigator"}' $true) 409 "repeat alert"
    $label = "$root/api/hard-cases/$($hard[0].id)/label"
    Assert-Status (Invoke-Api "POST" $label '{"label":"true_negative","actor":"investigator"}' $true) 200 "label hard case"
    Assert-Status (Invoke-Api "POST" $label '{"label":"true_negative","actor":"investigator"}' $true) 409 "repeat label"
    Assert-Status (Invoke-Api "POST" "$root/api/plugins/rollback" '{"actor":"investigator"}' $true) 200 "rollback"
    $after = (Invoke-Api "GET" "$root/api/overview" $null $false).Body | ConvertFrom-Json
    $active = @($after.plugins | Where-Object { $_.slot -eq "active" } | Select-Object -First 1)
    Assert-Status (Invoke-Api "POST" "$root/api/plugins/$($active[0].id)/fault" $null $true) 200 "fault"
    Assert-Status (Invoke-Api "POST" "$root/api/plugins/$($active[0].id)/fault" $null $true) 409 "repeat fault"
    Write-Output "http-audit ok"
} finally {
    if ($platform -ne $null) { Stop-Process -Id $platform.Id -Force -ErrorAction SilentlyContinue }
    if ($ai -ne $null) { Stop-Process -Id $ai.Id -Force -ErrorAction SilentlyContinue }
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like "*$webPort*" } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}
