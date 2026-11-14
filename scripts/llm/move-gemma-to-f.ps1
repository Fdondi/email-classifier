# Replace F:\LLMs gemma-4-26B symlink (points at E: HDD) with real files on F: SSD.
# Leaves E: originals until -RemoveSource is passed after a successful copy.
param(
    [switch]$RemoveSource,
    [switch]$SkipUnload
)

$ErrorActionPreference = "Stop"
$Lms = Join-Path $env:USERPROFILE ".cache\lm-studio\bin\lms.exe"
$SrcDir = "E:\LLMs\lmstudio-community\gemma-4-26B-A4B-it-GGUF"
$DstDir = "F:\LLMs\lmstudio-community\gemma-4-26B-A4B-it-GGUF"
$Files = @(
    "gemma-4-26B-A4B-it-Q4_K_M.gguf",
    "mmproj-gemma-4-26B-A4B-it-BF16.gguf"
)

function Write-Log([string]$Message) {
    Write-Host ("[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Message)
}

if (-not (Test-Path $SrcDir)) { throw "Source missing: $SrcDir" }
New-Item -ItemType Directory -Force -Path $DstDir | Out-Null

if (-not $SkipUnload -and (Test-Path $Lms)) {
    Write-Log "Unloading LM Studio models so files are not locked"
    & $Lms unload --all
    Start-Sleep -Seconds 3
}

foreach ($name in $Files) {
    $src = Join-Path $SrcDir $name
    $dst = Join-Path $DstDir $name
    if (-not (Test-Path $src)) { throw "Missing source file: $src" }

    $srcItem = Get-Item -LiteralPath $src -Force
    if ($srcItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Source is a reparse point, refusing to copy: $src"
    }

    if (Test-Path $dst) {
        $dstItem = Get-Item -LiteralPath $dst -Force
        if ($dstItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            Write-Log "Removing symlink $dst -> $($dstItem.Target -join ',')"
            Remove-Item -LiteralPath $dst -Force
        } elseif ($dstItem.Length -eq $srcItem.Length) {
            Write-Log "Already present with matching size: $dst"
            continue
        } else {
            Write-Log "Removing incomplete/mismatched $dst ($($dstItem.Length) bytes)"
            Remove-Item -LiteralPath $dst -Force
        }
    }

    Write-Log "Copying $name ($([math]::Round($srcItem.Length/1GB, 2)) GB) E: -> F:"
    $sw = [Diagnostics.Stopwatch]::StartNew()
    Copy-Item -LiteralPath $src -Destination $dst -Force
    $sw.Stop()
    $dstItem = Get-Item -LiteralPath $dst -Force
    if ($dstItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw "Destination is still a reparse point after copy: $dst"
    }
    if ($dstItem.Length -ne $srcItem.Length) {
        throw "Size mismatch for $name : src=$($srcItem.Length) dst=$($dstItem.Length)"
    }
    $mbps = if ($sw.Elapsed.TotalSeconds -gt 0) { [math]::Round(($srcItem.Length/1MB) / $sw.Elapsed.TotalSeconds, 1) } else { 0 }
    Write-Log "OK $name in $([math]::Round($sw.Elapsed.TotalSeconds,1))s (~$mbps MB/s)"
}

Write-Log "Verified real files on F:"
Get-ChildItem -LiteralPath $DstDir -Force | ForEach-Object {
    $kind = if ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) { "LINK" } else { "FILE" }
    Write-Host ("  {0}  {1,6:N2} GB  {2}" -f $kind, ($_.Length/1GB), $_.Name)
}

if ($RemoveSource) {
    foreach ($name in $Files) {
        $src = Join-Path $SrcDir $name
        if (Test-Path $src) {
            Write-Log "Removing source $src"
            Remove-Item -LiteralPath $src -Force
        }
    }
    Write-Log "E: originals removed. Relink with: New-Item -ItemType SymbolicLink if you still need E: paths."
} else {
    Write-Log "E: originals kept. Re-run with -RemoveSource after you confirm load from F: is fast."
}
