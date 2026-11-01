package fr.crusadecraft.gametest;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.config.SettingsService;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** L'écran de réglages (P30) : réservé, complet, et chaque changement revérifié puis pris en compte. */
@GameTestHolder(CrusadeCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettingsGameTests {

    private SettingsGameTests() {}

    @GameTest(template = TestWorld.TEMPLATE, setupTicks = TestWorld.SETUP_TICKS, batch = "p30_reglages", timeoutTicks = 100)
    public static void lesReglagesSeChangentEnJeu(GameTestHelper h) {
        TestWorld w = TestWorld.realm(h, "Valbrume");
        int before = CrusadeConfig.SERVER.mourningDays.get();
        try {
            w.check(!SettingsService.view(w.ruler).allowed() && SettingsService.view(w.ruler).entries().isEmpty(), "un joueur sans droits voit les réglages");
            w.check(!SettingsService.set(w.ruler, "pnj.joursDeDeuil", "5").success(), "un joueur sans droits change un réglage");

            SettingsService.grantForTests(w.ruler.getUUID(), true);
            SettingsService.View view = SettingsService.view(w.ruler);
            w.check(view.allowed() && view.entries().size() > 100, "les réglages ne sont pas tous là : " + view.entries().size());
            w.check(view.entries().stream().anyMatch(e -> e.path().equals("civilisations.actif") && e.kind() == SettingsService.Kind.BOOL),
                    "le réglage des civilisations manque");
            w.check(view.entries().stream().anyMatch(e -> e.path().equals("pnj.joursDeDeuil") && e.min().equals("0") && e.max().equals("60")),
                    "les bornes d'un nombre manquent");

            Outcome ok = SettingsService.set(w.ruler, "pnj.joursDeDeuil", "5");
            w.check(ok.success() && CrusadeConfig.SERVER.mourningDays.get() == 5, "le réglage n'est pas pris en compte : " + ok.message().getString());
            w.check(!SettingsService.set(w.ruler, "pnj.joursDeDeuil", "999").success(), "une valeur hors bornes est acceptée");
            w.check(!SettingsService.set(w.ruler, "pnj.joursDeDeuil", "beaucoup").success(), "un texte passe pour un nombre");
            w.check(!SettingsService.set(w.ruler, "economie.valeursOr", "x").success(), "une liste se change depuis l'écran");
            w.check(!SettingsService.set(w.ruler, "rien.du.tout", "1").success(), "un réglage inconnu est accepté");
            h.succeed();
        } finally {
            CrusadeConfig.SERVER.mourningDays.set(before);
            SettingsService.grantForTests(w.ruler.getUUID(), false);
            w.cleanup();
        }
    }
}
