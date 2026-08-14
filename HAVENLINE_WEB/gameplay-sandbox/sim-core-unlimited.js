import * as legacy from './sim-core.js';

export const ResourceKind = legacy.ResourceKind;
export const AutoActionKind = legacy.AutoActionKind;
export const vec3 = legacy.vec3;
export const distance2D = legacy.distance2D;
export const clampPosition = legacy.clampPosition;
export const companionIdsFor = legacy.companionIdsFor;
export const gatherSecondsFor = legacy.gatherSecondsFor;
export const movePlayer = legacy.movePlayer;
export const actionCandidates = legacy.actionCandidates;
export const chooseAutoAction = legacy.chooseAutoAction;
export const stepAutoAction = legacy.stepAutoAction;
export const updateWaveGate = legacy.updateWaveGate;
export const completeWaveIfClear = legacy.completeWaveIfClear;

export class Inventory {
  constructor() {
    this.capacity = 0; // explicit unlimited sentinel, matching Unity HavenlineInventory
    this.items = [];
  }

  get count() { return this.items.length; }
  get full() { return false; }
  get empty() { return this.count === 0; }
  countKind(kind) { return this.items.filter((item) => item === kind).length; }

  add(kind) {
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

function legacyCompatibleContract(contract) {
  // The pre-correction core used 8 only as a validation invariant. Feed that old implementation
  // a compatibility copy, then replace its inventory with the real uncapped inventory below.
  return {
    ...contract,
    player: {
      ...contract.player,
      carryCapacity: 8
    }
  };
}

export function createInitialState(contract, selectedLead = 'Character1') {
  const failures = validateContract(contract);
  if (failures.length) throw new Error(failures.join(' | '));

  const state = legacy.createInitialState(legacyCompatibleContract(contract), selectedLead);
  state.contract = contract;
  state.inventory = new Inventory();
  return state;
}

export function validateContract(contract) {
  const failures = legacy.validateContract(legacyCompatibleContract(contract))
    .filter((failure) => !failure.includes('Carry capacity must be 8'));

  if (contract?.player?.unlimitedCarry !== true)
    failures.push('Reference-game carry behavior must be explicitly unlimited.');
  if (contract?.player?.carryCapacity !== 0)
    failures.push('Unlimited carrying must use carryCapacity 0 as the runtime sentinel.');
  if (!Number.isInteger(contract?.player?.visibleCarrySlots) || contract.player.visibleCarrySlots < 24)
    failures.push('A polished compressed visible carry stack requires at least 24 authored visual slots.');

  return failures;
}

export function runDeterministicContractQA(contract) {
  const structural = validateContract(contract);
  const checks = [{
    id: 'contract-shape',
    pass: structural.length === 0,
    details: structural.length ? structural.join(' | ') : 'Contract explicitly locks uncapped carrying.'
  }];
  if (structural.length) return { passed: false, checks };

  const legacyResult = legacy.runDeterministicContractQA(legacyCompatibleContract(contract));
  for (const check of legacyResult.checks) {
    if (check.id === 'contract-shape' || check.id === 'carry-capacity') continue;
    checks.push(check);
  }

  const inventory = new Inventory();
  for (let index = 0; index < 128; index++) inventory.add(ResourceKind.Wood);
  checks.push({
    id: 'uncapped-carry',
    pass: inventory.count === 128 && inventory.full === false && inventory.capacity === 0,
    details: `Inventory accepted ${inventory.count} items with no gameplay cap.`
  });

  const state = createInitialState(contract, 'Character1');
  const woodNode = state.resources.find((node) => node.kind === ResourceKind.Wood);
  state.player.position = { ...woodNode.position };
  state.player.facing = { x: 0, z: 1 };
  for (let index = 0; index < 40; index++) state.inventory.add(ResourceKind.Stone);
  const before = state.inventory.count;
  woodNode.stepGather(woodNode.secondsPerUnit + 0.01, state.inventory);
  checks.push({
    id: 'gather-continues-over-old-cap',
    pass: state.inventory.count > before,
    details: `Gathering continued at carried load ${before}+ instead of stopping at the former eight-item cap.`
  });

  return { passed: checks.every((check) => check.pass), checks };
}
