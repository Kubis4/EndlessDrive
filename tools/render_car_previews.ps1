# High-Resolution 3D Software Renderer for EndlessDrive Modular Car Previews
# Uses .NET System.Drawing (GDI+) to produce crisp, publication-quality PNGs
Add-Type -AssemblyName System.Drawing

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$modelsDir = Join-Path $workspaceRoot "models"
$previewsDir = Join-Path $modelsDir "previews"
if (!(Test-Path $previewsDir)) { New-Item -ItemType Directory -Path $previewsDir -Force | Out-Null }

Write-Host "=========================================================="
Write-Host " Rendering High-Resolution 3D Modular Car Previews"
Write-Host "=========================================================="

# -----------------------------------------------------------------------------
# 1. Load OBJ Model and Materials
# -----------------------------------------------------------------------------
$objPath = Join-Path $modelsDir "car_modular.obj"
Write-Host "Loading OBJ: $objPath..."

$rawVertices = [System.Collections.Generic.List[float[]]]::new()
$rawFaces = [System.Collections.Generic.List[object]]::new()
$currentObject = ""
$currentMat = "Paint_Body"

# Material Base Colors
$MatColors = @{
    "Paint_Body"       = [System.Drawing.Color]::FromArgb(40, 110, 225)
    "Plastic_Trim"     = [System.Drawing.Color]::FromArgb(35, 42, 54)
    "Glass_Windshield" = [System.Drawing.Color]::FromArgb(120, 190, 240)
    "Glass_Tinted"     = [System.Drawing.Color]::FromArgb(90, 160, 220)
    "Interior_Trim"    = [System.Drawing.Color]::FromArgb(50, 60, 75)
    "Headlight_Lens"   = [System.Drawing.Color]::FromArgb(255, 250, 190)
    "Taillight_Lens"   = [System.Drawing.Color]::FromArgb(235, 45, 45)
    "Reverse_Light"    = [System.Drawing.Color]::FromArgb(240, 245, 255)
    "Chrome_Detail"    = [System.Drawing.Color]::FromArgb(230, 235, 245)
    "Tire_Rubber"      = [System.Drawing.Color]::FromArgb(28, 33, 40)
    "Wheel_Rim"        = [System.Drawing.Color]::FromArgb(205, 215, 225)
    "Brake_Disc"       = [System.Drawing.Color]::FromArgb(180, 185, 195)
    "Engine_Block"     = [System.Drawing.Color]::FromArgb(100, 115, 130)
    "Engine_Accent"    = [System.Drawing.Color]::FromArgb(215, 175, 45)
    "Radiator_Alu"     = [System.Drawing.Color]::FromArgb(160, 170, 180)
    "Battery_Case"     = [System.Drawing.Color]::FromArgb(30, 35, 45)
    "Battery_Terminal" = [System.Drawing.Color]::FromArgb(220, 50, 40)
    "RoofRack_Steel"   = [System.Drawing.Color]::FromArgb(35, 40, 50)
    "Exhaust_Metal"    = [System.Drawing.Color]::FromArgb(200, 205, 215)
    "Mirror_Glass"     = [System.Drawing.Color]::FromArgb(200, 225, 245)
}

$reader = [System.IO.StreamReader]::new($objPath)
while (($line = $reader.ReadLine()) -ne $null) {
    $line = $line.Trim()
    if ($line.StartsWith("v ")) {
        $parts = $line.Substring(2).Trim().Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)
        $vx = [float]::Parse($parts[0], [System.Globalization.CultureInfo]::InvariantCulture)
        $vy = [float]::Parse($parts[1], [System.Globalization.CultureInfo]::InvariantCulture)
        $vz = [float]::Parse($parts[2], [System.Globalization.CultureInfo]::InvariantCulture)
        $rawVertices.Add(@($vx, $vy, $vz))
    }
    elseif ($line.StartsWith("o ")) {
        $currentObject = $line.Substring(2).Trim()
    }
    elseif ($line.StartsWith("usemtl ")) {
        $currentMat = $line.Substring(7).Trim()
    }
    elseif ($line.StartsWith("f ")) {
        $parts = $line.Substring(2).Trim().Split(' ', [System.StringSplitOptions]::RemoveEmptyEntries)
        $indices = @()
        foreach ($p in $parts) {
            $sub = $p.Split('/')[0]
            $indices += [int]::Parse($sub) - 1
        }
        $rawFaces.Add([PSCustomObject]@{
            V1 = $indices[0]
            V2 = $indices[1]
            V3 = $indices[2]
            Mat = $currentMat
            Obj = $currentObject
        })
    }
}
$reader.Close()

