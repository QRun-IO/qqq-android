// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Record details and synthetic camera/gallery results against an isolated owner fixture.
import { runScannerE2e } from './scanner-e2e-runner.mjs';
const args = process.argv.slice(2);
if (args.length !== 4 || args[0] !== '--serial' || args[2] !== '--fixture' || !args[1] || !args[3]) {
   throw new Error('Usage: node scripts/ci/scanner-device-record-photo-e2e.mjs --serial DEVICE_SERIAL --fixture PRIVATE_RECORD_FILES_FIXTURE.json');
}
await runScannerE2e({ physical: true, serial: args[1], scenario: 'record-photos', fixture: args[3] });