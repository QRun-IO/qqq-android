// Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0
import { execFileSync, spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

/*******************************************************************************
 ** Enforce mechanical source rules without rewriting language-specific syntax.
 ** Generated build output and upstream wrappers are outside this authored-source
 ** inventory; resource artwork keeps its separately documented provenance.
 *******************************************************************************/
export function sourceFindings(file, source)
{
   const findings = [];
   const header = source.slice(0, 2500);
   if(!/copyright/i.test(header)) findings.push("K22-CODE-24: missing copyright header");
   if(!/SPDX-License-Identifier: Apache-2.0/.test(header)) findings.push("K22-CODE-24: missing Apache-2.0 SPDX header");
   if(source.includes("\r")) findings.push("K22-CODE-07: line endings must be LF");
   if(!source.endsWith("\n")) findings.push("K22-CODE-07: final newline required");
   source.split("\n").forEach((line, index) =>
   {
      if(/[ \t]+$/.test(line)) findings.push(`${index + 1}: K22-CODE-07: trailing whitespace`);
      if(line.includes("\t")) findings.push(`${index + 1}: K22-CODE-03: tabs are forbidden`);
      if(/\.(?:kt|java)$/.test(file) && /^\s*import\s+.*\.\*/.test(line)) findings.push(`${index + 1}: K22-CODE-14: wildcard import`);
   });
   return findings;
}



/*******************************************************************************
 ** Read Git's authored file list so caches and generated outputs never weaken
 ** the rules for maintained source. Parse each JavaScript tool without running
 ** its device, network or process operations.
 *******************************************************************************/
export function checkRepository(repository)
{
   const files = [...new Set(execFileSync("git", ["-C", repository, "ls-files", "--cached", "--others", "--exclude-standard", "-z"], { encoding: "utf8" }).split("\0"))]
      .filter(file => /\.(?:kt|kts|java|mjs|js|sh)$/.test(file));
   const findings = [];
   for(const file of files)
   {
      const absolute = resolve(repository, file);
      let source;
      try
      {
         source = new TextDecoder("utf-8", { fatal: true }).decode(readFileSync(absolute));
      }
      catch(error)
      {
         findings.push(`${file}: K22-CODE-07: source must be readable UTF-8`);
         continue;
      }
      findings.push(...sourceFindings(file, source).map(finding => `${file}: ${finding}`));
      if(/\.(?:mjs|js)$/.test(file))
      {
         const result = spawnSync(process.execPath, ["--check", absolute], { encoding: "utf8" });
         if(result.status !== 0) findings.push(`${file}: JavaScript syntax check failed\n${result.stderr ?? ""}`);
      }
   }
   return { files: files.length, findings };
}



if(process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url))
{
   const repository = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
   const result = checkRepository(repository);
   if(result.findings.length)
   {
      process.stderr.write(result.findings.join("\n") + "\n");
      process.exitCode = 1;
   }
   else
   {
      process.stdout.write(`Source standards passed for ${result.files} authored source files.\n`);
   }
}
