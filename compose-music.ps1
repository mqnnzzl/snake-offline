$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$musicJdk = Get-ChildItem -LiteralPath '.tools/jdk' -Directory | Select-Object -First 1
if (!$musicJdk) { throw 'Run setup-tools.ps1 first.' }
New-Item -ItemType Directory -Force build/music,app/src/main/res/raw,dist | Out-Null
& (Join-Path $musicJdk.FullName 'bin/javac.exe') -encoding UTF-8 -d build/music music/ComposeMusic.java
if ($LASTEXITCODE -ne 0) { throw 'Music compiler failed' }
& (Join-Path $musicJdk.FullName 'bin/java.exe') -cp build/music ComposeMusic app/src/main/res/raw/neon_orchard.wav
if ($LASTEXITCODE -ne 0) { throw 'Music render failed' }
Copy-Item -LiteralPath app/src/main/res/raw/neon_orchard.wav -Destination dist/neon-orchard.wav -Force
