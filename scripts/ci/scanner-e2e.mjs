// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Emulator-only entry point. Physical devices always fail its preflight.
import { runScannerE2e } from './scanner-e2e-runner.mjs';
await runScannerE2e({ qbit: process.argv[2] });
