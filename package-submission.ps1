$ErrorActionPreference = 'Stop'
$delivery = Join-Path $PSScriptRoot 'delivery'
$entries = @(
    '.git', '.gitignore', 'app/src', 'app/build.gradle.kts', 'gradle',
    'gradlew', 'gradlew.bat', 'gradle.properties', 'settings.gradle.kts',
    'README.md', 'AI-USAGE.md', 'package-submission.ps1'
)

if (!(Test-Path -LiteralPath (Join-Path $PSScriptRoot '.git') -PathType Container)) {
    throw 'Complete the Git commit in this project before packaging.'
}
if (Test-Path -LiteralPath (Join-Path $PSScriptRoot '.git/index.lock')) {
    throw 'Wait for the current Git operation to finish before packaging.'
}

New-Item -ItemType Directory -Path $delivery -Force | Out-Null
$archive = Join-Path $delivery 'PulseWear-source.zip'
if (Test-Path -LiteralPath $archive) {
    throw "An archive already exists at $archive. Choose another name before packaging again."
}

& tar.exe -a -cf $archive -C $PSScriptRoot @entries
if ($LASTEXITCODE -ne 0) { throw 'Could not create the source archive.' }
if ((Get-Item -LiteralPath $archive).Length -gt 50000000) {
    throw 'The archive exceeds the 50 MB submission limit.'
}
Get-Item -LiteralPath $archive
