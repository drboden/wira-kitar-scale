// Hedera client from environment variables:
//   HEDERA_NETWORK      testnet (default) or mainnet
//   HEDERA_ACCOUNT_ID   e.g. 0.0.1234567
//   HEDERA_PRIVATE_KEY  the account's private key (hex or DER). Keep it in a secret store, never in code.
//   HEDERA_KEY_TYPE     ECDSA (default, what the Hedera portal gives) or ED25519
const { Client, AccountId, PrivateKey } = require('@hashgraph/sdk');

function key() {
  const k = process.env.HEDERA_PRIVATE_KEY;
  if (!k) throw new Error('Set HEDERA_PRIVATE_KEY');
  const s = k.replace(/^0x/, '');
  if (s.startsWith('30')) return PrivateKey.fromStringDer(s);
  return (process.env.HEDERA_KEY_TYPE || 'ECDSA').toUpperCase() === 'ED25519'
    ? PrivateKey.fromStringED25519(s)
    : PrivateKey.fromStringECDSA(s);
}

function client() {
  const network = (process.env.HEDERA_NETWORK || 'testnet').toLowerCase();
  const id = process.env.HEDERA_ACCOUNT_ID;
  if (!id) throw new Error('Set HEDERA_ACCOUNT_ID');
  const c = network === 'mainnet' ? Client.forMainnet() : Client.forTestnet();
  c.setOperator(AccountId.fromString(id), key());
  return { client: c, network, key: key() };
}

module.exports = { client };
