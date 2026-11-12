param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$AiBaseUrl = "http://127.0.0.1:8000"
)

$ErrorActionPreference = "Stop"
$results = New-Object System.Collections.Generic.List[object]

function Add-Result([string]$name, [bool]$ok, [string]$detail = "") {
    if ($ok) {
        Write-Host "PASS $name"
    } else {
        Write-Host "FAIL $name :: $detail" -ForegroundColor Red
    }
    $results.Add([pscustomobject]@{ Name = $name; Ok = $ok; Detail = $detail })
}

function Invoke-CheckedRequest {
    param(
        [string]$Uri,
        [string]$Method = "GET",
        [string]$Body = $null,
        [string]$ContentType = $null
    )
    try {
        $params = @{ Uri = $Uri; Method = $Method; UseBasicParsing = $true }
        if (-not [string]::IsNullOrEmpty($Body)) { $params.Body = $Body }
        if (-not [string]::IsNullOrEmpty($ContentType)) { $params.ContentType = $ContentType }
        $response = Invoke-WebRequest @params
        return [pscustomobject]@{
            Status = [int]$response.StatusCode
            Content = [string]$response.Content
            Headers = $response.Headers
        }
    } catch {
        $status = 0
        if ($null -ne $_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        $content = ""
        if ($null -ne $_.ErrorDetails) { $content = [string]$_.ErrorDetails.Message }
        return [pscustomobject]@{ Status = $status; Content = $content; Headers = @{} }
    }
}

function Invoke-Upload {
    param([string]$Path, [string]$ContentType, [string]$FileName)
    $marker = "__HTTP_STATUS__"
    $lines = & curl.exe -sS -w "`n$marker%{http_code}" -H "Expect:" `
        -F "file=@$Path;type=$ContentType;filename=$FileName" "$BaseUrl/api/media/uploads"
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

$tmpHtml = Join-Path $env:TEMP ("tracewitness-audit-" + [guid]::NewGuid().ToString("N") + ".html")
$tmpWav = Join-Path $env:TEMP ("tracewitness-audit-" + [guid]::NewGuid().ToString("N") + ".wav")

try {
    $health = Invoke-RestMethod "$BaseUrl/api/health"
    Add-Result "platform health" ($health.service -eq "platform" -and $health.mode -eq "MOCK") ($health | ConvertTo-Json -Compress)

    $mode = Invoke-RestMethod "$BaseUrl/api/mode"
    Add-Result "mock mode" ($mode.mockMode -eq $true -and $mode.banner -eq "MOCK_MODE") ($mode | ConvertTo-Json -Compress)

    $index = Invoke-CheckedRequest "$BaseUrl/"
    Add-Result "index page" ($index.Status -eq 200 -and $index.Content -match "TraceWitness") "status=$($index.Status)"
    Add-Result "index CSP" ([string]$index.Headers["Content-Security-Policy"] -match "default-src 'self'") ([string]$index.Headers["Content-Security-Policy"])

    $js = Invoke-CheckedRequest "$BaseUrl/app.js"
    $css = Invoke-CheckedRequest "$BaseUrl/styles.css"
    Add-Result "static assets" ($js.Status -eq 200 -and $css.Status -eq 200) "js=$($js.Status) css=$($css.Status)"

    $overview = Invoke-RestMethod "$BaseUrl/api/overview"
    Add-Result "overview events" (@($overview.events).Count -ge 3) "events=$(@($overview.events).Count)"
    Add-Result "overview agent" ($null -ne $overview.agentRun -and @($overview.toolCalls).Count -ge 1) "calls=$(@($overview.toolCalls).Count)"
    Add-Result "overview sections" (@($overview.profiles).Count -gt 0 -and @($overview.queries).Count -gt 0 -and @($overview.trajectories).Count -gt 0 -and @($overview.plugins).Count -gt 0) ""

    $event = @($overview.events)[0]
    $eventDetail = Invoke-RestMethod "$BaseUrl/api/events/$($event.id)"
    Add-Result "event detail" ($eventDetail.id -eq $event.id) ""

    $runs = Invoke-RestMethod "$BaseUrl/api/agent/runs"
    $run = @($runs)[0]
    $runDetail = Invoke-RestMethod "$BaseUrl/api/agent/runs/$($run.id)"
    $calls = Invoke-RestMethod "$BaseUrl/api/agent/runs/$($run.id)/tool-calls"
    Add-Result "agent run/tool calls" ($runDetail.id -eq $run.id -and @($calls).Count -ge 1) "calls=$(@($calls).Count)"

    $media = Invoke-RestMethod "$BaseUrl/api/media"
    $channel = @($media.channels)[0]
    $clip = @($media.clips)[0]
    Add-Result "media timeline" ($null -ne $channel -and $null -ne $clip -and @($clip.frames).Count -gt 0) ""

    $audio = Invoke-CheckedRequest "$BaseUrl/api/media/assets/$($channel.id)/file"
    Add-Result "audio served" ($audio.Status -eq 200 -and [string]$audio.Headers["Content-Type"] -match "audio/wav" -and [string]$audio.Headers["X-Content-Type-Options"] -eq "nosniff") ""

    $frame = Invoke-CheckedRequest ($BaseUrl + @($clip.frames)[0].url)
    Add-Result "frame served" ($frame.Status -eq 200 -and [string]$frame.Headers["Content-Type"] -match "image/png") ""

    $package = @($overview.packages | Where-Object { $_.status -eq "valid" })[0]
    $valid = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/evidence/$($package.id)/verify" -ContentType "application/json" -Body "{}"
    $tampered = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/evidence/$($package.id)/verify" -ContentType "application/json" -Body '{"manifest":"tampered"}'
    Add-Result "evidence valid/tamper" ($valid.valid -eq $true -and $tampered.valid -eq $false) "valid=$($valid.valid) tamper=$($tampered.valid)"

    $plugin = @($overview.plugins)[0]
    $pluginNames = @($plugin.PSObject.Properties.Name)
    Add-Result "plugin secrets hidden" (-not ($pluginNames -contains "signature") -and -not ($pluginNames -contains "payload")) ($pluginNames -join ",")

    $aiHealth = Invoke-RestMethod "$AiBaseUrl/health"
    Add-Result "ai health" ($aiHealth.service -eq "ai" -and $aiHealth.mode -eq "MOCK") ($aiHealth | ConvertTo-Json -Compress)

    $clock = @{
        run_id = "run-w02-mock-incident-20261110"
        clock_mode = "EVAL"
        request_timestamp = "2026-01-01T00:00:03.000Z"
        tick = 3
    }
    $selectBody = @{
        run_id = $clock.run_id
        clock_mode = $clock.clock_mode
        request_timestamp = $clock.request_timestamp
        tick = $clock.tick
        task = "调查北门刚刚发生了什么"
        tools = @("search_events")
        tool_results = @()
        model_seed = 20261110
    } | ConvertTo-Json -Depth 6
    $selected = Invoke-RestMethod -Method Post -Uri "$AiBaseUrl/v1/agent/select-tool" -ContentType "application/json" -Body $selectBody
    Add-Result "ai select-tool" (@($selected.tool_calls).Count -ge 1 -and @($selected.tool_calls)[0].tool_name -eq "search_events") ""

    $extraBody = @{
        run_id = $clock.run_id
        clock_mode = $clock.clock_mode
        request_timestamp = $clock.request_timestamp
        tick = $clock.tick
        task = "x"
        tools = @()
        tool_results = @()
        model_seed = 20261110
        unexpected = $true
    } | ConvertTo-Json -Depth 6
    $extra = Invoke-CheckedRequest -Uri "$AiBaseUrl/v1/agent/select-tool" -Method "POST" -Body $extraBody -ContentType "application/json"
    Add-Result "ai extra field rejected" ($extra.Status -eq 400 -and $extra.Content -match "unexpected fields") "status=$($extra.Status)"

    $boundBody = @{
        run_id = $clock.run_id
        clock_mode = $clock.clock_mode
        request_timestamp = $clock.request_timestamp
        tick = $clock.tick
        frames = 2
        frame_width = 100000
        frame_height = 1080
    } | ConvertTo-Json -Depth 6
    $bound = Invoke-CheckedRequest -Uri "$AiBaseUrl/v1/vision/track" -Method "POST" -Body $boundBody -ContentType "application/json"
    Add-Result "ai vision bounds" ($bound.Status -eq 400 -and $bound.Content -match "frame_width is above maximum") "status=$($bound.Status)"

    [IO.File]::WriteAllText($tmpHtml, '<html><script>document.title="pwn"</script></html>')
    $html = Invoke-Upload $tmpHtml "text/html" "evil.html"
    Add-Result "html upload rejected" ($html.Status -eq 400 -and $html.Body -match "unsupported media type") "status=$($html.Status) body=$($html.Body)"

    $wav = New-Object byte[] 44
    [Text.Encoding]::ASCII.GetBytes("RIFF").CopyTo($wav, 0)
    [Text.Encoding]::ASCII.GetBytes("WAVE").CopyTo($wav, 8)
    [IO.File]::WriteAllBytes($tmpWav, $wav)
    $uploaded = Invoke-Upload $tmpWav "audio/wav" "sample.wav"
    $uploadedJson = $uploaded.Body | ConvertFrom-Json
    Add-Result "wav upload accepted" ($uploaded.Status -eq 200 -and $uploadedJson.contentType -eq "audio/wav") "status=$($uploaded.Status)"

    $wavAsset = Invoke-CheckedRequest "$BaseUrl$($uploadedJson.url)"
    Add-Result "wav asset hardening" (
        [string]$wavAsset.Headers["Content-Type"] -match "audio/wav" -and
        [string]$wavAsset.Headers["X-Content-Type-Options"] -eq "nosniff" -and
        [string]$wavAsset.Headers["Content-Security-Policy"] -match "default-src 'self'"
    ) ""
} finally {
    Remove-Item -LiteralPath $tmpHtml, $tmpWav -ErrorAction SilentlyContinue
}

$passed = @($results | Where-Object { $_.Ok }).Count
$failed = $results.Count - $passed
Write-Host "TOTAL pass=$passed fail=$failed of $($results.Count)"
if ($failed -gt 0) {
    exit 1
}
exit 0
