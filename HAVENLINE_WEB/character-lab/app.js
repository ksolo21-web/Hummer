import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';

const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
const formatInt = (n) => new Intl.NumberFormat().format(Math.round(n || 0));
const safeName = (name) => (name || 'character').replace(/[^a-z0-9._-]+/gi, '_');

const ui = {
  stage: $('#stage'), stageEmpty: $('#stageEmpty'), modelInput: $('#modelInput'), referenceInput: $('#referenceInput'),
  dropZone: $('#dropZone'), modelName: $('#modelName'), referenceName: $('#referenceName'), referenceImage: $('#referenceImage'),
  referenceEmpty: $('#referenceEmpty'), referenceOverlay: $('#referenceOverlay'), overlayToggle: $('#overlayToggle'),
  overlayOpacity: $('#overlayOpacity'), overlayValue: $('#overlayValue'), skeletonToggle: $('#skeletonToggle'),
  wireframeToggle: $('#wireframeToggle'), gridToggle: $('#gridToggle'), exposureRange: $('#exposureRange'), exposureValue: $('#exposureValue'),
  zoomRange: $('#zoomRange'), zoomValue: $('#zoomValue'), autoRotate: $('#autoRotate'), viewLabel: $('#viewLabel'),
  fpsLabel: $('#fpsLabel'), renderLabel: $('#renderLabel'), statsGrid: $('#statsGrid'), findings: $('#findings'),
  materialAudit: $('#materialAudit'), qaStatus: $('#qaStatus'), rigStatus: $('#rigStatus'), scaleStatus: $('#scaleStatus'),
  animationStatus: $('#animationStatus'), animationSelect: $('#animationSelect'), playBtn: $('#playBtn'), pauseBtn: $('#pauseBtn'),
  resetAnimBtn: $('#resetAnimBtn'), speedRange: $('#speedRange'), speedValue: $('#speedValue'), proofBtn: $('#proofBtn'),
  reportBtn: $('#reportBtn'), fitReferenceBtn: $('#fitReferenceBtn')
};

const slots = new Map(['C1','C2','C3','C4'].map((id) => [id, { id, modelFile: null, referenceFile: null, gltf: null, root: null, report: null, referenceUrl: null }]));
let activeSlotId = 'C1';
let activeView = 'front';
let activeRoot = null;
let activeGltf = null;
let skeletonHelper = null;
let mixer = null;
let currentAction = null;
let modelBounds = null;
let baseFraming = 1;
let fpsEMA = 60;
let lastFrame = performance.now();

const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, preserveDrawingBuffer: true, powerPreference: 'high-performance' });
renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.toneMapping = THREE.ACESFilmicToneMapping;
renderer.toneMappingExposure = 1.10;
renderer.shadowMap.enabled = true;
renderer.shadowMap.type = THREE.PCFSoftShadowMap;
ui.stage.prepend(renderer.domElement);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x121820);

const pmrem = new THREE.PMREMGenerator(renderer);
scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
pmrem.dispose();

const camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.01, 500);
camera.position.set(0, 1.5, 4);
const controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.dampingFactor = 0.08;
controls.screenSpacePanning = true;
controls.target.set(0, 1, 0);

const world = new THREE.Group();
scene.add(world);

const grid = new THREE.GridHelper(8, 16, 0x556474, 0x2d3946);
grid.material.opacity = 0.45;
grid.material.transparent = true;
scene.add(grid);

const key = new THREE.DirectionalLight(0xfff2df, 3.2);
key.position.set(3.5, 6, 5);
key.castShadow = true;
scene.add(key);
const fill = new THREE.DirectionalLight(0xb8d8ff, 1.4);
fill.position.set(-4, 3, 2);
scene.add(fill);
const rim = new THREE.DirectionalLight(0xffb37e, 1.0);
rim.position.set(2, 3, -5);
scene.add(rim);
const hemi = new THREE.HemisphereLight(0xe7f1ff, 0x202631, 1.2);
scene.add(hemi);

const loader = new GLTFLoader();
const clock = new THREE.Clock();

function currentSlot() { return slots.get(activeSlotId); }

function setStatus(text, kind = 'neutral') {
  ui.qaStatus.textContent = text;
  ui.qaStatus.className = `status-${kind}`;
}

