# Build Release APK using Java 17 (Adoptium/Temurin)
# Detects Adoptium Java 17 installation and sets JAVA_HOME

$possibleJdkPaths = @(
    "$env:ProgramFiles\Adoptium\temurin-17.jdk",
    "$env:ProgramFiles\Adoptium\temurin-17.0.12+7-jdk", # common version pattern
    "$env:ProgramFiles\Java\jdk-17",
    "$env:ProgramFiles\Amazon Corretto\jdk17.0.12_8",
    "$env:USERPROFILE\scoop\java\openjdk17",
    "$env:CHOCOLATEY_INSTALL\lib\temurin17\jdk"
)

$jdkPath = $null
foreach ($path in $possibleJdkPaths) {
    if (Test-Path $path) {
        $javaExe = Join-Path $path "bin\java.exe"
        if (Test-Path $javaExe) {
            $jdkPath = $path
            break
        }
    }
}

if (-not $jdkPath) {
    Write-Warning "No Java 17 JDK found in common locations."
    Write-Warning "Please install Adoptium Temurin 17 or adjust the script paths."
    Write-Warning "Checking java -version in PATH:"
    java -version 2>&1
    exit 1
}

Write-Host "Found Java 17 at: $jdkPath"
$env:JAVA_HOME = $jdkPath
$env:PATH = "$jdkPath\bin;$env:PATH"

# Verify
Write-Host "Java version:"
& "$jdkPath\bin\java.exe" -version 2>&1 | Select-String -Pattern "version"

# Clean and build
Write-Host "`nCleaning build..."
./gradlew.bat clean

Write-Host "`nAssembling Release APK..."
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