Write-Host "Loaded $($rawVertices.Count) vertices, $($rawFaces.Count) faces across 26 objects."

# Explode offsets mapping for the exploded view
function Get-ExplodeOffset($objName) {
    $n = $objName.ToLower()
    if ($n -like "*hood*")             { return @(0.5, 0.9, 0.0) }
    if ($n -like "*front_bumper*")     { return @(1.2, -0.1, 0.0) }
    if ($n -like "*rear_bumper*")      { return @(-1.2, -0.1, 0.0) }
    if ($n -like "*trunk*")            { return @(-0.9, 0.7, 0.0) }
    if ($n -like "*door_front_l*")     { return @(0.2, 0.0, 1.1) }
    if ($n -like "*door_front_r*")     { return @(0.2, 0.0, -1.1) }
    if ($n -like "*door_rear_l*")      { return @(-0.2, 0.0, 1.1) }
    if ($n -like "*door_rear_r*")      { return @(-0.2, 0.0, -1.1) }
    if ($n -like "*roof_rack*")        { return @(0.0, 1.3, 0.0) }
    if ($n -like "*engine*")           { return @(0.0, 1.0, 0.0) }
    if ($n -like "*radiator*")         { return @(0.9, 0.4, 0.0) }
    if ($n -like "*battery*")          { return @(0.4, 0.9, 0.6) }
    if ($n -like "*wheel_fl*")         { return @(0.3, 0.0, 0.9) }
    if ($n -like "*wheel_fr*")         { return @(0.3, 0.0, -0.9) }
    if ($n -like "*wheel_rl*")         { return @(-0.3, 0.0, 0.9) }
    if ($n -like "*wheel_rr*")         { return @(-0.3, 0.0, -0.9) }
    if ($n -like "*headlight_l*")      { return @(0.9, 0.3, 0.5) }
    if ($n -like "*headlight_r*")      { return @(0.9, 0.3, -0.5) }
    if ($n -like "*taillight_l*")      { return @(-0.9, 0.3, 0.5) }
    if ($n -like "*taillight_r*")      { return @(-0.9, 0.3, -0.5) }
    if ($n -like "*seat_front_l*")     { return @(0.0, 0.9, 0.6) }
    if ($n -like "*seat_front_r*")     { return @(0.0, 0.9, -0.6) }
    if ($n -like "*seat_rear*")        { return @(0.0, 0.9, 0.0) }
    if ($n -like "*fuel_tank*")        { return @(0.0, -0.7, 0.0) }
    if ($n -like "*suspension*")       { return @(0.0, -0.7, 0.0) }
    return @(0.0, 0.0, 0.0)
}

