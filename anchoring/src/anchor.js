#!/usr/bin/env node
// Anchor one day's confirmed Wira Kitar records.
//
//   node src/anchor.js --input records.json --date 2026-11-24 --method ots
//   node src/anchor.js --input records.csv  --date 2026-11-24 --method hedera
//   node src/anchor.js --input records.json --date 2026-11-24 --method none   (dry run)
//
// Writes, in --out (default ./anchors):
//   <batch>.json         the batch: root, count, anchor receipt, a proof for every record
//   <batch>-proofs.csv   record_id, leaf_hash, batch_id, proof  -> import back into the dashboard
//   <batch>.ots          OpenTimestamps proof (method ots)

const fs = require('fs');
const path = require('path');
const M = require('./merkle');
const { readRecords } = require('./io');

function args() {
  const a = process.argv.slice(2);
  const o = { method: 'ots', out: 'anchors' };
  for (let i = 0; i < a.length; i++) {
    const k = a[i].replace(/^--/, '');
    o[k] = a[i + 1];
    i++;
  }
  if (!o.input) throw new Error('Missing --input <records.json|records.csv>');
  if (!o.date) o.date = new Date().toISOString().slice(0, 10);
  return o;
}

async function anchorOts(rootBuf, outBase) {
  const OTS = require('opentimestamps');
  const detached = OTS.DetachedTimestampFile.fromHash(new OTS.Ops.OpSHA256(), rootBuf);
  await OTS.stamp(detached);
  const file = outBase + '.ots';
  fs.writeFileSync(file, Buffer.from(detached.serializeToBytes()));
  return {
    method: 'opentimestamps',
    ots_file: path.basename(file),
    status: 'pending',
    note: 'Bitcoin confirmation takes a few hours. Run: node src/upgrade.js ' + path.basename(file),
  };
}

async function anchorHedera(message) {
  const { client, network } = require('./hedera').client();
  const { TopicMessageSubmitTransaction } = require('@hashgraph/sdk');
  const topicId = process.env.HEDERA_TOPIC_ID;
  if (!topicId) throw new Error('Set HEDERA_TOPIC_ID (create one with: node src/hedera-create-topic.js)');
  const tx = await new TopicMessageSubmitTransaction({ topicId, message: JSON.stringify(message) }).execute(client);
  const receipt = await tx.getReceipt(client);
  const record = await tx.getRecord(client);
  client.close();
  const ts = record.consensusTimestamp.toString();
  return {
    method: 'hedera',
    network,
    topic_id: topicId,
    sequence_number: receipt.topicSequenceNumber.toString(),
    transaction_id: tx.transactionId.toString(),
    consensus_timestamp: ts,
    status: 'anchored',
    hedera_status: receipt.status.toString(),
    explorer: `https://hashscan.io/${network}/topic/${topicId}`,
  };
}

async function main() {
  const o = args();
  const records = readRecords(o.input);
  const leaves = records.map(M.leafHash);
  const { root, levels } = M.buildTree(leaves);
  const rootHex = root.toString('hex');
  const centre = o.centre || [...new Set(records.map(r => r.centre_id).filter(Boolean))].join('+') || 'WK';
  const batchId = `${centre}-${o.date}`.replace(/[^A-Za-z0-9+_-]/g, '_');

  const message = { v: 1, scheme: M.SCHEME, batch_id: batchId, date: o.date, count: records.length, root: rootHex };

  fs.mkdirSync(o.out, { recursive: true });
  const outBase = path.join(o.out, batchId);

  let anchor;
  if (o.method === 'ots') anchor = await anchorOts(root, outBase);
  else if (o.method === 'hedera') anchor = await anchorHedera(message);
  else if (o.method === 'none') anchor = { method: 'none', status: 'dry-run' };
  else throw new Error('Unknown --method ' + o.method);

  const batch = {
    ...message,
    created_at: new Date().toISOString(),
    leaf_fields: M.LEAF_FIELDS,
    anchor,
    records: records.map((r, i) => ({
      record_id: String(r.record_id),
      leaf_hash: leaves[i].toString('hex'),
      proof: M.proofFor(levels, i),
    })),
  };
  fs.writeFileSync(outBase + '.json', JSON.stringify(batch, null, 2));

  const csv = ['record_id,leaf_hash,batch_id,proof'];
  for (const r of batch.records) {
    csv.push([r.record_id, r.leaf_hash, batchId, '"' + JSON.stringify(r.proof).replace(/"/g, '""') + '"'].join(','));
  }
  fs.writeFileSync(outBase + '-proofs.csv', csv.join('\n') + '\n');

  console.log(`Anchored ${records.length} records`);
  console.log(`Batch:  ${batchId}`);
  console.log(`Root:   ${rootHex}`);
  console.log(`Anchor: ${JSON.stringify(anchor, null, 2)}`);
  console.log(`Files:  ${outBase}.json, ${outBase}-proofs.csv`);
}

main().catch(e => { console.error('Error:', e.message); process.exit(1); });
