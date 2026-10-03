package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.law.LawsView;
import fr.crusadecraft.core.realm.RealmView;
import fr.crusadecraft.core.treasury.TreasuryView;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.EconomyPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/**
 * Le menu royal, façon CK3 : un en-tête au blason du royaume, huit onglets, un contenu.
 *
 * <p>Chaque phase remplit un onglet. Ceux qui n'ont pas encore de contenu affichent un état vide.
 */
public class RealmScreen extends Screen {

    public enum Tab {
        ROYAUME(Items.GOLDEN_HELMET), CONSEIL(Items.LECTERN), VASSAUX(Items.SHIELD), DIPLOMATIE(Items.PAPER),
        TRESOR(Items.GOLD_INGOT), GUERRE(Items.IRON_SWORD), LOIS(Items.WRITABLE_BOOK), CHRONIQUES(Items.BOOK);

        final ItemStack icon;

        Tab(net.minecraft.world.item.Item icon) {
            this.icon = new ItemStack(icon);
        }

        Component label() {
            return Component.translatable("crusadecraft.tab." + name().toLowerCase(Locale.ROOT));
        }

        Component emptyTitle() {
            return Component.translatable("crusadecraft.tab." + name().toLowerCase(Locale.ROOT) + ".empty.title");
        }

        Component emptyBody() {
            return Component.translatable("crusadecraft.tab." + name().toLowerCase(Locale.ROOT) + ".empty.body");
        }
    }

    private static Tab lastTab = Tab.ROYAUME;

    private RealmView view;
    private Tab tab = lastTab;
    private int left, top, w, h;
    /** Données des onglets Trésor et Lois, demandées au serveur à l'ouverture de l'onglet. */
    private TreasuryView treasury;
    private LawsView laws;
    private fr.crusadecraft.core.diplomacy.DiplomacyView diplomacy;
    private fr.crusadecraft.core.diplomacy.ChanceryView chancery;
    private fr.crusadecraft.core.war.WarView war;
    private java.util.List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> news;
    private java.util.List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> chronicle;
    private int newsScroll;
    /** Onglet Chroniques : la chronique du règne (par défaut) ou les nouvelles du monde. */
    private static boolean showWorld;
    private Law selectedLaw;
    private int selectedLevel = -1;

