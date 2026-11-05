package fr.crusadecraft.client.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.city.SkinService;
import fr.crusadecraft.core.city.SkinSources;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.SkinPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Les skins côté joueur (P47) : les aller chercher sur Internet, depuis un pseudo, un lien ou la page d'un site connu ; les
 * envoyer au serveur ; recevoir ceux des PNJ qu'on croise, et les changer en textures.
 */
public final class SkinClient {
    private static final Map<UUID, String> ASSIGNED = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<String, Boolean> SLIM = new HashMap<>();
    private static final Set<String> ASKED = new HashSet<>();
    private static List<SkinService.Entry> library = List.of();
    private static final int CONNECT_MS = 6000, READ_MS = 10000;

    private SkinClient() {}

    public static void onLeave() {
        ASSIGNED.clear();
        ASKED.clear();
        library = List.of();
        TEXTURES.values().forEach(rl -> Minecraft.getInstance().getTextureManager().release(rl));
        TEXTURES.clear();
        SLIM.clear();
    }

    public static void onAssign(boolean full, Map<UUID, String> skins) {
        if (full) ASSIGNED.clear();
        skins.forEach((id, hash) -> {
            if (hash.isEmpty()) ASSIGNED.remove(id);
            else ASSIGNED.put(id, hash);
        });
    }

    public static void onLibrary(List<SkinService.Entry> entries) {
        library = List.copyOf(entries);
    }

    public static List<SkinService.Entry> library() {
        return library;
    }

    public static void askLibrary() {
        CrusadeNetwork.toServer(new SkinPackets.Request(""));
    }

    /** Le skin importé que porte cette entité, s'il en porte un et qu'on l'a déjà reçu. */
    public static String hashOf(UUID entity) {
        return ASSIGNED.get(entity);
    }

    public static ResourceLocation texture(String hash) {
        ResourceLocation rl = TEXTURES.get(hash);
        if (rl == null && ASKED.add(hash)) CrusadeNetwork.toServer(new SkinPackets.Request(hash));
        return rl;
    }

    public static boolean slim(String hash) {
        return SLIM.getOrDefault(hash, false);
    }

    public static void onData(String hash, boolean slim, byte[] bytes) {
        try {
            NativeImage img = legacyToModern(NativeImage.read(new java.io.ByteArrayInputStream(bytes)));
            ResourceLocation rl = new ResourceLocation(CrusadeCraft.MOD_ID, "npc_skin/" + hash);
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
            TEXTURES.put(hash, rl);
            SLIM.put(hash, slim);
        } catch (IOException | RuntimeException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : skin {} illisible", hash, e);
        }
    }