function resize() {
  const rect = ui.stage.getBoundingClientRect();
  const width = Math.max(1, Math.floor(rect.width));
  const height = Math.max(1, Math.floor(rect.height));
  renderer.setSize(width, height, false);
  if (modelBounds) fitCamera(activeView, false);
}
window.addEventListener('resize', resize);
new ResizeObserver(resize).observe(ui.stage);

function clearSceneRoot() {
  if (activeRoot) world.remove(activeRoot);
  if (skeletonHelper) {
    scene.remove(skeletonHelper);
    skeletonHelper = null;
  }
  mixer = null;
  currentAction = null;
  activeRoot = null;
  activeGltf = null;
  modelBounds = null;
}

function normalizeRoot(root) {
  root.updateMatrixWorld(true);
  const box = new THREE.Box3().setFromObject(root);
  const center = box.getCenter(new THREE.Vector3());
  root.position.x -= center.x;
  root.position.z -= center.z;
  root.position.y -= box.min.y;
  root.updateMatrixWorld(true);
  return new THREE.Box3().setFromObject(root);
}

function computeBounds() {
  if (!activeRoot) return null;
  activeRoot.updateMatrixWorld(true);
  modelBounds = new THREE.Box3().setFromObject(activeRoot);
  return modelBounds;
}

function fitCamera(view = activeView, updateButtons = true) {
  if (!activeRoot) return;
  activeView = view;
  const box = computeBounds();
  const size = box.getSize(new THREE.Vector3());
  const center = box.getCenter(new THREE.Vector3());
  const maxDim = Math.max(size.x, size.y, size.z, 0.1);
  const rect = ui.stage.getBoundingClientRect();
  const aspect = Math.max(0.25, rect.width / Math.max(1, rect.height));
  const framingMultiplier = Number(ui.zoomRange.value) / 100;
  baseFraming = maxDim * 0.62 * framingMultiplier;
  camera.left = -baseFraming * aspect;
  camera.right = baseFraming * aspect;
  camera.top = baseFraming;
  camera.bottom = -baseFraming;
  camera.near = 0.01;
  camera.far = maxDim * 30 + 20;

  const d = maxDim * 3.1 + 1;
  const target = new THREE.Vector3(center.x, center.y + size.y * 0.015, center.z);
  const positions = {
    front: new THREE.Vector3(target.x, target.y, target.z + d),
    'three-quarter': new THREE.Vector3(target.x + d * 0.72, target.y + d * 0.08, target.z + d * 0.72),
    side: new THREE.Vector3(target.x + d, target.y, target.z),
    back: new THREE.Vector3(target.x, target.y, target.z - d)
  };
  camera.position.copy(positions[view] || positions.front);
  camera.up.set(0, 1, 0);
  camera.lookAt(target);
  camera.updateProjectionMatrix();
  controls.target.copy(target);
  controls.update();
  ui.viewLabel.textContent = view.replace('-', ' ').toUpperCase();
  if (updateButtons) $$('.camera-grid button').forEach((b) => b.classList.toggle('active', b.dataset.view === view));
}

function getTexturesFromMaterial(material, set) {
  if (!material) return;
  for (const value of Object.values(material)) if (value?.isTexture) set.add(value);
}

