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

/** Shared implementation; physical execution is exposed only by the explicit device entry point. */
export async function runScannerE2e(options = {}) {
   const repository = resolve(fileURLToPath(new URL('../..', import.meta.url)));
   const qbit = resolve(options.qbit ?? join(repository, '../qbit-mobile-scanners'));
   const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
   const adb = sdk ? join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb') : 'adb';
   const serial = options.serial ?? process.env.ANDROID_SERIAL;
   const selectedScenario = options.scenario ?? process.env.E2E_SCENARIO;
   const physical = options.physical === true;
   const fixture = options.fixture ? JSON.parse(await readFile(options.fixture, 'utf8')) : null;
   if (fixture) {
      const url = new URL(fixture.baseUrl);
      if (!physical || selectedScenario !== 'photos' || url.protocol !== 'http:' || url.hostname !== '127.0.0.1' || !url.port || url.username || url.password || url.search || url.hash || url.pathname !== '/' ||
            fixture.username !== 'scanner' || fixture.adminUsername !== 'admin' || fixture.processName !== 'mobileRecordPhotos' || fixture.tableName !== 'document' || String(fixture.recordId) !== '1' ||
            typeof fixture.password !== 'string' || fixture.password.length < 16 || typeof fixture.adminPassword !== 'string' || fixture.adminPassword.length < 16) {
         throw new Error('Photo acceptance requires the isolated loopback Record Files fixture and its private credential file.');
      }
   }
   if (selectedScenario === 'photos' && !fixture) throw new Error('The photos scenario requires an explicit isolated Record Files fixture.');
   const adbPrefix = serial ? ['-s', serial] : [];
   const packageName = 'io.qrun.mobile.scanner.e2e';
   const adminPassword = fixture?.adminPassword ?? randomBytes(24).toString('base64url');
   const scannerPassword = fixture?.password ?? randomBytes(24).toString('base64url');
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

   const emulated = (await adbRun('shell', 'getprop', 'ro.kernel.qemu')).trim() === '1';
   if (physical) {
      if (!serial || serial.startsWith('emulator-') || emulated || (await adbRun('get-serialno')).trim() !== serial) {
         throw new Error('Physical acceptance requires an explicitly selected non-emulator serial.');
      }
      const manufacturer = (await adbRun('shell', 'getprop', 'ro.product.manufacturer')).trim();
      const model = (await adbRun('shell', 'getprop', 'ro.product.model')).trim();
      if (!/^zebra/i.test(manufacturer) || !/^TC22(?:$|[ _-])/i.test(model)) throw new Error('This physical acceptance entry point is limited to the approved Zebra TC22.');
      const installed = (await adbRun('shell', 'pm', 'list', 'packages', '-u', packageName)).split(/\r?\n/).map(line => line.trim());
      if (installed.includes('package:' + packageName) || installed.includes('package:' + packageName + '.test')) {
         throw new Error('Disposable test packages already exist. Refusing to replace or clear an existing installation.');
      }
   } else {
      if (!emulated) throw new Error('Refusing scanner E2E on a physical device. Use the separate explicit device-test entry point.');
      await adbRun('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP');
      await adbRun('shell', 'wm', 'dismiss-keyguard');
   }
   const localScenarios = {
      licenses: 'io.qrun.mobile.scanner.OpenSourceLicensesTest',
      orientation: 'io.qrun.mobile.scanner.OrientationPolicyTest',
   };
   const runLocalScenario = async (scenario) => {
      const output = await adbRun('shell', 'am', 'instrument', '-w', '-r',
         '-e', 'class', localScenarios[scenario], `${packageName}.test/androidx.test.runner.AndroidJUnitRunner`);
      const evidence = join(repository, physical ? 'scanner-host/build/native-device-e2e' : 'scanner-host/build/native-e2e');
      await mkdir(evidence, { recursive: true });
      await writeFile(join(evidence, `${scenario}-instrumentation.txt`), output);
      if (!/OK \(1 test\)/.test(output) || /FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(output)) {
         throw new Error(`Native ${scenario} acceptance failed; inspect the isolated test evidence.`);
      }
      console.log(`Native ${scenario} acceptance passed (bundled resources, no server).`);
   };
   const allowedScenarios = [...(physical ? ['navigation', 'photos'] : []), ...Object.keys(localScenarios), 'pixel-copy', 'unsupported-api', 'missing-extension', 'missing-channel', 'compatible', 'additive', 'admin-forms', 'session-monitor'];
   if (selectedScenario && !allowedScenarios.includes(selectedScenario)) throw new Error('Unknown native E2E scenario.');
   if (Object.hasOwn(localScenarios, selectedScenario)) {
      const temporary = [];
      try {
         await adbRun('install', ...(physical ? [] : ['-r']), join(repository, 'scanner-host/build/outputs/apk/e2e/scanner-host-e2e.apk'));
         temporary.push(packageName);
         await adbRun('install', ...(physical ? [] : ['-r']), join(repository, 'scanner-host/build/outputs/apk/androidTest/e2e/scanner-host-e2e-androidTest.apk'));
         temporary.push(packageName + '.test');
         await runLocalScenario(selectedScenario);
      } finally {
         for (const installed of temporary.reverse()) await adbRun('uninstall', installed);
      }
      return;
   }
   if (selectedScenario === 'navigation') {
      const sample = 'com.kingsrook.qqq.sampleandroidmobileapp';
      const existing = (await adbRun('shell', 'pm', 'list', 'packages', '-u', sample)).split(/\r?\n/).map(line => line.trim());
      if (existing.includes('package:' + sample) || existing.includes('package:' + sample + '.test')) throw new Error('Refusing to replace an existing sample/navigation test installation.');
      const temporary = [];
      try {
         await adbRun('install', join(repository, 'qqqsampleandroidmobileapp/build/outputs/apk/debug/qqqsampleandroidmobileapp-debug.apk'));
         temporary.push(sample);
         await adbRun('install', join(repository, 'qqqsampleandroidmobileapp/build/outputs/apk/androidTest/debug/qqqsampleandroidmobileapp-debug-androidTest.apk'));
         temporary.push(sample + '.test');
         const output = await adbRun('shell', 'am', 'instrument', '-w', '-r',
            '-e', 'class', 'com.kingsrook.qqq.sampleandroidmobileapp.NavigationTests,com.kingsrook.qqq.sampleandroidmobileapp.SemanticInputTests,com.kingsrook.qqq.sampleandroidmobileapp.SessionTests',
            sample + '.test/androidx.test.runner.AndroidJUnitRunner');
         const evidence = join(repository, 'scanner-host/build/native-device-e2e');
         await mkdir(evidence, { recursive: true });
         await writeFile(join(evidence, 'navigation-instrumentation.txt'), output);
         if (!/OK \(12 tests\)/.test(output) || /FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(output)) throw new Error('Physical native navigation/input/account acceptance failed; inspect the isolated test evidence.');
         console.log('Physical native navigation/input/account acceptance passed (12 tests, synthetic repository).');
      } finally {
         for (const installed of temporary.reverse()) await adbRun('uninstall', installed);
      }
      return;
   }
   const port = await new Promise((resolvePort, reject) => {
      const server = createServer();
      server.once('error', reject);
      server.listen(0, '127.0.0.1', () => { const chosen = server.address().port; server.close(() => resolvePort(chosen)); });
   });
   const classpath = fixture ? null : [join(qbit, 'demo/target/classes'), (await readFile(join(qbit, 'demo/target/e2e-classpath.txt'), 'utf8')).trim()].join(delimiter);
   const directory = fixture ? null : await mkdtemp(join(tmpdir(), 'qqq-native-e2e-'));
   const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
   const server = fixture ? null : spawn(java, ['-cp', classpath, 'com.kingsrook.qbits.mobilescanners.demo.MobileScannersDemo', String(port)], {
      cwd: directory, windowsHide: true, stdio: 'ignore',
      env: { ...process.env, MOBILE_DEMO_ADMIN_PASSWORD: adminPassword, MOBILE_DEMO_SCANNER_PASSWORD: scannerPassword },
   });
   let serverError;
   let proxy;
   const animationScales = new Map();
   const installedByRun = new Set();
   let reverseAdded = false;
   server?.on('error', (error) => { serverError = error; });
   try {
      for (const setting of physical ? [] : ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']) {
         const previous = (await adbRun('shell', 'settings', 'get', 'global', setting)).trim();
         if (!/^(null|[0-9]+(?:\.[0-9]+)?)$/.test(previous)) throw new Error('Unexpected emulator animation setting.');
         animationScales.set(setting, previous);
         await adbRun('shell', 'settings', 'put', 'global', setting, '0');
      }
      const origin = fixture ? fixture.baseUrl.replace(/\/$/, '') : `http://127.0.0.1:${port}`;
      const deadline = Date.now() + 60_000;
      while (true) {
         if (serverError) throw serverError;
         if (server && server.exitCode !== null) throw new Error('Isolated QQQ demo exited before becoming ready.');
         if (await fetch(`${origin}/qqq/v1/qbit/mobile-scanners/status`).then((response) => response.ok).catch(() => false)) break;
         if (Date.now() > deadline) throw new Error('Isolated QQQ demo did not become ready. Build the qbit/demo and test classpath first.');
         await new Promise((done) => setTimeout(done, 250));
      }
      proxy = await startSemanticApiProxy(origin);
      await adbRun('reverse', '--no-rebind', `tcp:${proxy.port}`, `tcp:${proxy.port}`);
      reverseAdded = true;
      await adbRun('install', ...(physical ? [] : ['-r']), join(repository, 'scanner-host/build/outputs/apk/e2e/scanner-host-e2e.apk'));
      installedByRun.add(packageName);
      await adbRun('install', ...(physical ? [] : ['-r']), join(repository, 'scanner-host/build/outputs/apk/androidTest/e2e/scanner-host-e2e-androidTest.apk'));
      installedByRun.add(packageName + '.test');
      const evidence = join(repository, physical ? 'scanner-host/build/native-device-e2e' : 'scanner-host/build/native-e2e');
      await mkdir(evidence, { recursive: true });
      const reports = [];
      if (!selectedScenario) {
         for (const scenario of Object.keys(localScenarios)) await runLocalScenario(scenario);
      }
      if (!selectedScenario || selectedScenario === 'pixel-copy') {
         const output = await adbRun('shell', 'am', 'instrument', '-w', '-r',
            '-e', 'class', 'io.qrun.mobile.scanner.AppScreenCaptureTest',
            `${packageName}.test/androidx.test.runner.AndroidJUnitRunner`);
         await writeFile(join(evidence, 'pixel-copy-instrumentation.txt'), output);
         if (!/OK \(1 test\)/.test(output) || /FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(output)) {
            throw new Error(`Native PixelCopy E2E failed:\n${output}`);
         }
         console.log('Native PixelCopy E2E passed.');
      }
      for (const phase of [
         { mode: 'unsupported-api', expectedError: 'Unsupported QQQ UI API version.' },
         { mode: 'missing-extension', expectedError: 'This server is missing the mobile-scanners extension.' },
         { mode: 'missing-channel', expectedError: 'This server does not support the Android client channel.' },
         { mode: 'compatible' },
         { mode: 'additive' },
         { mode: 'compatible', name: 'admin-forms', test: 'adminFormsApproveRevokeAndCancel', requireCancel: true },
         { mode: 'compatible', name: 'session-monitor', test: 'sessionActivityMirrorsActualScreenAndAdminEndForcesFreshLogin', requireSession: true },
         ...(fixture ? [{ mode: 'compatible', name: 'photos', test: 'cameraAutoUploadsAndPendingGallerySurvivesRotationThenCleansOnRemoteLogout', requirePhotos: true }] : []),
      ]) {
         if (selectedScenario && selectedScenario !== (phase.name ?? phase.mode)) continue;
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
         reports.push({ scenario: phase.name ?? phase.mode, ...proxy.verify(!phase.expectedError, Boolean(phase.requireCancel), Boolean(phase.requireSession), Boolean(phase.requirePhotos)) });
         console.log(`Native semantic E2E passed: ${phase.name ?? phase.mode}.`);
      }
      await writeFile(join(evidence, 'semantic-contract.json'), `${JSON.stringify(reports, null, 2)}\n`);
      console.log('All native requests in the selected scenarios matched the published semantic API.');
   } finally {
      for (const [setting, previous] of animationScales) {
         if (previous === 'null') await adbRun('shell', 'settings', 'delete', 'global', setting).catch(() => {});
         else await adbRun('shell', 'settings', 'put', 'global', setting, previous).catch(() => {});
      }
      if (installedByRun.has(packageName)) await adbRun('shell', 'run-as', packageName, 'rm', '-f', 'files/e2e.json').catch(() => {});
      for (const installed of [...installedByRun].reverse()) await adbRun('uninstall', installed).catch(() => {});
      if (proxy) {
         if (reverseAdded) await adbRun('reverse', '--remove', `tcp:${proxy.port}`).catch(() => {});
         await proxy.close();
      }
      const stopped = new Promise((done) => { if (!server || server.exitCode !== null || serverError) done(); else server.once('exit', done); });
      server?.kill();
      await Promise.race([stopped, new Promise((_, reject) => {
         const timer = setTimeout(() => reject(new Error('Isolated demo did not stop; its temporary database was retained.')), 5_000);
         timer.unref();
      })]);
      if (directory && (dirname(resolve(directory)) !== resolve(tmpdir()) || !basename(directory).startsWith('qqq-native-e2e-'))) {
         throw new Error('Refusing cleanup outside the isolated demo temporary directory.');
      }
      if (directory) await rm(directory, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
   }

}
