param(
    [ValidateSet('Play', 'Build', 'Check', 'Capture')]
    [string]$Action = 'Play',
    [string]$GodotPath = ''
)

$ErrorActionPreference = 'Stop'
$battlefieldRoot = Split-Path -Parent $PSScriptRoot
$battlefieldProject = Join-Path $battlefieldRoot 'godot'
$battlefieldBuild = Join-Path $battlefieldRoot 'build'
New-Item -ItemType Directory -Force -Path $battlefieldBuild | Out-Null
if (-not $GodotPath) { $GodotPath = $env:GODOT_BIN }
if (-not $GodotPath) {
    foreach ($name in @('godot', 'godot4')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) { $GodotPath = $command.Source; break }
    }
}
if (-not $GodotPath) {
    $candidates = @(
        (Join-Path $battlefieldBuild 'godot-tools'),
        (Join-Path $env:USERPROFILE 'Downloads\Godot_v4.7.2-stable_win64.exe')
    )
    foreach ($folder in $candidates) {
        $binary = Get-ChildItem -LiteralPath $folder -Filter '*_console.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($binary) { $GodotPath = $binary.FullName; break }
    }
}
if (-not $GodotPath -or -not (Test-Path -LiteralPath $GodotPath -PathType Leaf)) {
    throw 'Godot 4.7 was not found. Pass -GodotPath or set GODOT_BIN.'
}
$battlefieldArguments = @('--path', $battlefieldProject, '--rendering-method', 'forward_plus', '--audio-driver', 'Dummy')
$battlefieldLog = Join-Path $battlefieldBuild "battlefield-$($Action.ToLower()).log"
$battlefieldArguments += @('--log-file', $battlefieldLog)
switch ($Action) {
    'Play' { $battlefieldArguments += @('--resolution', '1920x1080', 'res://desert_battlefield/desert_battlefield.tscn') }
    'Build' { $battlefieldArguments += @('--headless', '--script', 'res://desert_battlefield/build_scene.gd') }
    'Check' { $battlefieldArguments += @('--headless', '--fixed-fps', '60', '--script', 'res://desert_battlefield/verify_scene.gd', '--', '--duration=120') }
    'Capture' { $battlefieldArguments += @('--resolution', '1920x1080', '--script', 'res://desert_battlefield/verify_scene.gd') }
}
# Keep this standalone showcase's shader cache away from the main game's profile.
$battlefieldOriginalAppData = $env:APPDATA
try {
    $env:APPDATA = Join-Path $battlefieldBuild 'battlefield-profile'
    New-Item -ItemType Directory -Force -Path $env:APPDATA | Out-Null
    if (-not (Test-Path -LiteralPath (Join-Path $battlefieldProject '.godot/uid_cache.bin'))) {
        $importLog = Join-Path $battlefieldBuild 'battlefield-import.log'
        & $GodotPath --path $battlefieldProject --headless --editor --import --rendering-method forward_plus --log-file $importLog
        if ($LASTEXITCODE -ne 0 -or (Select-String -LiteralPath $importLog -Pattern '^SCRIPT ERROR:|^ERROR:' -Quiet)) {
            throw "First-run asset import failed. See $importLog"
        }
    }
    if ($Action -eq 'Check') {
        $soilLog = Join-Path $battlefieldBuild 'battlefield-soil-checks.log'
        & $GodotPath --path $battlefieldProject --headless --rendering-method forward_plus --script res://desert_battlefield/soil_checks.gd --log-file $soilLog
        if ($LASTEXITCODE -ne 0 -or (Select-String -LiteralPath $soilLog -Pattern '^SCRIPT ERROR:|^ERROR:' -Quiet)) {
            throw "Sand deformation checks failed. See $soilLog"
        }
    }
    & $GodotPath @battlefieldArguments
    if ($LASTEXITCODE -ne 0) { throw "Battlefield $Action failed. See $battlefieldLog" }
    if (Select-String -LiteralPath $battlefieldLog -Pattern '^SCRIPT ERROR:|^ERROR:' -Quiet) {
        throw "Godot reported an error. See $battlefieldLog"
    }
} finally {
    $env:APPDATA = $battlefieldOriginalAppData
}
