// Génère les structures NBT des GameTests de CrusadeCraft (une dalle de pierre lisse, rien d'autre).
// Usage : node scripts/structures/generate.mjs
import { gzipSync } from 'node:zlib';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '..', '..', 'src', 'main', 'resources', 'data', 'crusadecraft', 'structures');

// --- encodeur NBT minimal ---
const T = { END: 0, BYTE: 1, INT: 3, STRING: 8, LIST: 9, COMPOUND: 10 };
const int = (v) => ({ t: T.INT, v });
const str = (v) => ({ t: T.STRING, v });
const list = (type, items) => ({ t: T.LIST, type, v: items });
const comp = (obj) => ({ t: T.COMPOUND, v: obj });

function payload(tag) {
  switch (tag.t) {
    case T.INT: { const b = Buffer.alloc(4); b.writeInt32BE(tag.v); return b; }
    case T.STRING: { const s = Buffer.from(tag.v, 'utf8'); const l = Buffer.alloc(2); l.writeUInt16BE(s.length); return Buffer.concat([l, s]); }
    case T.LIST: {
      const head = Buffer.alloc(5);
      head.writeUInt8(tag.v.length ? tag.type : T.END, 0);
      head.writeInt32BE(tag.v.length, 1);
      return Buffer.concat([head, ...tag.v.map(payload)]);
    }
    case T.COMPOUND: {
      const parts = [];
      for (const [k, v] of Object.entries(tag.v)) parts.push(named(k, v));
      parts.push(Buffer.from([T.END]));
      return Buffer.concat(parts);
    }
    default: throw new Error('type NBT non géré : ' + tag.t);
  }
}

function named(name, tag) {
  const n = Buffer.from(name, 'utf8');
  const head = Buffer.alloc(3);
  head.writeUInt8(tag.t, 0);
  head.writeUInt16BE(n.length, 1);
  return Buffer.concat([head, n, payload(tag)]);
}

function slab(size) {
  const blocks = [];
  for (let x = 0; x < size; x++) for (let z = 0; z < size; z++) {
    blocks.push(comp({ pos: list(T.INT, [int(x), int(0), int(z)]), state: int(0) }));
  }
  return comp({
    DataVersion: int(3465), // 1.20.1
    size: list(T.INT, [int(size), int(6), int(size)]),
    palette: list(T.COMPOUND, [comp({ Name: str('minecraft:smooth_stone') })]),
    blocks: list(T.COMPOUND, blocks),
    entities: list(T.COMPOUND, []),
  });
}

mkdirSync(out, { recursive: true });
writeFileSync(join(out, 'dalle.nbt'), gzipSync(named('', slab(9))));
console.log('Structure écrite : ' + join(out, 'dalle.nbt'));
