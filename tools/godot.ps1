param(
    [ValidateSet('Play', 'SandLab', 'SandBench', 'Editor', 'Import', 'Test', 'Balance', 'Capture', 'Android', 'BuildArt', 'BuildExpeditionArt', 'BuildPeaks', 'BuildRocks', 'BuildPlants', 'BuildRoadArt', 'BuildGroundArt', 'BuildBoundaryRocks', 'TextureExpeditionArt', 'RefreshPreview')]
    [string]$Action = 'Play',
    [string]$GodotPath = '',
    [string[]]$Suites = @(),
    [ValidateSet('Android Preview', 'Android Device')][string]$ExportPreset = 'Android Preview',
    [ValidateRange(10, 1800)][int]$TimeoutSeconds = 300,
    [switch]$Night,
    [switch]$Service,
    [switch]$Clean,
    [switch]$CacheOnly,
    [switch]$Loot
)

$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
$projectPath = Join-Path $repoPath 'godot'
$buildPath = Join-Path $repoPath 'build'
New-Item -ItemType Directory -Force -Path $buildPath | Out-Null

if (-not $GodotPath -and $env:GODOT_BIN) { $GodotPath = $env:GODOT_BIN }
if (-not $GodotPath) {
    foreach ($commandName in @('godot', 'godot4')) {
        $foundCommand = Get-Command $commandName -ErrorAction SilentlyContinue
        if ($foundCommand) { $GodotPath = $foundCommand.Source; break }
    }
}
if (-not $GodotPath) {
    $localBinary = Get-ChildItem -LiteralPath (Join-Path $buildPath 'godot-tools') -Filter '*_console.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localBinary) { $GodotPath = $localBinary.FullName }
}
if (-not $GodotPath -or -not (Test-Path -LiteralPath $GodotPath -PathType Leaf)) {
    throw 'Godot was not found. Use -GodotPath C:\path\Godot_console.exe or set GODOT_BIN.'
}

function Invoke-GodotChecked {
    param([string[]]$Arguments, [string]$LogName)
    $logPath = Join-Path $buildPath $LogName
    # Launch the actual engine, not the console forwarding process: a timeout
    # must terminate the test we own, without leaving its engine running.
    $enginePath = $GodotPath
    if ($enginePath.EndsWith('_console.exe')) {
        $nativePath = $enginePath.Replace('_console.exe', '.exe')
        if (Test-Path -LiteralPath $nativePath) { $enginePath = $nativePath }
    }
    $quotedArgs = @('--path', $projectPath, '--log-file', $logPath) + $Arguments | ForEach-Object { '"' + $_.Replace('"', '\"') + '"' }
    $engineWindowStyle = if ('--headless' -in $Arguments -or '--script' -in $Arguments) { 'Hidden' } else { 'Normal' }
    $engineProcess = Start-Process -FilePath $enginePath -ArgumentList $quotedArgs -PassThru -WindowStyle $engineWindowStyle
    if ('--headless' -in $Arguments -or '--script' -in $Arguments) {
        if (-not $engineProcess.WaitForExit($TimeoutSeconds * 1000)) {
            $engineProcess.Kill($true)
            $engineProcess.WaitForExit()
            throw "Godot exceeded ${TimeoutSeconds}s. See $logPath"
        }
    } else { $engineProcess.WaitForExit() }
    $engineCode = $engineProcess.ExitCode
    if ($engineCode -ne 0) { throw "Godot failed (exit $engineCode). See $logPath" }
    if ((Test-Path -LiteralPath $logPath) -and (Select-String -LiteralPath $logPath -Pattern '^SCRIPT ERROR:|^ERROR:' -Quiet)) {
        throw "Godot reported an error. See $logPath"
    }
}

# Import first so tests and the CLI can resolve GLB/OGG resources on a clean checkout.
Invoke-GodotChecked -Arguments @('--headless', '--editor', '--import') -LogName 'godot-import.log'

