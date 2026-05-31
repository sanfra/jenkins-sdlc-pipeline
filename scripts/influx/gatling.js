'use strict';

/**
 * Parses a Gatling stats.json and writes a 'gatling_run' measurement to InfluxDB.
 *
 * Gatling 3.x stats.json structure:
 *   root .stats  → global (all-requests) aggregated stats
 *   .stats.numberOfRequests.{total,ok,ko}
 *   .stats.percentiles1.total  → p50
 *   .stats.percentiles3.total  → p95
 *   .stats.percentiles4.total  → p99
 *   .stats.meanResponseTime.total
 *   .stats.meanNumberOfRequestsPerSecond.total
 *
 * Usage:
 *   node gatling.js --app <name> --env <dev|prod> --build <nr> \
 *       --stats-dir target/gatling-reports \
 *       [--simulation GenericSimulation]
 *
 * InfluxDB env vars required: INFLUXDB_URL, INFLUXDB_ORG, INFLUXDB_BUCKET, INFLUXDB_TOKEN
 */

const fs   = require('fs');
const path = require('path');
const { write, line } = require('./write');

const args       = parseArgs(process.argv.slice(2));
const statsDir   = args['stats-dir'] || 'target/gatling-reports';
const app        = required(args, 'app');
const env        = required(args, 'env');
const buildNr    = int(args.build);
const simulation = args.simulation || 'GenericSimulation';

const statsFile = findStatsJson(statsDir);
if (!statsFile) {
  console.error(`[influx] gatling: stats.json not found in ${statsDir}`);
  process.exit(1);
}

const data = JSON.parse(fs.readFileSync(statsFile, 'utf8'));
const s    = data.stats;

if (!s || !s.numberOfRequests) {
  console.error('[influx] gatling: root .stats object missing or malformed in', statsFile);
  process.exit(1);
}

const ok    = num(s.numberOfRequests.ok);
const ko    = num(s.numberOfRequests.ko);
const total = num(s.numberOfRequests.total);

const fields = {
  build_nr:       buildNr,
  requests_total: total,
  requests_ok:    ok,
  requests_ko:    ko,
  error_rate_pct: total > 0 ? parseFloat(((ko / total) * 100).toFixed(2)) : 0,
  mean_ms:        num(s.meanResponseTime && s.meanResponseTime.total),
  p50_ms:         num(s.percentiles1     && s.percentiles1.total),
  p95_ms:         num(s.percentiles3     && s.percentiles3.total),
  p99_ms:         num(s.percentiles4     && s.percentiles4.total),
  throughput_rps: num(s.meanNumberOfRequestsPerSecond && s.meanNumberOfRequestsPerSecond.total),
};

write(line('gatling_run', { app, env, simulation }, fields))
  .then(() => console.log('[influx] gatling_run OK'))
  .catch((e) => { console.error('[influx] gatling_run FAILED:', e.message); process.exit(1); });

// --- helpers ----------------------------------------------------------------

function findStatsJson(dir) {
  if (!fs.existsSync(dir)) return null;
  for (const entry of fs.readdirSync(dir)) {
    // Gatling 3.x: stats.json lives inside js/ subdirectory
    const inJs   = path.join(dir, entry, 'js', 'stats.json');
    if (fs.existsSync(inJs)) return inJs;
    const inRoot = path.join(dir, entry, 'stats.json');
    if (fs.existsSync(inRoot)) return inRoot;
  }
  return null;
}

function parseArgs(argv) {
  const r = {};
  for (let i = 0; i < argv.length - 1; i += 2) {
    r[argv[i].replace(/^--/, '')] = argv[i + 1];
  }
  return r;
}

function required(args, key) {
  if (!args[key]) throw new Error(`Missing required argument --${key}`);
  return args[key];
}

function num(v) { return parseFloat(v || '0') || 0; }
function int(v) { return parseInt(v  || '0') || 0; }
