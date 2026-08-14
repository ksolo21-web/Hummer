import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import {
  AutoActionKind,
  ResourceKind,
  actionCandidates,
  chooseAutoAction,
  completeWaveIfClear,
  createInitialState,
  distance2D,
  movePlayer,
  runDeterministicContractQA,
  stepAutoAction,
  updateWaveGate,
  validateContract,
  vec3
} from './sim-core.js';

const CONTRACT_URL = '../shared/HAVENLINE_REFERENCE_CONTRACT.json';
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
const ui = {
  stage: $('#stage'), bootCard: $('#bootCard'), resetBtn: $('#resetBtn'), qaBtn: $('#qaBtn'), objective: $('#objective'),
  carry: $('#carry'), furnaceStatus: $('#furnaceStatus'), crewStatus: $('#crewStatus'), actionStatus: $('#actionStatus'),
  actionProgress: $('#actionProgress'), perfStatus: $('#perfStatus'), joystick: $('#joystick'), stick: $('#stick'), sprintBtn: $('#sprintBtn'),
  contractVersion: $('#contractVersion'), unityVersion: $('#unityVersion'), cameraLock: $('#cameraLock'), speedLock: $('#speedLock'),
  carryLock: $('#carryLock'), boundsLock: $('#boundsLock'), authorityNote: $('#authorityNote'), leadModelInput: $('#leadModelInput'),
  showRanges: $('#showRanges'), showBounds: $('#showBounds'), pauseWaves: $('#pauseWaves'), teleportWood: $('#teleportWood'),
  teleportFurnace: $('#teleportFurnace'), teleportSurvivor: $('#teleportSurvivor'), spawnWolf: $('#spawnWolf'), woodCount: $('#woodCount'),
  stoneCount: $('#stoneCount'), storedCount: $('#storedCount'), warmthRadius: $('#warmthRadius'), rescueState: $('#rescueState'),
  waveState: $('#waveState'), eventLog: $('#eventLog')
};

let contract = null;
let state = null;
let selectedLead = 'Character1';
let scene, renderer, camera, playerGroup, playerProxy, playerLoadedModel, warmthMesh, boundsHelper, interactionRange;
let furnaceVisual, survivorVisual, northDefenseVisual, southDefenseVisual, helperVisual;
let companionGroups = [];
let resourceVisuals = new Map();
let wolfVisuals = new Map();
let lastFrame = performance.now();
let fpsEMA = 60;
let actionRescanClock = 0;
let loggedActionKey = '';
const keys = new Set();
const loader = new GLTFLoader();
const clock = new THREE.Clock();
const movement = { joystickX: 0, joystickY: 0, sprint: false, pointerId: null };
const colors = {
  snow: 0xcbdce7, ice: 0x8fc6df, dark: 0x172331, orange: 0xf18a35, blue: 0x4aa3e9, crew: 0x6fc3ff,
  wood: 0x8b5a35, stone: 0x89959e, metal: 0x687784, fuel: 0x9b7137, wolf: 0x913e47, warmth: 0xffa536
};

boot();

async function boot() {
  try {
    const response = await fetch(CONTRACT_URL, { cache: 'no-store' });
    if (!response.ok) throw new Error(`Runtime contract HTTP ${response.status}`);
    contract = await response.json();
    const failures = validateContract(contract);
    if (failures.length) throw new Error(`Runtime contract rejected: ${failures.join(' | ')}`);
    populateAuthority();
    initializeThree();
    resetRun();
    bindControls();
    ui.bootCard.style.display = 'none';
    logEvent(`Loaded HAVENLINE runtime contract ${contract.contractVersion}.`);
    requestAnimationFrame(animate);
  } catch (error) {
    console.error(error);
    ui.bootCard.innerHTML = `<strong>Sandbox blocked</strong><span>${escapeHtml(error.message || String(error))}</span>`;
    ui.bootCard.style.borderColor = '#ef6b70';
  }
}

