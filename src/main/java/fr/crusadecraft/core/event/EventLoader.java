package fr.crusadecraft.core.event;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import fr.crusadecraft.CrusadeCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lit les événements des datapacks ({@code data/<espace>/crusade_events/*.json}), au démarrage et à chaque
 * {@code /reload}. Un fichier illisible est signalé et ignoré ; les autres restent valables.
 */
public final class EventLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public EventLoader() {
        super(GSON, "crusade_events");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, EventDef> events = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            try {
                events.put(e.getKey(), EventDef.parse(e.getKey(), e.getValue().getAsJsonObject()));
            } catch (RuntimeException ex) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : événement {} illisible, ignoré : {}", e.getKey(), ex.getMessage());
            }
        }
        EventService.setEvents(events);
        CrusadeCraft.LOGGER.info("CrusadeCraft : {} événement(s) chargé(s)", events.size());
    }
}
