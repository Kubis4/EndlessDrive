# Generates standalone interactive 3D viewer for the modular car
$ErrorActionPreference = "Stop"

$workspaceRoot = Split-Path -Parent $PSScriptRoot
$modelsDir = Join-Path $workspaceRoot "models"
$glbPath = Join-Path $modelsDir "car_modular.glb"
$htmlPath = Join-Path $modelsDir "viewer.html"
$specPath = Join-Path $modelsDir "modular_car_spec.json"

Write-Host "Encoding car_modular.glb to Base64..."
$glbBytes = [System.IO.File]::ReadAllBytes($glbPath)
$glbBase64 = [System.Convert]::ToBase64String($glbBytes)
$specJson = [System.IO.File]::ReadAllText($specPath, [System.Text.Encoding]::UTF8)

$htmlHeader = @'
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>EndlessDrive - Modular 3D Car Inspector</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; }
    body {
      background: radial-gradient(circle at 50% 30%, #1e2638 0%, #0d111a 100%);
      color: #e2e8f0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      overflow: hidden;
      width: 100vw;
      height: 100vh;
    }
    #viewport { width: 100vw; height: 100vh; position: absolute; left: 0; top: 0; }

    /* Top Header */
    .top-bar {
      position: absolute;
      top: 16px;
      left: 20px;
      z-index: 10;
      background: rgba(15, 23, 42, 0.85);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 12px;
      padding: 12px 20px;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
    }
    .top-bar h1 {
      font-size: 18px;
      font-weight: 700;
      color: #38bdf8;
      letter-spacing: 0.5px;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .top-bar .subtitle {
      font-size: 12px;
      color: #94a3b8;
      margin-top: 4px;
    }

    /* Left Sidebar: Controls & Part List */
    .sidebar {
      position: absolute;
      top: 90px;
      left: 20px;
      bottom: 20px;
      width: 320px;
      z-index: 10;
      background: rgba(15, 23, 42, 0.85);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 12px;
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 14px;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
      overflow: hidden;
    }
    .section-title {
      font-size: 12px;
      text-transform: uppercase;
      font-weight: 700;
      color: #38bdf8;
      letter-spacing: 1px;
      margin-bottom: 6px;
    }
    .btn-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 6px;
    }
    .btn {
      background: #1e293b;
      border: 1px solid rgba(255, 255, 255, 0.12);
      color: #f1f5f9;
      padding: 7px 8px;
      border-radius: 6px;
      font-size: 11px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s ease;
      text-align: center;
    }
    .btn:hover {
      background: #38bdf8;
      color: #0f172a;
      border-color: #38bdf8;
      box-shadow: 0 0 12px rgba(56, 189, 248, 0.4);
    }
    .btn.active {
      background: #0284c7;
      color: #ffffff;
      border-color: #38bdf8;
    }

    /* Exploded View Slider */
    .slider-box {
      background: #0f172a;
      padding: 10px 12px;
      border-radius: 8px;
      border: 1px solid rgba(255, 255, 255, 0.08);
    }
    .slider-label {
      display: flex;
      justify-content: space-between;
      font-size: 11px;
      color: #cbd5e1;
      margin-bottom: 6px;
      font-weight: 600;
    }
    .slider-label span.val {
      color: #38bdf8;
      font-family: monospace;
    }
    input[type=range] {
      width: 100%;
      accent-color: #38bdf8;
      cursor: pointer;
    }

    /* Parts Scroll Area */
    .parts-container {
      flex: 1;
      overflow-y: auto;
      padding-right: 4px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .parts-container::-webkit-scrollbar {
      width: 4px;
    }
    .parts-container::-webkit-scrollbar-thumb {
      background: #334155;
      border-radius: 4px;
    }
    .part-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 6px 10px;
      background: rgba(30, 41, 59, 0.6);
      border-radius: 6px;
      font-size: 11px;
      border: 1px solid rgba(255, 255, 255, 0.05);
      transition: background 0.15s;
    }
    .part-item:hover {
      background: rgba(51, 65, 85, 0.8);
    }
    .part-item label {
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .part-item input[type="checkbox"] {
      accent-color: #38bdf8;
      cursor: pointer;
    }
    .part-item .slot-tag {
      font-size: 9px;
      padding: 2px 5px;
      border-radius: 4px;
      background: #0f172a;
      color: #94a3b8;
      font-family: monospace;
    }

    /* Floating Stats (Bottom Right) */
    .specs-box {
      position: absolute;
      bottom: 20px;
      right: 20px;
      background: rgba(15, 23, 42, 0.85);
      backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 12px;
      padding: 14px 18px;
      z-index: 10;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
      font-size: 11px;
      line-height: 1.6;
    }
    .specs-box .title {
      font-weight: 700;
      color: #38bdf8;
      margin-bottom: 6px;
      font-size: 12px;
    }
    .specs-box .stat-row {
      display: flex;
      justify-content: space-between;
      gap: 16px;
    }
    .specs-box .stat-label { color: #94a3b8; }
    .specs-box .stat-value { font-family: monospace; font-weight: 600; color: #f1f5f9; }

    /* Loading overlay */
    #loader {
      position: absolute;
      top: 0; left: 0; width: 100vw; height: 100vh;
      background: #0f172a;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      z-index: 100;
      transition: opacity 0.5s ease;
    }
    .spinner {
      width: 48px;
      height: 48px;
      border: 4px solid #1e293b;
      border-top-color: #38bdf8;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
      margin-bottom: 16px;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
  </style>
  <script type="importmap">
    {
      "imports": {
        "three": "https://unpkg.com/three@0.160.0/build/three.module.js",
        "three/addons/": "https://unpkg.com/three@0.160.0/examples/jsm/"
      }
    }
  </script>
</head>
<body>

  <div id="loader">
    <div class="spinner"></div>
    <div style="font-size: 14px; font-weight: 600; color: #38bdf8; letter-spacing: 1px;">LOADING 3D MODULAR CAR...</div>
  </div>

  <div id="viewport"></div>

  <div class="top-bar">
    <h1>🚗 EndlessDrive &mdash; Modular Car 3D</h1>
    <div class="subtitle">Complete 26-piece assembly • Exact dimensions from SedanSpec.kt</div>
  </div>

  <div class="sidebar">
    <div>
      <div class="section-title">Camera Views</div>
      <div class="btn-grid">
        <button class="btn" id="btn_iso">Isometric</button>
        <button class="btn" id="btn_front">Front</button>
        <button class="btn" id="btn_side">Side</button>
        <button class="btn" id="btn_rear">Rear</button>
        <button class="btn" id="btn_top">Top</button>
        <button class="btn" id="btn_engine">Engine</button>
      </div>
    </div>

    <div class="slider-box">
      <div class="slider-label">
        <span>Exploded Assembly</span>
        <span class="val" id="explodeVal">0%</span>
      </div>
      <input type="range" id="explodeSlider" min="0" max="100" value="0">
    </div>

    <div>
      <div class="section-title" style="display:flex; justify-content:space-between; align-items:center;">
        <span>Components (26 Parts)</span>
        <div style="display:flex; gap:6px;">
          <a href="#" id="link_all" style="color:#38bdf8; text-decoration:none; font-size:10px;">All</a>
          <span style="color:#475569;">|</span>
          <a href="#" id="link_none" style="color:#94a3b8; text-decoration:none; font-size:10px;">None</a>
        </div>
      </div>
    </div>

    <div class="parts-container" id="partsList">
      <!-- Injected via JavaScript -->
    </div>

    <div style="display:flex; gap:6px;">
      <button class="btn" style="flex:1" id="btn_rotate">Auto Rotate</button>
      <button class="btn" style="flex:1" id="btn_reset">Reset</button>
    </div>
  </div>

  <div class="specs-box">
    <div class="title">Physical Spec (Game Match)</div>
    <div class="stat-row"><span class="stat-label">Total Length:</span><span class="stat-value">5.60 m</span></div>
    <div class="stat-row"><span class="stat-label">Wheelbase:</span><span class="stat-value">3.528 m</span></div>
    <div class="stat-row"><span class="stat-label">Body Width:</span><span class="stat-value">1.84 m</span></div>
    <div class="stat-row"><span class="stat-label">Wheel Radius:</span><span class="stat-value">0.48 m</span></div>
    <div class="stat-row"><span class="stat-label">Track Width:</span><span class="stat-value">1.56 m</span></div>
    <div class="stat-row"><span class="stat-label">Ride Height:</span><span class="stat-value">0.42 m</span></div>
    <div class="stat-row"><span class="stat-label">Assembly:</span><span class="stat-value">26 Swappable Nodes</span></div>
  </div>

  <script type="module">
    import * as THREE from 'three';
    import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
    import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

    let scene, camera, renderer, controls;
    let carGroup;
    const partNodes = new Map();
    const originalPositions = new Map();
    let autoRotate = false;

    // Spec metadata
    const specMeta =
'@

$htmlFooter = @'
;

    // Embedded GLB base64
    const GLB_BASE64 = "
'@

$htmlTail = @'
";

    init();

    function init() {
      const container = document.getElementById('viewport');
      scene = new THREE.Scene();
      scene.background = new THREE.Color(0x0c1017);
      scene.fog = new THREE.FogExp2(0x0c1017, 0.04);

      camera = new THREE.PerspectiveCamera(40, window.innerWidth / window.innerHeight, 0.1, 100);
      camera.position.set(5.5, 3.2, 4.8);

      renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
      renderer.setSize(window.innerWidth, window.innerHeight);
      renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
      renderer.shadowMap.enabled = true;
      renderer.shadowMap.type = THREE.PCFSoftShadowMap;
      renderer.toneMapping = THREE.ACESFilmicToneMapping;
      renderer.toneMappingExposure = 1.15;
      container.appendChild(renderer.domElement);

      controls = new OrbitControls(camera, renderer.domElement);
      controls.enableDamping = true;
      controls.dampingFactor = 0.06;
      controls.maxPolarAngle = Math.PI / 2 + 0.05; // don't go below floor
      controls.minDistance = 1.5;
      controls.maxDistance = 25;
      controls.target.set(0, 0.5, 0);

      setupLighting();
      setupFloor();
      loadCarModel();

      window.addEventListener('resize', onWindowResize);
      animate();
    }

    function setupLighting() {
      const ambientLight = new THREE.AmbientLight(0xffffff, 0.65);
      scene.add(ambientLight);

      // Key light with shadow
      const keyLight = new THREE.DirectionalLight(0xfff5e6, 2.2);
      keyLight.position.set(6, 8, 5);
      keyLight.castShadow = true;
      keyLight.shadow.mapSize.width = 2048;
      keyLight.shadow.mapSize.height = 2048;
      keyLight.shadow.camera.near = 0.5;
      keyLight.shadow.camera.far = 25;
      keyLight.shadow.camera.left = -5;
      keyLight.shadow.camera.right = 5;
      keyLight.shadow.camera.top = 5;
      keyLight.shadow.camera.bottom = -5;
      keyLight.shadow.bias = -0.0004;
      scene.add(keyLight);

      // Fill light
      const fillLight = new THREE.DirectionalLight(0x7dd3fc, 1.2);
      fillLight.position.set(-6, 4, -4);
      scene.add(fillLight);

      // Rim light for car edge definition
      const rimLight = new THREE.DirectionalLight(0x38bdf8, 1.5);
      rimLight.position.set(0, 5, -8);
      scene.add(rimLight);

      // Warm ground bounce
      const bounceLight = new THREE.DirectionalLight(0x334155, 0.5);
      bounceLight.position.set(0, -3, 0);
      scene.add(bounceLight);
    }

    function setupFloor() {
      // Mirror / Shadow catcher floor
      const floorGeo = new THREE.PlaneGeometry(60, 60);
      const floorMat = new THREE.MeshStandardMaterial({
        color: 0x111622,
        roughness: 0.35,
        metalness: 0.2,
      });
      const floor = new THREE.Mesh(floorGeo, floorMat);
      floor.rotation.x = -Math.PI / 2;
      floor.position.y = -0.48; // road surface level matching wheel bottom (-0.48m)
      floor.receiveShadow = true;
      scene.add(floor);

      // Subtle grid helper
      const grid = new THREE.GridHelper(40, 40, 0x38bdf8, 0x1e293b);
      grid.position.y = -0.479;
      scene.add(grid);
    }

    function loadCarModel() {
      const loader = new GLTFLoader();
      const binaryString = atob(GLB_BASE64);
      const len = binaryString.length;
      const bytes = new Uint8Array(len);
      for (let i = 0; i < len; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }

      loader.parse(bytes.buffer, '', (gltf) => {
        carGroup = gltf.scene;
        scene.add(carGroup);

        // Enhance materials & collect parts
        carGroup.traverse((child) => {
          if (child.isMesh) {
            child.castShadow = true;
            child.receiveShadow = true;
            if (child.material) {
              child.material.side = THREE.DoubleSide;
            }
          }

          // In glTF, named modular nodes can be top-level or meshes
          if (child.name && child.name !== 'Scene' && child.name !== 'CarAssembly' && child.parent === carGroup) {
            partNodes.set(child.name, child);
            originalPositions.set(child.name, child.position.clone());
          }
        });

        buildPartsUI();

        // Hide loader immediately
        const loaderElem = document.getElementById('loader');
        if (loaderElem) {
          loaderElem.style.transition = 'opacity 0.4s ease';
          loaderElem.style.opacity = '0';
          setTimeout(() => {
            loaderElem.style.display = 'none';
          }, 400);
        }

      }, (err) => {
        console.error("Error parsing GLB", err);
        const loaderElem = document.getElementById('loader');
        if (loaderElem) {
          loaderElem.innerHTML = `<div style="color:#ef4444;font-weight:bold;padding:20px;text-align:center;">
            ERROR LOADING MODEL:<br>${err.message || err}
          </div>`;
        }
      });
    }

    function buildPartsUI() {
      const container = document.getElementById('partsList');
      container.innerHTML = '';

      const sortedNames = Array.from(partNodes.keys()).sort();
      sortedNames.forEach((name) => {
        const node = partNodes.get(name);
        const specItem = (specMeta.parts || []).find(p => p.name.toLowerCase() === name.toLowerCase()) || {};
        const slot = specItem.slot || "PART";

        const div = document.createElement('div');
        div.className = 'part-item';

        const label = document.createElement('label');
        const chk = document.createElement('input');
        chk.type = 'checkbox';
        chk.checked = true;
        chk.id = `chk_${name}`;
        chk.onchange = () => {
          node.visible = chk.checked;
        };

        const spanName = document.createElement('span');
        spanName.textContent = name.replace(/_/g, ' ');

        label.appendChild(chk);
        label.appendChild(spanName);

        const badge = document.createElement('span');
        badge.className = 'slot-tag';
        badge.textContent = slot;

        div.appendChild(label);
        div.appendChild(badge);
        container.appendChild(div);
      });
    }

    window.toggleAll = function(visible) {
      partNodes.forEach((node, name) => {
        node.visible = visible;
        const chk = document.getElementById(`chk_${name}`);
        if (chk) chk.checked = visible;
      });
    };

    window.onExplodeChange = function(val) {
      const frac = parseFloat(val) / 100.0;
      document.getElementById('explodeVal').textContent = `${Math.round(val)}%`;

      partNodes.forEach((node, name) => {
        const orig = originalPositions.get(name) || new THREE.Vector3();
        const dir = getPartExplodeVector(name);
        node.position.x = orig.x + dir.x * frac;
        node.position.y = orig.y + dir.y * frac;
        node.position.z = orig.z + dir.z * frac;
      });
    };

    function getPartExplodeVector(name) {
      const n = name.toLowerCase();
      // Calculate outward explosion vectors
      if (n.includes('hood')) return new THREE.Vector3(0.5, 0.8, 0);
      if (n.includes('front_bumper')) return new THREE.Vector3(1.0, -0.1, 0);
      if (n.includes('rear_bumper')) return new THREE.Vector3(-1.0, -0.1, 0);
      if (n.includes('trunk')) return new THREE.Vector3(-0.8, 0.6, 0);
      if (n.includes('door_front_l')) return new THREE.Vector3(0.2, 0, 0.9);
      if (n.includes('door_front_r')) return new THREE.Vector3(0.2, 0, -0.9);
      if (n.includes('door_rear_l')) return new THREE.Vector3(-0.2, 0, 0.9);
      if (n.includes('door_rear_r')) return new THREE.Vector3(-0.2, 0, -0.9);
      if (n.includes('roof_rack')) return new THREE.Vector3(0, 1.2, 0);
      if (n.includes('engine')) return new THREE.Vector3(0, 0.9, 0);
      if (n.includes('radiator')) return new THREE.Vector3(0.8, 0.3, 0);
      if (n.includes('battery')) return new THREE.Vector3(0.4, 0.8, 0.5);
      if (n.includes('wheel_fl')) return new THREE.Vector3(0.3, 0, 0.8);
      if (n.includes('wheel_fr')) return new THREE.Vector3(0.3, 0, -0.8);
      if (n.includes('wheel_rl')) return new THREE.Vector3(-0.3, 0, 0.8);
      if (n.includes('wheel_rr')) return new THREE.Vector3(-0.3, 0, -0.8);
      if (n.includes('headlight_l')) return new THREE.Vector3(0.8, 0.3, 0.4);
      if (n.includes('headlight_r')) return new THREE.Vector3(0.8, 0.3, -0.4);
      if (n.includes('taillight_l')) return new THREE.Vector3(-0.8, 0.3, 0.4);
      if (n.includes('taillight_r')) return new THREE.Vector3(-0.8, 0.3, -0.4);
      if (n.includes('seat_front_l')) return new THREE.Vector3(0, 0.8, 0.5);
      if (n.includes('seat_front_r')) return new THREE.Vector3(0, 0.8, -0.5);
      if (n.includes('seat_rear')) return new THREE.Vector3(0, 0.8, 0);
      if (n.includes('fuel_tank')) return new THREE.Vector3(0, -0.6, 0);
      if (n.includes('suspension')) return new THREE.Vector3(0, -0.6, 0);
      return new THREE.Vector3(0, 0, 0);
    }

    window.setCamera = function(view) {
      const duration = 800;
      let targetPos = new THREE.Vector3();
      let lookTarget = new THREE.Vector3(0, 0.5, 0);

      switch (view) {
        case 'iso': targetPos.set(5.5, 3.2, 4.8); break;
        case 'front': targetPos.set(6.5, 0.8, 0); break;
        case 'side': targetPos.set(0, 0.7, 6.5); break;
        case 'rear': targetPos.set(-6.5, 0.8, 0); break;
        case 'top': targetPos.set(0.01, 8.5, 0.01); break;
        case 'engine': targetPos.set(2.8, 2.0, 1.2); lookTarget.set(1.5, 0.3, 0); break;
      }

      animateCamera(targetPos, lookTarget, duration);
    };

    function animateCamera(newPos, newTarget, duration) {
      const startPos = camera.position.clone();
      const startTarget = controls.target.clone();
      const startTime = performance.now();

      function update(time) {
        const elapsed = time - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const ease = 0.5 - Math.cos(progress * Math.PI) / 2;

        camera.position.lerpVectors(startPos, newPos, ease);
        controls.target.lerpVectors(startTarget, newTarget, ease);
        controls.update();

        if (progress < 1) {
          requestAnimationFrame(update);
        }
      }
      requestAnimationFrame(update);
    }

    window.toggleAutoRotate = function() {
      autoRotate = !autoRotate;
      controls.autoRotate = autoRotate;
      controls.autoRotateSpeed = 2.0;
    };

    window.resetView = function() {
      document.getElementById('explodeSlider').value = 0;
      onExplodeChange(0);
      toggleAll(true);
      setCamera('iso');
    };

    // Attach DOM Event Listeners
    document.getElementById('btn_iso')?.addEventListener('click', () => setCamera('iso'));
    document.getElementById('btn_front')?.addEventListener('click', () => setCamera('front'));
    document.getElementById('btn_side')?.addEventListener('click', () => setCamera('side'));
    document.getElementById('btn_rear')?.addEventListener('click', () => setCamera('rear'));
    document.getElementById('btn_top')?.addEventListener('click', () => setCamera('top'));
    document.getElementById('btn_engine')?.addEventListener('click', () => setCamera('engine'));
    document.getElementById('btn_rotate')?.addEventListener('click', toggleAutoRotate);
    document.getElementById('btn_reset')?.addEventListener('click', resetView);
    document.getElementById('link_all')?.addEventListener('click', (e) => { e.preventDefault(); toggleAll(true); });
    document.getElementById('link_none')?.addEventListener('click', (e) => { e.preventDefault(); toggleAll(false); });
    document.getElementById('explodeSlider')?.addEventListener('input', (e) => onExplodeChange(e.target.value));

    function onWindowResize() {
      camera.aspect = window.innerWidth / window.innerHeight;
      camera.updateProjectionMatrix();
      renderer.setSize(window.innerWidth, window.innerHeight);
    }

    function animate() {
      requestAnimationFrame(animate);
      controls.update();
      renderer.render(scene, camera);
    }
  </script>
</body>
</html>
'@

$writer = [System.IO.StreamWriter]::new($htmlPath, $false, [System.Text.Encoding]::UTF8)
$writer.Write($htmlHeader)
$writer.Write($specJson)
$writer.Write($htmlFooter)
$writer.Write($glbBase64)
$writer.Write($htmlTail)
$writer.Close()

Write-Host "viewer.html generated successfully at: $htmlPath" -ForegroundColor Green
