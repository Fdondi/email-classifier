# Capture one real Gemma 4 classifier reply. Does not invent JSON.
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$OutDir = Join-Path $Root "testdata\llm"
$Request = Join-Path $OutDir "probe-request.json"
$RawOut = Join-Path $OutDir "gemma-4-classify-response.json"
$ContentOut = Join-Path $OutDir "gemma-4-classify-content.txt"

curl.exe -sS -X POST "http://127.0.0.1:1234/v1/chat/completions" -H "Content-Type: application/json" --data-binary "@$Request" -o $RawOut
if ($LASTEXITCODE -ne 0) {
    throw "curl failed: $LASTEXITCODE"
}
$parsed = Get-Content -Raw $RawOut | ConvertFrom-Json
if ($parsed.error) {
    throw "LM Studio error: $($parsed.error | ConvertTo-Json -Compress)"
}
$content = $parsed.choices[0].message.content
if (-not $content) {
    $content = $parsed.choices[0].message.reasoning_content
}
if (-not $content) {
    $content = $parsed.choices[0].message.reasoning
}
if (-not $content) {
    throw "Gemma returned empty content. Raw saved at $RawOut"
}
Set-Content -Path $ContentOut -Value $content -Encoding utf8
Write-Host "Saved $RawOut"
Write-Host "Saved $ContentOut"
Write-Host $content
