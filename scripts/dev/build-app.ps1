# Build the Angular UI and the backend jar (the jar contains the UI, so one process serves both),
# then restart the running app on the new jar.
#
#   .\scripts\dev\build-app.ps1              build, running the backend tests
#   .\scripts\dev\build-app.ps1 -SkipTests   build only
#
# Result: backend\target\backend-0.0.1-SNAPSHOT.jar, running on http://localhost:17432/

param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Frontend = Join-Path $RepoRoot "frontend"
$Backend = Join-Path $RepoRoot "backend"

Push-Location $Frontend
try {
    if (-not (Test-Path "node_modules")) {
        npm install
        if ($LASTEXITCODE -ne 0) { throw "npm install failed" }
    }
    npm run build
    if ($LASTEXITCODE -ne 0) { throw "ng build failed" }
} finally {
    Pop-Location
}

if (Test-Path (Join-Path $RepoRoot "results\nightly\running.txt")) {
    throw "A triage run is in progress (results\nightly\running.txt). Build again when it finishes."
}
# Windows locks the jar while the backend runs from it, so stop the backend before packaging.
& (Join-Path $PSScriptRoot "free-backend-port.ps1")

Push-Location $Backend
try {
    $mvnArgs = @("-q", "package")
    if ($SkipTests) { $mvnArgs += "-DskipTests" }
    & (Join-Path $Backend "mvnw.cmd") @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
} finally {
    Pop-Location
}

$jar = Join-Path $Backend "target\backend-0.0.1-SNAPSHOT.jar"
Write-Host "Built $jar"
# Start the app on the new jar (also refreshes the Startup shortcut).
& (Join-Path $PSScriptRoot "install-tray-autostart.ps1")
