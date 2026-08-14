import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import {
  AutoActionKind,
  ResourceKind,
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
const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];
const Y = new THREE.Vector3(0, 1, 0);
const ui = {
  stage: $('#stage'), boot: $('#bootCard'), reset: $('#resetBtn'), qa: $('#qaBtn'), objective: $('#objective'), carry: $('#carry'),
  furnace: $('#furnaceStatus'), crew: $('#crewStatus'), action: $('#actionStatus'), progress: $('#actionProgress'), perf: $('#perfStatus'),
  joystick: $('#joystick'), stick: $('#stick'), sprint: $('#sprintBtn'), version: $('#contractVersion'), unity: $('#unityVersion'), camera: $('#cameraLock'),
  speed: $('#speedLock'), capacity: $('#carryLock'), bounds: $('#boundsLock'), authority: $('#authorityNote'), modelInput: $('#leadModelInput'),
  showRanges: $('#showRanges'), showBounds: $('#showBounds'), pauseWaves: $('#pauseWaves'), goWood: $('#teleportWood'), goFurnace: $('#teleportFurnace'),
  goSurvivor: $('#teleportSurvivor'), spawnWolf: $('#spawnWolf'), wood: $('#woodCount'), stone: $('#stoneCount'), stored: $('#storedCount'),
  warmth: $('#warmthRadius'), rescue: $('#rescueState'), wave: $('#waveState'), log: $('#eventLog')
};

let contract, state, renderer, scene, camera, player, proxy, loadedLead, warmth, boundsLine, rangeRing;
let furnaceVisual, survivorVisual, helperVisual, northVisual, southVisual;
let companions = [], resources = new Map(), wolves = new Map();
let selectedLead = 'Character1', rescan = 0, last = performance.now(), fps = 60;
const keys = new Set(), loader = new GLTFLoader(), clock = new THREE.Clock();
const touch = { x: 0, y: 0, sprint: false, pointer: null };

main();

async function main() {
  try {
    const r = await fetch(CONTRACT_URL, { cache: 'no-store' });
    if (!r.ok) throw new Error(`Contract HTTP ${r.status}`);
    contract = await r.json();
    const failures = validateContract(contract);
    if (failures.length) throw new Error(failures.join(' | '));
    populateAuthority();
    init3D(); bindUi(); resetRun();
    ui.boot.style.display = 'none';
    log(`Loaded runtime contract ${contract.contractVersion}.`);
    requestAnimationFrame(frame);
  } catch (e) {
    console.error(e);
    ui.boot.innerHTML = `<strong>Sandbox blocked</strong><span>${escapeHtml(e.message || e)}</span>`;
    ui.boot.style.borderColor = '#ef6b70';
  }
}

function populateAuthority() {
  ui.version.textContent = contract.contractVersion; ui.unity.textContent = contract.unityEditor;
  ui.camera.textContent = `ortho ${contract.camera.size.toFixed(2)}`;
  ui.speed.textContent = `${contract.player.walkSpeed.toFixed(2)} / ${contract.player.runSpeed.toFixed(2)}`;
  ui.capacity.textContent = contract.player.carryCapacity;
  ui.bounds.textContent = `±${contract.world.boundX.toFixed(1)} × ±${contract.world.boundZ.toFixed(1)}`;
  ui.authority.textContent = 'Runtime values come from the byte-identical Unity/browser contract mirror; CI rejects drift.';
}

function init3D() {
  renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2)); renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.1; renderer.shadowMap.enabled = true;
  ui.stage.prepend(renderer.domElement);
  scene = new THREE.Scene(); scene.background = new THREE.Color(0x0d1a26); scene.fog = new THREE.Fog(0x0d1a26, 24, 52);
  const pmrem = new THREE.PMREMGenerator(renderer); scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.03).texture; pmrem.dispose();
  camera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0.1, 120);
  scene.add(new THREE.HemisphereLight(0xdceeff, 0x243342, 1.8));
  const sun = new THREE.DirectionalLight(0xf2f7ff, 3); sun.position.set(-6, 12, 8); sun.castShadow = true; scene.add(sun);
  const fireLight = new THREE.PointLight(0xff8a32, 10, 18, 2); fireLight.position.set(contract.world.furnace[0], 2.4, contract.world.furnace[2]); scene.add(fireLight);
  buildWorld(); resize(); addEventListener('resize', resize); new ResizeObserver(resize).observe(ui.stage);
}

