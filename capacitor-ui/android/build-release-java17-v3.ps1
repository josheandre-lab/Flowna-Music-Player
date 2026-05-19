# Build Release APK using Java 17 from PATH
# Ensures we are using Java 17 and sets JAVA_HOME accordingly

# Get java executable path from WHERE command
$javaExe = (Get-Command java).Source
if (-not $javaExe) {
    Write-Error "java command not found in PATH. Please install Java 17 and add to PATH."
    exit 1
}
Write-Host "Found java executable at: $javaExe"

# Get version
$versionOutput = & $javaExe -version 2>&1
$versionString = $versionOutput -join "`n"
if ($versionString -match 'version "([^"]+)"') {
    $version = $matches[1]
    Write-Host "Java version: $version"
    # Check if it's Java 17 (major version 17)
    if ($version -notmatch '^17(\.|$)') {
        Write-Warning "Detected Java version is not 17.x: $version"
        Write-Warning "Please ensure Java 17 is the first in PATH."
        # We'll continue but note the warning
    }
} else {
    Write-Warning "Could not parse Java version output:"
    Write-Warning $versionString
}

# Determine JDK home: go up two levels from bin\java.exe (typically <jdk>\bin\java.exe)
$javaDir = Split-Path $javaExe
$jdkHome = Split-Path $javaDir
if (Test-Path (Join-Path $jdkHome "lib")) {
    $env:JAVA_HOME = $jdkHome
    Write-Host "Setting JAVA_HOME to: $env:JAVA_HOME"
} else {
    Write-Warning "Could not determine JDK home from java.exe path. Trying to use parent of bin as JAVA_HOME."
    $env:JAVA_HOME = $javaDir
    Write-Host "Setting JAVA_HOME to: $env:JAVA_HOME"
}

# Ensure JAVA_HOME bin is in PATH
$javaBin = "$env:JAVA_HOME\bin"
if (-not ($env:PATH -split ';' -contains $javaBin)) {
    $env:PATH = "$javaBin;$env:PATH"
    Write-Host "Added JAVA_HOME/bin to PATH"
}

# Verify again
Write-Host "`nVerifying Java setup:"
& java -version 2>&1 | Select-String -Pattern "version"

# Clean and build
Write-Host "`nCleaning build..."
./gradlew.bat clean

Write-Host "`nAssembling Release APK with profiling..."
./gradlew.bat assembleRelease --profile

# Copy APK to desired location
$sourceApk = "app\build\outputs\apk\release\app-release.apk"
$destDir = "C:\Users\AKNYLMZ\Desktop\Flowna Music Player\app\build\outputs\apk\release"
$destApk = Join-Path $destDir "app-release.apk"

if (Test-Path $sourceApk) {
    if (-not (Test-Path $destDir)) {
        New-Item -ItemType Directory -Path $destDir | Out-Null
    }
    Copy-Item -Path $sourceApk -Destination $destApk -Force
    Write-Host "`nSUCCESS: APK copied to $destApk"
} else {
    Write-Warning "Source APK not found at $sourceApk"
    Write-Warning "Build may have failed. Check output above."
}