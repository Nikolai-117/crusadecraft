package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.city.CityGood;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.WorldEconomyPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * L'économie du monde (P54) : les villes et leurs bourses, le tableau des prix, les lignes les plus rentables, ceux qui sont
 * sur les routes, les royaumes. Une lecture : tout ce qui se règle reste dans le panneau de chaque ville.
 */
public class EconomyScreen extends Screen {
    private static final int W = 440, H = 250, ROW = 12;
    /** Toutes les tant de ticks, on redemande les chiffres. */
    private static final int ASK_EVERY = 60;

    private enum Tab { CITIES, PRICES, LINES, ROADS, REALMS, CONTRACTS }

    private enum Sort { RICH, NEED, NEAR, NAME }

    /** On revient là où l'on était, d'une ouverture à l'autre. */
    private static Tab tab = Tab.CITIES;
    private static Sort sort = Sort.RICH;
    private static int good;

    private CompoundTag view;
    private int left, top, scroll, askIn = ASK_EVERY;

    public EconomyScreen(CompoundTag view) {
        super(Component.translatable("crusadecraft.economy.title"));
        this.view = view;
    }

    public static void open(CompoundTag view, boolean refresh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof EconomyScreen s) {
            s.view = view;
            s.rebuildWidgets();
        } else if (!refresh) {
            mc.setScreen(new EconomyScreen(view));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (--askIn > 0) return;
        askIn = ASK_EVERY;
        CrusadeNetwork.toServer(new WorldEconomyPackets.Request(true));
    }

    private int x() {
        return left + 12;
    }

    private int y0() {
        return top + 42;
    }

    private int rows(int reserved) {
        return (H - 42 - 28 - reserved) / ROW;
    }

