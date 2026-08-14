export const ResourceKind = Object.freeze({
  Wood: 'wood',
  Stone: 'stone',
  Metal: 'metal',
  Fuel: 'fuel'
});

export const AutoActionKind = Object.freeze({
  None: 'none',
  Enemy: 'enemy',
  FurnaceRepair: 'furnace-repair',
  FurnaceDeposit: 'furnace-deposit',
  Rescue: 'rescue',
  Barricade: 'barricade',
  Resource: 'resource'
});

export const AUTO_ACTION_PRIORITY = Object.freeze({
  [AutoActionKind.Enemy]: 200,
  [AutoActionKind.FurnaceRepair]: 130,
  [AutoActionKind.Rescue]: 110,
  [AutoActionKind.FurnaceDeposit]: 95,
  [AutoActionKind.Barricade]: 70,
  [AutoActionKind.Resource]: 30,
  [AutoActionKind.None]: 0
});

const EPS = 1e-6;

export function vec3(value) {
  if (!Array.isArray(value) || value.length !== 3 || value.some((part) => !Number.isFinite(part))) {
    throw new Error(`Expected a numeric vec3, received ${JSON.stringify(value)}`);
  }
  return { x: value[0], y: value[1], z: value[2] };
}

export function distance2D(a, b) {
  const dx = a.x - b.x;
  const dz = a.z - b.z;
  return Math.hypot(dx, dz);
}

export function clampPosition(position, contract) {
  return {
    x: Math.max(-contract.world.boundX, Math.min(contract.world.boundX, position.x)),
    y: position.y,
    z: Math.max(-contract.world.boundZ, Math.min(contract.world.boundZ, position.z))
  };
}

export function companionIdsFor(selectedLead) {
  if (selectedLead === 'Character1') return ['Character2', 'Character3', 'Character4'];
  if (selectedLead === 'Character2') return ['Character1', 'Character3', 'Character4'];
  throw new Error(`Invalid starting lead: ${selectedLead}`);
}

export function gatherSecondsFor(contract, kind) {
  const value = contract.openingLoopTuning?.gatherSecondsPerUnit?.[kind];
  if (!Number.isFinite(value) || value <= 0) throw new Error(`No valid gather timing for ${kind}`);
  return value;
}

export class Inventory {
  constructor(capacity) {
    if (!Number.isInteger(capacity) || capacity <= 0) throw new Error('Inventory capacity must be a positive integer.');
    this.capacity = capacity;
    this.items = [];
  }

  get count() { return this.items.length; }
  get full() { return this.count >= this.capacity; }
  get empty() { return this.count === 0; }
  countKind(kind) { return this.items.filter((item) => item === kind).length; }

  add(kind) {
    if (this.full) return false;
    this.items.push(kind);
    return true;
  }

  removeFirst(kind = null) {
    if (this.empty) return null;
    if (kind == null) return this.items.shift() ?? null;
    const index = this.items.indexOf(kind);
    if (index < 0) return null;
    return this.items.splice(index, 1)[0] ?? null;
  }

  clear() {
    const result = [...this.items];
    this.items.length = 0;
    return result;
  }
}

export class ResourceNodeState {
  constructor(id, kind, position, units, secondsPerUnit) {
    this.id = id;
    this.kind = kind;
    this.position = position;
    this.unitsRemaining = units;
    this.secondsPerUnit = secondsPerUnit;
    this.progressSeconds = 0;
  }

  get depleted() { return this.unitsRemaining <= 0; }

  stepGather(dt, inventory) {
    if (this.depleted || inventory.full) {
      this.progressSeconds = 0;
      return 0;
    }
    this.progressSeconds += Math.max(0, dt);
    let gathered = 0;
    while (this.progressSeconds + EPS >= this.secondsPerUnit && !this.depleted && !inventory.full) {
      this.progressSeconds -= this.secondsPerUnit;
      if (!inventory.add(this.kind)) break;
      this.unitsRemaining -= 1;
      gathered += 1;
    }
    return gathered;
  }
}

export class FurnaceState {
  constructor(contract) {
    this.contract = contract;
    this.maxDurability = contract.openingLoopTuning.furnaceMaxDurability;
    this.durability = this.maxDurability;
    this.stored = { wood: 0, stone: 0, metal: 0, fuel: 0 };
    this.level = 1;
    this.depositProgressSeconds = 0;
    this.repairProgressSeconds = 0;
  }