function populateAuthority() {
  ui.contractVersion.textContent = contract.contractVersion;
  ui.unityVersion.textContent = contract.unityEditor;
  ui.cameraLock.textContent = `ortho ${contract.camera.size.toFixed(2)}`;
  ui.speedLock.textContent = `${contract.player.walkSpeed.toFixed(2)} / ${contract.player.runSpeed.toFixed(2)}`;
  ui.carryLock.textContent = String(contract.player.carryCapacity);
  ui.boundsLock.textContent = `±${contract.world.boundX.toFixed(1)} × ±${contract.world.boundZ.toFixed(1)}`;
  ui.authorityNote.textContent = 'This browser sandbox refuses guessed tuning. It loads the exact browser mirror of the Unity runtime contract; CI compares both JSON files byte-for-byte.';
}

function initializeThree() {
  renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.1;
  renderer.shadowMap.enabled = true;
  ui.stage.prepend(renderer.domElement);

  scene = new THREE.Scene();
  scene.background = new THREE.Color(0x0d1a26);
  scene.fog = new THREE.Fog(0x0d1a26, 24, 52);
  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.03).texture;
  pmrem.dispose();

  camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 120);
  camera.zoom = 1;
  scene.add(new THREE.HemisphereLight(0xdceeff, 0x243342, 1.8));
  const sun = new THREE.DirectionalLight(0xf2f7ff, 3.0);
  sun.position.set(-6, 12, 8); sun.castShadow = true; scene.add(sun);
  const warm = new THREE.PointLight(0xff8a32, 10, 18, 2);
  warm.position.set(contract.world.furnace[0], 2.4, contract.world.furnace[2]); warm.name = 'FurnaceLight'; scene.add(warm);

  buildWorld();
  resize();
  addEventListener('resize', resize);
  new ResizeObserver(resize).observe(ui.stage);
}

function buildWorld() {
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(contract.world.boundX * 2 + 8, contract.world.boundZ * 2 + 8),
    new THREE.MeshStandardMaterial({ color: colors.snow, roughness: 0.96, metalness: 0 })
  );
  ground.rotation.x = -Math.PI / 2; ground.position.y = -0.04; ground.receiveShadow = true; scene.add(ground);

  const grid = new THREE.GridHelper(Math.max(contract.world.boundX, contract.world.boundZ) * 2, 32, 0x7892a7, 0x9dafbd);
  grid.material.opacity = 0.16; grid.material.transparent = true; scene.add(grid);

  boundsHelper = createBoundsHelper(); scene.add(boundsHelper);
  interactionRange = new THREE.Mesh(new THREE.RingGeometry(0.97, 1, 96), new THREE.MeshBasicMaterial({ color: 0x67c6ff, transparent: true, opacity: 0.34, side: THREE.DoubleSide }));
  interactionRange.rotation.x = -Math.PI / 2; interactionRange.position.y = 0.025; interactionRange.visible = false; scene.add(interactionRange);

  furnaceVisual = createFurnace(); place(furnaceVisual, contract.world.furnace); scene.add(furnaceVisual);
  warmthMesh = new THREE.Mesh(new THREE.RingGeometry(0.94, 1, 128), new THREE.MeshBasicMaterial({ color: colors.warmth, transparent: true, opacity: 0.5, side: THREE.DoubleSide, depthWrite: false }));
  warmthMesh.rotation.x = -Math.PI / 2; warmthMesh.position.set(contract.world.furnace[0], 0.035, contract.world.furnace[2]); scene.add(warmthMesh);

  createTent(contract.world.leftTent, 'Starting Shelter');
  createTent(contract.world.rightTent, 'Rescue Shelter');
  createStorage(contract.world.storage);
  createCampfire(contract.world.campfire);

  survivorVisual = createActorProxy(0xf1c18b, 0x1a2a3a, 0.92); place(survivorVisual, contract.world.survivor); scene.add(survivorVisual);
  helperVisual = createActorProxy(0x7ccf9b, 0x1a2a3a, 0.9); helperVisual.visible = false; scene.add(helperVisual);

  northDefenseVisual = createDefenseVisual(); place(northDefenseVisual, contract.world.northBarricade); scene.add(northDefenseVisual);
  southDefenseVisual = createDefenseVisual(); place(southDefenseVisual, contract.world.southBarricade); scene.add(southDefenseVisual);

  const nodeSpecs = [
    ...contract.world.woodNodes.map((p, i) => [`wood-${i + 1}`, ResourceKind.Wood, p]),
    ...contract.world.stoneNodes.map((p, i) => [`stone-${i + 1}`, ResourceKind.Stone, p]),
    ['metal-1', ResourceKind.Metal, contract.world.metalNode],
    ['fuel-1', ResourceKind.Fuel, contract.world.fuelNode]
  ];
  for (const [id, kind, p] of nodeSpecs) {
    const visual = createResourceVisual(kind); place(visual, p); scene.add(visual); resourceVisuals.set(id, visual);
  }

  const gate = new THREE.Group();
  const postMat = new THREE.MeshStandardMaterial({ color: 0x4b3524, roughness: 0.9 });
  for (const x of [-2.4, 2.4]) {
    const post = new THREE.Mesh(new THREE.BoxGeometry(0.35, 3.2, 0.35), postMat); post.position.set(x, 1.6, 0); gate.add(post);
  }
  const cross = new THREE.Mesh(new THREE.BoxGeometry(5.2, 0.35, 0.35), postMat); cross.position.y = 2.75; gate.add(cross);
  place(gate, contract.world.forestGate); scene.add(gate);
}

