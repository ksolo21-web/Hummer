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

function priorityFor(contract, kind) {
  const p = contract.openingLoopTuning.automaticActionPriorities;
  switch (kind) {
    case AutoActionKind.Enemy: return p.enemy;
    case AutoActionKind.FurnaceRepair: return p.furnaceRepair;
    case AutoActionKind.Rescue: return p.rescue;
    case AutoActionKind.FurnaceDeposit: return p.furnaceDeposit;
    case AutoActionKind.Barricade: return p.construction;
    case AutoActionKind.Resource: return p.resource;
    default: return 0;
  }
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
    if (this.depleted || inventory.full) return 0;
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
    if (inventory.empty || !this.operational) return { deposited: null, levelChanged: false };
    this.depositProgressSeconds += Math.max(0, dt);
    const seconds = this.contract.openingLoopTuning.furnaceDepositSecondsPerUnit;
    if (this.depositProgressSeconds + EPS < seconds) return { deposited: null, levelChanged: false };
    this.depositProgressSeconds = 0;
    const item = inventory.removeFirst();
    if (item == null) return { deposited: null, levelChanged: false };
    this.stored[item] = (this.stored[item] ?? 0) + 1;
    return { deposited: item, levelChanged: this.recomputeLevel() };
  }

  stepRepair(dt, inventory) {
    if (this.durability >= this.maxDurability || inventory.countKind(ResourceKind.Wood) <= 0) return false;
    this.repairProgressSeconds += Math.max(0, dt);
    if (this.repairProgressSeconds + EPS < this.contract.openingLoopTuning.furnaceRepairSecondsPerUnit) return false;
    this.repairProgressSeconds = 0;
    if (inventory.removeFirst(ResourceKind.Wood) == null) return false;
    this.durability = Math.min(this.maxDurability, this.durability + this.contract.openingLoopTuning.furnaceRepairPerWood);
    return true;
  }
}

