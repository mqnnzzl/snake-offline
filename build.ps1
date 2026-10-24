param([switch]$SkipTests)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Set-Location $PSScriptRoot

$jdk = Get-ChildItem -LiteralPath '.tools/jdk' -Directory | Select-Object -First 1
$sdk = Get-ChildItem -LiteralPath '.tools/build-tools' -Directory | Select-Object -First 1
$platform = Get-ChildItem -LiteralPath '.tools/platform' -Directory | Select-Object -First 1
if (!$jdk -or !$sdk -or !$platform) { throw 'Missing toolchain. Run setup-tools.ps1 first.' }
$env:JAVA_HOME = $jdk.FullName
$env:PATH = "$($jdk.FullName)\bin;$env:PATH"
$java = Join-Path $jdk.FullName 'bin/java.exe'
$javac = Join-Path $jdk.FullName 'bin/javac.exe'
$jar = Join-Path $jdk.FullName 'bin/jar.exe'
$keytool = Join-Path $jdk.FullName 'bin/keytool.exe'
$androidJar = Join-Path (Join-Path '.tools/platform' $platform.Name) 'android.jar'
$aapt = Join-Path $sdk.FullName 'aapt2.exe'
$zipalign = Join-Path $sdk.FullName 'zipalign.exe'
$d8 = Join-Path $sdk.FullName 'lib/d8.jar'
$signer = Join-Path $sdk.FullName 'lib/apksigner.jar'

function Invoke-Checked {
    param([string]$Program, [string[]]$Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
}
New-Item -ItemType Directory -Force build,build/classes,build/dex,build/generated,build/tests,dist,.signing | Out-Null
$sources = @(Get-ChildItem -Path app/src/main/java -Recurse -Filter '*.java' | ForEach-Object FullName)
if (!$SkipTests) {
    $testSources = @('app/src/main/java/com/mintarcade/snake/SnakeGame.java') + @(Get-ChildItem -Path tests -Recurse -Filter '*.java' | ForEach-Object FullName)
    Invoke-Checked $javac (@('-encoding', 'UTF-8', '--release', '8', '-d', 'build/tests') + $testSources)
    Invoke-Checked $java @('-cp', 'build/tests', 'com.mintarcade.snake.SnakeGameTest')
}

Invoke-Checked $aapt @('compile', '--dir', 'app/src/main/res', '-o', 'build/resources.zip')
Invoke-Checked $aapt @('link', '-o', 'build/base.apk', '-I', $androidJar, '--manifest', 'app/src/main/AndroidManifest.xml', '--java', 'build/generated', 'build/resources.zip')
$generated = @(Get-ChildItem -Path build/generated -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-Checked $javac (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-Xlint:-options', '-classpath', $androidJar, '-d', 'build/classes') + $sources + $generated)
Invoke-Checked $jar @('cf', 'build/classes.jar', '-C', 'build/classes', '.')
Invoke-Checked $java @('-cp', $d8, 'com.android.tools.r8.D8', '--release', '--min-api', '23', '--lib', $androidJar, '--output', 'build/dex', 'build/classes.jar')
Copy-Item -LiteralPath build/base.apk -Destination build/unsigned.apk -Force
Invoke-Checked $jar @('uf', 'build/unsigned.apk', '-C', 'build/dex', 'classes.dex')
Invoke-Checked $zipalign @('-f', '-p', '4', 'build/unsigned.apk', 'build/aligned.apk')

# Generate a private local signing identity once; reuse it for future app updates.
# Its random password is stored locally and is never written to logs or source code.
$keyPath = Join-Path $PSScriptRoot '.signing/snake.p12'
$passwordPath = Join-Path $PSScriptRoot '.signing/store-password.txt'
if (!(Test-Path -LiteralPath $keyPath)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes); $rng.Dispose()
    [IO.File]::WriteAllText($passwordPath, [Convert]::ToBase64String($bytes))
    Invoke-Checked $keytool @('-genkeypair', '-keystore', $keyPath, '-storetype', 'PKCS12', '-storepass:file', $passwordPath, '-alias', 'snake', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Mint Arcade, OU=Offline Games, O=Mint Arcade, C=CN')
}
if (!(Test-Path -LiteralPath $passwordPath)) { throw 'The signing password file is missing; restore it from your private backup.' }
$apk = 'dist/snake-offline-v1.0.0.apk'
Invoke-Checked $java @('-jar', $signer, 'sign', '--ks', $keyPath, '--ks-pass', "file:$passwordPath", '--ks-key-alias', 'snake', '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'true', '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false', '--out', $apk, 'build/aligned.apk')
Invoke-Checked $java @('-jar', $signer, 'verify', '--verbose', $apk)
Invoke-Checked $zipalign @('-c', '-v', '4', $apk)
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $PSScriptRoot 'dist/SHA256SUMS.txt'), "$hash  snake-offline-v1.0.0.apk`n")
Write-Host "Built and verified: $apk"
