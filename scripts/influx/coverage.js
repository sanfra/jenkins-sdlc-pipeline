'use strict';

/**
 * Writes a 'coverage' measurement to InfluxDB.
 *
 * Usage:
 *   node coverage.js --app <name> --env <dev|prod> --build <nr> \
 *       --line <pct> [--branch <pct>] [--function <pct>] [--statement <pct>]
 *
 * Env vars required: INFLUXDB_URL, INFLUXDB_ORG, INFLUXDB_BUCKET, INFLUXDB_TOKEN
 */

const { write, line } = require('./write');

const args = parseArgs(process.argv.slice(2));

const tags = {
  app: required(args, 'app'),
  env: required(args, 'env'),
};

const fields = {
  build_nr:      int(args.build),
  line_pct:      float(args.line),
  branch_pct:    float(args.branch    || '0'),
  function_pct:  float(args.function  || '0'),
  statement_pct: float(args.statement || '0'),
};

write(line('coverage', tags, fields))
  .then(() => console.log('[influx] coverage OK'))
  .catch((e) => { console.error('[influx] coverage FAILED:', e.message); process.exit(1); });

// --- helpers ----------------------------------------------------------------

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

function float(v) { return parseFloat(v  || '0') || 0; }
function int(v)   { return parseInt(v    || '0') || 0; }
