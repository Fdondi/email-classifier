# Scheduled triage: start LM Studio, load Gemma 4, run triage, unload.
# Fails out loud if the chat model is not on disk yet (download unfinished).
# Does not invent mail or classification results.
#
# Succeeds at most once per -MinHoursBetween hours (default 6; results\nightly\last-success.txt).
# Earlier starts exit at once. Failed runs (network) leave no mark, so the next start retries.
# Exits that do nothing (too soon, GPU busy, another run going) append one line to
# results\nightly\checks.log instead of creating a run log. GPU busy (usually Salad): wait for the
# next check, never load on CPU. Salad pauses its GPU work whenever llama-server.exe runs, on GPU
# or CPU alike, so a CPU run costs Salad as much and lasts longer. The tray warns after 12 h
# without a run; "Run triage now" there loads on CPU if the GPU is still busy.
#
# -After none       leave the PC as-is (default for a manual run)
# -After hibernate  Windows equivalent of the plan's suspend; Task Scheduler can wake this
# -After shutdown   real power-off (S5). Windows cannot schedule that; Gigabyte BIOS RTC must
#                   power the machine on. Then this script shuts down again after a successful run.
# Either one applies only when the EmailToolNightlyTriage wake timer woke the PC for this run;
# a PC that was already on stays on.

param(
    [ValidateSet("none", "hibernate", "shutdown")]
    [string]$After = "none",
    [double]$MinHoursBetween = 6,
    [int]$MinFreeVramMib = 18000,
    [int]$MaxGpuUtilPercent = 30,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Lms = Join-Path $env:USERPROFILE ".cache\lm-studio\bin\lms.exe"
$LogDir = Join-Path $RepoRoot "results\nightly"
$ScriptStart = Get-Date
$Stamp = $ScriptStart.ToString("yyyy-MM-dd_HHmmss")
$LogFile = Join-Path $LogDir "$Stamp.log"
$ChecksLog = Join-Path $LogDir "checks.log"
$LockFile = Join-Path $LogDir "last-success.txt"
# Present while a run is going; the tray icon reads it (covers the model load, before the backend job starts).
$RunningFile = Join-Path $LogDir "running.txt"
$script:MarkedRunning = $false
$BackendUrl = "http://127.0.0.1:17432"
$LlmUrl = "http://127.0.0.1:1234/v1"

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# Lines are held until the run really starts (Open-RunLog), so frequent no-op checks do not
# leave a log file each.
$script:Pending = [System.Collections.Generic.List[string]]::new()
$script:RunLogOpen = $false

function Write-Log {
    param([string]$Message)
    $line = "$(Get-Date -Format o) $Message"
    if ($script:RunLogOpen) {
        Add-Content -Path $LogFile -Value $line
    } else {
        $script:Pending.Add($line)
    }
    Write-Host $line
}

function Open-RunLog {
    if (-not $script:RunLogOpen) {
        $script:RunLogOpen = $true
        if ($script:Pending.Count -gt 0) {
            Add-Content -Path $LogFile -Value $script:Pending
        }
    }
}

function Write-Check {
    param([string]$Message)
    $line = "$(Get-Date -Format o) $Message"
    Add-Content -Path $ChecksLog -Value $line
    Write-Host $line
}

function Get-LastSuccess {
    if (-not (Test-Path $LockFile)) {
        return $null
    }
    try {
        return [DateTimeOffset]::Parse((Get-Content $LockFile -Raw).Trim())
    } catch {
        return $null
    }
}

function Get-Json {
    param([string]$Url, [int]$TimeoutSec = 10)
    return Invoke-RestMethod -Uri $Url -TimeoutSec $TimeoutSec
}

function Wait-HttpJson {
    param([string]$Url, [int]$TimeoutSec)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $last = $null
    while ((Get-Date) -lt $deadline) {
        try {
            return Get-Json -Url $Url -TimeoutSec 5
        } catch {
            $last = $_.Exception.Message
            Start-Sleep -Seconds 3
        }
    }
    throw "Timed out waiting for $Url ($last)"
}

function Get-GemmaKey {
    $raw = & $Lms ls --json --llm | Out-String
    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw "lms ls --json --llm returned no output"
    }
    $parsed = $raw | ConvertFrom-Json
    $items = @()
    if ($parsed -is [System.Array]) {
        $items = $parsed
    } elseif ($null -ne $parsed) {
        $items = @($parsed)
    }
    $keys = foreach ($item in $items) {
        if ($item.modelKey) { $item.modelKey }
        elseif ($item.key) { $item.key }
        elseif ($item.id) { $item.id }
        elseif ($item.name) { $item.name }
    }
    $gemma4 = $keys | Where-Object { $_ -match "gemma-4" } | Select-Object -First 1
    if ($gemma4) {
        return $gemma4
    }
    $gemma = $keys | Where-Object { $_ -match "gemma" } | Select-Object -First 1
    if ($gemma) {
        return $gemma
    }
    throw "Gemma is not on disk yet. lms ls --llm is empty or has no gemma model. Let the LM Studio download finish."
}

