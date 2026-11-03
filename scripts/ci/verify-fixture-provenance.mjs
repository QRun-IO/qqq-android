// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
// Inspect Maven's resolved runtime closure; never resolve or substitute dependencies here.
import { createHash } from 'node:crypto';
import { readFileSync, realpathSync, writeFileSync } from 'node:fs';
import { delimiter, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { execFileSync } from 'node:child_process';

const [serverArgument, repositoryArgument, coreSha, serverSha] = process.argv.slice(2);
if (!serverArgument || !repositoryArgument || !/^[a-f0-9]{40}$/.test(coreSha ?? '') ||
      !/^[a-f0-9]{40}$/.test(serverSha ?? '')) {
   throw new Error('Usage: verify-fixture-provenance.mjs server private-m2 core-sha server-sha');
}
const server = resolve(serverArgument);
const repository = realpathSync(repositoryArgument);
const revision = `4.1.0-ci.${coreSha}`;
const requiredModules = new Set([
   'qqq-backend-core', 'qqq-backend-module-rdbms', 'qqq-middleware-javalin', 'qqq-openapi',
]);
const seenModules = new Set();
const seenClasses = new Map();
const classpath = readFileSync(join(server, 'demo/target/e2e-classpath.txt'), 'utf8').trim().split(delimiter);
if (classpath.length === 0 || classpath.some(entry => !entry.endsWith('.jar'))) {
   throw new Error('Runtime classpath must contain only resolved JARs');
}
const artifacts = classpath.map(entry => {
   const file = realpathSync(entry);
   const relativePath = relative(repository, file);
   if (isAbsolute(relativePath) || relativePath.startsWith(`..${sep}`) || relativePath === '..' || resolve(repository, relativePath) !== file) {
      throw new Error('Runtime artifact is outside the private Maven repository');
   }
   const coordinatePath = relativePath.split(sep).join('/');
   if (coordinatePath.startsWith('com/kingsrook/qqq/') && !coordinatePath.startsWith('com/kingsrook/qqq/qqq-frontend-next/')) {
      const [, , , module, version] = coordinatePath.split('/');
      if (version !== revision || seenModules.has(module)) {
         throw new Error(`Unexpected or repeated QQQ module: ${module}/${version}`);
      }
      seenModules.add(module);
   }
   // Inspect every QQQ-owned JAR, including the frontend and scanner, for shadowed backend classes.
   if (coordinatePath.startsWith('com/kingsrook/')) {
      const jar = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'jar.exe' : 'jar') : 'jar';
      const entries = execFileSync(jar, ['tf', file], { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
      for (const name of entries.split(/\r?\n/).filter(name => name.startsWith('com/kingsrook/') && name.endsWith('.class'))) {
         if (seenClasses.has(name)) throw new Error(`Duplicate QQQ class: ${name}`);
         seenClasses.set(name, coordinatePath);
      }
   }
   return { path: coordinatePath, sha256: createHash('sha256').update(readFileSync(file)).digest('hex') };
});
for (const module of requiredModules) {
   if (!seenModules.has(module)) throw new Error(`Required runtime module missing: ${module}`);
}
const h2Artifacts = artifacts.filter(artifact => artifact.path.startsWith('com/h2database/h2/'));
if (h2Artifacts.length !== 1 || h2Artifacts[0].path !== 'com/h2database/h2/2.2.224/h2-2.2.224.jar') {
   throw new Error('Expected exactly the qualified runtime H2 2.2.224 artifact');
}
const guavaArtifacts = artifacts.filter(artifact => artifact.path.startsWith('com/google/guava/guava/'));
if (guavaArtifacts.length !== 1 || guavaArtifacts[0].path !== 'com/google/guava/guava/33.7.2-jre/guava-33.7.2-jre.jar') {
   throw new Error('Expected exactly the qualified runtime Guava 33.7.2-jre artifact');
}
writeFileSync(join(server, 'demo/target/fixture-provenance.json'), `${JSON.stringify({
   coreSha, serverSha, revision, artifacts, qqqClassCount: seenClasses.size, duplicateQqqClasses: 0,
}, null, 2)}\n`);
console.log(`Verified ${artifacts.length} runtime JAR hashes and ${seenClasses.size} unique QQQ classes from the pinned source closure.`);
