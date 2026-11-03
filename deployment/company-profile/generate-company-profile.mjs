#!/usr/bin/env node
/* Copyright (c) 2026 QRunIO. SPDX-License-Identifier: Apache-2.0 */
// Renders a reviewable Zebra Enterprise Home Screen (EHS) config and, when the device is measured, a StageNow/MX
// AppMgr document from one non-secret JSON profile. Only elements documented by Zebra are emitted; see
// docs/COMPANY-PROFILE.md for the page citations. Nothing here installs, pushes or changes a device.
//
// Usage: node generate-company-profile.mjs <profile.json> <output-dir> [--require-ready]

import { mkdirSync, readFileSync, writeFileSync } from "node:fs"
import { join } from "node:path"
import { pathToFileURL } from "node:url"

export const EHS_PACKAGE = "com.zebra.mdna.enterprisehomescreen"
export const EHS_ACTIVITY = "com.zebra.mdna.enterprisehomescreen.HomeScreenActivity"
const PACKAGE = /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/
const TITLE_TOKENS = new Set(["bluetooth_address", "bluetooth_name", "imei_number", "mac_address", "network_host_name", "serial_number", "device_name"])
const LOGO_EXTENSIONS = [".bmp", ".gif", ".jpg", ".png", ".webp"]
const APP_ICON_EXTENSIONS = [".bmp", ".jpg", ".png"]
const COLOR_NAMES = new Set(["red", "blue", "green", "black", "white", "gray", "cyan", "magenta", "yellow", "lightgray", "darkgray"])
// Keys and values consumed by the app's RestrictionsManager reader (scanner-host res/xml/app_restrictions.xml).
export const MANAGED_SERVER_KEY = "qqq_server_url"
export const MANAGED_ORIENTATION_KEY = "qqq_orientation"
export const MANAGED_ORIENTATIONS = ["SYSTEM", "PORTRAIT", "LANDSCAPE", "REVERSE_PORTRAIT", "REVERSE_LANDSCAPE"]
const BLOCKED_WILDCARDS =/^com\.(a|an|and|andr|andro|androi|android)\*?$/

/*******************************************************************************
 ** Mirror the app's normalizeServerUrl for managed values: HTTPS origin only. Returns the normalized origin or null.
 *******************************************************************************/
export function managedServerUrl(value) {
   if (typeof value !== "string" || value.length > 2048) return null
   try {
      const url = new URL(value.trim())
      if (url.protocol !== "https:" || !url.hostname || url.username || url.password || url.search || url.hash || url.pathname !== "/") return null
      return `https://${url.host}/`
   } catch { return null }
}

/*******************************************************************************
 ** Escape the five characters EHS's XML parser requires to be escaped.
 *******************************************************************************/
export function xml(value) {
   return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&apos;")
}

function devicePath(value, extensions, label, errors) {
   if (value == null) return null
   if (typeof value !== "string" || !value.startsWith("/enterprise/usr/") || value.split("/").includes("..") || !extensions.some((e) => value.toLowerCase().endsWith(e))) {
      errors.push(`${label} must be an absolute /enterprise/usr/ path (persists across Enterprise Reset) ending in ${extensions.join(", ")}`)
      return null
   }
   return value
}

function color(value, label, errors) {
   if (value == null) return null
   if (typeof value !== "string" || !(/^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$/.test(value) || COLOR_NAMES.has(value))) errors.push(`${label} must be #RRGGBB, #AARRGGBB or a documented color name`)
   return value
}

function validateTitle(title, errors) {
   if (typeof title !== "string" || title.trim() === "") { errors.push("launcher.title is required"); return { text: "", dynamic: false } }
   const tokens = [...title.matchAll(/\[([^\]]*)\]/g)].map((m) => m[1])
   for (const token of tokens) if (!TITLE_TOKENS.has(token)) errors.push(`launcher.title token [${token}] is not a documented device ID`)
   if (new Set(tokens).size > 1 || tokens.length > 1) errors.push("launcher.title may contain at most one device ID token")
   if (title.replace(/\[[^\]]*\]/g, "").match(/[\[\]]/)) errors.push("launcher.title square brackets are only allowed around a documented device ID")
   return { text: title, dynamic: tokens.length === 1 }
}