function Test-ChatModelLoaded {
    # /v1/models lists installed models even when none are in memory.
    $origin = $LlmUrl -replace '/v1/?$', ''
    $models = Get-Json -Url "$origin/api/v0/models"
    $ids = @()
    if ($models.data) {
        $ids = @(
            $models.data |
                Where-Object { $_.state -eq 'loaded' -and $_.type -ne 'embeddings' -and $_.id -notmatch 'embed' } |
                ForEach-Object { $_.id }
        )
    }
    return $ids
}

# True only when the latest resume from sleep/hibernate was this task's wake timer, shortly before
# the run started. A PC that was already on (or woken by a person) is left on.
function Test-WokenByThisTask {
    $wake = Get-WinEvent -FilterHashtable @{ LogName = "System"; ProviderName = "Microsoft-Windows-Power-Troubleshooter"; Id = 1 } -MaxEvents 1 -ErrorAction SilentlyContinue
    if (-not $wake) {
        return $false
    }
    # Wake Source line reads: Timer - Windows will execute 'EmailToolNightlyTriage' scheduled task ...
    $minutesBeforeStart = ($ScriptStart - $wake.TimeCreated).TotalMinutes
    return $minutesBeforeStart -ge -1 -and $minutesBeforeStart -lt 10 -and $wake.Message -match "EmailToolNightlyTriage"
}

function Test-UserIdleEnoughToPowerOff {
    Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class LastInput {
    [StructLayout(LayoutKind.Sequential)]
    public struct LASTINPUTINFO { public uint cbSize; public uint dwTime; }
    [DllImport("user32.dll")] static extern bool GetLastInputInfo(ref LASTINPUTINFO plii);
    public static uint IdleMs() {
        LASTINPUTINFO info = new LASTINPUTINFO();
        info.cbSize = (uint)System.Runtime.InteropServices.Marshal.SizeOf(info);
        GetLastInputInfo(ref info);
        return (uint)Environment.TickCount - info.dwTime;
    }
}
"@
    return [LastInput]::IdleMs() -gt (15 * 60 * 1000)
}

function Start-BackendIfNeeded {
    try {
        $status = Get-Json -Url "$BackendUrl/api/status"
        Write-Log "Backend already up mailSource=$($status.mailSource) llmReachable=$($status.llmReachable)"
        return
    } catch {
        Write-Log "Backend not reachable yet: $($_.Exception.Message)"
    }
    $mvnw = Join-Path $RepoRoot "backend\mvnw.cmd"
    $backendDir = Join-Path $RepoRoot "backend"
    if (-not (Test-Path $mvnw)) {
        throw "Backend wrapper not found at $mvnw"
    }
    $outLog = Join-Path $LogDir "backend-$Stamp.out.log"
    $errLog = Join-Path $LogDir "backend-$Stamp.err.log"
    Write-Log "Starting backend (spring-boot:run). stdout=$outLog"
    Start-Process -FilePath $mvnw -ArgumentList "spring-boot:run" -WorkingDirectory $backendDir -RedirectStandardOutput $outLog -RedirectStandardError $errLog -WindowStyle Hidden | Out-Null
    Wait-HttpJson -Url "$BackendUrl/api/status" -TimeoutSec 180 | Out-Null
    Write-Log "Backend /api/status is up"
}

