/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
import assert from "node:assert/strict"
import { mkdtempSync, readFileSync, readdirSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import test from "node:test"
import { EHS_PACKAGE, MANAGED_ORIENTATIONS, generate, managedServerUrl, validate, xml } from "./generate-company-profile.mjs"

const example = () => JSON.parse(readFileSync(new URL("./profile.example.json", import.meta.url), "utf8"))
const measured = () => {
   const p = example()
   Object.assign(p.device, { androidVersion: "14", mxVersion: "14.0.0.0", osBuild: "measured-build" })
   p.ehs.installedVersion = "8.0"
   p.qqq.endpoint = "https://qqq.company.test"
   p.permittedApps.push({ label: "Camera", package: "com.example.camera", activity: null, icon: null })
   p.deploy = [{ package: EHS_PACKAGE, action: "Install", apk: "/enterprise/usr/persist/EHS.apk" }]
   return p
}
const out = () => mkdtempSync(join(tmpdir(), "company-profile-"))

test("example is valid but reports every unmeasured input and withholds MX output", () => {
   const dir = out()
   const report = generate(example(), dir)
   assert.deepEqual(report.errors, [])
   assert.equal(report.ready, false)
   assert.ok(report.missing.some((m) => m.startsWith("device.mxVersion")))
   assert.ok(report.missing.some((m) => m.startsWith("permittedApps")))
   assert.ok(report.missing.some((m) => m.startsWith("qqq.endpoint")))
   assert.deepEqual(readdirSync(dir).sort(), ["enterprisehomescreen.xml", "profile-report.json"])
})

test("measured profile is ready and emits documented EHS and MX elements only", () => {
   const dir = out()
   const report = generate(measured(), dir)
   assert.deepEqual(report.errors, [])
   assert.equal(report.ready, true)
   const ehs = readFileSync(join(dir, "enterprisehomescreen.xml"), "utf8")
   assert.match(ehs, /<title><value>Company \[serial_number\]<\/value><\/title>/)
   assert.match(ehs, /<wallpaper>\/enterprise\/usr\/company\/wallpaper\.png<\/wallpaper>/)
   assert.match(ehs, /<usb_debugging_disabled>1<\/usb_debugging_disabled>/)
   assert.match(ehs, /<kiosk_mode_enabled>0<\/kiosk_mode_enabled>/)
   assert.match(ehs, /<admin><\/admin>/)
   const mx = readFileSync(join(dir, "stagenow-mx-appmgr.xml"), "utf8")
   assert.match(mx, /value="SetDefaultLauncher"/)
   assert.match(mx, new RegExp(`name="Package" value="${EHS_PACKAGE.replace(/\./g, "\\.")}"`))
   assert.doesNotMatch(ehs, /<admin>[^<]+<\/admin>/)
   assert.doesNotMatch(mx + ehs, /token|secret/i)
})

test("security-weakening or undocumented inputs are rejected", () => {
   const cases = [
      (p) => { p.permittedApps.push({ label: "All", package: "com.android.*" }) },
      (p) => { p.permittedApps.push({ label: "Wild", package: "com.mobility.*" }) },
      (p) => { p.launcher.wallpaper = "/sdcard/wallpaper.png" },
      (p) => { p.launcher.wallpaper = "/enterprise/usr/../x.png" },
      (p) => { p.qqq.endpoint = "http://qqq.company.test" },
      (p) => { p.qqq.endpoint = "https://user:pw@qqq.company.test" },
      (p) => { p.launcher.title = "Dynamic [username]" },
      (p) => { p.restrictions.systemSettingsRestricted = 2 },
      (p) => { delete p.restrictions.recentAppsAccessDisabled },
      (p) => { p.launcher.iconSize = "XL" },
      (p) => { p.permittedApps.push({ label: "A very long label over limit", package: "com.example.long" }) },
      (p) => { p.permittedApps = [{ label: "Other", package: "com.example.other" }] },
      (p) => { p.deploy = [{ package: "com.evil.app", action: "Install", apk: "/enterprise/usr/persist/x.apk" }] },
   ]
   for (const mutate of cases) {
      const p = measured()
      mutate(p)
      assert.ok(validate(p).errors.length > 0, mutate.toString())
   }
})

test("managed configuration uses the app's exact keys and is withheld for a placeholder endpoint", () => {
   const withheld = out()
   const placeholder = generate(example(), withheld)
   assert.ok(!readdirSync(withheld).includes("managed-configuration.json"))
   assert.ok(placeholder.warnings.some((w) => w.startsWith("managed-configuration.json withheld")))

   const dir = out()
   const p = measured()
   p.qqq.endpoint = " https://QQQ.Company.test "
   p.qqq.orientation = "REVERSE_LANDSCAPE"
   assert.deepEqual(generate(p, dir).errors, [])
   assert.deepEqual(JSON.parse(readFileSync(join(dir, "managed-configuration.json"), "utf8")), {
      qqq_server_url: "https://qqq.company.test/",
      qqq_orientation: "REVERSE_LANDSCAPE",
   })

   const noOrientation = out()
   generate(measured(), noOrientation)
   assert.deepEqual(Object.keys(JSON.parse(readFileSync(join(noOrientation, "managed-configuration.json"), "utf8"))), ["qqq_server_url"])
})

test("managed server URL accepts only an https origin, mirroring the app", () => {
   assert.equal(managedServerUrl("https://qqq.company.test"), "https://qqq.company.test/")
   assert.equal(managedServerUrl("https://qqq.company.test:8443/"), "https://qqq.company.test:8443/")
   for (const bad of ["http://qqq.company.test", "https://u:p@qqq.company.test", "https://qqq.company.test/app", "https://qqq.company.test/?x=1", "https://qqq.company.test/#f", "ftp://qqq.company.test", "nonsense", "", null, 7, `https://${"a".repeat(2100)}`]) {
      assert.equal(managedServerUrl(bad), null, String(bad))
      const p = measured()
      p.qqq.endpoint = bad
      assert.ok(validate(p).errors.length > 0, String(bad))
   }
})

test("orientation must be an exact app value", () => {
   for (const good of MANAGED_ORIENTATIONS) {
      const p = measured()
      p.qqq.orientation = good
      assert.deepEqual(validate(p).errors, [])
   }
   for (const bad of ["portrait", "Landscape", "AUTO", "SENSOR", "", 90]) {
      const p = measured()
      p.qqq.orientation = bad
      assert.ok(validate(p).errors.length > 0, String(bad))
   }
})

test("XML special characters in labels are escaped", () => {
   assert.equal(xml(`a&b<c>"d"'e'`), "a&amp;b&lt;c&gt;&quot;d&quot;&apos;e&apos;")
   const p = measured()
   p.permittedApps[1].label = "Pick & <Scan>"
   const dir = out()
   assert.deepEqual(generate(p, dir).errors, [])
   assert.match(readFileSync(join(dir, "enterprisehomescreen.xml"), "utf8"), /label="Pick &amp; &lt;Scan&gt;"/)
})