    public RealmScreen(RealmView view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    /** Ouvre le menu directement sur un onglet. */
    public static RealmScreen withTab(RealmView view, Tab tab) {
        RealmScreen screen = new RealmScreen(view);
        screen.tab = tab;
        return screen;
    }

    public boolean isFor(RealmView other) {
        return view.id().equals(other.id());
    }

    public void refresh(RealmView newView) {
        this.view = newView;
    }

    public void setTreasury(TreasuryView v) {
        this.treasury = v;
    }

    public void setDiplomacy(fr.crusadecraft.core.diplomacy.DiplomacyView v) {
        this.diplomacy = v;
    }

    public void setChancery(fr.crusadecraft.core.diplomacy.ChanceryView v) {
        this.chancery = v;
    }

    public void setNews(java.util.List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> lines,
                        java.util.List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> chronicle) {
        this.news = lines;
        this.chronicle = chronicle;
        if (minecraft != null) rebuildWidgets();
    }

    public void setWar(fr.crusadecraft.core.war.WarView v) {
        this.war = v;
    }

    public void setLaws(LawsView v) {
        this.laws = v;
        if (minecraft != null) rebuildWidgets();
    }

    /** Présélectionne un niveau de loi (aperçu du banc d'essai visuel). */
    public void select(Law law, int level) {
        this.selectedLaw = law;
        this.selectedLevel = level;
        if (minecraft != null) rebuildWidgets();
    }

    /** Demande les données d'un onglet au serveur (rien hors connexion, comme au banc d'essai). */
    private void request(EconomyPackets.RequestTab.Tab which) {
        if (minecraft != null && minecraft.getConnection() != null) CrusadeNetwork.toServer(new EconomyPackets.RequestTab(which));
    }

    @Override
    protected void init() {
        w = Math.min(440, width - 16);
        h = Math.min(262, height - 16);
        left = (width - w) / 2;
        top = (height - h) / 2;
        if (tab == Tab.TRESOR && treasury == null) request(EconomyPackets.RequestTab.Tab.TREASURY);
        if (tab == Tab.LOIS && laws == null) request(EconomyPackets.RequestTab.Tab.LAWS);
        if (tab == Tab.DIPLOMATIE && diplomacy == null) request(EconomyPackets.RequestTab.Tab.DIPLOMACY);
        if (tab == Tab.VASSAUX && chancery == null) request(EconomyPackets.RequestTab.Tab.VASSALS);
        if (tab == Tab.GUERRE && war == null) request(EconomyPackets.RequestTab.Tab.WAR);
        if (tab == Tab.CHRONIQUES && news == null) request(EconomyPackets.RequestTab.Tab.NEWS);
        if (tab == Tab.LOIS) initLawButton();
        if (view.pendingEvents() > 0) {
            Button events = Button.builder(Component.translatable("crusadecraft.realm.events", view.pendingEvents()), b -> {
                if (minecraft != null && minecraft.getConnection() != null) {
                    CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.IntriguePackets.RequestEvent(null));
                }
            }).bounds(left + w - 128, top + 34, 120, 16).build();
            events.setTooltip(Tooltip.create(Component.translatable("crusadecraft.realm.events.tip")));
            addRenderableWidget(events);
        }
        int cy = top + 82, ch = h - 82 - 10;
        if (tab == Tab.CONSEIL) {
            Button intrigues = Button.builder(Component.translatable("crusadecraft.intrigue.open"), b -> {
                if (minecraft != null && minecraft.getConnection() != null) {
                    CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.IntriguePackets.IntrigueAction(
                            fr.crusadecraft.network.packet.IntriguePackets.IntrigueAction.Kind.REFRESH, null));
                }
            }).bounds(left + w - 8 - 112, cy + ch - 20, 104, 16).build();
            intrigues.setTooltip(Tooltip.create(Component.translatable("crusadecraft.intrigue.open.tip")));
            addRenderableWidget(intrigues);
        }
        if (tab == Tab.CHRONIQUES && (news != null || chronicle != null)) {
            Button reign = Button.builder(Component.translatable("crusadecraft.chronicle.reign"), b -> {
                showWorld = false;
                newsScroll = 0;
                rebuildWidgets();
            }).bounds(left + w - 8 - 4 - 150, cy + 4, 74, 14).build();
            reign.active = showWorld;
            Button world = Button.builder(Component.translatable("crusadecraft.chronicle.world"), b -> {
                showWorld = true;
                newsScroll = 0;
                rebuildWidgets();
            }).bounds(left + w - 8 - 4 - 74, cy + 4, 74, 14).build();
            world.active = !showWorld;
            addRenderableWidget(reign);
            addRenderableWidget(world);
        }
    }

    /** Le bouton qui propose (ou impose) le niveau de loi sélectionné. */
    private void initLawButton() {
        LawsView.LevelOption option = selectedOption();
        if (option == null) return;
        int cy = top + 82;
        int ch = h - 82 - 10;
        boolean force = !option.passes();
        Component label = force ? Component.translatable("crusadecraft.law.force", option.forceCost())
                : Component.translatable("crusadecraft.law.propose");
        Law law = selectedLaw;
        int level = selectedLevel;
        Button b = Button.builder(label, btn -> {
            CrusadeNetwork.toServer(new EconomyPackets.ProposeLaw(law, level, force));
            selectedLaw = null;
            selectedLevel = -1;
            rebuildWidgets();
        }).bounds(left + w - 8 - 150, cy + ch - 22, 142, 18).build();
        LawsView.LawLine line = lawLine(selectedLaw);
        b.active = option.unlocked() && line != null && line.cooldownLeft() == 0
                && (!force || laws.prestige() >= option.forceCost());
        b.setTooltip(Tooltip.create(Component.translatable(force ? "crusadecraft.law.force.tip" : "crusadecraft.law.propose.tip")));
        addRenderableWidget(b);
    }

    private LawsView.LawLine lawLine(Law law) {
        if (laws == null || law == null) return null;
        for (LawsView.LawLine l : laws.laws()) if (l.law() == law) return l;
        return null;
    }

    private LawsView.LevelOption selectedOption() {
        LawsView.LawLine line = lawLine(selectedLaw);
        if (line == null || selectedLevel < 0 || selectedLevel >= line.options().size() || selectedLevel == line.level()) return null;
        return line.options().get(selectedLevel);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, w, h);
        renderHeader(g);
        renderTabs(g, mouseX, mouseY);
        int cy = top + 82;
        int ch = h - 82 - 10;
        Ui.inset(g, left + 8, cy, w - 16, ch);
        if (tab == Tab.ROYAUME) {
            renderRealmTab(g, left + 8, cy, w - 16, ch, mouseX, mouseY);
        } else if (tab == Tab.CONSEIL && hasAnySeat()) {
            renderCouncilTab(g, left + 8, cy, w - 16, ch, mouseX, mouseY);
        } else if (tab == Tab.TRESOR) {
            renderTreasuryTab(g, left + 8, cy, w - 16, ch, mouseX, mouseY);
        } else if (tab == Tab.LOIS) {
            renderLawsTab(g, left + 8, cy, w - 16, ch, mouseX, mouseY);
        } else if (tab == Tab.DIPLOMATIE) {
            renderDiplomacyTab(g, left + 8, cy, w - 16, ch, mouseX, mouseY);
        } else if (tab == Tab.VASSAUX) {
            renderVassalsTab(g, left + 8, cy, w - 16, ch, mouseX, mouseY);
        } else if (tab == Tab.GUERRE) {
            renderWarTab(g, left + 8, cy, w - 16, ch, mouseX, mouseY);
        } else if (tab == Tab.CHRONIQUES && (news != null || chronicle != null)) {
            renderNewsTab(g, left + 8, cy, w - 16, ch);
        } else {
            renderEmpty(g, left + 8, cy, w - 16, ch);
        }
        super.render(g, mouseX, mouseY, partialTick);
        renderTabTooltips(g, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics g) {
        Ui.shield(g, font, left + 12, top + 10, 34, 42, view.color(), Ui.initial(view.name()));
        Component rankTitle = Component.translatable(view.rank().titleKey());
        Ui.scaled(g, font, Component.literal(view.name()), left + 56, top + 12, 1.6f, Ui.TEXT_TITLE, false);
        Component sub = Component.translatable("crusadecraft.realm.header.sub", rankTitle, view.house());
        Ui.text(g, font, sub, left + 56, top + 29, Ui.TEXT);
        if (!view.motto().isEmpty()) {
            Ui.text(g, font, Component.literal("« " + view.motto() + " »").withStyle(ChatFormatting.ITALIC),
                    left + 56, top + 41, Ui.TEXT_MUTED);
        }
        if (!view.state().isActive()) {
            Component state = Component.translatable(view.state().key());
            int sw = font.width(state);
            g.fill(left + w - sw - 18, top + 10, left + w - 8, top + 24, 0xC0601010);
            Ui.text(g, font, state, left + w - sw - 13, top + 13, 0xFFFFD0C0);
        }
    }

    private int tabWidth() {
        return (w - 16) / Tab.values().length;
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tw = tabWidth();
        int ty = top + 56;
        for (Tab t : Tab.values()) {
            int tx = left + 8 + t.ordinal() * tw;
            boolean selected = t == tab;
            boolean hover = Ui.inside(mouseX, mouseY, tx, ty, tw - 2, 24);
            g.fill(tx, ty, tx + tw - 2, ty + 26, selected ? Ui.PANEL_INNER : hover ? Ui.PANEL_HIGHLIGHT : 0xFF1F1813);
            g.renderOutline(tx, ty, tw - 2, selected ? 27 : 24, selected ? Ui.GOLD : Ui.GOLD_DARK);
            g.renderItem(t.icon, tx + (tw - 2) / 2 - 8, ty + 4);
            if (selected) g.fill(tx + 1, ty + 25, tx + tw - 3, ty + 27, Ui.PANEL_INNER);
        }
    }

    private void renderTabTooltips(GuiGraphics g, int mouseX, int mouseY) {
        int tw = tabWidth();
        int ty = top + 56;
        for (Tab t : Tab.values()) {
            int tx = left + 8 + t.ordinal() * tw;
            if (Ui.inside(mouseX, mouseY, tx, ty, tw - 2, 24)) {
                g.renderTooltip(font, t.label(), mouseX, mouseY);
            }
        }
    }

    private boolean hasAnySeat() {
        for (RealmView.Seat s : view.seats()) if (s.holder() != null) return true;
        return false;
    }

    /** Zones cliquables des sièges occupés, recalculées à chaque rendu. */
    private final java.util.List<Object[]> seatHits = new java.util.ArrayList<>();

    private void renderCouncilTab(GuiGraphics g, int x, int y, int cw, int ch, int mouseX, int mouseY) {
        seatHits.clear();
        int colW = (cw - 30) / 2;
        Ui.text(g, font, Component.translatable("crusadecraft.council.title"), x + 10, y + 8, Ui.TEXT_TITLE);
        Ui.text(g, font, Component.translatable("crusadecraft.council.mayors"), x + 20 + colW, y + 8, Ui.TEXT_TITLE);
        int ly = y + 22, ry = y + 22;
        for (RealmView.Seat seat : view.seats()) {
            boolean mayor = seat.post() == fr.crusadecraft.core.character.Post.MAIRE;
            int sx = mayor ? x + 20 + colW : x + 10;
            int sy = mayor ? ry : ly;
            if (sy + 17 > y + ch - 13) continue;
            boolean hover = seat.holder() != null && Ui.inside(mouseX, mouseY, sx, sy, colW, 17);
            g.fill(sx, sy, sx + colW, sy + 17, hover ? Ui.PANEL_HIGHLIGHT : 0xFF1C1612);
            g.renderOutline(sx, sy, colW, 17, Ui.GOLD_DARK);
            Component title = mayor ? Component.literal(seat.place())
                    : Component.translatable(seat.post().key(seat.female()));
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(title.getString(), colW / 2 - 6)), sx + 4, sy + 5, Ui.TEXT_MUTED);
            if (seat.holder() == null) {
                Ui.text(g, font, Component.translatable("crusadecraft.appoint.vacant"), sx + colW / 2, sy + 5, 0xFF6E6250);
            } else {
                String op = (seat.opinion() > 0 ? "+" : "") + seat.opinion();
                int opColor = seat.opinion() >= 20 ? Ui.GREEN : seat.opinion() <= -20 ? Ui.RED : 0xFFC99A3C;
                Ui.text(g, font, Component.literal(font.plainSubstrByWidth(seat.name(), colW / 2 - 28)), sx + colW / 2, sy + 5, Ui.TEXT);
                Ui.text(g, font, Component.literal(op), sx + colW - 4 - font.width(op), sy + 5, opColor);
                seatHits.add(new Object[]{seat.holder(), sx, sy, colW});
                if (hover) pendingTooltip = java.util.List.of(Component.translatable("crusadecraft.council.open", seat.name()));
            }
            if (mayor) ry += 19; else ly += 19;
        }
        String hint = font.plainSubstrByWidth(Component.translatable("crusadecraft.council.hint").getString(), cw - 130);
        Ui.text(g, font, Component.literal(hint), x + 10, y + ch - 11, Ui.TEXT_MUTED);
        if (pendingTooltip != null) {
            g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
            pendingTooltip = null;
        }
    }

    private void renderLoading(GuiGraphics g, int x, int y, int cw, int ch) {
        Ui.centered(g, font, Component.translatable("crusadecraft.tab.loading"), x + cw / 2, y + ch / 2 - 4, Ui.TEXT_MUTED);
    }

    private void renderTreasuryTab(GuiGraphics g, int x, int y, int cw, int ch, int mouseX, int mouseY) {
        if (treasury == null) {
            renderLoading(g, x, y, cw, ch);
            return;
        }
        boolean roomy = ch >= 150;
        List<Component> tip = TreasuryPanel.render(g, font, treasury, x, y, cw, roomy ? ch - 10 : ch, mouseX, mouseY);
        if (roomy) {
            String hint = font.plainSubstrByWidth(Component.translatable("crusadecraft.treasury.hint").getString(), cw - 16);
            Ui.text(g, font, Component.literal(hint), x + 8, y + ch - 11, Ui.TEXT_MUTED);
        }
        if (tip != null) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    private void renderDiplomacyTab(GuiGraphics g, int x, int y, int cw, int ch, int mouseX, int mouseY) {
        if (diplomacy == null) {
            renderLoading(g, x, y, cw, ch);
            return;
        }
        Ui.text(g, font, Component.translatable("crusadecraft.diplo.known", diplomacy.entries().size()), x + 8, y + 7, Ui.TEXT_TITLE);
        if (diplomacy.mailArrived() > 0) {
            Component mail = Component.translatable("crusadecraft.diplo.mail_waiting", diplomacy.mailArrived());
            Ui.text(g, font, mail, x + cw - 8 - font.width(mail), y + 7, 0xFFE0C060);
        }
        int ry = y + 20;
        int colW = (cw - 24) / 2;
        int i = 0;
        for (fr.crusadecraft.core.diplomacy.DiplomacyView.Entry e : diplomacy.entries()) {
            int col = i % 2, row = i / 2;
            int ex = x + 8 + col * (colW + 8), ey = ry + row * 14;
            if (ey > y + ch - 24) break;
            g.fill(ex, ey + 2, ex + 7, ey + 9, 0xFF000000 | e.color());
            String name = font.plainSubstrByWidth(fr.crusadecraft.client.screen.DiplomacyScreen.entryName(e).getString(), colW - 90);
            Ui.text(g, font, Component.literal(name), ex + 10, ey + 1, Ui.TEXT);
            Component st = Component.translatable(e.status().key());
            Ui.text(g, font, st, ex + colW - font.width(st) - (e.ai() ? 26 : 0), ey + 1, e.status().color());
            if (e.ai()) {
                String op = (e.opinion() > 0 ? "+" : "") + e.opinion();
                Ui.text(g, font, Component.literal(op), ex + colW - font.width(op), ey + 1, e.opinion() >= 20 ? Ui.GREEN : e.opinion() <= -20 ? Ui.RED : 0xFFC99A3C);
            }
            i++;
        }
        if (diplomacy.entries().isEmpty()) Ui.wrapped(g, font, Component.translatable("crusadecraft.diplo.nobody"), x + 8, ry, cw - 16, Ui.TEXT_MUTED);
        Ui.text(g, font, Component.translatable("crusadecraft.diplo.hint"), x + 8, y + ch - 11, Ui.TEXT_MUTED);
    }

    private void renderVassalsTab(GuiGraphics g, int x, int y, int cw, int ch, int mouseX, int mouseY) {
        if (chancery == null) {
            renderLoading(g, x, y, cw, ch);
            return;
        }
        Ui.text(g, font, Component.translatable("crusadecraft.chancery.rank", Component.translatable(chancery.rank().titleKey())), x + 8, y + 7, Ui.TEXT_TITLE);
        Component prov = Component.translatable("crusadecraft.chancery.provinces", chancery.provinces());
        Ui.text(g, font, prov, x + cw - 8 - font.width(prov), y + 7, Ui.TEXT_MUTED);
        int ry = y + 22;
        if (chancery.nextRank() != null) {
            Component next = Component.translatable("crusadecraft.chancery.next.short", Component.translatable(chancery.nextRank().titleKey()),
                    chancery.needProvinces(), chancery.needGold(), chancery.needPrestige());
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(next.getString(), cw - 16)), x + 8, ry, Ui.TEXT_MUTED);
            ry += 14;
        }
        Ui.text(g, font, Component.translatable("crusadecraft.chancery.vassals", chancery.vassals().size()), x + 8, ry, Ui.TEXT_TITLE);
        ry += 12;
        if (chancery.vassals().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.chancery.no_vassals"), x + 8, ry, cw - 16, Ui.TEXT_MUTED);
        }
        for (fr.crusadecraft.core.diplomacy.ChanceryView.Vassal v : chancery.vassals()) {
            if (ry > y + ch - 24) break;
            Component name = v.ai() ? fr.crusadecraft.core.world.AiNames.realm(v.rank(), v.label()) : Component.literal(v.label());
            g.fill(x + 8, ry + 2, x + 15, ry + 9, 0xFF000000 | v.color());
            Ui.text(g, font, name, x + 18, ry + 1, Ui.TEXT);
            Component terms = Component.translatable("crusadecraft.diplo.contract", v.tax(), v.levy())
                    .append(" · ").append(Component.translatable("crusadecraft.chancery.vassal.share", v.dailyShare()));
            Ui.text(g, font, terms, x + 18, ry + 11, Ui.TEXT_MUTED);
            ry += 23;
        }
        Ui.text(g, font, Component.translatable("crusadecraft.chancery.hint"), x + 8, y + ch - 11, Ui.TEXT_MUTED);
    }

    /** Les nouvelles du monde : guerres, paix, frontières qui bougent, alliances, coalitions. */
    private void renderNewsTab(GuiGraphics g, int x, int y, int cw, int ch) {
        java.util.List<fr.crusadecraft.network.packet.WorldPackets.NewsLine> news = showWorld ? this.news : this.chronicle;
        if (news == null) news = java.util.List.of();
        Ui.text(g, font, Component.translatable(showWorld ? "crusadecraft.news.title" : "crusadecraft.chronicle.title"), x + 8, y + 7, Ui.TEXT_TITLE);
        if (news.isEmpty()) {
            Ui.wrapped(g, font, Component.translatable(showWorld ? "crusadecraft.news.none" : "crusadecraft.chronicle.none"), x + 8, y + 24,
                    cw - 16, Ui.TEXT_MUTED);
            return;
        }
        int ry = y + 21;
        int bottom = y + ch - 4;
        int visible = 0;
        newsScroll = Math.max(0, Math.min(newsScroll, news.size() - 1));
        for (int i = newsScroll; i < news.size(); i++) {
            fr.crusadecraft.network.packet.WorldPackets.NewsLine line = news.get(i);
            Component day = Component.translatable("crusadecraft.war.log.day", line.day());
            int dw = font.width(day) + 4;
            java.util.List<net.minecraft.util.FormattedCharSequence> parts = font.split(line.text(), cw - 16 - dw);
            if (ry + parts.size() * 10 > bottom) break;
            Ui.text(g, font, day, x + 8, ry, Ui.TEXT_MUTED);
            for (net.minecraft.util.FormattedCharSequence part : parts) {
                g.drawString(font, part, x + 8 + dw, ry, Ui.TEXT, false);
                ry += 10;
            }
            ry += 3;
            visible++;
        }
        if (newsScroll + visible < news.size()) {
            Component more = Component.translatable("crusadecraft.news.more", news.size() - newsScroll - visible);
            Ui.text(g, font, more, x + cw - 8 - 158 - font.width(more), y + 7, Ui.TEXT_MUTED);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.CHRONIQUES && (news != null || chronicle != null)) {
            newsScroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** L'onglet Guerre : l'armée et les guerres en cours ; les ordres se donnent à la Table de guerre. */
    private void renderWarTab(GuiGraphics g, int x, int y, int cw, int ch, int mouseX, int mouseY) {
        if (war == null) {
            renderLoading(g, x, y, cw, ch);
            return;
        }
        Component men = Component.translatable("crusadecraft.war.army.men", war.men(), war.maxMen());
        Ui.text(g, font, men, x + 8, y + 7, Ui.TEXT_TITLE);
        Component state = war.raised() ? Component.translatable("crusadecraft.war.army.state.raised", war.upkeep())
                : Component.translatable("crusadecraft.war.army.state.home");
        Ui.text(g, font, state, x + cw - 8 - font.width(state), y + 7, war.raised() ? 0xFFE0C060 : Ui.TEXT_MUTED);
        int ry = y + 22;
        Ui.text(g, font, Component.translatable("crusadecraft.war.list.wars", war.wars().size()), x + 8, ry, Ui.TEXT_TITLE);
        ry += 13;
        if (war.wars().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.war.peaceful"), x + 8, ry, cw - 16, Ui.TEXT_MUTED);
        }
        for (fr.crusadecraft.core.war.WarView.WarLine line : war.wars()) {
            if (ry > y + ch - 34) break;
            fr.crusadecraft.core.war.WarView.Party enemy = WarScreen.enemy(line);
            g.fill(x + 8, ry + 2, x + 15, ry + 9, 0xFF000000 | enemy.color());
            Ui.text(g, font, Component.translatable("crusadecraft.war.against", WarScreen.partyName(enemy)), x + 18, ry + 1, Ui.TEXT);
            int score = WarScreen.myScore(line);
            Component sc = Component.translatable("crusadecraft.war.score", WarScreen.signed(score));
            Ui.text(g, font, sc, x + cw - 8 - font.width(sc), ry + 1, WarScreen.scoreColor(score));
            Component goal = line.target().isEmpty() ? Component.translatable(line.cb().key())
                    : Component.translatable("crusadecraft.war.goal", Component.translatable(line.cb().key()), line.target());
            Component meta = Component.translatable("crusadecraft.war.meta", goal, line.days());
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(meta.getString(), cw - 34)), x + 18, ry + 11, Ui.TEXT_MUTED);
            ry += 24;
        }
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(Component.translatable("crusadecraft.war.tab.hint").getString(), cw - 16)),
                x + 8, y + ch - 11, Ui.TEXT_MUTED);
    }

    private static final int CHIP_W = 64;
    private static final int LAW_ROW = 22;

    /** Zones cliquables des niveaux de loi : {loi, niveau, x, y}. */
    private final java.util.List<Object[]> lawHits = new java.util.ArrayList<>();

    private void renderLawsTab(GuiGraphics g, int x, int y, int cw, int ch, int mouseX, int mouseY) {
        lawHits.clear();
        if (laws == null) {
            renderLoading(g, x, y, cw, ch);
            return;
        }
        Ui.text(g, font, Component.translatable("crusadecraft.law.title"), x + 8, y + 7, Ui.TEXT_TITLE);
        Component meta = Component.translatable(laws.councilSize() == 0 ? "crusadecraft.law.meta.alone" : "crusadecraft.law.meta",
                laws.prestige(), laws.councilSize());
        Ui.text(g, font, meta, x + cw - 8 - font.width(meta), y + 7, Ui.TEXT_MUTED);
        List<Component> tip = null;
        int ry = y + 18;
        for (LawsView.LawLine line : laws.laws()) {
            Law law = line.law();
            int n = line.options().size();
            int chipsX = x + cw - 8 - n * (CHIP_W + 4) + 4;
            // Le niveau en vigueur se lit sur les pastilles ; sous le nom, seulement le délai s'il y en a un.
            // Une loi qui vient de changer s'affiche en ambre ; l'infobulle donne le délai.
            Component name = Component.translatable(law.key());
            float scale = Math.min(1f, (chipsX - x - 16) / (float) Math.max(1, font.width(name)));
            Ui.scaled(g, font, name, x + 8, ry + 7, scale, line.cooldownLeft() > 0 ? 0xFFE0A070 : Ui.TEXT, false);
            if (line.cooldownLeft() > 0 && Ui.inside(mouseX, mouseY, x + 8, ry + 4, chipsX - x - 16, 12)) {
                tip = List.of(Component.translatable("crusadecraft.law.cooldown", line.cooldownLeft()));
            }
            for (LawsView.LevelOption o : line.options()) {
                int cx = chipsX + o.level() * (CHIP_W + 4);
                boolean current = o.level() == line.level();
                boolean selected = law == selectedLaw && o.level() == selectedLevel;
                boolean hover = Ui.inside(mouseX, mouseY, cx, ry + 2, CHIP_W, 18);
                int bg = current ? 0xFF4A3A1E : selected ? Ui.PANEL_HIGHLIGHT : hover && o.unlocked() ? 0xFF2C231B : 0xFF1C1612;
                g.fill(cx, ry + 2, cx + CHIP_W, ry + 20, bg);
                g.renderOutline(cx, ry + 2, CHIP_W, 18, current || selected ? Ui.GOLD : Ui.GOLD_DARK);
                Component label = Component.translatable(law.levelKey(o.level()));
                String text = font.plainSubstrByWidth(label.getString(), CHIP_W - 6);
                int color = !o.unlocked() ? 0xFF6E6250 : current ? Ui.TEXT_TITLE : Ui.TEXT;
                Ui.centered(g, font, Component.literal(text), cx + CHIP_W / 2, ry + 7, color);
                lawHits.add(new Object[]{law, o.level(), cx, ry + 2});
                if (hover) tip = levelTooltip(law, line, o);
            }
            ry += LAW_ROW;
        }
        LawsView.LevelOption option = selectedOption();
        if (option != null) {
            Component level = Component.translatable(selectedLaw.levelKey(selectedLevel));
            Component summary = option.approve().isEmpty() && option.reject().isEmpty()
                    ? Component.translatable("crusadecraft.law.summary.alone", level)
                    : Component.translatable(option.passes() ? "crusadecraft.law.summary.passes" : "crusadecraft.law.summary.fails",
                            level, option.approve().size(), option.reject().size());
            Ui.wrapped(g, font, summary, x + 8, y + ch - 22, cw - 175, option.passes() ? Ui.TEXT : 0xFFE0A070);
        } else {
            Ui.wrapped(g, font, Component.translatable("crusadecraft.law.hint"), x + 8, y + ch - 22, cw - 16, Ui.TEXT_MUTED);
        }
        if (tip != null) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    private List<Component> levelTooltip(Law law, LawsView.LawLine line, LawsView.LevelOption o) {
        List<Component> tip = new java.util.ArrayList<>();
        tip.add(Component.translatable(law.levelKey(o.level())).withStyle(ChatFormatting.GOLD));
        for (String part : Component.translatable(law.levelKey(o.level()) + ".desc").getString().split("\\n")) {
            tip.add(Component.literal(part).withStyle(ChatFormatting.GRAY));
        }
        if (o.level() == line.level()) {
            tip.add(Component.translatable("crusadecraft.law.in_force").withStyle(ChatFormatting.GREEN));
            return tip;
        }
        if (!o.unlocked()) {
            tip.add(Component.translatable("crusadecraft.law.locked.tip",
                    Component.translatable(researchKey(law, o.level()))).withStyle(ChatFormatting.RED));
            return tip;
        }
        if (!o.approve().isEmpty()) tip.add(Component.translatable("crusadecraft.law.for", String.join(", ", o.approve())).withStyle(ChatFormatting.GREEN));
        if (!o.reject().isEmpty()) tip.add(Component.translatable("crusadecraft.law.against", String.join(", ", o.reject())).withStyle(ChatFormatting.RED));
        if (o.approve().isEmpty() && o.reject().isEmpty()) tip.add(Component.translatable("crusadecraft.law.no_council").withStyle(ChatFormatting.GRAY));
        if (!o.passes()) tip.add(Component.translatable("crusadecraft.law.force.cost", o.forceCost()).withStyle(ChatFormatting.YELLOW));
        if (line.cooldownLeft() > 0) tip.add(Component.translatable("crusadecraft.law.cooldown", line.cooldownLeft()).withStyle(ChatFormatting.RED));
        tip.add(Component.translatable("crusadecraft.law.click").withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    /** Clé du nom court de la recherche qui ouvre un niveau de loi. */
    private static String researchKey(Law law, int level) {
        var research = law.levels().get(level).research();
        return research == null ? "" : "crusadecraft.research.short." + research.getPath().replace("effects/", "");
    }

    private void renderEmpty(GuiGraphics g, int x, int y, int cw, int ch) {
        int cx = x + cw / 2;
        Ui.scaled(g, font, tab.label(), cx, y + 14, 1.3f, Ui.TEXT_TITLE, true);
        Ui.separator(g, x + cw / 4, y + 32, cw / 2, Ui.GOLD_DARK);
        g.renderItem(tab.icon, cx - 8, y + 44);
        Ui.centered(g, font, tab.emptyTitle(), cx, y + 68, Ui.TEXT);
        Ui.wrappedCentered(g, font, tab.emptyBody(), cx, y + 84, cw - 60, Ui.TEXT_MUTED);
    }

    private void renderRealmTab(GuiGraphics g, int x, int y, int cw, int ch, int mouseX, int mouseY) {
        int col = x + 10;
        int line = y + 10;
        rowWidth = cw / 2 - 16;
        Component holder = Component.translatable(view.rank().holderKey(view.feminineTitle()));
        row(g, "crusadecraft.realm.ruler", Component.literal(view.rulerName()).append(", ").append(holder), col, line);
        row(g, "crusadecraft.realm.house", Component.literal(view.house()), col, line += 12);
        ColonyInfo capital = view.capital();
        row(g, "crusadecraft.realm.capital", Component.literal(capital == null || capital.isMissing() ? "—" : capital.name()), col, line += 12);
        long age = Math.max(0, view.currentDay() - view.foundedDay());
        row(g, "crusadecraft.realm.founded", Component.translatable("crusadecraft.realm.founded.value", view.foundedDay(), age), col, line += 12);
        row(g, "crusadecraft.realm.state", Component.translatable(view.state().key()), col, line += 12);
        row(g, "crusadecraft.realm.population", Component.literal(String.valueOf(view.totalCitizens())), col, line += 12);

        // Ressources du souverain
        int ry = line + 20;
        Ui.text(g, font, Component.translatable("crusadecraft.realm.resources"), col, ry, Ui.TEXT_TITLE);
        ry += 12;
        resource(g, new ItemStack(Items.GOLD_NUGGET), view.gold(), "crusadecraft.resource.gold", col, ry, mouseX, mouseY);
        resource(g, new ItemStack(Items.GOLDEN_HELMET), view.prestige(), "crusadecraft.resource.prestige", col + 46, ry, mouseX, mouseY);
        resource(g, new ItemStack(Items.CANDLE), view.piety(), "crusadecraft.resource.piety", col + 92, ry, mouseX, mouseY);
        resource(g, new ItemStack(Items.WITHER_SKELETON_SKULL), view.dread(), "crusadecraft.resource.dread", col + 138, ry, mouseX, mouseY);

        // Domaine
        int dx = x + cw / 2 + 4;
        int dw = cw / 2 - 14;
        Ui.text(g, font, Component.translatable("crusadecraft.realm.domain"), dx, y + 10, Ui.TEXT_TITLE);
        int dy = y + 24;
        List<ColonyInfo> colonies = view.colonies();
        int rowH = 40;
        int shown = 0;
        for (ColonyInfo c : colonies) {
            int remainingAfter = colonies.size() - shown - 1;
            if (dy + rowH > y + ch) break;
            if (remainingAfter > 0 && dy + rowH + 10 > y + ch) break;
            g.fill(dx, dy, dx + dw, dy + rowH - 4, 0xFF1C1612);
            g.renderOutline(dx, dy, dw, rowH - 4, Ui.GOLD_DARK);
            if (c.isMissing()) {
                Ui.text(g, font, Component.translatable("crusadecraft.realm.colony.missing", c.ref().toString()), dx + 4, dy + 4, Ui.RED);
            } else {
                Component role = Component.translatable(shown == 0 ? "crusadecraft.realm.colony.capital" : "crusadecraft.realm.colony.province");
                Ui.text(g, font, Component.literal(font.plainSubstrByWidth(c.name(), dw - font.width(role) - 14)), dx + 4, dy + 4, Ui.TEXT);
                Ui.text(g, font, role, dx + dw - font.width(role) - 4, dy + 4, Ui.TEXT_MUTED);
                Component stats = Component.translatable("crusadecraft.realm.colony.stats", c.townHallLevel(), c.citizens(), c.maxCitizens());
                Ui.text(g, font, Component.literal(font.plainSubstrByWidth(stats.getString(), dw - 8)), dx + 4, dy + 15, Ui.TEXT_MUTED);
                Component label = Component.translatable("crusadecraft.realm.colony.happiness.short");
                Ui.text(g, font, label, dx + 4, dy + 25, Ui.TEXT_MUTED);
                int bx = dx + 8 + font.width(label);
                int bw = dw - (bx - dx) - 6;
                Ui.bar(g, bx, dy + 26, bw, 6, c.happiness() / 10.0, Ui.happinessColor(c.happiness()));
                if (Ui.inside(mouseX, mouseY, dx, dy + 23, dw, 12)) {
                    pendingTooltip = List.of(Component.translatable("crusadecraft.realm.colony.happiness",
                            String.format(Locale.ROOT, "%.1f", c.happiness())));
                }
            }
            dy += rowH;
            shown++;
        }
        if (shown < colonies.size()) {
            Ui.wrapped(g, font, (colonies.size() - shown == 1 ? Component.translatable("crusadecraft.realm.colony.more.one")
                    : Component.translatable("crusadecraft.realm.colony.more", colonies.size() - shown)), dx + 2, dy, dw - 4, Ui.TEXT_MUTED);
        }
        if (pendingTooltip != null) {
            g.renderComponentTooltip(font, pendingTooltip, mouseX, mouseY);
            pendingTooltip = null;
        }
    }

    private List<Component> pendingTooltip;

    private int rowWidth = 200;

    private void row(GuiGraphics g, String labelKey, Component value, int x, int y) {
        Component label = Component.translatable(labelKey);
        Ui.text(g, font, label, x, y, Ui.TEXT_MUTED);
        int room = rowWidth - 70;
        String shown = font.plainSubstrByWidth(value.getString(), room);
        Ui.text(g, font, Component.literal(shown), x + 70, y, Ui.TEXT);
    }

    private void resource(GuiGraphics g, ItemStack icon, long value, String key, int x, int y, int mouseX, int mouseY) {
        g.renderItem(icon, x, y);
        Ui.text(g, font, Component.literal(String.valueOf(value)), x + 18, y + 5, Ui.TEXT);
        if (Ui.inside(mouseX, mouseY, x, y, 42, 16)) {
            pendingTooltip = List.of(Component.translatable(key),
                    Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.CONSEIL) {
            for (Object[] hit : seatHits) {
                if (Ui.inside(mouseX, mouseY, (int) hit[1], (int) hit[2], (int) hit[3], 17)) {
                    fr.crusadecraft.network.CrusadeNetwork.toServer(new fr.crusadecraft.network.packet.RequestCharacterPacket(
                            (fr.crusadecraft.core.character.CitizenRef) hit[0], fr.crusadecraft.network.packet.RequestCharacterPacket.Mode.SHEET));
                    return true;
                }
            }
        }
        if (tab == Tab.LOIS) {
            for (Object[] hit : lawHits) {
                if (Ui.inside(mouseX, mouseY, (int) hit[2], (int) hit[3], CHIP_W, 18)) {
                    Law law = (Law) hit[0];
                    int level = (int) hit[1];
                    LawsView.LawLine line = lawLine(law);
                    if (line != null && level != line.level()) {
                        selectedLaw = law;
                        selectedLevel = level;
                        rebuildWidgets();
                    }
                    return true;
                }
            }
        }
        int tw = tabWidth();
        int ty = top + 56;
        for (Tab t : Tab.values()) {
            int tx = left + 8 + t.ordinal() * tw;
            if (Ui.inside(mouseX, mouseY, tx, ty, tw - 2, 24)) {
                tab = t;
                lastTab = t;
                if (t == Tab.TRESOR) request(EconomyPackets.RequestTab.Tab.TREASURY);
                if (t == Tab.LOIS) request(EconomyPackets.RequestTab.Tab.LAWS);
                if (t == Tab.DIPLOMATIE) request(EconomyPackets.RequestTab.Tab.DIPLOMACY);
                if (t == Tab.VASSAUX) request(EconomyPackets.RequestTab.Tab.VASSALS);
                if (t == Tab.GUERRE) request(EconomyPackets.RequestTab.Tab.WAR);
                if (t == Tab.CHRONIQUES) request(EconomyPackets.RequestTab.Tab.NEWS);
                selectedLaw = null;
                selectedLevel = -1;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (fr.crusadecraft.client.KeyBindings.REALM_MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
