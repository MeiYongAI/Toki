#Requires -Version 7.0
<#
.SYNOPSIS
Validates and optionally mirrors a published Toki release to the module repository.
.PARAMETER Tag
Source release tag in MeiYongAI/Toki, such as v1.0.3.
.PARAMETER AaptPath
Path to the Android SDK Build Tools aapt executable.
.PARAMETER Publish
Creates and publishes the module release after validating its uploaded assets.
.OUTPUTS
System.String. Validation results and the published release URL when requested.
.NOTES
Callers: maintainer command line, documented in docs/releasing.md.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^v\d+\.\d+\.\d+$')][string]$Tag,
    [Parameter(Mandatory)][string]$AaptPath,
    [switch]$Publish
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-GitHub {
    <#
    .SYNOPSIS
    Runs GitHub CLI and stops on an unsuccessful exit status.
    .PARAMETER Arguments
    String array containing the GitHub CLI command and its arguments.
    .OUTPUTS
    System.String[]. Standard output from GitHub CLI.
    .NOTES
    Callers: the script body in scripts/sync-module-release.ps1.
    #>
    param([Parameter(Mandatory)][string[]]$Arguments)
    $output = & gh @Arguments
    if ($LASTEXITCODE -ne 0) { throw "GitHub CLI failed: $($Arguments[0])" }
    return $output
}

Get-Command gh -ErrorAction Stop | Out-Null
if (!(Test-Path -LiteralPath $AaptPath -PathType Leaf)) { throw 'aapt executable not found.' }
$sourceRepo = 'MeiYongAI/Toki'
$targetRepo = 'Xposed-Modules-Repo/io.github.meiyongai.toki'
$directory = Join-Path $PSScriptRoot "../work/release-sync/$Tag"
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$directory = (Resolve-Path -LiteralPath $directory).Path
$release = Invoke-GitHub @('release', 'view', $Tag, '-R', $sourceRepo, '--json', 'name,body,isDraft,isPrerelease,assets,url') | ConvertFrom-Json
if ($release.isDraft -or $release.isPrerelease) { throw 'Only published stable releases are supported.' }
$apkAsset = @($release.assets | Where-Object name -eq 'app-release.apk')
$checksumAsset = @($release.assets | Where-Object name -eq 'SHA256SUMS.txt')
if ($apkAsset.Count -ne 1 -or $checksumAsset.Count -ne 1) { throw 'Required APK or checksum asset is missing.' }
if ($apkAsset[0].contentType -ne 'application/vnd.android.package-archive') { throw 'Incorrect APK content type.' }
Invoke-GitHub @('release', 'download', $Tag, '-R', $sourceRepo, '-D', $directory, '-p', 'app-release.apk', '-p', 'SHA256SUMS.txt', '--clobber')
$apk = Join-Path $directory 'app-release.apk'
$checksums = Join-Path $directory 'SHA256SUMS.txt'
$apkHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
$checksumHash = (Get-FileHash -LiteralPath $checksums -Algorithm SHA256).Hash.ToLowerInvariant()
if ($apkAsset[0].digest -ne "sha256:$apkHash" -or $checksumAsset[0].digest -ne "sha256:$checksumHash") {
    throw 'Downloaded files do not match the GitHub asset digests.'
}
$checksumLines = @(Get-Content -LiteralPath $checksums | Where-Object { $_ -match '^([0-9a-fA-F]{64})\s+\*?app-release\.apk$' })
if ($checksumLines.Count -ne 1 -or ($checksumLines[0] -split '\s+')[0] -ne $apkHash) { throw 'APK checksum verification failed.' }
$badging = & $AaptPath dump badging $apk
if ($LASTEXITCODE -ne 0) { throw 'aapt could not inspect the APK.' }
$packageLine = @($badging | Where-Object { $_ -match '^package:' })
if ($packageLine.Count -ne 1 -or $packageLine[0] -notmatch "name='io\.github\.meiyongai\.toki' versionCode='(\d+)' versionName='([^']+)'") {
    throw 'The APK package or version metadata is invalid.'
}
$versionCode = $Matches[1]
$versionName = $Matches[2]
if ($Tag -ne "v$versionName") { throw 'Source tag and APK version name differ.' }
$targetTag = "$versionCode-$versionName"
$archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
try {
    $entry = $archive.GetEntry('META-INF/xposed/java_init.list')
    $properties = $archive.GetEntry('META-INF/xposed/module.prop')
    if ($null -eq $entry -or $entry.Length -eq 0 -or $null -eq $properties) { throw 'Xposed module metadata is missing.' }
    $reader = [System.IO.StreamReader]::new($properties.Open())
    try { $moduleProperties = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($moduleProperties -notmatch '(?m)^minApiVersion=\d+\s*$' -or $moduleProperties -notmatch '(?m)^targetApiVersion=\d+\s*$') {
        throw 'Xposed API version metadata is missing.'
    }
} finally { $archive.Dispose() }
$body = $release.body.TrimEnd() + "`n`nSource release: $($release.url)`n"
$notes = Join-Path $directory 'release-notes.md'
[System.IO.File]::WriteAllText($notes, $body)
Write-Output "Validated $sourceRepo@$Tag -> $targetRepo@$targetTag; SHA-256 $apkHash"
if (!$Publish) { return }

$existing = Invoke-GitHub @('release', 'list', '-R', $targetRepo, '--limit', '100', '--json', 'tagName') | ConvertFrom-Json
if (@($existing | Where-Object tagName -eq $targetTag).Count) { throw 'Target release already exists; inspect it before making changes.' }
Invoke-GitHub @('release', 'create', $targetTag, '-R', $targetRepo, '--draft', '--title', $release.name, '--notes-file', $notes, $apk, $checksums)
$uploaded = Invoke-GitHub @('release', 'view', $targetTag, '-R', $targetRepo, '--json', 'assets') | ConvertFrom-Json
foreach ($asset in @(@{ Name = 'app-release.apk'; Hash = $apkHash }, @{ Name = 'SHA256SUMS.txt'; Hash = $checksumHash })) {
    $remote = @($uploaded.assets | Where-Object name -eq $asset.Name)
    if ($remote.Count -ne 1 -or $remote[0].digest -ne "sha256:$($asset.Hash)") { throw 'Uploaded asset verification failed; the release remains a draft.' }
    if ($asset.Name -eq 'app-release.apk' -and $remote[0].contentType -ne 'application/vnd.android.package-archive') {
        throw 'Uploaded APK content type is incorrect; the release remains a draft.'
    }
}
Invoke-GitHub @('release', 'edit', $targetTag, '-R', $targetRepo, '--draft=false', '--latest')
Write-Output "https://github.com/$targetRepo/releases/tag/$targetTag"