function auditModel(root, gltf) {
  const materials = new Set();
  const textures = new Set();
  const bones = new Set();
  let meshes = 0, skinnedMeshes = 0, vertices = 0, triangles = 0;
  let minWeightSum = Infinity, maxWeightSum = -Infinity, weightedSamples = 0;

  root.traverse((obj) => {
    if (obj.isBone) bones.add(obj);
    if (!obj.isMesh) return;
    meshes += 1;
    if (obj.isSkinnedMesh) {
      skinnedMeshes += 1;
      obj.skeleton?.bones?.forEach((bone) => bones.add(bone));
    }
    const geometry = obj.geometry;
    const position = geometry?.getAttribute('position');
    if (position) vertices += position.count;
    triangles += geometry?.index ? geometry.index.count / 3 : (position ? position.count / 3 : 0);
    const skinWeight = geometry?.getAttribute('skinWeight');
    if (skinWeight) {
      const stride = skinWeight.itemSize;
      const step = Math.max(1, Math.floor(skinWeight.count / 2000));
      for (let i = 0; i < skinWeight.count; i += step) {
        let sum = 0;
        for (let c = 0; c < stride; c++) sum += skinWeight.getComponent(i, c);
        minWeightSum = Math.min(minWeightSum, sum);
        maxWeightSum = Math.max(maxWeightSum, sum);
        weightedSamples += 1;
      }
    }
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    mats.filter(Boolean).forEach((mat) => { materials.add(mat); getTexturesFromMaterial(mat, textures); });
  });

  const box = new THREE.Box3().setFromObject(root);
  const size = box.getSize(new THREE.Vector3());
  const clips = gltf.animations?.length || 0;
  const report = {
    characterSlot: activeSlotId,
    modelName: currentSlot().modelFile?.name || 'unknown',
    generatedAt: new Date().toISOString(),
    renderer: 'Three.js r185 / WebGL',
    counts: { vertices: Math.round(vertices), triangles: Math.round(triangles), meshes, skinnedMeshes, bones: bones.size, materials: materials.size, textures: textures.size, animationClips: clips },
    boundsMeters: { width: size.x, height: size.y, depth: size.z },
    skinWeights: weightedSamples ? { sampledVertices: weightedSamples, minSum: minWeightSum, maxSum: maxWeightSum } : null,
    materials: [...materials].map((m) => ({ name: m.name || m.type, type: m.type, metalness: Number.isFinite(m.metalness) ? m.metalness : null, roughness: Number.isFinite(m.roughness) ? m.roughness : null, transparent: !!m.transparent, hasBaseColorTexture: !!m.map, hasNormalTexture: !!m.normalMap, hasORM: !!(m.roughnessMap || m.metalnessMap) }))
  };
  report.findings = buildFindings(report);
  return report;
}

function buildFindings(report) {
  const f = [];
  const c = report.counts;
  if (c.skinnedMeshes > 0 && c.bones >= 45 && c.bones <= 80) f.push({ severity: 'good', text: `Humanoid rig detected: ${c.bones} bones across ${c.skinnedMeshes} skinned mesh${c.skinnedMeshes === 1 ? '' : 'es'}.` });
  else if (c.skinnedMeshes === 0) f.push({ severity: 'bad', text: 'No skinned mesh detected. This is not ready for humanoid animation review.' });
  else f.push({ severity: 'warn', text: `Rig has ${c.bones} bones. Verify humanoid mapping before production.` });

  if (report.skinWeights) {
    const ok = Math.abs(report.skinWeights.minSum - 1) < 0.02 && Math.abs(report.skinWeights.maxSum - 1) < 0.02;
    f.push({ severity: ok ? 'good' : 'bad', text: ok ? 'Sampled skin weights are normalized near 1.0.' : `Skin-weight normalization is suspect (${report.skinWeights.minSum.toFixed(3)}–${report.skinWeights.maxSum.toFixed(3)}).` });
  } else f.push({ severity: 'warn', text: 'No skin-weight attribute found for automated normalization sampling.' });

  if (c.triangles <= 45000) f.push({ severity: 'good', text: `${formatInt(c.triangles)} triangles is a reasonable hero-character LOD0 starting point for the Havenline Android target.` });
  else if (c.triangles <= 70000) f.push({ severity: 'warn', text: `${formatInt(c.triangles)} triangles is usable for review but should receive LODs before final Android production.` });
  else f.push({ severity: 'warn', text: `${formatInt(c.triangles)} triangles is heavy for a frequently visible mobile character; generate production LODs.` });

  const metallic = report.materials.filter((m) => m.metalness !== null && m.metalness > 0.35);
  if (metallic.length) f.push({ severity: 'warn', text: `${metallic.length} material${metallic.length === 1 ? '' : 's'} use high metalness. Check that skin, cloth, fur, and leather are not accidentally metallic.` });
  else f.push({ severity: 'good', text: 'No obviously over-metallic PBR materials detected.' });

  const h = report.boundsMeters.height;
  if (h > 0.5 && h < 3.0) f.push({ severity: 'good', text: `Model height is ${h.toFixed(2)} scene units after import; verify Unity's meter scale during staging.` });
  else f.push({ severity: 'warn', text: `Model height is ${h.toFixed(2)} scene units. Check import scale before Unity integration.` });

  if (c.animationClips > 0) f.push({ severity: 'good', text: `${c.animationClips} embedded animation clip${c.animationClips === 1 ? '' : 's'} available for deformation review.` });
  else f.push({ severity: 'warn', text: 'No embedded animation clips. Rig can still be reviewed; Havenline gameplay clips must be applied later.' });
  return f;
}

