// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Test-only wire audit. It forwards to the real demo and never records headers or bodies.
import { createServer, request } from 'node:http';

const methods = new Set(['get', 'post', 'put', 'patch', 'delete', 'head', 'options']);
const escape = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

export async function startSemanticApiProxy(origin) {
   const response = await fetch(`${origin}/qqq/v1/openapi.json`);
   if (!response.ok) throw new Error('Cannot load the real server UI API document.');
   const document = await response.json();
   const operations = Object.entries(document.paths).flatMap(([path, item]) =>
      Object.entries(item).filter(([method]) => methods.has(method)).map(([method, operation]) => ({
         method: method.toUpperCase(), path, operationId: operation.operationId,
         matcher: new RegExp(`^${path.split('/').map((part) => /^\{[^}]+\}$/.test(part) ? '[^/]+' : escape(part)).join('/')}$`),
      })));
   let mode = 'compatible';
   let calls = [];
   const proxy = createServer((incoming, outgoing) => {
      const path = new URL(incoming.url, origin).pathname;
      const operation = operations.find((candidate) => candidate.method === incoming.method && candidate.matcher.test(path));
      const call = {
         method: incoming.method,
         path: operation?.path ?? path,
         operationId: operation?.operationId,
         credentials: Boolean(incoming.headers.authorization || incoming.headers.cookie || incoming.headers['x-qqq-device-token']),
         android: String(incoming.headers['x-qqq-client'] ?? '').startsWith('android/'),
      };
      calls.push(call);
      const upstream = request(new URL(incoming.url, origin), { method: incoming.method, headers: incoming.headers }, (result) => {
         call.status = result.statusCode;
         if (path === '/qqq/v1/version' && mode !== 'compatible') {
            const chunks = [];
            result.on('data', (chunk) => chunks.push(chunk));
            result.on('end', () => {
               try {
                  const version = JSON.parse(Buffer.concat(chunks).toString('utf8'));
                  if (mode === 'unsupported-api') version.apiVersion = '2.0.0';
                  if (mode === 'missing-extension') version.extensions = (version.extensions ?? []).filter((extension) => extension.name !== 'mobile-scanners');
                  if (mode === 'missing-channel') version.clientChannels = (version.clientChannels ?? []).filter((channel) => channel !== 'android');
                  if (mode === 'additive') {
                     version.apiVersion = '1.99.0';
                     version.futureOptionalProperty = { supportedByNewerClients: true };
                     version.features = [...(version.features ?? []), 'futureOptionalFeature'];
                     version.extensions = [...(version.extensions ?? []), { name: 'future-extension', version: '1.0.0', pathPrefix: '/qbit/future-extension' }];
                  }
                  const body = JSON.stringify(version);
                  const headers = { ...result.headers, 'content-length': Buffer.byteLength(body) };
                  delete headers['transfer-encoding'];
                  outgoing.writeHead(result.statusCode, headers);
                  outgoing.end(body);
               } catch {
                  outgoing.writeHead(502);
                  outgoing.end();
               }
            });
         } else {
            outgoing.writeHead(result.statusCode, result.headers);
            result.pipe(outgoing);
         }
      });
      upstream.on('error', () => { outgoing.writeHead(502); outgoing.end(); });
      incoming.pipe(upstream);
   });
   await new Promise((done, reject) => { proxy.once('error', reject); proxy.listen(0, '127.0.0.1', done); });
   return {
      port: proxy.address().port,
      setMode(value) { mode = value; calls = []; },
      evidence() { return calls.map(({ method, operationId, credentials, android, status }) => ({ method, operationId, credentials, android, status })); },
      verify(compatible, requireCancel = false) {
         if (!calls.length || calls[0].path !== '/qqq/v1/version' || calls[0].credentials) throw new Error('Native startup must discover compatibility before sending credentials or other requests.');
         if (calls.some((call) => !call.operationId)) throw new Error('Native flow made a request outside the published semantic API contract.');
         if (!compatible && calls.some((call) => call.path !== '/qqq/v1/version' || call.credentials)) throw new Error('An incompatible server received requests beyond public version discovery.');
         if (compatible) {
            for (const path of ['/qqq/v1/metaData/authentication', '/qqq/v1/metaData', '/qqq/v1/qbit/mobile-scanners/devices/enroll', '/qqq/v1/qbit/mobile-scanners/devices/current']) {
               if (!calls.some((call) => call.path === path && call.android)) throw new Error(`Missing native semantic operation: ${path}`);
            }
         }
         if (requireCancel && !calls.some((call) => call.android && call.method === 'POST' && call.path.endsWith('/cancel'))) {
            throw new Error('Native process cancellation did not reach the semantic API.');
         }
         return { mode, requests: calls.length, operations: [...new Set(calls.map((call) => call.operationId))].sort(), documentedRequests: calls.length };
      },
      async close() {
         proxy.closeAllConnections();
         await new Promise((done) => proxy.close(done));
      },
   };
}
