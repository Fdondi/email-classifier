# Restart is left to the caller. Unloads chat models, POSTs triage, polls until done.
# Fails out loud if the LLM never becomes ready or nothing is classified.

param(
    [string]$BackendUrl = "http://127.0.0.1:17432",
    [string]$LlmOrigin = "http://127.0.0.1:1234",
    [int]$PollSeconds = 10,
    [int]$MaxWaitMinutes = 90,
    [switch]$SkipUnload
)

$ErrorActionPreference = "Stop"
$Lms = Join-Path $env:USERPROFILE ".cache\lm-studio\bin\lms.exe"
$LogDir = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..\results")).Path "triage-verify"
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$Stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$LogFile = Join-Path $LogDir "$Stamp.log"

function Write-Log([string]$Message) {
    $line = "$(Get-Date -Format o) $Message"
    Add-Content -Path $LogFile -Value $line
    Write-Host $line
}

function Get-Json([string]$Url, [int]$TimeoutSec = 30) {
    return Invoke-RestMethod -Uri $Url -TimeoutSec $TimeoutSec
}

function Get-LoadedChatIds {
    $models = Get-Json -Url "$LlmOrigin/api/v0/models"
    if (-not $models.data) { return @() }
    return @(
        $models.data |
            Where-Object { $_.state -eq "loaded" -and $_.type -ne "embeddings" -and $_.id -notmatch "embed" } |
            ForEach-Object { $_.id }
    )
}

$status = Get-Json -Url "$BackendUrl/api/status"
Write-Log "Backend buildVersion=$($status.buildVersion) mailSource=$($status.mailSource) llmReachable=$($status.llmReachable)"

if (-not $SkipUnload) {
    if (-not (Test-Path $Lms)) { throw "LM Studio CLI missing: $Lms" }
    Write-Log "Unloading LM Studio models so triage must load"
    & $Lms unload --all 2>&1 | ForEach-Object { Write-Log "lms: $_" }
    Start-Sleep -Seconds 2
    $loaded = @(Get-LoadedChatIds)
    if ($loaded.Count -gt 0) { throw "Models still loaded after unload: $($loaded -join ', ')" }
    Write-Log "Confirmed no chat model loaded"
}

Write-Log "POST $BackendUrl/api/jobs/triage"
$progress = Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/jobs/triage" -TimeoutSec 30
Write-Log "Started phase=$($progress.phase) running=$($progress.running)"

$deadline = (Get-Date).AddMinutes($MaxWaitMinutes)
$sawStartingLlm = $false
$sawClassifying = $false
do {
    Start-Sleep -Seconds $PollSeconds
    $progress = Get-Json -Url "$BackendUrl/api/jobs/triage"
    if ($progress.phase -eq "STARTING_LLM") { $sawStartingLlm = $true }
    if ($progress.phase -eq "CLASSIFYING") { $sawClassifying = $true }
    $errPreview = if ($progress.errors -and $progress.errors.Count) { $progress.errors[0] } else { "" }
    Write-Log "phase=$($progress.phase) running=$($progress.running) ingested=$($progress.ingested) rules=$($progress.classifiedByRule) llm=$($progress.classifiedByLlm) fallback=$($progress.fallback) toClassify=$($progress.toClassify) err=$errPreview"
    if (-not $progress.running -and $progress.phase -in @("DONE", "FAILED", "IDLE")) { break }
} while ((Get-Date) -lt $deadline)

if ($progress.running) { throw "Timed out after ${MaxWaitMinutes}m still phase=$($progress.phase)" }

$runs = Get-Json -Url "$BackendUrl/api/jobs/runs"
$latest = $runs | Select-Object -First 1
Write-Log "Latest run outcome=$($latest.outcome) llmReady=$($latest.llmReady) byLlm=$($latest.classifiedByLlm) byRule=$($latest.classifiedByRule) fallback=$($latest.fallback) errors=$($latest.errors -join ' | ')"

if ($progress.phase -eq "FAILED") { throw "Triage finished FAILED: $($progress.errors -join '; ')" }
if (-not $latest.llmReady) { throw "Triage finished but llmReady=false: $($latest.errors -join '; ')" }
if (($latest.classifiedByLlm + $latest.classifiedByRule) -lt 1 -and $latest.fallback -lt 1 -and $progress.ingested -lt 1) {
    throw "Triage finished with no meaningful work (no ingest, no classifications)"
}
if ($latest.classifiedByLlm -lt 1 -and $latest.fallback -gt 0 -and ($latest.errors -join ' ') -match 'All \d+ emails failed') {
    throw "All LLM classifications failed: $($latest.errors -join '; ')"
}

Write-Log "VERIFY OK sawStartingLlm=$sawStartingLlm sawClassifying=$sawClassifying log=$LogFile"
$progress | ConvertTo-Json -Depth 5
if ($latest) { $latest | ConvertTo-Json -Depth 5 }
