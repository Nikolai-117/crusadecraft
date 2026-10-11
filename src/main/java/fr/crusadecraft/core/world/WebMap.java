package fr.crusadecraft.core.world;

import fr.crusadecraft.compat.ServerText;
import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.place.Lieu;
import fr.crusadecraft.core.place.LieuKind;
import fr.crusadecraft.core.place.PlaceService;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.war.WarFieldService;
import fr.crusadecraft.core.war.WarMap;
import fr.crusadecraft.data.CrusadeSavedData;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ce que montre la carte web (P17), sans rien savoir de Dynmap : pour chaque royaume, ses chunks en rectangles et sa
 * frontière en segments, avec la fiche qui s'ouvre au clic ; pour chaque lieu, un repère et sa fiche ; et la carte de
 * guerre (P18) : zones, flèches, repères et sièges en cours. Le module de Dynmap ({@code compat/dynmap}) ne fait que
 * recopier ce modèle dans ses marqueurs. Les identifiants disent la couche : {@code lieu_…}, {@code guerre_…}, le reste
 * pour les royaumes.
 */
public final class WebMap {
    /** Un aplat (rectangle de chunks ou région). */
    public record Area(String id, String world, String label, String html, double[] xs, double[] zs, int fill, double opacity, int line, int weight) {}

    /** Un trait de frontière. */
    public record Line(String id, String world, String label, String html, double[] xs, double[] zs, int color, int weight) {}

    /** Un repère. */
    public record Point(String id, String world, String label, String html, double x, double y, double z, String icon) {}

    /** Tout ce qu'il faut dessiner ; {@code hash} change dès que quelque chose change. */
    public record Model(List<Area> areas, List<Line> lines, List<Point> points, int hash) {}

    /** Les couleurs des zones de guerre : offensive, défense, objectif (les mêmes que sur la carte du mod). */
    static final int[] ZONE_COLORS = {0xD03A2F, 0x2F6FD0, 0xE0B020};

    private WebMap() {}

    /** Le nom d'un monde tel que Dynmap le donne sous Forge : le monde principal porte le nom de la sauvegarde. */
    public static String worldName(ServerLevel level) {
        ResourceLocation id = level.dimension().location();
        if (level.dimension() == Level.OVERWORLD) return level.getServer().getWorldData().getLevelName();
        if (level.dimension() == Level.END) return "DIM1";
        if (level.dimension() == Level.NETHER) return "DIM-1";
        return id.getNamespace() + "_" + id.getPath();
    }

