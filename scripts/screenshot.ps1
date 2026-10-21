param(
    [string]$OutputFile = "screen.png"
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$device = "192.168.1.100:35283"

for ($i = 0; $i -lt 3; $i++) {
    $out = & $adb connect $device 2>&1
    if ($out -match "connected") { break }
    Start-Sleep -Milliseconds 400
}
& $adb -s $device shell screencap -p /sdcard/temp_screen.png
& $adb -s $device pull /sdcard/temp_screen.png $OutputFile | Out-Null
& $adb -s $device shell rm /sdcard/temp_screen.png
Write-Host "Saved binary screenshot to $OutputFile"