function Invoke-TriageJob {
    # The backend can die mid-run (crash, tray Exit, Windows restart) and the internet drops now and then.
    # Each attempt restarts the backend if needed and asks for a run again. A run only classifies emails
    # still without a category, so a retry resumes where the last attempt stopped.
    $maxAttempts = 4
    for ($attempt = 1; ; $attempt++) {
        try {
            Start-BackendIfNeeded
            Write-Log "POST $BackendUrl/api/jobs/triage?source=nightly (attempt $attempt)"
            Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/jobs/triage?source=nightly" -TimeoutSec 60 | Out-Null
            $deadline = (Get-Date).AddHours(6)
            do {
                Start-Sleep -Seconds 2
                $job = Invoke-RestMethod -Method Get -Uri "$BackendUrl/api/jobs/triage" -TimeoutSec 30
                Write-Log "Triage $($job.phase): downloaded $($job.ingested) skipped $($job.skipped); classified rules=$($job.classifiedByRule) llm=$($job.classifiedByLlm) fallback=$($job.fallback)"
                if ((Get-Date) -gt $deadline) {
                    throw "Triage still running after 6 hours (phase=$($job.phase))"
                }
            } while ($job.running)
            return $job
        } catch {
            Write-Log "Triage attempt $attempt failed: $($_.Exception.Message)"
            if ($attempt -ge $maxAttempts) {
                throw
            }
            Start-Sleep -Seconds (60 * $attempt)
        }
    }
}

function Get-ForeignGpuApps {
    # Processes other than LM Studio holding GPU compute memory. Empty when unknown.
    $smi = Get-Command nvidia-smi -ErrorAction SilentlyContinue
    if (-not $smi) {
        Write-Log "nvidia-smi not on PATH; will still try lms load"
        return @()
    }
    $apps = & nvidia-smi --query-compute-apps=pid,process_name,used_gpu_memory --format=csv,noheader
    if ($LASTEXITCODE -ne 0) {
        Write-Log "nvidia-smi compute-apps query failed; will still try lms load"
        return @()
    }
    $foreign = @()
    foreach ($line in @($apps)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $name = ($line -split ",", 3)[1]
        if ($null -eq $name) { continue }
        $name = $name.Trim()
        if ($name -match "lmstudio|lm-studio|lms.exe|llama") { continue }
        $foreign += $name
    }
    return $foreign
}

function Get-GpuWaitReason {
    # Why Gemma cannot load right now, or $null. Also catches graphics users (games) that the
    # compute-apps list does not show: needs $MinFreeVramMib free, counting what LM Studio holds
    # (it is unloaded before Gemma loads).
    $foreign = @(Get-ForeignGpuApps)
    if ($foreign.Count -gt 0) {
        return "GPU in use by $($foreign -join ', ')"
    }
    if (-not (Get-Command nvidia-smi -ErrorAction SilentlyContinue)) {
        return $null
    }
    $freeRaw = & nvidia-smi --query-gpu=memory.free --format=csv,noheader,nounits
    if ($LASTEXITCODE -ne 0) {
        return $null
    }
    $free = [int](@($freeRaw)[0].Trim())
    $lmsHeld = 0
    $apps = & nvidia-smi --query-compute-apps=process_name,used_gpu_memory --format=csv,noheader,nounits
    foreach ($line in @($apps)) {
        $parts = $line -split ","
        if ($parts.Count -ge 2 -and $parts[0] -match "lmstudio|lm-studio|lms.exe|llama") {
            $mib = 0
            if ([int]::TryParse($parts[1].Trim(), [ref]$mib)) { $lmsHeld += $mib }
        }
    }
    if ($free + $lmsHeld -lt $MinFreeVramMib) {
        return "only $($free + $lmsHeld) MiB GPU memory free, Gemma needs $MinFreeVramMib"
    }
    # Compute load: one reading is noisy, so average a few over ~5 s.
    $samples = @()
    for ($i = 0; $i -lt 5; $i++) {
        $raw = & nvidia-smi --query-gpu=utilization.gpu --format=csv,noheader,nounits
        $pct = 0
        if ($LASTEXITCODE -eq 0 -and [int]::TryParse((@($raw)[0]).Trim(), [ref]$pct)) {
            $samples += $pct
        }
        if ($i -lt 4) { Start-Sleep -Seconds 1 }
    }
    if ($samples.Count -gt 0) {
        $avg = [math]::Round(($samples | Measure-Object -Average).Average)
        if ($avg -ge $MaxGpuUtilPercent) {
            return "GPU compute busy ($avg% average, limit $MaxGpuUtilPercent%)"
        }
    }
    return $null
}

