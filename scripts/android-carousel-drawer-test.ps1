# carousel + drawer flow test (adb + installed debug apk)
$ErrorActionPreference = 'Stop'
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = 'com.nekokata.app'
$act = 'com.nekokata.app/.MainActivity'

if (-not (Test-Path $adb)) { throw "adb not found" }

$pass = 0
$fail = 0
function Step($name, [scriptblock]$action) {
  Write-Host "`n--- $name ---"
  try {
    & $action
    if (Test-Crash) {
      Write-Host "FAIL: $name (crash)"
      $script:fail++
      return $false
    }
    Write-Host "PASS: $name"
    $script:pass++
    return $true
  } catch {
    Write-Host "FAIL: $name ($_)"
    $script:fail++
    return $false
  }
}
function Wait-App($ms = 1200) { Start-Sleep -Milliseconds $ms }
function Tap($x, $y) { & $adb shell input tap $x $y | Out-Null }
function Swipe-L($y = 1300) { & $adb shell input swipe 920 $y 280 $y 280 | Out-Null }
function Swipe-R($y = 1300) { & $adb shell input swipe 280 $y 920 $y 280 | Out-Null }
function Back() { & $adb shell input keyevent 4 | Out-Null }
function Test-Crash {
  $log = & $adb logcat -d -t 60 2>&1 | Out-String
  return $log -match 'FATAL EXCEPTION'
}
function Clear-Log { & $adb logcat -c | Out-Null }

# 1080x2400 reference scales if needed
$sizeLine = (& $adb shell wm size 2>&1) -replace 'Physical size: ', ''
$w, $h = $sizeLine.Trim().Split('x') | ForEach-Object { [int]$_ }
$scaleX = $w / 1080.0
$scaleY = $h / 2400.0
function Sx($x) { [int]($x * $scaleX) }
function Sy($y) { [int]($y * $scaleY) }

Write-Host "=== Carousel + drawer flow test ==="
Write-Host "Display: ${w}x${h}"

Clear-Log
& $adb shell am force-stop $pkg | Out-Null
& $adb shell am start -n $act -W | Out-Null
Wait-App 7000

Step "Cold start stable" { Wait-App 500 }

Step "Carousel swipe left x4" {
  1..4 | ForEach-Object { Swipe-L (Sy 1300); Wait-App 900 }
}

Step "Carousel swipe right x4" {
  1..4 | ForEach-Object { Swipe-R (Sy 1300); Wait-App 900 }
}

Step "Rapid carousel swipes (stress)" {
  1..6 | ForEach-Object {
    if ($_ % 2 -eq 0) { Swipe-L (Sy 1400) } else { Swipe-R (Sy 1400) }
    Wait-App 350
  }
  Wait-App 1500
}

Step "Open drawer (hamburger)" {
  Tap (Sx 72) (Sy 200)
  Wait-App 800
}

Step "Drawer: tap Fitness nav item" {
  Tap (Sx 200) (Sy 520)
  Wait-App 2500
}

Step "Carousel on Fitness screen" {
  1..3 | ForEach-Object { Swipe-L (Sy 1300); Wait-App 800 }
}

Step "Open drawer from carousel screen" {
  Tap (Sx 72) (Sy 200)
  Wait-App 800
}

Step "Drawer: tap Profile" {
  Tap (Sx 200) (Sy 1100)
  Wait-App 2500
}

Step "Close drawer via overlay (tap right)" {
  Tap (Sx 900) (Sy 1200)
  Wait-App 1200
}

Step "Carousel after drawer close" {
  1..3 | ForEach-Object { Swipe-L (Sy 1300); Wait-App 800 }
}

Step "Drawer open then immediate carousel (should not crash)" {
  Tap (Sx 72) (Sy 200)
  Wait-App 300
  Swipe-L (Sy 1300)
  Swipe-R (Sy 1300)
  Wait-App 1500
  Tap (Sx 72) (Sy 200)
  Wait-App 1000
}

Step "Select Stats from drawer then swipe" {
  Tap (Sx 200) (Sy 700)
  Wait-App 2500
  1..2 | ForEach-Object { Swipe-L (Sy 1300); Wait-App 800 }
}

Step "Back button from non-home screen" {
  Back
  Wait-App 1200
  Back
  Wait-App 1200
}

Step "Carousel dot area tap (bottom bar)" {
  Tap (Sx 540) (Sy 2280)
  Wait-App 1000
}

Write-Host "`n=== Results: $pass passed, $fail failed ==="
if ($fail -gt 0) { exit 1 }
exit 0
