package fr.crusadecraft.core.city;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.council.CouncilActions.Outcome;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.SkinPackets;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les skins de la ville (P47). Celui qui en importe un (depuis un lien, un pseudo, un fichier) l'envoie au serveur, qui le
 * garde dans {@code crusadecraft/skins/} et le renvoie à chaque joueur qui croise son porteur. Le site d'origine peut donc
 * disparaître, le serveur peut n'avoir aucun accès à Internet, et personne n'a rien à installer.
 */
public final class SkinService {
    /** Au plus, tant de skins dans la bibliothèque. */
    static final int MAX_SKINS = 512;
    public static final String PREFIX = "skin:";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Un skin de la bibliothèque. */
    public record Entry(String hash, String name, String source, boolean slim) {}

    private static Map<String, Entry> library;
    /** Ce qu'un joueur est en train d'envoyer, morceau par morceau. */
    private static final Map<UUID, java.io.ByteArrayOutputStream> UPLOADS = new HashMap<>();

    private SkinService() {}

    public static void onServerStopped() {
        library = null;
        UPLOADS.clear();
    }

    public static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve("crusadecraft").resolve("skins");
    }

    private static synchronized Map<String, Entry> library() {
        if (library != null) return library;
        Map<String, Entry> out = new LinkedHashMap<>();
        Path index = folder().resolve("index.json");
        if (Files.exists(index)) {
            try {
                JsonArray a = JsonParser.parseString(Files.readString(index, StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement e : a) {
                    JsonObject o = e.getAsJsonObject();
                    String hash = o.get("empreinte").getAsString();
                    if (Files.exists(folder().resolve(hash + ".png"))) {
                        out.put(hash, new Entry(hash, o.get("nom").getAsString(), o.has("source") ? o.get("source").getAsString() : "", o.has("alex") && o.get("alex").getAsBoolean()));
                    }
                }
            } catch (IOException | RuntimeException e) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : liste des skins illisible", e);
            }
        }
        library = out;
        return library;
    }

    private static void saveIndex() {
        JsonArray a = new JsonArray();
        for (Entry e : library().values()) {
            JsonObject o = new JsonObject();
            o.addProperty("empreinte", e.hash());
            o.addProperty("nom", e.name());
            o.addProperty("source", e.source());
            o.addProperty("alex", e.slim());
            a.add(o);
        }
        try {
            Files.writeString(folder().resolve("index.json"), GSON.toJson(a), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : liste des skins non écrite", e);
        }
    }

    public static List<Entry> entries() {
        return new ArrayList<>(library().values());
    }

    public static Optional<Entry> entry(String hash) {
        return Optional.ofNullable(library().get(hash));
    }

    static String hash(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data)).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Un morceau d'un skin qu'on envoie ; le dernier le fait entrer dans la bibliothèque, et le met à ce PNJ s'il y en a un. */
    public static void onUploadPart(ServerPlayer player, int part, boolean last, byte[] bytes, String name, String source, UUID npc) {
        if (!CityService.mayEditAnything(player)) {
            player.displayClientMessage(Component.translatable("crusadecraft.city.denied"), true);
            return;
        }
        java.io.ByteArrayOutputStream out = part == 0 ? new java.io.ByteArrayOutputStream() : UPLOADS.get(player.getUUID());
        if (out == null || out.size() + bytes.length > SkinSources.MAX_BYTES) {
            UPLOADS.remove(player.getUUID());
            player.displayClientMessage(Component.translatable("crusadecraft.skin.too_big"), true);
            return;
        }
        out.write(bytes, 0, bytes.length);
        UPLOADS.put(player.getUUID(), out);
        if (!last) return;
        UPLOADS.remove(player.getUUID());
        Outcome res = store(name, source, out.toByteArray());
        player.displayClientMessage(res.message(), true);
        if (!res.success()) return;
        CrusadeNetwork.toPlayer(player, new SkinPackets.Library(entries()));
        String hash = hash(out.toByteArray());
        if (npc != null) CityNpc.setLook(player, npc, PREFIX + hash);
    }

    /** Entre dans la bibliothèque, si c'est bien un skin. Le même deux fois ne compte qu'une fois. */
    public static Outcome store(String rawName, String source, byte[] data) {
        SkinSources.Checked checked = SkinSources.check(data);
        if (checked == null) return Outcome.no("crusadecraft.skin.invalid");
        String name = rawName == null || rawName.isBlank() ? "Skin" : rawName.strip();
        if (name.length() > 48) name = name.substring(0, 48);
        String hash = hash(data);
        Map<String, Entry> lib = library();
        if (lib.containsKey(hash)) return Outcome.ok(Component.translatable("crusadecraft.skin.known", lib.get(hash).name()));
        if (lib.size() >= MAX_SKINS) return Outcome.no("crusadecraft.skin.full", MAX_SKINS);
        try {
            Files.createDirectories(folder());
            Files.write(folder().resolve(hash + ".png"), data);
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : skin non écrit", e);
            return Outcome.no("crusadecraft.skin.invalid");
        }
        lib.put(hash, new Entry(hash, name, source == null ? "" : source, checked.slim()));
        saveIndex();
        return Outcome.ok(Component.translatable("crusadecraft.skin.added", name));
    }

    /** L'image d'un skin, pour le joueur qui la demande. */
    public static void send(ServerPlayer player, String hash) {
        Entry e = library().get(hash);
        if (e == null) return;
        try {
            CrusadeNetwork.toPlayer(player, new SkinPackets.Data(hash, e.slim(), Files.readAllBytes(folder().resolve(hash + ".png"))));
        } catch (IOException ex) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : skin {} illisible", hash, ex);
        }
    }

    /** L'empreinte d'un skin importé, si cette allure en est un. */
    public static String hashOf(String look) {
        return look != null && look.startsWith(PREFIX) ? look.substring(PREFIX.length()) : "";
    }

    /** Qui porte quoi : à l'arrivée d'un joueur. */
    public static void onLogin(ServerPlayer player) {
        Map<UUID, String> all = new HashMap<>();
        for (Person p : CrusadeSavedData.get(player.server).lineage().persons()) {
            if (!p.alive() || !p.hasSettings()) continue;
            String hash = hashOf(p.settings().look);
            if (!hash.isEmpty()) all.put(p.id(), hash);
        }
        CrusadeNetwork.toPlayer(player, new SkinPackets.Assign(true, all));
    }

    /** Ce PNJ change de skin : tout le monde le saura. */
    public static void broadcast(MinecraftServer server, UUID id, String hash) {
        Map<UUID, String> one = new HashMap<>();
        one.put(id, hash);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) CrusadeNetwork.toPlayer(p, new SkinPackets.Assign(false, one));
    }
}
