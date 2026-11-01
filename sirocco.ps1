param(
    [ValidateSet('Play', 'Build', 'Bake', 'Check', 'Capture', 'Benchmark')]
    [string]$Action = 'Play',
    # World to play: the desert battlefield or the forest highlands (offroad/).
    [ValidateSet('Menu', 'Desert', 'Highlands', 'Generated', 'Endless')]
    [string]$Scene = 'Menu',
    [string]$GodotPath = '',
    [ValidateSet('Auto', 'Notebook', 'Reference', 'Low')]
    [string]$Quality = 'Auto',
    [ValidateRange(0.5, 1.0)]
    [double]$RenderScale = 1.0,
    [ValidateRange(10, 600)]
    [int]$Duration = 60
)

$ErrorActionPreference = 'Stop'
$battlefieldRoot = $PSScriptRoot
$battlefieldProject = $battlefieldRoot
$battlefieldBuild = Join-Path $battlefieldRoot 'build'
New-Item -ItemType Directory -Force -Path $battlefieldBuild | Out-Null
# Godot must not import captures and logs written next to the project.
New-Item -ItemType File -Force -Path (Join-Path $battlefieldBuild '.gdignore') | Out-Null
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
        (Join-Path $env:USERPROFILE 'Downloads\Godot_v4.7.2-stable_win64.exe'),
        (Join-Path $env:USERPROFILE 'Downloads'),
        (Join-Path $env:USERPROFILE 'Desktop')
    )
    foreach ($folder in $candidates) {
        # Prefer the console build; the windowed one works too (see Invoke-Godot).
        $binary = Get-ChildItem -LiteralPath $folder -Filter 'Godot*_console.exe' -File -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $binary) { $binary = Get-ChildItem -LiteralPath $folder -Filter 'Godot_v4*.exe' -File -ErrorAction SilentlyContinue | Select-Object -First 1 }
        if ($binary) { $GodotPath = $binary.FullName; break }
    }
}

