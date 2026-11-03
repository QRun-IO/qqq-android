// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Synthetic camera/gallery activity results against the isolated owner-module fixture.
import { runScannerE2e } from './scanner-e2e-runner.mjs';
const args = process.argv.slice(2);
if (args.length !== 4 || args[0] !== '--serial' || args[2] !== '--fixture' || !args[1] || !args[3]) {
   throw new Error('Usage: node scripts/ci/scanner-device-photo-e2e.mjs --serial TC22_SERIAL --fixture PRIVATE_CREDENTIAL_FILE');
}
await runScannerE2e({ physical: true, serial: args[1], scenario: 'photos', fixture: args[3] });
