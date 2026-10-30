// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Real native host + isolated QQQ demo. Never touches the normal scanner package.
import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { mkdtemp, readFile, rm, mkdir, writeFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { basename, delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { startSemanticApiProxy } from './semantic-api-proxy.mjs';

const repository = resolve(fileURLToPath(new URL('../..', import.meta.url)));
const qbit = resolve(process.argv[2] ?? join(repository, '../qbit-mobile-scanners'));
const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
const adb = sdk ? join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb') : 'adb';
const serial = process.env.ANDROID_SERIAL;
const adbPrefix = serial ? ['-s', serial] : [];
const packageName = 'io.qrun.mobile.scanner.e2e';
const adminPassword = randomBytes(24).toString('base64url');
const scannerPassword = randomBytes(24).toString('base64url');
const redact = (text) => text.replaceAll(adminPassword, '<REDACTED>').replaceAll(scannerPassword, '<REDACTED>');
const run = (command, args, input) => new Promise((resolveRun, reject) => {
   const child = spawn(command, args, { windowsHide: true, stdio: ['pipe', 'pipe', 'pipe'] });
   // Native Compose instrumentation includes emulator startup and transport shutdown after the test finishes.
   const timeout = args.includes('instrument') ? 300_000 : 180_000;
   const deadline = setTimeout(() => { child.kill(); reject(new Error(`${command} exceeded the E2E timeout`)); }, timeout);
   let output = '';
   let statusBuffer = '';
   child.stdout.on('data', (data) => {
      output += data;
      if (args.includes('instrument')) {
         statusBuffer += data;
         const lines = statusBuffer.split(/\r?\n/);
         statusBuffer = lines.pop();
         for (const line of lines) {
            if (/^INSTRUMENTATION_STATUS: qqqStage=[A-Za-z0-9._ -]+$/.test(line)) console.log(line);
         }
      }
   });
   child.stderr.on('data', (data) => { output += data; });
   child.on('error', (error) => { clearTimeout(deadline); reject(error); });
   child.on('close', (code) => {
      clearTimeout(deadline);
      if (code === 0) resolveRun(redact(output)); else reject(new Error(redact(`${command} failed (${code}): ${output}`)));
   });
   child.stdin.end(input);
});
const adbRun = (...args) => run(adb, [...adbPrefix, ...args]);

if ((await adbRun('shell', 'getprop', 'ro.kernel.qemu')).trim() !== '1') {
   throw new Error('Refusing scanner E2E on a physical device. Select an emulator with ANDROID_SERIAL.');
}
await adbRun('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP');
await adbRun('shell', 'wm', 'dismiss-keyguard');
const port = await new Promise((resolvePort, reject) => {
   const server = createServer();
   server.once('error', reject);
   server.listen(0, '127.0.0.1', () => { const chosen = server.address().port; server.close(() => resolvePort(chosen)); });
});
const classpath = [join(qbit, 'demo/target/classes'), (await readFile(join(qbit, 'demo/target/e2e-classpath.txt'), 'utf8')).trim()].join(delimiter);
const directory = await mkdtemp(join(tmpdir(), 'qqq-native-e2e-'));
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const server = spawn(java, ['-cp', classpath, 'com.kingsrook.qbits.mobilescanners.demo.MobileScannersDemo', String(port)], {
   cwd: directory, windowsHide: true, stdio: 'ignore',
   env: { ...process.env, MOBILE_DEMO_ADMIN_PASSWORD: adminPassword, MOBILE_DEMO_SCANNER_PASSWORD: scannerPassword },
});
let serverError;
let proxy;
const animationScales = new Map();
server.on('error', (error) => { serverError = error; });
try {
   for (const setting of ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']) {
      const previous = (await adbRun('shell', 'settings', 'get', 'global', setting)).trim();
      if (!/^(null|[0-9]+(?:\.[0-9]+)?)$/.test(previous)) throw new Error('Unexpected emulator animation setting.');
      animationScales.set(setting, previous);
      await adbRun('shell', 'settings', 'put', 'global', setting, '0');
   }
   const origin = `http://127.0.0.1:${port}`;
   const deadline = Date.now() + 60_000;
   while (true) {
      if (serverError) throw serverError;
      if (server.exitCode !== null) throw new Error('Isolated QQQ demo exited before becoming ready.');
      if (await fetch(`${origin}/qqq/v1/qbit/mobile-scanners/status`).then((response) => response.ok).catch(() => false)) break;
      if (Date.now() > deadline) throw new Error('Isolated QQQ demo did not become ready. Build the qbit/demo and test classpath first.');
      await new Promise((done) => setTimeout(done, 250));
   }
   proxy = await startSemanticApiProxy(origin);
   await adbRun('reverse', `tcp:${proxy.port}`, `tcp:${proxy.port}`);
   await adbRun('install', '-r', join(repository, 'scanner-host/build/outputs/apk/e2e/scanner-host-e2e.apk'));
   await adbRun('install', '-r', join(repository, 'scanner-host/build/outputs/apk/androidTest/e2e/scanner-host-e2e-androidTest.apk'));
   const evidence = join(repository, 'scanner-host/build/native-e2e');
   await mkdir(evidence, { recursive: true });
   const reports = [];
   for (const phase of [
      { mode: 'unsupported-api', expectedError: 'Unsupported QQQ UI API version.' },
      { mode: 'missing-extension', expectedError: 'This server is missing the mobile-scanners extension.' },
      { mode: 'missing-channel', expectedError: 'This server does not support the Android client channel.' },
      { mode: 'compatible' },
      { mode: 'additive' },
      { mode: 'compatible', name: 'admin-forms', test: 'adminFormsApproveRevokeAndCancel' },
   ]) {
      if (process.env.E2E_SCENARIO && process.env.E2E_SCENARIO !== (phase.name ?? phase.mode)) continue;
      proxy.setMode(phase.mode);
      await adbRun('shell', 'pm', 'clear', packageName);
      await adbRun('shell', 'run-as', packageName, 'mkdir', '-p', 'files');
      await run(adb, [...adbPrefix, 'shell', 'run-as', packageName, 'sh', '-c', "'cat > files/e2e.json'"],
         JSON.stringify({ serverUrl: `http://127.0.0.1:${proxy.port}/`, adminPassword, scannerPassword, expectedError: phase.expectedError }));
      const test = phase.test ?? (phase.expectedError ? 'incompatibleServerStopsBeforeLogin' : 'loginEnrollApproveCaptureAndRevoke');
      const output = await adbRun('shell', 'am', 'instrument', '-w', '-r',
         '-e', 'class', `io.qrun.mobile.scanner.ScannerEndToEndTest#${test}`,
         `${packageName}.test/androidx.test.runner.AndroidJUnitRunner`);
      await writeFile(join(evidence, `${phase.name ?? phase.mode}-instrumentation.txt`), output);
      await writeFile(join(evidence, `${phase.name ?? phase.mode}-operations.json`), `${JSON.stringify(proxy.evidence(), null, 2)}\n`);
      if (!/OK \(1 test\)/.test(output) || /FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(output)) {
         throw new Error(`Native scanner E2E (${phase.mode}) failed:\n${output}`);
      }
      reports.push({ scenario: phase.name ?? phase.mode, ...proxy.verify(!phase.expectedError, Boolean(phase.test)) });
      console.log(`Native semantic E2E passed: ${phase.name ?? phase.mode}.`);
   }
   await writeFile(join(evidence, 'semantic-contract.json'), `${JSON.stringify(reports, null, 2)}\n`);
   console.log('All native requests in the selected scenarios matched the published semantic API.');
} finally {
   for (const [setting, previous] of animationScales) {
      if (previous === 'null') await adbRun('shell', 'settings', 'delete', 'global', setting).catch(() => {});
      else await adbRun('shell', 'settings', 'put', 'global', setting, previous).catch(() => {});
   }
   await adbRun('shell', 'run-as', packageName, 'rm', '-f', 'files/e2e.json').catch(() => {});
   await adbRun('uninstall', `${packageName}.test`).catch(() => {});
   await adbRun('uninstall', packageName).catch(() => {});
   if (proxy) {
      await adbRun('reverse', '--remove', `tcp:${proxy.port}`).catch(() => {});
      await proxy.close();
   }
   const stopped = new Promise((done) => { if (server.exitCode !== null || serverError) done(); else server.once('exit', done); });
   server.kill();
   await Promise.race([stopped, new Promise((_, reject) => {
      const timer = setTimeout(() => reject(new Error('Isolated demo did not stop; its temporary database was retained.')), 5_000);
      timer.unref();
   })]);
   if (dirname(resolve(directory)) !== resolve(tmpdir()) || !basename(directory).startsWith('qqq-native-e2e-')) {
      throw new Error('Refusing cleanup outside the isolated demo temporary directory.');
   }
   await rm(directory, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
}
