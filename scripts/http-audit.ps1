$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$aiPort = 18001
$webPort = 18080
$token = "http-audit-token"
$root = "http://127.0.0.1:$webPort"
$aiRoot = "http://127.0.0.1:$aiPort"
$temp = Join-Path $env:TEMP ("tw-http-audit-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $temp | Out-Null
$db = (Join-Path $temp "tracewitness") -replace "\\", "/"
$media = (Join-Path $temp "media") -replace "\\", "/"
$tmpHtml = Join-Path $temp "evil.html"
$tmpWav = Join-Path $temp "sample.wav"
$ai = $null
$platform = $null
$checkCount = 0

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
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Body = [string]$response.Content
            Headers = $response.Headers
        }
    } catch {
        $response = $_.Exception.Response
        if ($response -eq $null) { throw }
        $reader = New-Object System.IO.StreamReader($response.GetResponseStream())
        $text = $reader.ReadToEnd()
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Body = $text
            Headers = $response.Headers
        }
    }
}

function Add-Check([string]$label, [bool]$ok, [string]$detail = "") {
    $script:checkCount++
    if (-not $ok) {
        throw "$label failed :: $detail"
    }
    Write-Host "PASS $label"
}

function Assert-Status($result, [int]$expected, [string]$label) {
    Add-Check $label ($result.Status -eq $expected) "expected=$expected got=$($result.Status) body=$($result.Body)"
}

function Invoke-Upload([string]$path, [string]$contentType, [string]$filename, [bool]$auth) {
    $marker = "__HTTP_STATUS__"
    $headerArgs = @()
    if ($auth) { $headerArgs = @("-H", "X-Trace-Token: $token") }
    $lines = & curl.exe -sS -w "`n$marker%{http_code}" -H "Expect:" @headerArgs `
        -F "file=@$path;type=$contentType;filename=$filename" "$root/api/media/uploads"
    $text = ($lines -join "`n")
    $index = $text.LastIndexOf($marker)
    if ($index -lt 0) {
        return [pscustomobject]@{ Status = 0; Body = $text }
    }
    $statusText = $text.Substring($index + $marker.Length).Trim()
    return [pscustomobject]@{
        Status = [int]$statusText
        Body = $text.Substring(0, $index).Trim()
    }
}

function New-ClockBody([hashtable]$extra) {
    $body = @{
        run_id = "run-w02-mock-incident-20261110"
        clock_mode = "EVAL"
        request_timestamp = "2026-01-01T00:00:03.000Z"
        tick = 3
    }
    foreach ($key in $extra.Keys) {
        $body[$key] = $extra[$key]
    }
    return $body
}

