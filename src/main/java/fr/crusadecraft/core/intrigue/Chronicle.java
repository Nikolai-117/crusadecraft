package fr.crusadecraft.core.intrigue;

import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.world.WorldNews;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.List;

/**
 * La chronique du règne : elle s'écrit d'elle-même aux grands moments (fondation, titres, guerres, conquêtes,
 * lois, conseillers, événements, complots), et se lit dans l'onglet Chroniques ou dans le livre des chroniques.
 */
public final class Chronicle {

    private Chronicle() {}

    public static void record(MinecraftServer server, Realm realm, String key, Component... args) {
        long today = RealmService.currentDay(server);
        realm.intrigue().record(WorldNews.of(today, key, List.of(realm.id()), args));
        CrusadeSavedData.get(server).setDirty();
    }
}