function createBoundsHelper() {
  const { boundX: x, boundZ: z } = contract.world;
  const pts = [new THREE.Vector3(-x, 0.04, -z), new THREE.Vector3(x, 0.04, -z), new THREE.Vector3(x, 0.04, z), new THREE.Vector3(-x, 0.04, z), new THREE.Vector3(-x, 0.04, -z)];
  return new THREE.Line(new THREE.BufferGeometry().setFromPoints(pts), new THREE.LineBasicMaterial({ color: 0x68a9d5, transparent: true, opacity: 0.35 }));
}

function createActorProxy(jacketColor, accentColor, scale = 1) {
  const group = new THREE.Group(); group.userData.proxy = true;
  const jacket = new THREE.MeshStandardMaterial({ color: jacketColor, roughness: 0.78, metalness: 0 });
  const accent = new THREE.MeshStandardMaterial({ color: accentColor, roughness: 0.72, metalness: 0 });
  const skin = new THREE.MeshStandardMaterial({ color: 0x8f5f45, roughness: 0.86, metalness: 0 });
  const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.32, 0.74, 6, 12), jacket); body.position.y = 0.82; body.castShadow = true; group.add(body);
  const head = new THREE.Mesh(new THREE.SphereGeometry(0.29, 18, 12), skin); head.position.y = 1.57; head.castShadow = true; group.add(head);
  const pack = new THREE.Mesh(new THREE.BoxGeometry(0.55, 0.62, 0.23), accent); pack.position.set(0, 0.94, 0.31); group.add(pack);
  const feet = new THREE.Mesh(new THREE.BoxGeometry(0.58, 0.18, 0.38), accent); feet.position.set(0, 0.1, -0.04); group.add(feet);
  group.scale.setScalar(scale); return group;
}

function createFurnace() {
  const g = new THREE.Group();
  const body = new THREE.Mesh(new THREE.CylinderGeometry(0.72, 0.82, 1.5, 20), new THREE.MeshStandardMaterial({ color: 0x39434a, roughness: 0.58, metalness: 0.18 })); body.position.y = 0.78; body.castShadow = true; g.add(body);
  const fire = new THREE.Mesh(new THREE.SphereGeometry(0.33, 16, 12), new THREE.MeshBasicMaterial({ color: 0xff7a22 })); fire.position.set(0, 0.65, 0.68); fire.scale.y = 1.35; g.add(fire);
  const pipe = new THREE.Mesh(new THREE.CylinderGeometry(0.19, 0.24, 1.6, 16), body.material); pipe.position.set(0, 2.0, -0.15); g.add(pipe);
  return g;
}

function createDefenseVisual() {
  const g = new THREE.Group();
  const mat = new THREE.MeshStandardMaterial({ color: 0x6f4e32, roughness: 0.9, metalness: 0 });
  for (let i = 0; i < 5; i++) {
    const log = new THREE.Mesh(new THREE.BoxGeometry(1.05, 0.24, 0.32), mat); log.position.set((i - 2) * 0.82, 0.18 + (i % 2) * 0.18, 0); log.rotation.z = (i % 2 ? -1 : 1) * 0.06; g.add(log);
  }
  g.scale.y = 0.15; return g;
}

