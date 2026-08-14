import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { runDeterministicContractQA, validateContract } from './sim-core.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const contractPath = path.resolve(here, '../shared/HAVENLINE_REFERENCE_CONTRACT.json');
const unityContractPath = path.resolve(here, '../../HAVENLINE_UNITY/Assets/Havenline/Reference/HAVENLINE_REFERENCE_CONTRACT.json');

const browserBytes = fs.readFileSync(contractPath);
const unityBytes = fs.readFileSync(unityContractPath);
if (!browserBytes.equals(unityBytes)) {
  console.error('FAIL contract-mirror: browser and Unity contract files are not byte-identical.');
  process.exit(1);
}

const contract = JSON.parse(browserBytes.toString('utf8'));
const structuralFailures = validateContract(contract);
if (structuralFailures.length) {
  for (const failure of structuralFailures) console.error(`FAIL contract-shape: ${failure}`);
  process.exit(1);
}

const result = runDeterministicContractQA(contract);
for (const check of result.checks) {
  console.log(`${check.pass ? 'PASS' : 'FAIL'} ${check.id}: ${check.details}`);
}

if (!result.passed) process.exit(1);
console.log(`PASS HAVENLINE browser gameplay contract QA (${result.checks.length} checks).`);
