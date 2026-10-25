# Builds preview.html (sedan from the real game PNGs next to the combi concept) and renders it to
# preview.png with headless Edge/Chrome. Run from anywhere:  powershell -File build_preview.ps1
$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$svg = Get-Content (Join-Path $here "combi_blueprint.svg") -Raw

# Strip the XML prolog and the leading comment; keep only the <svg> element.
$svgStart = $svg.IndexOf("<svg")
$svgBody = $svg.Substring($svgStart)

# Guides block is delimited by comment markers so it can be moved on top for the dimension view.
$gStart = $svgBody.IndexOf("<!-- ============ GUIDES")
$gEnd = $svgBody.IndexOf("<!-- ============ BEHIND")
$guides = $svgBody.Substring($gStart, $gEnd - $gStart)
$withoutGuides = $svgBody.Remove($gStart, $gEnd - $gStart)

function Copy-Svg([string]$cls, [string]$viewBox, [bool]$guidesOnTop) {
    $s = $withoutGuides
    if ($guidesOnTop) { $s = $s.Replace("</svg>", $guides + "</svg>") }
    $s = $s -replace 'viewBox="[^"]*" width="[^"]*" height="[^"]*"', ('viewBox="' + $viewBox + '" class="' + $cls + '"')
    return $s
}

$drawable = "../../app/src/main/res/drawable-nodpi"
# Real sedan assembly: BodyPartCatalog anchors, order = seats, shell, trunk, hood, lights, bumpers, doors, rack.
$sedanLayers = @(
    @("car_seat_rear", 430, 120), @("car_seat_front", 750, 85), @("car_base_body", 0, 0),
    @("car_trunk", -15, 120), @("car_hood", 1120, 153), @("car_taillight_std", 5, 201),
    @("car_headlight_std", 1295, 258), @("car_bumper_rear_std", -10, 300), @("car_bumper_front_fog", 1290, 315),
    @("car_door_rear", 340, 45), @("car_door_front", 715, 45), @("car_roof_rack", 442, -15)
)
$sedanHtml = ($sedanLayers | ForEach-Object {
    '<img src="{0}/{1}.png" style="left:{2}px;top:{3}px">' -f $drawable, $_[0], $_[1], $_[2]
}) -join "`n"
$wheelsHtml = @"
<img src="$drawable/wheel_std.png" class="wheel" style="left:159px;top:297px">
<img src="$drawable/wheel_std.png" class="wheel" style="left:1097px;top:297px">
"@

