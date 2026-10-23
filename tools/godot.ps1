param(
    [ValidateSet('Play', 'Editor', 'Import', 'Test', 'Balance', 'Capture', 'Android', 'BuildArt')]
    [string]$Action = 'Play',
    [string]$GodotPath = '',
    [switch]$Night,
    [switch]$Service,
    [switch]$Clean,
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
    & $GodotPath --path $projectPath --log-file $logPath @Arguments
    $engineCode = $LASTEXITCODE
    if ($engineCode -ne 0) { throw "Godot failed (exit $engineCode). See $logPath" }
    if ((Test-Path -LiteralPath $logPath) -and (Select-String -LiteralPath $logPath -Pattern '^SCRIPT ERROR:|^ERROR:' -Quiet)) {
        throw "Godot reported an error. See $logPath"
    }
}

# Import first so tests and the CLI can resolve GLB/OGG resources on a clean checkout.
Invoke-GodotChecked -Arguments @('--headless', '--editor', '--import') -LogName 'godot-import.log'

switch ($Action) {
    'BuildArt' {
        Invoke-GodotChecked -Arguments @('--headless', '--script', 'res://tests/build_art.gd') -LogName 'godot-build-art.log'
    }
    'Import' { Write-Host 'Godot project imported.' }
    'Play' { Invoke-GodotChecked -Arguments @() -LogName 'godot-play.log' }
    'Editor' { Invoke-GodotChecked -Arguments @('--editor') -LogName 'godot-editor.log' }
    'Test' {
        Invoke-GodotChecked -Arguments @('--headless', '--fixed-fps', '60', '--script', 'res://tests/test_runner.gd', '--', '--test-session') -LogName 'godot-tests.log'
        foreach ($suite in @('equipment_checks', 'menu_ui_checks', 'editor_scene_checks', 'jeep_rig_checks', 'terrain_rework_checks', 'soft_soil_checks', 'soft_soil_field_checks', 'stop_placement_checks', 'driving_regression', 'journey_balance', 'expedition_checks')) {
            Invoke-GodotChecked -Arguments @('--headless', '--fixed-fps', '60', '--script', "res://tests/$suite.gd", '--', '--test-session') -LogName "godot-$suite.log"
        }
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
        Invoke-GodotChecked -Arguments @('--headless', '--export-debug', 'Android Preview') -LogName 'godot-android.log'
        Write-Host (Join-Path $exportPath 'endless-drive-godot.apk')
    }
}
