# Probe what this Windows machine can do for wake-from-sleep vs wake-from-off.
# Read-only. Does not change BIOS or power settings.

$cs = Get-CimInstance Win32_ComputerSystem
$bios = Get-CimInstance Win32_BIOS
$board = Get-CimInstance Win32_BaseBoard
Write-Host "Manufacturer=$($cs.Manufacturer)"
Write-Host "Model=$($cs.Model)"
Write-Host "BIOS=$($bios.Manufacturer) $($bios.SMBIOSBIOSVersion)"
Write-Host "Board=$($board.Manufacturer) $($board.Product)"
Write-Host "--- powercfg /a ---"
powercfg /a
Write-Host "--- waketimers ---"
powercfg /waketimers
Write-Host "--- last wake ---"
powercfg /lastwake
Write-Host "--- wake armed ---"
powercfg -devicequery wake_armed
Write-Host "--- hibernate ---"
powercfg /query SCHEME_CURRENT SUB_SLEEP HIBERNATEIDLE 2>$null
Write-Host "--- wake timers AC ---"
powercfg /query SCHEME_CURRENT SUB_SLEEP RTCWAKE 2>$null