function renderReport(report) {
  const values = [report.counts.vertices, report.counts.triangles, report.counts.meshes, report.counts.skinnedMeshes, report.counts.bones, report.counts.materials, report.counts.textures, report.counts.animationClips];
  [...ui.statsGrid.querySelectorAll('dd')].forEach((dd, i) => { dd.textContent = formatInt(values[i]); });
  ui.findings.innerHTML = '';
  report.findings.forEach((finding) => {
    const li = document.createElement('li');
    li.className = finding.severity;
    li.textContent = finding.text;
    ui.findings.appendChild(li);
  });
  ui.materialAudit.innerHTML = '';
  ui.materialAudit.classList.remove('muted');
  if (!report.materials.length) ui.materialAudit.textContent = 'No materials found.';
  report.materials.forEach((mat, i) => {
    const card = document.createElement('div');
    card.className = 'material-card';
    const metal = mat.metalness === null ? 'n/a' : mat.metalness.toFixed(2);
    const rough = mat.roughness === null ? 'n/a' : mat.roughness.toFixed(2);
    card.innerHTML = `<strong>${escapeHtml(mat.name || `Material ${i + 1}`)}</strong><span>${escapeHtml(mat.type)} · metal ${metal} · rough ${rough} · base ${mat.hasBaseColorTexture ? 'tex' : 'solid'}</span>`;
    ui.materialAudit.appendChild(card);
  });

  const severe = report.findings.some((x) => x.severity === 'bad');
  const warning = report.findings.some((x) => x.severity === 'warn');
  setStatus(severe ? 'Blocked by model QA' : warning ? 'Review candidate' : 'Automated QA clean', severe ? 'bad' : warning ? 'warn' : 'good');
  ui.rigStatus.textContent = `${report.counts.bones} bones / ${report.counts.skinnedMeshes} skinned`;
  ui.scaleStatus.textContent = `${report.boundsMeters.height.toFixed(2)} high`;
  ui.animationStatus.textContent = `${report.counts.animationClips} clips`;
}

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, (ch) => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' }[ch]));
}

function setMaterialWireframe(enabled) {
  if (!activeRoot) return;
  activeRoot.traverse((obj) => {
    if (!obj.isMesh) return;
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    mats.filter(Boolean).forEach((mat) => {
      if (mat.userData.__havenlineOriginalWireframe === undefined) mat.userData.__havenlineOriginalWireframe = !!mat.wireframe;
      mat.wireframe = enabled ? true : mat.userData.__havenlineOriginalWireframe;
      mat.needsUpdate = true;
    });
  });
}

function updateSkeleton() {
  if (skeletonHelper) { scene.remove(skeletonHelper); skeletonHelper = null; }
  if (!activeRoot || !ui.skeletonToggle.checked) return;
  skeletonHelper = new THREE.SkeletonHelper(activeRoot);
  skeletonHelper.material.depthTest = false;
  skeletonHelper.material.transparent = true;
  skeletonHelper.material.opacity = 0.78;
  skeletonHelper.renderOrder = 999;
  scene.add(skeletonHelper);
}

function setupAnimations(gltf) {
  if (currentAction) currentAction.stop();
  mixer = gltf.animations?.length ? new THREE.AnimationMixer(activeRoot) : null;
  currentAction = null;
  ui.animationSelect.innerHTML = '';
  const clips = gltf.animations || [];
  const enabled = clips.length > 0;
  [ui.animationSelect, ui.playBtn, ui.pauseBtn, ui.resetAnimBtn].forEach((el) => { el.disabled = !enabled; });
  if (!enabled) {
    ui.animationSelect.innerHTML = '<option>No animation clips</option>';
    return;
  }
  clips.forEach((clip, i) => {
    const opt = document.createElement('option');
    opt.value = String(i);
    opt.textContent = clip.name || `Clip ${i + 1}`;
    ui.animationSelect.appendChild(opt);
  });
  selectAnimation(0, true);
}

