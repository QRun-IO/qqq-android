// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
import assert from 'node:assert/strict';
import test from 'node:test';
import {
   classifyLine, configOutsideRepository, instrumentArguments, parseReverseList, planReverse, refuseExistingDisposablePackages,
   validateConfig, validateTarget,
} from './real-authentik-android.mjs';

const config = () => ({
   fixtureType: 'real-authentik-android', qqqOrigin: 'https://localhost:18443', adminCookie: 'synthetic-cookie-value',
   operators: { A: { display: 'Operator A' }, B: { display: 'Operator B' } },
});

/*******************************************************************************
 ** A wrong origin, fixture type or identical operators must stop the run before any adb call.
 *******************************************************************************/
test('config is admitted only for the exact fixture origin with distinct operators', () => {
   assert.doesNotThrow(() => validateConfig(config()));
   for (const bad of [
      { ...config(), qqqOrigin: 'https://localhost:18443/' }, { ...config(), qqqOrigin: 'http://localhost:18443' },
      { ...config(), qqqOrigin: 'https://127.0.0.1:18443' }, { ...config(), fixtureType: 'echo-ui-e2e' }, { ...config(), adminCookie: '' },
      { ...config(), adminCookie: 'a\nb' }, { ...config(), operators: { A: { display: 'Same' }, B: { display: 'Same' } } },
      { ...config(), sessionActorField: 'a; DROP' },
   ]) assert.throws(() => validateConfig(bad));
});

/*******************************************************************************
 ** Errors must not echo the secret they reject.
 *******************************************************************************/
test('a rejected config never leaks the cookie into the error', () => {
   const bad = { ...config(), adminCookie: 'secret-cookie\nvalue' };
   assert.throws(() => validateConfig(bad), error => !error.message.includes('secret-cookie'));
});

/*******************************************************************************
 ** The config holds a live administrator session; a checkout path must be refused.
 *******************************************************************************/
test('config inside the repository is refused, outside is allowed', () => {
   assert.equal(configOutsideRepository('/work/repo/scripts/config.json', '/work/repo'), false);
   assert.equal(configOutsideRepository('/work/repo/config.json', '/work/repo'), false);
   assert.equal(configOutsideRepository('/work/private/config.json', '/work/repo'), true);
   assert.equal(configOutsideRepository('/work/repository-other/config.json', '/work/repo'), true);
});

/*******************************************************************************
 ** Only the physical Zebra TC22 may be targeted.
 *******************************************************************************/
test('emulators and other devices are refused as targets', () => {
   assert.doesNotThrow(() => validateTarget('SYNTHETIC_SERIAL', { qemu: '', manufacturer: 'Zebra Technologies', model: 'TC22' }));
   assert.throws(() => validateTarget('emulator-5554', { qemu: '1', manufacturer: 'Google', model: 'sdk' }));
   assert.throws(() => validateTarget('SYNTHETIC_SERIAL', { qemu: '1', manufacturer: 'Zebra', model: 'TC22' }));
   assert.throws(() => validateTarget('SYNTHETIC_SERIAL', { qemu: '', manufacturer: 'Samsung', model: 'TC22' }));
   assert.throws(() => validateTarget('SYNTHETIC_SERIAL', { qemu: '', manufacturer: 'Zebra', model: 'TC27' }));
   assert.throws(() => validateTarget('bad serial; rm', { qemu: '', manufacturer: 'Zebra', model: 'TC22' }));
   assert.throws(() => validateTarget(undefined, { qemu: '', manufacturer: 'Zebra', model: 'TC22' }));
});

/*******************************************************************************
 ** An existing disposable package could hold another run's state; it is never replaced.
 *******************************************************************************/
test('existing disposable packages are refused and the normal package is irrelevant', () => {
   assert.doesNotThrow(() => refuseExistingDisposablePackages(['package:io.qrun.mobile.scanner', 'package:com.android.chrome']));
   assert.throws(() => refuseExistingDisposablePackages(['package:io.qrun.mobile.scanner.e2e']));
   assert.throws(() => refuseExistingDisposablePackages(['package:io.qrun.mobile.scanner.e2e.test']));
});

/*******************************************************************************
 ** Only 18443 and 19443 may ever be added; 8080 and every other mapping are invisible to the plan.
 *******************************************************************************/
test('reverse plan adds only missing fixture ports and never considers other mappings', () => {
   const existing = parseReverseList('host-1 tcp:8080 tcp:57832\nhost-1 tcp:18081 tcp:18081\n');
   assert.deepEqual(planReverse(existing), ['18443', '19443']);
   assert.deepEqual(planReverse(parseReverseList('host-1 tcp:18443 tcp:18443\n')), ['19443']);
   assert.deepEqual(planReverse(parseReverseList('host-1 tcp:18443 tcp:18443\nhost-1 tcp:19443 tcp:19443\n')), []);
   assert.throws(() => planReverse(parseReverseList('host-1 tcp:19443 tcp:9999\n')));
   assert.ok(planReverse([]).every(port => ['18443', '19443'].includes(port)));
});

/*******************************************************************************
 ** Output handling must map fixed stages and drop anything carrying a URL or query.
 *******************************************************************************/
test('instrumentation output exposes only fixed stages, the verdict and a safe failure', () => {
   assert.deepEqual(classifyLine('INSTRUMENTATION_STATUS: qqqStage=operator-A-verified'), { stage: 'operator-A-verified', text: 'Principal A verified, session ACTIVE.' });
   assert.deepEqual(classifyLine('OK (1 test)'), { verdict: 'OK (1 test)' });
   assert.deepEqual(classifyLine('java.lang.AssertionError: Native sign-in did not complete'), { failure: 'Native sign-in did not complete' });
   assert.deepEqual(classifyLine('java.lang.AssertionError: bad https://host/cb?code=abc'), {});
   assert.deepEqual(classifyLine('INSTRUMENTATION_STATUS: stack=https://localhost:19443/x?code=secret'), {});
   assert.deepEqual(classifyLine('INSTRUMENTATION_STATUS: qqqStage=bad stage'), {});
});

/*******************************************************************************
 ** The launched class is fixed, opts in explicitly and carries nothing from the config.
 *******************************************************************************/
test('instrumentation targets only the opt-in class in the disposable test package', () => {
   const joined = instrumentArguments().join(' ');
   assert.match(joined, /-e realAuthentik true/);
   assert.match(joined, /io\.qrun\.mobile\.scanner\.e2e\.test\/androidx\.test\.runner\.AndroidJUnitRunner$/);
   assert.equal(joined.includes('cookie'), false);
});
