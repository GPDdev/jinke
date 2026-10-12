param([switch]$ToolsOnly, [switch]$BuildDeviceCheck, [switch]$DeviceCheck)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Run-Tool([string]$Tool, [string[]]$Arguments) {
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed: $LASTEXITCODE" }
}

function Install-Package([string]$File, [string]$Hash, [string]$Destination) {
    if (Test-Path -LiteralPath $Destination) { return }
    $archive = Join-Path $PSScriptRoot ".tools/$File"
    if (!(Test-Path -LiteralPath $archive)) {
        Write-Host "Downloading official Android SDK: $File"
        Invoke-WebRequest -UseBasicParsing "https://dl.google.com/android/repository/$File" -OutFile $archive
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA1).Hash -ne $Hash) {
        throw "SDK checksum mismatch: $archive. Remove this file and retry."
    }
    Expand-Archive -LiteralPath $archive -DestinationPath (Split-Path $Destination) -Force
    if (!(Test-Path -LiteralPath $Destination)) { throw "SDK extraction failed: $Destination" }
}

New-Item -ItemType Directory -Force .tools, .tools/platforms, .tools/build-tools | Out-Null
Install-Package 'platform-35_r02.zip' '0bb560a90a7a2cbd0dd8348224d518b638fe7949' "$PSScriptRoot/.tools/platforms/android-35"
Install-Package 'build-tools_r35_windows.zip' 'af059bb67cf7786f45ee0db85e2d24985df1b4b6' "$PSScriptRoot/.tools/build-tools/android-15"
if ($ToolsOnly) { return }

$javaBin = Split-Path (Get-Command javac -ErrorAction Stop).Source
$androidJar = "$PSScriptRoot/.tools/platforms/android-35/android.jar"
$buildTools = "$PSScriptRoot/.tools/build-tools/android-15"
$output = "$PSScriptRoot/build"
New-Item -ItemType Directory -Force "$output/generated", "$output/classes", "$output/dex", "$output/check" | Out-Null

Run-Tool "$javaBin/javac.exe" @('-encoding', 'UTF-8', '--release', '8', '-d', "$output/check", 'app/src/main/java/org/hachile/jinke/Entry.java', 'app/src/main/java/org/hachile/jinke/TextExport.java', 'checks/TextExportCheck.java')
Run-Tool "$javaBin/java.exe" @('-ea', '-cp', "$output/check", 'org.hachile.jinke.TextExportCheck')
Run-Tool "$buildTools/aapt2.exe" @('compile', '--dir', 'app/src/main/res', '-o', "$output/resources.zip")
[xml]$manifest = Get-Content 'app/src/main/AndroidManifest.xml' -Encoding UTF8 -Raw
$manifest.DocumentElement.SetAttribute('package', 'org.hachile.jinke')
$manifest.Save("$output/AndroidManifest.xml")
Run-Tool "$buildTools/aapt2.exe" @('link', '-I', $androidJar, '--manifest', "$output/AndroidManifest.xml", "$output/resources.zip", '--java', "$output/generated", '-o', "$output/resources.apk")
$sources = @(Get-ChildItem app/src/main/java, "$output/generated" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Run-Tool "$javaBin/javac.exe" (@('-encoding', 'UTF-8', '--release', '8', '-classpath', $androidJar, '-d', "$output/classes") + $sources)
Run-Tool "$javaBin/jar.exe" @('cf', "$output/classes.jar", '-C', "$output/classes", '.')
Run-Tool "$buildTools/d8.bat" @('--release', '--min-api', '26', '--lib', $androidJar, '--output', "$output/dex", "$output/classes.jar")

Add-Type -AssemblyName System.IO.Compression.FileSystem
function Add-Dex([string]$Apk, [string]$Dex) {
    $zip = [System.IO.Compression.ZipFile]::Open($Apk, 'Update')
    try {
        $old = $zip.GetEntry('classes.dex')
        if ($old) { $old.Delete() }
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $Dex, 'classes.dex', 'Optimal') | Out-Null
    } finally { $zip.Dispose() }
}
Copy-Item -LiteralPath "$output/resources.apk" -Destination "$output/unsigned.apk" -Force
Add-Dex "$output/unsigned.apk" "$output/dex/classes.dex"
Run-Tool "$buildTools/zipalign.exe" @('-f', '4', "$output/unsigned.apk", "$output/aligned.apk")