function selectAnimation(index, autoplay = false) {
  if (!mixer || !activeGltf?.animations?.[index]) return;
  if (currentAction) currentAction.stop();
  currentAction = mixer.clipAction(activeGltf.animations[index]);
  currentAction.reset();
  currentAction.setLoop(THREE.LoopRepeat, Infinity);
  if (autoplay) currentAction.play();
}

async function parseFile(file) {
  const arrayBuffer = await file.arrayBuffer();
  return new Promise((resolve, reject) => loader.parse(arrayBuffer, '', resolve, reject));
}

async function loadModel(file) {
  const slot = currentSlot();
  setStatus('Loading model…', 'neutral');
  ui.modelName.textContent = file.name;
  try {
    const gltf = await parseFile(file);
    if (slot.root && slot.root !== activeRoot) slot.root.parent?.remove(slot.root);
    clearSceneRoot();
    slot.modelFile = file;
    slot.gltf = gltf;
    slot.root = gltf.scene;
    activeGltf = gltf;
    activeRoot = gltf.scene;
    activeRoot.name = `${activeSlotId}_${file.name}`;
    const box = normalizeRoot(activeRoot);
    world.add(activeRoot);
    modelBounds = box;
    activeRoot.traverse((obj) => {
      if (obj.isMesh) { obj.castShadow = true; obj.receiveShadow = true; }
    });
    slot.report = auditModel(activeRoot, gltf);
    renderReport(slot.report);
    setupAnimations(gltf);
    updateSkeleton();
    setMaterialWireframe(ui.wireframeToggle.checked);
    ui.stageEmpty.style.display = 'none';
    fitCamera('front');
  } catch (error) {
    console.error(error);
    setStatus('GLB load failed', 'bad');
    ui.findings.innerHTML = `<li class="bad">${escapeHtml(error?.message || String(error))}</li>`;
  }
}

function loadReference(file) {
  const slot = currentSlot();
  if (slot.referenceUrl) URL.revokeObjectURL(slot.referenceUrl);
  slot.referenceFile = file;
  slot.referenceUrl = URL.createObjectURL(file);
  ui.referenceName.textContent = file.name;
  applyReference(slot);
}

function applyReference(slot) {
  if (!slot.referenceUrl) {
    ui.referenceImage.style.display = 'none';
    ui.referenceOverlay.style.display = 'none';
    ui.referenceEmpty.style.display = 'block';
    ui.referenceName.textContent = 'Not loaded';
    return;
  }
  ui.referenceImage.src = slot.referenceUrl;
  ui.referenceOverlay.src = slot.referenceUrl;
  ui.referenceImage.style.display = 'block';
  ui.referenceEmpty.style.display = 'none';
  ui.referenceName.textContent = slot.referenceFile?.name || 'Reference';
  updateOverlay();
}

function switchSlot(id) {
  if (id === activeSlotId) return;
  clearSceneRoot();
  activeSlotId = id;
  $$('#slotButtons button').forEach((b) => b.classList.toggle('active', b.dataset.slot === id));
  const slot = currentSlot();
  ui.modelName.textContent = slot.modelFile?.name || 'Not loaded';
  applyReference(slot);
  if (slot.root && slot.gltf) {
    activeRoot = slot.root;
    activeGltf = slot.gltf;
    world.add(activeRoot);
    slot.report ||= auditModel(activeRoot, activeGltf);
    renderReport(slot.report);
    setupAnimations(activeGltf);
    updateSkeleton();
    setMaterialWireframe(ui.wireframeToggle.checked);
    ui.stageEmpty.style.display = 'none';
    fitCamera('front');
  } else {
    ui.stageEmpty.style.display = 'grid';
    ui.statsGrid.querySelectorAll('dd').forEach((dd) => dd.textContent = '—');
    ui.findings.innerHTML = '<li class="muted">No model loaded for this slot.</li>';
    ui.materialAudit.textContent = 'No materials loaded.';
    ui.materialAudit.classList.add('muted');
    setStatus('Awaiting model', 'neutral');
    ui.rigStatus.textContent = '—'; ui.scaleStatus.textContent = '—'; ui.animationStatus.textContent = '—';
  }
}

