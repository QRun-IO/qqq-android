// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Opt-in real Authentik native acceptance on ONE physical Zebra TC22 using only the disposable E2E package.
// See docs/REAL-AUTHENTIK-ACCEPTANCE.md. Never touches the normal scanner package or its data, reverse tcp:8080, any
// reverse mapping it did not add, or any trust store; never prints the serial, config, cookies or URLs with queries.
import { spawn } from 'node:child_process';
import { access, readFile } from 'node:fs/promises';
import { dirname, join, relative, resolve, isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';

export const E2E_PACKAGE = 'io.qrun.mobile.scanner.e2e';
export const TEST_PACKAGE = `${E2E_PACKAGE}.test`;
export const TEST_CLASS = 'io.qrun.mobile.scanner.RealAuthentikAcceptanceTest';
export const REVERSE_PORTS = ['18443', '19443'];
export const QQQ_ORIGIN = 'https://localhost:18443';
const INSTRUMENT_MS = 40 * 60_000;

/** Operator-facing text for the fixed stage names the instrumentation emits; nothing dynamic is ever printed. */
export const STAGES = {
   'login-screen-verified': 'Browser login offered, no password fallback.',
   'browser-A-requested': 'Opening external browser for operator A.',
   'browser-A-operator-enter-credentials': 'ACTION: in the browser sign in as scanner-a (type the credentials yourself).',
   'callback-exchange-A-complete': 'Callback and QQQ exchange completed for A.',
   'device-pending-A': 'Device is pending approval (expected).',
   'operator-A-verified': 'Principal A verified, session ACTIVE.',
   'company-signout-requested': 'Company sign-out requested.',
   'company-signout-operator-confirm-if-asked': 'ACTION: confirm provider logout in the browser if it asks.',
   'company-signout-verified': 'Company sign-out reported complete.',
   'operator-A-qqq-session-ended': 'QQQ session for A ended.',
   'browser-B-requested': 'Opening external browser for operator B.',
   'browser-B-operator-enter-credentials': 'ACTION: the provider must ask for credentials; sign in as scanner-b.',
   'callback-exchange-B-complete': 'Callback and QQQ exchange completed for B.',
   'operator-B-verified': 'Principal B verified, distinct session ACTIVE.',
};

/*******************************************************************************
 ** Admit only the exact fixture descriptor, before any adb call. Values are never echoed in errors.
 *******************************************************************************/
export function validateConfig(config) {
   const text = (value, max) => typeof value === 'string' && value.length > 0 && value.length <= max && !/[\r\n\u0000]/.test(value);
   if (config?.fixtureType !== 'real-authentik-android' || config.qqqOrigin !== QQQ_ORIGIN) throw new Error('Config must describe the real-authentik-android fixture on its exact HTTPS origin.');
   if (!text(config.adminCookie, 4096)) throw new Error('Config needs the administrator session cookie header value.');
   const a = config.operators?.A?.display, b = config.operators?.B?.display;
   if (!text(a, 200) || !text(b, 200) || a === b) throw new Error('Config needs distinct display names for operators A and B.');
   for (const key of ['A', 'B']) {
      const actor = config.operators[key].actor;
      if (actor !== undefined && !text(actor, 200)) throw new Error('Operator actor values must be short single-line text.');
   }
   if (config.sessionActorField !== undefined && !/^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(config.sessionActorField)) throw new Error('sessionActorField must be a column name.');
   return config;
}

/*******************************************************************************
 ** The config holds a live administrator session, so it must never sit inside a repository checkout.
 *******************************************************************************/
export function configOutsideRepository(configPath, repository) {
   const relativePath = relative(resolve(repository), resolve(configPath));
   return relativePath.startsWith('..') || isAbsolute(relativePath);
}

/*******************************************************************************
 ** Only the physical Zebra TC22 is a valid target; an emulator or any other device is refused.
 *******************************************************************************/
export function validateTarget(serial, properties) {
   if (!serial || !/^[A-Za-z0-9._:-]+$/.test(serial) || serial.startsWith('emulator-')) throw new Error('ANDROID_SERIAL must name the physical TC22 (emulators are prohibited).');
   if (properties.qemu === '1' || !/^zebra/i.test(properties.manufacturer ?? '') || !/^TC22/i.test(properties.model ?? '')) throw new Error('The selected device is not the physical Zebra TC22.');
}

/*******************************************************************************
 ** Disposable packages are never replaced or reused; the normal package is not consulted.
 *******************************************************************************/
export function refuseExistingDisposablePackages(installedLines) {
   const installed = new Set(installedLines.map(line => line.trim()));
   for (const pkg of [E2E_PACKAGE, TEST_PACKAGE]) {
      if (installed.has(`package:${pkg}`)) throw new Error(`${pkg} already exists. Remove it deliberately before this run; it will not be replaced.`);
   }
}

/*******************************************************************************
 ** Parse `adb reverse --list` into "tcp:A tcp:B" mappings (the device-name column is ignored).
 *******************************************************************************/
export function parseReverseList(output) {
   return output.split(/\r?\n/).map(line => line.trim().split(/\s+/)).filter(parts => parts.length >= 3).map(parts => `${parts[parts.length - 2]} ${parts[parts.length - 1]}`);
}

/*******************************************************************************
 ** Decide which of the two fixture ports to add. A pre-existing identical mapping is left in place and not owned;
 ** a differing one is refused rather than changed. Any other mapping (including 8080) is never considered.
 *******************************************************************************/
export function planReverse(existing) {
   const add = [];
   for (const port of REVERSE_PORTS) {
      const mappings = existing.filter(mapping => mapping.startsWith(`tcp:${port} `));
      if (mappings.length === 0) add.push(port);
      else if (mappings.some(mapping => mapping !== `tcp:${port} tcp:${port}`)) throw new Error(`Existing reverse mapping for ${port} differs; refusing to change it.`);
   }
   return add;
}

/*******************************************************************************
 ** Classify one instrumentation output line: a fixed stage, the final verdict, a safe failure sentence, or nothing.
 ** Anything URL-like or query-like is dropped, so no callback, token or credential can be printed.
 *******************************************************************************/
export function classifyLine(line) {
   const stage = /^INSTRUMENTATION_STATUS: qqqStage=([A-Za-z0-9-]+)$/.exec(line);
   if (stage) return { stage: stage[1], text: STAGES[stage[1]] ?? `Stage ${stage[1]}` };
   if (/^(OK \(\d+ tests?\)|FAILURES!!!|Tests run:.*)$/.test(line)) return { verdict: line };
   const assertion = /AssertionError: ([^\r\n]{1,160})/.exec(line);
   if (assertion && !/[?&=]|https?:/i.test(assertion[1])) return { failure: assertion[1] };
   return {};
}

/*******************************************************************************
 ** The instrumentation command line the launcher runs; fixed, with no value from the config.
 *******************************************************************************/
export function instrumentArguments() {
   return ['shell', 'am', 'instrument', '-w', '-r', '-e', 'realAuthentik', 'true', '-e', 'class', TEST_CLASS, `${TEST_PACKAGE}/androidx.test.runner.AndroidJUnitRunner`];
}

/*******************************************************************************
 ** Run adb for the selected device without a shell; never echo its output.
 *******************************************************************************/
function adb(adbPath, serial, argv, { input, timeout = 120_000, onLine } = {}) {
   return new Promise((done, reject) => {
      const child = spawn(adbPath, ['-s', serial, ...argv], { stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true });
      let out = '', pending = '';
      const timer = setTimeout(() => { child.kill(); reject(new Error('adb operation timed out')); }, timeout);
      child.stdout.on('data', chunk => {
         out += chunk;
         if (onLine) { pending += chunk; const lines = pending.split(/\r?\n/); pending = lines.pop(); lines.forEach(onLine); }
      });
      child.stderr.on('data', () => {});
      child.on('error', () => { clearTimeout(timer); reject(new Error('adb could not start')); });
      child.on('close', code => { clearTimeout(timer); code === 0 ? done(out) : reject(new Error(`adb exited ${code}`)); });
      child.stdin.end(input ?? '');
   });
}

/*******************************************************************************
 ** Orchestrate one run. Cleanup removes only what this run added: the config file, the two disposable packages
 ** and the reverse mappings it created. The operator's trust store is never touched.
 *******************************************************************************/
async function main(args, environment) {
   const option = name => { const index = args.indexOf(name); return index < 0 ? undefined : args[index + 1]; };
   const repository = resolve(option('--repo') ?? join(dirname(fileURLToPath(import.meta.url)), '../..'));
   const serial = environment.ANDROID_SERIAL;
   const sdk = environment.ANDROID_HOME ?? environment.ANDROID_SDK_ROOT;
   const adbPath = sdk ? join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb') : 'adb';
   const say = text => console.log(String(text).replaceAll(serial ?? '\u0000', '<DEVICE>'));

   if (!args.includes('--confirm-temporary-ca-installed')) throw new Error('Install the temporary test CA through Android settings, then pass --confirm-temporary-ca-installed.');
   const configPath = option('--config') ?? (() => { throw new Error('--config <owner-private json> is required.'); })();
   if (!configOutsideRepository(configPath, repository)) throw new Error('Keep the config outside the repository checkout.');
   const configText = await readFile(configPath, 'utf8');
   let config;
   try { config = JSON.parse(configText); } catch { throw new Error('Config is not valid JSON.'); }
   validateConfig(config);
   const apks = [
      join(repository, 'scanner-host/build/outputs/apk/e2e/scanner-host-e2e.apk'),
      join(repository, 'scanner-host/build/outputs/apk/androidTest/e2e/scanner-host-e2e-androidTest.apk'),
   ];
   for (const apk of apks) await access(apk).catch(() => { throw new Error('Build :scanner-host:assembleE2e :scanner-host:assembleE2eAndroidTest first.'); });

   if (!serial || !/^[A-Za-z0-9._:-]+$/.test(serial) || serial.startsWith('emulator-')) throw new Error('ANDROID_SERIAL must name the physical TC22 (emulators are prohibited).');
   const property = async name => (await adb(adbPath, serial, ['shell', 'getprop', name])).trim();
   validateTarget(serial, { qemu: await property('ro.kernel.qemu'), manufacturer: await property('ro.product.manufacturer'), model: await property('ro.product.model') });
   say(`Device: physical TC22, Android ${await property('ro.build.version.release')}`);

   const installed = (await adb(adbPath, serial, ['shell', 'pm', 'list', 'packages', '-u'])).split(/\r?\n/);
   refuseExistingDisposablePackages(installed);
   say('Disposable packages are absent; the normal scanner app is neither read nor changed.');

   const reverseList = async () => parseReverseList(await adb(adbPath, serial, ['reverse', '--list']));
   const before = await reverseList();
   const owned = planReverse(before);
   const installedHere = [];
   let configPushed = false;
   try {
      for (const port of owned) await adb(adbPath, serial, ['reverse', '--no-rebind', `tcp:${port}`, `tcp:${port}`]);
      say(`Reverse mappings added by this run: ${owned.length ? owned.join(', ') : 'none (already present, left in place)'}.`);
      for (const [apk, pkg] of [[apks[0], E2E_PACKAGE], [apks[1], TEST_PACKAGE]]) {
         await adb(adbPath, serial, ['install', '--no-streaming', apk], { timeout: 300_000 });
         installedHere.push(pkg);
      }
      await adb(adbPath, serial, ['shell', 'run-as', E2E_PACKAGE, 'mkdir', '-p', 'files']);
      await adb(adbPath, serial, ['shell', 'run-as', E2E_PACKAGE, 'sh', '-c', "'cat > files/real-auth.json'"], { input: configText });
      configPushed = true;

      say('Starting. Keep the TC22 unlocked and awake; follow the ACTION lines.');
      let verdict = '', failure = '';
      await adb(adbPath, serial, instrumentArguments(), {
         timeout: INSTRUMENT_MS,
         onLine: line => {
            const result = classifyLine(line);
            if (result.stage) say(result.text);
            else if (result.verdict) verdict = result.verdict;
            else if (result.failure && !failure) failure = result.failure;
         },
      });
      if (!/^OK \(/.test(verdict)) throw new Error(`Acceptance did not pass${failure ? `: ${failure}` : '.'}`);
      say(`PASS: ${verdict}`);
   } finally {
      if (configPushed) await adb(adbPath, serial, ['shell', 'run-as', E2E_PACKAGE, 'rm', '-f', 'files/real-auth.json']).catch(() => say('WARNING: could not remove the pushed config; uninstalling the package removes it.'));
      for (const pkg of [...installedHere].reverse()) await adb(adbPath, serial, ['uninstall', pkg]).catch(() => say(`WARNING: could not uninstall ${pkg}.`));
      for (const port of owned) await adb(adbPath, serial, ['reverse', '--remove', `tcp:${port}`]).catch(() => say(`WARNING: could not remove reverse ${port}.`));
      const after = await reverseList().catch(() => null);
      if (after && JSON.stringify([...after].sort()) !== JSON.stringify([...before].sort())) say('WARNING: reverse mappings differ from the starting set; review manually.');
      say('Reminder: remove the temporary test CA through Android settings when finished.');
   }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
   main(process.argv.slice(2), process.env).catch(error => {
      console.error(`FAILED: ${String(error.message).replaceAll(process.env.ANDROID_SERIAL ?? '\u0000', '<DEVICE>')}`);
      process.exitCode = 1;
   });
}