export class ConstructionSiteState {
  constructor(id, position, requirement, secondsPerUnit) {
    this.id = id;
    this.position = position;
    this.requirement = { wood: requirement.wood ?? 0, stone: requirement.stone ?? 0, metal: requirement.metal ?? 0 };
    this.delivered = { wood: 0, stone: 0, metal: 0 };
    this.secondsPerUnit = secondsPerUnit;
    this.progressSeconds = 0;
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

  stepContribute(dt, inventory) {
    if (this.built || !canSiteAcceptInventory(this, inventory)) return null;
    this.progressSeconds += Math.max(0, dt);
    if (this.progressSeconds + EPS < this.secondsPerUnit) return null;
    this.progressSeconds = 0;
    return this.deliverOne(inventory);
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
  const failures = validateContract(contract);
  if (failures.length) throw new Error(failures.join(' | '));
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
      north: new ConstructionSiteState('north', vec3(contract.world.northBarricade), contract.openingLoopTuning.northBarricadeBuild, contract.openingLoopTuning.playerConstructionSecondsPerUnit),
      south: new ConstructionSiteState('south', vec3(contract.world.southBarricade), contract.openingLoopTuning.southBarricadeBuild, contract.openingLoopTuning.playerConstructionSecondsPerUnit)
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
    elapsedSeconds: 0
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
  const result = [];
  for (const enemy of state.waves.enemies) {
    if (enemy.alive) addCandidate(state, result, AutoActionKind.Enemy, enemy.id, enemy.position, state.contract.player.combatRadius, 'Fight wolf', false);
  }

  const furnacePosition = vec3(state.contract.world.furnace);
  if (state.furnace.durability < state.furnace.maxDurability && state.inventory.countKind(ResourceKind.Wood) > 0) {
    addCandidate(state, result, AutoActionKind.FurnaceRepair, 'furnace', furnacePosition, state.contract.player.depositRadius, 'Repair furnace', false);
  } else if (!state.inventory.empty && state.furnace.operational) {
    addCandidate(state, result, AutoActionKind.FurnaceDeposit, 'furnace', furnacePosition, state.contract.player.depositRadius, 'Feed furnace', false);
  }

  if (!state.survivor.rescued && state.furnace.operational && state.furnace.level >= 2) {
    addCandidate(state, result, AutoActionKind.Rescue, 'survivor', state.survivor.position, state.contract.player.rescueRadius, 'Rescue survivor', false);
  }

  for (const site of [state.defenses.north, state.defenses.south]) {
    if (!site.built && canSiteAcceptInventory(site, state.inventory)) {
      addCandidate(state, result, AutoActionKind.Barricade, site.id, site.position, state.contract.player.buildRadius, `Build ${site.id} barricade`, false);
    }
  }

  if (!state.inventory.full) {
    for (const node of state.resources) {
      if (!node.depleted) addCandidate(state, result, AutoActionKind.Resource, node.id, node.position, state.contract.player.interactionRadius, `Gather ${node.kind}`, false);
    }
  }

  return result.sort((a, b) => b.score - a.score || a.distance - b.distance || a.id.localeCompare(b.id));
}

function addCandidate(state, destination, kind, id, position, radius, label, allowHysteresis) {
  const distance = distance2D(state.player.position, position);
  const range = radius + (allowHysteresis ? state.contract.openingLoopTuning.automaticActionTargetHysteresis : 0);
  if (distance > range) return null;
  const dx = position.x - state.player.position.x;
  const dz = position.z - state.player.position.z;
  const len = Math.hypot(dx, dz);
  const direction = len > 0.001 ? { x: dx / len, z: dz / len } : state.player.facing;
  const dot = Math.max(-1, Math.min(1, direction.x * state.player.facing.x + direction.z * state.player.facing.z));
  const facing01 = (dot + 1) * 0.5;
  const distanceScore = 1 - Math.max(0, Math.min(1, distance / Math.max(0.01, radius)));
  const t = state.contract.openingLoopTuning;
  const score = priorityFor(state.contract, kind) + distanceScore * t.automaticActionDistanceScoreWeight + facing01 * t.automaticActionFacingWeight;
  const candidate = { kind, id, position, radius, distance, score, label, progress: 0 };
  destination?.push(candidate);
  return candidate;
}

function canContinuePrevious(state, previous) {
  if (!previous?.id || previous.kind === AutoActionKind.None) return null;
  const c = state.contract;
  if (previous.kind === AutoActionKind.Enemy) {
    const enemy = state.waves.enemies.find((item) => item.id === previous.id && item.alive);
    return enemy ? addCandidate(state, null, previous.kind, previous.id, enemy.position, c.player.combatRadius, previous.label || 'Fight wolf', true) : null;
  }
  if (previous.kind === AutoActionKind.FurnaceRepair) {
    if (!(state.furnace.durability < state.furnace.maxDurability && state.inventory.countKind(ResourceKind.Wood) > 0)) return null;
    return addCandidate(state, null, previous.kind, 'furnace', vec3(c.world.furnace), c.player.depositRadius, previous.label || 'Repair furnace', true);
  }
  if (previous.kind === AutoActionKind.FurnaceDeposit) {
    if (state.inventory.empty || !state.furnace.operational || state.furnace.durability < state.furnace.maxDurability) return null;
    return addCandidate(state, null, previous.kind, 'furnace', vec3(c.world.furnace), c.player.depositRadius, previous.label || 'Feed furnace', true);
  }
  if (previous.kind === AutoActionKind.Rescue) {
    if (state.survivor.rescued || state.survivor.rescueProgressSeconds >= c.openingLoopTuning.survivorRescueSeconds) return null;
    return addCandidate(state, null, previous.kind, 'survivor', state.survivor.position, c.player.rescueRadius, previous.label || 'Rescue survivor', true);
  }
  if (previous.kind === AutoActionKind.Barricade) {
    const site = state.defenses[previous.id];
    if (!site || site.built || !canSiteAcceptInventory(site, state.inventory)) return null;
    return addCandidate(state, null, previous.kind, previous.id, site.position, c.player.buildRadius, previous.label || `Build ${previous.id} barricade`, true);
  }
  if (previous.kind === AutoActionKind.Resource) {
    const node = state.resources.find((item) => item.id === previous.id);
    if (!node || node.depleted || state.inventory.full) return null;
    return addCandidate(state, null, previous.kind, previous.id, node.position, c.player.interactionRadius, previous.label || `Gather ${node.kind}`, true);
  }
  return null;
}

function canSiteAcceptInventory(site, inventory) {
  return [ResourceKind.Wood, ResourceKind.Stone, ResourceKind.Metal].some((kind) => site.canAccept(kind) && inventory.countKind(kind) > 0);
}

export function chooseAutoAction(state, previous = state.action) {
  const retained = canContinuePrevious(state, previous);
  if (retained) return { ...retained, progress: previous.progress ?? 0 };
  const candidates = actionCandidates(state);
  return candidates.length ? candidates[0] : { kind: AutoActionKind.None, id: null, label: 'None', progress: 0 };
}

export function stepAutoAction(state, dt) {
  const action = state.action;
  const t = state.contract.openingLoopTuning;
  if (!action || action.kind === AutoActionKind.None) return null;

  if (action.kind === AutoActionKind.Resource) {
    const node = state.resources.find((candidate) => candidate.id === action.id);
    if (!node) return null;
    const gathered = node.stepGather(dt, state.inventory);
    action.progress = Math.min(1, node.progressSeconds / node.secondsPerUnit);
    return gathered > 0 ? { type: 'gather', kind: node.kind, amount: gathered } : null;
  }

  if (action.kind === AutoActionKind.FurnaceDeposit) {
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
    const delivered = site.stepContribute(dt, state.inventory);
    action.progress = site.progress;
    return delivered ? { type: 'build', site: action.id, kind: delivered, built: site.built, progress: site.progress } : null;
  }

  if (action.kind === AutoActionKind.Enemy) {
    const enemy = state.waves.enemies.find((candidate) => candidate.id === action.id && candidate.alive);
    if (!enemy) return null;
    enemy.hitCooldown = (enemy.hitCooldown ?? 0) - dt;
    if (enemy.hitCooldown > 0) return null;
    enemy.hitCooldown = t.wolf.playerHitSeconds;
    enemy.health -= t.wolf.playerDamagePerHit;
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
    health: t.wolf.health,
    alive: true,
    position: { x: (index - (count - 1) * 0.5) * 1.8, y: 0, z: index % 2 === 0 ? -t.wolf.spawnZ : t.wolf.spawnZ },
    hitCooldown: 0,
    attackCooldown: 0
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
  if (contract.contractVersion !== '1.3.1') failures.push(`Expected contractVersion 1.3.1; got ${contract.contractVersion ?? '<missing>'}.`);
  if (contract.product !== 'HAVENLINE') failures.push('Contract product must be HAVENLINE.');
  if (contract.camera?.projection !== 'orthographic') failures.push('Shipping camera must remain orthographic.');
  if (!Number.isFinite(contract.camera?.size) || contract.camera.size <= 0) failures.push('Camera size is invalid.');
  if (contract.player?.carryCapacity !== 8) failures.push('Carry capacity must be 8.');
  if (contract.characterSystem?.activeCrewSize !== 4) failures.push('Active core crew size must be 4.');
  if (JSON.stringify(contract.characterSystem?.startingPlayableLeads) !== JSON.stringify(['Character1', 'Character2'])) failures.push('Starting leads must be Character1 and Character2.');
  if (!Array.isArray(contract.characterSystem?.companionFormationOffsets) || contract.characterSystem.companionFormationOffsets.length !== 3) failures.push('Three companion formation offsets are required.');
  if (!Array.isArray(contract.world?.woodNodes) || contract.world.woodNodes.length !== 6) failures.push('Six wood nodes are required.');
  if (!Array.isArray(contract.world?.stoneNodes) || contract.world.stoneNodes.length !== 4) failures.push('Four stone nodes are required.');
  if ((contract.openingLoopTuning?.furnaceLevel2?.wood ?? 0) !== 18 || (contract.openingLoopTuning?.furnaceLevel2?.stone ?? 0) !== 6) failures.push('Furnace Level 2 must require 18 wood and 6 stone.');
  if ((contract.openingLoopTuning?.northBarricadeBuild?.wood ?? 0) !== 8 || (contract.openingLoopTuning?.northBarricadeBuild?.stone ?? 0) !== 3) failures.push('North barricade must require 8 wood and 3 stone.');
  if (contract.openingLoopTuning?.playerConstructionSecondsPerUnit !== 0.24) failures.push('Player construction timing must be 0.24 seconds per contribution.');
  if (contract.openingLoopTuning?.automaticActionPriorities?.construction !== 80) failures.push('Construction priority must be 80.');
  if (contract.openingLoopTuning?.firstWaveEnemyCount !== 3) failures.push('First wave must contain three wolves.');
  if (contract.openingLoopTuning?.wolf?.health !== 65 || contract.openingLoopTuning?.wolf?.playerDamagePerHit !== 22) failures.push('Wolf/player combat tuning does not match Unity.');
  for (const kind of Object.values(ResourceKind)) {
    try { gatherSecondsFor(contract, kind); } catch (error) { failures.push(error.message); }
  }
  return failures;
}

export function runDeterministicContractQA(contract) {
  const checks = [];
  const push = (id, pass, details) => checks.push({ id, pass: !!pass, details });
  const contractFailures = validateContract(contract);
  push('contract-shape', contractFailures.length === 0, contractFailures.length ? contractFailures.join(' | ') : 'Contract v1.3.1 shape is valid.');
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

  const scoring = createInitialState(contract, 'Character1');
  scoring.player.position = { ...vec3(contract.world.furnace), z: contract.world.furnace[2] + 0.5 };
  scoring.inventory.add(ResourceKind.Wood);
  scoring.furnace.durability = scoring.furnace.maxDurability - 50;
  const selected = chooseAutoAction(scoring);
  push('automatic-action-priority', selected.kind === AutoActionKind.FurnaceRepair, `Highest eligible nearby action = ${selected.kind}.`);

  return { passed: checks.every((check) => check.pass), checks };
}
