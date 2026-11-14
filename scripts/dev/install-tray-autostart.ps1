# Start email-tool (backend + UI + tray icon) at every Windows logon.
#
#   .\scripts\dev\install-tray-autostart.ps1              add to Startup and start it now
#   .\scripts\dev\install-tray-autostart.ps1 -NoStart     add to Startup only
#   .\scripts\dev\install-tray-autostart.ps1 -Remove      take it out of Startup and stop it
#
# Uses a shortcut in the per-user Startup folder, so no administrator rights are needed.
# Run scripts\dev\build-app.ps1 first. Rebuild + re-run this script to update.
# Backend log: results\nightly\backend.log

param(
    [switch]$Remove,
    [switch]$NoStart
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$BackendDir = Join-Path $RepoRoot "backend"
$Jar = Join-Path $BackendDir "target\backend-0.0.1-SNAPSHOT.jar"
$LogFile = Join-Path $RepoRoot "results\nightly\backend.log"
$Shortcut = Join-Path ([Environment]::GetFolderPath("Startup")) "Email Triage.lnk"
$FreePort = Join-Path $PSScriptRoot "free-backend-port.ps1"

if ($Remove) {
    if (Test-Path $Shortcut) {
        Remove-Item $Shortcut
        Write-Host "Removed $Shortcut"
    }
    & $FreePort
    exit 0
}

if (-not (Test-Path $Jar)) {
    throw "Missing $Jar. Run scripts\dev\build-app.ps1 first."
}

# javaw: no console window. Prefer JAVA_HOME, then PATH.
$javaw = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\javaw.exe"
    if (Test-Path $candidate) { $javaw = $candidate }
}
if (-not $javaw) {
    $cmd = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($cmd) { $javaw = $cmd.Source }
}
if (-not $javaw) {
    throw "javaw.exe not found. Install a JDK 21+ or set JAVA_HOME."
}

New-Item -ItemType Directory -Force -Path (Split-Path $LogFile) | Out-Null

# The working directory must be backend\ : application.yml uses ../config, ./data and so on.
$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut($Shortcut)
$link.TargetPath = $javaw
$link.Arguments = "-jar `"$Jar`" --logging.file.name=`"$LogFile`""
$link.WorkingDirectory = $BackendDir
$link.WindowStyle = 7
$link.Description = "Email triage: backend, UI and tray icon"
$link.Save()
Write-Host "Autostart shortcut: $Shortcut"

if (-not $NoStart) {
    & $FreePort
    Start-Process -FilePath $javaw -ArgumentList $link.Arguments -WorkingDirectory $BackendDir -WindowStyle Hidden
    Write-Host "Started. The tray icon appears in about 20 seconds; click it to open http://localhost:17432/"
}