function validateApp(app, index, list, errors, { icons }) {
   const where = `${list}[${index}]`
   if (!app || typeof app !== "object") { errors.push(`${where} must be an object`); return null }
   const pkg = app.package
   if (typeof pkg !== "string" || !PACKAGE.test(pkg)) {
      errors.push(`${where}.package must be an exact package name (wildcards are rejected: least privilege)`)
      if (typeof pkg === "string" && (pkg.includes("*") && BLOCKED_WILDCARDS.test(pkg))) errors.push(`${where}.package wildcard is blocked by EHS`)
      return null
   }
   if (pkg === EHS_PACKAGE) errors.push(`${where} must not list EHS itself`)
   if (typeof app.label !== "string" || app.label.trim() === "") errors.push(`${where}.label is required`)
   else if (app.label.length > 18) errors.push(`${where}.label is longer than 18 characters; EHS truncates it`)
   if (app.activity != null && (typeof app.activity !== "string" || !PACKAGE.test(app.activity))) errors.push(`${where}.activity must be a class name`)
   const icon = icons ? devicePath(app.icon, APP_ICON_EXTENSIONS, `${where}.icon`, errors) : null
   return { label: app.label, package: pkg, activity: app.activity ?? "", icon }
}

function applicationTag(app, indent) {
   const icon = app.icon ? ` icon="${xml(app.icon)}"` : ""
   return `${indent}<application label="${xml(app.label)}" package="${xml(app.package)}" activity="${xml(app.activity)}"${icon}/>`
}

/*******************************************************************************
 ** Validate a profile. Returns normalized values plus the inputs that are still missing before it can be installed.
 *******************************************************************************/
export function validate(profile) {
   const errors = []
   const missing = []
   const warnings = []
   const p = profile ?? {}
   if (typeof p.profileId !== "string" || !/^[a-z0-9][a-z0-9-]{1,62}$/.test(p.profileId)) errors.push("profileId must be lowercase letters, digits and hyphens")
   if (!Number.isInteger(p.revision) || p.revision < 1) errors.push("revision must be a positive integer")

   const device = p.device ?? {}
   for (const key of ["androidVersion", "mxVersion", "osBuild"]) if (!device[key]) missing.push(`device.${key}: measure on the target device before generating installable MX output`)
   if (!p.ehs?.installedVersion) missing.push("ehs.installedVersion: select an EHS build supported on the measured firmware (docs checked: 8.0)")

   const q = p.qqq ?? {}
   if (typeof q.package !== "string" || !PACKAGE.test(q.package)) errors.push("qqq.package must be the existing QQQ package name")
   if (typeof q.activity !== "string" || !PACKAGE.test(q.activity)) errors.push("qqq.activity must be a fully qualified activity class")
   if (typeof q.label !== "string" || q.label === "" || q.label.length > 18) errors.push("qqq.label is required (18 characters or fewer)")
   const endpoint = managedServerUrl(q.endpoint)
   if (endpoint === null) errors.push("qqq.endpoint must be an https origin with no credentials, path, query or fragment (the app rejects anything else)")
   else if (new URL(endpoint).hostname.endsWith(".invalid")) missing.push("qqq.endpoint: replace the placeholder with the company QQQ server URL")
   if (q.orientation != null && !MANAGED_ORIENTATIONS.includes(q.orientation)) errors.push(`qqq.orientation must be one of ${MANAGED_ORIENTATIONS.join(", ")} or omitted`)

   const l = p.launcher ?? {}
   const title = validateTitle(l.title, errors)
   const logo = devicePath(l.titleBarLogo, LOGO_EXTENSIONS, "launcher.titleBarLogo", errors)
   const wallpaper = devicePath(l.wallpaper, LOGO_EXTENSIONS, "launcher.wallpaper", errors)
   if (!logo) missing.push("launcher.titleBarLogo: company logo file and its /enterprise/usr path")
   if (!wallpaper) missing.push("launcher.wallpaper: company wallpaper file and its /enterprise/usr path")
   const textColor = color(l.iconLabelTextColor ?? "#FFFFFFFF", "launcher.iconLabelTextColor", errors)
   const backgroundColor = color(l.iconLabelBackgroundColor ?? "#00FFFFFF", "launcher.iconLabelBackgroundColor", errors)
   if (l.iconSize != null) errors.push("launcher.iconSize is unsupported: Zebra's snippet does not show where user_options sits in the config file")

   const apps = []
   if (!Array.isArray(p.permittedApps) || p.permittedApps.length === 0) errors.push("permittedApps must list at least the QQQ application")
   else p.permittedApps.forEach((a, i) => { const v = validateApp(a, i, "permittedApps", errors, { icons: true }); if (v) apps.push(v) })
   if (!apps.some((a) => a.package === q.package)) errors.push("permittedApps must include the qqq.package application")
   if (new Set(apps.map((a) => a.package)).size !== apps.length) errors.push("permittedApps contains a duplicate package")
   if (apps.length === 1) missing.push("permittedApps: administrator list of required Android companion apps (camera/photo picker, printing); only QQQ is listed")

   const tools = []
   ;(p.tools ?? []).forEach((a, i) => { const v = validateApp(a, i, "tools", errors, { icons: false }); if (v) tools.push(v) })

   const r = p.restrictions ?? {}
   for (const [key, allowed] of [["systemSettingsRestricted", [0, 1, 3]], ["recentAppsAccessDisabled", [0, 1]], ["recentAppsButtonDisabled", [0, 1, 2, 3]]]) {
      if (!allowed.includes(r[key])) errors.push(`restrictions.${key} must be one of ${allowed.join(", ")} (explicit decision required)`)
   }
   if (r.systemSettingsRestricted === 3) warnings.push("systemSettingsRestricted 3 requires MX 11.7+ and OSx 11.5.21+ on the device")
   if (r.systemSettingsRestricted === 1) warnings.push("systemSettingsRestricted 1 (EHS default) hides most Settings panels; confirm Bluetooth printer pairing and Wi-Fi workflows on a spare device")

   const deploy = []
   ;(p.deploy ?? []).forEach((d, i) => {
      const where = `deploy[${i}]`
      const known = [EHS_PACKAGE, q.package]
      if (!d || !known.includes(d.package)) errors.push(`${where}.package must be EHS or the QQQ package`)
      else if (!["Install", "Upgrade"].includes(d.action)) errors.push(`${where}.action must be Install or Upgrade`)
      else if (typeof d.apk !== "string" || !d.apk.startsWith("/") || d.apk.split("/").includes("..") || !d.apk.endsWith(".apk")) errors.push(`${where}.apk must be an absolute on-device .apk path already staged`)
      else deploy.push({ package: d.package, action: d.action, apk: d.apk })
   })
   if (!deploy.some((d) => d.package === EHS_PACKAGE)) missing.push("deploy: EHS APK on-device path (artifact delivery method and checksum are an administrator decision)")

   return { errors, missing, warnings, normalized: { title, logo, wallpaper, textColor, backgroundColor, apps, tools, deploy, qqq: q, launcher: l, restrictions: r } }
}

