package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.skin.SkinClient;
import fr.crusadecraft.client.skin.Wardrobe;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.CityGood;
import fr.crusadecraft.core.city.CitySettings;
import fr.crusadecraft.core.city.SkinService;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.world.AiPersonality;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CityPanelPackets;
import fr.crusadecraft.network.packet.NpcPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Le panneau d'une ville (P46) : tout ce qu'elle a de réglable. Il s'ouvre quand on la crée, et ensuite au bâton d'intendant
 * tourné vers le ciel. Le nom et ce qui y est permis, son camp (indépendante, un royaume, un royaume qu'on fonde), ses
 * bâtiments, ses gens, l'allure qu'ils peuvent prendre, et ses réserves.
 */
public class CityPanelScreen extends Screen {
    private static final int W = 440, H = 250, ROW = 18;

    private enum Tab { LIVE, GENERAL, CAMP, BUILDINGS, PEOPLE, LOOKS, STOCK }

    /** On revient là où l'on était, d'une ouverture à l'autre. */
    private static Tab tab = Tab.GENERAL;
    /** Toutes les tant de ticks, on redemande ce qui se passe. */
    private static final int LIVE_EVERY = 40;
    /** Ce qui se passe à l'instant, tel que le serveur vient de le dire. */
    private CompoundTag live = new CompoundTag();
    private int liveIn, liveScroll;
    private final List<UUID> shownPawns = new ArrayList<>();

    private CompoundTag view;
    private final UUID site;
    private int left, top, scroll;
    private String nameDraft, maxDraft, menDraft, womenDraft, familiesDraft, historyDraft, realmNameDraft = "", rumorDraft = "", filterDraft = "", importDraft = "";
    private int realmIndex, rankIndex = TitleRank.ROYAUME.ordinal(), colorIndex, personalityIndex = AiPersonality.OPPORTUNISTE.ordinal();
    private boolean mine = true;
    /** Apparence : le métier dont on règle les tenues (−1 : toute la ville) ; ce qu'on parcourt ; où on en est. */
    private int poolJob = -1, gridSource, gridPage, gridJob = -1;
    private Component status = Component.empty();

    private static final int[] COLORS = {0x8C3B3B, 0xB0413A, 0xC77E2E, 0xC9A55C, 0x6E9E4A, 0x2F7F7A, 0x3B6C8C, 0x3D4F9E, 0x6B4F8C, 0x8E4585, 0x7A5C3E, 0x4A4A4A};

    public CityPanelScreen(CompoundTag view) {
        super(Component.literal(view.getString("name")));
        this.view = view;
        this.site = view.getUUID("site");
        readDrafts();
    }

    /** Ouvre le panneau, ou y montre ce qui vient de changer. Une vue vide : il n'a plus le droit, on ferme. */
    public static void open(CompoundTag view) {
        Minecraft mc = Minecraft.getInstance();
        if (!view.hasUUID("site")) {
            if (mc.screen instanceof CityPanelScreen) mc.setScreen(null);
            return;
        }
        if (mc.screen instanceof CityPanelScreen s && s.site.equals(view.getUUID("site"))) {
            s.view = view;
            s.readDrafts();
            s.rebuildWidgets();
            return;
        }
        mc.setScreen(new CityPanelScreen(view));
    }

    /** P50 : les dernières nouvelles de la ville. */
    public static void onLive(CompoundTag view) {
        if (!(Minecraft.getInstance().screen instanceof CityPanelScreen s) || !view.hasUUID("site") || !s.site.equals(view.getUUID("site"))) return;
        s.live = view;
        // Les boutons « Voir » suivent ceux qui sont sur les routes.
        List<UUID> pawns = new ArrayList<>();
        ListTag list = view.getList("pawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) pawns.add(list.getCompound(i).getUUID("id"));
        if (!pawns.equals(s.shownPawns)) {
            s.shownPawns.clear();
            s.shownPawns.addAll(pawns);
            if (tab == Tab.LIVE) s.rebuildWidgets();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (tab != Tab.LIVE || --liveIn > 0) return;
        liveIn = LIVE_EVERY;
        CrusadeNetwork.toServer(new CityPanelPackets.AskLive(site));
    }

    private void readDrafts() {
        CitySettings s = CitySettings.load(view.getCompound("settings"));
        nameDraft = view.getString("name");
        maxDraft = s.peopleMax > 0 ? Integer.toString(s.peopleMax) : "";
        menDraft = String.join(", ", s.men);
        womenDraft = String.join(", ", s.women);
        familiesDraft = String.join(", ", s.families);
        historyDraft = view.getString("history");
    }

    private CitySettings settings() {
        return CitySettings.load(view.getCompound("settings"));
    }

    private void send(Consumer<CompoundTag> fill) {
        CompoundTag t = new CompoundTag();
        fill.accept(t);
        CrusadeNetwork.toServer(new CityPanelPackets.Edit(site, t));
    }

    // ------------------------------------------------------------------ construire

    private int x() {
        return left + 12;
    }

    private int y0() {
        return top + 42;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        Tab[] all = Tab.values();
        int tw = (W - 24 - (all.length - 1) * 2) / all.length;
        for (int i = 0; i < all.length; i++) {
            Tab t = all[i];
            Button b = Button.builder(Component.translatable("crusadecraft.city.panel.tab." + t.name().toLowerCase(Locale.ROOT)), x -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(left + 12 + i * (tw + 2), top + 24, tw, 14).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + W - 66, top + H - 22, 54, 16).build());
        switch (tab) {
            case LIVE -> initLive();
            case GENERAL -> initGeneral();
            case CAMP -> initCamp();
            case BUILDINGS -> initBuildings();
            case PEOPLE -> initPeople();
            case LOOKS -> initLooks();
            case STOCK -> initStock();
        }
    }

    private static final int LIVE_W = 150, LIVE_ROW = 36, LIVE_ROWS = 4;

    private void initLive() {
        liveIn = 0;
        // De quoi aller voir, pour qui a la main sur le monde.
        if (!live.getBoolean("canGo")) return;
        ListTag pawns = live.getList("pawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < pawns.size() && i < LIVE_ROWS; i++) {
            UUID id = pawns.getCompound(i).getUUID("id");
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.city.panel.live.go"), b -> {
                send(t -> t.putUUID("goSee", id));
                onClose();
            }).bounds(left + W - 12 - 32, y0() + 13 + i * LIVE_ROW, 32, 14).build());
        }
    }

    private EditBox box(int x, int y, int w, String value, int max, Consumer<String> onChange) {
        EditBox e = new EditBox(font, x, y, w, 14, Component.empty());
        e.setMaxLength(max);
        e.setValue(value == null ? "" : value);
        e.setResponder(onChange);
        addRenderableWidget(e);
        return e;
    }

    private Button button(String key, int x, int y, int w, Button.OnPress press) {
        Button b = Button.builder(Component.translatable(key), press).bounds(x, y, w, 14).build();
        addRenderableWidget(b);
        return b;
    }

    private void toggle(String key, boolean on, int x, int y, int w, Consumer<Boolean> change) {
        Component label = Component.translatable(key).append(" : ").append(Component.translatable(on ? "crusadecraft.city.panel.yes" : "crusadecraft.city.panel.no"));
        addRenderableWidget(Button.builder(label, b -> change.accept(!on)).bounds(x, y, w, 14)
                .tooltip(Tooltip.create(Component.translatable(key + ".tip"))).build());
    }

    private void initGeneral() {
        CitySettings s = settings();
        int y = y0();
        box(x() + 96, y, 170, nameDraft, 32, v -> nameDraft = v);
        button("crusadecraft.city.panel.rename", x() + 270, y, 80, b -> send(t -> t.putString("rename", nameDraft)));
        y += 20;
        box(x() + 96, y, 40, maxDraft, 3, v -> maxDraft = v);
        button("crusadecraft.city.panel.apply", x() + 140, y, 60, b -> send(t -> t.putInt("peopleMax", parse(maxDraft, 0))));
        y += 22;
        int w = (W - 24 - 6) / 4;
        toggle("crusadecraft.city.panel.marry", s.canMarry, x(), y, w, v -> send(t -> t.putBoolean("canMarry", v)));
        toggle("crusadecraft.city.panel.travel", s.canTravel, x() + w + 2, y, w, v -> send(t -> t.putBoolean("canTravel", v)));
        toggle("crusadecraft.city.panel.leave", s.canLeave, x() + 2 * (w + 2), y, w, v -> send(t -> t.putBoolean("canLeave", v)));
        toggle("crusadecraft.city.panel.speak", s.canSpeak, x() + 3 * (w + 2), y, w, v -> send(t -> t.putBoolean("canSpeak", v)));
        y += 34;
        box(x() + 96, y, W - 24 - 96, menDraft, 2000, v -> menDraft = v);
        y += 17;
        box(x() + 96, y, W - 24 - 96, womenDraft, 2000, v -> womenDraft = v);
        y += 17;
        box(x() + 96, y, W - 24 - 96, familiesDraft, 2000, v -> familiesDraft = v);
        y += 18;
        button("crusadecraft.city.panel.names.save", x() + 96, y, 150, b -> send(t -> {
            t.putString("men", menDraft);
            t.putString("women", womenDraft);
            t.putString("families", familiesDraft);
        }));
    }

    private void initCamp() {
        int y = y0() + 14;
        button("crusadecraft.city.panel.independent.go", x(), y, 170, b -> send(t -> t.putBoolean("independent", true)));
        if (view.hasUUID("nationRealm")) {
            UUID nation = view.getUUID("nationRealm");
            button("crusadecraft.nation.open", x() + 174, y, 110, b -> CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.NationPackets.Request(nation)));
        }
        y += 22;
        ListTag realms = view.getList("realms", Tag.TAG_COMPOUND);
        if (!realms.isEmpty()) {
            realmIndex = Mth.clamp(realmIndex, 0, realms.size() - 1);
            CompoundTag r = realms.getCompound(realmIndex);
            button("crusadecraft.city.panel.prev", x(), y, 14, b -> {
                realmIndex = Math.floorMod(realmIndex - 1, realms.size());
                rebuildWidgets();
            });
            Button name = Button.builder(Component.literal(r.getString("name")), b -> {
            }).bounds(x() + 16, y, 196, 14).build();
            name.active = false;
            addRenderableWidget(name);
            button("crusadecraft.city.panel.next", x() + 214, y, 14, b -> {
                realmIndex = Math.floorMod(realmIndex + 1, realms.size());
                rebuildWidgets();
            });
            button("crusadecraft.city.panel.join", x() + 232, y, 90, b -> send(t -> t.putUUID("join", r.getUUID("id"))));
        }
        y += 22;
        box(x(), y + 12, 130, realmNameDraft, 32, v -> realmNameDraft = v);
        TitleRank rank = TitleRank.values()[rankIndex];
        addRenderableWidget(Button.builder(Component.translatable(rank.titleKey()), b -> {
            rankIndex = (rankIndex + 1) % TitleRank.values().length;
            rebuildWidgets();
        }).bounds(x() + 134, y + 12, 64, 14).build());
        addRenderableWidget(Button.builder(Component.literal("█").withStyle(s -> s.withColor(COLORS[colorIndex])), b -> {
            colorIndex = (colorIndex + 1) % COLORS.length;
            rebuildWidgets();
        }).bounds(x() + 200, y + 12, 18, 14).build());
        AiPersonality p = AiPersonality.values()[personalityIndex];
        addRenderableWidget(Button.builder(Component.translatable(p.key()), b -> {
            personalityIndex = (personalityIndex + 1) % AiPersonality.values().length;
            rebuildWidgets();
        }).bounds(x() + 220, y + 12, 82, 14).build());
        addRenderableWidget(Button.builder(Component.translatable(mine ? "crusadecraft.city.panel.realm.mine" : "crusadecraft.city.panel.realm.computer"), b -> {
            mine = !mine;
            rebuildWidgets();
        }).bounds(x() + 304, y + 12, 58, 14).tooltip(Tooltip.create(Component.translatable("crusadecraft.city.panel.realm.who.tip"))).build());
        button("crusadecraft.city.panel.realm.found", x() + 364, y + 12, 52, b -> send(t -> {
            CompoundTag r = new CompoundTag();
            r.putString("name", realmNameDraft);
            r.putString("rank", TitleRank.values()[rankIndex].name());
            r.putInt("color", COLORS[colorIndex]);
            r.putString("personality", AiPersonality.values()[personalityIndex].name());
            r.putBoolean("mine", mine);
            t.put("newRealm", r);
        }));
        y += 32;
        if (!view.getString("nation").isEmpty()) {
            MultiLineEditBox history = new MultiLineEditBox(font, x(), y + 12, W - 24, H - (y - top) - 12 - 48, Component.empty(), Component.empty());
            history.setCharacterLimit(4000);
            history.setValue(historyDraft);
            history.setValueListener(v -> historyDraft = v);
            addRenderableWidget(history);
            button("crusadecraft.city.panel.history.save", x(), top + H - 22, 150, b -> send(t -> t.putString("history", historyDraft)));
        }
    }

    private void initBuildings() {
        ListTag list = view.getList("buildings", Tag.TAG_COMPOUND);
        int rows = (H - 42 - 28) / ROW;
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        BuildingFunction[] all = BuildingFunction.values();
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag b = list.getCompound(i);
            int y = y0() + (i - scroll) * ROW;
            int current = b.getInt("function");
            UUID id = b.getUUID("id");
            button("crusadecraft.city.panel.prev", x() + 160, y, 14, x -> function(id, current <= -1 ? all.length - 1 : current - 1));
            Component label = current >= 0 ? Component.translatable(all[current].key())
                    : b.getInt("suggestion") >= 0 ? Component.translatable("crusadecraft.city.panel.suggested", Component.translatable(all[b.getInt("suggestion")].key()))
                    : Component.translatable("crusadecraft.city.panel.no_function");
            Button f = Button.builder(label, x -> {
                if (current < 0 && b.getInt("suggestion") >= 0) function(id, b.getInt("suggestion"));
            }).bounds(x() + 176, y, 136, 14).build();
            f.active = current < 0 && b.getInt("suggestion") >= 0;
            addRenderableWidget(f);
            button("crusadecraft.city.panel.next", x() + 314, y, 14, x -> function(id, current + 1 >= all.length ? -1 : current + 1));
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.city.panel.remove"), x -> send(t -> {
                t.putUUID("building", id);
                t.putBoolean("forget", true);
            })).bounds(x() + W - 24 - 14, y, 14, 14).tooltip(Tooltip.create(Component.translatable("crusadecraft.city.panel.forget.tip"))).build());
        }
        button("crusadecraft.city.panel.recognize", x(), top + H - 22, 130, b -> send(t -> t.putBoolean("recognize", true)));
        button("crusadecraft.city.panel.accept_all", x() + 134, top + H - 22, 130, b -> send(t -> t.putBoolean("acceptAll", true)));
    }

    private void function(UUID building, int f) {
        send(t -> {
            t.putUUID("building", building);
            t.putInt("function", f);
        });
    }

    private List<CompoundTag> people() {
        List<CompoundTag> out = new ArrayList<>();
        String needle = filterDraft.strip().toLowerCase(Locale.ROOT);
        ListTag list = view.getList("residents", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);
            if (needle.isEmpty() || p.getString("name").toLowerCase(Locale.ROOT).contains(needle) || p.getString("job").toLowerCase(Locale.ROOT).contains(needle)) out.add(p);
        }
        return out;
    }

    private void initPeople() {
        EditBox filter = box(x() + 96, y0(), 180, filterDraft, 32, v -> {
            filterDraft = v;
            scroll = 0;
        });
        filter.setHint(Component.translatable("crusadecraft.city.panel.people.filter"));
        List<CompoundTag> list = people();
        int rows = (H - 42 - 18 - 28) / ROW;
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag p = list.get(i);
            UUID id = p.getUUID("id");
            button("crusadecraft.city.panel.people.edit", x() + W - 24 - 70, y0() + 18 + (i - scroll) * ROW, 70, b -> CrusadeNetwork.toServer(new NpcPackets.Request(id)));
        }
    }

    /** La clé des réserves pour le métier choisi. */
    private String poolKey() {
        return poolJob < 0 ? CitySettings.ALL : BuildingFunction.values()[poolJob].name();
    }

    private List<String> pool() {
        return settings().pools.getOrDefault(poolKey(), List.of());
    }

    /** Ce qu'on parcourt : les skins importés, ou les tenues de Comes Alive d'un sexe et d'un dossier de métier. */
    private List<String> gridItems() {
        if (gridSource == 0) {
            List<String> out = new ArrayList<>();
            for (SkinService.Entry e : SkinClient.library()) out.add(SkinService.PREFIX + e.hash());
            return out;
        }
        List<String> jobs = Wardrobe.jobs();
        String job = gridJob < 0 || gridJob >= jobs.size() ? null : jobs.get(gridJob);
        return Wardrobe.filter(gridSource == 2, job);
    }

    private static final int GRID_COLS = 11, GRID_ROWS = 2, CELL_W = 22, CELL_H = 38;

    private int gridX() {
        return x();
    }

    private int gridY() {
        return top + 112;
    }

    private void initLooks() {
        SkinClient.askLibrary();
        BuildingFunction[] all = BuildingFunction.values();
        int y = y0();
        button("crusadecraft.city.panel.prev", x() + 70, y, 14, b -> {
            poolJob = poolJob <= -1 ? all.length - 1 : poolJob - 1;
            rebuildWidgets();
        });
        Button who = Button.builder(poolJob < 0 ? Component.translatable("crusadecraft.city.panel.looks.everyone") : Component.translatable(all[poolJob].key()), b -> {
        }).bounds(x() + 86, y, 110, 14).build();
        who.active = false;
        addRenderableWidget(who);
        button("crusadecraft.city.panel.next", x() + 198, y, 14, b -> {
            poolJob = poolJob + 1 >= all.length ? -1 : poolJob + 1;
            rebuildWidgets();
        });
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.city.panel.looks.apply"), b -> send(t -> t.putBoolean("applyPools", true))).bounds(x() + 216, y, 126, 14)
                .tooltip(Tooltip.create(Component.translatable("crusadecraft.city.panel.looks.apply.tip"))).build());
        // Ce qu'on parcourt, en dessous.
        int sy = gridY() - 16;
        String[] sources = {"crusadecraft.city.panel.looks.skins", "crusadecraft.city.panel.looks.men", "crusadecraft.city.panel.looks.women"};
        for (int i = 0; i < 3; i++) {
            int s = i;
            Button b = Button.builder(Component.translatable(sources[i]), x -> {
                gridSource = s;
                gridPage = 0;
                rebuildWidgets();
            }).bounds(x() + i * 96, sy, 94, 14).build();
            b.active = gridSource != i;
            addRenderableWidget(b);
        }
        if (gridSource != 0) {
            List<String> jobs = Wardrobe.jobs();
            String label = gridJob < 0 || gridJob >= jobs.size() ? Component.translatable("crusadecraft.city.panel.looks.all_jobs").getString() : jobs.get(gridJob);
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                gridJob = gridJob + 1 >= jobs.size() ? -1 : gridJob + 1;
                gridPage = 0;
                rebuildWidgets();
            }).bounds(x() + 288, sy, 84, 14).build());
        }
        // Importer un skin.
        int iy = top + H - 42;
        EditBox importing = box(x(), iy, 270, importDraft, 300, v -> importDraft = v);
        importing.setHint(Component.translatable("crusadecraft.skin.import.hint"));
        button("crusadecraft.skin.import", x() + 274, iy, 66, b -> {
            status = Component.translatable("crusadecraft.skin.import.running");
            SkinClient.importSkin(importDraft, null, c -> status = c);
        });
        button("crusadecraft.skin.folder", x() + 342, iy, 74, b -> net.minecraft.Util.getPlatform().openFile(SkinClient.localFolder().toFile()));
    }

    private void initStock() {
        CityGood[] goods = CityGood.values();
        for (int i = 0; i < goods.length; i++) {
            CityGood g = goods[i];
            int col = i % 2, y = y0() + (i / 2) * ROW, cx = x() + col * 212;
            addRenderableWidget(Button.builder(Component.literal("−8"), b -> stock(g, -8)).bounds(cx + 130, y, 24, 14).build());
            addRenderableWidget(Button.builder(Component.literal("+8"), b -> stock(g, 8)).bounds(cx + 156, y, 24, 14).build());
        }
        int py = y0() + 5 * ROW;
        addRenderableWidget(Button.builder(Component.literal("−50"), b -> send(t -> t.putLong("purse", -50))).bounds(x() + 130, py, 24, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+50"), b -> send(t -> t.putLong("purse", 50))).bounds(x() + 156, py, 24, 14).build());
        int ry = py + 20;
        EditBox rumor = box(x(), ry, 330, rumorDraft, 200, v -> rumorDraft = v);
        rumor.setHint(Component.translatable("crusadecraft.city.panel.rumor.hint"));
        button("crusadecraft.city.panel.rumor.add", x() + 334, ry, 82, b -> {
            String text = rumorDraft;
            rumorDraft = "";
            send(t -> t.putString("rumorAdd", text));
        });
        ListTag rumors = view.getList("rumors", Tag.TAG_STRING);
        for (int i = 0; i < rumors.size() && i < 3; i++) {
            int at = i;
            button("crusadecraft.city.panel.remove", x(), ry + 18 + i * 14, 14, b -> send(t -> t.putInt("rumorRemove", at)));
        }
    }

    private void stock(CityGood g, int delta) {
        send(t -> {
            t.putString("good", g.name());
            t.putInt("delta", delta);
        });
    }

    private static int parse(String s, int fallback) {
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ------------------------------------------------------------------ dessiner

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, W, H);
        Ui.scaled(g, font, Component.literal(view.getString("name")), left + 12, top + 8, 1.25f, Ui.TEXT_TITLE, false);
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.people.count", view.getInt("people"), view.getInt("max")), left + W - 112, top + 10, 100, Ui.TEXT_MUTED);
        switch (tab) {
            case LIVE -> renderLive(g);
            case GENERAL -> renderGeneral(g);
            case CAMP -> renderCamp(g);
            case BUILDINGS -> renderBuildings(g);
            case PEOPLE -> renderPeople(g);
            case LOOKS -> renderLooks(g, mx, my);
            case STOCK -> renderStock(g);
        }
        super.render(g, mx, my, partial);
    }

    private void renderLive(GuiGraphics g) {
        int y = y0(), jw = W - 24 - LIVE_W - 8, jh = H - 42 - 46;
        Ui.inset(g, x(), y, jw, jh);
        ListTag journal = live.getList("journal", Tag.TAG_COMPOUND);
        if (journal.isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.city.panel.live.empty"), x() + 6, y + 6, jw - 12, Ui.TEXT_MUTED);
        int rows = (jh - 4) / 11;
        liveScroll = Mth.clamp(liveScroll, 0, Math.max(0, journal.size() - rows));
        for (int i = liveScroll; i < journal.size() && i < liveScroll + rows; i++) {
            CompoundTag l = journal.getCompound(i);
            int ly = y + 3 + (i - liveScroll) * 11;
            int color = switch (l.getString("kind")) {
                case "SALE" -> Ui.TEXT;
                case "ROAD" -> Ui.TEXT_TITLE;
                case "LIFE" -> Ui.GREEN;
                default -> Ui.TEXT_MUTED;
            };
            Ui.fitted(g, font, Component.literal(l.getString("time")), x() + 4, ly, 30, Ui.GOLD_DARK);
            Ui.fitted(g, font, Component.literal(l.getString("text")), x() + 36, ly, jw - 40, color);
        }
        // Ceux qui sont sur les routes.
        int rx = x() + jw + 8;
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.live.roads"), rx, y, LIVE_W, Ui.GOLD);
        ListTag pawns = live.getList("pawns", Tag.TAG_COMPOUND);
        if (pawns.isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.city.panel.live.roads.empty"), rx, y + 13, LIVE_W, Ui.TEXT_MUTED);
        boolean go = live.getBoolean("canGo");
        for (int i = 0; i < pawns.size() && i < LIVE_ROWS; i++) {
            CompoundTag p = pawns.getCompound(i);
            int py = y + 12 + i * LIVE_ROW, tw = go ? LIVE_W - 34 : LIVE_W;
            Ui.fitted(g, font, Component.literal(p.getString("name")), rx, py, tw, p.getBoolean("caravan") ? Ui.TEXT_TITLE : Ui.TEXT);
            String stage = p.getString("stage");
            Component where = stage.equals("STAY") ? Component.translatable("crusadecraft.city.panel.live.staying", p.getString("to")) : Component.translatable("crusadecraft.city.panel.live.towards", p.getString("to"));
            Ui.fitted(g, font, where, rx, py + 10, tw, Ui.TEXT_MUTED);
            Ui.bar(g, rx, py + 20, tw, 3, p.getInt("percent") / 100.0, p.getBoolean("caravan") ? Ui.GOLD : Ui.TEXT_MUTED);
            if (!p.getString("cargo").isEmpty()) Ui.fitted(g, font, Component.literal(p.getString("cargo")), rx, py + 24, tw, Ui.GOLD_DARK);
        }
        Ui.wrapped(g, font, Component.literal(live.getString("summary")), x(), top + H - 44, W - 90, Ui.TEXT_MUTED);
    }

    private void label(GuiGraphics g, String key, int x, int y) {
        Ui.fitted(g, font, Component.translatable(key), x, y + 3, 92, Ui.TEXT_MUTED);
    }

    private void renderGeneral(GuiGraphics g) {
        int y = y0();
        label(g, "crusadecraft.city.panel.name", x(), y);
        y += 20;
        label(g, "crusadecraft.city.panel.people_max", x(), y);
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.people_max.hint", view.getInt("cap")), x() + 206, y + 3, 210, Ui.TEXT_MUTED);
        y += 42;
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.names"), x(), y, W - 24, Ui.GOLD);
        y += 14;
        label(g, "crusadecraft.city.panel.names.men", x(), y);
        y += 17;
        label(g, "crusadecraft.city.panel.names.women", x(), y);
        y += 17;
        label(g, "crusadecraft.city.panel.names.families", x(), y);
        Ui.wrapped(g, font, Component.literal(view.getString("summary")), x(), top + H - 22 - 22, W - 90, Ui.TEXT_MUTED);
    }

    private void renderCamp(GuiGraphics g) {
        int y = y0();
        String realm = view.getString("realm");
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.camp", realm.isEmpty() ? "—" : realm), x(), y, W - 24, Ui.TEXT_TITLE);
        y += 58;
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.realm.new"), x(), y, W - 24, Ui.GOLD);
        y += 32;
        if (!view.getString("nation").isEmpty()) Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.history"), x(), y, W - 24, Ui.GOLD);
        else Ui.wrapped(g, font, Component.translatable("crusadecraft.city.panel.history.none"), x(), y, W - 24, Ui.TEXT_MUTED);
    }

    private void renderBuildings(GuiGraphics g) {
        ListTag list = view.getList("buildings", Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.city.panel.buildings.empty"), x(), y0(), W - 24, Ui.TEXT_MUTED);
            return;
        }
        int rows = (H - 42 - 28) / ROW;
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag b = list.getCompound(i);
            int y = y0() + (i - scroll) * ROW;
            String name = b.getString("name");
            int f = b.getInt("function");
            Component shown = !name.isEmpty() ? Component.literal(name) : f >= 0 ? Component.translatable(BuildingFunction.values()[f].key()) : Component.translatable("crusadecraft.city.panel.building.unnamed");
            Ui.fitted(g, font, shown, x(), y + 3, 156, Ui.TEXT);
            Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.building.people", b.getInt("people")), x() + 332, y + 3, 66, Ui.TEXT_MUTED);
        }
    }

    private void renderPeople(GuiGraphics g) {
        label(g, "crusadecraft.city.panel.people.search", x(), y0());
        List<CompoundTag> list = people();
        int rows = (H - 42 - 18 - 28) / ROW;
        if (list.isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.city.panel.people.empty"), x(), y0() + 20, W - 24, Ui.TEXT_MUTED);
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag p = list.get(i);
            int y = y0() + 18 + (i - scroll) * ROW;
            String look = p.getString("look");
            Ui.fitted(g, font, Component.literal(p.getString("name")), x(), y + 3, 170, p.getBoolean("notable") ? Ui.TEXT_TITLE : Ui.TEXT);
            Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.people.row", p.getString("job"), p.getInt("age")), x() + 174, y + 3, 110, Ui.TEXT_MUTED);
            if (p.getBoolean("away")) Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.people.away"), x() + 288, y + 3, 56, Ui.GOLD);
            else if (!look.isEmpty()) Ui.fitted(g, font, Component.literal(Wardrobe.label(look)), x() + 288, y + 3, 56, Ui.TEXT_MUTED);
        }
    }

    private void renderLooks(GuiGraphics g, int mx, int my) {
        int y = y0();
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.looks.for"), x(), y + 3, 68, Ui.TEXT_MUTED);
        // Ce qu'ils peuvent déjà porter : un clic le retire.
        List<String> pool = pool();
        int py = y + 18;
        Ui.inset(g, x(), py, W - 24, 36);
        if (pool.isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.city.panel.looks.pool.empty"), x() + 6, py + 6, W - 36, Ui.TEXT_MUTED);
        }
        for (int i = 0; i < pool.size() && i < 26; i++) {
            int cx = x() + 3 + i * 16;
            Wardrobe.doll(g, pool.get(i), cx, py + 2, 1);
            if (Ui.inside(mx, my, cx, py + 2, 16, 32)) {
                g.renderTooltip(font, List.of(Component.literal(Wardrobe.label(pool.get(i))), Component.translatable("crusadecraft.city.panel.looks.remove")), java.util.Optional.empty(), mx, my);
            }
        }
        // Ce qu'on peut leur donner : un clic l'ajoute.
        List<String> items = gridItems();
        int perPage = GRID_COLS * GRID_ROWS;
        int pages = Math.max(1, (items.size() + perPage - 1) / perPage);
        gridPage = Mth.clamp(gridPage, 0, pages - 1);
        Ui.inset(g, gridX(), gridY(), W - 24, CELL_H * GRID_ROWS + 4);
        if (items.isEmpty()) {
            Ui.wrapped(g, font, Component.translatable(gridSource == 0 ? "crusadecraft.city.panel.looks.no_skins" : "crusadecraft.city.panel.looks.no_clothes"), gridX() + 8, gridY() + 8, W - 40, Ui.TEXT_MUTED);
        }
        for (int i = gridPage * perPage; i < items.size() && i < (gridPage + 1) * perPage; i++) {
            int k = i - gridPage * perPage;
            int cx = gridX() + 6 + (k % GRID_COLS) * (CELL_W + 16), cy = gridY() + 4 + (k / GRID_COLS) * CELL_H;
            boolean hover = Ui.inside(mx, my, cx - 2, cy, 20, 36);
            boolean chosen = pool.contains(items.get(i));
            if (chosen) g.fill(cx - 2, cy, cx + 18, cy + 36, Ui.GOLD_DARK);
            else if (hover) g.fill(cx - 2, cy, cx + 18, cy + 36, Ui.PANEL_HIGHLIGHT);
            Wardrobe.doll(g, items.get(i), cx, cy + 2, 1);
        }
        for (int i = gridPage * perPage; i < items.size() && i < (gridPage + 1) * perPage; i++) {
            int k = i - gridPage * perPage;
            int cx = gridX() + 6 + (k % GRID_COLS) * (CELL_W + 16), cy = gridY() + 4 + (k / GRID_COLS) * CELL_H;
            if (Ui.inside(mx, my, cx - 2, cy, 20, 36)) g.renderTooltip(font, Component.literal(Wardrobe.label(items.get(i))), mx, my);
        }
        if (pages > 1) Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.looks.page", gridPage + 1, pages), x() + 376, gridY() - 12, 40, Ui.TEXT_MUTED);
        Ui.fitted(g, font, status, x(), top + H - 22 + 3, W - 90, Ui.GOLD);
    }

    private void renderStock(GuiGraphics g) {
        CompoundTag stock = view.getCompound("stock");
        CityGood[] goods = CityGood.values();
        for (int i = 0; i < goods.length; i++) {
            CityGood good = goods[i];
            int col = i % 2, y = y0() + (i / 2) * ROW, cx = x() + col * 212;
            Ui.fitted(g, font, Component.translatable(good.key()), cx, y + 3, 80, Ui.TEXT);
            Ui.fitted(g, font, Component.literal(Integer.toString(stock.getInt(good.name()))), cx + 88, y + 3, 40, stock.getInt(good.name()) < 16 ? Ui.RED : Ui.TEXT_TITLE);
        }
        int py = y0() + 5 * ROW;
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.purse"), x(), py + 3, 80, Ui.TEXT);
        Ui.fitted(g, font, Component.literal(Long.toString(view.getLong("purse"))), x() + 88, py + 3, 40, Ui.TEXT_TITLE);
        ListTag orders = view.getList("orders", Tag.TAG_COMPOUND);
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.orders", orders.size()), x() + 212, py + 3, 200, orders.isEmpty() ? Ui.TEXT_MUTED : Ui.GOLD);
        ListTag rumors = view.getList("rumors", Tag.TAG_STRING);
        int ry = py + 20;
        for (int i = 0; i < rumors.size() && i < 3; i++) Ui.fitted(g, font, Component.literal(rumors.getString(i)), x() + 18, ry + 18 + i * 14 + 3, W - 24 - 18, Ui.TEXT_MUTED);
    }

    // ------------------------------------------------------------------ gestes

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == Tab.LOOKS) {
            int py = y0() + 18;
            List<String> pool = pool();
            for (int i = 0; i < pool.size() && i < 26; i++) {
                int cx = x() + 3 + i * 16;
                if (Ui.inside(mx, my, cx, py + 2, 16, 32)) {
                    String look = pool.get(i);
                    send(t -> {
                        t.putString("poolRemove", look);
                        t.putString("poolJob", poolJob < 0 ? "" : BuildingFunction.values()[poolJob].name());
                    });
                    return true;
                }
            }
            List<String> items = gridItems();
            int perPage = GRID_COLS * GRID_ROWS;
            for (int i = gridPage * perPage; i < items.size() && i < (gridPage + 1) * perPage; i++) {
                int k = i - gridPage * perPage;
                int cx = gridX() + 6 + (k % GRID_COLS) * (CELL_W + 16), cy = gridY() + 4 + (k / GRID_COLS) * CELL_H;
                if (Ui.inside(mx, my, cx - 2, cy, 20, 36)) {
                    String look = items.get(i);
                    send(t -> {
                        t.putString(pool.contains(look) ? "poolRemove" : "poolAdd", look);
                        t.putString("poolJob", poolJob < 0 ? "" : BuildingFunction.values()[poolJob].name());
                    });
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (super.mouseScrolled(mx, my, delta)) return true;
        if (tab == Tab.LOOKS) {
            gridPage = Math.max(0, gridPage - (int) Math.signum(delta));
            return true;
        }
        if (tab == Tab.LIVE) {
            liveScroll = Math.max(0, liveScroll - (int) Math.signum(delta));
            return true;
        }
        if (tab == Tab.BUILDINGS || tab == Tab.PEOPLE) {
            scroll = Math.max(0, scroll - (int) Math.signum(delta));
            rebuildWidgets();
            return true;
        }
        return false;
    }

    /** Pour le banc d'essai : le centre de cette figure de la grille d'allures. */
    public int[] gridCellForShots(int k) {
        return new int[]{gridX() + 6 + (k % GRID_COLS) * (CELL_W + 16) + 8, gridY() + 4 + (k / GRID_COLS) * CELL_H + 18};
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
