'use strict';

/**
 * Security scan entry point — dispatches to tech-specific scanner.
 *
 * Usage:
 *   node scan.js --tech <spa|node|java|dotnet> --level <full|audit|skipped> \
 *       --app <name> --env <dev|prod> --build <nr>
 *
 * Exit codes: 0 = OK or level!=full
 *             1 = critical/high found with level=full
 */

const { execSync } = require('child_process');
const { write, line } = require('../influx/write');
const { extractFindings } = require('./findings');

const args  = parseArgs(process.argv.slice(2));
const tech  = args.tech  || 'node';
const level = required(args, 'level');
const app   = required(args, 'app');
const build = args.build || '0';
const env   = args.env   || 'dev';

if (level === 'skipped') {
  console.log('[security] SKIPPED');
  process.exit(0);
}

console.log(`[security] START  tech=${tech}  level=${level}  app=${app}  build=#${build}`);

let vuln;
if      (tech === 'spa' || tech === 'node') vuln = scanNpm();
else if (tech === 'java')    vuln = stub('Java — OWASP Dependency Check (not yet configured)');
else if (tech === 'dotnet')  vuln = stub('.NET — dotnet list package --vulnerable (not yet configured)');
else { console.warn(`[security] unknown tech='${tech}' — skipping`); process.exit(0); }

const { critical, high, moderate, low, auditJson = {} } = vuln;
console.log(`[security] critical=${critical}  high=${high}  moderate=${moderate}  low=${low}`);

const gateFailure = level === 'full' && (critical > 0 || high > 0);
const findingLines = extractFindings(auditJson, { app, env, build });

write([
  line('security_scan', { app, env, tech }, { build_nr: parseInt(build) || 0, critical, high, moderate, low }),
  ...findingLines,
])
  .then(() => console.log(`[influx] security_scan OK (${findingLines.length} findings)`))
  .catch(e  => console.warn('[influx] security_scan skipped:', e.message))
  .finally(() => {
    if (gateFailure) {
      console.error(`[security] FAILED — ${critical} critical, ${high} high vulnerabilities found`);
      process.exit(1);
    }
    console.log('[security] PASSED');
  });

// --- scanners ---------------------------------------------------------------

function scanNpm() {
  let raw = '{}';
  try {
    raw = execSync('npm audit --json', { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] });
  } catch (e) {
    raw = e.stdout || '{}'; // npm audit exits 1 when vulns found — stdout is still valid JSON
  }
  const auditJson = parseJson(raw);
  const v = (auditJson.metadata || {}).vulnerabilities || {};

  try {
    execSync('npm outdated', { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] });
    console.log('[security] All packages up to date.');
  } catch (e) {
    if (e.stdout) console.log('[security] Outdated packages:\n' + e.stdout);
  }

  return {
    critical: v.critical||0, high: v.high||0, moderate: v.moderate||0, low: v.low||0,
    auditJson,
  };
}

function stub(name) {
  console.log(`[security] ${name}`);
  return { critical: 0, high: 0, moderate: 0, low: 0, auditJson: {} };
}

// --- helpers ----------------------------------------------------------------

function parseJson(s) { try { return JSON.parse(s); } catch (_) { return {}; } }

function required(a, k) {
  if (!a[k]) throw new Error(`Missing required argument --${k}`);
  return a[k];
}

function parseArgs(argv) {
  const r = {};
  for (let i = 0; i < argv.length - 1; i += 2) r[argv[i].replace(/^--/, '')] = argv[i + 1];
  return r;
}
