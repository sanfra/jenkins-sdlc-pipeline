'use strict';

/**
 * Writes pipeline lifecycle measurements to InfluxDB.
 *
 * Sub-commands:
 *
 *   run     — writes 'pipeline_run' (one point per build, in post { always })
 *     node pipeline.js run --app <n> --env <e> --build <nr> \
 *         --result <SUCCESS|FAILURE|ABORTED|UNSTABLE> \
 *         --duration-ms <ms> --version <v>
 *
 *   deploy  — writes 'deploy' (one point per PROD deploy)
 *     node pipeline.js deploy --app <n> --env <prod> --build <nr> \
 *         --artifact-version <dev-build-nr> \
 *         --status <SUCCESS|FAILURE> --duration-ms <ms>
 *
 * InfluxDB env vars required: INFLUXDB_URL, INFLUXDB_ORG, INFLUXDB_BUCKET, INFLUXDB_TOKEN
 */

const { write, line } = require('./write');

const [,, cmd, ...rest] = process.argv;
const args = parseArgs(rest);

let measurement, tags, fields;

if (cmd === 'run') {
  measurement = 'pipeline_run';
  tags = {
    app:    required(args, 'app'),
    env:    required(args, 'env'),
    result: args.result || 'UNKNOWN',
  };
  fields = {
    build_nr:    int(args.build),
    duration_ms: int(args['duration-ms']),
    version:     `"${args.version || '0'}"`,
  };

} else if (cmd === 'deploy') {
  measurement = 'deploy';
  tags = {
    app:    required(args, 'app'),
    env:    required(args, 'env'),
    status: args.status || 'UNKNOWN',
  };
  fields = {
    build_nr:         int(args.build),
    artifact_version: int(args['artifact-version']),
    duration_ms:      int(args['duration-ms']),
  };

} else {
  console.error(`[influx] pipeline.js: unknown command '${cmd || '(none)'}'. Use: run | deploy`);
  process.exit(1);
}

write(line(measurement, tags, fields))
  .then(() => console.log(`[influx] ${measurement} OK`))
  .catch((e) => { console.error(`[influx] ${measurement} FAILED:`, e.message); process.exit(1); });

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

function int(v) { return parseInt(v || '0') || 0; }