  get operational() { return this.durability > 0; }
  get warmthRadius() {
    const t = this.contract.openingLoopTuning;
    return t.warmthRadiusLevel1 + (this.level - 1) * t.warmthRadiusPerAdditionalLevel;
  }

  recomputeLevel() {
    const t = this.contract.openingLoopTuning;
    let next = 1;
    if (meets(this.stored, t.furnaceLevel2)) next = 2;
    if (meets(this.stored, t.furnaceLevel3)) next = 3;
    if (meets(this.stored, t.furnaceLevel4)) next = 4;
    const changed = next !== this.level;
    this.level = next;
    return changed;
  }

  stepDeposit(dt, inventory) {
    if (inventory.empty) {
      this.depositProgressSeconds = 0;
      return { deposited: null, levelChanged: false };
    }
    this.depositProgressSeconds += Math.max(0, dt);
    const seconds = this.contract.openingLoopTuning.furnaceDepositSecondsPerUnit;
    if (this.depositProgressSeconds + EPS < seconds) return { deposited: null, levelChanged: false };
    this.depositProgressSeconds -= seconds;
    const item = inventory.removeFirst();
    if (item == null) return { deposited: null, levelChanged: false };
    this.stored[item] = (this.stored[item] ?? 0) + 1;
    return { deposited: item, levelChanged: this.recomputeLevel() };
  }

  stepRepair(dt, inventory) {
    if (this.durability >= this.maxDurability || inventory.countKind(ResourceKind.Wood) <= 0) {
      this.repairProgressSeconds = 0;
      return false;
    }
    this.repairProgressSeconds += Math.max(0, dt);
    if (this.repairProgressSeconds + EPS < this.contract.openingLoopTuning.furnaceRepairSecondsPerUnit) return false;
    this.repairProgressSeconds = 0;
    if (inventory.removeFirst(ResourceKind.Wood) == null) return false;
    this.durability = Math.min(this.maxDurability, this.durability + this.contract.openingLoopTuning.furnaceRepairPerWood);
    return true;
  }
}

export class ConstructionSiteState {
  constructor(id, position, requirement) {
    this.id = id;
    this.position = position;
    this.requirement = { wood: requirement.wood ?? 0, stone: requirement.stone ?? 0, metal: requirement.metal ?? 0 };
    this.delivered = { wood: 0, stone: 0, metal: 0 };
    this.built = false;
    this.health = 0;
    this.maxHealth = 160;
  }

  get progress() {
    const required = this.requirement.wood + this.requirement.stone + this.requirement.metal;
    const delivered = this.delivered.wood + this.delivered.stone + this.delivered.metal;
    return required <= 0 ? 1 : Math.min(1, delivered / required);
  }

  canAccept(kind) {
    return !this.built && (this.delivered[kind] ?? 0) < (this.requirement[kind] ?? 0);
  }

  deliverOne(inventory) {
    for (const kind of [ResourceKind.Wood, ResourceKind.Stone, ResourceKind.Metal]) {
      if (!this.canAccept(kind)) continue;
      if (inventory.removeFirst(kind) == null) continue;
      this.delivered[kind] += 1;
      if (meets(this.delivered, this.requirement)) {
        this.built = true;
        this.health = this.maxHealth;
      }
      return kind;
    }
    return null;
  }
}

export function meets(have, requirement) {
  if (!requirement) return false;
  return (have.wood ?? 0) >= (requirement.wood ?? 0) &&
    (have.stone ?? 0) >= (requirement.stone ?? 0) &&
    (have.metal ?? 0) >= (requirement.metal ?? 0) &&
    (have.fuel ?? 0) >= (requirement.fuel ?? 0);
}

