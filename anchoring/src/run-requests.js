#!/usr/bin/env node
// Used by the GitHub "Hedera anchoring" workflow.
// Processes every file in requests/ that has no result yet in <results dir>/requests/.
//   { "action": "create-topic" }
//   { "action": "anchor", "input": "samples/records-2026-11-16.json", "date": "2026-11-16", "method": "hedera" }
// Results (and anchor batch files) are written to the results dir, which the workflow
// commits to the "anchors" branch.
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const resultsDir = process.argv[2] || 'out';
const cfg = JSON.parse(fs.readFileSync('hedera-config.json', 'utf8'));
process.env.HEDERA_NETWORK = process.env.HEDERA_NETWORK || cfg.network;
process.env.HEDERA_ACCOUNT_ID = process.env.HEDERA_ACCOUNT_ID || cfg.account_id;
process.env.HEDERA_KEY_TYPE = process.env.HEDERA_KEY_TYPE || cfg.key_type || 'ECDSA';
if (cfg.topic_id && !process.env.HEDERA_TOPIC_ID) process.env.HEDERA_TOPIC_ID = cfg.topic_id;

fs.mkdirSync(path.join(resultsDir, 'requests'), { recursive: true });
fs.mkdirSync(path.join(resultsDir, 'anchors'), { recursive: true });

const files = fs.existsSync('requests') ? fs.readdirSync('requests').filter(f => f.endsWith('.json')).sort() : [];
let done = 0;
for (const f of files) {
  const resultFile = path.join(resultsDir, 'requests', f.replace(/\.json$/, '.result.json'));
  if (fs.existsSync(resultFile) && JSON.parse(fs.readFileSync(resultFile, 'utf8')).ok) continue; // failed ones are retried
  const req = JSON.parse(fs.readFileSync(path.join('requests', f), 'utf8'));
  const result = { request: f, ...req, ran_at: new Date().toISOString(), network: process.env.HEDERA_NETWORK, account_id: process.env.HEDERA_ACCOUNT_ID };
  try {
    let cmd;
    if (req.action === 'create-topic') cmd = ['src/hedera-create-topic.js'];
    else if (req.action === 'anchor') cmd = ['src/anchor.js', '--input', req.input, '--date', req.date, '--method', req.method || 'hedera', '--out', path.join(resultsDir, 'anchors')];
    else throw new Error('Unknown action ' + req.action);
    result.output = execFileSync('node', cmd, { encoding: 'utf8', env: process.env, timeout: 120000 });
    result.ok = true;
    const m = result.output.match(/Created topic (\S+)/);
    if (m) result.topic_id = m[1];
  } catch (e) {
    result.ok = false;
    result.error = String((e.stderr || '') + (e.stdout || '') || e.message).slice(0, 4000);
  }
  fs.writeFileSync(resultFile, JSON.stringify(result, null, 2));
  console.log(`${f}: ${result.ok ? 'OK' : 'FAILED'}`);
  console.log(result.output || result.error);
  done++;
}
console.log(`Processed ${done} request(s)`);