function buildWorld() {
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(contract.world.boundX * 2 + 8, contract.world.boundZ * 2 + 8), mat(0xcbdce7, .96));
  ground.rotation.x = -Math.PI / 2; ground.position.y = -.04; ground.receiveShadow = true; scene.add(ground);
  const grid = new THREE.GridHelper(Math.max(contract.world.boundX, contract.world.boundZ) * 2, 32, 0x7892a7, 0x9dafbd); grid.material.opacity = .16; grid.material.transparent = true; scene.add(grid);
  boundsLine = createBounds(); scene.add(boundsLine);
  rangeRing = new THREE.Mesh(new THREE.RingGeometry(.97, 1, 96), new THREE.MeshBasicMaterial({ color: 0x67c6ff, transparent: true, opacity: .34, side: THREE.DoubleSide }));
  rangeRing.rotation.x = -Math.PI / 2; rangeRing.position.y = .025; rangeRing.visible = false; scene.add(rangeRing);

  furnaceVisual = furnaceModel(); place(furnaceVisual, contract.world.furnace); scene.add(furnaceVisual);
  warmth = new THREE.Mesh(new THREE.RingGeometry(.94, 1, 128), new THREE.MeshBasicMaterial({ color: 0xffa536, transparent: true, opacity: .5, side: THREE.DoubleSide, depthWrite: false }));
  warmth.rotation.x = -Math.PI / 2; warmth.position.set(contract.world.furnace[0], .035, contract.world.furnace[2]); scene.add(warmth);
  tent(contract.world.leftTent); tent(contract.world.rightTent); boxProp(contract.world.storage, 1.8, 1, 1.2, 0x785437); campfire(contract.world.campfire);
  survivorVisual = actor(0xf1c18b, 0x1a2a3a, .92); place(survivorVisual, contract.world.survivor); scene.add(survivorVisual);
  helperVisual = actor(0x7ccf9b, 0x1a2a3a, .9); helperVisual.visible = false; scene.add(helperVisual);
  northVisual = defense(); place(northVisual, contract.world.northBarricade); scene.add(northVisual);
  southVisual = defense(); place(southVisual, contract.world.southBarricade); scene.add(southVisual);
  const specs = [
    ...contract.world.woodNodes.map((p,i)=>[`wood-${i+1}`, ResourceKind.Wood, p]),
    ...contract.world.stoneNodes.map((p,i)=>[`stone-${i+1}`, ResourceKind.Stone, p]),
    ['metal-1', ResourceKind.Metal, contract.world.metalNode], ['fuel-1', ResourceKind.Fuel, contract.world.fuelNode]
  ];
  for (const [id, kind, p] of specs) { const v = resource(kind); place(v, p); scene.add(v); resources.set(id, v); }
}

function resetRun() {
  if (!contract) return;
  if (player) scene.remove(player); companions.forEach(x=>scene.remove(x)); companions = [];
  wolves.forEach(x=>scene.remove(x)); wolves.clear(); helperVisual.visible = false;
  state = createInitialState(contract, selectedLead); rescan = 0;
  player = new THREE.Group(); proxy = actor(0xf18a35, 0x2f5e84, 1); player.add(proxy); scene.add(player); loadedLead = null;
  const palette = [0x4d91bd, 0x6b77b8, 0x8d6cb1];
  state.companions.forEach((id,i)=>{ const g=actor(palette[i],0x26394c,.92); g.userData.id=id; g.userData.offset=vec3(contract.characterSystem.companionFormationOffsets[i]); scene.add(g); companions.push(g); });
  ui.log.innerHTML = ''; log(`New run: ${selectedLead}; companions ${state.companions.join(', ')}.`); sync(); hud();
}

