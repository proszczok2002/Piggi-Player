[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [int]::MaxValue)]
    [int]$VersionCode,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$VersionName,

    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Notes,

    [string]$OutputPath
)

$apk = Get-Item -LiteralPath $ApkPath -ErrorAction Stop
if ($apk.Extension -ne '.apk') {
    throw "ApkPath musi wskazywać plik .apk."
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $apk.DirectoryName 'piggi-player-update.json'
}

$manifest = [ordered]@{
    versionCode = $VersionCode
    versionName = $VersionName
    apk = $apk.Name
    sha256 = (Get-FileHash -LiteralPath $apk.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    notes = $Notes
}

$json = $manifest | ConvertTo-Json
$utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText(
    [System.IO.Path]::GetFullPath($OutputPath),
    $json + [Environment]::NewLine,
    $utf8WithoutBom
)

Get-Item -LiteralPath $OutputPath
