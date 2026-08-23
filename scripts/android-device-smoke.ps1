# device smoke test for emulator or phone (requires adb + installed debug apk)
$ErrorActionPreference = 'Stop'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = 'com.nekokata.app'
$act = 'com.nekokata.app/.MainActivity'

if (-not (Test-Path $adb)) { throw "adb not found at $adb" }

function Wait-App($sec = 4) { Start-Sleep -Seconds $sec }

Write-Host "`n=== nekokata device smoke ===`n"

& $adb logcat -c | Out-Null
& $adb shell am force-stop $pkg | Out-Null
& $adb shell am start -n $act -W | Out-Null
Write-Host "Launched app"
Wait-App 6

$log = & $adb logcat -d -t 200 2>&1 | Out-String
$fatals = $log | Select-String -Pattern 'FATAL EXCEPTION|AndroidRuntime.*FATAL' -AllMatches
$jsErrors = $log | Select-String -Pattern 'chromium.*CONSOLE.*error|Capacitor.*Error' -AllMatches

if ($fatals) {
  Write-Host "FAIL: Native crash detected"
  $fatals | ForEach-Object { Write-Host $_.Line }
  exit 1
} else { Write-Host "OK: No native crash on cold start" }

if ($jsErrors) {
  Write-Host "WARN: Possible JS console errors (review logcat)"
  $jsErrors | Select-Object -First 5 | ForEach-Object { Write-Host $_.Line }
} else { Write-Host "OK: No obvious JS errors in recent logcat" }

# swipe carousel left x3 (main content area ~middle of screen)
$wm = & $adb shell wm size 2>&1
Write-Host "Display: $wm"
& $adb shell input swipe 900 1200 300 1200 250 | Out-Null
Wait-App 1
& $adb shell input swipe 900 1200 300 1200 250 | Out-Null
Wait-App 1
& $adb shell input swipe 300 1200 900 1200 250 | Out-Null
Wait-App 2

$log2 = & $adb logcat -d -t 100 2>&1 | Out-String
if ($log2 | Select-String -Pattern 'FATAL EXCEPTION') {
  Write-Host "FAIL: Crash after carousel swipes"
  exit 1
} else { Write-Host "OK: Survived carousel swipes" }

# open nav drawer (hamburger top-left)
& $adb shell input tap 80 180 | Out-Null
Wait-App 1
& $adb shell input tap 80 180 | Out-Null
Wait-App 1
Write-Host "OK: Nav drawer toggle exercised"

# hardware back a few times
& $adb shell input keyevent 4 | Out-Null
& $adb shell input keyevent 4 | Out-Null
Wait-App 1
$log3 = & $adb logcat -d -t 80 2>&1 | Out-String
if ($log3 | Select-String -Pattern 'FATAL EXCEPTION') {
  Write-Host "FAIL: Crash after back button"
  exit 1
} else { Write-Host "OK: Survived back button presses" }

Write-Host "`nDevice smoke passed (basic). Run the SYS-02 checklist in README for full QA.`n"
exit 0