New-Item -ItemType Directory -Force .signing | Out-Null
$key = "$PSScriptRoot/.signing/jinke.keystore"
$passwordFile = "$PSScriptRoot/.signing/password.txt"
if (!(Test-Path -LiteralPath $key)) {
    $passwordBytes = New-Object byte[] 24
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($passwordBytes) } finally { $random.Dispose() }
    [System.IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($passwordBytes))
    Run-Tool "$javaBin/keytool.exe" @('-genkeypair', '-keystore', $key, '-storepass:file', $passwordFile, '-keypass:file', $passwordFile, '-alias', 'jinke', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-dname', 'CN=Jinke Local')
}
Run-Tool "$buildTools/apksigner.bat" @('sign', '--ks', $key, '--ks-pass', "file:$passwordFile", '--out', "$output/jinke.apk", "$output/aligned.apk")
Run-Tool "$buildTools/apksigner.bat" @('verify', '--verbose', "$output/jinke.apk")
Write-Host "APK ready: $output/jinke.apk ($((Get-Item "$output/jinke.apk").Length) bytes)"

if ($BuildDeviceCheck -or $DeviceCheck) {
    New-Item -ItemType Directory -Force "$output/test/classes", "$output/test/dex" | Out-Null
    [xml]$testManifest = Get-Content 'app/src/androidTest/AndroidManifest.xml' -Encoding UTF8 -Raw
    $testManifest.DocumentElement.SetAttribute('package', 'org.hachile.jinke.check')
    $testManifest.Save("$output/test/AndroidManifest.xml")
    Run-Tool "$buildTools/aapt2.exe" @('link', '-I', $androidJar, '--manifest', "$output/test/AndroidManifest.xml", '-o', "$output/test/resources.apk")
    Run-Tool "$javaBin/javac.exe" @('-encoding', 'UTF-8', '--release', '8', '-classpath', "$androidJar;$output/classes", '-d', "$output/test/classes", 'app/src/androidTest/java/org/hachile/jinke/StoreCheck.java')
    Run-Tool "$javaBin/jar.exe" @('cf', "$output/test/classes.jar", '-C', "$output/test/classes", '.')
    Run-Tool "$buildTools/d8.bat" @('--min-api', '26', '--lib', $androidJar, '--classpath', "$output/classes.jar", '--output', "$output/test/dex", "$output/test/classes.jar")
    Copy-Item "$output/test/resources.apk" "$output/test/unsigned.apk" -Force
    Add-Dex "$output/test/unsigned.apk" "$output/test/dex/classes.dex"
    Run-Tool "$buildTools/zipalign.exe" @('-f', '4', "$output/test/unsigned.apk", "$output/test/aligned.apk")
    Run-Tool "$buildTools/apksigner.bat" @('sign', '--ks', $key, '--ks-pass', "file:$passwordFile", '--out', "$output/test/check.apk", "$output/test/aligned.apk")
}

if ($DeviceCheck) {
    Install-Package 'platform-tools_r37.0.1-win.zip' 'e03e78b1d80b396f1c3358e31251cb31740e1110' "$PSScriptRoot/.tools/platform-tools"
    $adb = "$PSScriptRoot/.tools/platform-tools/adb.exe"
    Run-Tool $adb @('install', '-r', "$output/jinke.apk")
    Run-Tool $adb @('install', '-r', "$output/test/check.apk")
    $result = & $adb shell am instrument -w org.hachile.jinke.check/org.hachile.jinke.StoreCheck
    $result | Write-Output
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'JINKE_CHECK_OK') { throw 'Device check failed' }
}
