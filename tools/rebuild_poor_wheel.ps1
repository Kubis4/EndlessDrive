# Rebuild wheel_poor: rubber from std, steelie hub from v5.
# Content radius ignores opaque-black backgrounds on 24bpp sources.

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$HubFrac = 0.61
$stdPath = "C:\Users\Kubis\StudioProjects\EndlessDrive\art\wheels\wheel_std_hd_source.png"
$steelPath = "C:\Users\Kubis\StudioProjects\EndlessDrive\art\wheels\wheel_poor_v5_hd_source.png"
if (-not (Test-Path $steelPath)) {
    $steelPath = "C:\Users\Kubis\.cursor\projects\c-Users-Kubis-StudioProjects-EndlessDrive\assets\wheel_poor_v5_hd.png"
}
$outHd = "C:\Users\Kubis\StudioProjects\EndlessDrive\art\wheels\wheel_poor_hd_source.png"
$outPng = "C:\Users\Kubis\StudioProjects\EndlessDrive\app\src\main\res\drawable-nodpi\wheel_poor.png"
$oldWebp = "C:\Users\Kubis\StudioProjects\EndlessDrive\app\src\main\res\drawable-nodpi\wheel_poor.webp"

function Get-Pixels([System.Drawing.Bitmap]$bmp) {
    # Force 32bpp so we can write alpha even if source is 24bpp.
    $conv = New-Object System.Drawing.Bitmap $bmp.Width, $bmp.Height, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($conv)
    $g.Clear([System.Drawing.Color]::Transparent)
    $g.DrawImage($bmp, 0, 0, $bmp.Width, $bmp.Height)
    $g.Dispose()
    $rect = New-Object System.Drawing.Rectangle 0, 0, $conv.Width, $conv.Height
    $data = $conv.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $buf = New-Object byte[] ($data.Stride * $conv.Height)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $buf, 0, $buf.Length)
    $conv.UnlockBits($data)
    $info = @{ Buf = $buf; Stride = $data.Stride; W = $conv.Width; H = $conv.Height; Bmp = $conv }
    return $info
}

function Is-Bg([int]$b, [int]$g, [int]$r, [int]$a) {
    if ($a -lt 8) { return $true }
    # Pure / near-black plate behind the tire (not charcoal rubber with blue tint etc.)
    return ($r -le 6 -and $g -le 6 -and $b -le 6)
}

function Content-Radius($info) {
    $cx = ($info.W - 1) * 0.5
    $cy = ($info.H - 1) * 0.5
    $maxR = 1.0
    for ($y = 0; $y -lt $info.H; $y++) {
        $row = $y * $info.Stride
        for ($x = 0; $x -lt $info.W; $x++) {
            $i = $row + $x * 4
            $b = [int]$info.Buf[$i]; $g = [int]$info.Buf[$i + 1]; $r = [int]$info.Buf[$i + 2]; $a = [int]$info.Buf[$i + 3]
            if (Is-Bg $b $g $r $a) { continue }
            $dx = $x - $cx; $dy = $y - $cy
            $d = [math]::Sqrt($dx * $dx + $dy * $dy)
            if ($d -gt $maxR) { $maxR = $d }
        }
    }
    return $maxR
}

function Sample-Bilinear($info, [double]$fx, [double]$fy) {
    $w = $info.W; $h = $info.H
    if ($fx -lt 0 -or $fy -lt 0 -or $fx -gt ($w - 1) -or $fy -gt ($h - 1)) {
        return @(0, 0, 0, 0)
    }
    $x0 = [int][math]::Floor($fx)
    $y0 = [int][math]::Floor($fy)
    $x1 = [math]::Min($x0 + 1, $w - 1)
    $y1 = [math]::Min($y0 + 1, $h - 1)
    $tx = $fx - $x0
    $ty = $fy - $y0
    $i00 = $y0 * $info.Stride + $x0 * 4
    $i10 = $y0 * $info.Stride + $x1 * 4
    $i01 = $y1 * $info.Stride + $x0 * 4
    $i11 = $y1 * $info.Stride + $x1 * 4
    $out = New-Object int[] 4
    for ($c = 0; $c -lt 4; $c++) {
        $p00 = [int]$info.Buf[$i00 + $c]
        $p10 = [int]$info.Buf[$i10 + $c]
        $p01 = [int]$info.Buf[$i01 + $c]
        $p11 = [int]$info.Buf[$i11 + $c]
        $a = $p00 * (1 - $tx) + $p10 * $tx
        $b = $p01 * (1 - $tx) + $p11 * $tx
        $out[$c] = [int][math]::Round($a * (1 - $ty) + $b * $ty)
    }
    return $out
}

Write-Host "Loading sources..."
$stdBmp = [System.Drawing.Bitmap]::FromFile($stdPath)
$steelBmp = [System.Drawing.Bitmap]::FromFile($steelPath)
$std = Get-Pixels $stdBmp
$steel = Get-Pixels $steelBmp
$stdBmp.Dispose(); $steelBmp.Dispose()

$stdR = Content-Radius $std
$steelR = Content-Radius $steel
Write-Host ("std {0}x{1} contentR={2:N1}; steel {3}x{4} contentR={5:N1}" -f $std.W, $std.H, $stdR, $steel.W, $steel.H, $steelR)