export function createInitialState(contract, selectedLead = 'Character1') {
  validateContract(contract);
  const inventory = new Inventory(contract.player.carryCapacity);
  const nodes = [];
  contract.world.woodNodes.forEach((p, i) => nodes.push(new ResourceNodeState(
    `wood-${i + 1}`, ResourceKind.Wood, vec3(p), contract.openingLoopTuning.woodUnitsPerNode, gatherSecondsFor(contract, ResourceKind.Wood))));
  contract.world.stoneNodes.forEach((p, i) => nodes.push(new ResourceNodeState(
    `stone-${i + 1}`, ResourceKind.Stone, vec3(p), contract.openingLoopTuning.stoneUnitsPerNode, gatherSecondsFor(contract, ResourceKind.Stone))));
  nodes.push(new ResourceNodeState('metal-1', ResourceKind.Metal, vec3(contract.world.metalNode), contract.openingLoopTuning.metalUnitsPerNode, gatherSecondsFor(contract, ResourceKind.Metal)));
  nodes.push(new ResourceNodeState('fuel-1', ResourceKind.Fuel, vec3(contract.world.fuelNode), contract.openingLoopTuning.fuelUnitsPerNode, gatherSecondsFor(contract, ResourceKind.Fuel)));

  return {
    contract,
    selectedLead,
    companions: companionIdsFor(selectedLead),
    player: {
      position: vec3(contract.player.spawn),
      velocity: { x: 0, y: 0, z: 0 },
      facing: { x: 0, z: -1 },
      sprinting: false,
      health: 100
    },
    inventory,
    resources: nodes,
    furnace: new FurnaceState(contract),
    survivor: { position: vec3(contract.world.survivor), rescued: false, rescueProgressSeconds: 0 },
    defenses: {
      north: new ConstructionSiteState('north', vec3(contract.world.northBarricade), contract.openingLoopTuning.northBarricadeBuild),
      south: new ConstructionSiteState('south', vec3(contract.world.southBarricade), contract.openingLoopTuning.southBarricadeBuild)
    },
    waves: {
      unlocked: false,
      active: false,
      waveNumber: 0,
      completedWaves: 0,
      timer: contract.openingLoopTuning.firstWaveDelaySeconds,
      enemies: []
    },
    helper: { active: false, state: 'trapped', position: vec3(contract.world.survivor) },
    action: { kind: AutoActionKind.None, id: null, progress: 0, label: 'None' },
    elapsedSeconds: 0,
    eventSequence: 0
  };
}

export function movePlayer(state, input, dt) {
  const contract = state.contract;
  const x = Math.max(-1, Math.min(1, input.x ?? 0));
  const z = Math.max(-1, Math.min(1, input.z ?? 0));
  const magnitude = Math.hypot(x, z);
  const normalized = magnitude > 1 ? { x: x / magnitude, z: z / magnitude } : { x, z };
  const moving = Math.hypot(normalized.x, normalized.z) > 0.001;
  const speed = input.sprint ? contract.player.runSpeed : contract.player.walkSpeed;
  const target = { x: normalized.x * speed, z: normalized.z * speed };
  const rate = moving ? contract.player.acceleration : contract.player.deceleration;
  state.player.velocity.x = moveToward(state.player.velocity.x, target.x, rate * dt);
  state.player.velocity.z = moveToward(state.player.velocity.z, target.z, rate * dt);
  state.player.position = clampPosition({
    x: state.player.position.x + state.player.velocity.x * dt,
    y: state.player.position.y,
    z: state.player.position.z + state.player.velocity.z * dt
  }, contract);
  if (moving) {
    const len = Math.hypot(normalized.x, normalized.z) || 1;
    state.player.facing = { x: normalized.x / len, z: normalized.z / len };
  }
  state.player.sprinting = !!input.sprint;
  return moving;
}

function moveToward(current, target, maxDelta) {
  if (Math.abs(target - current) <= maxDelta) return target;
  return current + Math.sign(target - current) * maxDelta;
}

