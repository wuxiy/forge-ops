const ENCODING = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'

/** Browser-side ULID（Crockford base32，26 位，时间有序），与后端格式一致。 */
export function ulid(): string {
  const now = Date.now()
  let time = now
  let out = ''
  for (let i = 0; i < 10; i++) {
    out = ENCODING[time % 32] + out
    time = Math.floor(time / 32)
  }
  const bytes = new Uint8Array(10)
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(bytes)
  } else {
    for (let i = 0; i < 10; i++) bytes[i] = Math.floor(Math.random() * 256)
  }
  let entropy = ''
  for (const b of bytes) entropy += ENCODING[b % 32]
  return out + entropy
}