# -----------------------------------------------------------------------------
# 2. 3D Math & Render Pipeline
# -----------------------------------------------------------------------------
function Render-Scene {
    param (
        [string]$OutputFile,
        [float[]]$CamPos,
        [float[]]$CamTarget,
        [float]$FovYDeg,
        [int]$Width,
        [int]$Height,
        [string]$Title,
        [string]$Subtitle,
        [bool]$IsExploded = $false
    )

    Write-Host "Rendering: $OutputFile ($Width x $Height)..."

    $bmp = [System.Drawing.Bitmap]::new($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

    # Background gradient
    $bgBrush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.Point]::new(0, 0),
        [System.Drawing.Point]::new(0, $Height),
        [System.Drawing.Color]::FromArgb(26, 34, 48),
        [System.Drawing.Color]::FromArgb(12, 16, 23)
    )
    $g.FillRectangle($bgBrush, 0, 0, $Width, $Height)
    $bgBrush.Dispose()

    # Camera Coordinate Frame
    $cpx = [double]$CamPos[0]; $cpy = [double]$CamPos[1]; $cpz = [double]$CamPos[2]
    $ctx = [double]$CamTarget[0]; $cty = [double]$CamTarget[1]; $ctz = [double]$CamTarget[2]

    $fx = $ctx - $cpx
    $fy = $cty - $cpy
    $fz = $ctz - $cpz
    $fwdLen = [math]::Sqrt($fx*$fx + $fy*$fy + $fz*$fz)
    if ($fwdLen -lt 0.0001) { $fwdLen = 1.0 }
    $fwdx = $fx / $fwdLen; $fwdy = $fy / $fwdLen; $fwdz = $fz / $fwdLen

    # Right = Fwd x UpApprox (0, 1, 0)
    $rx = $fwdy * 0.0 - $fwdz * 1.0
    $ry = $fwdz * 0.0 - $fwdx * 0.0
    $rz = $fwdx * 1.0 - $fwdy * 0.0
    $rLen = [math]::Sqrt($rx*$rx + $ry*$ry + $rz*$rz)
    if ($rLen -lt 0.0001) { $rx = 1.0; $ry = 0.0; $rz = 0.0; $rLen = 1.0 }
    $rx /= $rLen; $ry /= $rLen; $rz /= $rLen

    # True Up = Right x Fwd
    $ux = $ry*$fwdz - $rz*$fwdy
    $uy = $rz*$fwdx - $rx*$fwdz
    $uz = $rx*$fwdy - $ry*$fwdx

    $aspect = [float]$Width / [float]$Height
    $tanHalfFov = [math]::Tan(($FovYDeg * [math]::PI / 180.0) / 2.0)

    # Lighting Vector
    $lx = 0.55; $ly = 0.75; $lz = 0.35
    $lLen = [math]::Sqrt($lx*$lx + $ly*$ly + $lz*$lz)
    $lx /= $lLen; $ly /= $lLen; $lz /= $lLen

    # Helper: Project a 3D point to 2D screen
    $Project = {
        param([double]$x, [double]$y, [double]$z)
        $dx = $x - $cpx
        $dy = $y - $cpy
        $dz = $z - $cpz

        $camZ = $dx*$fwdx + $dy*$fwdy + $dz*$fwdz
        if ($camZ -le 0.1) { return $null }

        $camX = $dx*$rx + $dy*$ry + $dz*$rz
        $camY = $dx*$ux + $dy*$uy + $dz*$uz

        $ndcX = $camX / ($camZ * $aspect * $tanHalfFov)
        $ndcY = $camY / ($camZ * $tanHalfFov)

        $scrX = [float](($ndcX + 1.0) * 0.5 * $Width)
        $scrY = [float]((1.0 - $ndcY) * 0.5 * $Height)
        return @($scrX, $scrY, [float]$camZ)
    }

    # Draw Studio Ground Grid
    $gridPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(40, 56, 189, 248), 1)
    $roadY = -0.48
    for ($gx = -6; $gx -le 6; $gx += 1.0) {
        $p1 = & $Project $gx $roadY -4.0
        $p2 = & $Project $gx $roadY 4.0
        if ($p1 -and $p2) {
            $g.DrawLine($gridPen, [float]$p1[0], [float]$p1[1], [float]$p2[0], [float]$p2[1])
        }
    }
    for ($gz = -4; $gz -le 4; $gz += 1.0) {
        $p1 = & $Project -6.0 $roadY $gz
        $p2 = & $Project 6.0 $roadY $gz
        if ($p1 -and $p2) {
            $g.DrawLine($gridPen, [float]$p1[0], [float]$p1[1], [float]$p2[0], [float]$p2[1])
        }
    }
    $gridPen.Dispose()

    # Compute exploded positions if enabled
    $vertTransformed = [System.Collections.Generic.List[float[]]]::new($rawVertices.Count)
    for ($i = 0; $i -lt $rawVertices.Count; $i++) {
        $v = $rawVertices[$i]
        $vertTransformed.Add(@($v[0], $v[1], $v[2]))
    }

    if ($IsExploded) {
        # Modify vertices per object
        for ($fi = 0; $fi -lt $rawFaces.Count; $fi++) {
            $f = $rawFaces[$fi]
            $off = Get-ExplodeOffset $f.Obj
            $ox = [double]$off[0]; $oy = [double]$off[1]; $oz = [double]$off[2]
            if ($ox -ne 0.0 -or $oy -ne 0.0 -or $oz -ne 0.0) {
                foreach ($vidx in @($f.V1, $f.V2, $f.V3)) {
                    $orig = $rawVertices[$vidx]
                    $vx = [double]$orig[0] + $ox
                    $vy = [double]$orig[1] + $oy
                    $vz = [double]$orig[2] + $oz
                    $vertTransformed[$vidx] = @($vx, $vy, $vz)
                }
            }
        }
    }

    # Prepare and Sort Faces (Painter's Algorithm)
    $drawableFaces = [System.Collections.Generic.List[object]]::new($rawFaces.Count)

    for ($fi = 0; $fi -lt $rawFaces.Count; $fi++) {
        $f = $rawFaces[$fi]
        $v1 = $vertTransformed[$f.V1]
        $v2 = $vertTransformed[$f.V2]
        $v3 = $vertTransformed[$f.V3]

        # Normal vector
        $ax = $v2[0] - $v1[0]; $ay = $v2[1] - $v1[1]; $az = $v2[2] - $v1[2]
        $bx = $v3[0] - $v1[0]; $by = $v3[1] - $v1[1]; $bz = $v3[2] - $v1[2]
        $nx = $ay*$bz - $az*$by
        $ny = $az*$bx - $ax*$bz
        $nz = $ax*$by - $ay*$bx
        $nLen = [math]::Sqrt($nx*$nx + $ny*$ny + $nz*$nz)
        if ($nLen -lt 0.0001) { continue }
        $nx /= $nLen; $ny /= $nLen; $nz /= $nLen

        # Center point
        $cx = ($v1[0] + $v2[0] + $v3[0]) / 3.0
        $cy = ($v1[1] + $v2[1] + $v3[1]) / 3.0
        $cz = ($v1[2] + $v2[2] + $v3[2]) / 3.0

        # View vector from camera
        $vx = $cx - $cpx; $vy = $cy - $cpy; $vz = $cz - $cpz
        $depth = $vx*$fwdx + $vy*$fwdy + $vz*$fwdz
        if ($depth -le 0.1) { continue }

        $drawableFaces.Add([PSCustomObject]@{
            V1 = $v1; V2 = $v2; V3 = $v3
            Norm = @($nx, $ny, $nz)
            Center = @($cx, $cy, $cz)
            Depth = $depth
            Mat = $f.Mat
            Obj = $f.Obj
        })
    }

    # Sort furthest to nearest
    $sortedFaces = $drawableFaces | Sort-Object -Property Depth -Descending

    # Render polygons
    $brushCache = @{}
    $penCache = @{}

    foreach ($df in $sortedFaces) {
        $p1 = & $Project $df.V1[0] $df.V1[1] $df.V1[2]
        $p2 = & $Project $df.V2[0] $df.V2[1] $df.V2[2]
        $p3 = & $Project $df.V3[0] $df.V3[1] $df.V3[2]

        if (!$p1 -or !$p2 -or !$p3) { continue }

        # Lighting calculation (Ambient + Diffuse + Top/Sky Bounce)
        $dotL = $df.Norm[0]*$lx + $df.Norm[1]*$ly + $df.Norm[2]*$lz
        $diffuse = [math]::Max(0.0, $dotL)
        $skyBounce = [math]::Max(0.0, $df.Norm[1]) * 0.25
        $intensity = [math]::Min(1.0, [math]::Max(0.28, 0.40 + 0.50 * $diffuse + $skyBounce))

        # Base Color
        $baseCol = if ($MatColors.ContainsKey($df.Mat)) { $MatColors[$df.Mat] } else { [System.Drawing.Color]::FromArgb(160, 160, 160) }

        $r = [int]($baseCol.R * $intensity)
        $gCol = [int]($baseCol.G * $intensity)
        $b = [int]($baseCol.B * $intensity)
        $alpha = if ($df.Mat -like "*Glass*") { 180 } else { 255 }

        $polyColor = [System.Drawing.Color]::FromArgb($alpha, $r, $gCol, $b)
        $brush = [System.Drawing.SolidBrush]::new($polyColor)

        # Subtle edge outline for crisp cel-shaded CAD look
        $edgeColor = [System.Drawing.Color]::FromArgb([int]($alpha*0.7), [int]($r*0.75), [int]($gCol*0.75), [int]($b*0.75))
        $pen = [System.Drawing.Pen]::new($edgeColor, 0.8)

        $pts = @(
            [System.Drawing.PointF]::new($p1[0], $p1[1]),
            [System.Drawing.PointF]::new($p2[0], $p2[1]),
            [System.Drawing.PointF]::new($p3[0], $p3[1])
        )

        $g.FillPolygon($brush, $pts)
        $g.DrawPolygon($pen, $pts)

        $brush.Dispose()
        $pen.Dispose()
    }

    # -------------------------------------------------------------------------
    # UI Overlay: Watermark, Title & Physical Specs HUD
    # -------------------------------------------------------------------------
    # Top banner background
    $hudBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(210, 15, 23, 42))
    $g.FillRectangle($hudBrush, 24, 24, 520, 84)
    $hudBorderPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(100, 56, 189, 248), 1)
    $g.DrawRectangle($hudBorderPen, 24, 24, 520, 84)

    $titleFont = [System.Drawing.Font]::new("Segoe UI", 16, [System.Drawing.FontStyle]::Bold)
    $subFont = [System.Drawing.Font]::new("Segoe UI", 10, [System.Drawing.FontStyle]::Regular)
    $hudFont = [System.Drawing.Font]::new("Segoe UI", 9, [System.Drawing.FontStyle]::Bold)

    $cyanBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(56, 189, 248))
    $whiteBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(241, 245, 249))
    $grayBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(148, 163, 184))

    $g.DrawString($Title, $titleFont, $cyanBrush, 36, 34)
    $g.DrawString($Subtitle, $subFont, $whiteBrush, 36, 68)

    # Bottom Specs Box
    $g.FillRectangle($hudBrush, 24, $Height - 74, $Width - 48, 50)
    $g.DrawRectangle($hudBorderPen, 24, $Height - 74, $Width - 48, 50)

    $specsText = "SPECS: Length: 5.60 m (worldWidthM)  |  Wheelbase: 3.528 m  |  Width: 1.84 m  |  Track: 1.56 m  |  Wheel Ø: 0.96 m  |  Ride Height: 0.42 m  |  26 Modular Nodes"
    $g.DrawString($specsText, $hudFont, $cyanBrush, 36, $Height - 58)

    # Clean up
    $titleFont.Dispose(); $subFont.Dispose(); $hudFont.Dispose()
    $cyanBrush.Dispose(); $whiteBrush.Dispose(); $grayBrush.Dispose()
    $hudBrush.Dispose(); $hudBorderPen.Dispose()
    $g.Dispose()

    # Save PNG
    $bmp.Save($OutputFile, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "  -> Saved: $OutputFile" -ForegroundColor Green
}

