# Stop the email-tool JVM listening on the API port. Does not kill LM Studio or other apps.
# Spring itself releases the port on a clean shutdown. What usually holds 8080 is a leftover
# forked java.exe after Ctrl+C on mvnw spring-boot:run.

param(
    [int]$Port = 8080,
    [int]$WaitSeconds = 15
)

$ErrorActionPreference = "Stop"

function Get-ListenPids {
    $rows = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    return @($rows | Select-Object -ExpandProperty OwningProcess -Unique)
}

function Get-ProcessRow([int]$Id) {
    return Get-CimInstance Win32_Process -Filter "ProcessId=$Id"
}

function Test-EmailToolJvm([string]$CommandLine, [string]$Name) {
    if ($Name -ne "java.exe") {
        return $false
    }
    if ([string]::IsNullOrWhiteSpace($CommandLine)) {
        return $true
    }
    return $CommandLine -match "EmailToolApplication" -or
        $CommandLine -match "email-tool" -or
        $CommandLine -match "spring-boot-"
}

$pids = @(Get-ListenPids)
if ($pids.Count -eq 0) {
    Write-Host "Port $Port is free"
    exit 0
}

foreach ($listenPid in $pids) {
    $proc = Get-ProcessRow $listenPid
    if (-not $proc) {
        continue
    }
    if (-not (Test-EmailToolJvm $proc.CommandLine $proc.Name)) {
        throw "Port $Port is held by PID $($proc.ProcessId) $($proc.Name) - not the email-tool JVM. Not killing it. Command: $($proc.CommandLine)"
    }
    Write-Host "Stopping PID $($proc.ProcessId) ($($proc.Name)) holding port $Port"
    Stop-Process -Id $proc.ProcessId -Force
    if ($proc.ParentProcessId) {
        $parent = Get-ProcessRow $proc.ParentProcessId
        if ($parent -and $parent.Name -eq "java.exe" -and $parent.CommandLine -match "spring-boot") {
            Write-Host "Stopping Maven parent PID $($parent.ProcessId)"
            Stop-Process -Id $parent.ProcessId -Force -ErrorAction SilentlyContinue
        }
    }
}

$deadline = (Get-Date).AddSeconds($WaitSeconds)
while ((Get-Date) -lt $deadline) {
    if ((Get-ListenPids).Count -eq 0) {
        Write-Host "Port $Port is free"
        exit 0
    }
    Start-Sleep -Milliseconds 200
}

throw "Port $Port still in use after ${WaitSeconds}s"
