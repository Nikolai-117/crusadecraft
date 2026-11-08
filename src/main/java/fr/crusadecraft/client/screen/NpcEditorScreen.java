package fr.crusadecraft.client.screen;

import fr.crusadecraft.client.skin.SkinClient;
import fr.crusadecraft.client.skin.Wardrobe;
import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.city.BuildingFunction;
import fr.crusadecraft.core.city.NpcSettings;
import fr.crusadecraft.core.city.SkinService;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.NpcPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * L'éditeur d'un habitant (P47), façon mod de PNJ : sa fiche, son allure, ses statistiques, sa conduite, son métier, ses
 * répliques, ses échanges. Le villageois change sous vos yeux. Tout reste enregistré sur sa personne, et revient s'il réapparaît.
 */
public class NpcEditorScreen extends Screen {
    private static final int W = 440, H = 250;

    private enum Tab { SHEET, LOOK, STATS, CONDUCT, JOB, LINES, TRADES }

    private static Tab tab = Tab.SHEET;
    /** Les moments pour lesquels on peut écrire. */
    private static final String[] MOMENTS = {NpcSettings.ANY, "ACCUEIL", "TRAVAIL", "METIER", "VILLE", "ROYAUME", "RUMEUR", "FAMILLE", "AVIS", "ADIEU", "MATIN", "MIDI", "SOIR", "NUIT",
            "PLUIE", "ORAGE", "DOULEUR", "MERCI", "REPAS", "MARCHE", "VOYAGE"};

    private CompoundTag view;
    private NpcSettings settings;
    private UUID id;
    private int left, top;
    private String nameDraft, surnameDraft, titleDraft, ageDraft, bioDraft, healthDraft, speedDraft, damageDraft, startDraft, endDraft, sizeDraft, lineDraft = "", importDraft = "", usesDraft = "12";
    private int momentIndex = 1, gridSource, gridPage, gridJob = -1, lineScroll, offerScroll;
    private Component status = Component.empty();
    /** Le marché qu'on compose : ce qu'il demande (deux objets au plus) et ce qu'il donne. */
    private ItemStack costA = ItemStack.EMPTY, costB = ItemStack.EMPTY, result = ItemStack.EMPTY;
    private int activeSlot;

    public NpcEditorScreen(CompoundTag view) {
        super(Component.literal(view.getString("name")));
        read(view);
    }

