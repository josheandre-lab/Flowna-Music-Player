# Build Release APK with Java 17 and copy to destination

$javaExe = (Get-Command java).Source
if (-not $javaExe) {
    Write-Error "java command not found in PATH."
    exit 1
}
Write-Host "Found java executable at: $javaExe"

$javaDir = Split-Path $javaExe
$jdkHome = Split-Path $javaDir
$env:JAVA_HOME = $jdkHome
Write-Host "Setting JAVA_HOME to: $env:JAVA_HOME"

$javaBin = "$env:JAVA_HOME\bin"
if (-not ($env:PATH -split ';' -contains $javaBin)) {
    $env:PATH = "$javaBin;$env:PATH"
    Write-Host "Added JAVA_HOME/bin to PATH"
}

Write-Host "Verifying Java setup:"
& java -version 2>&1 | Select-String -Pattern "version"

Write-Host "`nAssembling Release APK..."
./gradlew.bat assembleRelease

# Copy APK to destination
$sourceApk = "app\build\outputs\apk\release\app-release.apk"
$destDir = "C:\Users\AKNYLMZ\Desktop\Flowna Music Player\app\build\outputs\apk\release"
$destApk = Join-Path $destDir "app-release.apk"

if (Test-Path $sourceApk) {
    if (-not (Test-Path $destDir)) {
        New-Item -ItemType Directory -Path $destDir | Out-Null
    }
    Copy-Item -Path $sourceApk -Destination $destApk -Force
    Write-Host "APK copied to $destApk"
} else {
    Write-Warning "APK not found. Build may have failed."
}