function bindUi() {
  addEventListener('keydown', e=>{ keys.add(e.code); if (/Arrow|Space/.test(e.code)) e.preventDefault(); }); addEventListener('keyup', e=>keys.delete(e.code));
  ui.reset.addEventListener('click', resetRun); ui.qa.addEventListener('click', runQa);
  $$('.segmented [data-lead]').forEach(b=>b.addEventListener('click',()=>{ selectedLead=b.dataset.lead; $$('.segmented [data-lead]').forEach(x=>x.classList.toggle('active',x===b)); resetRun(); }));
  ui.modelInput.addEventListener('change', e=>e.target.files?.[0] && loadLead(e.target.files[0]));
  ui.showBounds.addEventListener('change',()=>boundsLine.visible=ui.showBounds.checked); ui.showRanges.addEventListener('change', rangeVisual);
  ui.goWood.addEventListener('click',()=>teleport(state.resources.find(n=>n.kind===ResourceKind.Wood)?.position)); ui.goFurnace.addEventListener('click',()=>teleport(vec3(contract.world.furnace)));
  ui.goSurvivor.addEventListener('click',()=>teleport(state.survivor.position)); ui.spawnWolf.addEventListener('click', debugWolf);
  bindJoystick();
  const sprint=(held)=>{ touch.sprint=held; ui.sprint.classList.toggle('held',held); }; ui.sprint.addEventListener('pointerdown',e=>{ui.sprint.setPointerCapture(e.pointerId); sprint(true);}); ui.sprint.addEventListener('pointerup',()=>sprint(false)); ui.sprint.addEventListener('pointercancel',()=>sprint(false));
}

function bindJoystick() {
  const update=e=>{ if(touch.pointer!==e.pointerId)return; const r=ui.joystick.getBoundingClientRect(), cx=r.left+r.width/2, cy=r.top+r.height/2, rad=r.width*.37; let dx=e.clientX-cx,dy=e.clientY-cy,l=Math.hypot(dx,dy); if(l>rad){dx*=rad/l;dy*=rad/l;} touch.x=dx/rad;touch.y=-dy/rad;ui.stick.style.transform=`translate(calc(-50% + ${dx}px),calc(-50% + ${dy}px))`; };
  ui.joystick.addEventListener('pointerdown',e=>{touch.pointer=e.pointerId;ui.joystick.setPointerCapture(e.pointerId);update(e);}); ui.joystick.addEventListener('pointermove',update);
  const end=e=>{if(touch.pointer!==e.pointerId)return;touch.pointer=null;touch.x=touch.y=0;ui.stick.style.transform='translate(-50%,-50%)';}; ui.joystick.addEventListener('pointerup',end);ui.joystick.addEventListener('pointercancel',end);
}

function input() {
  let sx=touch.x, sy=touch.y; if(keys.has('KeyA')||keys.has('ArrowLeft'))sx-=1;if(keys.has('KeyD')||keys.has('ArrowRight'))sx+=1;if(keys.has('KeyW')||keys.has('ArrowUp'))sy+=1;if(keys.has('KeyS')||keys.has('ArrowDown'))sy-=1;
  let l=Math.hypot(sx,sy); if(l>1){sx/=l;sy/=l;l=1;}
  const forward=new THREE.Vector3();camera.getWorldDirection(forward);forward.y=0;forward.normalize();
  // Unity ReferenceCameraBasis: right is the screen-right basis. No sign flip.
  const right=new THREE.Vector3().crossVectors(forward,Y).normalize();
  const world=right.multiplyScalar(sx).add(forward.multiplyScalar(sy));
  return {x:world.x,z:world.z,sprint:touch.sprint||keys.has('ShiftLeft')||keys.has('ShiftRight'),magnitude:l};
}

