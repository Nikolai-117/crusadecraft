// Dessins des textures de CrusadeCraft. Un caractère = un pixel ; « . » = transparent.
// Relancer `node scripts/textures/generate.mjs` après modification.

const LEATHER = {
  k: '2e1810', r: '8e2a22', R: 'b5443a', d: '5e1a14',
  g: 'c9a24a', G: 'f2d27a', p: 'eadcb8', P: 'c9b48a',
};

const REGALIA = {
  k: '3b2610', g: 'c9a24a', G: 'f4d77a', d: '8a6a1e', r: 'b3261e', R: 'e8655a',
};

export const TEXTURES = {
  // Cadre de la Table de guerre : parchemin dans un liseré de chêne noir, avec un tracé de carte.
  'block/war_map': {
    palette: { k: '2b1d12', w: '5a3d24', p: 'e8d9b0', P: 'd6c393', b: '6d8fb3', r: 'a8443a', g: '7a8c4e', d: '8a7650' },
    pixels: [
      'kkkkkkkkkkkkkkkk',
      'kwwwwwwwwwwwwwwk',
      'kwpppPpppppbpppk',
      'kwppgggpppbbpPpk',
      'kwpgggggpbbppppk',
      'kwpPgrggpbpppppk',
      'kwppprpppbpppdpk',
      'kwppprrrrbrrppPk',
      'kwpPpppprbppprpk',
      'kwppdppprbppprpk',
      'kwpppppbbpppgrgk',
      'kwppPpbbppppggPk',
      'kwpppbbpppPppppk',
      'kwppbbpppppppPpk',
      'kwwwwwwwwwwwwwwk',
      'kkkkkkkkkkkkkkkk',
    ],
  },
  'item/sceptre': {
    palette: REGALIA,
    pixels: [
      '...........kk...',
      '..........kGGk..',
      '.........kGRrGk.',
      '.........kgrRgk.',
      '..........kggk..',
      '.........kdgk...',
      '........kdgk....',
      '.......kdgk.....',
      '......kdgk......',
      '.....kdGk.......',
      '....kdgk........',
      '...kdgk.........',
      '..kGgk..........',
      '.kGGk...........',
      '.kkk............',
      '................',
    ],
  },
  'item/registre': {
    palette: LEATHER,
    pixels: [
      '................',
      '..kkkkkkkkkkkk..',
      '..kgrrrrrrrrgk..',
      '..krRRRRRRRRrkp.',
      '..krRdddddRRrkp.',
      '..krRdGGGdRRrkP.',
      '..krRdGgGdRRrkp.',
      '..kgggGgGggggkp.',
      '..krRdGgGdRRrkP.',
      '..krRdGGGdRRrkp.',
      '..krRdddddRRrkp.',
      '..krRRRRRRRRrkP.',
      '..kgrrrrrrrrgkp.',
      '..kkkkkkkkkkkkk.',
      '...pppppppppppk.',
      '...kkkkkkkkkkk..',
    ],
  },
};