$html = @"
<!doctype html>
<html><head><meta charset="utf-8"><title>EndlessDrive - combi concept</title>
<style>
  body { background:#1B2230; color:#E2E8F0; font-family:"Segoe UI",Arial,sans-serif; margin:0; padding:24px 32px; width:1536px; }
  h1 { font-size:22px; margin:0 0 4px; color:#7DD3FC; }
  h2 { font-size:16px; margin:28px 0 8px; color:#F8FAFC; }
  p.note { font-size:12px; color:#94A3B8; margin:0 0 10px; }
  .frame { position:relative; width:1560px; height:620px; transform:scale(.62); transform-origin:0 0; margin-bottom:-236px; }
  .frame.raw { width:1472px; }
  .frame img { position:absolute; image-rendering:auto; }
  .frame img.wheel { width:244px; height:244px; }
  .frame .canvas { position:absolute; left:40px; top:40px; width:1472px; height:459px; }
  svg { display:block; overflow:visible; }
  svg.assembled #part-wheel-rear, svg.assembled #part-wheel-front { display:none; }
  .frame .canvas .wheelpng { position:absolute; width:244px; height:244px; }
  .frame.exploded { height:900px; margin-bottom:-330px; }
  svg.exploded #part-cargo       { transform:translate(-150px,-210px); }
  svg.exploded #part-seat-rear   { transform:translate(-40px,-420px); }
  svg.exploded #part-seat-front  { transform:translate(60px,-460px); }
  svg.exploded #part-tailgate    { transform:translate(-230px,-80px); }
  svg.exploded #part-hood        { transform:translate(0,-220px); }
  svg.exploded #part-taillight   { transform:translate(-230px,130px); }
  svg.exploded #part-headlight   { transform:translate(230px,20px); }
  svg.exploded #part-bumper-rear { transform:translate(-200px,240px); }
  svg.exploded #part-bumper-front{ transform:translate(200px,110px); }
  svg.exploded #part-door-rear   { transform:translate(-60px,470px); }
  svg.exploded #part-door-front  { transform:translate(110px,470px); }
  svg.exploded #part-roof-rack   { transform:translate(0,-60px); }
  svg.exploded #part-wheel-rear  { transform:translate(-560px,-20px); }
  svg.exploded #part-wheel-front { transform:translate(360px,-300px); }
  svg.exploded #interior-backdrop { display:none; }
  .legend { font-size:12px; color:#CBD5E1; columns:3; margin-top:8px; }
  .legend b { color:#F472B6; }
  .legend .reuse { color:#86EFAC; }
</style></head><body>
<h1>EndlessDrive – COMBI: modulárny 2.5D návrh v ráme sedanu (1472 × 459 px = 5.60 m)</h1>
<p class="note">Hore: skutočné PNG sedanu zložené podľa BodyPartCatalog. Pod ním: návrh combi v tom istom ráme, s tými istými kotvami a skutočnými kolesami (wheel_std.png, r = 122 px).</p>

<h2>1. SEDAN – aktuálny stav v hre (reálne sprite-y)</h2>
<div class="frame raw" style="height:600px;margin-bottom:-220px">
$sedanHtml
$wheelsHtml
</div>

<h2>2. COMBI – zložené auto (návrh)</h2>
<div class="frame">
$(Copy-Svg "assembled" "-40 -40 1560 620" $false)
<div class="canvas">
<img src="$drawable/wheel_std.png" class="wheelpng" style="left:159px;top:297px">
<img src="$drawable/wheel_std.png" class="wheelpng" style="left:1097px;top:297px">
</div>
</div>

<h2>3. COMBI – kotvy a rozmery (rovnaké ako sedan, nové len TAILGATE a voliteľné CARGO)</h2>
<div class="frame">
$(Copy-Svg "guides" "-40 -40 1560 620" $true)
</div>

<h2>4. COMBI – rozložené diely (každá skupina = jeden PNG sprite)</h2>
<div class="frame exploded">
$(Copy-Svg "exploded" "-420 -420 2340 1340" $false)
</div>
<div class="legend">
  <div><b>car_base_body_combi</b> 1472×459 – NOVÉ (škrupina)</div>
  <div><b>car_tailgate_combi</b> (8,14) 168×290 – NOVÉ, slot TRUNK_LID (nahrádza car_trunk)</div>
  <div><b>car_door_rear_combi</b> (340,45) 387×391 – NOVÉ (rovnaký rám ako sedan)</div>
  <div><b>car_taillight_combi</b> (92,200) 96×92 – NOVÉ, rovnaká veľkosť, nová kotva</div>
  <div><b>car_cargo_boxes</b> (100,110) 240×90 – VOLITEĹNÉ, za karosériou</div>
  <div class="reuse">car_door_front (715,45) – reuse</div>
  <div class="reuse">car_hood (1120,153) – reuse</div>
  <div class="reuse">car_headlight_std (1295,258) – reuse</div>
  <div class="reuse">car_bumper_front_fog (1290,315) – reuse</div>
  <div class="reuse">car_bumper_rear_std (-10,300) – reuse</div>
  <div class="reuse">car_seat_front / car_seat_rear – reuse</div>
  <div class="reuse">car_roof_rack (442,-15) – reuse</div>
  <div class="reuse">wheel_*.png – reuse (rovnaké osi 0.191 / 0.828, r 0.083)</div>
</div>
</body></html>
"@

$outHtml = Join-Path $here "preview.html"
Set-Content -Path $outHtml -Value $html -Encoding UTF8
Write-Host "wrote $outHtml"

$browsers = @(
    "$env:ProgramFiles (x86)\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe"
)
$browser = $browsers | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $browser) { Write-Warning "No Edge/Chrome found - open preview.html manually."; exit 0 }

$ErrorActionPreference = "Continue"
function Shoot([string]$htmlPath, [string]$pngPath, [string]$size) {
    $uri = "file:///" + ($htmlPath -replace '\\', '/')
    Start-Process -FilePath $browser -Wait -WindowStyle Hidden -ArgumentList @(
        "--headless=new", "--disable-gpu", "--hide-scrollbars", "--window-size=$size",
        "--screenshot=`"$pngPath`"", "`"$uri`""
    ) | Out-Null
    if (Test-Path $pngPath) { Write-Host "wrote $pngPath" } else { Write-Warning "screenshot failed: $pngPath" }
}
Shoot $outHtml (Join-Path $here "preview.png") "1600,2560"

# Full-resolution single views (1 canvas px = 1 screen px) for detail review.
$single = @"
<!doctype html><html><head><meta charset="utf-8"><style>
body{margin:0;background:#1B2230;} .frame{position:relative;width:1560px;height:620px;}
svg{display:block;overflow:visible;} svg.assembled #part-wheel-rear,svg.assembled #part-wheel-front{display:none;}
.canvas{position:absolute;left:40px;top:40px;width:1472px;height:459px;} .canvas img{position:absolute;width:244px;height:244px;}
</style></head><body><div class="frame">
$(Copy-Svg "assembled" "-40 -40 1560 620" $false)
<div class="canvas"><img src="$drawable/wheel_std.png" style="left:159px;top:297px"><img src="$drawable/wheel_std.png" style="left:1097px;top:297px"></div>
</div></body></html>
"@
$singleHtml = Join-Path $here "combi_assembled.html"
Set-Content -Path $singleHtml -Value $single -Encoding UTF8
Shoot $singleHtml (Join-Path $here "combi_assembled.png") "1560,620"

$explodedCss = ($html -split "`n" | Where-Object { $_ -like "*svg.exploded*" }) -join "`n"
$exploded = @"
<!doctype html><html><head><meta charset="utf-8"><style>
body{margin:0;background:#1B2230;} svg{display:block;overflow:visible;width:1600px;height:916px;}
$explodedCss
</style></head><body>
$(Copy-Svg "exploded" "-420 -420 2340 1340" $false)
</body></html>
"@
$explodedHtml = Join-Path $here "combi_exploded.html"
Set-Content -Path $explodedHtml -Value $exploded -Encoding UTF8
Shoot $explodedHtml (Join-Path $here "combi_exploded.png") "1600,916"
Remove-Item $singleHtml, $explodedHtml -ErrorAction SilentlyContinue
