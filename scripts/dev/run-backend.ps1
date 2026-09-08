# Free 8080 if our leftover JVM is still bound, then start the backend in this console.

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$FreePort = Join-Path $PSScriptRoot "free-backend-port.ps1"
$BackendDir = Join-Path $RepoRoot "backend"
$Mvnw = Join-Path $BackendDir "mvnw.cmd"

& $FreePort
Set-Location $BackendDir
& $Mvnw spring-boot:run