function createResourceVisual(kind) {
  if (kind === ResourceKind.Wood) {
    const g = new THREE.Group();
    const trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.24, 1.4, 10), new THREE.MeshStandardMaterial({ color: colors.wood, roughness: 0.92 })); trunk.position.y = 0.7; g.add(trunk);
    const crown = new THREE.Mesh(new THREE.ConeGeometry(0.95, 2.2, 10), new THREE.MeshStandardMaterial({ color: 0x254b42, roughness: 0.92 })); crown.position.y = 2.0; g.add(crown); return g;
  }
  const color = kind === ResourceKind.Stone ? colors.stone : kind === ResourceKind.Metal ? colors.metal : colors.fuel;
  const geom = kind === ResourceKind.Stone ? new THREE.DodecahedronGeometry(0.55, 0) : new THREE.BoxGeometry(0.75, 0.55, 0.65);
  const mesh = new THREE.Mesh(geom, new THREE.MeshStandardMaterial({ color, roughness: 0.75, metalness: kind === ResourceKind.Metal ? 0.35 : 0 })); mesh.position.y = 0.38; mesh.castShadow = true; return mesh;
}

function createTent(position, name) {
  const g = new THREE.Group(); g.name = name;
  const mat = new THREE.MeshStandardMaterial({ color: 0x355c79, roughness: 0.9, side: THREE.DoubleSide });
  const tent = new THREE.Mesh(new THREE.ConeGeometry(1.8, 2.3, 4), mat); tent.rotation.y = Math.PI / 4; tent.position.y = 1.15; tent.scale.z = 1.4; tent.castShadow = true; g.add(tent); place(g, position); scene.add(g);
}
function createStorage(position) { const m = new THREE.Mesh(new THREE.BoxGeometry(1.8, 1.0, 1.2), new THREE.MeshStandardMaterial({ color: 0x785437, roughness: 0.9 })); m.position.y = 0.5; place(m, position); scene.add(m); }
function createCampfire(position) { const g = new THREE.Group(); const ring = new THREE.Mesh(new THREE.TorusGeometry(0.6, 0.14, 8, 18), new THREE.MeshStandardMaterial({ color: 0x5e6466, roughness: 0.9 })); ring.rotation.x = Math.PI / 2; ring.position.y = 0.12; g.add(ring); const flame = new THREE.Mesh(new THREE.ConeGeometry(0.28, 0.9, 12), new THREE.MeshBasicMaterial({ color: 0xff8b32 })); flame.position.y = 0.5; g.add(flame); place(g, position); scene.add(g); }
function place(object, p) { object.position.set(p[0], p[1] ?? 0, p[2]); }

function resetRun() {
  if (!contract || !scene) return;
  for (const g of companionGroups) scene.remove(g);
  companionGroups = [];
  if (playerGroup) scene.remove(playerGroup);
  if (helperVisual) helperVisual.visible = false;
  for (const visual of wolfVisuals.values()) scene.remove(visual);
  wolfVisuals.clear();

  state = createInitialState(contract, selectedLead);
  playerGroup = new THREE.Group(); playerProxy = createActorProxy(colors.orange, 0x2f5e84, 1); playerGroup.add(playerProxy); scene.add(playerGroup);
  playerLoadedModel = null;
  createCompanions();
  actionRescanClock = 0; loggedActionKey = '';
  ui.eventLog.innerHTML = '';
  logEvent(`New run: ${selectedLead} lead; companions ${state.companions.join(', ')}.`);
  syncVisuals(); updateHud();
}

function createCompanions() {
  const offsets = contract.characterSystem.companionFormationOffsets;
  const palette = [0x4d91bd, 0x6b77b8, 0x8d6cb1];
  state.companions.forEach((id, i) => {
    const g = createActorProxy(palette[i], 0x26394c, 0.92); g.userData.characterId = id; g.userData.offset = vec3(offsets[i]); scene.add(g); companionGroups.push(g);
  });
}