export function actionCandidates(state) {
  const c = state.contract;
  const result = [];
  const player = state.player.position;

  for (const enemy of state.waves.enemies) {
    if (!enemy.alive) continue;
    pushCandidate(result, AutoActionKind.Enemy, enemy.id, enemy.position, c.player.combatRadius, player, state.player.facing, 'Fight wolf');
  }

  const furnacePosition = vec3(c.world.furnace);
  if (state.furnace.durability < state.furnace.maxDurability && state.inventory.countKind(ResourceKind.Wood) > 0) {
    pushCandidate(result, AutoActionKind.FurnaceRepair, 'furnace', furnacePosition, c.player.depositRadius, player, state.player.facing, 'Repair furnace');
  }
  if (!state.inventory.empty) {
    pushCandidate(result, AutoActionKind.FurnaceDeposit, 'furnace', furnacePosition, c.player.depositRadius, player, state.player.facing, 'Feed furnace');
  }

  if (!state.survivor.rescued && state.furnace.operational && state.furnace.level >= 2) {
    pushCandidate(result, AutoActionKind.Rescue, 'survivor', state.survivor.position, c.player.rescueRadius, player, state.player.facing, 'Rescue survivor');
  }

  for (const site of [state.defenses.north, state.defenses.south]) {
    if (!site.built && canSiteAcceptInventory(site, state.inventory)) {
      pushCandidate(result, AutoActionKind.Barricade, site.id, site.position, c.player.buildRadius, player, state.player.facing, `Build ${site.id} barricade`);
    }
  }

  if (!state.inventory.full) {
    for (const node of state.resources) {
      if (!node.depleted) pushCandidate(result, AutoActionKind.Resource, node.id, node.position, c.player.interactionRadius, player, state.player.facing, `Gather ${node.kind}`);
    }
  }

  return result.sort((a, b) => b.score - a.score || a.distance - b.distance || a.id.localeCompare(b.id));
}

function pushCandidate(list, kind, id, position, radius, player, facing, label) {
  const distance = distance2D(player, position);
  if (distance > radius) return;
  const dx = position.x - player.x;
  const dz = position.z - player.z;
  const len = Math.hypot(dx, dz) || 1;
  const dot = Math.max(-1, Math.min(1, (dx / len) * facing.x + (dz / len) * facing.z));
  const facing01 = (dot + 1) * 0.5;
  const score = AUTO_ACTION_PRIORITY[kind] * 10 - distance + facing01 * 0.35;
  list.push({ kind, id, position, radius, distance, score, label });
}

function canSiteAcceptInventory(site, inventory) {
  return [ResourceKind.Wood, ResourceKind.Stone, ResourceKind.Metal].some((kind) => site.canAccept(kind) && inventory.countKind(kind) > 0);
}

export function chooseAutoAction(state, previous = state.action) {
  const candidates = actionCandidates(state);
  if (!candidates.length) return { kind: AutoActionKind.None, id: null, label: 'None', progress: 0 };
  const best = candidates[0];
  if (previous?.id) {
    const current = candidates.find((candidate) => candidate.id === previous.id && candidate.kind === previous.kind);
    if (current && current.score + state.contract.openingLoopTuning.automaticActionTargetHysteresis >= best.score) return { ...current, progress: previous.progress ?? 0 };
  }
  return { ...best, progress: 0 };
}

export function stepAutoAction(state, dt) {
  const action = state.action;
  const t = state.contract.openingLoopTuning;
  if (!action || action.kind === AutoActionKind.None) return null;

  if (action.kind === AutoActionKind.Resource) {
    const node = state.resources.find((candidate) => candidate.id === action.id);
    if (!node) return null;
    const before = node.unitsRemaining;
    const gathered = node.stepGather(dt, state.inventory);
    action.progress = Math.min(1, node.progressSeconds / node.secondsPerUnit);
    return gathered > 0 ? { type: 'gather', kind: node.kind, amount: before - node.unitsRemaining } : null;
  }

  if (action.kind === AutoActionKind.FurnaceDeposit) {
    state.furnace.depositProgressSeconds += 0;
    const result = state.furnace.stepDeposit(dt, state.inventory);
    action.progress = Math.min(1, state.furnace.depositProgressSeconds / t.furnaceDepositSecondsPerUnit);
    return result.deposited ? { type: 'deposit', kind: result.deposited, levelChanged: result.levelChanged, level: state.furnace.level } : null;
  }

  if (action.kind === AutoActionKind.FurnaceRepair) {
    const repaired = state.furnace.stepRepair(dt, state.inventory);
    action.progress = Math.min(1, state.furnace.repairProgressSeconds / t.furnaceRepairSecondsPerUnit);
    return repaired ? { type: 'furnace-repair', durability: state.furnace.durability } : null;
  }

  if (action.kind === AutoActionKind.Rescue) {
    state.survivor.rescueProgressSeconds += Math.max(0, dt);
    action.progress = Math.min(1, state.survivor.rescueProgressSeconds / t.survivorRescueSeconds);
    if (state.survivor.rescueProgressSeconds + EPS >= t.survivorRescueSeconds) {
      state.survivor.rescued = true;
      state.helper.active = true;
      state.helper.state = 'following';
      action.progress = 1;
      return { type: 'rescue' };
    }
    return null;
  }

  if (action.kind === AutoActionKind.Barricade) {
    const site = state.defenses[action.id];
    if (!site) return null;
    const delivered = site.deliverOne(state.inventory);
    action.progress = site.progress;
    return delivered ? { type: 'build', site: action.id, kind: delivered, built: site.built, progress: site.progress } : null;
  }

  if (action.kind === AutoActionKind.Enemy) {
    const enemy = state.waves.enemies.find((candidate) => candidate.id === action.id && candidate.alive);
    if (!enemy) return null;
    enemy.hitCooldown = (enemy.hitCooldown ?? 0) - dt;
    if (enemy.hitCooldown > 0) return null;
    enemy.hitCooldown = 0.64;
    enemy.health -= 22;
    if (enemy.health <= 0) enemy.alive = false;
    return { type: 'enemy-hit', id: enemy.id, alive: enemy.alive, health: Math.max(0, enemy.health) };
  }

  return null;
}