function updateOverlay() {
  const slot = currentSlot();
  const show = !!slot.referenceUrl && ui.overlayToggle.checked;
  ui.referenceOverlay.style.display = show ? 'block' : 'none';
  ui.referenceOverlay.style.opacity = String(Number(ui.overlayOpacity.value) / 100);
  ui.overlayValue.textContent = `${ui.overlayOpacity.value}%`;
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = filename; a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function exportReport() {
  const slot = currentSlot();
  if (!slot.report) return setStatus('Load a model before exporting QA', 'warn');
  const payload = {
    ...slot.report,
    referenceName: slot.referenceFile?.name || null,
    currentView: activeView,
    inspection: { skeletonVisible: ui.skeletonToggle.checked, wireframe: ui.wireframeToggle.checked, exposure: renderer.toneMappingExposure }
  };
  saveBlob(new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }), `${activeSlotId}_${safeName(slot.modelFile?.name)}_qa.json`);
}

function drawProofLabel(ctx, label, x, y, width, height) {
  ctx.fillStyle = 'rgba(8,10,14,.72)';
  ctx.fillRect(x, y + height - 34, width, 34);
  ctx.fillStyle = '#ffffff';
  ctx.font = '700 16px system-ui, sans-serif';
  ctx.fillText(label, x + 12, y + height - 12);
}

async function exportFourViewProof() {
  if (!activeRoot) return setStatus('Load a model before exporting proof', 'warn');
  const slot = currentSlot();
  const prevPosition = camera.position.clone();
  const prevTarget = controls.target.clone();
  const prevView = activeView;
  const views = [['front','FRONT'], ['three-quarter','3/4 FRONT'], ['side','SIDE'], ['back','BACK']];
  const tileW = 720, tileH = 720;
  const out = document.createElement('canvas');
  out.width = tileW * 2; out.height = tileH * 2 + 56;
  const ctx = out.getContext('2d');
  ctx.fillStyle = '#0f141b'; ctx.fillRect(0, 0, out.width, out.height);
  ctx.fillStyle = '#f18b37'; ctx.font = '800 20px system-ui, sans-serif';
  ctx.fillText(`HAVENLINE ${activeSlotId} · ${slot.modelFile?.name || 'model'} · 4-VIEW REVIEW`, 14, 34);

  const originalSize = new THREE.Vector2();
  renderer.getSize(originalSize);
  renderer.setSize(tileW, tileH, false);
  for (let i = 0; i < views.length; i++) {
    fitCamera(views[i][0], false);
    renderer.render(scene, camera);
    const x = (i % 2) * tileW;
    const y = 56 + Math.floor(i / 2) * tileH;
    ctx.drawImage(renderer.domElement, x, y, tileW, tileH);
    drawProofLabel(ctx, views[i][1], x, y, tileW, tileH);
  }
  renderer.setSize(originalSize.x, originalSize.y, false);
  camera.position.copy(prevPosition);
  controls.target.copy(prevTarget);
  camera.lookAt(prevTarget);
  camera.updateProjectionMatrix(); controls.update();
  activeView = prevView; fitCamera(prevView);
  out.toBlob((blob) => blob && saveBlob(blob, `${activeSlotId}_${safeName(slot.modelFile?.name)}_4view.png`), 'image/png');
}

function handleDroppedFile(file) {
  const type = file.type || '';
  const lower = file.name.toLowerCase();
  if (lower.endsWith('.glb') || lower.endsWith('.gltf') || type.includes('gltf')) loadModel(file);
  else if (type.startsWith('image/')) loadReference(file);
  else setStatus(`Unsupported file: ${file.name}`, 'warn');
}

