'use strict';

/**
 * Extracts individual CVE findings from npm audit JSON and returns
 * InfluxDB line-protocol strings for the 'security_finding' measurement.
 *
 * Tags:  app, env, severity, package_name
 * Fields: build_nr, title, url, fix_available
 */

const { line } = require('../influx/write');

function extractFindings(auditJson, { app, env, build }) {
  const vulns = auditJson.vulnerabilities || {};
  const lines = [];

  for (const [name, v] of Object.entries(vulns)) {
    const direct = Array.isArray(v.via) && v.via.find(x => typeof x === 'object');
    if (!direct) continue;

    lines.push(line(
      'security_finding',
      { app, env, severity: v.severity, package_name: name },
      {
        build_nr:      parseInt(build) || 0,
        title:         `"${escStr(direct.title || '')}"`,
        url:           `"${escStr(direct.url   || '')}"`,
        fix_available: v.fixAvailable ? 1 : 0,
      }
    ));
  }

  return lines;
}

function escStr(s) {
  return String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

module.exports = { extractFindings };