$side = 512
$fmt = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
$outBmp = New-Object System.Drawing.Bitmap $side, $side, $fmt
$gClear = [System.Drawing.Graphics]::FromImage($outBmp)
$gClear.Clear([System.Drawing.Color]::Transparent)
$gClear.Dispose()
$outBuf = New-Object byte[] ($side * $side * 4)
$outStride = $side * 4
$ocx = ($side - 1) * 0.5
$ocy = ($side - 1) * 0.5
# Fill nearly the full canvas; WheelNormalizer adds a little crop pad.
$outR = $side * 0.492
$hubCut = $outR * $HubFrac
$blend = $outR * 0.016

$stdCx = ($std.W - 1) * 0.5
$stdCy = ($std.H - 1) * 0.5
$steelCx = ($steel.W - 1) * 0.5
$steelCy = ($steel.H - 1) * 0.5

Write-Host ("Compositing {0}px hubCut={1:N1}" -f $side, $hubCut)
for ($y = 0; $y -lt $side; $y++) {
    for ($x = 0; $x -lt $side; $x++) {
        $dx = $x - $ocx; $dy = $y - $ocy
        $d = [math]::Sqrt($dx * $dx + $dy * $dy)
        $i = $y * $outStride + $x * 4
        if ($d -gt $outR + 0.75) {
            $outBuf[$i] = 0; $outBuf[$i + 1] = 0; $outBuf[$i + 2] = 0; $outBuf[$i + 3] = 0
            continue
        }
        $nx = $dx / $outR
        $ny = $dy / $outR
        $s = Sample-Bilinear $std ($stdCx + $nx * $stdR) ($stdCy + $ny * $stdR)
        $h = Sample-Bilinear $steel ($steelCx + $nx * $steelR) ($steelCy + $ny * $steelR)

        $sBg = Is-Bg $s[0] $s[1] $s[2] $s[3]
        $hBg = Is-Bg $h[0] $h[1] $h[2] $h[3]

        if ($d -le ($hubCut - $blend)) {
            if ($hBg) {
                $outBuf[$i] = 0; $outBuf[$i + 1] = 0; $outBuf[$i + 2] = 0; $outBuf[$i + 3] = 0
            } else {
                $outBuf[$i] = [byte]$h[0]
                $outBuf[$i + 1] = [byte]$h[1]
                $outBuf[$i + 2] = [byte]$h[2]
                $outBuf[$i + 3] = 255
            }
        } elseif ($d -ge ($hubCut + $blend)) {
            if ($sBg) {
                # Between knobby tread blocks: transparent, not black plate.
                $outBuf[$i] = 0; $outBuf[$i + 1] = 0; $outBuf[$i + 2] = 0; $outBuf[$i + 3] = 0
            } else {
                $outBuf[$i] = [byte]$s[0]
                $outBuf[$i + 1] = [byte]$s[1]
                $outBuf[$i + 2] = [byte]$s[2]
                $outBuf[$i + 3] = 255
            }
        } else {
            $t = ($d - ($hubCut - $blend)) / (2.0 * $blend)
            if ($t -lt 0) { $t = 0 }
            if ($t -gt 1) { $t = 1 }
            if ($hBg -and $sBg) {
                $outBuf[$i] = 0; $outBuf[$i + 1] = 0; $outBuf[$i + 2] = 0; $outBuf[$i + 3] = 0
            } elseif ($hBg) {
                $outBuf[$i] = [byte]$s[0]; $outBuf[$i + 1] = [byte]$s[1]; $outBuf[$i + 2] = [byte]$s[2]; $outBuf[$i + 3] = 255
            } elseif ($sBg) {
                $outBuf[$i] = [byte]$h[0]; $outBuf[$i + 1] = [byte]$h[1]; $outBuf[$i + 2] = [byte]$h[2]; $outBuf[$i + 3] = 255
            } else {
                for ($c = 0; $c -lt 3; $c++) {
                    $outBuf[$i + $c] = [byte][math]::Round($h[$c] * (1 - $t) + $s[$c] * $t)
                }
                $outBuf[$i + 3] = 255
            }
        }
    }
}

# Write pixels
$rect = New-Object System.Drawing.Rectangle 0, 0, $side, $side
$data = $outBmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::WriteOnly, $fmt)
[System.Runtime.InteropServices.Marshal]::Copy($outBuf, 0, $data.Scan0, $outBuf.Length)
$outBmp.UnlockBits($data)

# Stats
$a0 = 0; $black = 0; $other = 0; $contentMax = 1.0
for ($y = 0; $y -lt $side; $y++) {
    for ($x = 0; $x -lt $side; $x++) {
        $i = $y * $outStride + $x * 4
        $b = [int]$outBuf[$i]; $g = [int]$outBuf[$i + 1]; $r = [int]$outBuf[$i + 2]; $a = [int]$outBuf[$i + 3]
        if ($a -lt 8) { $a0++; continue }
        $d = [math]::Sqrt(($x - $ocx) * ($x - $ocx) + ($y - $ocy) * ($y - $ocy))
        if ($d -gt $contentMax) { $contentMax = $d }
        if ($r -le 6 -and $g -le 6 -and $b -le 6) { $black++ } else { $other++ }
    }
}
Write-Host ("alpha0={0} nearBlackOpaque={1} other={2} contentR={3:N1} fill={4:N2}" -f $a0, $black, $other, $contentMax, ($contentMax / ($side * 0.5)))

$outBmp.Save($outHd, [System.Drawing.Imaging.ImageFormat]::Png)
$outBmp.Save($outPng, [System.Drawing.Imaging.ImageFormat]::Png)
Write-Host "Wrote $outPng"
if (Test-Path $oldWebp) { Remove-Item $oldWebp -Force }

$std.Bmp.Dispose(); $steel.Bmp.Dispose(); $outBmp.Dispose()
Write-Host "Done."