    /** Comme Minecraft pour les joueurs : une seconde couche sans le moindre trou, sur un vieux skin, c'est un fond oublié, pas un chapeau. */
    private static void notchHack(NativeImage img, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) for (int y = y0; y < y1; y++) if ((img.getPixelRGBA(x, y) >>> 24) < 128) return;
        for (int x = x0; x < x1; x++) for (int y = y0; y < y1; y++) img.setPixelRGBA(x, y, img.getPixelRGBA(x, y) & 0x00FFFFFF);
    }

    /** L'ancien format, deux fois plus large que haut : on lui ajoute le bas, comme le fait Minecraft pour les joueurs. */
    private static NativeImage legacyToModern(NativeImage in) {
        if (in.getHeight() == in.getWidth()) return in;
        int s = in.getWidth() / 64;
        notchHack(in, 32 * s, 0, 64 * s, 32 * s);
        NativeImage out = new NativeImage(in.getWidth(), in.getWidth(), true);
        out.copyFrom(in);
        in.close();
        out.fillRect(0, 32 * s, 64 * s, 32 * s, 0);
        // Jambe et bras gauches : miroirs des droits.
        int[][] parts = {{4, 16, 16, 32, 4, 4}, {8, 16, 16, 32, 4, 4}, {0, 20, 24, 32, 4, 12}, {4, 20, 16, 32, 4, 12}, {8, 20, 8, 32, 4, 12}, {12, 20, 16, 32, 4, 12},
                {44, 16, -8, 32, 4, 4}, {48, 16, -8, 32, 4, 4}, {40, 20, 0, 32, 4, 12}, {44, 20, -8, 32, 4, 12}, {48, 20, -16, 32, 4, 12}, {52, 20, -8, 32, 4, 12}};
        for (int[] p : parts) out.copyRect(p[0] * s, p[1] * s, p[2] * s, p[3] * s, p[4] * s, p[5] * s, true, false);
        return out;
    }

    // ------------------------------------------------------------------ aller le chercher

    /**
     * Va chercher un skin : un pseudo, un lien direct, l'adresse d'une page d'un site connu, ou un fichier du dossier
     * {@code crusadecraft/skins_a_importer}. Sans bloquer le jeu ; le résultat revient par {@code done} (une clé de langue).
     */
    public static void importSkin(String rawInput, UUID npc, Consumer<Component> done) {
        String input = rawInput == null ? "" : rawInput.strip();
        Thread t = new Thread(() -> {
            Component result;
            try {
                byte[] data;
                String name, source;
                if (SkinSources.isUrl(input)) {
                    source = SkinSources.directUrl(input);
                    data = download(source);
                    name = nameFromUrl(source);
                } else if (SkinSources.isName(input) && !Files.exists(localFolder().resolve(input + ".png"))) {
                    source = input;
                    data = download(skinUrlOf(input));
                    name = input;
                } else {
                    Path file = localFolder().resolve(input.endsWith(".png") ? input : input + ".png").normalize();
                    if (!file.startsWith(localFolder()) || !Files.exists(file)) {
                        finish(done, Component.translatable("crusadecraft.skin.not_found", input));
                        return;
                    }
                    data = Files.readAllBytes(file);
                    name = input.endsWith(".png") ? input.substring(0, input.length() - 4) : input;
                    source = "";
                }
                if (SkinSources.check(data) == null) {
                    finish(done, Component.translatable("crusadecraft.skin.not_a_skin"));
                    return;
                }
                send(data, name, source, npc);
                result = Component.translatable("crusadecraft.skin.sent", name);
            } catch (IOException | RuntimeException e) {
                CrusadeCraft.LOGGER.warn("CrusadeCraft : import du skin « {} » impossible : {}", input, e.toString());
                result = Component.translatable("crusadecraft.skin.failed", input);
            }
            finish(done, result);
        }, "CrusadeCraft skin");
        t.setDaemon(true);
        t.start();
    }

    private static void finish(Consumer<Component> done, Component message) {
        Minecraft.getInstance().execute(() -> done.accept(message));
    }

    public static Path localFolder() {
        return FMLPaths.GAMEDIR.get().resolve("crusadecraft").resolve("skins_a_importer").toAbsolutePath().normalize();
    }

    /** Les fichiers qu'on a rangés soi-même, à importer d'un clic. */
    public static List<String> localFiles() {
        List<String> out = new ArrayList<>();
        try {
            Files.createDirectories(localFolder());
            try (Stream<Path> files = Files.list(localFolder())) {
                files.filter(f -> f.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")).forEach(f -> out.add(f.getFileName().toString()));
            }
        } catch (IOException e) {
            CrusadeCraft.LOGGER.warn("CrusadeCraft : dossier des skins illisible", e);
        }
        java.util.Collections.sort(out);
        return out;
    }

    private static void send(byte[] data, String name, String source, UUID npc) {
        Minecraft.getInstance().execute(() -> {
            int parts = Math.max(1, (data.length + SkinPackets.PART - 1) / SkinPackets.PART);
            for (int i = 0; i < parts; i++) {
                int from = i * SkinPackets.PART, to = Math.min(data.length, from + SkinPackets.PART);
                byte[] piece = java.util.Arrays.copyOfRange(data, from, to);
                String shortSource = source.length() > 250 ? source.substring(0, 250) : source;
                CrusadeNetwork.toServer(new SkinPackets.Upload(i, i == parts - 1, piece, name.length() > 60 ? name.substring(0, 60) : name, shortSource, npc));
            }
        });
    }

    private static String nameFromUrl(String url) {
        String s = url.replaceAll("[?#].*$", "").replaceAll("/+$", "");
        s = s.substring(s.lastIndexOf('/') + 1).replaceAll("\\.png$", "");
        return s.isBlank() ? "Skin" : s;
    }

    /** Le skin d'un compte Minecraft : Mojang donne son identifiant, puis l'adresse de son image. */
    private static String skinUrlOf(String name) throws IOException {
        String body = new String(download("https://api.mojang.com/users/profiles/minecraft/" + name), StandardCharsets.UTF_8);
        String id = JsonParser.parseString(body).getAsJsonObject().get("id").getAsString();
        String profile = new String(download("https://sessionserver.mojang.com/session/minecraft/profile/" + id), StandardCharsets.UTF_8);
        for (JsonElement e : JsonParser.parseString(profile).getAsJsonObject().getAsJsonArray("properties")) {
            JsonObject o = e.getAsJsonObject();
            if (!"textures".equals(o.get("name").getAsString())) continue;
            String decoded = new String(Base64.getDecoder().decode(o.get("value").getAsString()), StandardCharsets.UTF_8);
            return JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString().replace("http://", "https://");
        }
        throw new IOException("aucun skin pour " + name);
    }

    /** Au plus un méga-octet, quelques redirections, et jamais de quoi faire attendre le jeu. */
    private static byte[] download(String url) throws IOException {
        String current = url;
        for (int hops = 0; hops < 4; hops++) {
            HttpURLConnection c = (HttpURLConnection) URI.create(current).toURL().openConnection();
            c.setConnectTimeout(CONNECT_MS);
            c.setReadTimeout(READ_MS);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (CrusadeCraft)");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                current = URI.create(current).resolve(c.getHeaderField("Location")).toString();
                if (!SkinSources.isUrl(current)) throw new IOException("redirection refusée");
                continue;
            }
            if (code != 200) throw new IOException("réponse " + code);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    if (out.size() > SkinSources.MAX_BYTES) throw new IOException("trop gros");
                }
                return out.toByteArray();
            }
        }
        throw new IOException("trop de redirections");
    }
}