    public static Model build(MinecraftServer server) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        String lang = CrusadeConfig.SERVER.webMapLanguage.get();
        boolean showAi = CrusadeConfig.SERVER.webMapAiRealms.get();
        double opacity = CrusadeConfig.SERVER.webMapOpacity.get();
        List<Area> areas = new ArrayList<>();
        List<Line> lines = new ArrayList<>();
        List<Point> points = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            String world = worldName(level);
            Territory.Snapshot snap = Territory.snapshot(server, level.dimension().location());
            for (Map.Entry<UUID, LongSet> e : snap.chunksByOwner().entrySet()) {
                UUID owner = e.getKey();
                Optional<AiRealm> ai = data.world().realm(owner);
                Optional<Realm> human = data.realm(owner);
                if (ai.isEmpty() && human.isEmpty()) continue;
                if (ai.isPresent() && !showAi) continue;
                int color = ai.map(AiRealm::color).orElseGet(() -> human.get().color()) & 0xFFFFFF;
                String name = ServerText.render(realmName(ai, human), lang);
                String html = realmSheet(server, data, ai, human, e.getValue().size(), snap.claimedBy(owner), lang);
                String base = "royaume_" + owner + "_" + world;
                int i = 0;
                for (WebMapGeometry.Rect r : WebMapGeometry.rectangles(e.getValue())) {
                    double x0 = r.x0() * TerritoryMap.CELL, z0 = r.z0() * TerritoryMap.CELL;
                    double x1 = (r.x1() + 1) * TerritoryMap.CELL, z1 = (r.z1() + 1) * TerritoryMap.CELL;
                    areas.add(new Area(base + "_" + i++, world, name, html, new double[]{x0, x1, x1, x0}, new double[]{z0, z0, z1, z1},
                            color, opacity, color, 0));
                }
                int j = 0;
                int border = darker(color);
                for (WebMapGeometry.Segment s : WebMapGeometry.border(e.getValue())) {
                    lines.add(new Line(base + "_bord_" + j++, world, name, html, new double[]{s.x0(), s.x1()}, new double[]{s.z0(), s.z1()}, border, 3));
                }
            }
        }
        if (CrusadeConfig.SERVER.webMapPlaces.get()) addPlaces(server, data, lang, points, areas);
        if (CrusadeConfig.SERVER.webMapWar.get()) addWar(server, data, lang, areas, lines, points);
        int hash = 17;
        for (Area a : areas) hash = hash * 31 + a.id().hashCode() * 7 + a.html().hashCode() + java.util.Arrays.hashCode(a.xs()) + java.util.Arrays.hashCode(a.zs());
        for (Line l : lines) hash = hash * 31 + l.id().hashCode() + java.util.Arrays.hashCode(l.xs()) + java.util.Arrays.hashCode(l.zs());
        for (Point p : points) hash = hash * 31 + p.id().hashCode() + p.html().hashCode() + Double.hashCode(p.x() + p.z());
        return new Model(areas, lines, points, hash);
    }

    private static Component realmName(Optional<AiRealm> ai, Optional<Realm> human) {
        return ai.<Component>map(r -> AiNames.realm(r.rank(), r.place())).orElseGet(() -> Component.literal(human.get().name()));
    }

    /** La fiche d'un royaume, comme sur la capture « Duchy of France » : titre, souverain, trésor, population, terres… */
    static String realmSheet(MinecraftServer server, CrusadeSavedData data, Optional<AiRealm> ai, Optional<Realm> human, int chunks,
                             int claimed, String lang) {
        StringBuilder h = new StringBuilder("<div class=\"crusade-fiche\" style=\"min-width:220px\">");
        String name = ServerText.render(realmName(ai, human), lang);
        h.append("<div style=\"font-size:16px;font-weight:bold\">").append(esc(name)).append("</div>");
        var rank = ai.map(AiRealm::rank).orElseGet(() -> human.get().rank());
        h.append("<div>★ ").append(esc(text(Component.translatable(rank.titleKey()), lang))).append("</div>");
        if (human.isPresent() && human.get().motto() != null && !human.get().motto().isBlank()) {
            h.append("<div style=\"font-style:italic;opacity:.8\">").append(esc(human.get().motto())).append("</div>");
        }
        h.append("<br>");
        if (ai.isPresent()) {
            AiRealm r = ai.get();
            row(h, lang, "crusadecraft.webmap.ruler", r.ruler().name() + " (" + text(Component.translatable(rank.holderKey(r.ruler().female())), lang) + ")");
            if (CrusadeConfig.SERVER.webMapTreasury.get()) row(h, lang, "crusadecraft.webmap.treasury", String.format("%,d", r.gold()).replace(',', ' '));
            row(h, lang, "crusadecraft.webmap.faith", text(Component.translatable(r.faith().key()), lang));
            row(h, lang, "crusadecraft.webmap.house", r.house());
            row(h, lang, "crusadecraft.webmap.population", String.valueOf(r.population()));
        } else {
            Realm r = human.get();
            row(h, lang, "crusadecraft.webmap.ruler", r.rulerName() + " (" + text(Component.translatable(rank.holderKey(r.feminineTitle())), lang) + ")");
            if (CrusadeConfig.SERVER.webMapTreasury.get()) row(h, lang, "crusadecraft.webmap.treasury", String.format("%,d", r.gold()).replace(',', ' '));
            row(h, lang, "crusadecraft.webmap.house", r.house());
            int citizens = 0;
            if (MineColoniesBridge.isReady()) {
                for (ColonyRef c : r.allColonies()) {
                    ColonyInfo info = MineColoniesBridge.info(c);
                    if (info != null) citizens += info.citizens();
                }
            }
            row(h, lang, "crusadecraft.webmap.population", String.valueOf(citizens));
        }
        long founded = ai.map(AiRealm::foundedDay).orElseGet(() -> human.get().foundedDay());
        if (founded > 0) row(h, lang, "crusadecraft.webmap.founded", text(Component.translatable("crusadecraft.letter.day", founded), lang));
        String lands = human.isPresent()
                ? text(Component.translatable("crusadecraft.webmap.lands.claimed", chunks, claimed, TerritoryService.claimCap(human.get())), lang)
                : text(Component.translatable("crusadecraft.webmap.lands", chunks), lang);
        row(h, lang, "crusadecraft.webmap.lands.label", lands);
        UUID id = ai.map(AiRealm::id).orElseGet(() -> human.get().id());
        data.diplomacy().suzerainOf(id).ifPresent(s -> row(h, lang, "crusadecraft.webmap.suzerain", realmLabel(data, s, lang)));
        List<UUID> vassals = data.diplomacy().vassalsOf(id);
        if (!vassals.isEmpty()) {
            StringBuilder v = new StringBuilder();
            for (UUID x : vassals) v.append(v.length() > 0 ? ", " : "").append(realmLabel(data, x, lang));
            row(h, lang, "crusadecraft.webmap.vassals", v.toString());
        }
        if (ai.isPresent() && CrusadeConfig.SERVER.webMapCouncil.get() && !ai.get().council().isEmpty()) {
            h.append("<br>");
            for (Map.Entry<Post, AiCharacter> c : ai.get().council().entrySet()) {
                row(h, lang, null, text(Component.translatable(c.getKey().key(c.getValue().female())), lang) + " : " + c.getValue().name());
            }
        }
        // La fiche que le joueur a écrite pour ce royaume, s'il y en a une.
        Lieu.Ref ref = ai.isPresent() ? Lieu.Ref.aiRealm(id) : Lieu.Ref.playerRealm(id);
        data.places().byRef(ref).filter(l -> !l.text().isBlank()).ifPresent(l -> h.append("<br><div style=\"max-width:320px\">")
                .append(esc(clip(l.text(), 600)).replace("\n", "<br>")).append("</div>"));
        return h.append("</div>").toString();
    }

    private static String realmLabel(CrusadeSavedData data, UUID id, String lang) {
        Optional<AiRealm> ai = data.world().realm(id);
        if (ai.isPresent()) return ServerText.render(AiNames.realm(ai.get().rank(), ai.get().place()), lang);
        return data.realm(id).map(Realm::name).orElse("?");
    }

    /** Les lieux en repères : villes, forts, lieux saints… ; une région en aplat léger. */
    private static void addPlaces(MinecraftServer server, CrusadeSavedData data, String lang, List<Point> points, List<Area> areas) {
        PlaceService.Resolver res = new PlaceService.Resolver(server);
        boolean showAi = CrusadeConfig.SERVER.webMapAiRealms.get();
        for (Lieu l : res.places().all()) {
            if (l.anchor() == null || l.dimension() == null || l.kind().title()) continue;
            ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, l.dimension()));
            if (level == null) continue;
            UUID holder = res.holderRealm(l);
            if (!showAi && holder != null && data.world().realm(holder).isPresent()) continue;
            String world = worldName(level);
            String name = ServerText.render(res.name(l), lang);
            StringBuilder h = new StringBuilder("<div class=\"crusade-fiche\" style=\"min-width:200px\">");
            h.append("<div style=\"font-size:15px;font-weight:bold\">").append(esc(name)).append("</div>");
            h.append("<div>").append(esc(text(Component.translatable(l.kind().key()), lang))).append("</div><br>");
            row(h, lang, "crusadecraft.lieu.field.holder", ServerText.render(res.holderName(l), lang));
            if (!l.text().isBlank()) h.append("<br><div style=\"max-width:320px\">").append(esc(clip(l.text(), 600)).replace("\n", "<br>")).append("</div>");
            h.append("</div>");
            String id = "lieu_" + l.id();
            if (l.kind() == LieuKind.REGION && l.radius() > 0) {
                double[][] c = WebMapGeometry.circle(l.anchor().getX() + 0.5, l.anchor().getZ() + 0.5, l.radius(), 32);
                int color = res.color(l) & 0xFFFFFF;
                areas.add(new Area(id, world, name, h.toString(), c[0], c[1], color, 0.12, color, 2));
            }
            points.add(new Point(id, world, name, h.toString(), l.anchor().getX() + 0.5, l.anchor().getY(), l.anchor().getZ() + 0.5, icon(l.kind())));
        }
    }

    private static ServerLevel level(MinecraftServer server, ResourceLocation dim) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
    }

    /**
     * La carte de guerre (P18), en couche à part : les zones de chaque royaume (rectangles de chunks et leur bord), ses
     * flèches d'attaque (trait et pointe), ses repères, et les sièges en cours. Elle est publique comme toute la carte
     * web : qui a l'adresse voit les plans.
     */
    private static void addWar(MinecraftServer server, CrusadeSavedData data, String lang, List<Area> areas, List<Line> lines, List<Point> points) {
        String title = text(Component.translatable("crusadecraft.warmap.title"), lang);
        List<Map.Entry<UUID, WarMap.Plan>> plans = new ArrayList<>(data.warMap().all().entrySet());
        plans.sort(Map.Entry.comparingByKey());
        for (Map.Entry<UUID, WarMap.Plan> e : plans) {
            UUID realm = e.getKey();
            Optional<AiRealm> ai = data.world().realm(realm);
            Optional<Realm> human = data.realm(realm);
            if (ai.isEmpty() && human.isEmpty()) continue;
            String realmName = ServerText.render(realmName(ai, human), lang);
            int realmColor = ai.map(AiRealm::color).orElseGet(() -> human.get().color()) & 0xFFFFFF;
            WarMap.Plan plan = e.getValue();
            List<ResourceLocation> dims = new ArrayList<>(plan.dimensions());
            dims.sort(Comparator.comparing(ResourceLocation::toString));
            for (ResourceLocation dim : dims) {
                ServerLevel level = level(server, dim);
                if (level == null) continue;
                String world = worldName(level);
                Long2ByteMap zones = plan.zones(dim);
                for (WarMap.Zone zone : WarMap.Zone.values()) {
                    LongOpenHashSet cells = new LongOpenHashSet();
                    for (var z : zones.long2ByteEntrySet()) if (z.getByteValue() == zone.ordinal()) cells.add(z.getLongKey());
                    if (cells.isEmpty()) continue;
                    String zoneName = text(Component.translatable("crusadecraft.warmap.zone." + zone.name().toLowerCase(Locale.ROOT)), lang);
                    String html = "<div class=\"crusade-fiche\"><div style=\"font-size:15px;font-weight:bold\">" + esc(title)
                            + "</div><div>" + esc(realmName) + "</div><br><b>" + esc(zoneName) + "</b> : "
                            + esc(text(Component.translatable("crusadecraft.webmap.lands", cells.size()), lang)) + "</div>";
                    String base = "guerre_" + realm + "_" + zone.name().toLowerCase(Locale.ROOT) + "_" + world;
                    int color = ZONE_COLORS[zone.ordinal()];
                    int i = 0;
                    for (WebMapGeometry.Rect r : WebMapGeometry.rectangles(cells)) {
                        double x0 = r.x0() * TerritoryMap.CELL, z0 = r.z0() * TerritoryMap.CELL;
                        double x1 = (r.x1() + 1) * TerritoryMap.CELL, z1 = (r.z1() + 1) * TerritoryMap.CELL;
                        areas.add(new Area(base + "_" + i++, world, realmName + " — " + zoneName, html,
                                new double[]{x0, x1, x1, x0}, new double[]{z0, z0, z1, z1}, color, 0.3, color, 0));
                    }
                    int j = 0;
                    for (WebMapGeometry.Segment s : WebMapGeometry.border(cells)) {
                        lines.add(new Line(base + "_bord_" + j++, world, realmName + " — " + zoneName, html,
                                new double[]{s.x0(), s.x1()}, new double[]{s.z0(), s.z1()}, color, 2));
                    }
                }
            }
            String arrowText = text(Component.translatable("crusadecraft.warmap.arrow"), lang);
            for (WarMap.Arrow a : plan.arrows()) {
                ServerLevel level = level(server, a.dim());
                if (level == null) continue;
                String world = worldName(level);
                String html = "<div class=\"crusade-fiche\"><b>" + esc(title) + "</b><br>" + esc(realmName) + "<br><br><b>" + esc(arrowText) + "</b></div>";
                lines.add(new Line("guerre_" + a.id(), world, realmName, html, new double[]{a.x0(), a.x1()}, new double[]{a.z0(), a.z1()}, realmColor, 5));
                for (double[] head : arrowHead(a.x0(), a.z0(), a.x1(), a.z1())) {
                    lines.add(new Line("guerre_" + a.id() + "_pointe" + (int) head[4], world, realmName, html,
                            new double[]{head[0], head[2]}, new double[]{head[1], head[3]}, realmColor, 5));
                }
            }
            for (WarMap.Pin p : plan.pins()) {
                ServerLevel level = level(server, p.dim());
                if (level == null) continue;
                String kind = text(Component.translatable("crusadecraft.warmap.mark." + p.kind().name().toLowerCase(Locale.ROOT)), lang);
                String html = "<div class=\"crusade-fiche\"><b>" + esc(title) + "</b><br>" + esc(realmName) + "<br><br><b>" + esc(kind) + "</b>"
                        + (p.note().isBlank() ? "" : "<br>" + esc(p.note())) + "</div>";
                points.add(new Point("guerre_" + p.id(), worldName(level), p.note().isBlank() ? kind : p.note(), html, p.x() + 0.5, 64, p.z() + 0.5,
                        pinIcon(p.kind())));
            }
        }
        // Les sièges en cours : là où une garnison tient encore.
        for (WarFieldService.SiegeView s : WarFieldService.sieges()) {
            ServerLevel level = level(server, s.dim());
            if (level == null) continue;
            String label = text(Component.translatable("crusadecraft.warmap.siege", realmLabel(data, s.defender(), lang), realmLabel(data, s.attacker(), lang)), lang);
            String html = "<div class=\"crusade-fiche\"><b>" + esc(label) + "</b><br>"
                    + esc(text(Component.translatable("crusadecraft.warmap.siege.garrison", s.garrison()), lang)) + "</div>";
            points.add(new Point("guerre_siege_" + s.x() + "_" + s.z(), worldName(level), label, html, s.x() + 0.5, 64, s.z() + 0.5, "fire"));
        }
    }

    /**
     * Les deux traits de la pointe d'une flèche, à 25° de part et d'autre, longs d'un sixième de la flèche (8 à 64 blocs) :
     * chacun {x0, z0, x1, z1, numéro}. Rien pour une flèche de moins d'un bloc.
     */
    static List<double[]> arrowHead(double x0, double z0, double x1, double z1) {
        double dx = x1 - x0, dz = z1 - z0, len = Math.hypot(dx, dz);
        if (len < 1) return List.of();
        double head = Math.max(8, Math.min(64, len / 6)), ang = Math.atan2(dz, dx);
        List<double[]> out = new ArrayList<>(2);
        for (int side = 0; side < 2; side++) {
            double t = ang + Math.PI + (side == 0 ? -1 : 1) * Math.toRadians(25);
            out.add(new double[]{x1, z1, x1 + Math.cos(t) * head, z1 + Math.sin(t) * head, side});
        }
        return out;
    }

    /** Une icône livrée avec Dynmap, pour chaque genre de repère de guerre. */
    static String pinIcon(WarMap.Mark kind) {
        return switch (kind) {
            case OBJECTIF -> "redflag";
            case RASSEMBLEMENT -> "blueflag";
            case DANGER -> "warning";
            case NOTE -> "sign";
        };
    }

    /** Une icône livrée avec Dynmap, selon le genre de lieu. */
    static String icon(LieuKind kind) {
        return switch (kind) {
            case VILLE -> "house";
            case VILLAGE -> "house";
            case AVANT_POSTE, FORT -> "tower";
            case LIEU_SAINT -> "temple";
            case RUINE -> "skull";
            case REGION -> "world";
            default -> "default";
        };
    }

    private static void row(StringBuilder h, String lang, String labelKey, String value) {
        if (value == null || value.isBlank()) return;
        if (labelKey != null) h.append("<b>").append(esc(text(Component.translatable(labelKey), lang))).append(" :</b> ");
        h.append(esc(value)).append("<br>");
    }

    private static String text(Component c, String lang) {
        return ServerText.render(c, lang);
    }

    private static String clip(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    /** Échappe le HTML : un nom ou une fiche ne doivent jamais devenir du code dans la page. */
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static int darker(int rgb) {
        int r = ((rgb >> 16) & 0xFF) * 6 / 10, g = ((rgb >> 8) & 0xFF) * 6 / 10, b = (rgb & 0xFF) * 6 / 10;
        return (r << 16) | (g << 8) | b;
    }
}
