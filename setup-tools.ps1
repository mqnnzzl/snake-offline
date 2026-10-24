$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force .tools | Out-Null
$packages = @(
    @{ Name = 'build-tools'; Url = 'https://dl.google.com/android/repository/build-tools_r35_windows.zip'; Algorithm = 'SHA1'; Hash = 'af059bb67cf7786f45ee0db85e2d24985df1b4b6' },
    @{ Name = 'platform'; Url = 'https://dl.google.com/android/repository/platform-35_r02.zip'; Algorithm = 'SHA1'; Hash = '0bb560a90a7a2cbd0dd8348224d518b638fe7949' },
    @{ Name = 'jdk'; Url = 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip'; Algorithm = 'SHA256'; Hash = 'e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0' }
)
foreach ($package in $packages) {
    $archive = Join-Path '.tools' ($package.Name + '.zip')
    if (!(Test-Path -LiteralPath $archive)) {
        & curl.exe -L --fail --retry 2 --connect-timeout 20 --max-time 900 $package.Url -o $archive
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $($package.Name)" }
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm $package.Algorithm).Hash -ne $package.Hash) { throw "Checksum mismatch: $archive" }
    Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path '.tools' $package.Name) -Force
}
Write-Host 'Toolchain ready. Run: powershell -ExecutionPolicy Bypass -File build.ps1'
