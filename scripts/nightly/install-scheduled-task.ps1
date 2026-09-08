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
Write-Host ""
Write-Host "Full power-off is BIOS, not Windows. On this Gigabyte Z790 GAMING X AX:"
Write-Host "  Del → Settings → Platform Power → ErP=Disabled, Resume by Alarm=Enabled, 03:00:00 daily"
Write-Host "  Disable Fast Startup in Windows. Auto-logon is required so Gemma can use the GPU after S5."
Write-Host "  Then re-run this installer with -After shutdown"
