// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Manual-only system-consent acceptance. Never part of hosted/emulator defaults.
import { runScannerE2e } from './scanner-e2e-runner.mjs';
const args = process.argv.slice(2);
if (args.length !== 5 || args[0] !== '--serial' || args[2] !== '--fixture' || args[4] !== '--operator-consent' || !args[1] || !args[3]) {
   throw new Error('Usage: node scripts/ci/scanner-screen-device.mjs --serial TC22_SERIAL --fixture PRIVATE_QA_FIXTURE.json --operator-consent');
}
await runScannerE2e({ physical: true, serial: args[1], scenario: 'device-screen', fixture: args[3], operatorConsent: true });
