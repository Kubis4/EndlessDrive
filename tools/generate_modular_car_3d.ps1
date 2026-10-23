# EndlessDrive - Modular 3D Car Model Generator
# Recreates the exact game sedan in 3D with all modular parts matching SedanSpec and GameConfig.
# Outputs:
#   - models/car_modular.obj + car_modular.mtl (Complete assembled car with named modular objects)
#   - models/parts/*.obj (Individual detachable parts with local/global coordinates)
#   - models/car_modular.gltf (glTF 2.0 with scene hierarchy, PBR materials, and embedded buffer)
#   - models/car_modular.glb (Binary glTF 2.0 format)
#   - models/modular_car_spec.json (Machine-readable specification for in-game assembly)
#   - app/src/main/assets/expedition/car_modular.obj (Production renderer asset)

param(
    [string]$OutputDir = "$PSScriptRoot\..\models",
    [string]$AssetDir = "$PSScriptRoot\..\app\src\main\assets\expedition"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " EndlessDrive: Generating Modular 3D Car Model" -ForegroundColor Cyan
Write-Host " Exact dimensions from SedanSpec.kt and GameConfig.kt" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# -----------------------------------------------------------------------------
# 3D Math Helper Classes
# -----------------------------------------------------------------------------
class Vec3 {
    [double]$X; [double]$Y; [double]$Z
    Vec3([double]$x, [double]$y, [double]$z) {
        $this.X = $x; $this.Y = $y; $this.Z = $z
    }
    static [Vec3] Add([Vec3]$a, [Vec3]$b) {
        return [Vec3]::new($a.X + $b.X, $a.Y + $b.Y, $a.Z + $b.Z)
    }
    static [Vec3] Sub([Vec3]$a, [Vec3]$b) {
        return [Vec3]::new($a.X - $b.X, $a.Y - $b.Y, $a.Z - $b.Z)
    }
    static [Vec3] Mul([Vec3]$a, [double]$s) {
        return [Vec3]::new($a.X * $s, $a.Y * $s, $a.Z * $s)
    }
    static [Vec3] Cross([Vec3]$a, [Vec3]$b) {
        return [Vec3]::new(
            $a.Y * $b.Z - $a.Z * $b.Y,
            $a.Z * $b.X - $a.X * $b.Z,
            $a.X * $b.Y - $a.Y * $b.X
        )
    }
    static [double] Dot([Vec3]$a, [Vec3]$b) {
        return ($a.X * $b.X + $a.Y * $b.Y + $a.Z * $b.Z)
    }
    [double] Length() {
        return [Math]::Sqrt($this.X * $this.X + $this.Y * $this.Y + $this.Z * $this.Z)
    }
    [Vec3] Normalized() {
        $len = $this.Length()
        if ($len -lt 1e-9) { return [Vec3]::new(0, 1, 0) }
        return [Vec3]::new($this.X / $len, $this.Y / $len, $this.Z / $len)
    }
}

class Vec2 {
    [double]$U; [double]$V
    Vec2([double]$u, [double]$v) {
        $this.U = $u; $this.V = $v
    }
}

class Vertex {
    [Vec3]$Pos
    [Vec3]$Norm
    [Vec2]$UV
    Vertex([Vec3]$p, [Vec3]$n, [Vec2]$uv) {
        $this.Pos = $p; $this.Norm = $n; $this.UV = $uv
    }
}

class Face {
    [int]$V1; [int]$V2; [int]$V3
    [string]$Material
    Face([int]$v1, [int]$v2, [int]$v3, [string]$mat) {
        $this.V1 = $v1; $this.V2 = $v2; $this.V3 = $v3; $this.Material = $mat
    }
}

class ModularPart {
    [string]$Name
    [string]$Slot
    [string]$ItemId
    [Vec3]$Pivot
    [Vec3]$MinBBox
    [Vec3]$MaxBBox
    [System.Collections.Generic.List[Vertex]]$Vertices
    [System.Collections.Generic.List[Face]]$Faces

    ModularPart([string]$name, [string]$slot, [string]$itemId, [Vec3]$pivot) {
        $this.Name = $name
        $this.Slot = $slot
        $this.ItemId = $itemId
        $this.Pivot = $pivot
        $this.Vertices = [System.Collections.Generic.List[Vertex]]::new()
        $this.Faces = [System.Collections.Generic.List[Face]]::new()
        $this.MinBBox = [Vec3]::new(999999, 999999, 999999)
        $this.MaxBBox = [Vec3]::new(-999999, -999999, -999999)
    }

    [int] AddVertex([Vec3]$p, [Vec3]$n, [Vec2]$uv) {
        if ($p.X -lt $this.MinBBox.X) { $this.MinBBox.X = $p.X }
        if ($p.Y -lt $this.MinBBox.Y) { $this.MinBBox.Y = $p.Y }
        if ($p.Z -lt $this.MinBBox.Z) { $this.MinBBox.Z = $p.Z }
        if ($p.X -gt $this.MaxBBox.X) { $this.MaxBBox.X = $p.X }
        if ($p.Y -gt $this.MaxBBox.Y) { $this.MaxBBox.Y = $p.Y }
        if ($p.Z -gt $this.MaxBBox.Z) { $this.MaxBBox.Z = $p.Z }

        $v = [Vertex]::new($p, $n, $uv)
        $this.Vertices.Add($v)
        return ($this.Vertices.Count - 1)
    }

    [void] AddTri([Vec3]$p1, [Vec3]$p2, [Vec3]$p3, [Vec3]$n, [string]$mat) {
        $uv1 = [Vec2]::new(0, 0); $uv2 = [Vec2]::new(1, 0); $uv3 = [Vec2]::new(0.5, 1)
        $i1 = $this.AddVertex($p1, $n, $uv1)
        $i2 = $this.AddVertex($p2, $n, $uv2)
        $i3 = $this.AddVertex($p3, $n, $uv3)
        $this.Faces.Add([Face]::new($i1, $i2, $i3, $mat))
    }

    [void] AddQuad([Vec3]$p1, [Vec3]$p2, [Vec3]$p3, [Vec3]$p4, [Vec3]$n, [string]$mat) {
        $uv1 = [Vec2]::new(0, 0); $uv2 = [Vec2]::new(1, 0); $uv3 = [Vec2]::new(1, 1); $uv4 = [Vec2]::new(0, 1)
        $i1 = $this.AddVertex($p1, $n, $uv1)
        $i2 = $this.AddVertex($p2, $n, $uv2)
        $i3 = $this.AddVertex($p3, $n, $uv3)
        $i4 = $this.AddVertex($p4, $n, $uv4)
        $this.Faces.Add([Face]::new($i1, $i2, $i3, $mat))
        $this.Faces.Add([Face]::new($i1, $i3, $i4, $mat))
    }

    [void] AddQuadUV([Vec3]$p1, [Vec3]$p2, [Vec3]$p3, [Vec3]$p4, [Vec3]$n1, [Vec3]$n2, [Vec3]$n3, [Vec3]$n4, [Vec2]$uv1, [Vec2]$uv2, [Vec2]$uv3, [Vec2]$uv4, [string]$mat) {
        $i1 = $this.AddVertex($p1, $n1, $uv1)
        $i2 = $this.AddVertex($p2, $n2, $uv2)
        $i3 = $this.AddVertex($p3, $n3, $uv3)
        $i4 = $this.AddVertex($p4, $n4, $uv4)
        $this.Faces.Add([Face]::new($i1, $i2, $i3, $mat))
        $this.Faces.Add([Face]::new($i1, $i3, $i4, $mat))
    }

    [void] AddBox([double]$minX, [double]$minY, [double]$minZ, [double]$maxX, [double]$maxY, [double]$maxZ, [string]$mat) {
        # Front (+X)
        $this.AddQuad([Vec3]::new($maxX, $minY, $minZ), [Vec3]::new($maxX, $minY, $maxZ), [Vec3]::new($maxX, $maxY, $maxZ), [Vec3]::new($maxX, $maxY, $minZ), [Vec3]::new(1, 0, 0), $mat)
        # Back (-X)
        $this.AddQuad([Vec3]::new($minX, $minY, $maxZ), [Vec3]::new($minX, $minY, $minZ), [Vec3]::new($minX, $maxY, $minZ), [Vec3]::new($minX, $maxY, $maxZ), [Vec3]::new(-1, 0, 0), $mat)
        # Left (+Z)
        $this.AddQuad([Vec3]::new($maxX, $minY, $maxZ), [Vec3]::new($minX, $minY, $maxZ), [Vec3]::new($minX, $maxY, $maxZ), [Vec3]::new($maxX, $maxY, $maxZ), [Vec3]::new(0, 0, 1), $mat)
        # Right (-Z)
        $this.AddQuad([Vec3]::new($minX, $minY, $minZ), [Vec3]::new($maxX, $minY, $minZ), [Vec3]::new($maxX, $maxY, $minZ), [Vec3]::new($minX, $maxY, $minZ), [Vec3]::new(0, 0, -1), $mat)
        # Top (+Y)
        $this.AddQuad([Vec3]::new($minX, $maxY, $minZ), [Vec3]::new($maxX, $maxY, $minZ), [Vec3]::new($maxX, $maxY, $maxZ), [Vec3]::new($minX, $maxY, $maxZ), [Vec3]::new(0, 1, 0), $mat)
        # Bottom (-Y)
        $this.AddQuad([Vec3]::new($minX, $minY, $maxZ), [Vec3]::new($maxX, $minY, $maxZ), [Vec3]::new($maxX, $minY, $minZ), [Vec3]::new($minX, $minY, $minZ), [Vec3]::new(0, -1, 0), $mat)
    }

    [void] AddCylinderX([double]$x1, [double]$x2, [double]$cy, [double]$cz, [double]$radius, [int]$segs, [string]$mat, [bool]$caps) {
        $step = 2.0 * [Math]::PI / $segs
        for ($i = 0; $i -lt $segs; $i++) {
            $a1 = $i * $step
            $a2 = ($i + 1) * $step
            $y1 = $cy + $radius * [Math]::Cos($a1)
            $z1 = $cz + $radius * [Math]::Sin($a1)
            $y2 = $cy + $radius * [Math]::Cos($a2)
            $z2 = $cz + $radius * [Math]::Sin($a2)

            $n1 = [Vec3]::new(0, [Math]::Cos($a1), [Math]::Sin($a1))
            $n2 = [Vec3]::new(0, [Math]::Cos($a2), [Math]::Sin($a2))

            $pA = [Vec3]::new($x1, $y1, $z1)
            $pB = [Vec3]::new($x2, $y1, $z1)
            $pC = [Vec3]::new($x2, $y2, $z2)
            $pD = [Vec3]::new($x1, $y2, $z2)

            $this.AddQuadUV($pA, $pB, $pC, $pD, $n1, $n1, $n2, $n2, [Vec2]::new(0, 0), [Vec2]::new(1, 0), [Vec2]::new(1, 1), [Vec2]::new(0, 1), $mat)

            if ($caps) {
                # Cap 1 (-X)
                $this.AddTri([Vec3]::new($x1, $cy, $cz), [Vec3]::new($x1, $y2, $z2), [Vec3]::new($x1, $y1, $z1), [Vec3]::new(-1, 0, 0), $mat)
                # Cap 2 (+X)
                $this.AddTri([Vec3]::new($x2, $cy, $cz), [Vec3]::new($x2, $y1, $z1), [Vec3]::new($x2, $y2, $z2), [Vec3]::new(1, 0, 0), $mat)
            }
        }
    }

    [void] AddCylinderZ([double]$z1, [double]$z2, [double]$cx, [double]$cy, [double]$radius, [int]$segs, [string]$mat, [bool]$caps) {
        $step = 2.0 * [Math]::PI / $segs
        for ($i = 0; $i -lt $segs; $i++) {
            $a1 = $i * $step
            $a2 = ($i + 1) * $step
            $x1 = $cx + $radius * [Math]::Cos($a1)
            $y1 = $cy + $radius * [Math]::Sin($a1)
            $x2 = $cx + $radius * [Math]::Cos($a2)
            $y2 = $cy + $radius * [Math]::Sin($a2)

            $n1 = [Vec3]::new([Math]::Cos($a1), [Math]::Sin($a1), 0)
            $n2 = [Vec3]::new([Math]::Cos($a2), [Math]::Sin($a2), 0)

            $pA = [Vec3]::new($x1, $y1, $z1)
            $pB = [Vec3]::new($x2, $y2, $z1)
            $pC = [Vec3]::new($x2, $y2, $z2)
            $pD = [Vec3]::new($x1, $y1, $z2)

            $this.AddQuadUV($pA, $pB, $pC, $pD, $n1, $n2, $n2, $n1, [Vec2]::new(0, 0), [Vec2]::new(1, 0), [Vec2]::new(1, 1), [Vec2]::new(0, 1), $mat)

            if ($caps) {
                # Cap 1 (-Z)
                $this.AddTri([Vec3]::new($cx, $cy, $z1), [Vec3]::new($x2, $y2, $z1), [Vec3]::new($x1, $y1, $z1), [Vec3]::new(0, 0, -1), $mat)
                # Cap 2 (+Z)
                $this.AddTri([Vec3]::new($cx, $cy, $z2), [Vec3]::new($x1, $y1, $z2), [Vec3]::new($x2, $y2, $z2), [Vec3]::new(0, 0, 1), $mat)
            }
        }
    }
}

# -----------------------------------------------------------------------------
# Material Definitions (Color, Metallic, Roughness)
# -----------------------------------------------------------------------------
$Materials = @{
    "Mat_CarPaint"       = @{ Kd = "0.43 0.30 0.54"; Ks = "0.5 0.5 0.5"; Ns = 90; d = 1.0; BaseColor = @(0.43, 0.30, 0.54, 1.0); Metallic = 0.45; Roughness = 0.28 }
    "Mat_CarPaint_Dark"  = @{ Kd = "0.22 0.16 0.28"; Ks = "0.3 0.3 0.3"; Ns = 50; d = 1.0; BaseColor = @(0.22, 0.16, 0.28, 1.0); Metallic = 0.40; Roughness = 0.45 }
    "Mat_Plastic_Black"  = @{ Kd = "0.08 0.08 0.09"; Ks = "0.1 0.1 0.1"; Ns = 20; d = 1.0; BaseColor = @(0.08, 0.08, 0.09, 1.0); Metallic = 0.05; Roughness = 0.75 }
    "Mat_Glass_Tinted"   = @{ Kd = "0.22 0.50 0.85"; Ks = "0.9 0.9 0.9"; Ns = 150; d = 0.55; BaseColor = @(0.22, 0.50, 0.85, 0.55); Metallic = 0.10; Roughness = 0.08 }
    "Mat_Chrome"         = @{ Kd = "0.85 0.86 0.88"; Ks = "0.95 0.95 0.95"; Ns = 220; d = 1.0; BaseColor = @(0.85, 0.86, 0.88, 1.0); Metallic = 0.95; Roughness = 0.12 }
    "Mat_TireRubber"     = @{ Kd = "0.12 0.12 0.13"; Ks = "0.05 0.05 0.05"; Ns = 15; d = 1.0; BaseColor = @(0.12, 0.12, 0.13, 1.0); Metallic = 0.02; Roughness = 0.88 }
    "Mat_WheelRim"       = @{ Kd = "0.18 0.18 0.20"; Ks = "0.6 0.6 0.6"; Ns = 80; d = 1.0; BaseColor = @(0.18, 0.18, 0.20, 1.0); Metallic = 0.70; Roughness = 0.35 }
    "Mat_BrakeDisc"      = @{ Kd = "0.65 0.66 0.68"; Ks = "0.7 0.7 0.7"; Ns = 100; d = 1.0; BaseColor = @(0.65, 0.66, 0.68, 1.0); Metallic = 0.85; Roughness = 0.30 }
    "Mat_BrakeCaliper"   = @{ Kd = "0.78 0.15 0.15"; Ks = "0.5 0.5 0.5"; Ns = 60; d = 1.0; BaseColor = @(0.78, 0.15, 0.15, 1.0); Metallic = 0.30; Roughness = 0.35 }
    "Mat_HeadlightLens"  = @{ Kd = "0.95 0.97 1.00"; Ks = "0.9 0.9 0.9"; Ns = 180; d = 0.45; BaseColor = @(0.95, 0.97, 1.00, 0.45); Metallic = 0.10; Roughness = 0.05 }
    "Mat_TaillightRed"   = @{ Kd = "0.85 0.08 0.08"; Ks = "0.8 0.8 0.8"; Ns = 120; d = 0.75; BaseColor = @(0.85, 0.08, 0.08, 0.75); Metallic = 0.20; Roughness = 0.15 }
    "Mat_TaillightWhite" = @{ Kd = "0.90 0.90 0.92"; Ks = "0.8 0.8 0.8"; Ns = 120; d = 0.70; BaseColor = @(0.90, 0.90, 0.92, 0.70); Metallic = 0.20; Roughness = 0.15 }
    "Mat_Interior_Dark"  = @{ Kd = "0.15 0.15 0.17"; Ks = "0.1 0.1 0.1"; Ns = 25; d = 1.0; BaseColor = @(0.15, 0.15, 0.17, 1.0); Metallic = 0.05; Roughness = 0.70 }
    "Mat_Seat_Fabric"    = @{ Kd = "0.25 0.26 0.28"; Ks = "0.08 0.08 0.08"; Ns = 15; d = 1.0; BaseColor = @(0.25, 0.26, 0.28, 1.0); Metallic = 0.02; Roughness = 0.80 }
    "Mat_EngineMetal"    = @{ Kd = "0.55 0.56 0.58"; Ks = "0.6 0.6 0.6"; Ns = 70; d = 1.0; BaseColor = @(0.55, 0.56, 0.58, 1.0); Metallic = 0.75; Roughness = 0.40 }
    "Mat_Radiator"       = @{ Kd = "0.35 0.35 0.37"; Ks = "0.4 0.4 0.4"; Ns = 40; d = 1.0; BaseColor = @(0.35, 0.35, 0.37, 1.0); Metallic = 0.50; Roughness = 0.55 }
    "Mat_Battery"        = @{ Kd = "0.10 0.10 0.10"; Ks = "0.2 0.2 0.2"; Ns = 30; d = 1.0; BaseColor = @(0.10, 0.10, 0.10, 1.0); Metallic = 0.05; Roughness = 0.60 }
    "Mat_RoofRack"       = @{ Kd = "0.14 0.14 0.16"; Ks = "0.4 0.4 0.4"; Ns = 50; d = 1.0; BaseColor = @(0.14, 0.14, 0.16, 1.0); Metallic = 0.60; Roughness = 0.45 }
    "Mat_Gold"           = @{ Kd = "0.85 0.70 0.25"; Ks = "0.9 0.8 0.4"; Ns = 140; d = 1.0; BaseColor = @(0.85, 0.70, 0.25, 1.0); Metallic = 0.85; Roughness = 0.20 }
}

$AllParts = [System.Collections.Generic.List[ModularPart]]::new()

# =============================================================================
# PART 1: Chassis / Stripped Shell
# =============================================================================
Write-Host "Building: Chassis_Shell..." -ForegroundColor Gray
$chassis = [ModularPart]::new("Chassis_Shell", "CHASSIS", "car_base_body", [Vec3]::new(0, 0, 0))

# Floor pan & transmission tunnel
$chassis.AddBox(-2.25, -0.332, -0.84, 1.95, -0.28, 0.84, "Mat_Plastic_Black")
$chassis.AddBox(-1.60, -0.28, -0.16, 1.10, -0.05, 0.16, "Mat_Interior_Dark")

# Firewall & Engine bay aprons
$chassis.AddBox(0.95, -0.28, -0.82, 1.02, 0.85, 0.82, "Mat_CarPaint_Dark") # Firewall
$chassis.AddBox(1.02, -0.25, 0.52, 2.10, 0.45, 0.75, "Mat_CarPaint_Dark")  # Left inner apron
$chassis.AddBox(1.02, -0.25, -0.75, 2.10, 0.45, -0.52, "Mat_CarPaint_Dark") # Right inner apron
$chassis.AddBox(2.05, -0.25, -0.75, 2.15, 0.45, 0.75, "Mat_CarPaint_Dark")  # Front core support frame

# Rear Trunk Floor & Wheel Wells
$chassis.AddBox(-2.45, -0.15, -0.78, -1.15, -0.10, 0.78, "Mat_Interior_Dark") # Trunk floor
$chassis.AddBox(-2.00, -0.20, 0.52, -1.45, 0.48, 0.80, "Mat_CarPaint_Dark")   # Left rear wheel tub
$chassis.AddBox(-2.00, -0.20, -0.80, -1.45, 0.48, -0.52, "Mat_CarPaint_Dark")  # Right rear wheel tub

# Cabin Pillars and Roof Panel
# A-pillars (Left & Right)
$chassis.AddBox(0.58, 0.88, 0.65, 1.00, 1.50, 0.75, "Mat_CarPaint")
$chassis.AddBox(0.58, 0.88, -0.75, 1.00, 1.50, -0.65, "Mat_CarPaint")

# B-pillars (Left & Right)
$chassis.AddBox(0.00, -0.28, 0.80, 0.08, 1.52, 0.88, "Mat_Plastic_Black")
$chassis.AddBox(0.00, -0.28, -0.88, 0.08, 1.52, -0.80, "Mat_Plastic_Black")

# C-pillars (Left & Right)
$chassis.AddBox(-1.18, 0.92, 0.68, -0.82, 1.55, 0.82, "Mat_CarPaint")
$chassis.AddBox(-1.18, 0.92, -0.82, -0.82, 1.55, -0.68, "Mat_CarPaint")

# Roof panel with rain gutters
$chassis.AddBox(-0.85, 1.50, -0.70, 0.60, 1.55, 0.70, "Mat_CarPaint")
$chassis.AddBox(-0.86, 1.52, 0.68, 0.61, 1.56, 0.72, "Mat_Plastic_Black")
$chassis.AddBox(-0.86, 1.52, -0.72, 0.61, 1.56, -0.68, "Mat_Plastic_Black")

# Fixed Windshield Glass & Rear Window Glass
# Windshield
$chassis.AddQuad(
    [Vec3]::new(1.00, 0.90, -0.66), [Vec3]::new(1.00, 0.90, 0.66),
    [Vec3]::new(0.60, 1.50, 0.64), [Vec3]::new(0.60, 1.50, -0.64),
    [Vec3]::new(0.83, 0.55, 0), "Mat_Glass_Tinted"
)
# Rear Window
$chassis.AddQuad(
    [Vec3]::new(-0.85, 1.52, 0.64), [Vec3]::new(-0.85, 1.52, -0.64),
    [Vec3]::new(-1.20, 0.95, -0.68), [Vec3]::new(-1.20, 0.95, 0.68),
    [Vec3]::new(-0.85, 0.52, 0), "Mat_Glass_Tinted"
)

# Dashboard & Interior Controls
$chassis.AddBox(0.52, 0.40, -0.72, 0.92, 0.88, 0.72, "Mat_Interior_Dark")
# Steering Column & Wheel (Driver at left Z=+0.38m)
$chassis.AddCylinderX(0.48, 0.65, 0.65, 0.38, 0.035, 8, "Mat_Plastic_Black", $true)
$chassis.AddCylinderX(0.45, 0.48, 0.65, 0.38, 0.18, 16, "Mat_Plastic_Black", $false)
$chassis.AddBox(0.44, 0.63, 0.35, 0.49, 0.67, 0.41, "Mat_Chrome") # Center badge

# Center console shifter & handbrake
$chassis.AddBox(0.18, -0.05, -0.04, 0.32, 0.15, 0.04, "Mat_Plastic_Black")
$chassis.AddCylinderZ(-0.02, 0.02, 0.25, 0.15, 0.015, 6, "Mat_Chrome", $true)

# Rear quarter outer skins — above the wheel tubs, from C-pillar to the tail.
# The 2D shell (car_base_body) has this panel; the stripped 3D chassis did not.
$chassis.AddBox(-2.38, 0.50, 0.84, -0.82, 0.94, 0.92, "Mat_CarPaint")
$chassis.AddBox(-2.38, 0.50, -0.92, -0.82, 0.94, -0.84, "Mat_CarPaint")

# Fuel filler door: square outline on the LEFT rear quarter, above the wheel.
# Same anchor as CarBodyFx.FUEL_LID_* (fx 0.168 / fy 0.552 / size 0.026).
$lidX = (0.168 - 0.5) * 5.6
$lidY = (0.913 - 0.552) * 1.746
$lidHalf = (0.026 * 5.6) / 2.0
$frame = 0.010
$zSkin = 0.92
$zProud = 0.936
$chassis.AddBox(
    $lidX - $lidHalf - $frame, $lidY - $lidHalf - $frame, $zSkin - 0.004,
    $lidX + $lidHalf + $frame, $lidY + $lidHalf + $frame, $zSkin + 0.006,
    "Mat_Plastic_Black"
)
$chassis.AddBox(
    $lidX - $lidHalf, $lidY - $lidHalf, $zSkin,
    $lidX + $lidHalf, $lidY + $lidHalf, $zProud,
    "Mat_CarPaint"
)
# Hinge on the rear edge of the flap
$chassis.AddBox(
    $lidX - $lidHalf, $lidY - $lidHalf + 0.012, $zProud - 0.002,
    $lidX - $lidHalf + 0.016, $lidY + $lidHalf - 0.012, $zProud + 0.006,
    "Mat_CarPaint_Dark"
)

$AllParts.Add($chassis)

# =============================================================================
# PART 2: Hood (HOOD slot)
# =============================================================================
Write-Host "Building: Hood..." -ForegroundColor Gray
$hood = [ModularPart]::new("Hood", "HOOD", "hood", [Vec3]::new(0.98, 0.86, 0.0))
# Hood main plate: X from 0.95 to 2.10, Y slopes from 0.88 down to 0.45
$hoodSteps = 12
$hxMin = 0.95; $hxMax = 2.10
$hStep = ($hxMax - $hxMin) / $hoodSteps
for ($i = 0; $i -lt $hoodSteps; $i++) {
    $x1 = $hxMin + $i * $hStep
    $x2 = $hxMin + ($i + 1) * $hStep
    # Parabolic slope matching hoodPoly
    $t1 = ($x1 - $hxMin) / ($hxMax - $hxMin)
    $t2 = ($x2 - $hxMin) / ($hxMax - $hxMin)
    $y1 = 0.88 - 0.43 * [Math]::Pow($t1, 1.2)
    $y2 = 0.88 - 0.43 * [Math]::Pow($t2, 1.2)
    $w1 = 0.78 - 0.06 * $t1
    $w2 = 0.78 - 0.06 * $t2

    $hood.AddQuad(
        [Vec3]::new($x1, $y1, -$w1), [Vec3]::new($x2, $y2, -$w2),
        [Vec3]::new($x2, $y2, $w2), [Vec3]::new($x1, $y1, $w1),
        [Vec3]::new(0.3, 0.95, 0).Normalized(), "Mat_CarPaint"
    )
    # Underside
    $hood.AddQuad(
        [Vec3]::new($x1, $y1 - 0.02, $w1), [Vec3]::new($x2, $y2 - 0.02, $w2),
        [Vec3]::new($x2, $y2 - 0.02, -$w2), [Vec3]::new($x1, $y1 - 0.02, -$w1),
        [Vec3]::new(0, -1, 0), "Mat_Plastic_Black"
    )
}
# Center hood bulge (Power bulge / muscle crease)
$hood.AddBox(1.20, 0.74, -0.22, 1.95, 0.78, 0.22, "Mat_CarPaint")
$AllParts.Add($hood)

# =============================================================================
# PART 3: Front Bumper (FRONT_BUMPER slot)
# =============================================================================
Write-Host "Building: Front_Bumper..." -ForegroundColor Gray
$fBumper = [ModularPart]::new("Front_Bumper", "FRONT_BUMPER", "bumper_f", [Vec3]::new(2.15, 0.05, 0.0))
# Main front bumper bar
$fBumper.AddBox(2.10, -0.28, -0.84, 2.45, 0.35, 0.84, "Mat_Plastic_Black")
# Front nose trim (upper matching body paint)
$fBumper.AddBox(2.15, 0.15, -0.82, 2.46, 0.36, 0.82, "Mat_CarPaint")
# Lower grille cutouts
$fBumper.AddBox(2.40, -0.20, -0.45, 2.47, 0.05, 0.45, "Mat_Radiator")
# Recessed Fog Lights (Left & Right)
$fBumper.AddCylinderX(2.38, 2.47, 0.0, 0.62, 0.075, 12, "Mat_HeadlightLens", $true)
$fBumper.AddCylinderX(2.38, 2.47, 0.0, -0.62, 0.075, 12, "Mat_HeadlightLens", $true)
# Front license plate holder
$fBumper.AddBox(2.46, -0.15, -0.18, 2.48, -0.02, 0.18, "Mat_Chrome")
$AllParts.Add($fBumper)

# =============================================================================
# PART 4: Rear Bumper (REAR_BUMPER slot)
# =============================================================================
Write-Host "Building: Rear_Bumper..." -ForegroundColor Gray
$rBumper = [ModularPart]::new("Rear_Bumper", "REAR_BUMPER", "bumper_r", [Vec3]::new(-2.50, 0.05, 0.0))
$rBumper.AddBox(-2.78, -0.25, -0.85, -2.48, 0.38, 0.85, "Mat_Plastic_Black")
$rBumper.AddBox(-2.77, 0.18, -0.84, -2.47, 0.39, 0.84, "Mat_CarPaint")
# Dual Chrome Exhaust Pipes (Left & Right)
$rBumper.AddCylinderX(-2.88, -2.70, -0.18, 0.52, 0.045, 10, "Mat_Chrome", $true)
$rBumper.AddCylinderX(-2.88, -2.70, -0.18, -0.52, 0.045, 10, "Mat_Chrome", $true)
$AllParts.Add($rBumper)

# =============================================================================
# PART 5: Trunk Lid (TRUNK_LID slot)
# =============================================================================
Write-Host "Building: Trunk_Lid..." -ForegroundColor Gray
$trunk = [ModularPart]::new("Trunk_Lid", "TRUNK_LID", "trunk_lid", [Vec3]::new(-1.22, 0.94, 0.0))
# Horizontal deck
$trunk.AddBox(-2.55, 0.72, -0.74, -1.20, 0.76, 0.74, "Mat_CarPaint")
# Vertical rear tail panel
$trunk.AddBox(-2.56, 0.45, -0.74, -2.52, 0.75, 0.74, "Mat_CarPaint")
# Trunk handle & badge
$trunk.AddBox(-2.57, 0.60, -0.12, -2.53, 0.65, 0.12, "Mat_Chrome")
$AllParts.Add($trunk)

# =============================================================================
# PART 6 & 7: Front Doors (DOOR_FRONT slot - Left & Right)
# =============================================================================
Write-Host "Building: Doors (Front L & R)..." -ForegroundColor Gray
foreach ($side in @("L", "R")) {
    $isLeft = ($side -eq "L")
    $zSign = if ($isLeft) { 1.0 } else { -1.0 }
    $zInner = 0.82 * $zSign
    $zOuter = 0.92 * $zSign
    $pName = "Door_Front_$side"
    $door = [ModularPart]::new($pName, "DOOR_FRONT", "door_front", [Vec3]::new(0.86, 0.50, $zOuter))

    # Lower outer sheet metal panel
    $minZ = [Math]::Min($zInner, $zOuter); $maxZ = [Math]::Max($zInner, $zOuter)
    $door.AddBox(0.05, -0.28, $minZ, 0.88, 0.92, $maxZ, "Mat_CarPaint")
    # Sculpted horizontal crease (from upgrade.py gy=298/306)
    $door.AddBox(0.06, 0.28, $minZ, 0.87, 0.32, $maxZ + (0.015 * $zSign), "Mat_CarPaint_Dark")

    # Window frame & glass
    # Vertical frames (A-pillar edge and B-pillar edge)
    $door.AddBox(0.05, 0.92, $minZ, 0.12, 1.48, $maxZ, "Mat_Plastic_Black")
    $door.AddBox(0.78, 0.92, $minZ, 0.86, 1.45, $maxZ, "Mat_Plastic_Black")
    $door.AddBox(0.05, 1.42, $minZ, 0.86, 1.48, $maxZ, "Mat_Plastic_Black")
    # Tinted Window Glass
    $glassZ = ($zInner + $zOuter) * 0.5
    $door.AddQuad(
        [Vec3]::new(0.12, 0.92, $glassZ), [Vec3]::new(0.78, 0.92, $glassZ),
        [Vec3]::new(0.78, 1.42, $glassZ), [Vec3]::new(0.12, 1.42, $glassZ),
        [Vec3]::new(0, 0, $zSign), "Mat_Glass_Tinted"
    )

    # Flush Chrome Door Handle
    $door.AddBox(0.20, 0.84, $zOuter, 0.36, 0.89, $zOuter + (0.02 * $zSign), "Mat_Chrome")

    # Side-view Mirror (Mounted at A-pillar corner)
    $door.AddBox(0.80, 0.92, $zOuter, 0.86, 0.96, $zOuter + (0.06 * $zSign), "Mat_Plastic_Black")
    $door.AddBox(0.76, 0.90, $zOuter + (0.06 * $zSign), 0.90, 1.02, $zOuter + (0.16 * $zSign), "Mat_Plastic_Black")
    $door.AddBox(0.75, 0.91, $zOuter + (0.07 * $zSign), 0.76, 1.01, $zOuter + (0.15 * $zSign), "Mat_Chrome") # Mirror glass

    # Inner door trim card & armrest
    $door.AddBox(0.08, -0.22, $zInner - (0.03 * $zSign), 0.85, 0.88, $zInner, "Mat_Interior_Dark")
    $door.AddBox(0.25, 0.40, $zInner - (0.07 * $zSign), 0.65, 0.48, $zInner, "Mat_Interior_Dark") # Armrest

    $AllParts.Add($door)
}

# =============================================================================
# PART 8 & 9: Rear Doors (DOOR_REAR slot - Left & Right)
# =============================================================================
Write-Host "Building: Doors (Rear L & R)..." -ForegroundColor Gray
foreach ($side in @("L", "R")) {
    $isLeft = ($side -eq "L")
    $zSign = if ($isLeft) { 1.0 } else { -1.0 }
    $zInner = 0.82 * $zSign
    $zOuter = 0.92 * $zSign
    $pName = "Door_Rear_$side"
    $door = [ModularPart]::new($pName, "DOOR_REAR", "door_rear", [Vec3]::new(0.02, 0.50, $zOuter))

    $minZ = [Math]::Min($zInner, $zOuter); $maxZ = [Math]::Max($zInner, $zOuter)
    # Lower outer sheet metal (with rear lower wheel arch clearance)
    $door.AddBox(-0.80, -0.28, $minZ, 0.03, 0.94, $maxZ, "Mat_CarPaint")
    $door.AddBox(-0.79, 0.28, $minZ, 0.02, 0.32, $maxZ + (0.015 * $zSign), "Mat_CarPaint_Dark")

    # Window frame & glass
    $door.AddBox(-0.03, 0.94, $minZ, 0.03, 1.50, $maxZ, "Mat_Plastic_Black")
    $door.AddBox(-0.78, 0.94, $minZ, -0.70, 1.48, $maxZ, "Mat_Plastic_Black")
    $door.AddBox(-0.78, 1.44, $minZ, 0.03, 1.50, $maxZ, "Mat_Plastic_Black")

    $glassZ = ($zInner + $zOuter) * 0.5
    $door.AddQuad(
        [Vec3]::new(-0.70, 0.94, $glassZ), [Vec3]::new(-0.03, 0.94, $glassZ),
        [Vec3]::new(-0.03, 1.44, $glassZ), [Vec3]::new(-0.70, 1.44, $glassZ),
        [Vec3]::new(0, 0, $zSign), "Mat_Glass_Tinted"
    )

    # Flush Chrome Handle
    $door.AddBox(-0.65, 0.84, $zOuter, -0.49, 0.89, $zOuter + (0.02 * $zSign), "Mat_Chrome")

    # Inner door panel
    $door.AddBox(-0.75, -0.22, $zInner - (0.03 * $zSign), 0.0, 0.90, $zInner, "Mat_Interior_Dark")
    $door.AddBox(-0.55, 0.40, $zInner - (0.07 * $zSign), -0.15, 0.48, $zInner, "Mat_Interior_Dark")

    $AllParts.Add($door)
}

# =============================================================================
# PART 10 & 11: Headlights (HEADLIGHT slot - Left & Right)
# =============================================================================
Write-Host "Building: Headlights (L & R)..." -ForegroundColor Gray
foreach ($side in @("L", "R")) {
    $isLeft = ($side -eq "L")
    $zSign = if ($isLeft) { 1.0 } else { -1.0 }
    $z1 = 0.52 * $zSign; $z2 = 0.78 * $zSign
    $minZ = [Math]::Min($z1, $z2); $maxZ = [Math]::Max($z1, $z2)
    $hl = [ModularPart]::new("Headlight_$side", "HEADLIGHT", "headlight", [Vec3]::new(2.15, 0.43, ($z1 + $z2) * 0.5))

    # Housing back cup
    $hl.AddBox(2.05, 0.32, $minZ, 2.22, 0.54, $maxZ, "Mat_Plastic_Black")
    # Chrome reflector bowls (Twin eyes)
    $cz = ($minZ + $maxZ) * 0.5
    $hl.AddCylinderX(2.07, 2.22, 0.43, $cz - 0.06 * $zSign, 0.05, 10, "Mat_Chrome", $true)
    $hl.AddCylinderX(2.07, 2.22, 0.43, $cz + 0.06 * $zSign, 0.05, 10, "Mat_Chrome", $true)
    # Clear outer lens
    $hl.AddBox(2.22, 0.32, $minZ, 2.26, 0.54, $maxZ, "Mat_HeadlightLens")
    $AllParts.Add($hl)
}

# =============================================================================
# PART 12 & 13: Taillights (TAILLIGHT slot - Left & Right)
# =============================================================================
Write-Host "Building: Taillights (L & R)..." -ForegroundColor Gray
foreach ($side in @("L", "R")) {
    $isLeft = ($side -eq "L")
    $zSign = if ($isLeft) { 1.0 } else { -1.0 }
    $z1 = 0.55 * $zSign; $z2 = 0.82 * $zSign
    $minZ = [Math]::Min($z1, $z2); $maxZ = [Math]::Max($z1, $z2)
    $tl = [ModularPart]::new("Taillight_$side", "TAILLIGHT", "taillight", [Vec3]::new(-2.48, 0.60, ($z1 + $z2) * 0.5))

    # Housing
    $tl.AddBox(-2.55, 0.48, $minZ, -2.40, 0.72, $maxZ, "Mat_Plastic_Black")
    # Upper Red Stop/Brake section
    $tl.AddBox(-2.58, 0.60, $minZ, -2.55, 0.72, $maxZ, "Mat_TaillightRed")
    # Middle White Reverse/Turn section
    $tl.AddBox(-2.58, 0.53, $minZ, -2.55, 0.60, $maxZ, "Mat_TaillightWhite")
    # Lower Red Reflector section
    $tl.AddBox(-2.58, 0.48, $minZ, -2.55, 0.53, $maxZ, "Mat_TaillightRed")
    $AllParts.Add($tl)
}

# =============================================================================
# PART 14 & 15: Front Seats (SEAT_FRONT slot - Left & Right)
# =============================================================================
Write-Host "Building: Seats (Front L & R)..." -ForegroundColor Gray
foreach ($side in @("L", "R")) {
    $isLeft = ($side -eq "L")
    $zC = if ($isLeft) { 0.38 } else { -0.38 }
    $seat = [ModularPart]::new("Seat_Front_$side", "SEAT_FRONT", "seat_front", [Vec3]::new(0.28, 0.25, $zC))

    # Floor mounting steel brackets & rails
    $seat.AddBox(0.08, -0.28, $zC - 0.20, 0.52, -0.24, $zC - 0.16, "Mat_EngineMetal")
    $seat.AddBox(0.08, -0.28, $zC + 0.16, 0.52, -0.24, $zC + 0.20, "Mat_EngineMetal")

    # Seat Cushion Base
    $seat.AddBox(0.10, -0.24, $zC - 0.24, 0.50, -0.06, $zC + 0.24, "Mat_Seat_Fabric")
    # Side Thigh Bolsters
    $seat.AddBox(0.12, -0.06, $zC - 0.25, 0.48, 0.05, $zC - 0.18, "Mat_Interior_Dark")
    $seat.AddBox(0.12, -0.06, $zC + 0.18, 0.48, 0.05, $zC + 0.25, "Mat_Interior_Dark")

    # Contoured Backrest (angled back slightly)
    $seat.AddBox(0.05, -0.06, $zC - 0.22, 0.15, 0.65, $zC + 0.22, "Mat_Seat_Fabric")
    # Side Torso Bolsters
    $seat.AddBox(0.08, 0.10, $zC - 0.24, 0.18, 0.60, $zC - 0.16, "Mat_Interior_Dark")
    $seat.AddBox(0.08, 0.10, $zC + 0.16, 0.18, 0.60, $zC + 0.24, "Mat_Interior_Dark")

    # Headrest
    $seat.AddBox(0.06, 0.66, $zC - 0.02, 0.10, 0.72, $zC + 0.02, "Mat_Chrome") # Steel poles
    $seat.AddBox(0.03, 0.72, $zC - 0.14, 0.13, 0.88, $zC + 0.14, "Mat_Seat_Fabric")

    $AllParts.Add($seat)
}

# =============================================================================
# PART 16: Rear Seat (SEAT_REAR slot)
# =============================================================================
Write-Host "Building: Seat_Rear..." -ForegroundColor Gray
$rSeat = [ModularPart]::new("Seat_Rear", "SEAT_REAR", "seat_rear", [Vec3]::new(-0.55, 0.25, 0.0))
# Bench Cushion
$rSeat.AddBox(-0.75, -0.24, -0.68, -0.32, -0.08, 0.68, "Mat_Seat_Fabric")
# Split Backrest
$rSeat.AddBox(-0.85, -0.08, -0.68, -0.75, 0.68, 0.68, "Mat_Seat_Fabric")
# Integrated Rear Headrests (Left & Right)
$rSeat.AddBox(-0.84, 0.68, 0.25, -0.76, 0.82, 0.55, "Mat_Seat_Fabric")
$rSeat.AddBox(-0.84, 0.68, -0.55, -0.76, 0.82, -0.25, "Mat_Seat_Fabric")
$AllParts.Add($rSeat)

# =============================================================================
# PART 17: Roof Rack (ROOF_RACK slot - Expedition style)
# =============================================================================
Write-Host "Building: Roof_Rack..." -ForegroundColor Gray
$rack = [ModularPart]::new("Roof_Rack", "ROOF_RACK", "roof_rack", [Vec3]::new(-0.15, 1.54, 0.0))
# 4 Mounting Feet (clamped to rain channels)
foreach ($rx in @(-0.72, 0.42)) {
    foreach ($rz in @(-0.62, 0.62)) {
        $rack.AddBox($rx - 0.04, 1.52, $rz - 0.03, $rx + 0.04, 1.60, $rz + 0.03, "Mat_Plastic_Black")
    }
}
# Lower Base Tubular Frame
$rack.AddBox(-0.85, 1.60, -0.62, 0.55, 1.63, -0.58, "Mat_RoofRack")
$rack.AddBox(-0.85, 1.60, 0.58, 0.55, 1.63, 0.62, "Mat_RoofRack")
$rack.AddBox(-0.85, 1.60, -0.62, -0.81, 1.63, 0.62, "Mat_RoofRack")
$rack.AddBox(0.51, 1.60, -0.62, 0.55, 1.63, 0.62, "Mat_RoofRack")

# Upper Perimeter Railing
$rack.AddBox(-0.85, 1.68, -0.62, 0.55, 1.71, -0.58, "Mat_RoofRack")
$rack.AddBox(-0.85, 1.68, 0.58, 0.55, 1.71, 0.62, "Mat_RoofRack")
$rack.AddBox(-0.85, 1.68, -0.62, -0.81, 1.71, 0.62, "Mat_RoofRack")
$rack.AddBox(0.51, 1.68, -0.62, 0.55, 1.71, 0.62, "Mat_RoofRack")

# Vertical Corner & Side Struts
foreach ($rx in @(-0.84, -0.40, 0.05, 0.52)) {
    $rack.AddBox($rx, 1.60, -0.61, $rx + 0.03, 1.70, -0.59, "Mat_RoofRack")
    $rack.AddBox($rx, 1.60, 0.59, $rx + 0.03, 1.70, 0.61, "Mat_RoofRack")
}

# 6 Transverse Cargo Crossbars
for ($c = 0; $c -le 5; $c++) {
    $barX = -0.75 + $c * 0.24
    $rack.AddBox($barX - 0.015, 1.60, -0.58, $barX + 0.015, 1.62, 0.58, "Mat_RoofRack")
}

# Expedition Spotlight Unit on front of rack (matching HeadlightFx.RACK_LAMP_FX)
$rack.AddBox(0.55, 1.62, -0.18, 0.65, 1.72, 0.18, "Mat_Plastic_Black")
$rack.AddBox(0.65, 1.63, -0.17, 0.66, 1.71, 0.17, "Mat_HeadlightLens") # Lamp lens
$AllParts.Add($rack)

# =============================================================================
# PART 18: Engine (ENGINE slot)
# =============================================================================
Write-Host "Building: Engine..." -ForegroundColor Gray
$eng = [ModularPart]::new("Engine", "ENGINE", "engine_a", [Vec3]::new(1.55, 0.15, 0.0))
# Engine Block (Inline 4)
$eng.AddBox(1.35, -0.15, -0.22, 1.75, 0.22, 0.22, "Mat_EngineMetal")
# Cylinder Head & Valve Cover (Top)
$eng.AddBox(1.38, 0.22, -0.20, 1.72, 0.38, 0.20, "Mat_CarPaint_Dark")
$eng.AddCylinderX(1.42, 1.48, 0.40, 0.08, 0.03, 8, "Mat_Gold", $true) # Oil Cap

# Intake Manifold & Air Filter Box (Right side)
$eng.AddBox(1.40, 0.12, -0.38, 1.70, 0.28, -0.22, "Mat_Plastic_Black")
$eng.AddBox(1.25, 0.18, -0.45, 1.45, 0.35, -0.28, "Mat_Plastic_Black") # Air filter box

# Exhaust Manifold & Heat Shield (Left side)
$eng.AddBox(1.40, 0.05, 0.22, 1.70, 0.25, 0.35, "Mat_EngineMetal")
$eng.AddCylinderX(1.30, 1.42, -0.05, 0.28, 0.04, 8, "Mat_EngineMetal", $false) # Downpipe

# Front Serpentine Belt, Pulleys & Alternator
$eng.AddCylinderX(1.75, 1.78, 0.0, 0.0, 0.09, 12, "Mat_Chrome", $true)       # Crank pulley
$eng.AddCylinderX(1.75, 1.78, 0.25, 0.0, 0.06, 12, "Mat_Chrome", $true)      # Cam pulley
$eng.AddCylinderX(1.74, 1.77, 0.15, 0.18, 0.05, 10, "Mat_Chrome", $true)      # Alternator pulley
$eng.AddBox(1.60, 0.08, 0.14, 1.74, 0.22, 0.26, "Mat_EngineMetal")           # Alternator body
$eng.AddBox(1.32, -0.10, -0.18, 1.45, 0.02, -0.06, "Mat_EngineMetal")         # Starter motor

# Transmission / Bellhousing
$eng.AddBox(1.10, -0.18, -0.18, 1.35, 0.12, 0.18, "Mat_EngineMetal")
$AllParts.Add($eng)

# =============================================================================
# PART 19: Radiator (RADIATOR slot)
# =============================================================================
Write-Host "Building: Radiator..." -ForegroundColor Gray
$rad = [ModularPart]::new("Radiator", "RADIATOR", "radiator", [Vec3]::new(2.05, 0.15, 0.0))
# Radiator aluminum fin core
$rad.AddBox(2.03, -0.10, -0.42, 2.07, 0.38, 0.42, "Mat_Radiator")
# Upper & lower header tanks
$rad.AddBox(2.02, 0.38, -0.44, 2.08, 0.42, 0.44, "Mat_Plastic_Black")
$rad.AddBox(2.02, -0.14, -0.44, 2.08, -0.10, 0.44, "Mat_Plastic_Black")
# Electric Cooling Fan (on rear of radiator)
$rad.AddCylinderX(1.98, 2.03, 0.14, 0.0, 0.18, 16, "Mat_Plastic_Black", $false)
$rad.AddCylinderX(1.97, 2.01, 0.14, 0.0, 0.05, 8, "Mat_Plastic_Black", $true) # Fan hub
$AllParts.Add($rad)

# =============================================================================
# PART 20: Battery (BATTERY slot)
# =============================================================================
Write-Host "Building: Battery..." -ForegroundColor Gray
$bat = [ModularPart]::new("Battery", "BATTERY", "battery_std", [Vec3]::new(1.66, 0.34, -0.51))
# Battery Casing
$bat.AddBox(1.55, 0.22, -0.62, 1.78, 0.44, -0.40, "Mat_Battery")
# Top lid & handle
$bat.AddBox(1.54, 0.44, -0.63, 1.79, 0.46, -0.39, "Mat_Plastic_Black")
# Positive Terminal (Red)
$bat.AddCylinderX(1.60, 1.64, 0.48, -0.56, 0.015, 8, "Mat_BrakeCaliper", $true)
# Negative Terminal (Black/Chrome)
$bat.AddCylinderX(1.70, 1.74, 0.48, -0.46, 0.015, 8, "Mat_Chrome", $true)
$AllParts.Add($bat)

# =============================================================================
# PART 21: Fuel Tank (FUEL_TANK slot)
# =============================================================================
Write-Host "Building: Fuel_Tank..." -ForegroundColor Gray
$tank = [ModularPart]::new("Fuel_Tank", "FUEL_TANK", "tank_std", [Vec3]::new(-1.20, -0.13, 0.0))
# Fuel Tank Vessel
$tank.AddBox(-1.45, -0.28, -0.52, -0.95, 0.00, 0.52, "Mat_EngineMetal")
# Steel mounting straps
$tank.AddBox(-1.38, -0.29, -0.53, -1.33, 0.01, 0.53, "Mat_Plastic_Black")
$tank.AddBox(-1.08, -0.29, -0.53, -1.03, 0.01, 0.53, "Mat_Plastic_Black")
# Fuel pump module
$tank.AddCylinderX(-1.22, -1.16, 0.02, 0.0, 0.08, 10, "Mat_Chrome", $true)
$AllParts.Add($tank)

# =============================================================================
# PART 22: Suspension, Brakes & Drivetrain (SUSPENSION, BRAKES, DRIVETRAIN)
# =============================================================================
Write-Host "Building: Suspension_Drivetrain..." -ForegroundColor Gray
$susp = [ModularPart]::new("Suspension_Drivetrain", "SUSPENSION", "suspension_basic", [Vec3]::new(0, 0, 0))

# Front Subframe & MacPherson Struts (X = +1.764m)
$fx = 1.764
$susp.AddBox($fx - 0.12, -0.28, -0.58, $fx + 0.12, -0.20, 0.58, "Mat_Plastic_Black")
foreach ($sz in @(-0.68, 0.68)) {
    # Coil Springs / Strut towers
    $susp.AddCylinderZ($sz - 0.05, $sz + 0.05, $fx, 0.20, 0.065, 12, "Mat_BrakeCaliper", $true)
    # Lower control wishbone arm
    $susp.AddBox($fx - 0.08, -0.24, [Math]::Min(0.50 * [Math]::Sign($sz), $sz), $fx + 0.08, -0.18, [Math]::Max(0.50 * [Math]::Sign($sz), $sz), "Mat_EngineMetal")
}

# Rear Subframe & Independent Arms (X = -1.764m)
$rx = -1.764
$susp.AddBox($rx - 0.14, -0.28, -0.58, $rx + 0.14, -0.18, 0.58, "Mat_Plastic_Black")
foreach ($sz in @(-0.68, 0.68)) {
    $susp.AddCylinderZ($sz - 0.05, $sz + 0.05, $rx, 0.18, 0.065, 12, "Mat_BrakeCaliper", $true)
    $susp.AddBox($rx - 0.08, -0.24, [Math]::Min(0.50 * [Math]::Sign($sz), $sz), $rx + 0.08, -0.18, [Math]::Max(0.50 * [Math]::Sign($sz), $sz), "Mat_EngineMetal")
}

# Longitudinal Driveshaft (from engine at X=1.10 to rear diff at X=-1.764)
$susp.AddCylinderX(-1.70, 1.10, -0.12, 0.0, 0.035, 8, "Mat_Chrome", $false)
# Rear Differential Pumpkin
$susp.AddBox($rx - 0.18, -0.22, -0.15, $rx + 0.18, 0.02, 0.15, "Mat_EngineMetal")

# 4 Disc Brakes & Calipers (At all 4 wheel hubs)
foreach ($ax in @($fx, $rx)) {
    foreach ($az in @(-0.76, 0.76)) {
        $sign = [Math]::Sign($az)
        # Ventilated Brake Disc (Diameter 0.32m -> Radius 0.16m)
        $susp.AddCylinderZ($az - 0.015 * $sign, $az + 0.015 * $sign, $ax, 0.0, 0.16, 16, "Mat_BrakeDisc", $true)
        # Sport Caliper (Red)
        $susp.AddBox($ax - 0.05, 0.08, [Math]::Min($az, $az + 0.04 * $sign), $ax + 0.09, 0.18, [Math]::Max($az, $az + 0.04 * $sign), "Mat_BrakeCaliper")
    }
}
$AllParts.Add($susp)

# =============================================================================
# PART 23-26: Wheels (Front & Rear, Left & Right)
# =============================================================================
Write-Host "Building: Wheels (FL, FR, RL, RR) with detachable Rim & Tire..." -ForegroundColor Gray

# Wheel parameters matching GameConfig:
# WHEEL_RADIUS = 0.48m (outer diameter 0.96m)
# WHEEL_HUB_FRAC = 0.61f -> Rim radius = 0.61 * 0.48 = 0.2928m (diameter 0.5856m)
# Tire Width = 0.26m
$wheelPositions = @(
    @{ Name = "Wheel_FL"; Slot = "TIRE_FRONT"; Item = "tire_std"; X = 1.764; Z = 0.78 }
    @{ Name = "Wheel_FR"; Slot = "TIRE_FRONT"; Item = "tire_std"; X = 1.764; Z = -0.78 }
    @{ Name = "Wheel_RL"; Slot = "TIRE_REAR"; Item = "tire_std"; X = -1.764; Z = 0.78 }
    @{ Name = "Wheel_RR"; Slot = "TIRE_REAR"; Item = "tire_std"; X = -1.764; Z = -0.78 }
)

foreach ($wp in $wheelPositions) {
    $wx = $wp.X; $wz = $wp.Z
    $isL = ($wz -gt 0)
    $zSign = if ($isL) { 1.0 } else { -1.0 }
    $wheel = [ModularPart]::new($wp.Name, $wp.Slot, $wp.Item, [Vec3]::new($wx, 0.0, $wz))

    $halfW = 0.13
    $zIn = $wz - $halfW * $zSign
    $zOut = $wz + $halfW * $zSign
    $minZ = [Math]::Min($zIn, $zOut); $maxZ = [Math]::Max($zIn, $zOut)

    $rimRadius = 0.2928
    $outerRadius = 0.48
    $segs = 24

    # 1. WHEEL RIM (Hub, Spokes with Circular Openings, Lip)
    $wheel.AddCylinderZ($minZ, $maxZ, $wx, 0.0, $rimRadius, $segs, "Mat_WheelRim", $false)
    # Outer Rim Bead Lip
    $wheel.AddCylinderZ($zOut - 0.02 * $zSign, $zOut, $wx, 0.0, $rimRadius + 0.015, $segs, "Mat_Chrome", $true)

    # Center Hub & 5 Lugs
    $hubZ = $zOut - 0.03 * $zSign
    $wheel.AddCylinderZ($hubZ, $zOut + 0.01 * $zSign, $wx, 0.0, 0.09, 12, "Mat_WheelRim", $true)
    for ($l = 0; $l -lt 5; $l++) {
        $la = $l * (2.0 * [Math]::PI / 5.0)
        $lx = $wx + 0.055 * [Math]::Cos($la)
        $ly = 0.055 * [Math]::Sin($la)
        $wheel.AddCylinderZ($hubZ, $zOut + 0.02 * $zSign, $lx, $ly, 0.012, 6, "Mat_Chrome", $true)
    }

    # 8 Rim Spokes / Circular cutouts (Matching the iconic 2D game wheel sprite!)
    for ($s = 0; $s -lt 8; $s++) {
        $sa = $s * (2.0 * [Math]::PI / 8.0)
        $sx = $wx + 0.19 * [Math]::Cos($sa)
        $sy = 0.19 * [Math]::Sin($sa)
        $wheel.AddCylinderZ($zOut - 0.04 * $zSign, $zOut - 0.01 * $zSign, $sx, $sy, 0.045, 8, "Mat_Plastic_Black", $true)
    }

    # 2. ALL-TERRAIN KNOBBY TIRE (Modular Tire on Rim)
    # Inner bead mating with rim
    $step = 2.0 * [Math]::PI / $segs
    for ($i = 0; $i -lt $segs; $i++) {
        $a1 = $i * $step; $a2 = ($i + 1) * $step
        $cos1 = [Math]::Cos($a1); $sin1 = [Math]::Sin($a1)
        $cos2 = [Math]::Cos($a2); $sin2 = [Math]::Sin($a2)

        # Sidewall (Outer side)
        $p1 = [Vec3]::new($wx + $rimRadius * $cos1, $rimRadius * $sin1, $zOut)
        $p2 = [Vec3]::new($wx + $rimRadius * $cos2, $rimRadius * $sin2, $zOut)
        $p3 = [Vec3]::new($wx + ($outerRadius - 0.03) * $cos2, ($outerRadius - 0.03) * $sin2, $zOut)
        $p4 = [Vec3]::new($wx + ($outerRadius - 0.03) * $cos1, ($outerRadius - 0.03) * $sin1, $zOut)
        $wheel.AddQuad($p1, $p2, $p3, $p4, [Vec3]::new(0, 0, $zSign), "Mat_TireRubber")

        # Sidewall (Inner side)
        $q1 = [Vec3]::new($wx + $rimRadius * $cos1, $rimRadius * $sin1, $zIn)
        $q2 = [Vec3]::new($wx + ($outerRadius - 0.03) * $cos1, ($outerRadius - 0.03) * $sin1, $zIn)
        $q3 = [Vec3]::new($wx + ($outerRadius - 0.03) * $cos2, ($outerRadius - 0.03) * $sin2, $zIn)
        $q4 = [Vec3]::new($wx + $rimRadius * $cos2, $rimRadius * $sin2, $zIn)
        $wheel.AddQuad($q1, $q2, $q3, $q4, [Vec3]::new(0, 0, -$zSign), "Mat_TireRubber")

        # Outer tread surface
        $t1 = [Vec3]::new($wx + $outerRadius * $cos1, $outerRadius * $sin1, $minZ)
        $t2 = [Vec3]::new($wx + $outerRadius * $cos2, $outerRadius * $sin2, $minZ)
        $t3 = [Vec3]::new($wx + $outerRadius * $cos2, $outerRadius * $sin2, $maxZ)
        $t4 = [Vec3]::new($wx + $outerRadius * $cos1, $outerRadius * $sin1, $maxZ)
        $normT = [Vec3]::new($cos1, $sin1, 0)
        $wheel.AddQuad($t1, $t2, $t3, $t4, $normT, "Mat_TireRubber")

        # Knobby Lug Tread Blocks (Every second segment has a deep off-road tread block!)
        if ($i % 2 -eq 0) {
            $lugR = $outerRadius + 0.02
            $l1 = [Vec3]::new($wx + $lugR * $cos1, $lugR * $sin1, $minZ + 0.02)
            $l2 = [Vec3]::new($wx + $lugR * $cos2, $lugR * $sin2, $minZ + 0.02)
            $l3 = [Vec3]::new($wx + $lugR * $cos2, $lugR * $sin2, $maxZ - 0.02)
            $l4 = [Vec3]::new($wx + $lugR * $cos1, $lugR * $sin1, $maxZ - 0.02)
            $wheel.AddQuad($l1, $l2, $l3, $l4, $normT, "Mat_TireRubber")
        }
    }

    $AllParts.Add($wheel)
}

Write-Host "Total Modular Parts Generated: $($AllParts.Count)" -ForegroundColor Green

# -----------------------------------------------------------------------------
# Exporter: Directory Setup
# -----------------------------------------------------------------------------
if (!(Test-Path $OutputDir)) { New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null }
$PartsDir = Join-Path $OutputDir "parts"
if (!(Test-Path $PartsDir)) { New-Item -ItemType Directory -Path $PartsDir -Force | Out-Null }

if (!(Test-Path $AssetDir)) { New-Item -ItemType Directory -Path $AssetDir -Force | Out-Null }

# -----------------------------------------------------------------------------
# Exporter 1: car_modular.mtl
# -----------------------------------------------------------------------------
Write-Host "Exporting: car_modular.mtl..." -ForegroundColor Yellow
$mtlPath = Join-Path $OutputDir "car_modular.mtl"
$mtlWriter = [System.IO.StreamWriter]::new($mtlPath, $false, [System.Text.Encoding]::UTF8)
$mtlWriter.WriteLine("# EndlessDrive Modular Car Material Library")
$mtlWriter.WriteLine("# Standard PBR and Phong shading attributes`n")

foreach ($mName in $Materials.Keys | Sort-Object) {
    $m = $Materials[$mName]
    $mtlWriter.WriteLine("newmtl $mName")
    $mtlWriter.WriteLine("Ka 0.2 0.2 0.2")
    $mtlWriter.WriteLine("Kd $($m.Kd)")
    $mtlWriter.WriteLine("Ks $($m.Ks)")
    $mtlWriter.WriteLine("Ns $($m.Ns)")
    $mtlWriter.WriteLine("d $($m.d)")
    $mtlWriter.WriteLine("illum 2`n")
}
$mtlWriter.Close()

# -----------------------------------------------------------------------------
# Exporter 2: car_modular.obj (Complete Assembled Modular Model)
# -----------------------------------------------------------------------------
Write-Host "Exporting: car_modular.obj (Complete model with all objects)..." -ForegroundColor Yellow
$objPath = Join-Path $OutputDir "car_modular.obj"
$objWriter = [System.IO.StreamWriter]::new($objPath, $false, [System.Text.Encoding]::UTF8)
$objWriter.WriteLine("# EndlessDrive - Assembled Modular Car 3D Model")
$objWriter.WriteLine("# Total Parts: $($AllParts.Count)")
$objWriter.WriteLine("mtllib car_modular.mtl`n")

$vOffset = 1
$totalFaces = 0

foreach ($part in $AllParts) {
    $objWriter.WriteLine("o $($part.Name)")
    $objWriter.WriteLine("g $($part.Name)")

    # Vertices
    foreach ($v in $part.Vertices) {
        $p = $v.Pos
        $objWriter.WriteLine([string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "v {0:F4} {1:F4} {2:F4}", $p.X, $p.Y, $p.Z))
    }
    # Normals
    foreach ($v in $part.Vertices) {
        $n = $v.Norm
        $objWriter.WriteLine([string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "vn {0:F4} {1:F4} {2:F4}", $n.X, $n.Y, $n.Z))
    }
    # UVs
    foreach ($v in $part.Vertices) {
        $uv = $v.UV
        $objWriter.WriteLine([string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "vt {0:F4} {1:F4}", $uv.U, $uv.V))
    }

    # Faces grouped by material
    $currentMat = ""
    foreach ($f in $part.Faces) {
        if ($f.Material -ne $currentMat) {
            $currentMat = $f.Material
            $objWriter.WriteLine("usemtl $currentMat")
        }
        $i1 = $f.V1 + $vOffset
        $i2 = $f.V2 + $vOffset
        $i3 = $f.V3 + $vOffset
        $objWriter.WriteLine("f $i1/$i1/$i1 $i2/$i2/$i2 $i3/$i3/$i3")
        $totalFaces++
    }

    $vOffset += $part.Vertices.Count
    $objWriter.WriteLine("")
}

$objWriter.Close()
Write-Host "  -> car_modular.obj generated ($totalFaces triangles)" -ForegroundColor Green

# -----------------------------------------------------------------------------
# Exporter 3: Individual Parts (models/parts/*.obj)
# -----------------------------------------------------------------------------
Write-Host "Exporting individual part OBJ files..." -ForegroundColor Yellow
foreach ($part in $AllParts) {
    $partFile = Join-Path $PartsDir "$($part.Name.ToLower()).obj"
    $pWriter = [System.IO.StreamWriter]::new($partFile, $false, [System.Text.Encoding]::UTF8)
    $pWriter.WriteLine("# Part: $($part.Name) (Slot: $($part.Slot))")
    $pWriter.WriteLine("mtllib ../car_modular.mtl`n")
    $pWriter.WriteLine("o $($part.Name)")
    $pWriter.WriteLine("g $($part.Name)")

    foreach ($v in $part.Vertices) {
        $p = $v.Pos
        $pWriter.WriteLine([string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "v {0:F4} {1:F4} {2:F4}", $p.X, $p.Y, $p.Z))
    }
    foreach ($v in $part.Vertices) {
        $n = $v.Norm
        $pWriter.WriteLine([string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "vn {0:F4} {1:F4} {2:F4}", $n.X, $n.Y, $n.Z))
    }
    foreach ($v in $part.Vertices) {
        $uv = $v.UV
        $pWriter.WriteLine([string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "vt {0:F4} {1:F4}", $uv.U, $uv.V))
    }

    $currentMat = ""
    foreach ($f in $part.Faces) {
        if ($f.Material -ne $currentMat) {
            $currentMat = $f.Material
            $pWriter.WriteLine("usemtl $currentMat")
        }
        $i1 = $f.V1 + 1; $i2 = $f.V2 + 1; $i3 = $f.V3 + 1
        $pWriter.WriteLine("f $i1/$i1/$i1 $i2/$i2/$i2 $i3/$i3/$i3")
    }
    $pWriter.Close()
}

# -----------------------------------------------------------------------------
# Exporter 4: glTF 2.0 (.gltf & .glb) with Scene Hierarchy and PBR Materials
# -----------------------------------------------------------------------------
Write-Host "Exporting: car_modular.gltf & car_modular.glb..." -ForegroundColor Yellow

# Build binary buffer containing:
# - Indices (uint16 or uint32)
# - Position (float32 x 3)
# - Normal (float32 x 3)
# - TexCoord (float32 x 2)
$memStream = [System.IO.MemoryStream]::new()
$binWriter = [System.IO.BinaryWriter]::new($memStream)

$bufferViews = [System.Collections.Generic.List[PSObject]]::new()
$accessors = [System.Collections.Generic.List[PSObject]]::new()
$meshes = [System.Collections.Generic.List[PSObject]]::new()
$nodes = [System.Collections.Generic.List[PSObject]]::new()
$materialsGltf = [System.Collections.Generic.List[PSObject]]::new()
$matIndexMap = @{}

# Create glTF materials
$mIdx = 0
foreach ($mName in $Materials.Keys | Sort-Object) {
    $m = $Materials[$mName]
    $matObj = @{
        name = $mName
        pbrMetallicRoughness = @{
            baseColorFactor = $m.BaseColor
            metallicFactor = $m.Metallic
            roughnessFactor = $m.Roughness
        }
        doubleSided = $true
    }
    if ($m.d -lt 0.99) {
        $matObj.alphaMode = "BLEND"
    }
    $materialsGltf.Add($matObj)
    $matIndexMap[$mName] = $mIdx
    $mIdx++
}

# Add all 26 modular nodes to the scene
$sceneNodes = [System.Collections.Generic.List[int]]::new()
$partIndex = 0

foreach ($part in $AllParts) {
    # Group faces by material to create primitives
    $facesByMat = @{}
    foreach ($f in $part.Faces) {
        if (!$facesByMat.ContainsKey($f.Material)) {
            $facesByMat[$f.Material] = [System.Collections.Generic.List[Face]]::new()
        }
        $facesByMat[$f.Material].Add($f)
    }

    $primitives = [System.Collections.Generic.List[PSObject]]::new()

    # Align binary writer to 4 bytes
    while (($memStream.Position % 4) -ne 0) { $binWriter.Write([byte]0) }

    # 1. Vertex Position BufferView
    $posOffset = [int]$memStream.Position
    $minP = [float[]]@([float]$part.MinBBox.X, [float]$part.MinBBox.Y, [float]$part.MinBBox.Z)
    $maxP = [float[]]@([float]$part.MaxBBox.X, [float]$part.MaxBBox.Y, [float]$part.MaxBBox.Z)
    foreach ($v in $part.Vertices) {
        $binWriter.Write([float]$v.Pos.X)
        $binWriter.Write([float]$v.Pos.Y)
        $binWriter.Write([float]$v.Pos.Z)
    }
    $posLength = [int]$memStream.Position - $posOffset

    $bufferViews.Add(@{
        buffer = 0
        byteOffset = $posOffset
        byteLength = $posLength
        target = 34962 # ARRAY_BUFFER
    })
    $posBufferViewIdx = $bufferViews.Count - 1

    $accessors.Add(@{
        bufferView = $posBufferViewIdx
        byteOffset = 0
        componentType = 5126 # FLOAT
        count = $part.Vertices.Count
        type = "VEC3"
        min = $minP
        max = $maxP
    })
    $posAccessorIdx = $accessors.Count - 1

    # 2. Normal BufferView
    while (($memStream.Position % 4) -ne 0) { $binWriter.Write([byte]0) }
    $normOffset = [int]$memStream.Position
    foreach ($v in $part.Vertices) {
        $binWriter.Write([float]$v.Norm.X)
        $binWriter.Write([float]$v.Norm.Y)
        $binWriter.Write([float]$v.Norm.Z)
    }
    $normLength = [int]$memStream.Position - $normOffset
    $bufferViews.Add(@{
        buffer = 0
        byteOffset = $normOffset
        byteLength = $normLength
        target = 34962
    })
    $normBufferViewIdx = $bufferViews.Count - 1
    $accessors.Add(@{
        bufferView = $normBufferViewIdx
        byteOffset = 0
        componentType = 5126
        count = $part.Vertices.Count
        type = "VEC3"
    })
    $normAccessorIdx = $accessors.Count - 1

    # 3. TexCoord BufferView
    while (($memStream.Position % 4) -ne 0) { $binWriter.Write([byte]0) }
    $uvOffset = [int]$memStream.Position
    foreach ($v in $part.Vertices) {
        $binWriter.Write([float]$v.UV.U)
        $binWriter.Write([float]$v.UV.V)
    }
    $uvLength = [int]$memStream.Position - $uvOffset
    $bufferViews.Add(@{
        buffer = 0
        byteOffset = $uvOffset
        byteLength = $uvLength
        target = 34962
    })
    $uvBufferViewIdx = $bufferViews.Count - 1
    $accessors.Add(@{
        bufferView = $uvBufferViewIdx
        byteOffset = 0
        componentType = 5126
        count = $part.Vertices.Count
        type = "VEC2"
    })
    $uvAccessorIdx = $accessors.Count - 1

    # 4. Indices per primitive
    foreach ($matName in $facesByMat.Keys) {
        $matFaces = $facesByMat[$matName]
        while (($memStream.Position % 4) -ne 0) { $binWriter.Write([byte]0) }
        $idxOffset = [int]$memStream.Position

        $idxCount = $matFaces.Count * 3
        $minIdx = 9999999
        $maxIdx = 0

        foreach ($f in $matFaces) {
            foreach ($idx in @($f.V1, $f.V2, $f.V3)) {
                $binWriter.Write([uint32]$idx)
                if ($idx -lt $minIdx) { $minIdx = $idx }
                if ($idx -gt $maxIdx) { $maxIdx = $idx }
            }
        }
        $idxLength = [int]$memStream.Position - $idxOffset

        $bufferViews.Add(@{
            buffer = 0
            byteOffset = $idxOffset
            byteLength = $idxLength
            target = 34963 # ELEMENT_ARRAY_BUFFER
        })
        $idxBufferViewIdx = $bufferViews.Count - 1

        $accessors.Add(@{
            bufferView = $idxBufferViewIdx
            byteOffset = 0
            componentType = 5125 # UNSIGNED_INT
            count = $idxCount
            type = "SCALAR"
            min = @($minIdx)
            max = @($maxIdx)
        })
        $idxAccessorIdx = $accessors.Count - 1

        $primitives.Add(@{
            attributes = @{
                POSITION = $posAccessorIdx
                NORMAL = $normAccessorIdx
                TEXCOORD_0 = $uvAccessorIdx
            }
            indices = $idxAccessorIdx
            material = $matIndexMap[$matName]
            mode = 4 # TRIANGLES
        })
    }

    $meshes.Add(@{
        name = $part.Name
        primitives = $primitives
    })
    $meshIdx = $meshes.Count - 1

    $nodes.Add(@{
        name = $part.Name
        mesh = $meshIdx
    })
    $sceneNodes.Add($partIndex)
    $partIndex++
}

$binBytes = $memStream.ToArray()
$base64Data = [System.Convert]::ToBase64String($binBytes)

# Create glTF JSON structure
$gltfObj = @{
    asset = @{
        generator = "EndlessDrive Modular Car Exporter"
        version = "2.0"
    }
    scene = 0
    scenes = @(
        @{
            name = "CarAssembly"
            nodes = $sceneNodes
        }
    )
    nodes = $nodes
    meshes = $meshes
    materials = $materialsGltf
    accessors = $accessors
    bufferViews = $bufferViews
    buffers = @(
        @{
            byteLength = $binBytes.Length
            uri = "data:application/octet-stream;base64,$base64Data"
        }
    )
}

$gltfJson = ConvertTo-Json -InputObject $gltfObj -Depth 10
$gltfPath = Join-Path $OutputDir "car_modular.gltf"
[System.IO.File]::WriteAllText($gltfPath, $gltfJson, [System.Text.Encoding]::UTF8)
Write-Host "  -> car_modular.gltf generated" -ForegroundColor Green

# -----------------------------------------------------------------------------
# Exporter 5: car_modular.glb (Binary glTF 2.0)
# -----------------------------------------------------------------------------
# Change buffer uri for GLB (GLB buffer 0 has no uri)
$gltfObj.buffers = @(
    @{
        byteLength = $binBytes.Length
    }
)
$glbJsonString = ConvertTo-Json -InputObject $gltfObj -Depth 10 -Compress
$glbJsonBytes = [System.Text.Encoding]::UTF8.GetBytes($glbJsonString)

# Pad JSON chunk to 4-byte boundary with spaces (0x20)
$jsonPad = (4 - ($glbJsonBytes.Length % 4)) % 4
if ($jsonPad -gt 0) {
    $glbJsonBytes = [byte[]]($glbJsonBytes + [byte[]](@(0x20) * $jsonPad))
}

# Pad Binary chunk to 4-byte boundary with zeros (0x00)
$binPad = (4 - ($binBytes.Length % 4)) % 4
$binPaddedBytes = $binBytes
if ($binPad -gt 0) {
    $binPaddedBytes = [byte[]]($binBytes + [byte[]](@(0x00) * $binPad))
}

$glbTotalLength = 12 + 8 + $glbJsonBytes.Length + 8 + $binPaddedBytes.Length

$glbPath = Join-Path $OutputDir "car_modular.glb"
$glbStream = [System.IO.FileStream]::new($glbPath, [System.IO.FileMode]::Create)
$glbWriter = [System.IO.BinaryWriter]::new($glbStream)

# 12-byte GLB Header
$glbWriter.Write([uint32]0x46546C67) # 'glTF' (0x46546C67)
$glbWriter.Write([uint32]2)          # Version 2
$glbWriter.Write([uint32]$glbTotalLength)

# Chunk 0: JSON (0x4E4F534A)
$glbWriter.Write([uint32]$glbJsonBytes.Length)
$glbWriter.Write([uint32]0x4E4F534A)
$glbWriter.Write($glbJsonBytes, 0, $glbJsonBytes.Length)

# Chunk 1: BIN (0x004E4942)
$glbWriter.Write([uint32]$binPaddedBytes.Length)
$glbWriter.Write([uint32]0x004E4942)
$glbWriter.Write($binPaddedBytes, 0, $binPaddedBytes.Length)

$glbWriter.Flush()
$glbStream.Close()
Write-Host "  -> car_modular.glb generated ($([math]::Round($glbTotalLength/1024, 1)) KB)" -ForegroundColor Green

# -----------------------------------------------------------------------------
# Exporter 6: modular_car_spec.json (Specification for in-game assembly)
# -----------------------------------------------------------------------------
Write-Host "Exporting: modular_car_spec.json..." -ForegroundColor Yellow
$specList = [System.Collections.ArrayList]::new()

foreach ($part in $AllParts) {
    $pMin = $part.MinBBox
    $pMax = $part.MaxBBox
    $dx = [math]::Round(([double]$pMax.X - [double]$pMin.X), 4)
    $dy = [math]::Round(([double]$pMax.Y - [double]$pMin.Y), 4)
    $dz = [math]::Round(([double]$pMax.Z - [double]$pMin.Z), 4)

    [void]$specList.Add(@{
        name = $part.Name
        slot = $part.Slot
        itemDefId = $part.ItemId
        pivot = @([double]$part.Pivot.X, [double]$part.Pivot.Y, [double]$part.Pivot.Z)
        boundingBox = @{
            min = @([double]$pMin.X, [double]$pMin.Y, [double]$pMin.Z)
            max = @([double]$pMax.X, [double]$pMax.Y, [double]$pMax.Z)
            size = @($dx, $dy, $dz)
        }
        vertexCount = $part.Vertices.Count
        triangleCount = $part.Faces.Count
        fileObj = "parts/$($part.Name.ToLower()).obj"
    })
}

$metaSpec = @{
    carModel = "SEDAN_MODULAR_MVP"
    referenceDimensions = @{
        totalLengthM = 5.6
        wheelbaseM = 3.528
        wheelRadiusM = 0.48
        wheelTrackM = 1.56
        bodyWidthM = 1.84
        heightM = 1.88
        groundClearanceM = 0.42
    }
    slots = @(
        "CHASSIS", "HOOD", "FRONT_BUMPER", "REAR_BUMPER", "TRUNK_LID",
        "DOOR_FRONT", "DOOR_REAR", "HEADLIGHT", "TAILLIGHT",
        "SEAT_FRONT", "SEAT_REAR", "ROOF_RACK", "ENGINE", "RADIATOR",
        "BATTERY", "FUEL_TANK", "SUSPENSION", "TIRE_FRONT", "TIRE_REAR"
    )
    parts = $specList
}

$specJson = ConvertTo-Json -InputObject $metaSpec -Depth 6
$specPath = Join-Path $OutputDir "modular_car_spec.json"
[System.IO.File]::WriteAllText($specPath, $specJson, [System.Text.Encoding]::UTF8)
Write-Host "  -> modular_car_spec.json generated" -ForegroundColor Green

# -----------------------------------------------------------------------------
# Copy to Android Asset Directory
# -----------------------------------------------------------------------------
Write-Host "Copying assets to: $AssetDir..." -ForegroundColor Yellow
Copy-Item (Join-Path $OutputDir "car_modular.obj") $AssetDir -Force

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host " SUCCESS: Modular 3D Car Model Generated Successfully!" -ForegroundColor Cyan
Write-Host " Models location: $OutputDir" -ForegroundColor Cyan
Write-Host " Assets location: $AssetDir" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
