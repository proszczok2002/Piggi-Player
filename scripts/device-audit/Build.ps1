param([Parameter(Mandatory=$true)][string]$Sdk, [string]$Jdk = 'C:/Program Files/Java/jdk-25.0.2')
$ErrorActionPreference = 'Stop'
$out = Join-Path $PSScriptRoot '../../app/build/device-audit'
New-Item -ItemType Directory -Force "$out/classes" | Out-Null
$out = (Resolve-Path $out).Path
$bt = Join-Path $Sdk 'build-tools/37.0.0'
$androidJar = Join-Path $Sdk 'platforms/android-37.0/android.jar'
function Check { if ($LASTEXITCODE -ne 0) { throw "Tool failed: $LASTEXITCODE" } }
& "$Jdk/bin/javac.exe" --release 17 -encoding UTF-8 -classpath $androidJar -d "$out/classes" "$PSScriptRoot/AuditInstrumentation.java"
Check
& "$Jdk/bin/jar.exe" cf "$out/audit.jar" -C "$out/classes" .
Check
& "$bt/d8.bat" --lib $androidJar --min-api 29 --output $out "$out/audit.jar"
Check
& "$bt/aapt2.exe" link -I $androidJar --manifest "$PSScriptRoot/AndroidManifest.xml" -o "$out/unsigned.apk"
Check
$pack = @'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], 'a') as z:
    z.write(sys.argv[2], 'classes.dex')
'@
$pack | python - "$out/unsigned.apk" "$out/classes.dex"
Check
if (!(Test-Path "$out/audit.p12")) {
    & "$Jdk/bin/keytool.exe" -genkeypair -keystore "$out/audit.p12" -storepass android -keypass android -alias audit -dname 'CN=Local Piggi audit' -keyalg RSA -validity 3650
    Check
}
& "$bt/apksigner.bat" sign --ks "$out/audit.p12" --ks-key-alias audit --ks-pass pass:android --out "$out/audit.apk" "$out/unsigned.apk"
Check
Write-Output "$out/audit.apk"