    public static void open(CompoundTag view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NpcEditorScreen s && s.id.equals(view.getUUID("id"))) {
            s.read(view);
            s.rebuildWidgets();
            return;
        }
        mc.setScreen(new NpcEditorScreen(view));
    }

    private void read(CompoundTag v) {
        view = v;
        id = v.getUUID("id");
        settings = NpcSettings.load(v.getCompound("settings"));
        nameDraft = v.getString("name");
        surnameDraft = v.getString("surname");
        titleDraft = settings.title;
        ageDraft = Integer.toString(v.getInt("age"));
        bioDraft = v.getString("bio");
        healthDraft = number(settings.maxHealth);
        speedDraft = number(settings.speed);
        damageDraft = number(settings.damage);
        startDraft = hour(settings.workStart);
        endDraft = hour(settings.workEnd);
        sizeDraft = settings.size < 0 ? "" : Integer.toString(Math.round(settings.size * 100));
    }

    private static String number(double d) {
        return d <= 0 ? "" : d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    /** L'heure qu'indique un tick de la journée (le tick zéro est à six heures du matin). */
    private static String hour(int ticks) {
        return ticks < 0 ? "" : Integer.toString(Math.floorMod(ticks / 1000 + 6, 24));
    }

    private static int ticks(String hour) {
        try {
            String s = hour.strip();
            if (s.isEmpty()) return -1;
            return Math.floorMod(Integer.parseInt(s) - 6, 24) * 1000;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double parse(String s) {
        try {
            return s.isBlank() ? 0 : Double.parseDouble(s.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void send(Consumer<CompoundTag> fill) {
        CompoundTag t = new CompoundTag();
        fill.accept(t);
        CrusadeNetwork.toServer(new NpcPackets.Edit(id, t));
    }

    private int cx() {
        return left + 88;
    }

    private int cy() {
        return top + 42;
    }

    private int cw() {
        return W - 98;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        Tab[] all = Tab.values();
        int tw = (W - 24 - (all.length - 1) * 2) / all.length;
        for (int i = 0; i < all.length; i++) {
            Tab t = all[i];
            Button b = Button.builder(Component.translatable("crusadecraft.city.npc.tab." + t.name().toLowerCase(Locale.ROOT)), x -> {
                tab = t;
                rebuildWidgets();
            }).bounds(left + 12 + i * (tw + 2), top + 24, tw, 14).build(KitButton::new);
            b.active = t != tab;
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 12, top + H - 22, 66, 16).build(KitButton::new));
        switch (tab) {
            case SHEET -> initSheet();
            case LOOK -> initLook();
            case STATS -> initStats();
            case CONDUCT -> initConduct();
            case JOB -> initJob();
            case LINES -> initLines();
            case TRADES -> initTrades();
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
        Button b = Button.builder(Component.translatable(key), press).bounds(x, y, w, 14).build(KitButton::new);
        addRenderableWidget(b);
        return b;
    }

    private void toggle(String key, boolean on, int x, int y, int w, Consumer<Boolean> change) {
        Component label = Component.translatable(key).append(" : ").append(Component.translatable(on ? "crusadecraft.city.panel.yes" : "crusadecraft.city.panel.no"));
        addRenderableWidget(Button.builder(label, b -> change.accept(!on)).bounds(x, y, w, 14).build(KitButton::new));
    }

    // ------------------------------------------------------------------ la fiche

    private void initSheet() {
        int y = cy();
        box(cx() + 60, y, 110, nameDraft, 32, v -> nameDraft = v);
        box(cx() + 174, y, 110, surnameDraft, 32, v -> surnameDraft = v);
        y += 17;
        box(cx() + 60, y, 110, titleDraft, 32, v -> titleDraft = v).setHint(Component.translatable("crusadecraft.city.npc.title.hint"));
        box(cx() + 204, y, 30, ageDraft, 3, v -> ageDraft = v);
        y += 17;
        boolean female = view.getBoolean("female");
        addRenderableWidget(Button.builder(Component.translatable(female ? "crusadecraft.city.npc.female" : "crusadecraft.city.npc.male"), b -> send(t -> t.putBoolean("female", !female))).bounds(cx() + 60, y, 70, 14).build(KitButton::new));
        button("crusadecraft.city.npc.save", cx() + cw() - 90, y, 90, b -> send(t -> {
            t.putString("name", nameDraft);
            t.putString("surname", surnameDraft);
            t.putString("title", titleDraft);
            t.putInt("age", (int) parse(ageDraft));
            t.putString("bio", bioDraft);
        }));
        y += 18;
        MultiLineEditBox bio = new MultiLineEditBox(font, cx(), y, cw(), 40, Component.empty(), Component.empty());
        bio.setCharacterLimit(fr.crusadecraft.core.city.CityNpc.BIO_EDIT_MAX);
        bio.setValue(bioDraft);
        bio.setValueListener(v -> bioDraft = v);
        addRenderableWidget(bio);
        y += 46;
        List<Integer> mine = traits();
        int col = 0, row = 0, bw = (cw() - 6) / 4;
        for (Trait trait : Trait.values()) {
            if (trait.kind() != Trait.Kind.PERSONALITY) continue;
            if (y + row * 15 > top + H - 40) break;
            boolean on = mine.contains(trait.ordinal());
            Component label = Component.translatable(trait.key());
            if (on) label = label.copy().withStyle(net.minecraft.ChatFormatting.GOLD);
            int ordinal = trait.ordinal();
            addRenderableWidget(Button.builder(label, b -> setTrait(mine, ordinal)).bounds(cx() + col * (bw + 2), y + row * 15, bw, 14)
                    .tooltip(Tooltip.create(Component.translatable(trait.descriptionKey()))).build(KitButton::new));
            if (++col == 4) {
                col = 0;
                row++;
            }
        }
    }

    private List<Integer> traits() {
        List<Integer> out = new ArrayList<>();
        ListTag l = view.getList("traits", Tag.TAG_INT);
        for (Tag x : l) out.add(((IntTag) x).getAsInt());
        return out;
    }

    private void setTrait(List<Integer> current, int trait) {
        List<Integer> next = new ArrayList<>(current);
        if (next.contains(trait)) next.remove(Integer.valueOf(trait));
        else next.add(trait);
        send(t -> {
            ListTag l = new ListTag();
            for (int v : next) l.add(IntTag.valueOf(v));
            t.put("traits", l);
        });
    }

    // ------------------------------------------------------------------ l'allure

    private static final int GRID_COLS = 8, GRID_ROWS = 2, CELL = 38;

    private List<String> gridItems() {
        if (gridSource == 0) {
            List<String> out = new ArrayList<>();
            for (SkinService.Entry e : SkinClient.library()) out.add(SkinService.PREFIX + e.hash());
            return out;
        }
        List<String> jobs = Wardrobe.jobs();
        String job = gridJob < 0 || gridJob >= jobs.size() ? null : jobs.get(gridJob);
        return Wardrobe.filter(view.getBoolean("female"), job);
    }

    private int gridY() {
        return cy() + 36;
    }

    private void initLook() {
        SkinClient.askLibrary();
        int y = cy();
        button("crusadecraft.city.npc.look.skins", cx(), y, 80, b -> {
            gridSource = 0;
            gridPage = 0;
            rebuildWidgets();
        }).active = gridSource != 0;
        button("crusadecraft.city.npc.look.clothes", cx() + 82, y, 80, b -> {
            gridSource = 1;
            gridPage = 0;
            rebuildWidgets();
        }).active = gridSource != 1;
        if (gridSource == 1) {
            List<String> jobs = Wardrobe.jobs();
            String label = gridJob < 0 || gridJob >= jobs.size() ? Component.translatable("crusadecraft.city.panel.looks.all_jobs").getString() : jobs.get(gridJob);
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                gridJob = gridJob + 1 >= jobs.size() ? -1 : gridJob + 1;
                gridPage = 0;
                rebuildWidgets();
            }).bounds(cx() + 164, y, 80, 14).build(KitButton::new));
        }
        button("crusadecraft.city.npc.look.reset", cx() + cw() - 90, y, 90, b -> send(t -> {
            t.putString("look", "");
            t.putString("hair", "");
        }));
        y += 18;
        List<String> hair = Wardrobe.hair();
        if (!hair.isEmpty()) {
            int at = hair.indexOf(settings.hair);
            button("crusadecraft.city.npc.look.hair", cx(), y, 80, b -> {
                String next = hair.get((at + 1) % hair.size());
                send(t -> t.putString("hair", next));
            });
        }
        box(cx() + 150, y, 30, sizeDraft, 3, v -> sizeDraft = v);
        button("crusadecraft.city.panel.apply", cx() + 184, y, 50, b -> send(t -> t.putFloat("size", sizeDraft.isBlank() ? -1f : (float) parse(sizeDraft) / 100f)));
        int iy = top + H - 42;
        EditBox importing = box(cx(), iy, 220, importDraft, 300, v -> importDraft = v);
        importing.setHint(Component.translatable("crusadecraft.skin.import.hint"));
        button("crusadecraft.skin.import", cx() + 224, iy, 54, b -> {
            status = Component.translatable("crusadecraft.skin.import.running");
            SkinClient.importSkin(importDraft, id, c -> status = c);
        });
        button("crusadecraft.skin.folder", cx() + 280, iy, 62, b -> net.minecraft.Util.getPlatform().openFile(SkinClient.localFolder().toFile()));
    }

    // ------------------------------------------------------------------ statistiques

    private void initStats() {
        int y = cy() + 14;
        box(cx() + 130, y, 50, healthDraft, 6, v -> healthDraft = v);
        y += 17;
        box(cx() + 130, y, 50, speedDraft, 6, v -> speedDraft = v);
        y += 17;
        box(cx() + 130, y, 50, damageDraft, 6, v -> damageDraft = v);
        y += 20;
        button("crusadecraft.city.npc.save", cx() + 130, y, 90, b -> send(t -> {
            t.putDouble("maxHealth", parse(healthDraft));
            t.putDouble("speed", parse(speedDraft));
            t.putDouble("damage", parse(damageDraft));
        }));
        y += 22;
        toggle("crusadecraft.city.npc.invincible", settings.invincible, cx(), y, 160, v -> send(t -> t.putBoolean("invincible", v)));
        toggle("crusadecraft.city.npc.show_name", settings.showName, cx() + 164, y, 160, v -> send(t -> t.putBoolean("showName", v)));
    }

    // ------------------------------------------------------------------ conduite

    private void initConduct() {
        int y = cy();
        NpcSettings.Behavior[] all = NpcSettings.Behavior.values();
        for (int i = 0; i < all.length; i++) {
            NpcSettings.Behavior be = all[i];
            button("crusadecraft.city.npc.behavior." + be.name().toLowerCase(Locale.ROOT), cx() + i * 114, y, 110, b -> send(t -> t.putString("behavior", be.name()))).active = settings.behavior != be;
        }
        y += 34;
        box(cx() + 120, y, 30, startDraft, 2, v -> startDraft = v);
        box(cx() + 180, y, 30, endDraft, 2, v -> endDraft = v);
        button("crusadecraft.city.panel.apply", cx() + 216, y, 60, b -> send(t -> {
            t.putInt("workStart", ticks(startDraft));
            t.putInt("workEnd", ticks(endDraft));
        }));
        y += 24;
        int w = (cw() - 4) / 3;
        toggle("crusadecraft.city.npc.can_travel", settings.canTravel, cx(), y, w, v -> send(t -> t.putBoolean("canTravel", v)));
        toggle("crusadecraft.city.npc.can_marry", settings.canMarry, cx() + w + 2, y, w, v -> send(t -> t.putBoolean("canMarry", v)));
        toggle("crusadecraft.city.npc.can_leave", settings.canLeave, cx() + 2 * (w + 2), y, w, v -> send(t -> t.putBoolean("canLeave", v)));
        y += 22;
        for (int i = 0; i < 3; i++) {
            int level = i;
            button("crusadecraft.city.npc.chatty." + i, cx() + i * (w + 2), y, w, b -> send(t -> t.putInt("chatty", level))).active = settings.chatty != i;
        }
    }

    // ------------------------------------------------------------------ métier

    private void initJob() {
        int y = cy() + 14;
        BuildingFunction[] all = BuildingFunction.values();
        int bw = (cw() - 8) / 4, n = 0;
        String job = view.getString("job");
        button("crusadecraft.city.npc.job.none", cx(), y, bw, b -> send(t -> t.putString("job", ""))).active = !job.isEmpty();
        n++;
        for (BuildingFunction f : all) {
            if (f.dwelling()) continue;
            Button b = Button.builder(Component.translatable(f.key()), x -> send(t -> t.putString("job", f.name())))
                    .bounds(cx() + (n % 4) * (bw + 2), y + (n / 4) * 15, bw, 14).build(KitButton::new);
            b.active = !job.equals(f.name());
            addRenderableWidget(b);
            n++;
        }
    }

    // ------------------------------------------------------------------ répliques

    private String moment() {
        return MOMENTS[momentIndex];
    }

    private void initLines() {
        int y = cy();
        button("crusadecraft.city.panel.prev", cx(), y, 14, b -> {
            momentIndex = Math.floorMod(momentIndex - 1, MOMENTS.length);
            lineScroll = 0;
            rebuildWidgets();
        });
        Button shown = Button.builder(Component.translatable("crusadecraft.city.npc.moment." + moment().toLowerCase(Locale.ROOT).replace("*", "any")), b -> {
        }).bounds(cx() + 16, y, 150, 14).build(KitButton::new);
        shown.active = false;
        addRenderableWidget(shown);
        button("crusadecraft.city.panel.next", cx() + 168, y, 14, b -> {
            momentIndex = (momentIndex + 1) % MOMENTS.length;
            lineScroll = 0;
            rebuildWidgets();
        });
        y += 18;
        box(cx(), y, cw() - 66, lineDraft, 200, v -> lineDraft = v).setHint(Component.translatable("crusadecraft.city.npc.line.hint"));
        button("crusadecraft.city.npc.line.add", cx() + cw() - 62, y, 62, b -> {
            String text = lineDraft, m = moment();
            lineDraft = "";
            send(t -> {
                t.putString("moment", m);
                t.putString("addLine", text);
            });
        });
        List<String> lines = settings.lines.getOrDefault(moment(), List.of());
        int rows = 8;
        lineScroll = Mth.clamp(lineScroll, 0, Math.max(0, lines.size() - rows));
        for (int i = lineScroll; i < lines.size() && i < lineScroll + rows; i++) {
            int at = i;
            button("crusadecraft.city.panel.remove", cx(), y + 18 + (i - lineScroll) * 15, 14, b -> {
                String m = moment();
                send(t -> {
                    t.putString("moment", m);
                    t.putInt("removeLine", at);
                });
            });
        }
    }

    // ------------------------------------------------------------------ échanges

    /** Les échanges montrés : ceux qu'on lui a donnés, sinon ceux qu'il a. */
    private MerchantOffers offers() {
        if (settings.offers != null) return new MerchantOffers(settings.offers);
        return view.contains("offersNow") ? new MerchantOffers(view.getCompound("offersNow")) : new MerchantOffers();
    }

    private static final int INV_COLS = 9, SLOT = 18;

    private int slotsY() {
        return cy();
    }

    private int invY() {
        return top + H - 22 - 4 * SLOT - 2;
    }

    private void initTrades() {
        MerchantOffers offers = offers();
        int rows = 5;
        offerScroll = Mth.clamp(offerScroll, 0, Math.max(0, offers.size() - rows));
        int lx = cx() + 180;
        for (int i = offerScroll; i < offers.size() && i < offerScroll + rows; i++) {
            int at = i;
            button("crusadecraft.city.panel.remove", lx + cw() - 180 - 14, cy() + (i - offerScroll) * 20 + 2, 14, b -> send(t -> t.putInt("removeOffer", at)));
        }
        box(cx() + 92, slotsY() + 24, 30, usesDraft, 4, v -> usesDraft = v);
        button("crusadecraft.city.npc.offer.add", cx(), slotsY() + 42, 84, b -> {
            if (costA.isEmpty() || result.isEmpty()) {
                status = Component.translatable("crusadecraft.city.npc.offer.empty");
                return;
            }
            ItemStack a = costA, bb = costB, r = result;
            send(t -> {
                CompoundTag o = new CompoundTag();
                o.put("a", a.save(new CompoundTag()));
                o.put("b", bb.save(new CompoundTag()));
                o.put("r", r.save(new CompoundTag()));
                o.putInt("uses", (int) parse(usesDraft));
                t.put("addOffer", o);
            });
            costA = ItemStack.EMPTY;
            costB = ItemStack.EMPTY;
            result = ItemStack.EMPTY;
        });
        button("crusadecraft.city.npc.offer.reset", cx() + 88, slotsY() + 42, 84, b -> send(t -> t.putBoolean("resetOffers", true)));
    }

    // ------------------------------------------------------------------ dessiner

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, W, H);
        Ui.scaled(g, font, Component.literal(view.getString("name") + " " + view.getString("surname")), left + 12, top + 8, 1.25f, Ui.TEXT_TITLE, false);
        Ui.fitted(g, font, Component.literal(view.getString("jobTitle") + " · " + view.getString("city")), left + W - 172, top + 10, 160, Ui.TEXT_MUTED);
        Ui.inset(g, left + 12, top + 42, 68, 110);
        Portraits.render(g, view.getInt("entity"), left + 12, top + 42, 68, 110, mx, my);
        if (!settings.look.isEmpty()) {
            Ui.fitted(g, font, Component.literal(Wardrobe.label(settings.look)), left + 12, top + 156, 68, Ui.TEXT_MUTED);
        }
        if (view.getBoolean("notable")) Ui.wrapped(g, font, Component.translatable("crusadecraft.city.npc.notable"), left + 12, top + 172, 68, Ui.GOLD);
        switch (tab) {
            case SHEET -> renderSheet(g);
            case LOOK -> renderLook(g, mx, my);
            case STATS -> renderStats(g);
            case CONDUCT -> renderConduct(g);
            case JOB -> renderJob(g);
            case LINES -> renderLines(g);
            case TRADES -> renderTrades(g, mx, my);
        }
        super.render(g, mx, my, partial);
    }

    private void label(GuiGraphics g, String key, int x, int y, int w) {
        Ui.fitted(g, font, Component.translatable(key), x, y + 3, w, Ui.TEXT_MUTED);
    }

    private void renderSheet(GuiGraphics g) {
        int y = cy();
        label(g, "crusadecraft.city.npc.name", cx(), y, 58);
        y += 17;
        label(g, "crusadecraft.city.npc.title", cx(), y, 58);
        label(g, "crusadecraft.city.npc.age", cx() + 174, y, 30);
        y += 17;
        label(g, "crusadecraft.city.npc.sex", cx(), y, 58);
    }

    private void renderLook(GuiGraphics g, int mx, int my) {
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.npc.size"), cx() + 88, cy() + 21, 60, Ui.TEXT_MUTED);
        List<String> items = gridItems();
        int perPage = GRID_COLS * GRID_ROWS;
        int pages = Math.max(1, (items.size() + perPage - 1) / perPage);
        gridPage = Mth.clamp(gridPage, 0, pages - 1);
        int gy = gridY();
        Ui.inset(g, cx(), gy, cw(), CELL * GRID_ROWS + 4);
        if (items.isEmpty()) Ui.wrapped(g, font, Component.translatable(gridSource == 0 ? "crusadecraft.city.panel.looks.no_skins" : "crusadecraft.city.panel.looks.no_clothes"), cx() + 8, gy + 8, cw() - 16, Ui.TEXT_MUTED);
        for (int i = gridPage * perPage; i < items.size() && i < (gridPage + 1) * perPage; i++) {
            int k = i - gridPage * perPage;
            int x = cx() + 8 + (k % GRID_COLS) * 42, y = gy + 4 + (k / GRID_COLS) * CELL;
            if (items.get(i).equals(settings.look)) g.fill(x - 2, y, x + 18, y + 36, Ui.GOLD_DARK);
            else if (Ui.inside(mx, my, x - 2, y, 20, 36)) g.fill(x - 2, y, x + 18, y + 36, Ui.PANEL_HIGHLIGHT);
            Wardrobe.doll(g, items.get(i), x, y + 2, 1);
        }
        for (int i = gridPage * perPage; i < items.size() && i < (gridPage + 1) * perPage; i++) {
            int k = i - gridPage * perPage;
            int x = cx() + 8 + (k % GRID_COLS) * 42, y = gy + 4 + (k / GRID_COLS) * CELL;
            if (Ui.inside(mx, my, x - 2, y, 20, 36)) g.renderTooltip(font, Component.literal(Wardrobe.label(items.get(i))), mx, my);
        }
        if (pages > 1) Ui.fitted(g, font, Component.translatable("crusadecraft.city.panel.looks.page", gridPage + 1, pages), cx(), gy + CELL * GRID_ROWS + 6, 60, Ui.TEXT_MUTED);
        Ui.fitted(g, font, status, cx() + 70, top + H - 22 + 3, cw() - 70, Ui.GOLD);
    }

    private void renderStats(GuiGraphics g) {
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.npc.stats.hint"), cx(), cy(), cw(), Ui.TEXT_MUTED);
        int y = cy() + 14;
        label(g, "crusadecraft.city.npc.max_health", cx(), y, 126);
        y += 17;
        label(g, "crusadecraft.city.npc.speed", cx(), y, 126);
        y += 17;
        label(g, "crusadecraft.city.npc.damage", cx(), y, 126);
    }

    private void renderConduct(GuiGraphics g) {
        Ui.wrapped(g, font, Component.translatable("crusadecraft.city.npc.behavior." + settings.behavior.name().toLowerCase(Locale.ROOT) + ".tip"), cx(), cy() + 17, cw(), Ui.TEXT_MUTED);
        int y = cy() + 34;
        label(g, "crusadecraft.city.npc.hours", cx(), y, 116);
        label(g, "crusadecraft.city.npc.hours.to", cx() + 154, y, 24);
    }

    private void renderJob(GuiGraphics g) {
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.npc.job.hint"), cx(), cy(), cw(), Ui.TEXT_MUTED);
    }

    private void renderLines(GuiGraphics g) {
        List<String> lines = settings.lines.getOrDefault(moment(), List.of());
        int y = cy() + 18;
        if (lines.isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.city.npc.lines.empty"), cx(), y + 20, cw(), Ui.TEXT_MUTED);
        for (int i = lineScroll; i < lines.size() && i < lineScroll + 8; i++) {
            Ui.fitted(g, font, Component.literal(lines.get(i)), cx() + 18, y + 18 + (i - lineScroll) * 15 + 3, cw() - 18, Ui.TEXT);
        }
    }

    private void slot(GuiGraphics g, ItemStack stack, int x, int y, boolean active, int mx, int my) {
        g.fill(x, y, x + SLOT, y + SLOT, active ? Ui.GOLD : Ui.GOLD_DARK);
        g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, Ui.PANEL_INNER);
        if (stack.isEmpty()) return;
        g.renderItem(stack, x + 1, y + 1);
        g.renderItemDecorations(font, stack, x + 1, y + 1);
    }

    private void renderTrades(GuiGraphics g, int mx, int my) {
        int sy = slotsY();
        slot(g, costA, cx(), sy, activeSlot == 0, mx, my);
        Ui.text(g, font, Component.literal("+"), cx() + 21, sy + 5, Ui.TEXT_MUTED);
        slot(g, costB, cx() + 30, sy, activeSlot == 1, mx, my);
        Ui.text(g, font, Component.literal("→"), cx() + 52, sy + 5, Ui.TEXT_MUTED);
        slot(g, result, cx() + 66, sy, activeSlot == 2, mx, my);
        label(g, "crusadecraft.city.npc.offer.uses", cx(), sy + 24, 88);
        // Ses échanges, à droite.
        MerchantOffers offers = offers();
        int lx = cx() + 180;
        Ui.inset(g, lx - 2, cy() - 2, cw() - 178, 104);
        if (offers.isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.city.npc.offers.empty"), lx + 4, cy() + 4, cw() - 188, Ui.TEXT_MUTED);
        ItemStack tip = ItemStack.EMPTY;
        for (int i = offerScroll; i < offers.size() && i < offerScroll + 5; i++) {
            MerchantOffer o = offers.get(i);
            int y = cy() + (i - offerScroll) * 20;
            tip = offerItem(g, o.getBaseCostA(), lx, y, mx, my, tip);
            if (!o.getCostB().isEmpty()) tip = offerItem(g, o.getCostB(), lx + 18, y, mx, my, tip);
            Ui.text(g, font, Component.literal("→"), lx + 38, y + 5, Ui.TEXT_MUTED);
            tip = offerItem(g, o.getResult(), lx + 48, y, mx, my, tip);
            Ui.fitted(g, font, Component.literal("× " + o.getMaxUses()), lx + 68, y + 5, 40, Ui.TEXT_MUTED);
        }
        // Le sac du joueur : un clic met l'objet dans la case choisie.
        var inv = Minecraft.getInstance().player.getInventory();
        int iy = invY();
        Ui.fitted(g, font, Component.translatable("crusadecraft.city.npc.offer.pick"), cx(), iy - 11, cw(), Ui.TEXT_MUTED);
        for (int i = 0; i < 36; i++) {
            int x = cx() + (i % INV_COLS) * SLOT, y = iy + (i / INV_COLS) * SLOT;
            ItemStack s = inv.getItem(i);
            slot(g, s, x, y, false, mx, my);
            if (!s.isEmpty() && Ui.inside(mx, my, x, y, SLOT, SLOT)) tip = s;
        }
        Ui.fitted(g, font, status, cx() + 9 * SLOT + 6, iy + 2, cw() - 9 * SLOT - 6, Ui.RED);
        if (!tip.isEmpty()) g.renderTooltip(font, tip, mx, my);
    }

    private ItemStack offerItem(GuiGraphics g, ItemStack s, int x, int y, int mx, int my, ItemStack tip) {
        g.renderItem(s, x, y + 1);
        g.renderItemDecorations(font, s, x, y + 1);
        return Ui.inside(mx, my, x, y, 16, 18) ? s : tip;
    }

    // ------------------------------------------------------------------ gestes

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tab == Tab.LOOK) {
            List<String> items = gridItems();
            int perPage = GRID_COLS * GRID_ROWS, gy = gridY();
            for (int i = gridPage * perPage; i < items.size() && i < (gridPage + 1) * perPage; i++) {
                int k = i - gridPage * perPage;
                int x = cx() + 8 + (k % GRID_COLS) * 42, y = gy + 4 + (k / GRID_COLS) * CELL;
                if (Ui.inside(mx, my, x - 2, y, 20, 36)) {
                    String look = items.get(i);
                    send(t -> t.putString("look", look));
                    return true;
                }
            }
        }
        if (tab == Tab.TRADES) {
            int sy = slotsY();
            if (Ui.inside(mx, my, cx(), sy, SLOT, SLOT)) return slotClick(0, button);
            if (Ui.inside(mx, my, cx() + 30, sy, SLOT, SLOT)) return slotClick(1, button);
            if (Ui.inside(mx, my, cx() + 66, sy, SLOT, SLOT)) return slotClick(2, button);
            var inv = Minecraft.getInstance().player.getInventory();
            int iy = invY();
            for (int i = 0; i < 36; i++) {
                int x = cx() + (i % INV_COLS) * SLOT, y = iy + (i / INV_COLS) * SLOT;
                if (!Ui.inside(mx, my, x, y, SLOT, SLOT)) continue;
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) return true;
                if (activeSlot == 0) costA = s.copy();
                else if (activeSlot == 1) costB = s.copy();
                else result = s.copy();
                activeSlot = (activeSlot + 1) % 3;
                status = Component.empty();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Clic gauche : la choisir ; clic droit : la vider. */
    private boolean slotClick(int slot, int button) {
        activeSlot = slot;
        if (button == 1) {
            if (slot == 0) costA = ItemStack.EMPTY;
            else if (slot == 1) costB = ItemStack.EMPTY;
            else result = ItemStack.EMPTY;
        }
        return true;
    }

    /** Sur une case du marché, la molette change la quantité. */
    private boolean scrollSlot(double mx, double my, double delta) {
        int sy = slotsY();
        ItemStack s = Ui.inside(mx, my, cx(), sy, SLOT, SLOT) ? costA : Ui.inside(mx, my, cx() + 30, sy, SLOT, SLOT) ? costB : Ui.inside(mx, my, cx() + 66, sy, SLOT, SLOT) ? result : null;
        if (s == null || s.isEmpty()) return false;
        s.setCount(Mth.clamp(s.getCount() + (int) Math.signum(delta), 1, s.getMaxStackSize()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (super.mouseScrolled(mx, my, delta)) return true;
        int d = (int) Math.signum(delta);
        if (tab == Tab.TRADES) {
            if (scrollSlot(mx, my, delta)) return true;
            offerScroll = Math.max(0, offerScroll - d);
            rebuildWidgets();
            return true;
        }
        if (tab == Tab.LOOK) {
            gridPage = Math.max(0, gridPage - d);
            return true;
        }
        if (tab == Tab.LINES) {
            lineScroll = Math.max(0, lineScroll - d);
            rebuildWidgets();
            return true;
        }
        return false;
    }

    /** Pour le banc d'essai : le centre de cette case du sac. */
    public int[] inventorySlotForShots(int i) {
        return new int[]{cx() + (i % INV_COLS) * SLOT + 8, invY() + (i / INV_COLS) * SLOT + 8};
    }

    /** Pour le banc d'essai : le centre de la case de ce qu'il donne. */
    public int[] resultSlotForShots() {
        return new int[]{cx() + 66 + 8, slotsY() + 8};
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
