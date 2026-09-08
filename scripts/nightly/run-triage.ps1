# Nightly: start LM Studio, load Gemma 4, run triage, unload.
# Fails out loud if the chat model is not on disk yet (download unfinished).
# Does not invent mail or classification results.
#
# -After none       leave the PC as-is (default for a manual run)
# -After hibernate  Windows equivalent of the plan's suspend; Task Scheduler can wake this
# -After shutdown   real power-off (S5). Windows cannot schedule that; Gigabyte BIOS RTC must
#                   power the machine on. Then this script shuts down again after a successful run.

param(
    [ValidateSet("none", "hibernate", "shutdown")]
    [string]$After = "none",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Lms = Join-Path $env:USERPROFILE ".cache\lm-studio\bin\lms.exe"
$LogDir = Join-Path $RepoRoot "results\nightly"
$Stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$LogFile = Join-Path $LogDir "$Stamp.log"
$LockFile = Join-Path $LogDir "last-success-day.txt"
$BackendUrl = "http://127.0.0.1:8080"
$LlmUrl = "http://127.0.0.1:1234/v1"

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

function Write-Log {
    param([string]$Message)
    $line = "$(Get-Date -Format o) $Message"
    Add-Content -Path $LogFile -Value $line
    Write-Host $line
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
    $models = Get-Json -Url "$LlmUrl/models"
    $ids = @()
    if ($models.data) {
        $ids = @($models.data | ForEach-Object { $_.id })
    }
    $chat = $ids | Where-Object { $_ -and ($_ -notmatch "embed") }
    return $chat
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

function Assert-GpuLooksFree {
    $smi = Get-Command nvidia-smi -ErrorAction SilentlyContinue
    if (-not $smi) {
        Write-Log "nvidia-smi not on PATH; will still try lms load"
        return
    }
    $apps = & nvidia-smi --query-compute-apps=pid,process_name,used_gpu_memory --format=csv,noheader
    if ($LASTEXITCODE -ne 0) {
        Write-Log "nvidia-smi compute-apps query failed; will still try lms load"
        return
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
    if ($foreign.Count -gt 0) {
        throw "GPU is in use by $($foreign -join ', '). Did not load Gemma."
    }
}

try {
    Write-Log "Nightly triage starting. repo=$RepoRoot after=$After"
    if (-not $Force) {
        $hour = [int](Get-Date).ToString("HH")
        if ($hour -lt 2 -or $hour -gt 6) {
            Write-Log "Skipping: local hour $hour is outside 02:00-06:00 (pass -Force to run anyway)."
            exit 0
        }
        $today = Get-Date -Format "yyyy-MM-dd"
        if ((Test-Path $LockFile) -and ((Get-Content $LockFile -Raw).Trim() -eq $today)) {
            Write-Log "Skipping: already succeeded today ($today)."
            exit 0
        }
    }
    if (-not (Test-Path $Lms)) {
        throw "LM Studio CLI not found at $Lms"
    }

    Write-Log "Starting LM Studio server on 1234"
    & $Lms server start --port 1234
    Wait-HttpJson -Url "$LlmUrl/models" -TimeoutSec 60 | Out-Null

    Assert-GpuLooksFree
    Start-BackendIfNeeded

    $modelKey = Get-GemmaKey
    Write-Log "Loading $modelKey"
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

    Write-Log "POST $BackendUrl/api/jobs/triage?source=nightly"
    $job = Invoke-RestMethod -Method Post -Uri "$BackendUrl/api/jobs/triage?source=nightly" -TimeoutSec 21600
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
    Set-Content -Path $LockFile -Value (Get-Date -Format "yyyy-MM-dd")
    Write-Log "Nightly triage finished"

    if ($After -eq "none") {
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
    Write-Log "FAILED: $($_.Exception.Message)"
    try { & $Lms unload --all } catch { }
    exit 1
}
