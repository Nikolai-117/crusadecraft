package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Ce qu'on raconte dans une ville (P42) : ce qui s'y est vraiment passé (un mariage, une naissance, un marchand mort,
 * un joueur qui frappe quelqu'un, une pénurie), que les habitants répètent. Une rumeur vit quelques jours.
 */
public final class CityRumors {
    /** Une rumeur s'éteint au bout de tant de jours. */
    static final int LIFE_DAYS = 6;
    /** Au plus, ce qu'une ville garde en mémoire. */
    static final int MAX = 12;

    private CityRumors() {}

    /** P46 : un texte qu'on a écrit soi-même, dans le panneau de la ville. */
    public static void addText(MinecraftServer server, Site site, String text) {
        site.rumors().add(0, new Site.Rumor(text, fr.crusadecraft.core.realm.RealmService.currentDay(server)));
        CityJournal.add(server, site, CityJournal.Kind.NEWS, text);
        while (site.rumors().size() > MAX) site.rumors().remove(site.rumors().size() - 1);
    }

    /** Une rumeur de plus : {@code kind} est la fin d'une clé de langue ({@code crusadecraft.city.rumor.mariage}), {@code args} ses trous. */
    public static void add(MinecraftServer server, Site site, String kind, Object... args) {
        String text = Fr.key("crusadecraft.city.rumor." + kind.toLowerCase(java.util.Locale.ROOT), args);
        long today = RealmService.currentDay(server);
        List<Site.Rumor> list = site.rumors();
        list.removeIf(r -> r.text().equals(text));
        list.add(0, new Site.Rumor(text, today));
        while (list.size() > MAX) list.remove(list.size() - 1);
        CityJournal.add(server, site, CityJournal.Kind.NEWS, text);
        CrusadeSavedData.get(server).setDirty();
    }

    /** Une rumeur encore vivante. */
    public static Optional<String> pick(MinecraftServer server, Site site, Random random) {
        long today = RealmService.currentDay(server);
        site.rumors().removeIf(r -> today - r.day() > LIFE_DAYS);
        if (site.rumors().isEmpty()) return Optional.empty();
        return Optional.of(site.rumors().get(random.nextInt(site.rumors().size())).text());
    }
}
