// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Explicit, isolated TC22 acceptance. Never alters normal app data, device settings or DataWedge.
import { runScannerE2e } from './scanner-e2e-runner.mjs';
const args = process.argv.slice(2);
if (args.length < 4 || args.length > 5 || args[0] !== '--serial' || args[2] !== '--scenario' || !args[1] || !args[3]) {
   throw new Error('Usage: node scripts/ci/scanner-device-e2e.mjs --serial TC22_SERIAL --scenario session-monitor [QBIT_DIRECTORY]');
}
await runScannerE2e({ physical: true, serial: args[1], scenario: args[3], qbit: args[4] });