ui.modelInput.addEventListener('change', (e) => e.target.files?.[0] && loadModel(e.target.files[0]));
ui.referenceInput.addEventListener('change', (e) => e.target.files?.[0] && loadReference(e.target.files[0]));
['dragenter','dragover'].forEach((eventName) => ui.dropZone.addEventListener(eventName, (e) => { e.preventDefault(); ui.dropZone.classList.add('drag'); }));
['dragleave','drop'].forEach((eventName) => ui.dropZone.addEventListener(eventName, (e) => { e.preventDefault(); ui.dropZone.classList.remove('drag'); }));
ui.dropZone.addEventListener('drop', (e) => [...(e.dataTransfer?.files || [])].forEach(handleDroppedFile));
ui.stage.addEventListener('dragover', (e) => e.preventDefault());
ui.stage.addEventListener('drop', (e) => { e.preventDefault(); [...(e.dataTransfer?.files || [])].forEach(handleDroppedFile); });
$$('#slotButtons button').forEach((button) => button.addEventListener('click', () => switchSlot(button.dataset.slot)));
$$('.camera-grid button').forEach((button) => button.addEventListener('click', () => fitCamera(button.dataset.view)));
ui.zoomRange.addEventListener('input', () => { ui.zoomValue.textContent = `${ui.zoomRange.value}%`; fitCamera(activeView, false); });
ui.skeletonToggle.addEventListener('change', updateSkeleton);
ui.wireframeToggle.addEventListener('change', () => setMaterialWireframe(ui.wireframeToggle.checked));
ui.gridToggle.addEventListener('change', () => { grid.visible = ui.gridToggle.checked; });
ui.overlayToggle.addEventListener('change', updateOverlay);
ui.overlayOpacity.addEventListener('input', updateOverlay);
ui.exposureRange.addEventListener('input', () => { renderer.toneMappingExposure = Number(ui.exposureRange.value) / 100; ui.exposureValue.textContent = renderer.toneMappingExposure.toFixed(2); });
ui.animationSelect.addEventListener('change', () => selectAnimation(Number(ui.animationSelect.value), true));
ui.playBtn.addEventListener('click', () => currentAction?.play());
ui.pauseBtn.addEventListener('click', () => { if (currentAction) currentAction.paused = !currentAction.paused; });
ui.resetAnimBtn.addEventListener('click', () => { currentAction?.reset().play(); });
ui.speedRange.addEventListener('input', () => { const speed = Number(ui.speedRange.value) / 100; if (mixer) mixer.timeScale = speed; ui.speedValue.textContent = `${speed.toFixed(2)}×`; });
ui.proofBtn.addEventListener('click', exportFourViewProof);
ui.reportBtn.addEventListener('click', exportReport);
ui.fitReferenceBtn.addEventListener('click', () => { ui.referenceImage.style.objectFit = ui.referenceImage.style.objectFit === 'cover' ? 'contain' : 'cover'; });

function animate(now) {
  requestAnimationFrame(animate);
  const dt = Math.min(clock.getDelta(), 0.1);
  if (mixer) mixer.update(dt);
  controls.autoRotate = ui.autoRotate.checked;
  controls.autoRotateSpeed = 1.3;
  controls.update();
  renderer.render(scene, camera);
  const elapsed = Math.max(1, now - lastFrame);
  const fps = 1000 / elapsed;
  fpsEMA = fpsEMA * 0.92 + fps * 0.08;
  lastFrame = now;
  ui.fpsLabel.textContent = `${Math.round(fpsEMA)} FPS`;
  const info = renderer.info.render;
  ui.renderLabel.textContent = `${info.calls} calls · ${formatInt(info.triangles)} tris`;
}

async function loadFromQueryString() {
  const params = new URLSearchParams(window.location.search);
  const requestedSlot = (params.get('slot') || '').toUpperCase();
  if (slots.has(requestedSlot) && requestedSlot !== activeSlotId) switchSlot(requestedSlot);

  const modelUrl = params.get('model');
  const referenceUrl = params.get('reference');
  try {
    if (referenceUrl) {
      const response = await fetch(referenceUrl);
      if (!response.ok) throw new Error(`Reference HTTP ${response.status}`);
      const blob = await response.blob();
      const name = referenceUrl.split('/').pop() || 'reference.png';
      loadReference(new File([blob], name, { type: blob.type || 'image/png' }));
    }
    if (modelUrl) {
      const response = await fetch(modelUrl);
      if (!response.ok) throw new Error(`Model HTTP ${response.status}`);
      const blob = await response.blob();
      const name = modelUrl.split('/').pop() || 'character.glb';
      await loadModel(new File([blob], name, { type: blob.type || 'model/gltf-binary' }));
    }
  } catch (error) {
    console.error(error);
    setStatus(`URL preload failed: ${error.message || error}`, 'bad');
  }
}

resize();
requestAnimationFrame(animate);
loadFromQueryString();