# -----------------------------------------------------------------------------
# 3. Render 4 Distinct Camera Views
# -----------------------------------------------------------------------------
$view1Params = @{
    OutputFile = (Join-Path $previewsDir "car_3d_isometric.png")
    CamPos     = @(5.6, 2.6, 4.4)
    CamTarget  = @(0.0, 0.4, 0.0)
    FovYDeg    = 38
    Width      = 1600
    Height     = 900
    Title      = "ENDLESSDRIVE - MODULAR 3D CAR"
    Subtitle   = "Isometric 3/4 Studio View | SedanSpec.kt Dimensionally Accurate"
}
Render-Scene @view1Params

$view2Params = @{
    OutputFile = (Join-Path $previewsDir "car_3d_side.png")
    CamPos     = @(0.0, 0.5, 7.8)
    CamTarget  = @(0.0, 0.5, 0.0)
    FovYDeg    = 36
    Width      = 1600
    Height     = 900
    Title      = "ENDLESSDRIVE - SIDE PROFILE"
    Subtitle   = "Orthographic Profile | 5.60m Length | 3.528m Wheelbase | 0.48m Wheel Radius"
}
Render-Scene @view2Params

$view3Params = @{
    OutputFile = (Join-Path $previewsDir "car_3d_front.png")
    CamPos     = @(4.8, 1.4, 2.6)
    CamTarget  = @(1.2, 0.35, 0.0)
    FovYDeg    = 42
    Width      = 1600
    Height     = 900
    Title      = "ENDLESSDRIVE - FRONT 3/4 FASCIA"
    Subtitle   = "Front Bumper, Dual Headlights, Power Hood and Front Axle Track (1.56m)"
}
Render-Scene @view3Params

$view4Params = @{
    OutputFile = (Join-Path $previewsDir "car_3d_exploded.png")
    CamPos     = @(6.2, 3.8, 5.2)
    CamTarget  = @(0.0, 0.5, 0.0)
    FovYDeg    = 42
    Width      = 1600
    Height     = 900
    Title      = "ENDLESSDRIVE - EXPLODED ASSEMBLY"
    Subtitle   = "Full 26-Part Modular Breakdown | Swappable Doors, Engine, Wheels and Racks"
    IsExploded = $true
}
Render-Scene @view4Params

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " All 4 High-Resolution 3D Previews Rendered Successfully!" -ForegroundColor Cyan
Write-Host " Output Folder: $previewsDir" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