function bindControls() {
  addEventListener('keydown', (e) => { keys.add(e.code); if (['ArrowUp','ArrowDown','ArrowLeft','ArrowRight','Space'].includes(e.code)) e.preventDefault(); });
  addEventListener('keyup', (e) => keys.delete(e.code));
  ui.resetBtn.addEventListener('click', resetRun);
  ui.qaBtn.addEventListener('click', runQaFromUi);
  $$('.segmented [data-lead]').forEach((button) => button.addEventListener('click', () => {
    selectedLead = button.dataset.lead; $$('.segmented [data-lead]').forEach((b) => b.classList.toggle('active', b === button)); resetRun();
  }));
  ui.leadModelInput.addEventListener('change', (e) => e.target.files?.[0] && loadLeadModel(e.target.files[0]));
  ui.showBounds.addEventListener('change', () => { if (boundsHelper) boundsHelper.visible = ui.showBounds.checked; });
  ui.showRanges.addEventListener('change', updateRangeVisual);
  ui.teleportWood.addEventListener('click', () => teleportTo(state.resources.find((n) => n.kind === ResourceKind.Wood)?.position));
  ui.teleportFurnace.addEventListener('click', () => teleportTo(vec3(contract.world.furnace)));
  ui.teleportSurvivor.addEventListener('click', () => teleportTo(state.survivor.position));
  ui.spawnWolf.addEventListener('click', () => spawnDebugWolf());
  bindJoystick(); bindSprint();
}

function bindJoystick() {
  const update = (e) => {
    if (movement.pointerId !== e.pointerId) return;
    const r = ui.joystick.getBoundingClientRect(); const cx = r.left + r.width / 2; const cy = r.top + r.height / 2;
    const radius = r.width * 0.37; let dx = e.clientX - cx, dy = e.clientY - cy; const len = Math.hypot(dx, dy);
    if (len > radius) { dx *= radius / len; dy *= radius / len; }
    movement.joystickX = dx / radius; movement.joystickY = -dy / radius; ui.stick.style.transform = `translate(calc(-50% + ${dx}px), calc(-50% + ${dy}px))`;
  };
  ui.joystick.addEventListener('pointerdown', (e) => { movement.pointerId = e.pointerId; ui.joystick.setPointerCapture(e.pointerId); update(e); });
  ui.joystick.addEventListener('pointermove', update);
  const end = (e) => { if (movement.pointerId !== e.pointerId) return; movement.pointerId = null; movement.joystickX = movement.joystickY = 0; ui.stick.style.transform = 'translate(-50%,-50%)'; };
  ui.joystick.addEventListener('pointerup', end); ui.joystick.addEventListener('pointercancel', end);
}

function bindSprint() {
  const set = (held) => { movement.sprint = held; ui.sprintBtn.classList.toggle('held', held); };
  ui.sprintBtn.addEventListener('pointerdown', (e) => { ui.sprintBtn.setPointerCapture(e.pointerId); set(true); });
  ui.sprintBtn.addEventListener('pointerup', () => set(false)); ui.sprintBtn.addEventListener('pointercancel', () => set(false));
}

function readMovement() {
  let sx = movement.joystickX; let sy = movement.joystickY;
  if (keys.has('KeyA') || keys.has('ArrowLeft')) sx -= 1;
  if (keys.has('KeyD') || keys.has('ArrowRight')) sx += 1;
  if (keys.has('KeyW') || keys.has('ArrowUp')) sy += 1;
  if (keys.has('KeyS') || keys.has('ArrowDown')) sy -= 1;
  const len = Math.hypot(sx, sy); if (len > 1) { sx /= len; sy /= len; }
  const forward = new THREE.Vector3(); camera.getWorldDirection(forward); forward.y = 0; forward.normalize();
  const right = new THREE.Vector3().crossVectors(forward, new THREE.Vector3(0, 1, 0)).negate().normalize();
  const world = right.multiplyScalar(sx).add(forward.multiplyScalar(sy));
  return { x: world.x, z: world.z, sprint: movement.sprint || keys.has('ShiftLeft') || keys.has('ShiftRight'), magnitude: len };
}

function updateSimulation(dt) {
  if (!state) return;
  const input = readMovement();
  const moved = movePlayer(state, input, dt);
  state.elapsedSeconds += dt;

  if (moved && input.magnitude > contract.openingLoopTuning.automaticActionMovementCancelThreshold) {
    state.action = { kind: AutoActionKind.None, id: null, label: 'Moving', progress: 0 };
    resetInteractionProgress();
  } else {
    actionRescanClock -= dt;
    if (actionRescanClock <= 0) {
      actionRescanClock = contract.openingLoopTuning.automaticActionRescanSeconds;
      state.action = chooseAutoAction(state, state.action);
    }
    const event = stepAutoAction(state, dt);
    if (event) handleSimEvent(event);
  }

  updateHelper(dt);
  if (!ui.pauseWaves.checked) {
    const waveEvent = updateWaveGate(state, dt);
    if (waveEvent) handleSimEvent(waveEvent);
  }
  updateWolves(dt);
  if (completeWaveIfClear(state)) logEvent(`Wave ${state.waves.waveNumber} cleared. Next pressure in ${state.waves.timer.toFixed(0)}s.`);
}

