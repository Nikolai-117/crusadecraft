// Fusionne les fichiers de langue d'une phase (scripts/lang/<phase>_<fr|en>.json) dans les fichiers
// du mod. Les clés existantes sont remplacées, les nouvelles ajoutées à la fin. Usage :
//   node scripts/lang/merge.mjs p2
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const langDir = join(here, '..', '..', 'src', 'main', 'resources', 'assets', 'crusadecraft', 'lang');
const phase = process.argv[2];
if (!phase) throw new Error('Préciser la phase, par exemple : node scripts/lang/merge.mjs p2');

for (const [suffix, file] of [['fr', 'fr_fr.json'], ['en', 'en_us.json']]) {
  const target = join(langDir, file);
  const base = JSON.parse(readFileSync(target, 'utf8'));
  const overlay = JSON.parse(readFileSync(join(here, `${phase}_${suffix}.json`), 'utf8'));
  let added = 0, replaced = 0;
  for (const [k, v] of Object.entries(overlay)) {
    if (k in base) replaced++; else added++;
    base[k] = v;
  }
  writeFileSync(target, JSON.stringify(base, null, 2) + '\n', 'utf8');
  console.log(`${file} : ${added} ajoutée(s), ${replaced} remplacée(s)`);
}
