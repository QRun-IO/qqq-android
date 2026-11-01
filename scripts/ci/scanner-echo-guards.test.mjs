// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
import assert from 'node:assert/strict';
import test from 'node:test';
import { runScannerE2e, validateEchoFixtureDescriptor, validateEchoFixtureContract, physicalSerialMatches, redactEchoOutput } from './scanner-e2e-runner.mjs';

const fixture = {
   fixtureType: 'echo-ui-e2e', simulatedPrinting: true, baseUrl: 'http://127.0.0.1:49152/',
   username: 'scanner', password: 'synthetic-scanner-password', adminUsername: 'admin', adminPassword: 'synthetic-admin-password',
};
const version = { apiVersion: '1.10.0', extensions: [{ name: 'mobile-scanners', version: '1.4.0', pathPrefix: '/qbit/mobile-scanners' }] };
const branding = { displayName: 'Echo UI E2E — simulated printing' };

/*******************************************************************************
 ** Identity is checked before evidence redaction; only the comparison escapes.
 *******************************************************************************/
test('raw serial matches exactly and a different physical device is refused', () => {
   const serial = 'SYNTHETIC_TC22_SERIAL';
   assert.equal(physicalSerialMatches(serial, `${serial}\r\n`), true);
   assert.equal(physicalSerialMatches(serial, 'ANOTHER_DEVICE\r\n'), false);
   assert.equal(physicalSerialMatches(serial, redactEchoOutput(`${serial}\r\n`, serial)), false);
});

/*******************************************************************************
 ** Serial-bearing successful output and errors remain safe to persist as reports.
 *******************************************************************************/
test('Echo artifacts and error text contain no selected serial', () => {
   const serial = 'SYNTHETIC_TC22_SERIAL';
   const artifact = JSON.stringify({ output: redactEchoOutput(`${serial}\nOK (1 test)`, serial),
      error: redactEchoOutput(`Device ${serial} failed`, serial) });
   assert.equal(artifact.includes(serial), false);
   assert.equal(artifact.includes('<DEVICE>'), true);
});

/*******************************************************************************
 ** A valid descriptor is inspected without contacting a server or any device.
 *******************************************************************************/
test('explicit simulated Echo descriptor is accepted', () => {
   assert.equal(validateEchoFixtureDescriptor(fixture).port, '49152');
   validateEchoFixtureContract(version, branding);
});

/*******************************************************************************
 ** Existing scanner and printing listeners can never become this test's target.
 *******************************************************************************/
test('all protected normal and printing ports are refused', () => {
   for (const port of [57832, 51631, 18081, 18086]) {
      assert.throws(() => validateEchoFixtureDescriptor({ ...fixture, baseUrl: `http://127.0.0.1:${port}/` }));
   }
});

/*******************************************************************************
 ** URL decoration cannot turn the private fixture into another host or route.
 *******************************************************************************/
test('non-private or decorated fixture origins are refused', () => {
   for (const baseUrl of ['https://127.0.0.1:49152/', 'http://example.invalid:49152/', 'http://127.0.0.1/',
      'http://127.0.0.1:49152/path', 'http://127.0.0.1:49152/?value=1', 'http://127.0.0.1:49152/#fragment',
      'http://scanner:secret@127.0.0.1:49152/', 'not-a-url']) {
      assert.throws(() => validateEchoFixtureDescriptor({ ...fixture, baseUrl }));
   }
});

/*******************************************************************************
 ** Purpose, simulation and private account declarations must all match exactly.
 *******************************************************************************/
test('wrong-purpose or incomplete credentials are refused', () => {
   for (const changed of [{ fixtureType: 'device-screen-qa' }, { simulatedPrinting: false }, { simulatedPrinting: 'true' },
      { username: 'operator' }, { adminUsername: 'owner' }, { password: null }, { password: 'short' }, { adminPassword: 'short' }]) {
      assert.throws(() => validateEchoFixtureDescriptor({ ...fixture, ...changed }));
   }
});

/*******************************************************************************
 ** Another real demo or an unqualified contract cannot masquerade as this fixture.
 *******************************************************************************/
test('contract and simulated branding must match the prepared cohort', () => {
   assert.throws(() => validateEchoFixtureContract({ ...version, apiVersion: '1.9.0' }, branding));
   assert.throws(() => validateEchoFixtureContract({ ...version, extensions: [{ ...version.extensions[0], version: '1.3.0' }] }, branding));
   assert.throws(() => validateEchoFixtureContract(version, { displayName: 'QQQ Mobile' }));
});

/*******************************************************************************
 ** These calls stop before reading a fixture or spawning ADB, even when selected
 ** through the generic emulator entry or an incomplete physical invocation.
 *******************************************************************************/
test('Echo requires its explicit physical entry inputs before device operations', async () => {
   await assert.rejects(runScannerE2e({ scenario: 'echo' }), /explicit TC22/);
   await assert.rejects(runScannerE2e({ scenario: 'echo', physical: true, fixture: 'unused.json' }), /explicit TC22/);
   await assert.rejects(runScannerE2e({ scenario: 'echo', physical: true, fixture: 'unused.json', serial: 'emulator-5554' }), /explicit TC22/);
});