function updateHelper(dt) {
  if (!state.helper.active) return;
  const target = state.player.position; const p = state.helper.position; const dx = target.x - p.x; const dz = target.z - p.z; const d = Math.hypot(dx, dz);
  if (d > 2.8) { const step = Math.min(d - 2.2, 3.15 * dt); p.x += dx / d * step; p.z += dz / d * step; state.helper.state = 'following'; }
}

function updateWolves(dt) {
  for (const enemy of state.waves.enemies) {
    if (!enemy.alive) continue;
    const target = state.defenses.north.built && state.defenses.north.health > 0 ? state.defenses.north.position : vec3(contract.world.furnace);
    const dPlayer = distance2D(enemy.position, state.player.position);
    if (dPlayer < 2.8) continue;
    const dx = target.x - enemy.position.x, dz = target.z - enemy.position.z, d = Math.hypot(dx, dz) || 1;
    const speed = 3.9; enemy.position.x += dx / d * speed * dt; enemy.position.z += dz / d * speed * dt;
  }
}

function handleSimEvent(event) {
  if (event.type === 'gather') logEvent(`Gathered ${event.amount} ${event.kind}.`);
  if (event.type === 'deposit') {
    logEvent(`Deposited ${event.kind} into furnace.`);
    if (event.levelChanged) logEvent(`Furnace reached Level ${event.level}; warmth expanded.`);
  }
  if (event.type === 'rescue') logEvent('Frozen survivor rescued; helper activated.');
  if (event.type === 'build') { logEvent(`Delivered ${event.kind} to ${event.site} barricade (${Math.round(event.progress * 100)}%).`); if (event.built) logEvent(`${capitalize(event.site)} barricade completed.`); }
  if (event.type === 'enemy-hit' && !event.alive) logEvent(`${event.id} defeated.`);
  if (event.type === 'wave-start') logEvent(`Wave ${event.wave} started with ${event.count} wolves.`);
}

function resetInteractionProgress() {
  for (const node of state.resources) node.progressSeconds = 0;
  state.furnace.depositProgressSeconds = 0; state.furnace.repairProgressSeconds = 0;
  if (!state.survivor.rescued) state.survivor.rescueProgressSeconds = 0;
}

function syncVisuals() {
  if (!state) return;
  playerGroup.position.set(state.player.position.x, state.player.position.y, state.player.position.z);
  const angle = Math.atan2(state.player.facing.x, state.player.facing.z); playerGroup.rotation.y = angle;

  const offsets = contract.characterSystem.companionFormationOffsets;
  companionGroups.forEach((group, i) => {
    const o = offsets[i]; const target = new THREE.Vector3(state.player.position.x + o[0], 0, state.player.position.z + o[2]);
    group.position.lerp(target, 0.075); group.rotation.y = angle;
  });

  for (const node of state.resources) {
    const visual = resourceVisuals.get(node.id); if (!visual) continue;
    const ratio = Math.max(0.12, node.unitsRemaining / (node.kind === ResourceKind.Wood ? contract.openingLoopTuning.woodUnitsPerNode : node.kind === ResourceKind.Stone ? contract.openingLoopTuning.stoneUnitsPerNode : 10));
    visual.visible = !node.depleted; visual.scale.setScalar(Math.max(0.45, 0.7 + 0.3 * ratio));
  }

  const warmth = state.furnace.warmthRadius; warmthMesh.scale.set(warmth, warmth, warmth);
  furnaceVisual.scale.setScalar(1 + (state.furnace.level - 1) * 0.09);
  survivorVisual.visible = !state.survivor.rescued;
  helperVisual.visible = state.helper.active; if (state.helper.active) helperVisual.position.set(state.helper.position.x, 0, state.helper.position.z);
  northDefenseVisual.scale.y = 0.15 + state.defenses.north.progress * 0.85;
  southDefenseVisual.scale.y = 0.15 + state.defenses.south.progress * 0.85;

  const livingIds = new Set();
  for (const enemy of state.waves.enemies) {
    if (!enemy.alive) continue; livingIds.add(enemy.id);
    let visual = wolfVisuals.get(enemy.id); if (!visual) { visual = createWolf(); scene.add(visual); wolfVisuals.set(enemy.id, visual); }
    visual.position.set(enemy.position.x, 0, enemy.position.z);
  }
  for (const [id, visual] of [...wolfVisuals]) if (!livingIds.has(id)) { scene.remove(visual); wolfVisuals.delete(id); }
  updateRangeVisual();
}

