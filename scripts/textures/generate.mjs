// Génère les textures pixel-art de CrusadeCraft à partir des dessins ASCII de textures.mjs.
// Usage : node scripts/textures/generate.mjs
// Aucune dépendance : l'encodeur PNG tient dans ce fichier (zlib est fourni par Node).
import { deflateSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { TEXTURES } from './textures.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '..', '..', 'src', 'main', 'resources', 'assets', 'crusadecraft', 'textures');

const CRC_TABLE = new Uint32Array(256).map((_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}

function png(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // filtre « aucun »
    rgba.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function hex(color) {
  const v = parseInt(color, 16);
  return [(v >> 16) & 0xff, (v >> 8) & 0xff, v & 0xff, 0xff];
}

let count = 0;
for (const [name, def] of Object.entries(TEXTURES)) {
  const rows = def.pixels;
  const h = rows.length;
  const w = rows[0].length;
  const rgba = Buffer.alloc(w * h * 4);
  rows.forEach((row, y) => {
    if (row.length !== w) throw new Error(`${name} : la ligne ${y} fait ${row.length} caractères au lieu de ${w}`);
    [...row].forEach((ch, x) => {
      if (ch === '.') return;
      const color = def.palette[ch];
      if (!color) throw new Error(`${name} : couleur inconnue « ${ch} » (ligne ${y})`);
      rgba.set(hex(color), (y * w + x) * 4);
    });
  });
  const file = join(out, `${name}.png`);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, png(w, h, rgba));
  count++;
}
console.log(`${count} texture(s) écrite(s) dans ${out}`);
