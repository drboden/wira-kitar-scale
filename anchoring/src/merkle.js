// Wira Kitar record hashing and Merkle tree.
// The same rules are implemented in verify.html, so anyone can check a record in a browser.
//
// Rules (version 1):
//   canonical(record) = JSON.stringify of the LEAF_FIELDS below, in this exact order
//                       weight_kg is a string with 3 decimals, scale_stable is true/false
//   leaf  = SHA-256( 0x00 || utf8(canonical) )
//   node  = SHA-256( 0x01 || left || right )
//   odd node at a level is carried up unchanged
//   root  = hex of the top node

const crypto = require('crypto');

const SCHEME = 'wira-kitar-merkle-v1';

const LEAF_FIELDS = [
  'record_id',      // visit_bag id
  'visit_id',
  'centre_id',
  'collector_ref',  // anonymised collector id, never name/phone/plate
  'material',
  'weight_kg',
  'weight_source',  // 'scale' or 'manual'
  'scale_raw',      // exact indicator line, '' for manual
  'scale_stable',
  'captured_at',    // ISO 8601 UTC
  'salt',           // random per record, stored in the dashboard, stops guessing
];

// Only on correction records. Added after the fields above, and only when present,
// so normal bags (and batches anchored before corrections existed) hash exactly as before.
const OPTIONAL_FIELDS = [
  'adjusts_record_id', // record_id of the original bag being corrected
  'reason_code',       // e.g. wrong_weight, wrong_material, duplicate
];

function normalise(record) {
  const out = {};
  for (const f of LEAF_FIELDS) {
    let v = record[f];
    if (v === undefined || v === null) {
      if (f === 'scale_raw') v = '';
      else throw new Error(`Record ${record.record_id || '?'} is missing "${f}"`);
    }
    if (f === 'weight_kg') {
      const n = Number(v);
      if (!Number.isFinite(n)) throw new Error(`Record ${record.record_id}: bad weight_kg "${v}"`);
      v = n.toFixed(3);
    } else if (f === 'scale_stable') {
      v = v === true || v === 'true' || v === 1 || v === '1';
    } else {
      v = String(v);
    }
    out[f] = v;
  }
  for (const f of OPTIONAL_FIELDS) {
    const v = record[f];
    if (v !== undefined && v !== null && v !== '') out[f] = String(v);
  }
  if ((out.adjusts_record_id === undefined) !== (out.reason_code === undefined)) {
    throw new Error(`Record ${out.record_id}: a correction needs both adjusts_record_id and reason_code`);
  }
  if (out.salt.length < 16) throw new Error(`Record ${out.record_id}: salt must be at least 16 characters`);
  return out;
}

function canonical(record) {
  return JSON.stringify(normalise(record));
}

function sha256(...buffers) {
  const h = crypto.createHash('sha256');
  for (const b of buffers) h.update(b);
  return h.digest();
}

function leafHash(record) {
  return sha256(Buffer.from([0x00]), Buffer.from(canonical(record), 'utf8'));
}

function nodeHash(left, right) {
  return sha256(Buffer.from([0x01]), left, right);
}

// Returns { root: Buffer, levels: Buffer[][] }
function buildTree(leaves) {
  if (!leaves.length) throw new Error('No records to anchor');
  const levels = [leaves];
  while (levels[levels.length - 1].length > 1) {
    const cur = levels[levels.length - 1];
    const next = [];
    for (let i = 0; i < cur.length; i += 2) {
      next.push(i + 1 < cur.length ? nodeHash(cur[i], cur[i + 1]) : cur[i]);
    }
    levels.push(next);
  }
  return { root: levels[levels.length - 1][0], levels };
}

// Proof for leaf at index: list of { side: 'L'|'R', hash } (side = where the sibling sits)
function proofFor(levels, index) {
  const proof = [];
  let i = index;
  for (let l = 0; l < levels.length - 1; l++) {
    const level = levels[l];
    const isRight = i % 2 === 1;
    const sib = isRight ? i - 1 : i + 1;
    if (sib < level.length) {
      proof.push({ side: isRight ? 'L' : 'R', hash: level[sib].toString('hex') });
    }
    i = Math.floor(i / 2);
  }
  return proof;
}

function rootFromProof(leaf, proof) {
  let h = leaf;
  for (const step of proof) {
    const sib = Buffer.from(step.hash, 'hex');
    h = step.side === 'L' ? nodeHash(sib, h) : nodeHash(h, sib);
  }
  return h;
}

function randomSalt() {
  return crypto.randomBytes(16).toString('hex');
}

module.exports = { SCHEME, LEAF_FIELDS, OPTIONAL_FIELDS, normalise, canonical, leafHash, buildTree, proofFor, rootFromProof, randomSalt };