function createWolf() {
  const g = new THREE.Group(); const mat = new THREE.MeshStandardMaterial({ color: colors.wolf, roughness: 0.78 });
  const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.27, 0.65, 5, 10), mat); body.rotation.z = Math.PI / 2; body.position.y = 0.45; g.add(body);
  const head = new THREE.Mesh(new THREE.SphereGeometry(0.28, 12, 8), mat); head.position.set(0.5, 0.55, 0); g.add(head); g.scale.setScalar(0.9); return g;
}

function updateCamera(dt) {
  if (!state) return;
  const focus = new THREE.Vector3(state.player.position.x, state.player.position.y + contract.camera.focusHeight, state.player.position.z);
  const velocity = new THREE.Vector3(state.player.velocity.x, 0, state.player.velocity.z); if (velocity.lengthSq() > 0.001) velocity.normalize().multiplyScalar(contract.camera.lookAhead); focus.add(velocity);
  const desired = focus.clone().add(new THREE.Vector3(...contract.camera.offset));
  const alpha = 1 - Math.exp(-contract.camera.followSharpness * dt); camera.position.lerp(desired, alpha); camera.lookAt(focus); camera.updateMatrixWorld();
}

function updateRangeVisual() {
  if (!state || !interactionRange) return;
  interactionRange.visible = ui.showRanges.checked;
  interactionRange.position.x = state.player.position.x; interactionRange.position.z = state.player.position.z;
  const radius = state.action.kind === AutoActionKind.Enemy ? contract.player.combatRadius : state.action.kind === AutoActionKind.Rescue ? contract.player.rescueRadius : state.action.kind === AutoActionKind.Barricade ? contract.player.buildRadius : state.action.kind === AutoActionKind.FurnaceDeposit || state.action.kind === AutoActionKind.FurnaceRepair ? contract.player.depositRadius : contract.player.interactionRadius;
  interactionRange.scale.set(radius, radius, radius);
}

function updateHud() {
  if (!state) return;
  ui.carry.textContent = `${state.inventory.count} / ${state.inventory.capacity}`;
  ui.furnaceStatus.textContent = `L${state.furnace.level} · ${Math.round(state.furnace.durability)}/${Math.round(state.furnace.maxDurability)}`;
  ui.crewStatus.textContent = `${selectedLead} + ${state.companions.length} companions`;
  ui.actionStatus.textContent = state.action?.label || 'None'; ui.actionProgress.style.width = `${Math.round((state.action?.progress ?? 0) * 100)}%`;
  ui.woodCount.textContent = String(state.inventory.countKind(ResourceKind.Wood)); ui.stoneCount.textContent = String(state.inventory.countKind(ResourceKind.Stone));
  ui.storedCount.textContent = `${state.furnace.stored.wood} / ${state.furnace.stored.stone}`; ui.warmthRadius.textContent = `${state.furnace.warmthRadius.toFixed(1)} m`;
  ui.rescueState.textContent = state.survivor.rescued ? 'Rescued' : state.furnace.level >= 2 ? 'Ready' : 'Needs furnace L2';
  ui.waveState.textContent = state.waves.active ? `Wave ${state.waves.waveNumber} · ${state.waves.enemies.filter((e) => e.alive).length} wolves` : state.waves.unlocked ? `${state.waves.timer.toFixed(0)}s` : 'Locked';
  ui.objective.textContent = objectiveText();
}