# Runs Godot and waits for it: PowerShell's call operator does not wait for the
# windowed Godot executable, so start it as a process. Returns the exit code.
function Invoke-Godot([string[]]$Arguments) {
    $quoted = $Arguments | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }
    $process = Start-Process -FilePath $GodotPath -ArgumentList $quoted -NoNewWindow -Wait -PassThru
    return $process.ExitCode
}
if (-not $GodotPath -or -not (Test-Path -LiteralPath $GodotPath -PathType Leaf)) {
    throw 'Godot 4.7 was not found. Pass -GodotPath or set GODOT_BIN.'
}
$battlefieldArguments = @('--path', $battlefieldProject, '--rendering-method', 'forward_plus', '--audio-driver', 'Dummy')
$battlefieldLog = Join-Path $battlefieldBuild "battlefield-$($Action.ToLower()).log"
$battlefieldArguments += @('--log-file', $battlefieldLog)
$demoOptions = @("--quality=$($Quality.ToLower())")
# Only override the profile's scale if the caller explicitly supplied one.
if ($PSBoundParameters.ContainsKey('RenderScale')) {
    $demoOptions += '--render-scale=' + $RenderScale.ToString([System.Globalization.CultureInfo]::InvariantCulture)
}
$scenePath = if ($Scene -eq 'Menu') { 'res://offroad/endless_menu.tscn' } elseif ($Scene -eq 'Highlands') { 'res://offroad/highlands.tscn' } elseif ($Scene -eq 'Generated') { 'res://offroad/generated_expedition.tscn' } elseif ($Scene -eq 'Endless') { 'res://offroad/endless.tscn' } else { 'res://desert_battlefield/desert_battlefield.tscn' }
switch ($Action) {
    'Play' { $battlefieldArguments += @('--resolution', '1920x1080', $scenePath) }
    'Build' { $battlefieldArguments += @('--headless', '--script', 'res://desert_battlefield/build_scene.gd') }
    # Re-bakes the highlands maps, ground mesh and scene from offroad/highlands.gd.
    'Bake' { $battlefieldArguments += @('--headless', '--script', 'res://offroad/build_highlands.gd') }
    'Check' { $battlefieldArguments += @('--headless', '--fixed-fps', '60', '--script', 'res://desert_battlefield/verify_scene.gd'); $demoOptions += '--duration=120' }
    'Capture' { $battlefieldArguments += @('--resolution', '1920x1080', '--script', 'res://desert_battlefield/verify_scene.gd') }
    'Benchmark' { $battlefieldArguments += @('--disable-vsync', '--resolution', '1920x1080', '--script', 'res://desert_battlefield/verify_scene.gd'); $demoOptions += "--duration=$Duration" }
}
$battlefieldArguments += @('--') + $demoOptions
# Keep the demo's shader cache inside build/ instead of the shared Godot profile.
$battlefieldOriginalAppData = $env:APPDATA
try {
    $env:APPDATA = Join-Path $battlefieldBuild 'battlefield-profile'
    New-Item -ItemType Directory -Force -Path $env:APPDATA | Out-Null
    if (-not (Test-Path -LiteralPath (Join-Path $battlefieldProject '.godot/uid_cache.bin'))) {
        $importLog = Join-Path $battlefieldBuild 'battlefield-import.log'
        $code = Invoke-Godot @('--path', $battlefieldProject, '--headless', '--editor', '--import', '--rendering-method', 'forward_plus', '--log-file', $importLog)
        if ($code -ne 0 -or (Select-String -LiteralPath $importLog -Pattern '^SCRIPT ERROR:|^ERROR:' -Quiet)) {
            throw "First-run asset import failed. See $importLog"
        }
    }
    if ($Action -eq 'Check') {
        $handlingLog = Join-Path $battlefieldBuild 'battlefield-handling-checks.log'
        $code = Invoke-Godot (@('--path', $battlefieldProject, '--headless', '--rendering-method', 'forward_plus', '--script', 'res://desert_battlefield/handling_checks.gd', '--log-file', $handlingLog, '--') + $demoOptions)
        if ($code -ne 0 -or (Select-String -LiteralPath $handlingLog -Pattern '^SCRIPT ERROR:|^ERROR:' -Quiet)) {
            throw "Vehicle handling checks failed. See $handlingLog"
        }
        $rolloverLog = Join-Path $battlefieldBuild 'battlefield-rollover-checks.log'
        $code = Invoke-Godot (@('--path', $battlefieldProject, '--headless', '--rendering-method', 'forward_plus', '--script', 'res://desert_battlefield/rollover_checks.gd', '--log-file', $rolloverLog, '--') + $demoOptions)
        if ($code -ne 0 -or (Select-String -LiteralPath $rolloverLog -Pattern '^SCRIPT ERROR:|^ERROR:' -Quiet)) {
            throw "Vehicle rollover/energy checks failed. See $rolloverLog"
        }
        $soilLog = Join-Path $battlefieldBuild 'battlefield-soil-checks.log'
        $code = Invoke-Godot (@('--path', $battlefieldProject, '--headless', '--rendering-method', 'forward_plus', '--script', 'res://desert_battlefield/soil_checks.gd', '--log-file', $soilLog, '--') + $demoOptions)
        if ($code -ne 0 -or (Select-String -LiteralPath $soilLog -Pattern '^SCRIPT ERROR:|^ERROR:' -Quiet)) {
            throw "Sand deformation checks failed. See $soilLog"
        }
    }
    if ($Action -eq 'Bake' -and (Test-Path -LiteralPath (Join-Path $battlefieldProject 'assets/source/highlands'))) {
        # Props from the Sketchfab source packs (assets/source, not in git):
        # textures, an import so they become textures, then the meshes.
        foreach ($stage in @('textures', 'import', 'meshes')) {
            $propsLog = Join-Path $battlefieldBuild "battlefield-props-$stage.log"
            if ($stage -eq 'import') {
                $code = Invoke-Godot @('--path', $battlefieldProject, '--headless', '--editor', '--import', '--log-file', $propsLog)
            } else {
                $code = Invoke-Godot @('--path', $battlefieldProject, '--headless', '--script', 'res://offroad/build_props.gd', '--log-file', $propsLog, '--', "--stage=$stage")
            }
            if ($code -ne 0 -or (Select-String -LiteralPath $propsLog -Pattern '^SCRIPT ERROR:' -Quiet)) {
                throw "Props $stage failed. See $propsLog"
            }
        }
    }
    $code = Invoke-Godot $battlefieldArguments
    if ($code -ne 0) { throw "Battlefield $Action failed. See $battlefieldLog" }
    if (Select-String -LiteralPath $battlefieldLog -Pattern '^SCRIPT ERROR:|^ERROR:' -Quiet) {
        throw "Godot reported an error. See $battlefieldLog"
    }
} finally {
    $env:APPDATA = $battlefieldOriginalAppData
}