export function updateWaveGate(state, dt) {
  const t = state.contract.openingLoopTuning;
  state.waves.unlocked = state.furnace.level >= 2 && state.survivor.rescued && state.defenses.north.built;
  if (!state.waves.unlocked || state.waves.active) return null;
  state.waves.timer = Math.max(0, state.waves.timer - dt);
  if (state.waves.timer > 0) return null;

  state.waves.waveNumber += 1;
  state.waves.active = true;
  const count = 2 + state.waves.waveNumber;
  state.waves.enemies = Array.from({ length: count }, (_, index) => ({
    id: `wave-${state.waves.waveNumber}-wolf-${index + 1}`,
    health: 65,
    alive: true,
    position: { x: (index - (count - 1) * 0.5) * 1.8, y: 0, z: index % 2 === 0 ? -15.2 : 15.2 },
    hitCooldown: 0
  }));
  return { type: 'wave-start', wave: state.waves.waveNumber, count };
}

export function completeWaveIfClear(state) {
  if (!state.waves.active || state.waves.enemies.some((enemy) => enemy.alive)) return false;
  state.waves.active = false;
  state.waves.completedWaves += 1;
  const t = state.contract.openingLoopTuning;
  state.waves.timer = Math.max(t.minimumWaveDelaySeconds, t.firstWaveDelaySeconds - state.waves.completedWaves * t.waveDelayReductionPerCompletedWave);
  return true;
}

export function validateContract(contract) {
  const failures = [];
  if (!contract || typeof contract !== 'object') return ['Contract is missing or not an object.'];
  if (contract.contractVersion !== '1.3.0') failures.push(`Expected contractVersion 1.3.0; got ${contract.contractVersion ?? '<missing>'}.`);
  if (contract.product !== 'HAVENLINE') failures.push('Contract product must be HAVENLINE.');
  if (contract.camera?.projection !== 'orthographic') failures.push('Shipping camera must remain orthographic.');
  if (!Number.isFinite(contract.camera?.size) || contract.camera.size <= 0) failures.push('Camera size is invalid.');
  if (!Number.isFinite(contract.player?.carryCapacity) || contract.player.carryCapacity !== 8) failures.push('Carry capacity must be 8.');
  if (contract.characterSystem?.activeCrewSize !== 4) failures.push('Active core crew size must be 4.');
  if (JSON.stringify(contract.characterSystem?.startingPlayableLeads) !== JSON.stringify(['Character1', 'Character2'])) failures.push('Starting leads must be Character1 and Character2.');
  if (!Array.isArray(contract.characterSystem?.companionFormationOffsets) || contract.characterSystem.companionFormationOffsets.length !== 3) failures.push('Three companion formation offsets are required.');
  if (!Array.isArray(contract.world?.woodNodes) || contract.world.woodNodes.length !== 6) failures.push('Six wood nodes are required.');
  if (!Array.isArray(contract.world?.stoneNodes) || contract.world.stoneNodes.length !== 4) failures.push('Four stone nodes are required.');
  if ((contract.openingLoopTuning?.furnaceLevel2?.wood ?? 0) !== 18 || (contract.openingLoopTuning?.furnaceLevel2?.stone ?? 0) !== 6) failures.push('Furnace Level 2 must require 18 wood and 6 stone.');
  if ((contract.openingLoopTuning?.northBarricadeBuild?.wood ?? 0) !== 8 || (contract.openingLoopTuning?.northBarricadeBuild?.stone ?? 0) !== 3) failures.push('North barricade must require 8 wood and 3 stone.');
  if (contract.openingLoopTuning?.firstWaveEnemyCount !== 3) failures.push('First wave must contain three wolves.');
  for (const kind of Object.values(ResourceKind)) {
    try { gatherSecondsFor(contract, kind); } catch (error) { failures.push(error.message); }
  }
  return failures;
}