function objectiveText() {
  if (state.furnace.level < 2) return `Feed furnace: ${state.furnace.stored.wood}/18 wood · ${state.furnace.stored.stone}/6 stone`;
  if (!state.survivor.rescued) return 'Reach the frozen survivor — rescue begins automatically';
  if (!state.defenses.north.built) return `Build north barricade: ${state.defenses.north.delivered.wood}/8 wood · ${state.defenses.north.delivered.stone}/3 stone`;
  if (state.waves.active) return `Defend the outpost — ${state.waves.enemies.filter((e) => e.alive).length} wolves remain`;
  return `Prepare for pressure — next wave ${state.waves.timer.toFixed(0)}s`;
}

async function loadLeadModel(file) {
  try {
    const buffer = await file.arrayBuffer(); const gltf = await new Promise((resolve, reject) => loader.parse(buffer, '', resolve, reject));
    if (playerLoadedModel) playerGroup.remove(playerLoadedModel);
    playerProxy.visible = false; playerLoadedModel = gltf.scene; normalizeModel(playerLoadedModel, 1.78); playerGroup.add(playerLoadedModel); logEvent(`Loaded local ${file.name} as ${selectedLead} visual.`);
  } catch (error) { console.error(error); logEvent(`Lead GLB rejected: ${error.message || error}`); }
}

function normalizeModel(root, targetHeight) {
  root.updateMatrixWorld(true); const box = new THREE.Box3().setFromObject(root); const size = box.getSize(new THREE.Vector3()); const scale = size.y > 0.001 ? targetHeight / size.y : 1; root.scale.multiplyScalar(scale); root.updateMatrixWorld(true);
  const after = new THREE.Box3().setFromObject(root); const center = after.getCenter(new THREE.Vector3()); root.position.x -= center.x; root.position.z -= center.z; root.position.y -= after.min.y;
  root.traverse((obj) => { if (obj.isMesh) { obj.castShadow = true; obj.receiveShadow = true; } });
}

function teleportTo(target) { if (!target) return; state.player.position.x = target.x; state.player.position.z = target.z + 0.6; state.player.velocity.x = state.player.velocity.z = 0; logEvent(`Debug teleport near ${target.x.toFixed(1)}, ${target.z.toFixed(1)}.`); }
function spawnDebugWolf() { const id = `debug-wolf-${Date.now()}`; state.waves.enemies.push({ id, health: 65, alive: true, position: { x: state.player.position.x + 1.5, y: 0, z: state.player.position.z - 1.5 }, hitCooldown: 0 }); state.waves.active = true; logEvent('Spawned one debug wolf near the lead.'); }

function runQaFromUi() {
  const result = runDeterministicContractQA(contract); logEvent(result.passed ? 'CONTRACT QA PASSED.' : 'CONTRACT QA FAILED.');
  for (const check of result.checks) logEvent(`${check.pass ? '✓' : '✗'} ${check.id}: ${check.details}`);
}

function logEvent(message) {
  const li = document.createElement('li'); li.textContent = message; ui.eventLog.prepend(li); while (ui.eventLog.children.length > 18) ui.eventLog.lastElementChild.remove();
}

function resize() {
  if (!renderer || !camera) return; const rect = ui.stage.getBoundingClientRect(); const w = Math.max(1, rect.width), h = Math.max(1, rect.height); renderer.setSize(w, h, false);
  const aspect = w / h; const size = contract.camera.size; camera.left = -size * aspect; camera.right = size * aspect; camera.top = size; camera.bottom = -size; camera.near = 0.1; camera.far = 120; camera.updateProjectionMatrix();
}

function animate(now) {
  requestAnimationFrame(animate); const dt = Math.min(clock.getDelta(), 0.05); updateSimulation(dt); syncVisuals(); updateCamera(dt); updateHud(); renderer.render(scene, camera);
  const elapsed = Math.max(1, now - lastFrame); fpsEMA = fpsEMA * 0.92 + (1000 / elapsed) * 0.08; lastFrame = now; const r = renderer.info.render; ui.perfStatus.textContent = `${Math.round(fpsEMA)} FPS · ${r.calls} calls · ${formatInt(r.triangles)} tris`;
}

function formatInt(value) { return new Intl.NumberFormat().format(Math.round(value || 0)); }
function capitalize(value) { return value ? value[0].toUpperCase() + value.slice(1) : value; }
function escapeHtml(value) { return String(value).replace(/[&<>'"]/g, (ch) => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' }[ch])); }