function simulate(dt) {
  const i=input(); movePlayer(state,i,dt); state.elapsedSeconds+=dt; rescan-=dt;
  if(rescan<=0){rescan=contract.openingLoopTuning.automaticActionRescanSeconds;state.action=chooseAutoAction(state,state.action);}
  const paused=i.magnitude>contract.openingLoopTuning.automaticActionMovementCancelThreshold;
  if(!paused){const ev=stepAutoAction(state,dt);if(ev)event(ev);}
  else if(state.action?.kind!==AutoActionKind.None) state.action.label=`${baseActionLabel(state.action)} · paused while moving`;
  updateHelper(dt); if(!ui.pauseWaves.checked){const ev=updateWaveGate(state,dt);if(ev)event(ev);} updateWolves(dt);
  if(completeWaveIfClear(state))log(`Wave ${state.waves.waveNumber} cleared. Next pressure in ${state.waves.timer.toFixed(0)}s.`);
}

function baseActionLabel(a){return a.kind===AutoActionKind.Enemy?'Fight wolf':a.kind===AutoActionKind.FurnaceRepair?'Repair furnace':a.kind===AutoActionKind.FurnaceDeposit?'Feed furnace':a.kind===AutoActionKind.Rescue?'Rescue survivor':a.kind===AutoActionKind.Barricade?`Build ${a.id} barricade`:a.kind===AutoActionKind.Resource?`Gather ${state.resources.find(n=>n.id===a.id)?.kind||'resource'}`:'None';}

function updateHelper(dt){if(!state.helper.active)return;const p=state.helper.position,t=state.player.position,dx=t.x-p.x,dz=t.z-p.z,d=Math.hypot(dx,dz);if(d>2.8){const step=Math.min(d-2.2,3.15*dt);p.x+=dx/d*step;p.z+=dz/d*step;state.helper.state='following';}}

function updateWolves(dt){const w=contract.openingLoopTuning.wolf;for(const enemy of state.waves.enemies){if(!enemy.alive)continue;const northStanding=state.defenses.north.built&&state.defenses.north.health>0;const target=northStanding?state.defenses.north.position:vec3(contract.world.furnace);const dx=target.x-enemy.position.x,dz=target.z-enemy.position.z,d=Math.hypot(dx,dz)||1;
    if(d>1.55){enemy.position.x+=dx/d*w.moveSpeed*dt;enemy.position.z+=dz/d*w.moveSpeed*dt;enemy.attackCooldown=0;continue;}
    enemy.attackCooldown=(enemy.attackCooldown||0)+dt;if(enemy.attackCooldown<w.attackSeconds)continue;enemy.attackCooldown=0;
    if(northStanding){state.defenses.north.health=Math.max(0,state.defenses.north.health-w.damageToBarricade);if(state.defenses.north.health<=0)log('North barricade destroyed; wolves retargeted furnace.');}
    else {state.furnace.durability=Math.max(0,state.furnace.durability-w.damageToFurnace);if(state.furnace.durability<=0)log('Furnace disabled by wolf damage.');}
  }}

function event(e){if(e.type==='gather')log(`Gathered ${e.amount} ${e.kind}.`);if(e.type==='deposit'){log(`Deposited ${e.kind}.`);if(e.levelChanged)log(`Furnace reached Level ${e.level}; warmth expanded.`);}if(e.type==='rescue')log('Frozen survivor rescued; helper activated.');if(e.type==='build'){log(`Delivered ${e.kind} to ${e.site} barricade (${Math.round(e.progress*100)}%).`);if(e.built)log(`${cap(e.site)} barricade completed.`);}if(e.type==='enemy-hit'&&!e.alive)log(`${e.id} defeated.`);if(e.type==='wave-start')log(`Wave ${e.wave} started with ${e.count} wolves.`);}

