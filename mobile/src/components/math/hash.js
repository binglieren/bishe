/**
 * 内容寻址用的 SHA-1 工具。
 *
 * 用法：const sha = await sha1Hex16(text)
 *
 * 优先 expo-crypto（原生 C 实现，<1 ms）；
 * 退化方案：纯 JS sha1（Hermes 兼容，~5 ms / KB，足够缓存键场景）。
 *
 * 返回前 16 位 hex（64 bit）—— birthday collision 在 ~4 B 条目级，缓存场景安全。
 */
import * as Crypto from 'expo-crypto';

/** 主入口：异步 sha1 → 16 hex chars */
export async function sha1Hex16(text) {
  try {
    const full = await Crypto.digestStringAsync(
      Crypto.CryptoDigestAlgorithm.SHA1,
      String(text ?? ''),
      { encoding: Crypto.CryptoEncoding.HEX }
    );
    return full.slice(0, 16);
  } catch (e) {
    // expo-crypto 在某些 dev 环境可能缺原生模块，退到 JS 实现
    return sha1JsHex16(String(text ?? ''));
  }
}

// ────────────────────────────────────────────────────────────
// 纯 JS SHA-1（公开领域，参考 RFC 3174；只在原生模块不可用时启用）
// ────────────────────────────────────────────────────────────
function sha1JsHex16(msg) {
  function rotl(n, s) { return (n << s) | (n >>> (32 - s)); }
  function toHex(n) {
    let h = '';
    for (let i = 7; i >= 0; i--) h += ((n >>> (i * 4)) & 0xf).toString(16);
    return h;
  }
  // utf-8 encode
  const bytes = [];
  for (let i = 0; i < msg.length; i++) {
    let c = msg.charCodeAt(i);
    if (c < 0x80) bytes.push(c);
    else if (c < 0x800) {
      bytes.push(0xc0 | (c >> 6));
      bytes.push(0x80 | (c & 0x3f));
    } else if (c < 0xd800 || c >= 0xe000) {
      bytes.push(0xe0 | (c >> 12));
      bytes.push(0x80 | ((c >> 6) & 0x3f));
      bytes.push(0x80 | (c & 0x3f));
    } else {
      // surrogate pair
      i++;
      const c2 = msg.charCodeAt(i);
      const cp = 0x10000 + (((c & 0x3ff) << 10) | (c2 & 0x3ff));
      bytes.push(0xf0 | (cp >> 18));
      bytes.push(0x80 | ((cp >> 12) & 0x3f));
      bytes.push(0x80 | ((cp >> 6) & 0x3f));
      bytes.push(0x80 | (cp & 0x3f));
    }
  }
  const len = bytes.length;
  bytes.push(0x80);
  while (bytes.length % 64 !== 56) bytes.push(0);
  const bitLen = len * 8;
  for (let i = 7; i >= 0; i--) bytes.push((bitLen >>> (i * 8)) & 0xff);

  let h0 = 0x67452301, h1 = 0xefcdab89, h2 = 0x98badcfe, h3 = 0x10325476, h4 = 0xc3d2e1f0;
  for (let off = 0; off < bytes.length; off += 64) {
    const w = new Array(80);
    for (let i = 0; i < 16; i++) {
      w[i] = (bytes[off + i * 4] << 24)
           | (bytes[off + i * 4 + 1] << 16)
           | (bytes[off + i * 4 + 2] << 8)
           | bytes[off + i * 4 + 3];
    }
    for (let i = 16; i < 80; i++) {
      w[i] = rotl(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
    }
    let a = h0, b = h1, c = h2, d = h3, e = h4;
    for (let i = 0; i < 80; i++) {
      let f, k;
      if (i < 20)      { f = (b & c) | ((~b) & d); k = 0x5a827999; }
      else if (i < 40) { f = b ^ c ^ d;             k = 0x6ed9eba1; }
      else if (i < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8f1bbcdc; }
      else             { f = b ^ c ^ d;             k = 0xca62c1d6; }
      const t = (rotl(a, 5) + f + e + k + w[i]) | 0;
      e = d; d = c; c = rotl(b, 30); b = a; a = t;
    }
    h0 = (h0 + a) | 0; h1 = (h1 + b) | 0; h2 = (h2 + c) | 0;
    h3 = (h3 + d) | 0; h4 = (h4 + e) | 0;
  }
  return (toHex(h0) + toHex(h1)).slice(0, 16);
}
