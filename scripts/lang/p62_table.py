"""P62 : les textes de la carte telle quelle et des titres en créatif. Produit p62_fr.json et p62_en.json, à fusionner par merge.mjs."""
import io
import json
import os

ROWS = [
    ('crusadecraft.hub.chancery.grant', 'Accorder le titre : %s', 'Grant the title: %s'),
    ('crusadecraft.hub.chancery.free', 'Mode créatif : le titre s\'accorde sans conditions ; chaque cran de la frise se clique.',
     'Creative mode: titles are granted with no conditions; every step of the timeline can be clicked.'),
    ('crusadecraft.title.granted', 'Titre accordé : %s (mode créatif).', 'Title granted: %s (creative mode).'),
    ('crusadecraft.title.free_only', 'Seul l\'hôte ou un opérateur, en mode créatif, s\'accorde un titre ainsi.',
     'Only the host or an operator, in creative mode, can grant themselves a title this way.'),
    ('crusadecraft.title.already', 'Vous tenez déjà ce titre : %s.', 'You already hold this title: %s.'),
    ('crusadecraft.command.titre.unknown', '« %s » n\'est pas un titre : baronnie, comte, duche, royaume ou empire.',
     '"%s" is not a title: baronnie, comte, duche, royaume or empire.'),
    ('crusadecraft.command.titre.done', '%s est désormais : %s.', '%s is now: %s.'),
]

here = os.path.dirname(os.path.abspath(__file__))
for i, name in ((1, 'p62_fr.json'), (2, 'p62_en.json')):
    with io.open(os.path.join(here, name), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({r[0]: r[i] for r in ROWS}, f, ensure_ascii=False, indent=2)
        f.write('\n')
print(len(ROWS), 'textes')