function sync(){player.position.set(state.player.position.x,state.player.position.y,state.player.position.z);const angle=Math.atan2(state.player.facing.x,state.player.facing.z);player.rotation.y=angle;
  companions.forEach((g,i)=>{const raw=g.userData.offset;const rotated=new THREE.Vector3(raw.x,0,raw.z).applyAxisAngle(Y,angle);const target=new THREE.Vector3(state.player.position.x+rotated.x,0,state.player.position.z+rotated.z);g.position.lerp(target,.075);g.rotation.y=angle;});
  for(const node of state.resources){const v=resources.get(node.id);if(v)v.visible=!node.depleted;}
  warmth.scale.setScalar(state.furnace.warmthRadius);furnaceVisual.scale.setScalar(1+(state.furnace.level-1)*.09);survivorVisual.visible=!state.survivor.rescued;helperVisual.visible=state.helper.active;if(state.helper.active)helperVisual.position.set(state.helper.position.x,0,state.helper.position.z);
  northVisual.scale.y=.15+state.defenses.north.progress*.85;northVisual.visible=!state.defenses.north.built||state.defenses.north.health>0;southVisual.scale.y=.15+state.defenses.south.progress*.85;
  const live=new Set();for(const e of state.waves.enemies){if(!e.alive)continue;live.add(e.id);let v=wolves.get(e.id);if(!v){v=wolfModel();scene.add(v);wolves.set(e.id,v);}v.position.set(e.position.x,0,e.position.z);}for(const [id,v] of [...wolves])if(!live.has(id)){scene.remove(v);wolves.delete(id);}rangeVisual();}

function updateCamera(dt){const focus=new THREE.Vector3(state.player.position.x,state.player.position.y+contract.camera.focusHeight,state.player.position.z);const vel=new THREE.Vector3(state.player.velocity.x,0,state.player.velocity.z);if(vel.lengthSq()>.001)focus.add(vel.normalize().multiplyScalar(contract.camera.lookAhead));const desired=focus.clone().add(new THREE.Vector3(...contract.camera.offset));camera.position.lerp(desired,1-Math.exp(-contract.camera.followSharpness*dt));camera.lookAt(focus);camera.updateMatrixWorld();}

function hud(){ui.carry.textContent=`${state.inventory.count} / ${state.inventory.capacity}`;ui.furnace.textContent=`L${state.furnace.level} · ${Math.round(state.furnace.durability)}/${Math.round(state.furnace.maxDurability)}`;ui.crew.textContent=`${selectedLead} + ${state.companions.length} companions`;ui.action.textContent=state.action?.label||'None';ui.progress.style.width=`${Math.round((state.action?.progress||0)*100)}%`;ui.wood.textContent=state.inventory.countKind(ResourceKind.Wood);ui.stone.textContent=state.inventory.countKind(ResourceKind.Stone);ui.stored.textContent=`${state.furnace.stored.wood} / ${state.furnace.stored.stone}`;ui.warmth.textContent=`${state.furnace.warmthRadius.toFixed(1)} m`;ui.rescue.textContent=state.survivor.rescued?'Rescued':state.furnace.level>=2?'Ready':'Needs furnace L2';ui.wave.textContent=state.waves.active?`Wave ${state.waves.waveNumber} · ${state.waves.enemies.filter(e=>e.alive).length} wolves`:state.waves.unlocked?`${state.waves.timer.toFixed(0)}s`:'Locked';ui.objective.textContent=objective();}
function objective(){if(state.furnace.level<2)return`Feed furnace: ${state.furnace.stored.wood}/18 wood · ${state.furnace.stored.stone}/6 stone`;if(!state.survivor.rescued)return'Reach frozen survivor — rescue is automatic';if(!state.defenses.north.built)return`Build north barricade: ${state.defenses.north.delivered.wood}/8 wood · ${state.defenses.north.delivered.stone}/3 stone`;if(state.waves.active)return`Defend outpost — ${state.waves.enemies.filter(e=>e.alive).length} wolves remain`;return`Prepare for pressure — next wave ${state.waves.timer.toFixed(0)}s`;}