switch ($Action) {
    'RefreshPreview' {
        if ($CacheOnly) {
            # Refresh geometry only; retain authored wrapper/camera/UI edits.
            foreach ($preview in @(@('editor_terrain', '1000003', '0'), @('bridge', '24601', '2664'), @('alpine', '1000005', '180'), @('forest', '1000004', '180'), @('canyon', '1000002', '180'), @('swamp', '1000006', '120'), @('mountain', '24601', '3200'), @('rail', '24601', '744'), @('tunnel', '24601', '1704'))) {
                Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/refresh_editor_preview.gd', '--', "--seed=$($preview[1])", "--distance=$($preview[2])", "--output=res://scenes/world/generated/$($preview[0]).scn") -LogName "godot-refresh-$($preview[0]).log"
            }
            Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/build_deformation_preview.gd') -LogName 'godot-refresh-deformation.log'
            Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/junction_gallery.gd') -LogName 'godot-refresh-junctions.log'
        } else {
            Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/refresh_editor_preview.gd', '--', '--showcases') -LogName 'godot-refresh-preview.log'
        }
    }
    'BuildArt' {
        Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/build_art.gd') -LogName 'godot-build-art.log'
    }
    'BuildPlants' {
        # Imported, user-supplied plant textures and normalized meshes.
        & python (Join-Path $PSScriptRoot 'prepare_supplied_plants.py')
        if ($LASTEXITCODE -ne 0) { throw 'Plant texture preparation failed.' }
        Invoke-GodotChecked -Arguments @('--headless', '--editor', '--import') -LogName 'godot-import-plants.log'
        Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/build_supplied_plants.gd') -LogName 'godot-build-plants.log'
    }
    'BuildExpeditionArt' {
        Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/build_expedition_art.gd') -LogName 'godot-build-expedition-art.log'
    }
    'BuildRoadArt' {
        & python (Join-Path $PSScriptRoot 'build_road_weathering.py')
        if ($LASTEXITCODE -ne 0) { throw 'Road weathering preparation failed.' }
        & python (Join-Path $PSScriptRoot 'build_weathered_asphalt.py')
        if ($LASTEXITCODE -ne 0) { throw 'Asphalt texture preparation failed.' }
        Invoke-GodotChecked -Arguments @('--headless', '--editor', '--import') -LogName 'godot-import-road-art.log'
        Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/build_road_obstacles.gd') -LogName 'godot-build-road-obstacles.log'
    }
    'BuildGroundArt' {
        & python (Join-Path $PSScriptRoot 'build_ground_surfaces.py')
        if ($LASTEXITCODE -ne 0) { throw 'Ground surface preparation failed.' }
        Invoke-GodotChecked -Arguments @('--headless', '--editor', '--import') -LogName 'godot-import-ground-art.log'
    }
    'BuildBoundaryRocks' {
        Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/build_boundary_rocks.gd') -LogName 'godot-build-boundary-rocks.log'
    }
    'BuildPeaks' {
        Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/build_expedition_art.gd', '--', '--only-peaks') -LogName 'godot-build-peaks.log'
    }
    'BuildRocks' {
        Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/build_expedition_art.gd', '--', '--only-rocks') -LogName 'godot-build-rocks.log'
    }
    'TextureExpeditionArt' {
        # Real renderer is required to serialize Texture2DArray pixel data.
        Invoke-GodotChecked -Arguments @('--audio-driver', 'Dummy', '--script', 'res://tests/build_expedition_textures.gd') -LogName 'godot-build-expedition-textures.log'
        Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/apply_expedition_textures.gd') -LogName 'godot-apply-expedition-textures.log'
    }
    'Import' { Write-Host 'Godot project imported.' }
    'Play' { Invoke-GodotChecked -Arguments @() -LogName 'godot-play.log' }
    'SandLab' { Invoke-GodotChecked -Arguments @('res://scenes/sand_lab.tscn') -LogName 'godot-sand-lab.log' }
    'SandBench' { Invoke-GodotChecked -Arguments @('--audio-driver', 'Dummy', '--fixed-fps', '60', '--script', 'res://tests/sand_lab_benchmark.gd', '--', '--test-session') -LogName 'godot-sand-benchmark.log' }
    'Editor' { Invoke-GodotChecked -Arguments @('--editor') -LogName 'godot-editor.log' }
    'Test' {
        $saveProbeAppData = $env:APPDATA
        $failedSuites = @()
        try {
            if (-not $Suites.Count) {
                $Suites = @('audit_regression_checks', 'expedition_art_checks', 'sand_lab_checks', 'test_runner', 'equipment_checks', 'menu_ui_checks', 'hud_c_checks', 'ui_localization_checks', 'editor_scene_checks', 'streaming_visual_checks', 'visual_polish_checks', 'route_commit_checks', 'jeep_rig_checks', 'terrain_rework_checks', 'terrain_reference_checks', 'displaced_soil_checks', 'surface_balance_checks', 'vehicle_stability_checks', 'drivetrain_checks', 'recovery_system_checks', 'winch_anchor_checks', 'expedition_world_checks', 'road_handling_checks', 'route_structure_checks', 'long_expedition_checks', 'soft_soil_checks', 'soft_soil_field_checks', 'stop_placement_checks', 'driving_regression', 'journey_balance', 'expedition_checks', 'frame_pacing_checks', 'menu_save_exit_checks')
            }
            if ('vegetation_checks' -notin $Suites -and -not $PSBoundParameters.ContainsKey('Suites')) { $Suites = @('vegetation_checks') + $Suites }
            if (-not $PSBoundParameters.ContainsKey('Suites')) { $Suites += 'preview_geometry_checks' }
            if ('road_obstacle_checks' -notin $Suites -and -not $PSBoundParameters.ContainsKey('Suites')) { $Suites = @('road_obstacle_checks') + $Suites }
            if ('forest_detail_checks' -notin $Suites -and -not $PSBoundParameters.ContainsKey('Suites')) { $Suites = @('forest_detail_checks') + $Suites }
            if ('vehicle_outline_checks' -notin $Suites -and -not $PSBoundParameters.ContainsKey('Suites')) { $Suites = @('vehicle_outline_checks') + $Suites }
            if (-not $PSBoundParameters.ContainsKey('Suites')) { $Suites += 'road_surface_checks' }
            if (-not $PSBoundParameters.ContainsKey('Suites')) { $Suites += 'ground_surface_checks' }
            if (-not $PSBoundParameters.ContainsKey('Suites')) { $Suites += 'track_edge_checks', 'vehicle_selection_checks', 'save_checks' }
            foreach ($suite in $Suites) {
                # Suite identifiers may include version digits (e.g. v3_world_checks).
                if ($suite -notmatch '^[a-z][a-z0-9_]*$') { throw "Invalid suite: $suite" }
                $env:APPDATA = Join-Path $buildPath $(if ($suite -eq 'menu_save_exit_checks') { 'menu-flow-user' } else { 'godot-tests-profile' })
                try {
                    Invoke-GodotChecked -Arguments @('--headless', '--fixed-fps', '60', '--script', "res://tests/$suite.gd", '--', '--test-session') -LogName "godot-$suite.log"
                    Write-Host "PASS $suite"
                } catch {
                    $failedSuites += $suite
                    Write-Warning "$suite : $_"
                }
            }
        } finally {
            $env:APPDATA = $saveProbeAppData
        }
        if ($failedSuites.Count) { throw "Failed suites ($($failedSuites.Count)): $($failedSuites -join ', ')" }
        Write-Host "All $($Suites.Count) suites passed."
    }
    'Balance' {
        Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/journey_balance.gd', '--', '--test-session') -LogName 'godot-journey_balance.log'
    }
    'Capture' {
        $captureArgs = @('--audio-driver', 'Dummy', '--fixed-fps', '60', '--script', 'res://tests/capture.gd')
        $captureLog = Join-Path $buildPath 'godot-capture.log'
        # Engine arguments must precede --; custom options follow it.
        $captureArgs += @('--path', $projectPath, '--log-file', $captureLog, '--', '--test-session')
        if ($Night) { $captureArgs += '--night' }
        if ($Service) { $captureArgs += '--service' }
        if ($Clean) { $captureArgs += '--clean' }
        if ($Loot) { $captureArgs += '--loot' }
        $previewName = if ($Loot) { 'godot-loot.png' } elseif ($Service) { 'godot-service.png' } elseif ($Night) { 'godot-night.png' } else { 'godot-preview.png' }
        $captureArgs += "--output=$(Join-Path $buildPath $previewName)"
        & $GodotPath @captureArgs
        if ($LASTEXITCODE -ne 0) { throw "Capture failed. See $captureLog" }
        Write-Host (Join-Path $buildPath $previewName)
    }
    'Android' {
        $exportPath = Join-Path $projectPath 'export'
        New-Item -ItemType Directory -Force -Path $exportPath | Out-Null
        Invoke-GodotChecked -Arguments @('--headless', '--export-debug', $ExportPreset) -LogName 'godot-android.log'
        Write-Host (Join-Path $exportPath $(if ($ExportPreset -eq 'Android Device') { 'endless-drive-arm64.apk' } else { 'endless-drive-godot.apk' }))
    }
}
