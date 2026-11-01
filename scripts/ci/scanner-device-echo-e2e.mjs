// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Explicit disposable native Echo acceptance; the fixture's printer/jobs are simulated.
import { runScannerE2e } from './scanner-e2e-runner.mjs';
const args = process.argv.slice(2);
if (args.length !== 4 || args[0] !== '--serial' || args[2] !== '--fixture' || !args[1] || !args[3]) {
   throw new Error('Usage: node scripts/ci/scanner-device-echo-e2e.mjs --serial TC22_SERIAL --fixture PRIVATE_ECHO_FIXTURE.json');
}
await runScannerE2e({ physical: true, serial: args[1], scenario: 'echo', fixture: args[3] });