function rangeVisual(){rangeRing.visible=ui.showRanges.checked;if(!rangeRing.visible)return;rangeRing.position.x=state.player.position.x;rangeRing.position.z=state.player.position.z;const r=state.action.kind===AutoActionKind.Enemy?contract.player.combatRadius:state.action.kind===AutoActionKind.Rescue?contract.player.rescueRadius:state.action.kind===AutoActionKind.Barricade?contract.player.buildRadius:[AutoActionKind.FurnaceDeposit,AutoActionKind.FurnaceRepair].includes(state.action.kind)?contract.player.depositRadius:contract.player.interactionRadius;rangeRing.scale.setScalar(r);}

async function loadLead(file){try{const gltf=await new Promise((resolve,reject)=>loader.parse(await file.arrayBuffer(),'',resolve,reject));if(loadedLead)player.remove(loadedLead);proxy.visible=false;loadedLead=gltf.scene;normalize(loadedLead,1.78);player.add(loadedLead);log(`Loaded ${file.name} as ${selectedLead} visual.`);}catch(e){console.error(e);log(`GLB rejected: ${e.message||e}`);}}
function normalize(root,height){root.updateMatrixWorld(true);let b=new THREE.Box3().setFromObject(root),s=b.getSize(new THREE.Vector3());root.scale.multiplyScalar(s.y>.001?height/s.y:1);root.updateMatrixWorld(true);b=new THREE.Box3().setFromObject(root);const c=b.getCenter(new THREE.Vector3());root.position.x-=c.x;root.position.z-=c.z;root.position.y-=b.min.y;root.traverse(o=>{if(o.isMesh){o.castShadow=true;o.receiveShadow=true;}});}
function teleport(t){if(!t)return;state.player.position.x=t.x;state.player.position.z=t.z+.6;state.player.velocity.x=state.player.velocity.z=0;log(`Debug teleport near ${t.x.toFixed(1)}, ${t.z.toFixed(1)}.`);}
function debugWolf(){const w=contract.openingLoopTuning.wolf,id=`debug-wolf-${Date.now()}`;state.waves.enemies.push({id,health:w.health,alive:true,position:{x:state.player.position.x+1.5,y:0,z:state.player.position.z-1.5},hitCooldown:0,attackCooldown:0});state.waves.active=true;log('Spawned one debug wolf near lead.');}
function runQa(){const r=runDeterministicContractQA(contract);log(r.passed?'CONTRACT QA PASSED.':'CONTRACT QA FAILED.');r.checks.forEach(c=>log(`${c.pass?'✓':'✗'} ${c.id}: ${c.details}`));}
function frame(now){requestAnimationFrame(frame);const dt=Math.min(clock.getDelta(),.05);simulate(dt);sync();updateCamera(dt);hud();renderer.render(scene,camera);const elapsed=Math.max(1,now-last);fps=fps*.92+(1000/elapsed)*.08;last=now;ui.perf.textContent=`${Math.round(fps)} FPS · ${renderer.info.render.calls} calls · ${fmt(renderer.info.render.triangles)} tris`;}
function resize(){if(!renderer)return;const r=ui.stage.getBoundingClientRect(),w=Math.max(1,r.width),h=Math.max(1,r.height);renderer.setSize(w,h,false);const a=w/h,s=contract.camera.size;camera.left=-s*a;camera.right=s*a;camera.top=s;camera.bottom=-s;camera.updateProjectionMatrix();}