function Wait-OverviewSeed([string]$url) {
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $overview = (Invoke-Api "GET" $url $null $false).Body | ConvertFrom-Json
            $ticket = @($overview.tickets | Where-Object { $_.status -eq "pending_human" -and $_.ticketKind -eq "merge" } | Select-Object -First 1)
            $passage = @($overview.passages | Where-Object { $_.alerted -eq $true -and $_.status -eq "pending_human" } | Select-Object -First 1)
            $hard = @($overview.hardCases | Where-Object { $_.status -eq "open" } | Select-Object -First 1)
            $package = @($overview.packages | Where-Object { $_.status -eq "valid" } | Select-Object -First 1)
            if ($ticket[0].id -and $passage[0].id -and $hard[0].id -and $package[0].id) {
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
    $env:TRACEWITNESS_AI_BASE_URL = $aiRoot
    $ai = Start-Process -FilePath $python -ArgumentList @("-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "$aiPort") -WorkingDirectory (Join-Path $repo "ai-service") -PassThru -WindowStyle Hidden
    Wait-Url "$aiRoot/health"
    $args = "gradlew.bat bootRun --args=`"--server.port=$webPort --tracewitness.media-dir=$media`""
    $platform = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $args) -WorkingDirectory (Join-Path $repo "platform") -PassThru -WindowStyle Hidden
    Wait-Url "$root/api/health"

    $health = (Invoke-Api "GET" "$root/api/health" $null $false).Body | ConvertFrom-Json
    Add-Check "platform health" ($health.service -eq "platform" -and $health.mode -eq "MOCK") ($health | ConvertTo-Json -Compress)

    $mode = (Invoke-Api "GET" "$root/api/mode" $null $false).Body | ConvertFrom-Json
    Add-Check "mock mode" ($mode.mockMode -eq $true -and $mode.banner -eq "MOCK_MODE") ($mode | ConvertTo-Json -Compress)

    $session = (Invoke-Api "GET" "$root/api/session" $null $false).Body | ConvertFrom-Json
    Add-Check "loopback session token" ($session.token -eq $token) "token not returned"

    $index = Invoke-Api "GET" "$root/" $null $false
    Add-Check "index page" ($index.Status -eq 200 -and $index.Body -match "TraceWitness") "status=$($index.Status)"
    Add-Check "index CSP" ([string]$index.Headers["Content-Security-Policy"] -match "default-src 'self'") ([string]$index.Headers["Content-Security-Policy"])
    Add-Check "index nosniff" ([string]$index.Headers["X-Content-Type-Options"] -eq "nosniff") ([string]$index.Headers["X-Content-Type-Options"])
    Add-Check "index frame deny" ([string]$index.Headers["X-Frame-Options"] -eq "DENY") ([string]$index.Headers["X-Frame-Options"])
    Add-Check "index referrer policy" ([string]$index.Headers["Referrer-Policy"] -eq "no-referrer") ([string]$index.Headers["Referrer-Policy"])

    $js = Invoke-Api "GET" "$root/app.js" $null $false
    $css = Invoke-Api "GET" "$root/styles.css" $null $false
    Add-Check "static assets" ($js.Status -eq 200 -and $css.Status -eq 200) "js=$($js.Status) css=$($css.Status)"

    Assert-Status (Invoke-Api "GET" "$root/api/events/tw.event.missing.000001" $null $false) 404 "missing event"

    $overview = Wait-OverviewSeed "$root/api/overview"
    Add-Check "overview seeded" (@($overview.events).Count -ge 3 -and $null -ne $overview.agentRun) "events=$(@($overview.events).Count)"
    Add-Check "overview sections" (@($overview.profiles).Count -gt 0 -and @($overview.queries).Count -gt 0 -and @($overview.trajectories).Count -gt 0 -and @($overview.plugins).Count -gt 0) "one or more dashboard sections empty"
    Add-Check "agent tool calls" (@($overview.toolCalls).Count -ge 1) "calls=$(@($overview.toolCalls).Count)"
    Add-Check "agent tools" (@($overview.tools).Count -eq 6 -and @($overview.tools | Where-Object { $_.implemented -eq $true }).Count -eq 6) "tool registry mismatch"

    $event = @($overview.events)[0]
    $eventDetail = (Invoke-Api "GET" "$root/api/events/$($event.id)" $null $false).Body | ConvertFrom-Json
    Add-Check "event detail" ($eventDetail.id -eq $event.id) "id mismatch"

    $mediaTimeline = (Invoke-Api "GET" "$root/api/media" $null $false).Body | ConvertFrom-Json
    $channel = @($mediaTimeline.channels)[0]
    $clip = @($mediaTimeline.clips)[0]
    Add-Check "media timeline" ($null -ne $channel -and $null -ne $clip -and @($clip.frames).Count -gt 0) "media timeline incomplete"
    $audio = Invoke-Api "GET" "$root/api/media/assets/$($channel.id)/file" $null $false
    Add-Check "audio served" ($audio.Status -eq 200 -and [string]$audio.Headers["Content-Type"] -match "audio/wav") "status=$($audio.Status) type=$([string]$audio.Headers['Content-Type'])"
    $frame = Invoke-Api "GET" ($root + @($clip.frames)[0].url) $null $false
    Add-Check "frame served" ($frame.Status -eq 200 -and [string]$frame.Headers["Content-Type"] -match "image/png") "status=$($frame.Status) type=$([string]$frame.Headers['Content-Type'])"

    $plugin = @($overview.plugins)[0]
    $pluginNames = @($plugin.PSObject.Properties.Name)
    Add-Check "plugin secrets hidden" (-not ($pluginNames -contains "signature") -and -not ($pluginNames -contains "payload")) ($pluginNames -join ",")

    $aiHealth = (Invoke-Api "GET" "$aiRoot/health" $null $false).Body | ConvertFrom-Json
    Add-Check "ai health" ($aiHealth.service -eq "ai" -and $aiHealth.mode -eq "MOCK") ($aiHealth | ConvertTo-Json -Compress)
    $selectBody = New-ClockBody (@{
        task = "investigate the north gate event"
        tools = @("search_events")
        tool_results = @()
        model_seed = 20261110
    }) | ConvertTo-Json -Compress -Depth 8
    $selected = (Invoke-Api "POST" "$aiRoot/v1/agent/select-tool" $selectBody $false).Body | ConvertFrom-Json
    Add-Check "ai select-tool" (@($selected.tool_calls).Count -ge 1 -and @($selected.tool_calls)[0].tool_name -eq "search_events") "unexpected tool selection"
    $extraBody = New-ClockBody (@{
        task = "x"
        tools = @()
        tool_results = @()
        model_seed = 20261110
        unexpected = $true
    }) | ConvertTo-Json -Compress -Depth 8
    $extra = Invoke-Api "POST" "$aiRoot/v1/agent/select-tool" $extraBody $false
    Add-Check "ai extra field rejected" ($extra.Status -eq 400) "status=$($extra.Status) body=$($extra.Body)"
    $boundBody = New-ClockBody (@{
        frames = 2
        frame_width = 100000
        frame_height = 1080
    }) | ConvertTo-Json -Compress -Depth 8
    $bound = Invoke-Api "POST" "$aiRoot/v1/vision/track" $boundBody $false
    Add-Check "ai vision bounds" ($bound.Status -eq 400) "status=$($bound.Status) body=$($bound.Body)"

    [IO.File]::WriteAllText($tmpHtml, '<html><script>document.title="pwn"</script></html>')
    $html = Invoke-Upload $tmpHtml "text/html" "evil.html" $true
    Add-Check "html upload rejected" ($html.Status -eq 400 -and $html.Body -match "unsupported media type") "status=$($html.Status) body=$($html.Body)"
    $wav = New-Object byte[] 44
    [Text.Encoding]::ASCII.GetBytes("RIFF").CopyTo($wav, 0)
    [Text.Encoding]::ASCII.GetBytes("WAVE").CopyTo($wav, 8)
    [IO.File]::WriteAllBytes($tmpWav, $wav)
    $uploaded = Invoke-Upload $tmpWav "audio/wav" "sample.wav" $true
    $uploadedJson = $uploaded.Body | ConvertFrom-Json
    Add-Check "wav upload accepted" ($uploaded.Status -eq 200 -and $uploadedJson.contentType -eq "audio/wav") "status=$($uploaded.Status) body=$($uploaded.Body)"
    $wavAsset = Invoke-Api "GET" "$root$($uploadedJson.url)" $null $false
    Add-Check "wav asset hardening" (
        [string]$wavAsset.Headers["Content-Type"] -match "audio/wav" -and
        [string]$wavAsset.Headers["X-Content-Type-Options"] -eq "nosniff" -and
        [string]$wavAsset.Headers["Content-Security-Policy"] -match "default-src 'self'"
    ) "status=$($wavAsset.Status)"
    $unauthenticatedUpload = Invoke-Upload $tmpWav "audio/wav" "sample.wav" $false
    Add-Check "upload requires token" ($unauthenticatedUpload.Status -eq 401) "status=$($unauthenticatedUpload.Status)"

    $package = @($overview.packages | Where-Object { $_.status -eq "valid" } | Select-Object -First 1)[0]
    $valid = (Invoke-Api "POST" "$root/api/evidence/$($package.id)/verify" '{}' $true).Body | ConvertFrom-Json
    $tampered = (Invoke-Api "POST" "$root/api/evidence/$($package.id)/verify" '{"manifest":"tampered"}' $true).Body | ConvertFrom-Json
    Add-Check "evidence valid/tamper" ($valid.valid -eq $true -and $tampered.valid -eq $false) "valid=$($valid.valid) tamper=$($tampered.valid)"

    Assert-Status (Invoke-Api "POST" "$root/api/plugins/rollback" '{"actor":"investigator"}' $false) 401 "missing token"
    $ticket = @($overview.tickets | Where-Object { $_.status -eq "pending_human" -and $_.ticketKind -eq "merge" } | Select-Object -First 1)
    $passage = @($overview.passages | Where-Object { $_.alerted -eq $true -and $_.status -eq "pending_human" } | Select-Object -First 1)
    $hard = @($overview.hardCases | Where-Object { $_.status -eq "open" } | Select-Object -First 1)
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
    $exported = Invoke-Api "GET" "$root/api/hard-cases/export" $null $false
    Add-Check "hard case export" ($exported.Status -eq 200 -and $exported.Body -match $hard[0].id) "status=$($exported.Status)"

    Write-Host "TOTAL pass=$checkCount fail=0"
} finally {
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