export function runDeterministicContractQA(contract) {
  const checks = [];
  const push = (id, pass, details) => checks.push({ id, pass: !!pass, details });
  const contractFailures = validateContract(contract);
  push('contract-shape', contractFailures.length === 0, contractFailures.length ? contractFailures.join(' | ') : 'Contract v1.3 shape is valid.');
  if (contractFailures.length) return { passed: false, checks };

  push('crew-c1', JSON.stringify(companionIdsFor('Character1')) === JSON.stringify(['Character2','Character3','Character4']), 'C1 lead produces C2/C3/C4 companion crew.');
  push('crew-c2', JSON.stringify(companionIdsFor('Character2')) === JSON.stringify(['Character1','Character3','Character4']), 'C2 lead produces C1/C3/C4 companion crew.');

  const clamped = clampPosition({ x: 999, y: contract.player.spawn[1], z: -999 }, contract);
  push('world-clamp', clamped.x === contract.world.boundX && clamped.z === -contract.world.boundZ, `Clamp resolved to (${clamped.x}, ${clamped.z}).`);

  const furnace = new FurnaceState(contract);
  furnace.stored.wood = 18;
  furnace.stored.stone = 5;
  furnace.recomputeLevel();
  push('furnace-does-not-upgrade-early', furnace.level === 1, `Level with 18 wood / 5 stone = ${furnace.level}.`);
  furnace.stored.stone = 6;
  furnace.recomputeLevel();
  push('furnace-level2-threshold', furnace.level === 2, `Level with 18 wood / 6 stone = ${furnace.level}.`);
  push('warmth-expands', Math.abs(furnace.warmthRadius - 8.0) < EPS, `Level 2 warmth radius = ${furnace.warmthRadius.toFixed(2)}.`);

  const state = createInitialState(contract, 'Character1');
  state.furnace.stored.wood = 18;
  state.furnace.stored.stone = 6;
  state.furnace.recomputeLevel();
  state.survivor.rescued = true;
  state.helper.active = true;
  state.inventory.items.push(...Array(8).fill(ResourceKind.Wood));
  for (let i = 0; i < 8; i++) state.defenses.north.deliverOne(state.inventory);
  state.inventory.items.push(...Array(3).fill(ResourceKind.Stone));
  for (let i = 0; i < 3; i++) state.defenses.north.deliverOne(state.inventory);
  push('north-defense-build', state.defenses.north.built, `North defense progress = ${(state.defenses.north.progress * 100).toFixed(0)}%.`);
  state.waves.timer = 0;
  const waveEvent = updateWaveGate(state, 0.1);
  push('first-wave-gate', state.waves.unlocked && waveEvent?.count === 3, waveEvent ? `Wave ${waveEvent.wave} spawned ${waveEvent.count} wolves.` : 'Wave failed to start.');

  const locked = createInitialState(contract, 'Character1');
  locked.waves.timer = 0;
  const lockedEvent = updateWaveGate(locked, 1);
  push('wave-remains-locked', lockedEvent == null && !locked.waves.unlocked, 'Wave stays locked before furnace L2 + rescue + north defense.');

  const inventory = new Inventory(contract.player.carryCapacity);
  for (let i = 0; i < contract.player.carryCapacity + 2; i++) inventory.add(ResourceKind.Wood);
  push('carry-capacity', inventory.count === 8, `Inventory stopped at ${inventory.count}/${inventory.capacity}.`);

  return { passed: checks.every((check) => check.pass), checks };
}
