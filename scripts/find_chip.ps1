Add-Type -AssemblyName System.Drawing
$bmp = [System.Drawing.Bitmap]::FromFile("c:\Users\Admin\Music\CALCULATOR AND CONVERSION PLATFORM FOR ANDROID\curr_opened.png")
0..40 | ForEach-Object {
    $x = 350 + $_ * 15
    $c = $bmp.GetPixel($x, 385)
    if ($c.G -gt 40 -or $c.B -gt 40) {
        Write-Host "X=$x R=$($c.R) G=$($c.G) B=$($c.B)"
    }
}
