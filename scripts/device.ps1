param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$CmdArgs
)

$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$device = "192.168.1.100:35283"

# Ensure connected
for ($i = 0; $i -lt 3; $i++) {
    $out = & $adb connect $device 2>&1
    if ($out -match "connected") { break }
    Start-Sleep -Milliseconds 400
}

if ($CmdArgs.Count -eq 0) {
    & $adb -s $device devices
} else {
    & $adb -s $device @CmdArgs
}
