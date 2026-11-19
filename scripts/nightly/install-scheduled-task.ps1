# Register the nightly triage task.
#
# Windows Task Scheduler WakeToRun can wake from SLEEP (S3) or HIBERNATE (S4).
# It cannot power the PC on from a full shutdown (S5). That is the motherboard RTC,
# which is what Linux rtcwake -m off uses. This board is a Gigabyte Z790 GAMING X AX.
#
# BIOS (Del at boot) → Settings → Platform Power:
#   ErP = Disabled          (Enabled kills Resume by Alarm)
#   Resume by Alarm = Enabled
#   Wake up day = 0         (every day)
#   Wake up hour/minute/second = 03:00:00
#
# Also in Windows: disable Fast Startup so "Shut down" is actually S5.
# GPU inference needs a logged-on user, so true-off also needs Windows auto-logon.
#
# Default -After hibernate: looks off, and Windows can wake it without BIOS.
# Pass -After shutdown only after the BIOS alarm and auto-logon are in place.

param(
    [ValidateSet("none", "hibernate", "shutdown")]
    [string]$After = "hibernate"
)

$ErrorActionPreference = "Stop"
$ScriptPath = Join-Path $PSScriptRoot "run-triage.ps1"
$TaskName = "EmailToolNightlyTriage"

if (-not (Test-Path $ScriptPath)) {
    throw "Missing $ScriptPath"
}

$arg = "-NoProfile -ExecutionPolicy Bypass -File `"$ScriptPath`" -After $After"
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $arg -WorkingDirectory $PSScriptRoot
$daily = New-ScheduledTaskTrigger -Daily -At 3:05AM
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -WakeToRun
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited

Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
try {
    $startup = New-ScheduledTaskTrigger -AtStartup
    $startup.Delay = "PT2M"
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger @($daily, $startup) -Settings $settings -Principal $principal -Description "Load Gemma 4 in LM Studio and run email-tool triage" | Out-Null
    Write-Host "Registered '$TaskName' for $env:USERNAME"
    Write-Host "  Triggers: daily 03:05 (wake from sleep/hibernate) and 2 min after boot (BIOS RTC / autologon)"
} catch {
    Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $daily -Settings $settings -Principal $principal -Description "Load Gemma 4 in LM Studio and run email-tool triage" | Out-Null
    Write-Host "Registered '$TaskName' for $env:USERNAME (daily 03:05 only; AtStartup needs an elevated PowerShell)"
}
Write-Host "  After a successful run: $After"
Write-Host "  Script: $ScriptPath"

# Watchdog + catch-up: every 15 minutes (and at logon) run-triage.ps1 makes sure the backend and its
# tray icon are up, then runs triage only if it is due (last success 6 h or more ago) and the GPU is
# free (never on CPU: see run-triage.ps1). It never wakes the PC, and never powers down after a daytime run.
$CatchUpName = "EmailToolTriageCatchUp"
$catchUpArg = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$ScriptPath`" -After none"
$catchUpAction = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $catchUpArg -WorkingDirectory $PSScriptRoot
$hourly = New-ScheduledTaskTrigger -Daily -At 12:00AM
$hourly.Repetition = (New-ScheduledTaskTrigger -Once -At 12:00AM `
        -RepetitionInterval (New-TimeSpan -Minutes 15) -RepetitionDuration (New-TimeSpan -Hours 23 -Minutes 59)).Repetition
$catchUpSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable `
        -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Hours 8)
Unregister-ScheduledTask -TaskName $CatchUpName -Confirm:$false -ErrorAction SilentlyContinue
try {
    $logon = New-ScheduledTaskTrigger -AtLogOn -User $env:USERNAME
    $logon.Delay = "PT5M"
    Register-ScheduledTask -TaskName $CatchUpName -Action $catchUpAction -Trigger @($hourly, $logon) -Settings $catchUpSettings -Principal $principal -Description "Run email-tool triage when 6 h have passed since the last success and the GPU is free" | Out-Null
    Write-Host "Registered '$CatchUpName': checks every 15 min and 5 min after logon; runs at most once per 6 h"
} catch {
    Register-ScheduledTask -TaskName $CatchUpName -Action $catchUpAction -Trigger $hourly -Settings $catchUpSettings -Principal $principal -Description "Run email-tool triage when 6 h have passed since the last success and the GPU is free" | Out-Null
    Write-Host "Registered '$CatchUpName': checks every 15 min; runs at most once per 6 h (logon trigger needs an elevated PowerShell)"
}
Write-Host ""
Write-Host "Full power-off is BIOS, not Windows. On this Gigabyte Z790 GAMING X AX:"
Write-Host "  Del → Settings → Platform Power → ErP=Disabled, Resume by Alarm=Enabled, 03:00:00 daily"
Write-Host "  Disable Fast Startup in Windows. Auto-logon is required so Gemma can use the GPU after S5."
Write-Host "  Then re-run this installer with -After shutdown"
