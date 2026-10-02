// Dessins des textures de CrusadeCraft. Un caractère = un pixel ; « . » = transparent.
// Relancer `node scripts/textures/generate.mjs` après modification.

const LEATHER = {
  k: '2e1810', r: '8e2a22', R: 'b5443a', d: '5e1a14',
  g: 'c9a24a', G: 'f2d27a', p: 'eadcb8', P: 'c9b48a',
};

export const TEXTURES = {
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
