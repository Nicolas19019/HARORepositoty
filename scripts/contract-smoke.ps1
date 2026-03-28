$ErrorActionPreference = 'Stop'

param(
  [Parameter(Mandatory = $false)]
  [string]$ApiBase = "https://harorepositoty2-590358146556.europe-west1.run.app",

  [Parameter(Mandatory = $false)]
  [string]$Email = ""
)

function New-TempJsonFile([string]$json) {
  $name = "haro-" + [Guid]::NewGuid().ToString("N").Substring(0, 10) + ".json"
  $path = Join-Path $env:TEMP $name
  Set-Content -Path $path -Value $json -Encoding ascii
  return $path
}

function Post-Json([string]$url, [string]$json) {
  $file = New-TempJsonFile $json
  try {
    return curl.exe -s -i -H "Content-Type: application/json" -X POST $url --data-binary "@$file"
  } finally {
    Remove-Item -Force -ErrorAction SilentlyContinue $file
  }
}

if (-not $Email) {
  $Email = "dummy+" + [Guid]::NewGuid().ToString("N").Substring(0, 8) + "@example.com"
}

$api = $ApiBase.TrimEnd("/")

Write-Host ("API_BASE=" + $api)
Write-Host ("EMAIL=" + $Email)

$linkBody = @{ email = $Email; baseUrl = $api } | ConvertTo-Json -Compress
$linkRaw = Post-Json "$api/api/verification/contract/link" $linkBody
Write-Host "`n== /contract/link =="
Write-Host $linkRaw

$linkJson = ($linkRaw -split "`r?`n`r?`n", 2)[1] | ConvertFrom-Json
$code = $linkJson.code
Write-Host ("CODE=" + $code)

$accessBody = @{ email = $Email; code = $code } | ConvertTo-Json -Compress
$accessRaw = Post-Json "$api/api/verification/contract/access" $accessBody
Write-Host "`n== /contract/access =="
Write-Host $accessRaw

$completeBody = @{ email = $Email; code = $code } | ConvertTo-Json -Compress
$completeRaw = Post-Json "$api/api/verification/contract/complete" $completeBody
Write-Host "`n== /contract/complete =="
Write-Host $completeRaw

