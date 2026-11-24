// Read records from a JSON export ([...] or { records: [...] }) or a CSV export.
const fs = require('fs');

// RFC 4180 CSV parser (handles quotes, commas and newlines inside quotes,
// which matters because indicator lines like "ST,GS,+0012.34kg" contain commas).
function parseCsv(text) {
  const rows = [];
  let row = [], field = '', q = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (q) {
      if (c === '"') {
        if (text[i + 1] === '"') { field += '"'; i++; } else q = false;
      } else field += c;
    } else if (c === '"') q = true;
    else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field); field = '';
      if (row.length > 1 || row[0] !== '') rows.push(row);
      row = [];
    } else field += c;
  }
  if (field !== '' || row.length) { row.push(field); rows.push(row); }
  const [head, ...body] = rows;
  return body.map(r => Object.fromEntries(head.map((h, i) => [h.trim(), r[i] ?? ''])));
}

function readRecords(file) {
  const text = fs.readFileSync(file, 'utf8').replace(/^﻿/, '');
  let records;
  if (/\.csv$/i.test(file)) records = parseCsv(text);
  else {
    const j = JSON.parse(text);
    records = Array.isArray(j) ? j : j.records;
  }
  if (!Array.isArray(records) || !records.length) throw new Error('No records found in ' + file);
  // Only confirmed, not removed, records are anchored
  return records.filter(r => !(r.removed === true || r.removed === 'true'));
}

module.exports = { readRecords, parseCsv };