/*******************************************************************************
 ** Render enterprisehomescreen.xml. Tags not parameterized keep the values of Zebra's documented default file.
 *******************************************************************************/
export function renderEhs(profile, n) {
   const qqqApp = n.apps.find((a) => a.package === n.qqq.package)
   const lines = ["<?xml version=\"1.0\" encoding=\"utf-8\"?>", `<!-- profile ${xml(profile.profileId)} revision ${profile.revision}; generated for review, see docs/COMPANY-PROFILE.md. EHS docs 8.0. -->`, "<config>"]
   lines.push("    <kiosk>", applicationTag(qqqApp, "        "), "    </kiosk>")
   lines.push("    <applications>", ...n.apps.map((a) => applicationTag(a, "        ")), "    </applications>")
   if (n.tools.length > 0) lines.push("    <tools>", ...n.tools.map((a) => applicationTag(a, "        ")), "    </tools>")
   // EHS stores a hash here when an administrator sets the password in Admin Mode; it must never be authored.
   lines.push("    <passwords>", "        <admin></admin>", "    </passwords>")
   lines.push("    <preferences>")
   lines.push(n.title.dynamic ? `        <title><value>${xml(n.title.text)}</value></title>` : `        <title>${xml(n.title.text)}</title>`)
   if (n.logo) {
      lines.push("        <title_bar_icon>", "            <title_bar_icon_disabled>0</title_bar_icon_disabled>", `            <title_bar_icon_file>${xml(n.logo)}</title_bar_icon_file>`)
      lines.push(`            <reuse_custom_icon>${n.launcher.reuseLogoOnEhsScreens ? 1 : 0}</reuse_custom_icon>`, "        </title_bar_icon>")
   }
   lines.push(`        <icon_label_background_color>${xml(n.backgroundColor)}</icon_label_background_color>`, `        <icon_label_text_color>${xml(n.textColor)}</icon_label_text_color>`)
   lines.push("        <orientation></orientation>", "        <auto_launch_enable>0</auto_launch_enable>", "        <service_auto_launch_enable>0</service_auto_launch_enable>")
   lines.push(`        <wallpaper>${xml(n.wallpaper ?? "")}</wallpaper>`)
   if (n.wallpaper) lines.push(`        <wallpaper_stretching_enabled>${n.launcher.wallpaperStretch ? 1 : 0}</wallpaper_stretching_enabled>`)
   lines.push("        <kiosk_mode_enabled>0</kiosk_mode_enabled>", "        <keyguard_camera_disabled>1</keyguard_camera_disabled>", "        <keyguard_search_disabled>1</keyguard_search_disabled>")
   lines.push("        <usb_debugging_disabled>1</usb_debugging_disabled>", `        <system_settings_restricted>${n.restrictions.systemSettingsRestricted}</system_settings_restricted>`)
   lines.push(`        <recent_apps_button_disabled>${n.restrictions.recentAppsButtonDisabled}</recent_apps_button_disabled>`, `        <recent_apps_access_disabled>${n.restrictions.recentAppsAccessDisabled}</recent_apps_access_disabled>`)
   lines.push("        <wireless_info>", `            <show_ip_address>${profile.launcher?.showIpAddress ? 1 : 0}</show_ip_address>`, "            <show_mac_address>0</show_mac_address>", "            <show_bssid>0</show_bssid>", "            <show_ssid>0</show_ssid>", "            <show_scan_result>0</show_scan_result>", "        </wireless_info>")
   lines.push("        <screen_blanking>", "            <blanking_enabled>0</blanking_enabled>", "            <speed_threshold>10</speed_threshold>", "            <blanking_mode>1</blanking_mode>", "        </screen_blanking>")
   lines.push("    </preferences>")
   lines.push("</config>", "")
   return lines.join("\n")
}

