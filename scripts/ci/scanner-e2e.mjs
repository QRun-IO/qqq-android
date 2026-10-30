// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Real native host + isolated QQQ demo. Never touches the normal scanner package.
import { spawn } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { mkdtemp, readFile, rm, mkdir, writeFile } from 'node:fs/promises';
import { createServer } from 'node:net';
import { tmpdir } from 'node:os';
import { basename, delimiter, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

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
   const deadline = setTimeout(() => { child.kill(); reject(new Error(`${command} exceeded the E2E timeout`)); }, 180_000);
   let output = '';
   child.stdout.on('data', (data) => { output += data; });
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
server.on('error', (error) => { serverError = error; });
try {
   const origin = `http://127.0.0.1:${port}`;
   const deadline = Date.now() + 60_000;
   while (true) {
      if (serverError) throw serverError;
      if (server.exitCode !== null) throw new Error('Isolated QQQ demo exited before becoming ready.');
      if (await fetch(`${origin}/mobile/v1/status`).then((response) => response.ok).catch(() => false)) break;
      if (Date.now() > deadline) throw new Error('Isolated QQQ demo did not become ready. Build the qbit/demo and test classpath first.');
      await new Promise((done) => setTimeout(done, 250));
   }
   await adbRun('reverse', `tcp:${port}`, `tcp:${port}`);
   await adbRun('install', '-r', join(repository, 'scanner-host/build/outputs/apk/e2e/scanner-host-e2e.apk'));
   await adbRun('install', '-r', join(repository, 'scanner-host/build/outputs/apk/androidTest/e2e/scanner-host-e2e-androidTest.apk'));
   await adbRun('shell', 'pm', 'clear', packageName);
   await adbRun('shell', 'run-as', packageName, 'mkdir', '-p', 'files');
   await run(adb, [...adbPrefix, 'shell', 'run-as', packageName, 'sh', '-c', "'cat > files/e2e.json'"],
      JSON.stringify({ serverUrl: `${origin}/`, adminPassword, scannerPassword }));
   const output = await adbRun('shell', 'am', 'instrument', '-w', '-r',
      '-e', 'class', 'io.qrun.mobile.scanner.ScannerEndToEndTest',
      `${packageName}.test/androidx.test.runner.AndroidJUnitRunner`);
   const evidence = join(repository, 'scanner-host/build/native-e2e');
   await mkdir(evidence, { recursive: true });
   await writeFile(join(evidence, 'instrumentation.txt'), output);
   if (!/OK \(1 test\)/.test(output) || /FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed/.test(output)) {
      throw new Error(`Native scanner E2E failed:\n${output}`);
   }
   console.log('Native scanner E2E passed: real login, enrollment, approval, app/process launch, barcode persistence, and revoked-device denial.');
} finally {
   await adbRun('shell', 'run-as', packageName, 'rm', '-f', 'files/e2e.json').catch(() => {});
   await adbRun('uninstall', `${packageName}.test`).catch(() => {});
   await adbRun('uninstall', packageName).catch(() => {});
   await adbRun('reverse', '--remove', `tcp:${port}`).catch(() => {});
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
