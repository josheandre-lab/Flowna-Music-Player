<#
  Build Android Release APK using Android Studio's bundled JDK
  - Detects common Studio JDK locations and sets JAVA_HOME for the current session
  - Runs Gradle release build with profiling
  - Copies the resulting APK to the user's desired location:
    C:\Users\AKNYLMZ\Desktop\Flowna Music Player\app\build\outputs\apk\release\app-release.apk
  Run from: capacitor-ui/android
#>

$possiblePaths = @(
  "$env:ProgramFiles\\Android\\Android Studio\\jbr",
  "$env:ProgramFiles\\Android\\Android Studio\\jre",
  "$env:ProgramFiles(x86)\\Android\\Android Studio\\jbr",
  "$env:ProgramFiles(x86)\\Android\\Android Studio\\jre",
  "$env:LOCALAPPDATA\\JetBrains\\AndroidStudio\\jbr",
  "$env:LOCALAPPDATA\\Google\\AndroidStudio\\jbr",
  "$env:LOCALAPPDATA\\AndroidStudio\\jbr"
)

$studioJdkPath = $null
foreach ($p in $possiblePaths) {
  if (Test-Path $p) {
    $javaExe = Join-Path $p "bin\\java.exe"
    if (Test-Path $javaExe) {
      $studioJdkPath = $p
      break
    }
  }
}

if (-not $studioJdkPath) {
  Write-Host "Android Studio JDK not found in known locations. Please install or specify JAVA_HOME manually." -ForegroundColor Yellow
  exit 1
}
Set-Item Env:JAVA_HOME $studioJdkPath
& "$studioJdkPath\\bin\\java.exe" -version > $null 2>&1
echo "JAVA_HOME set to $studioJdkPath"
$env:JAVA_HOME = $studioJdkPath
$env:PATH = "$studioJdkPath\\bin;" + $env:PATH

Write-Host "Starting release build with JAVA_HOME=$studioJdkPath"
./gradlew.bat assembleRelease --profile

# Copy APK to destination
$source = "app/build/outputs/apk/release/app-release.apk"
$destDir = "C:\\Users\\AKNYLMZ\\Desktop\\Flowna Music Player\\app\\build\\outputs\\apk\\release"
$dest = Join-Path $destDir "app-release.apk"
if (Test-Path $source) {
  if (-not (Test-Path $destDir)) { New-Item -Path $destDir -ItemType Directory | Out-Null }
  Copy-Item -Path $source -Destination $dest -Force
  Write-Host "APK copied to $dest"
} else {
  Write-Warning "Source APK not found at $source yet."
}

exit 0