function createBounds(){const x=contract.world.boundX,z=contract.world.boundZ,p=[new THREE.Vector3(-x,.04,-z),new THREE.Vector3(x,.04,-z),new THREE.Vector3(x,.04,z),new THREE.Vector3(-x,.04,z),new THREE.Vector3(-x,.04,-z)];return new THREE.Line(new THREE.BufferGeometry().setFromPoints(p),new THREE.LineBasicMaterial({color:0x68a9d5,transparent:true,opacity:.35}));}
function actor(jacket,accent,scale=1){const g=new THREE.Group(),body=new THREE.Mesh(new THREE.CapsuleGeometry(.32,.74,6,12),mat(jacket,.78)),head=new THREE.Mesh(new THREE.SphereGeometry(.29,18,12),mat(0x8f5f45,.86)),pack=new THREE.Mesh(new THREE.BoxGeometry(.55,.62,.23),mat(accent,.72));body.position.y=.82;head.position.y=1.57;pack.position.set(0,.94,.31);g.add(body,head,pack);g.scale.setScalar(scale);return g;}
function furnaceModel(){const g=new THREE.Group(),body=new THREE.Mesh(new THREE.CylinderGeometry(.72,.82,1.5,20),mat(0x39434a,.58,.18)),fire=new THREE.Mesh(new THREE.SphereGeometry(.33,16,12),new THREE.MeshBasicMaterial({color:0xff7a22}));body.position.y=.78;fire.position.set(0,.65,.68);fire.scale.y=1.35;g.add(body,fire);return g;}
function defense(){const g=new THREE.Group(),m=mat(0x6f4e32,.9);for(let i=0;i<5;i++){const l=new THREE.Mesh(new THREE.BoxGeometry(1.05,.24,.32),m);l.position.set((i-2)*.82,.18+(i%2)*.18,0);g.add(l);}g.scale.y=.15;return g;}
function resource(k){if(k===ResourceKind.Wood){const g=new THREE.Group(),trunk=new THREE.Mesh(new THREE.CylinderGeometry(.18,.24,1.4,10),mat(0x8b5a35,.92)),crown=new THREE.Mesh(new THREE.ConeGeometry(.95,2.2,10),mat(0x254b42,.92));trunk.position.y=.7;crown.position.y=2;g.add(trunk,crown);return g;}const color=k===ResourceKind.Stone?0x89959e:k===ResourceKind.Metal?0x687784:0x9b7137,m=new THREE.Mesh(k===ResourceKind.Stone?new THREE.DodecahedronGeometry(.55):new THREE.BoxGeometry(.75,.55,.65),mat(color,.75,k===ResourceKind.Metal?.35:0));m.position.y=.38;return m;}
function tent(p){const t=new THREE.Mesh(new THREE.ConeGeometry(1.8,2.3,4),mat(0x355c79,.9));t.rotation.y=Math.PI/4;t.position.y=1.15;t.scale.z=1.4;place(t,p);scene.add(t);}
function boxProp(p,x,y,z,c){const m=new THREE.Mesh(new THREE.BoxGeometry(x,y,z),mat(c,.9));m.position.y=y/2;place(m,p);scene.add(m);}
function campfire(p){const f=new THREE.Mesh(new THREE.ConeGeometry(.28,.9,12),new THREE.MeshBasicMaterial({color:0xff8b32}));f.position.y=.5;place(f,p);scene.add(f);}
function wolfModel(){const g=new THREE.Group(),m=mat(0x913e47,.78),body=new THREE.Mesh(new THREE.CapsuleGeometry(.27,.65,5,10),m),head=new THREE.Mesh(new THREE.SphereGeometry(.28,12,8),m);body.rotation.z=Math.PI/2;body.position.y=.45;head.position.set(.5,.55,0);g.add(body,head);g.scale.setScalar(.9);return g;}
function mat(color,rough=.8,metal=0){return new THREE.MeshStandardMaterial({color,roughness:rough,metalness:metal});}
function place(o,p){o.position.set(p[0],p[1]||0,p[2]);}
function log(message){const li=document.createElement('li');li.textContent=message;ui.log.prepend(li);while(ui.log.children.length>18)ui.log.lastElementChild.remove();}
function fmt(v){return new Intl.NumberFormat().format(Math.round(v||0));}function cap(v){return v?v[0].toUpperCase()+v.slice(1):v;}function escapeHtml(v){return String(v).replace(/[&<>'"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));}
