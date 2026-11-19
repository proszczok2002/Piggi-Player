[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [string]$ArchiveLabel
)

$ErrorActionPreference = 'Stop'

$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\')
$archiveBase = [IO.Path]::GetFullPath((Join-Path $projectRoot 'niepotrzebne')).TrimEnd('\')

if ([string]::IsNullOrWhiteSpace($ArchiveLabel)) {
    $ArchiveLabel = Get-Date -Format 'yyyy-MM-dd-HHmmss-release-cleanup'
}

if ($ArchiveLabel.IndexOfAny([IO.Path]::GetInvalidFileNameChars()) -ge 0 -or
    $ArchiveLabel -in @('.', '..')) {
    throw 'ArchiveLabel musi być bezpieczną nazwą pojedynczego katalogu.'
}

$archiveRoot = [IO.Path]::GetFullPath((Join-Path $archiveBase $ArchiveLabel)).TrimEnd('\')
if (-not $archiveRoot.StartsWith($archiveBase + '\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Katalog archiwum wychodzi poza folder niepotrzebne.'
}

$releaseDir = Join-Path $projectRoot 'releases'
$manifestPath = Join-Path $releaseDir 'piggi-player-update.json'
$validationWhatIf = $WhatIfPreference
$WhatIfPreference = $false
try {
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Brak manifestu bieżącego wydania: $manifestPath"
    }

    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding utf8 | ConvertFrom-Json
    $activeApkName = [string]$manifest.apk
    $activeApkPath = Join-Path $releaseDir $activeApkName
    if ([string]::IsNullOrWhiteSpace($activeApkName) -or
        -not (Test-Path -LiteralPath $activeApkPath -PathType Leaf)) {
        throw 'Manifest nie wskazuje istniejącego aktywnego APK.'
    }

    $activeHash = (Get-FileHash -LiteralPath $activeApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($activeHash -ne ([string]$manifest.sha256).ToLowerInvariant()) {
        throw 'SHA-256 aktywnego APK nie zgadza się z manifestem. Przerwano porządki.'
    }

    $generatedTargets = @(
        [pscustomobject]@{ Source = (Join-Path $projectRoot '.gradle'); Name = '.gradle' },
        [pscustomobject]@{ Source = (Join-Path $projectRoot '.kotlin'); Name = '.kotlin' },
        [pscustomobject]@{ Source = (Join-Path $projectRoot 'build'); Name = 'root-build' },
        [pscustomobject]@{ Source = (Join-Path $projectRoot 'app\build'); Name = 'app-build' }
    )

    $existingGenerated = @($generatedTargets | Where-Object {
        Test-Path -LiteralPath $_.Source
    })

    $obsoleteReleaseArtifacts = @(
        Get-ChildItem -LiteralPath $releaseDir -File -Force |
            Where-Object {
                $_.Extension -in @('.apk', '.aab', '.apks') -and
                $_.Name -ne $activeApkName
            }
    )
} finally {
    $WhatIfPreference = $validationWhatIf
}

if ($existingGenerated.Count -eq 0 -and $obsoleteReleaseArtifacts.Count -eq 0) {
    Write-Output "Projekt jest już czysty. Aktywny APK: $activeApkName"
    return
}

if (Test-Path -LiteralPath $archiveRoot) {
    throw "Katalog archiwum już istnieje: $archiveRoot"
}

if ($PSCmdlet.ShouldProcess($archiveRoot, 'Utworzenie bezpiecznego archiwum')) {
    New-Item -ItemType Directory -Path $archiveRoot -Force | Out-Null
}

if ($existingGenerated.Count -gt 0) {
    $generatedArchive = Join-Path $archiveRoot 'generated-build'
    if ($PSCmdlet.ShouldProcess($generatedArchive, 'Utworzenie katalogu na odtwarzalne wyniki buildu')) {
        New-Item -ItemType Directory -Path $generatedArchive -Force | Out-Null
    }

    foreach ($target in $existingGenerated) {
        $source = [IO.Path]::GetFullPath($target.Source)
        if (-not $source.StartsWith($projectRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
            throw "Źródło wychodzi poza projekt: $source"
        }

        $destination = Join-Path $generatedArchive $target.Name
        if (Test-Path -LiteralPath $destination) {
            throw "Cel już istnieje: $destination"
        }

        if ($PSCmdlet.ShouldProcess($source, "Przeniesienie do $destination")) {
            Move-Item -LiteralPath $source -Destination $destination
        }
    }
}

if ($obsoleteReleaseArtifacts.Count -gt 0) {
    $releaseArchive = Join-Path $archiveRoot 'old-release-artifacts'
    if ($PSCmdlet.ShouldProcess($releaseArchive, 'Utworzenie katalogu na stare paczki')) {
        New-Item -ItemType Directory -Path $releaseArchive -Force | Out-Null
    }

    foreach ($artifact in $obsoleteReleaseArtifacts) {
        $destination = Join-Path $releaseArchive $artifact.Name
        if ($PSCmdlet.ShouldProcess($artifact.FullName, "Przeniesienie do $destination")) {
            Move-Item -LiteralPath $artifact.FullName -Destination $destination
        }
    }
}

[pscustomobject]@{
    ActiveVersion = "$($manifest.versionName) ($($manifest.versionCode))"
    ActiveApk = $activeApkName
    ActiveSha256 = $activeHash
    Archive = $archiveRoot
    GeneratedDirectoriesMoved = $existingGenerated.Count
    OldReleaseArtifactsMoved = $obsoleteReleaseArtifacts.Count
}