try {
    # Watchdog: the catch-up task runs this every 15 min and at logon, so the backend (and its tray
    # icon) comes back even when no run is due. Failure here only logs; the run checks below go on.
    try {
        Get-Json -Url "$BackendUrl/api/status" | Out-Null
    } catch {
        Write-Check "Backend not running: starting it."
        try { Start-BackendIfNeeded } catch { Write-Check "Backend did not start: $($_.Exception.Message)" }
    }

    # One run at a time: the night task and the catch-up task can fire together, and a second
    # run would unload the model under the first.
    $RunMutex = [System.Threading.Mutex]::new($false, "Local\EmailToolTriageRun")
    $owned = $false
    try { $owned = $RunMutex.WaitOne(0) } catch [System.Threading.AbandonedMutexException] { $owned = $true }
    if (-not $owned) {
        Write-Check "Another run is in progress."
        exit 0
    }

    if (-not $Force) {
        $last = Get-LastSuccess
        if ($null -ne $last) {
            $since = [DateTimeOffset]::Now - $last
            if ($since.TotalHours -lt $MinHoursBetween) {
                Write-Check ("Too soon: last success {0:HH:mm}, runs again after {1} h." -f $last.LocalDateTime, $MinHoursBetween)
                exit 0
            }
        }
    }

    Write-Log "Triage run starting. repo=$RepoRoot after=$After"

    # Before touching LM Studio or the backend: if something else holds the GPU, wait for the next check.
    $waitReason = Get-GpuWaitReason
    if ($null -ne $waitReason) {
        Write-Check "Waiting: $waitReason."
        exit 0
    }
    Open-RunLog
    Set-Content -Path $RunningFile -Value (Get-Date -Format o)
    $script:MarkedRunning = $true

    if (-not (Test-Path $Lms)) {
        throw "LM Studio CLI not found at $Lms"
    }

    Write-Log "Starting LM Studio server on 1234"
    & $Lms server start --port 1234
    Wait-HttpJson -Url "$LlmUrl/models" -TimeoutSec 60 | Out-Null

    Start-BackendIfNeeded

    $modelKey = Get-GemmaKey
    Write-Log "Loading $modelKey (--gpu max)"
    & $Lms unload --all
    & $Lms load $modelKey -y --gpu max
    $deadline = (Get-Date).AddMinutes(20)
    $chatIds = @()
    while ((Get-Date) -lt $deadline) {
        $chatIds = @(Test-ChatModelLoaded)
        if ($chatIds.Count -gt 0) {
            break
        }
        Start-Sleep -Seconds 5
    }
    if ($chatIds.Count -eq 0) {
        throw "Gemma did not appear in $LlmUrl/models after load. VRAM/load may have failed."
    }
    Write-Log "Chat model ready: $($chatIds -join ', ')"

    $status = Get-Json -Url "$BackendUrl/api/status"
    Write-Log "Backend status mailSource=$($status.mailSource) llmReachable=$($status.llmReachable)"

    $job = Invoke-TriageJob
    $jobJson = $job | ConvertTo-Json -Compress
    Write-Log "Triage result: $jobJson"
    $errList = @()
    if ($job.errors) {
        $errList = @($job.errors)
    }
    if ($errList.Count -gt 0) {
        Write-Log "Triage reported errors: $($errList -join '; ')"
        if (([int]$job.classifiedByLlm) -eq 0) {
            throw "Triage finished with errors and no LLM classifications: $($errList -join '; ')"
        }
    }

    Write-Log "Unloading LM Studio models"
    & $Lms unload --all
    Set-Content -Path $LockFile -Value ([DateTimeOffset]::Now.ToString("o"))
    Write-Log "Nightly triage finished"

    if ($After -eq "none") {
        exit 0
    }
    if (-not (Test-WokenByThisTask)) {
        Write-Log "Not powering off: the PC was already on (this task did not wake it)."
        exit 0
    }
    if (-not (Test-UserIdleEnoughToPowerOff)) {
        Write-Log "Not powering off: keyboard/mouse used in the last 15 minutes."
        exit 0
    }
    if ($After -eq "hibernate") {
        Write-Log "Hibernating (Windows can wake this with the scheduled task)."
        shutdown.exe /h
        exit 0
    }
    Write-Log "Shutting down (S5). Next power-on must come from Gigabyte Resume by Alarm, not Windows."
    shutdown.exe /s /t 0
    exit 0
} catch {
    Open-RunLog
    Write-Log "FAILED: $($_.Exception.Message)"
    try { & $Lms unload --all } catch { }
    exit 1
}
finally {
    # Only the run that wrote the marker removes it (a skipped second start must leave the first one's).
    if ($script:MarkedRunning) { Remove-Item $RunningFile -ErrorAction SilentlyContinue }
}