    private List<CompoundTag> list(String key) {
        List<CompoundTag> out = new ArrayList<>();
        ListTag l = view.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) out.add(l.getCompound(i));
        return out;
    }

    /** Ce qui manque à cette ville, en ordre de gravité : combien de marchandises sous le seuil. */
    private static int needs(CompoundTag city) {
        int n = 0;
        CompoundTag stock = city.getCompound("stock");
        for (CityGood g : CityGood.values()) if (stock.getInt(g.name()) < 16) n++;
        return n + city.getInt("famine") * 10;
    }

    private List<CompoundTag> cities() {
        List<CompoundTag> out = list("cities");
        Comparator<CompoundTag> order = switch (sort) {
            case RICH -> Comparator.comparingLong((CompoundTag c) -> -c.getLong("purse"));
            case NEED -> Comparator.comparingInt((CompoundTag c) -> -needs(c));
            case NEAR -> Comparator.comparingInt((CompoundTag c) -> c.getInt("distance") < 0 ? Integer.MAX_VALUE : c.getInt("distance"));
            case NAME -> Comparator.comparing((CompoundTag c) -> c.getString("name"), String.CASE_INSENSITIVE_ORDER);
        };
        out.sort(order);
        return out;
    }

    private List<CompoundTag> priced() {
        String g = CityGood.values()[good].name();
        List<CompoundTag> out = list("cities");
        out.sort(Comparator.comparingInt((CompoundTag c) -> -c.getCompound("quote").getInt(g)).thenComparing(c -> c.getString("name"), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private int size() {
        return switch (tab) {
            case CITIES -> list("cities").size();
            case PRICES -> list("cities").size();
            case LINES -> list("lines").size();
            case ROADS -> list("roads").size();
            case REALMS -> list("realms").size();
            case CONTRACTS -> offers().size();
        };
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        Tab[] all = Tab.values();
        int tw = (W - 24 - (all.length - 1)) / all.length;
        for (int i = 0; i < all.length; i++) {
            Tab t = all[i];
            Button b = Button.builder(Component.translatable("crusadecraft.economy.tab." + t.name().toLowerCase(Locale.ROOT)), x -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(left + 12 + i * (tw + 1), top + 24, tw, 14).build();
            b.active = t != tab;
            addRenderableWidget(b);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + W - 66, top + H - 22, 54, 16).build());
        if (tab == Tab.CITIES) initBlockades();
        if (tab == Tab.CONTRACTS) initContracts();
        if (tab == Tab.CITIES) {
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.economy.sort", Component.translatable("crusadecraft.economy.sort." + sort.name().toLowerCase(Locale.ROOT))), b -> {
                sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
                scroll = 0;
                rebuildWidgets();
            }).bounds(x(), top + H - 22, 150, 16).build());
        }
        if (tab == Tab.PRICES) {
            CityGood[] goods = CityGood.values();
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.city.panel.prev"), b -> {
                good = Math.floorMod(good - 1, goods.length);
                scroll = 0;
                rebuildWidgets();
            }).bounds(x(), y0(), 14, 14).build());
            Button shown = Button.builder(Component.translatable(goods[good].key()), b -> {
            }).bounds(x() + 15, y0(), 90, 14).build();
            shown.active = false;
            addRenderableWidget(shown);
            addRenderableWidget(Button.builder(Component.translatable("crusadecraft.city.panel.next"), b -> {
                good = (good + 1) % goods.length;
                scroll = 0;
                rebuildWidgets();
            }).bounds(x() + 106, y0(), 14, 14).build());
        }
    }

    // ------------------------------------------------------------------ les contrats

    private static final int BIG_ROW = 24;

    /** Ce qu'on a accepté d'abord, puis les offres, puis les commandes, du plus près au plus loin. */
    private List<CompoundTag> offers() {
        List<CompoundTag> mine = new ArrayList<>(), open = new ArrayList<>(), orders = list("orders");
        for (CompoundTag c : list("contracts")) (c.getBoolean("mine") ? mine : open).add(c);
        Comparator<CompoundTag> near = Comparator.comparingInt(c -> c.getInt("near"));
        open.sort(near);
        orders.sort(near);
        List<CompoundTag> out = new ArrayList<>(mine);
        out.addAll(open);
        out.addAll(orders);
        return out;
    }

    private void act(java.util.function.Consumer<CompoundTag> fill) {
        CompoundTag t = new CompoundTag();
        fill.accept(t);
        CrusadeNetwork.toServer(new WorldEconomyPackets.Action(t));
    }

    private void initContracts() {
        List<CompoundTag> list = offers();
        int rows = (H - 42 - 28) / BIG_ROW;
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag c = list.get(i);
            int ry = y0() + (i - scroll) * BIG_ROW + 3, bx = x() + W - 24 - 62;
            if (c.hasUUID("site")) {
                java.util.UUID site = c.getUUID("site");
                Button b = Button.builder(Component.translatable("crusadecraft.economy.contract.deliver"), x -> act(t -> t.putUUID("deliver", site))).bounds(bx, ry, 62, 14)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("crusadecraft.economy.contract.deliver.tip"))).build();
                b.active = c.getBoolean("can");
                addRenderableWidget(b);
            } else {
                java.util.UUID id = c.getUUID("id");
                boolean mine = c.getBoolean("mine");
                Button b = Button.builder(Component.translatable(mine ? "crusadecraft.economy.contract.abandon" : "crusadecraft.economy.contract.accept"), x -> act(t -> t.putUUID(mine ? "abandon" : "accept", id))).bounds(bx, ry, 62, 14)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(mine ? "crusadecraft.economy.contract.abandon.tip" : "crusadecraft.economy.contract.accept.tip", fr.crusadecraft.core.city.CityContracts.SIGN_RANGE))).build();
                addRenderableWidget(b);
            }
        }
    }

    private String blocks(int near) {
        return near == Integer.MAX_VALUE ? "?" : Integer.toString(near);
    }

    private void renderContracts(GuiGraphics g) {
        List<CompoundTag> list = offers();
        if (list.isEmpty()) {
            empty(g, "crusadecraft.economy.contract.empty", y0());
            return;
        }
        int rows = (H - 42 - 28) / BIG_ROW, tw = W - 24 - 62 - 40;
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag c = list.get(i);
            int ry = y0() + (i - scroll) * BIG_ROW;
            Component head, line;
            int color = Ui.TEXT;
            if (c.hasUUID("site")) {
                boolean urgent = c.getBoolean("urgent");
                head = Component.translatable(urgent ? "crusadecraft.economy.contract.order.urgent" : "crusadecraft.economy.contract.order", c.getString("city"), c.getString("what"));
                line = Component.translatable("crusadecraft.economy.contract.order.line", blocks(c.getInt("near")), c.getLong("days"));
                if (urgent) color = Ui.RED;
            } else {
                boolean mine = c.getBoolean("mine");
                head = Component.translatable("crusadecraft.economy.contract." + c.getString("kind").toLowerCase(Locale.ROOT), c.getString("to"));
                line = mine && c.contains("presence") ? Component.translatable("crusadecraft.economy.contract.presence", c.getInt("presence"), fr.crusadecraft.core.city.CityContracts.PRESENCE)
                        : mine ? Component.translatable("crusadecraft.economy.contract.mine", blocks(c.getInt("near")), c.getLong("days"))
                        : Component.translatable("crusadecraft.economy.contract.line", c.getString("from"), c.getInt("route"), blocks(c.getInt("near")), c.getLong("days"));
                if (mine) color = Ui.TEXT_TITLE;
            }
            Ui.fitted(g, font, head, x(), ry + 2, tw, color);
            Ui.fitted(g, font, line, x(), ry + 12, tw, Ui.TEXT_MUTED);
            Ui.fitted(g, font, Component.translatable("crusadecraft.economy.contract.reward", c.getInt("reward")), x() + tw + 2, ry + 7, 38, Ui.GOLD);
        }
    }

    /** P55 : en face d'une ville ennemie, de quoi la couper du monde ; en face de celle qu'on bloque, de quoi lever le blocus. */
    private void initBlockades() {
        List<CompoundTag> list = cities();
        int rows = rows(14);
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag c = list.get(i);
            boolean mine = c.getBoolean("byMe");
            if (!mine && !c.getBoolean("canBlock")) continue;
            int ry = y0() + 13 + (i - scroll) * ROW;
            java.util.UUID id = c.getUUID("id");
            addRenderableWidget(Button.builder(Component.translatable(mine ? "crusadecraft.economy.blockade.lift" : "crusadecraft.economy.blockade"), b -> {
                CompoundTag t = new CompoundTag();
                t.putUUID("site", id);
                t.putBoolean("lift", mine);
                CrusadeNetwork.toServer(new WorldEconomyPackets.Action(t));
            }).bounds(x() + W - 24 - 44, ry - 2, 44, 12).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("crusadecraft.economy.blockade.tip", fr.crusadecraft.core.city.CityBlockades.COST))).build());
        }
    }

    // ------------------------------------------------------------------ dessiner

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Ui.panel(g, left, top, W, H);
        Ui.scaled(g, font, title, left + 12, top + 8, 1.25f, Ui.TEXT_TITLE, false);
        switch (tab) {
            case CITIES -> renderCities(g, mx, my);
            case PRICES -> renderPrices(g);
            case LINES -> renderLines(g);
            case ROADS -> renderRoads(g);
            case REALMS -> renderRealms(g);
            case CONTRACTS -> renderContracts(g);
        }
        super.render(g, mx, my, partial);
        if (tab == Tab.CITIES) cityTooltip(g, mx, my);
    }

    private void head(GuiGraphics g, String key, int x, int y, int w) {
        Ui.fitted(g, font, Component.translatable("crusadecraft.economy.col." + key), x, y, w, Ui.GOLD);
    }

    private void cell(GuiGraphics g, String s, int x, int y, int w, int color) {
        Ui.fitted(g, font, Component.literal(s), x, y, w, color);
    }

    private void empty(GuiGraphics g, String key, int y) {
        Ui.wrapped(g, font, Component.translatable(key), x(), y, W - 24, Ui.TEXT_MUTED);
    }

    private void renderCities(GuiGraphics g, int mx, int my) {
        List<CompoundTag> list = cities();
        int y = y0();
        head(g, "city", x(), y, 112);
        head(g, "realm", x() + 116, y, 96);
        head(g, "people", x() + 214, y, 28);
        head(g, "purse", x() + 246, y, 34);
        head(g, "lacks", x() + 284, y, 132);
        if (list.isEmpty()) {
            empty(g, "crusadecraft.economy.cities.empty", y + 14);
            return;
        }
        int rows = rows(14);
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag c = list.get(i);
            int ry = y + 13 + (i - scroll) * ROW;
            if (Ui.inside(mx, my, x(), ry - 1, W - 24, ROW)) g.fill(x() - 2, ry - 2, x() + W - 22, ry + ROW - 2, Ui.PANEL_HIGHLIGHT);
            int color = c.getBoolean("mine") ? Ui.TEXT_TITLE : Ui.TEXT;
            cell(g, c.getString("name"), x(), ry, 112, color);
            cell(g, c.getString("realm"), x() + 116, ry, 96, Ui.TEXT_MUTED);
            cell(g, c.getBoolean("market") ? "—" : Integer.toString(c.getInt("people")), x() + 214, ry, 28, Ui.TEXT);
            cell(g, Long.toString(c.getLong("purse")), x() + 246, ry, 34, Ui.TEXT);
            int room = c.getBoolean("byMe") || c.getBoolean("canBlock") ? 86 : 132;
            if (c.contains("blocked")) cell(g, c.getString("blocked"), x() + 284, ry, room, Ui.RED);
            else if (c.getInt("famine") > 0) Ui.fitted(g, font, Component.translatable("crusadecraft.economy.famine", c.getInt("famine")), x() + 284, ry, room, Ui.RED);
            else cell(g, lacks(c), x() + 284, ry, room, Ui.TEXT_MUTED);
        }
    }

    /** « Blé, Fer », ce qu'il lui manque. */
    private String lacks(CompoundTag city) {
        StringJoiner j = new StringJoiner(", ");
        CompoundTag stock = city.getCompound("stock");
        for (CityGood g : CityGood.values()) if (stock.getInt(g.name()) < 16) j.add(Component.translatable(g.key()).getString());
        return j.toString();
    }

    private void cityTooltip(GuiGraphics g, int mx, int my) {
        List<CompoundTag> list = cities();
        int y = y0(), rows = rows(14);
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            int ry = y + 13 + (i - scroll) * ROW;
            if (!Ui.inside(mx, my, x(), ry - 1, W - 24, ROW)) continue;
            CompoundTag c = list.get(i), stock = c.getCompound("stock");
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(c.getString("name")));
            if (c.getInt("distance") >= 0) tip.add(Component.translatable("crusadecraft.economy.distance", c.getInt("distance")));
            for (CityGood good : CityGood.values()) tip.add(Component.translatable(good.key()).append(" : " + stock.getInt(good.name())));
            g.renderComponentTooltip(font, tip, mx, my);
            return;
        }
    }

    private void renderPrices(GuiGraphics g) {
        List<CompoundTag> list = priced();
        String key = CityGood.values()[good].name();
        int y = y0();
        if (list.isEmpty()) {
            empty(g, "crusadecraft.economy.cities.empty", y + 20);
            return;
        }
        CompoundTag best = list.get(0), worst = list.get(list.size() - 1);
        Ui.fitted(g, font, Component.translatable("crusadecraft.economy.prices.advice", view.getInt("quoteFor"), best.getString("name"), best.getCompound("quote").getInt(key),
                worst.getString("name"), worst.getCompound("quote").getInt(key)), x() + 126, y + 3, W - 24 - 126, Ui.TEXT_MUTED);
        y += 18;
        head(g, "city", x(), y, 130);
        head(g, "realm", x() + 134, y, 110);
        head(g, "stock", x() + 248, y, 40);
        head(g, "price", x() + 292, y, 40);
        int rows = rows(18 + 14), top = best.getCompound("quote").getInt(key);
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag c = list.get(i);
            int ry = y + 13 + (i - scroll) * ROW, stock = c.getCompound("stock").getInt(key), price = c.getCompound("quote").getInt(key);
            cell(g, c.getString("name"), x(), ry, 130, c.getBoolean("mine") ? Ui.TEXT_TITLE : Ui.TEXT);
            cell(g, c.getString("realm"), x() + 134, ry, 110, Ui.TEXT_MUTED);
            cell(g, Integer.toString(stock), x() + 248, ry, 40, stock < 16 ? Ui.RED : Ui.TEXT);
            cell(g, Integer.toString(price), x() + 292, ry, 28, Ui.TEXT_TITLE);
            Ui.bar(g, x() + 322, ry + 2, 94, 5, top <= 0 ? 0 : (double) price / top, Ui.GOLD);
        }
    }

    private void renderLines(GuiGraphics g) {
        List<CompoundTag> list = list("lines");
        int y = y0();
        head(g, "route", x(), y, 146);
        head(g, "what", x() + 150, y, 54);
        head(g, "trips", x() + 206, y, 22);
        head(g, "moved", x() + 230, y, 30);
        head(g, "earned", x() + 262, y, 32);
        head(g, "tolls", x() + 296, y, 30);
        head(g, "status", x() + 328, y, 88);
        if (list.isEmpty()) {
            empty(g, "crusadecraft.economy.lines.empty", y + 14);
            return;
        }
        int rows = rows(14);
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag l = list.get(i);
            int ry = y + 13 + (i - scroll) * ROW;
            int color = l.getBoolean("mine") ? Ui.TEXT_TITLE : Ui.TEXT;
            cell(g, l.getString("from") + " → " + l.getString("to"), x(), ry, 146, color);
            Component what = l.contains("good") ? Component.translatable(CityGood.valueOf(l.getString("good")).key()) : Component.translatable("crusadecraft.city.panel.trade.auto");
            Ui.fitted(g, font, what, x() + 150, ry, 54, Ui.TEXT_MUTED);
            cell(g, Integer.toString(l.getInt("trips")), x() + 206, ry, 22, Ui.TEXT);
            cell(g, Long.toString(l.getLong("moved")), x() + 230, ry, 30, Ui.TEXT);
            cell(g, Long.toString(l.getLong("earned")), x() + 262, ry, 32, Ui.GOLD);
            cell(g, Long.toString(l.getLong("tolls")), x() + 296, ry, 30, Ui.TEXT_MUTED);
            String status = l.getString("status");
            if (!status.isEmpty()) Ui.fitted(g, font, Component.translatable("crusadecraft.city.line.status." + status), x() + 328, ry, 88, status.equals("cut") || status.equals("blockade") || status.equals("war") || status.equals("toll") ? Ui.RED : Ui.TEXT_MUTED);
        }
    }

    private static final int CHART_H = 44;

    private void renderRoads(GuiGraphics g) {
        int y = y0();
        // Ce qui s'est vendu dans le monde, jour par jour : le plus récent à droite.
        long[] moved = view.getLongArray("moved"), money = view.getLongArray("money");
        Ui.inset(g, x(), y, W - 24, CHART_H);
        long max = 1, total = 0, totalMoney = 0;
        for (long v : moved) {
            max = Math.max(max, v);
            total += v;
        }
        for (long v : money) totalMoney += v;
        int bw = moved.length == 0 ? 0 : (W - 24 - 8) / moved.length;
        for (int i = 0; i < moved.length; i++) {
            int bh = (int) Math.round((CHART_H - 16) * (double) moved[i] / max);
            int bx = x() + 4 + i * bw;
            if (bh > 0) g.fill(bx, y + CHART_H - 3 - bh, bx + bw - 2, y + CHART_H - 3, i == moved.length - 1 ? Ui.TEXT_TITLE : Ui.GOLD);
        }
        Ui.fitted(g, font, Component.translatable("crusadecraft.economy.roads.chart", moved.length, total, totalMoney), x() + 4, y + 3, W - 32, Ui.TEXT_MUTED);
        y += CHART_H + 4;
        List<CompoundTag> list = list("roads");
        head(g, "who", x(), y, 112);
        head(g, "route", x() + 116, y, 150);
        head(g, "cargo", x() + 270, y, 86);
        if (list.isEmpty()) {
            empty(g, "crusadecraft.economy.roads.empty", y + 14);
            return;
        }
        int rows = rows(CHART_H + 4 + 14);
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag p = list.get(i);
            int ry = y + 13 + (i - scroll) * ROW;
            cell(g, p.getString("name"), x(), ry, 112, p.getBoolean("caravan") ? Ui.TEXT_TITLE : Ui.TEXT);
            cell(g, p.getString("from") + " → " + p.getString("to"), x() + 116, ry, 150, Ui.TEXT_MUTED);
            cell(g, p.getString("cargo"), x() + 270, ry, 86, Ui.GOLD_DARK);
            Ui.bar(g, x() + 360, ry + 2, 56, 5, p.getInt("percent") / 100.0, p.getBoolean("caravan") ? Ui.GOLD : Ui.TEXT_MUTED);
        }
    }

    private void renderRealms(GuiGraphics g) {
        List<CompoundTag> list = list("realms");
        list.sort(Comparator.comparingLong((CompoundTag c) -> -c.getLong("gold")));
        int y = y0();
        head(g, "realm", x() + 12, y, 116);
        head(g, "gold", x() + 130, y, 40);
        head(g, "cities", x() + 172, y, 28);
        head(g, "people", x() + 202, y, 26);
        head(g, "purses", x() + 230, y, 38);
        head(g, "lines", x() + 270, y, 26);
        head(g, "earned", x() + 298, y, 40);
        head(g, "toll", x() + 340, y, 34);
        head(g, "duty", x() + 376, y, 40);
        if (list.isEmpty()) {
            empty(g, "crusadecraft.economy.realms.empty", y + 14);
            return;
        }
        int rows = rows(14);
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        for (int i = scroll; i < list.size() && i < scroll + rows; i++) {
            CompoundTag r = list.get(i);
            int ry = y + 13 + (i - scroll) * ROW;
            g.fill(x(), ry, x() + 8, ry + 8, 0xFF000000 | r.getInt("color"));
            cell(g, r.getString("name"), x() + 12, ry, 116, r.getBoolean("mine") ? Ui.TEXT_TITLE : Ui.TEXT);
            cell(g, Long.toString(r.getLong("gold")), x() + 130, ry, 40, Ui.GOLD);
            cell(g, Long.toString(r.getLong("cities")), x() + 172, ry, 28, Ui.TEXT);
            cell(g, Long.toString(r.getLong("people")), x() + 202, ry, 26, Ui.TEXT);
            cell(g, Long.toString(r.getLong("purses")), x() + 230, ry, 38, Ui.TEXT);
            cell(g, Long.toString(r.getLong("lines")), x() + 270, ry, 26, Ui.TEXT);
            cell(g, Long.toString(r.getLong("earned")), x() + 298, ry, 40, Ui.TEXT);
            cell(g, Integer.toString(r.getInt("toll")), x() + 340, ry, 34, Ui.TEXT_MUTED);
            cell(g, r.getInt("duty") + " %", x() + 376, ry, 40, Ui.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (super.mouseScrolled(mx, my, delta)) return true;
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, size() - 1));
        // Les boutons d'une ligne la suivent.
        if (tab == Tab.CITIES || tab == Tab.CONTRACTS) rebuildWidgets();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
