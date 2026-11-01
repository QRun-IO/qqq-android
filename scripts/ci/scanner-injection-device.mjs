// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Explicit physical-only security test. Never installs, clears, or targets the normal/E2E operator apps.
import { spawn } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { startSemanticApiProxy } from './semantic-api-proxy.mjs';

const [serial, fixturePath, probePath] = process.argv.slice(2);
if (!serial || !/^[A-Za-z0-9._:-]+$/.test(serial) || serial.startsWith('emulator-') || !fixturePath || !probePath || process.argv.length !== 5) {
   throw new Error('Usage: node scripts/ci/scanner-injection-device.mjs <explicit-TC22-serial> <private-qa-fixture.json> <separately-signed-probe.apk>');
}
const repository = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const config = JSON.parse(await readFile(resolve(fixturePath), 'utf8'));
const origin = new URL(config.baseUrl);
if (config.purpose !== 'scanner-injection-qa' || origin.protocol !== 'http:' || origin.hostname !== '127.0.0.1' ||
      !origin.port || origin.pathname !== '/' || origin.username || origin.password || origin.search || origin.hash ||
      typeof config.scannerPassword !== 'string' || config.scannerPassword.length < 16 ||
      typeof config.adminPassword !== 'string' || config.adminPassword.length < 16) {
   throw new Error('Requires an explicitly designated disposable loopback QA fixture with private random scanner/admin credentials.');
}
const physicalTriggers = Object.hasOwn(config, 'expectedBarcode');
if (physicalTriggers && (typeof config.expectedBarcode !== 'string' || !/^[\x20-\x7e]{1,4096}$/.test(config.expectedBarcode))) {
   throw new Error('Optional expectedBarcode must be a nonempty printable synthetic test value; it is never trimmed or coerced.');
}
const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
if (!sdk) throw new Error('ANDROID_HOME is required; the harness never changes Android user home or device keys.');
const adb = join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
const target = 'io.qrun.mobile.scanner.qa';
const probe = 'io.qrun.mobile.scanner.probe';
const evidence = join(repository, 'scanner-host/build/native-device-qa-injection');
await mkdir(evidence, { recursive: true });
await writeFile(join(evidence, 'proof.json'), JSON.stringify({ status: 'PREPARING', physicalTriggerVerified: false }, null, 2) + '\n');
const redact = value => value.replaceAll(config.scannerPassword, '<REDACTED>').replaceAll(config.adminPassword, '<REDACTED>');
let proxy;
const stages = new Map();
let stepIds = new Set();
const nativeSteps = () => proxy.evidence().filter(call => call.android && call.method === 'POST' && stepIds.has(call.operationId)).length;
const run = (args, input) => new Promise((done, fail) => {
   const child = spawn(adb, ['-s', serial, ...args], { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
   let output = '';
   let lines = '';
   const timer = setTimeout(() => { child.kill(); fail(new Error('Bounded QA adb operation timed out.')); }, args.includes('instrument') ? (physicalTriggers ? 600_000 : 300_000) : 60_000);
   child.stdout.on('data', data => {
      output += data;
      lines += data;
      const complete = lines.split(/\r?\n/);
      lines = complete.pop();
      for (const line of complete) {
         const stage = /^INSTRUMENTATION_STATUS: qqqStage=(injection-baseline|injection-complete|hardware-(?:initial|recreated|resumed)-(?:ready|saved)|hardware-complete)$/.exec(line);
         if (stage) { stages.set(stage[1], nativeSteps()); console.log(line); }
      }
   });
   child.stderr.on('data', data => { output += data; });
   child.on('error', error => { clearTimeout(timer); fail(error); });
   child.on('close', code => { clearTimeout(timer); code === 0 ? done(redact(output)) : fail(new Error(redact(`QA adb operation failed (${code}): ${output}`))); });
   child.stdin.end(input);
});

if ((await run(['get-serialno'])).trim() !== serial || (await run(['shell', 'getprop', 'ro.kernel.qemu'])).trim() === '1') {
   throw new Error('Requires the explicitly selected physical device; no emulator is started.');
}
if (!/^zebra/i.test((await run(['shell', 'getprop', 'ro.product.manufacturer'])).trim()) ||
      !/^TC22(?:$|[ _-])/i.test((await run(['shell', 'getprop', 'ro.product.model'])).trim())) {
   throw new Error('This physical acceptance is limited to the approved Zebra TC22.');
}
for (const name of [target, target + '.test', probe]) {
   const installed = (await run(['shell', 'pm', 'list', 'packages', '-u', name])).split(/\r?\n/).map(line => line.trim());
   if (installed.includes('package:' + name)) throw new Error(`Refusing to replace or clear existing disposable package ${name}.`);
}
const apks = [
   [target, join(repository, 'scanner-host/build/outputs/apk/qa/scanner-host-qa.apk')],
   [target + '.test', join(repository, 'scanner-host/build/outputs/apk/androidTest/qa/scanner-host-qa-androidTest.apk')],
   [probe, resolve(probePath)],
];
const buildTools = process.env.ANDROID_BUILD_TOOLS_VERSION ?? '35.0.0';
if (!/^\d+\.\d+\.\d+$/.test(buildTools)) throw new Error('ANDROID_BUILD_TOOLS_VERSION must identify an installed stable SDK toolset.');
const aapt2 = join(sdk, 'build-tools', buildTools, process.platform === 'win32' ? 'aapt2.exe' : 'aapt2');
const packageName = apk => new Promise((done, fail) => {
   const child = spawn(aapt2, ['dump', 'badging', apk], { windowsHide: true, stdio: ['ignore', 'pipe', 'ignore'] });
   let output = '';
   const timer = setTimeout(() => { child.kill(); fail(new Error('APK identity verification timed out.')); }, 30_000);
   child.stdout.on('data', data => { output += data; });
   child.on('error', error => { clearTimeout(timer); fail(error); });
   child.on('close', code => {
      clearTimeout(timer);
      const identity = /^package: name='([^']+)'/m.exec(output)?.[1];
      if (code !== 0 || !identity) fail(new Error('Cannot verify APK package identity before installation.'));
      else done(identity);
   });
});
for (const [expected, apk] of apks) {
   if (await packageName(apk) !== expected) throw new Error('APK application ID does not match its disposable allowlisted target.');
}
const artifacts = await Promise.all(apks.map(async ([applicationId, path]) => ({
   applicationId, sha256: createHash('sha256').update(await readFile(path)).digest('hex'),
})));
const installedByRun = [];
let reversed = false;
let output = '';
await mkdir(evidence, { recursive: true });
try {
   const api = await fetch(new URL('qqq/v1/openapi.json', origin), { signal: AbortSignal.timeout(20_000) });
   if (!api.ok) throw new Error('Disposable fixture OpenAPI is unavailable.');
   const contract = await api.json();
   stepIds = new Set(Object.entries(contract.paths).filter(([path]) => /\/processes\/\{[^}]+\}\/\{[^}]+\}\/step\/\{[^}]+\}$/.test(path))
      .map(([, value]) => value.post?.operationId).filter(Boolean));
   if (stepIds.size !== 1) throw new Error('Expected exactly one documented semantic process-step operation.');
   proxy = await startSemanticApiProxy(origin.toString().replace(/\/$/, ''));
   await run(['reverse', '--no-rebind', `tcp:${proxy.port}`, `tcp:${proxy.port}`]);
   reversed = true;
   for (const [applicationId, apk] of apks) { await run(['install', apk]); installedByRun.push(applicationId); }
   await run(['shell', 'run-as', target, 'mkdir', '-p', 'files']);
   await run(['shell', 'run-as', target, 'sh', '-c', "'cat > files/qa-injection.json'"],
      JSON.stringify({ serverUrl: `http://127.0.0.1:${proxy.port}/`, scannerPassword: config.scannerPassword, adminPassword: config.adminPassword,
         ...(physicalTriggers ? { expectedBarcode: config.expectedBarcode } : {}) }));
   output = await run(['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
      'io.qrun.mobile.scanner.ScannerInjectionTest#unrelatedUidCannotSubmitAProductionScan',
      `${target}.test/androidx.test.runner.AndroidJUnitRunner`]);
   await writeFile(join(evidence, 'instrumentation.txt'), output);
   if (!/OK \(1 test\)/.test(output) || /FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(output)) throw new Error('Production-path scan injection acceptance failed.');
   const expectedSteps = physicalTriggers ? 4 : 1;
   if (stages.get('injection-baseline') !== 1 || stages.get('injection-complete') !== 1 || nativeSteps() !== expectedSteps) {
      throw new Error('Native process steps must equal the manual control plus configured operator scans; forged scans must add zero mutations.');
   }
   if (physicalTriggers && (stages.get('hardware-initial-saved') !== 2 || stages.get('hardware-recreated-saved') !== 3 ||
         stages.get('hardware-resumed-saved') !== 4 || stages.get('hardware-complete') !== 4)) {
      throw new Error('All three actual operator scans must have distinct verified receipts after initial start, recreation and resume.');
   }
   const semantic = proxy.verify(true);
   await writeFile(join(evidence, 'proof.json'), JSON.stringify({ status: 'PASSED', artifacts, semantic, nativeProcessSteps: nativeSteps(),
      expectedNativeProcessSteps: expectedSteps,
      manualPositiveControls: 1, forgedScanIntents: 8, extraNativeProcessSteps: 0,
      targetVariant: 'qa-production-factory', source: 'ordinary-uid-separate-signer',
      physicalTriggerVerified: physicalTriggers,
      physicalTriggerCount: physicalTriggers ? 3 : 0,
      ...(physicalTriggers ? { expectedBarcodeSha256: createHash('sha256').update(config.expectedBarcode).digest('hex'),
         physicalLifecyclePhases: ['initial', 'activity-recreation', 'activity-stop-resume'] } : {}) }, null, 2) + '\n');
   console.log('QA injection proof passed: separate UID/signer, actual-action route control, foreground production path, zero forged scan mutations.');
   if (physicalTriggers) console.log('Actual TC22 trigger proof passed: three distinct acknowledged hardware scans across recreation and background/resume.');
} catch (error) {
   await writeFile(join(evidence, 'proof.json'), JSON.stringify({ status: 'FAILED', artifacts, physicalTriggerVerified: false }, null, 2) + '\n');
   throw error;
} finally {
   const cleanupFailures = [];
   if (proxy) await writeFile(join(evidence, 'operations.json'), JSON.stringify(proxy.evidence(), null, 2) + '\n')
      .catch(() => { cleanupFailures.push('operation evidence'); });
   if (installedByRun.includes(target)) await run(['shell', 'run-as', target, 'rm', '-f', 'files/qa-injection.json'])
      .catch(() => { cleanupFailures.push('QA credential file'); });
   for (const name of installedByRun.reverse()) await run(['uninstall', name]).catch(() => { cleanupFailures.push(name); });
   if (reversed) await run(['reverse', '--remove', `tcp:${proxy.port}`]).catch(() => { cleanupFailures.push('test reverse'); });
   if (proxy) await proxy.close().catch(() => { cleanupFailures.push('test proxy'); });
   if (cleanupFailures.length) throw new Error(`QA cleanup/recording incomplete: ${cleanupFailures.join(', ')}`);
}