/*******************************************************************************
 ** Render the StageNow/MX AppMgr document: optional APK installs, then EHS as the default launcher.
 *******************************************************************************/
export function renderMx(profile, n) {
   const lines = ["<?xml version=\"1.0\" encoding=\"utf-8\"?>", `<!-- profile ${xml(profile.profileId)} revision ${profile.revision}; AppMgr only. Device ${xml(profile.device.model)} Android ${xml(profile.device.androidVersion)} MX ${xml(profile.device.mxVersion)}. -->`, "<wap-provisioningdoc>"]
   for (const d of n.deploy) lines.push("    <characteristic version=\"4.2\" type=\"AppMgr\">", `        <parm name="Action" value="${d.action}" />`, `        <parm name="APK" value="${xml(d.apk)}" />`, "    </characteristic>")
   lines.push("    <characteristic version=\"4.2\" type=\"AppMgr\">", "        <parm name=\"Action\" value=\"SetDefaultLauncher\" />", `        <parm name="Package" value="${EHS_PACKAGE}" />`, "    </characteristic>")
   lines.push("</wap-provisioningdoc>", "")
   return lines.join("\n")
}

/*******************************************************************************
 ** Validate then write outputs. MX output is withheld until the device is measured.
 *******************************************************************************/
export function generate(profile, outDir) {
   const { errors, missing, warnings, normalized } = validate(profile)
   const report = { profileId: profile?.profileId, revision: profile?.revision, ready: false, errors, missing, warnings, files: [] }
   if (errors.length > 0) return report
   mkdirSync(outDir, { recursive: true })
   const emit = (name, text) => { writeFileSync(join(outDir, name), text, "utf8"); report.files.push(name) }
   emit("enterprisehomescreen.xml", renderEhs(profile, normalized))
   // Android managed configuration for the EMM; withheld while the endpoint is still a placeholder.
   if (!missing.some((m) => m.startsWith("qqq.endpoint"))) {
      const managed = { [MANAGED_SERVER_KEY]: managedServerUrl(profile.qqq.endpoint) }
      if (profile.qqq.orientation != null) managed[MANAGED_ORIENTATION_KEY] = profile.qqq.orientation
      emit("managed-configuration.json", JSON.stringify(managed, null, 2) + "\n")
   } else report.warnings.push("managed-configuration.json withheld: qqq.endpoint is still a placeholder")
   const mxReady =!missing.some((m) => m.startsWith("device.") || m.startsWith("deploy:"))
   if (mxReady) emit("stagenow-mx-appmgr.xml", renderMx(profile, normalized))
   else report.warnings.push("stagenow-mx-appmgr.xml withheld: device measurements and EHS APK path are missing")
   report.ready = missing.length === 0
   writeFileSync(join(outDir, "profile-report.json"), JSON.stringify(report, null, 2) + "\n", "utf8")
   report.files.push("profile-report.json")
   return report
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
   const [input, outDir, ...flags] = process.argv.slice(2)
   if (!input || !outDir) { console.error("usage: generate-company-profile.mjs <profile.json> <output-dir> [--require-ready]"); process.exit(64) }
   const report = generate(JSON.parse(readFileSync(input, "utf8")), outDir)
   console.log(JSON.stringify(report, null, 2))
   if (report.errors.length > 0) process.exit(1)
   if (flags.includes("--require-ready") && !report.ready) process.exit(2)
}
