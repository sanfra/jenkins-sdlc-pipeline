'use strict';

const http  = require('http');
const https = require('https');

/**
 * Writes one or more InfluxDB line-protocol strings to InfluxDB 2.x.
 *
 * Required env vars:
 *   INFLUXDB_URL    e.g. http://influxdb-jenkins:8086
 *   INFLUXDB_ORG    e.g. sanfra
 *   INFLUXDB_BUCKET e.g. jenkins
 *   INFLUXDB_TOKEN  InfluxDB 2.x API token
 *
 * @param {string|string[]} lines  One or more line-protocol strings
 * @returns {Promise<void>}
 */
function write(lines) {
  const { INFLUXDB_URL, INFLUXDB_ORG, INFLUXDB_BUCKET, INFLUXDB_TOKEN } = process.env;

  if (!INFLUXDB_URL || !INFLUXDB_ORG || !INFLUXDB_BUCKET || !INFLUXDB_TOKEN) {
    return Promise.reject(
      new Error('Missing required env var: INFLUXDB_URL / INFLUXDB_ORG / INFLUXDB_BUCKET / INFLUXDB_TOKEN')
    );
  }

  const url = new URL(`${INFLUXDB_URL}/api/v2/write`);
  url.searchParams.set('org',       INFLUXDB_ORG);
  url.searchParams.set('bucket',    INFLUXDB_BUCKET);
  url.searchParams.set('precision', 's');

  const payload = Array.isArray(lines) ? lines.join('\n') + '\n' : lines + '\n';
  const body    = Buffer.from(payload, 'utf8');
  const client  = url.protocol === 'https:' ? https : http;

  return new Promise((resolve, reject) => {
    const req = client.request(
      {
        hostname: url.hostname,
        port:     url.port,
        path:     url.pathname + url.search,
        method:   'POST',
        headers:  {
          Authorization:   `Token ${INFLUXDB_TOKEN}`,
          'Content-Type':  'text/plain; charset=utf-8',
          'Content-Length': body.length,
        },
      },
      (res) => {
        let raw = '';
        res.on('data', (chunk) => (raw += chunk));
        res.on('end', () => {
          if (res.statusCode >= 200 && res.statusCode < 300) resolve();
          else reject(new Error(`HTTP ${res.statusCode}: ${raw.trim()}`));
        });
      }
    );
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

/**
 * Builds an InfluxDB line-protocol string.
 *
 * @param {string} measurement
 * @param {Object} tags    Plain object {key: stringValue} — auto-escaped
 * @param {Object} fields  Plain object {key: value}
 *                           numbers → written as-is (e.g. 3.14, 42i not needed for floats)
 *                           strings → caller must wrap in double quotes: '"my string"'
 * @param {number} [ts]    Unix timestamp in seconds (defaults to now)
 * @returns {string}
 */
function line(measurement, tags, fields, ts) {
  const tagStr = Object.entries(tags)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${esc(k)}=${esc(String(v))}`)
    .join(',');

  const fieldStr = Object.entries(fields)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${esc(k)}=${v}`)
    .join(',');

  const timestamp = ts || Math.floor(Date.now() / 1000);
  return `${measurement},${tagStr} ${fieldStr} ${timestamp}`;
}

function esc(s) {
  return String(s).replace(/[ ,=]/g, '\\$&');
}

module.exports = { write, line };
