#!/usr/bin/env node
// One-off: create KLEAN's Wira Kitar anchoring topic on Hedera.
// Only KLEAN's key can post to it (submit key), but anyone can read it.
const { TopicCreateTransaction } = require('@hashgraph/sdk');
const { client: makeClient } = require('./hedera');

(async () => {
  const { client, network, key } = makeClient();
  const tx = await new TopicCreateTransaction()
    .setTopicMemo('Wira Kitar verified recovery anchors (KLEAN)')
    .setSubmitKey(key.publicKey)
    .execute(client);
  const receipt = await tx.getReceipt(client);
  client.close();
  const id = receipt.topicId.toString();
  console.log(`Created topic ${id} on ${network}`);
  console.log(`Set HEDERA_TOPIC_ID=${id}`);
  console.log(`View: https://hashscan.io/${network}/topic/${id}`);
})().catch(e => { console.error('Error:', e.message); process.exit(1); });
