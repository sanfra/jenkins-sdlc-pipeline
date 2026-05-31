'use strict';

/**
 * Queries SonarQube metrics API and writes a 'code_quality' measurement to InfluxDB.
 *
 * Usage:
 *   node sonarqube.js --app <name> --env <dev|prod> --build <nr> \
 *       [--sonar-url http://sonarqube:9000] [--project-key sanfra-app]
 *
 * sonar-url  defaults to env var SONAR_HOST_URL
 * sonar token is read from env var SONAR_TOKEN (already in Jenkins environment)
 * InfluxDB env vars required: INFLUXDB_URL, INFLUXDB_ORG, INFLUXDB_BUCKET, INFLUXDB_TOKEN
 */

const http  = require('http');
const https = require('https');
const { write, line } = require('./write');

const METRICS = [
  'bugs', 'vulnerabilities', 'code_smells',
  'duplicated_lines_density', 'security_hotspots',
].join(',');

const args       = parseArgs(process.argv.slice(2));
const sonarUrl   = args['sonar-url']   || process.env.SONAR_HOST_URL || 'http://sonarqube:9000';
const sonarToken = process.env.SONAR_TOKEN || '';
const projectKey = args['project-key'] || args.app || 'sanfra-app';
const app        = args.app  || projectKey;
const env        = required(args, 'env');
const buildNr    = int(args.build);

fetchMetrics(sonarUrl, sonarToken, projectKey)
  .then((m) => {
    const fields = {
      build_nr:         buildNr,
      bugs:             num(m.bugs),
      vulnerabilities:  num(m.vulnerabilities),
      code_smells:      num(m.code_smells),
      duplications_pct: num(m.duplicated_lines_density),
      hotspots:         num(m.security_hotspots),
    };
    return write(line('code_quality', { app, env }, fields));
  })
  .then(() => console.log('[influx] code_quality OK'))
  .catch((e) => { console.error('[influx] code_quality FAILED:', e.message); process.exit(1); });

// --- helpers ----------------------------------------------------------------

function fetchMetrics(baseUrl, token, key) {
  const endpoint = `${baseUrl}/api/measures/component?component=${key}&metricKeys=${METRICS}`;
  const auth     = Buffer.from(`${token}:`).toString('base64');
  const client   = endpoint.startsWith('https') ? https : http;

  return new Promise((resolve, reject) => {
    client.get(endpoint, { headers: { Authorization: `Basic ${auth}` } }, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => {
        try {
          const payload = JSON.parse(data);
          if (!payload.component) throw new Error(`Unexpected response: ${data.slice(0, 200)}`);
          const result = {};
          (payload.component.measures || []).forEach((m) => (result[m.metric] = m.value));
          resolve(result);
        } catch (e) { reject(e); }
      });
    }).on('error', reject);
  });
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

function num(v) { return parseFloat(v  || '0') || 0; }
function int(v) { return parseInt(v    || '0') || 0; }
