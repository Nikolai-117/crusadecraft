package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.ColonyInfo;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Post;
import fr.crusadecraft.core.council.CouncilTask;
import fr.crusadecraft.core.council.CouncilView;
import fr.crusadecraft.core.diplomacy.ChanceryView;
import fr.crusadecraft.core.law.Law;
import fr.crusadecraft.core.law.LawsView;
import fr.crusadecraft.core.realm.RealmView;
import fr.crusadecraft.core.realm.TitleRank;
import fr.crusadecraft.core.treasury.TreasuryView;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.client.map.ClientMapData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.CourtLifePackets;
import fr.crusadecraft.network.packet.CourtPackets;
import fr.crusadecraft.network.packet.DiplomacyPackets;
import fr.crusadecraft.network.packet.EconomyPackets;
import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Le menu royal (P61), d'après la maquette « Varenhold » de Romain : une barre en haut (le blason, les ressources, le jour), un rail d'icônes à gauche,
 * et deux panneaux. Royaume, Conseil, Lois et Chancellerie sont composés comme la maquette ; Trésor, Armée, Voisins et Chronique reprennent les
 * contenus d'avant dans un panneau large ; Domaine et Maison ouvrent leurs propres écrans.
 *
 * <p>Tout se dessine au rendu, et les zones cliquables se notent en même temps : les données arrivent du serveur page par page, et la mise en page
 * suit ce qui est arrivé sans reconstruire de boutons.
 */
public class RealmScreen extends Screen {

    public enum Tab {
        ROYAUME, CONSEIL, VASSAUX, DIPLOMATIE, TRESOR, GUERRE, LOIS, CHRONIQUES;

        Component label() {
            return Component.translatable("crusadecraft.tab." + name().toLowerCase(Locale.ROOT));
        }
    }

    /** Une entrée du rail : une page du menu, ou un écran à part (Domaine, Maison). */
    private record Rail(Kit.Icon icon, String key, Tab tab, String tip) {}

    private static final Rail[] RAIL_ITEMS = {
            new Rail(Kit.Icon.ROYAUME, "royaume", Tab.ROYAUME, null),
            new Rail(Kit.Icon.CONSEIL, "conseil", Tab.CONSEIL, null),
            new Rail(Kit.Icon.LOIS, "lois", Tab.LOIS, null),
            new Rail(Kit.Icon.CHANCELLERIE, "chancellerie", Tab.VASSAUX, null),
            new Rail(Kit.Icon.DOMAINE, "domaine", null, "crusadecraft.hub.rail.domaine.tip"),
            new Rail(Kit.Icon.TRESOR, "tresor", Tab.TRESOR, null),
            new Rail(Kit.Icon.ARMEE, "armee", Tab.GUERRE, null),
            new Rail(Kit.Icon.LETTRE, "voisins", Tab.DIPLOMATIE, null),
            new Rail(Kit.Icon.MAISON, "maison", null, "crusadecraft.hub.rail.maison.tip"),
            new Rail(Kit.Icon.CHRONIQUE, "chronique", Tab.CHRONIQUES, null),
    };

    private static final int BAR = 22, RAIL = 30;
    private static final int BACKDROP = 0xFF0C0F12;
    private static final int INK_OK = 0xFF2E6B2A, INK_KO = 0xFFA0321F, INK_SOFT = 0xFF6E6250;
    private static Tab lastTab = Tab.ROYAUME;
    private static boolean showWorld;

    private RealmView view;
    private Tab tab = lastTab;
    private TreasuryView treasury;
    private LawsView laws;
    private fr.crusadecraft.core.diplomacy.DiplomacyView diplomacy;
    private ChanceryView chancery;
    private fr.crusadecraft.core.war.WarView war;
    private CouncilView council;
    private List<WorldPackets.NewsLine> news, chronicle;
    private int newsScroll;

    private Law focusLaw, selectedLaw;
    private int selectedLevel = -1;
    private Post selectedPost;
    private int selectedMayor = -1;
    private boolean mayorsTab;

    private final KingdomMap map = new KingdomMap();
    private int lx, ly, lw, lh, rx, ry, rw, rh;

    /** Les zones cliquables de la dernière image, et l'infobulle à poser par-dessus tout. */
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private final List<Hit> hits = new ArrayList<>();
    private List<Component> tip;
    private int mx, my;

    public RealmScreen(RealmView view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    /** Ouvre le menu directement sur une page. */
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

    public void setChancery(ChanceryView v) {
        this.chancery = v;
        // Un titre vient de changer : la barre du haut et l'en-tête le suivent.
        if (v.rank() != view.rank()) send(new fr.crusadecraft.network.packet.RequestRealmPacket(fr.crusadecraft.network.packet.OpenRealmScreenPacket.Target.MENU));
    }

    public void setNews(List<WorldPackets.NewsLine> lines, List<WorldPackets.NewsLine> chronicle) {
        this.news = lines;
        this.chronicle = chronicle;
    }

    public void setWar(fr.crusadecraft.core.war.WarView v) {
        this.war = v;
    }

    public void setLaws(LawsView v) {
        this.laws = v;
    }

    /** Le conseil, avec les tâches : demandé à l'ouverture de la page Conseil. */
    public void setCouncil(CouncilView v) {
        this.council = v;
        if (selectedPost == null && v.focus() != null && v.focus().isCouncil()) selectedPost = v.focus();
    }

    /** Présélectionne un niveau de loi (aperçu du banc d'essai visuel). */
    public void select(Law law, int level) {
        this.focusLaw = law;
        this.selectedLaw = law;
        this.selectedLevel = level;
    }

    private void send(Object packet) {
        if (minecraft != null && minecraft.getConnection() != null) CrusadeNetwork.toServer(packet);
    }

    private void request(EconomyPackets.RequestTab.Tab which) {
        send(new EconomyPackets.RequestTab(which));
    }

    /** Ce qu'il faut demander au serveur pour une page. */
    private void requestFor(Tab t) {
        switch (t) {
            case ROYAUME -> {
                request(EconomyPackets.RequestTab.Tab.VASSALS);
                request(EconomyPackets.RequestTab.Tab.LAWS);
                requestMap();
            }
            case CONSEIL -> send(new CourtPackets.CouncilAction(CourtPackets.CouncilAction.Kind.OPEN, null, null, null));
            case VASSAUX -> {
                request(EconomyPackets.RequestTab.Tab.VASSALS);
                requestMap();
            }
            case LOIS -> request(EconomyPackets.RequestTab.Tab.LAWS);
            case TRESOR -> request(EconomyPackets.RequestTab.Tab.TREASURY);
            case DIPLOMATIE -> request(EconomyPackets.RequestTab.Tab.DIPLOMACY);
            case GUERRE -> request(EconomyPackets.RequestTab.Tab.WAR);
            case CHRONIQUES -> request(EconomyPackets.RequestTab.Tab.NEWS);
        }
    }

    private void requestMap() {
        if (ClientMapData.view() == null) ClientMapData.refreshIfStale(0);
        else ClientMapData.refreshIfStale(60_000);
    }

    private void setTab(Tab t) {
        if (t != tab) {
            selectedLaw = null;
            selectedLevel = -1;
        }
        tab = t;
        lastTab = t;
        newsScroll = 0;
        requestFor(t);
    }

    private void activate(int index) {
        Rail r = RAIL_ITEMS[index];
        if (r.tab() != null) setTab(r.tab());
        else if (r.icon() == Kit.Icon.DOMAINE) send(new fr.crusadecraft.network.packet.CityPackets.Request());
        else send(CourtLifePackets.HouseAction.of(CourtLifePackets.HouseAction.Kind.REFRESH));
    }

    /** Vrai tant que le menu a baissé l'échelle d'interface : il la rend en se fermant. */
    private boolean scaled;

    /**
     * La maquette est dessinée pour 640 x 360. Sur un écran 1080p en échelle automatique, l'interface n'en fait que 480 x 270 : le menu se
     * dessine alors une échelle en dessous (un nombre entier, pour que les pixels restent nets), et la rend en se fermant.
     */
    private void adaptScale() {
        if (minecraft == null || !fr.crusadecraft.config.CrusadeConfig.CLIENT.hubScale.get()) return;
        com.mojang.blaze3d.platform.Window win = minecraft.getWindow();
        int current = (int) win.getGuiScale(), s = current;
        while (s > 1 && (win.getWidth() / s < 600 || win.getHeight() / s < 340)) s--;
        if (s != current) {
            win.setGuiScale(s);
            width = win.getGuiScaledWidth();
            height = win.getGuiScaledHeight();
            scaled = true;
        }
    }

    private void restoreScale() {
        if (!scaled || minecraft == null) return;
        scaled = false;
        com.mojang.blaze3d.platform.Window win = minecraft.getWindow();
        win.setGuiScale(win.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode()));
    }

    @Override
    protected void init() {
        adaptScale();
        int areaX = RAIL + 4, areaY = BAR + 6;
        int areaW = width - areaX - 4, areaH = height - areaY - 4;
        lw = Mth.clamp((int) (areaW * 0.37), 170, 250);
        lx = areaX;
        ly = areaY;
        lh = areaH;
        rx = lx + lw + 6;
        ry = areaY;
        rw = width - 4 - rx;
        rh = areaH;
        requestFor(tab);
    }

    @Override
    public void removed() {
        map.release();
        restoreScale();
        super.removed();
    }

    // ------------------------------------------------------------------ petits outils

    private boolean over(int x, int y, int w, int h) {
        return Ui.inside(mx, my, x, y, w, h);
    }

    private void click(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private void tip(int x, int y, int w, int h, Component... lines) {
        if (over(x, y, w, h)) tip = List.of(lines);
    }

    private void text(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font, c, x, y, color, false);
    }

    private void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    private void right(GuiGraphics g, Component c, int rightX, int y, int color) {
        g.drawString(font, c, rightX - font.width(c), y, color, false);
    }

    private static MutableComponent bold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.BOLD);
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    private static MutableComponent hub(String key, Object... args) {
        return Component.translatable("crusadecraft.hub." + key, args);
    }

    /** Coupe un texte trop long, avec des points de suspension. */
    private String fit(String s, int w) {
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
    }

    /** Comme {@link #fit}, mais mesuré dans la gothique, plus large que la police du jeu. */
    private Component fitGothic(String s, int w) {
        if (Kit.titleWidth(font, Component.literal(s), 1) <= w) return Component.literal(s);
        String cut = s;
        while (!cut.isEmpty() && Kit.titleWidth(font, Component.literal(cut + "…"), 1) > w) cut = cut.substring(0, cut.length() - 1);
        return Component.literal(cut + "…");
    }

    private Component fitBold(String s, int w) {
        if (font.width(bold(s)) <= w) return bold(s);
        String cut = s;
        while (!cut.isEmpty() && font.width(bold(cut + "…")) > w) cut = cut.substring(0, cut.length() - 1);
        return bold(cut + "…");
    }

    private int wrap(GuiGraphics g, Component c, int x, int y, int w, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(c, Math.max(10, w));
        int n = Math.min(lines.size(), maxLines);
        for (int i = 0; i < n; i++) g.drawString(font, lines.get(i), x, y + i * 10, color, false);
        return n;
    }

    /** « de Varenhold », « d'Ambrevent ». */
    private static Component of(String name) {
        boolean vowel = !name.isEmpty() && "AEIOUYÉÈÊÂÎÔÛaeiouyéèêâîôû".indexOf(name.charAt(0)) >= 0;
        return hub(vowel ? "of.vowel" : "of", name);
    }

    /** Les milliers séparés par une espace ordinaire : la fine insécable n'est pas dans la police du jeu. */
    private static String group(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) out.append(' ');
            out.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + out;
    }

    private static String signed(int v) {
        return (v > 0 ? "+" : "") + v;
    }

    private static int opinionColor(int opinion) {
        return opinion >= 20 ? Kit.SINOPLE : opinion <= -20 ? Kit.GUEULES : Kit.AMBRE;
    }

    private static Component opinionWord(int opinion) {
        String k = opinion <= -50 ? "hostile" : opinion <= -20 ? "wary" : opinion < 25 ? "reserved" : opinion < 40 ? "friendly" : "loyal";
        return hub("opinion." + k);
    }

    private static boolean mouseDown() {
        return GLFW.glfwGetMouseButton(net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    /** Un bouton dessiné du kit, et sa zone cliquable s'il est actif. */
    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean active, boolean primary, Runnable action, Component tooltip) {
        boolean hover = over(x, y, w, h);
        Kit.State state = !active ? Kit.State.DISABLED : hover && mouseDown() ? Kit.State.PRESSED : hover ? Kit.State.HOVER : Kit.State.NORMAL;
        Kit.button(g, x, y, w, h, state, primary);
        String s = label.getString().length() <= 2 ? label.getString() : fit(label.getString(), w - 6);
        g.drawString(font, s, x + (w - font.width(s)) / 2, y + (h - 8) / 2 + (state == Kit.State.PRESSED ? 1 : 0), Kit.buttonText(state, primary), false);
        if (active) click(x, y, w, h, action);
        if (tooltip != null) tip(x, y, w, h, tooltip);
    }

    private void iconButton(GuiGraphics g, int x, int y, int size, Kit.Icon icon, int color, Runnable action, Component tooltip) {
        boolean hover = over(x, y, size, size);
        Kit.State state = hover && mouseDown() ? Kit.State.PRESSED : hover ? Kit.State.HOVER : Kit.State.NORMAL;
        Kit.button(g, x, y, size, size, state, false);
        Kit.icon(g, icon, x + (size - 10) / 2, y + (size - 10) / 2, hover ? Kit.SURVOL_TEXTE : color);
        click(x, y, size, size, action);
        if (tooltip != null) tip(x, y, size, size, tooltip);
    }

    /** Un lien doré souligné, aligné à droite sur {@code rightX}. */
    private void link(GuiGraphics g, Component label, int x, int y, Runnable action) {
        int w = font.width(label);
        boolean hover = over(x, y - 1, w, 10);
        text(g, label.copy().withStyle(ChatFormatting.UNDERLINE), x, y, hover ? Kit.SURVOL_TEXTE : Kit.OR);
        click(x, y - 1, w, 10, action);
    }

    private void sectionLink(GuiGraphics g, Component title, int x, int y, int w, Component label, Runnable action) {
        int w2 = font.width(label);
        Kit.section(g, font, title, x, y, w - w2 - 6, null, 0);
        link(g, label, x + w - w2, y + 1, action);
    }

    private void loading(GuiGraphics g, int x, int y) {
        text(g, tr("crusadecraft.tab.loading"), x, y, Kit.GRIS_DISCRET);
    }

    /** L'en-tête d'un panneau de gauche : un cartouche avec l'icône de la page, le titre en gothique, une ligne d'explication, le filet. */
    private void pageHeader(GuiGraphics g, Kit.Icon icon, Component title, Component sub, int x, int y, int w) {
        pageHeader(g, icon, title, sub, x, y, w, w);
    }

    private void pageHeader(GuiGraphics g, Kit.Icon icon, Component title, Component sub, int x, int y, int w, int ruleW) {
        g.fill(x, y, x + 26, y + 26, Kit.CONTOUR);
        g.fill(x + 1, y + 1, x + 25, y + 25, Kit.PUITS);
        g.renderOutline(x + 1, y + 1, 24, 24, Kit.FILET_OR);
        Kit.icon(g, icon, x + 8, y + 8, Kit.OR);
        headerText(g, title, sub, x + 32, y, x + w);
        Kit.rule(g, x, y + 33, ruleW);
    }

    private void headerText(GuiGraphics g, Component title, Component sub, int tx, int y, int limit) {
        int room = limit - tx;
        boolean big = Kit.titleWidth(font, title, 2) <= room;
        if (big) Kit.title(g, font, title, tx, y + 3, 2, Kit.OR, true);
        else Kit.title(g, font, fitGothic(title.getString(), room), tx, y + 5, 1, Kit.OR, true);
        if (sub != null) text(g, fit(sub.getString(), room), tx, big ? y + 22 : y + 17, Kit.GRIS_PERLE);
    }

    private record LegendItem(int color, Component label, boolean outline) {}

    private int legendWidth(List<LegendItem> items) {
        int w = 0;
        for (LegendItem it : items) w += 11 + font.width(it.label()) + 8;
        return Math.max(0, w - 8);
    }

    private void legend(GuiGraphics g, List<LegendItem> items, int x, int y) {
        for (LegendItem it : items) {
            g.fill(x, y, x + 8, y + 8, Kit.CONTOUR);
            if (it.outline()) g.renderOutline(x + 1, y + 1, 6, 6, it.color());
            else g.fill(x + 1, y + 1, x + 7, y + 7, it.color());
            text(g, it.label(), x + 11, y, Kit.GRIS_PERLE);
            x += 11 + font.width(it.label()) + 8;
        }
    }

    /** Le titre d'un panneau de droite, sa légende, et la carte dessous. Renvoie le bas de la carte. */
    private int mapBlock(GuiGraphics g, Component title, List<LegendItem> legend, int bottomH, Component overlay) {
        int x = rx + 10, w = rw - 20;
        int lg = legendWidth(legend);
        boolean big = Kit.titleWidth(font, title, 2) + lg + 16 <= w;
        boolean beside = Kit.titleWidth(font, title, 1) + lg + 16 <= w;
        Kit.title(g, font, title, x, ry + (big ? 12 : 13), big ? 2 : 1, Kit.OR, true);
        if (beside) legend(g, legend, x + w - lg, ry + 11);
        int top = ry + (big ? 32 : 26);
        int mh = Math.max(50, ry + rh - 8 - bottomH - top);
        map.draw(g, font, x, top, w, mh);
        int plateY = top + mh - 14;
        if (!beside) {
            // Plus large que la carte : la légende se coupe en deux lignes.
            List<List<LegendItem>> rowsOf = new ArrayList<>();
            List<LegendItem> row = new ArrayList<>();
            for (LegendItem it : legend) {
                row.add(it);
                if (legendWidth(row) > w - 12 && row.size() > 1) {
                    row.remove(row.size() - 1);
                    rowsOf.add(row);
                    row = new ArrayList<>(List.of(it));
                }
            }
            rowsOf.add(row);
            for (int i = rowsOf.size() - 1; i >= 0; i--) {
                int rw2 = legendWidth(rowsOf.get(i));
                g.fill(x + 3, plateY, x + 7 + rw2, plateY + 11, 0xE00F1216);
                legend(g, rowsOf.get(i), x + 5, plateY + 2);
                plateY -= 12;
            }
            plateY -= 1;
        }
        if (overlay != null) {
            int ow = font.width(overlay) + 8;
            g.fill(x + 3, plateY, x + 3 + ow, plateY + 11, 0xF01B2026);
            g.renderOutline(x + 3, plateY, ow, 11, Kit.CONTOUR);
            text(g, overlay, x + 7, plateY + 2, Kit.CREME);
        }
        return top + mh;
    }

    // ------------------------------------------------------------------ le rendu

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        mx = mouseX;
        my = mouseY;
        hits.clear();
        tip = null;
        g.fill(0, 0, width, height, BACKDROP);
        Kit.grain(g, 0, BAR, width, height - BAR);
        renderBar(g);
        renderRail(g);
        switch (tab) {
            case ROYAUME -> renderRealm(g);
            case CONSEIL -> renderCouncil(g);
            case LOIS -> renderLaws(g);
            case VASSAUX -> renderChancery(g);
            default -> renderWide(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (tip != null) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    /** La barre du haut : le blason et le nom, les ressources, les alertes, le jour, les réglages et la croix. */
    private void renderBar(GuiGraphics g) {
        g.fill(0, 0, width, BAR, Kit.ENTETE);
        g.fill(0, BAR - 2, width, BAR - 1, 0xFF1A1F25);
        g.fill(0, BAR - 1, width, BAR, Kit.CONTOUR);
        // À droite d'abord : ce qui reste de place va aux ressources.
        int x = width - 4 - 16;
        iconButton(g, x, 3, 16, Kit.Icon.FERMER, Kit.CREME, this::onClose, hub("close"));
        x -= 18;
        iconButton(g, x, 3, 16, Kit.Icon.REGLAGES, Kit.CREME,
                () -> send(new fr.crusadecraft.network.packet.SettingsPackets.Request("", "")), tr("crusadecraft.settings.open.tip"));
        x -= 6;
        Kit.divider(g, x, 4, 14);
        Component day = hub("day", view.currentDay());
        x -= 6 + font.width(day);
        text(g, day, x, 7, Kit.CREME);
        x -= 6;
        Kit.divider(g, x, 4, 14);
        x -= 4 + 16;
        iconButton(g, x, 3, 16, Kit.Icon.DRAPEAU, Kit.OR, () -> send(fr.crusadecraft.network.packet.LieuPackets.Request.of(null)), tr("crusadecraft.lieu.open.tip"));
        x -= 18;
        iconButton(g, x, 3, 16, Kit.Icon.INTRIGUES, Kit.OR, () -> send(new fr.crusadecraft.network.packet.IntriguePackets.IntrigueAction(
                fr.crusadecraft.network.packet.IntriguePackets.IntrigueAction.Kind.REFRESH, null)), tr("crusadecraft.intrigue.open.tip"));
        if (view.pendingEvents() > 0) {
            x -= 18;
            boolean hover = over(x, 3, 16, 16);
            g.fill(x, 3, x + 16, 19, Kit.CONTOUR);
            g.fill(x + 1, 4, x + 15, 18, hover ? 0xFF7A2418 : 0xFF5A1A14);
            g.renderOutline(x + 1, 4, 14, 14, Kit.ALERTE_LISERE);
            Kit.resource(g, Kit.Resource.SCEAU, x + 2, 5);
            click(x, 3, 16, 16, () -> send(new fr.crusadecraft.network.packet.IntriguePackets.RequestEvent(null)));
            tip(x, 3, 16, 16, hub("events", view.pendingEvents()), tr("crusadecraft.realm.events.tip").withStyle(ChatFormatting.GRAY));
        }
        int rightEdge = x - 8;
        // Les ressources : avec leur nom s'il y a la place, sans sinon (l'infobulle le donne). Elles passent avant le rang.
        record Res(Kit.Resource icon, Kit.Icon glyph, long value, String key) {}
        List<Res> res = List.of(new Res(Kit.Resource.OR, null, view.gold(), "gold"), new Res(Kit.Resource.PRESTIGE, null, view.prestige(), "prestige"),
                new Res(Kit.Resource.PIETE, null, view.piety(), "piety"), new Res(null, Kit.Icon.ARMEE, view.dread(), "dread"));
        int full = 0, bare = 0;
        for (Res r : res) {
            int vw = font.width(bold(group(r.value())));
            full += 15 + vw + 4 + font.width(tr("crusadecraft.resource." + r.key())) + 10;
            bare += 15 + vw + 10;
        }
        Component rank = tr(view.rank().titleKey());
        int rankW = font.width(rank) + 5;
        int avail = rightEdge - 24 - 16;
        int nameW = Kit.titleWidth(font, Component.literal(view.name()), 1);
        boolean showRank = nameW + rankW + bare <= avail;
        int nameRoom = Math.max(30, avail - bare - (showRank ? rankW : 0));
        boolean labels = Math.min(nameW, nameRoom) + (showRank ? rankW : 0) + full <= avail;

        // À gauche : le blason, le nom en gothique, le rang.
        Ui.shield(g, font, 6, 4, 12, 14, view.color(), null);
        int nx = 24;
        Component name = fitGothic(view.name(), nameRoom);
        Kit.title(g, font, name, nx, 8, 1, Kit.OR, true);
        tip(nx, 3, Kit.titleWidth(font, name, 1), 16, Component.literal(view.name()));
        nx += Kit.titleWidth(font, name, 1) + 5;
        if (showRank) {
            text(g, rank, nx, 7, Kit.GRIS_PERLE);
            nx += rankW;
        }
        nx += 3;
        Kit.divider(g, nx, 4, 14);
        nx += 8;
        for (Res r : res) {
            int start = nx;
            if (r.icon() != null) Kit.resource(g, r.icon(), nx, 5);
            else Kit.icon(g, r.glyph(), nx + 1, 6, Kit.GUEULES);
            nx += 15;
            Component v = bold(group(r.value()));
            text(g, v, nx, 7, Kit.VALEUR);
            nx += font.width(v) + 4;
            if (labels) {
                Component l = tr("crusadecraft.resource." + r.key());
                text(g, l, nx, 7, Kit.GRIS_DISCRET);
                nx += font.width(l);
            }
            nx += 10 - (labels ? 0 : 4);
            tip(start, 3, nx - start - 6, 16, tr("crusadecraft.resource." + r.key()).withStyle(ChatFormatting.GOLD),
                    tr("crusadecraft.resource." + r.key() + ".desc").withStyle(ChatFormatting.GRAY));
            if (nx > rightEdge) break;
        }
    }

    /** Le rail : quatre pages du gouvernement, un filet, puis le reste du royaume. Les touches 1 à 0 y mènent aussi. */
    private void renderRail(GuiGraphics g) {
        g.fill(0, BAR, RAIL, height, Kit.RAIL);
        g.fill(RAIL - 1, BAR, RAIL, height, Kit.CONTOUR);
        int step = Mth.clamp((height - BAR - 16) / 11, 18, 24);
        int size = Math.min(20, step - 2);
        int y = BAR + 6;
        for (int i = 0; i < RAIL_ITEMS.length; i++) {
            if (i == 4) {
                g.fill(6, y + 2, RAIL - 7, y + 3, Kit.FILET);
                y += step / 2 + 1;
            }
            Rail r = RAIL_ITEMS[i];
            int ix = (RAIL - 1 - size) / 2;
            boolean selected = r.tab() != null && r.tab() == tab;
            boolean hover = over(ix, y, size, size);
            if (selected) Kit.selection(g, ix, y, size, size);
            else if (hover) {
                g.fill(ix, y, ix + size, y + size, Kit.BOUTON);
                g.renderOutline(ix, y, size, size, Kit.CONTOUR);
            }
            Kit.icon(g, r.icon(), ix + (size - 10) / 2, y + (size - 10) / 2, selected ? Kit.OR : hover ? Kit.CREME : Kit.GRIS_PERLE);
            Component badge = badge(r);
            if (badge != null) {
                g.fill(ix + size - 2, y - 1, ix + size + 3, y + 4, Kit.CONTOUR);
                g.fill(ix + size - 1, y, ix + size + 2, y + 3, 0xFFC0473A);
            }
            int index = i;
            click(ix, y, size, size, () -> activate(index));
            List<Component> lines = new ArrayList<>();
            lines.add(hub("rail." + r.key()).withStyle(ChatFormatting.GOLD));
            if (r.tip() != null) lines.add(tr(r.tip()).withStyle(ChatFormatting.GRAY));
            if (badge != null) lines.add(badge.copy().withStyle(ChatFormatting.RED));
            if (r.tab() == null) lines.add(hub("rail.opens").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(hub("rail.key", (i + 1) % 10).withStyle(ChatFormatting.DARK_GRAY));
            if (hover) tip = lines;
            y += step;
        }
    }

    /** Une pastille rouge sur le rail quand une page attend un geste. */
    private Component badge(Rail r) {
        if (r.tab() == Tab.VASSAUX && chancery != null && chancery.canRankUp()) return hub("badge.title");
        if (r.tab() == Tab.CONSEIL) {
            for (Post p : Post.values()) if (p.isCouncil() && holderOf(p) == null) return hub("badge.vacant");
        }
        return null;
    }

    private RealmView.Seat holderOf(Post post) {
        for (RealmView.Seat s : view.seats()) if (s.post() == post && s.holder() != null) return s;
        return null;
    }

    // ------------------------------------------------------------------ Royaume

    private void renderRealm(GuiGraphics g) {
        Kit.anchored(g, lx, ly, lw, lh);
        Kit.anchored(g, rx, ry, rw, rh);
        int x = lx + 10, w = lw - 20, y = ly + 10;
        Ui.shield(g, font, x + 1, y, 22, 26, view.color(), Ui.initial(view.name()));
        Component sub = Component.literal(tr(view.rank().titleKey()).getString() + " · " + tr("crusadecraft.realm.house").getString() + " " + view.house());
        headerText(g, Component.literal(view.name()), sub, x + 32, y, x + w - 16);
        button(g, x + w - 14, y, 14, 14, Component.literal("✎"), true, false, () -> minecraft.setScreen(new NameForm(this,
                tr("crusadecraft.name.realm.title"), view.name(), name -> send(new fr.crusadecraft.network.packet.NamePackets.Rename(
                        fr.crusadecraft.network.packet.NamePackets.Rename.Kind.REALM, null, name)))), tr("crusadecraft.name.realm.tip"));
        Kit.rule(g, x, y + 33, w);

        // Le souverain : son vrai visage s'il est là, son titre, la fondation.
        y += 42;
        Kit.portraitFrame(g, x, y, 30, 34, true);
        if (minecraft != null && minecraft.player != null) PlayerFaceRenderer.draw(g, minecraft.player.getSkinTextureLocation(), x + 3, y + 5, 24);
        else Faces.draw(g, view.rulerName(), view.feminineTitle(), x + 3, y + 5, 3);
        int tx = x + 37;
        text(g, fitBold(view.rulerName(), x + w - tx), tx, y + 2, Kit.VALEUR);
        Component holder = Component.literal(tr(view.rank().holderKey(view.feminineTitle())).getString() + " " + of(view.name()).getString());
        text(g, fit(holder.getString(), x + w - tx), tx, y + 13, Kit.GRIS_PERLE);
        Component founded = hub("realm.founded", view.foundedDay());
        text(g, founded, tx, y + 24, Kit.GRIS_DISCRET);
        Component state = tr(view.state().key());
        int sx = tx + font.width(founded) + font.width(" · ");
        if (sx + font.width(state) <= x + w) {
            text(g, " · ", tx + font.width(founded), y + 24, Kit.GRIS_DISCRET);
            text(g, state, sx, y + 24, view.state().isActive() ? Kit.SINOPLE : Kit.GUEULES);
        } else if (!view.state().isActive()) {
            text(g, fit(state.getString(), x + w - tx), tx, y + 24, Kit.GUEULES);
        }
        y += 44;
        y = renderNextTitle(g, x, y, w);
        renderLawsInForce(g, x, y + 4, w, ly + lh - 8);
        renderRealmRight(g);
    }

    /** « Prochain titre » : les quatre conditions, comme à la chancellerie. */
    private int renderNextTitle(GuiGraphics g, int x, int y, int w) {
        if (chancery == null) {
            Kit.section(g, font, hub("realm.next"), x, y, w, null, 0);
            loading(g, x, y + 14);
            return y + 28;
        }
        if (chancery.nextRank() == null) {
            Kit.section(g, font, hub("realm.next"), x, y, w, null, 0);
            text(g, fit(hub("realm.top").getString(), w), x, y + 14, Kit.GRIS_DISCRET);
            return y + 28;
        }
        List<Object[]> reqs = requirements(chancery);
        int met = 0;
        for (Object[] r : reqs) if ((boolean) r[0]) met++;
        Kit.section(g, font, hub("realm.next"), x, y, w, hub("realm.next.count", met, reqs.size()), Kit.GRIS_DISCRET);
        y += 14;
        Component link = hub("rail.chancellerie");
        Component next = Component.literal(tr(chancery.nextRank().titleKey()).getString() + " " + of(view.name()).getString());
        text(g, fitBold(next.getString(), w - font.width(link) - 8), x, y, Kit.VALEUR);
        link(g, link, x + w - font.width(link), y, () -> setTab(Tab.VASSAUX));
        y += 12;
        Kit.segments(g, x, y, w, reqs.size(), met);
        y += 9;
        boolean twoCols = w >= 150;
        int colW = twoCols ? (w - 8) / 2 : w;
        for (int i = 0; i < reqs.size(); i++) {
            Object[] r = reqs.get(i);
            int cx = twoCols ? x + (i % 2) * (colW + 8) : x;
            int cy = twoCols ? y + (i / 2) * 12 : y + i * 12;
            boolean ok = (boolean) r[0];
            Kit.check(g, ok, cx, cy - 1);
            String value = (String) r[2];
            int vw = font.width(value);
            Component label = (Component) r[1];
            if (font.width(label) <= colW - 12 - vw - 3) text(g, label, cx + 12, cy, Kit.GRIS_PERLE);
            tip(cx, cy - 1, colW, 11, label);
            text(g, value, cx + colW - vw, cy, ok ? Kit.VALEUR : Kit.GUEULES);
        }
        return y + (twoCols ? 24 : 48) + 2;
    }

    /** Les conditions du prochain titre : {remplie, nom, valeur}. */
    private List<Object[]> requirements(ChanceryView c) {
        List<Object[]> out = new ArrayList<>();
        out.add(new Object[]{c.provinces() >= c.needProvinces(), hub("req.provinces"), c.provinces() + "/" + c.needProvinces()});
        out.add(new Object[]{c.gold() >= c.needGold(), hub("req.gold"), group(c.gold()) + "/" + group(c.needGold())});
        out.add(new Object[]{c.prestige() >= c.needPrestige(), hub("req.prestige"), c.prestige() + "/" + c.needPrestige()});
        out.add(new Object[]{c.sealOk(), hub("req.seal"), hub(c.sealOk() ? "req.seal.ok" : "req.seal.missing").getString()});
        return out;
    }

    private void renderLawsInForce(GuiGraphics g, int x, int y, int w, int bottom) {
        sectionLink(g, hub("realm.laws"), x, y, w, hub("rail.lois"), () -> setTab(Tab.LOIS));
        y += 14;
        if (laws == null) {
            loading(g, x, y);
            return;
        }
        int col = 0;
        for (LawsView.LawLine line : laws.laws()) col = Math.max(col, font.width(bold(tr(line.law().levelKey(line.level())).getString())));
        int vx = x + w - col;
        for (LawsView.LawLine line : laws.laws()) {
            if (y + 9 > bottom) break;
            text(g, fit(tr(line.law().key()).getString(), vx - x - 6), x, y, Kit.GRIS_PERLE);
            text(g, bold(tr(line.law().levelKey(line.level())).getString()), vx, y, line.cooldownLeft() > 0 ? Kit.AMBRE : Kit.VALEUR);
            y += 12;
        }
    }

    private void renderRealmRight(GuiGraphics g) {
        Set<UUID> vassals = new HashSet<>();
        if (chancery != null) for (ChanceryView.Vassal v : chancery.vassals()) vassals.add(v.id());
        map.configure(vassals, Set.of(), Set.of());
        int w = rw - 20, x = rx + 10;
        boolean wide = w >= 300;
        int bottomH = wide ? Mth.clamp((int) (rh * 0.40), 92, 130) : Mth.clamp((int) (rh * 0.45), 104, 130);
        int mapBottom = mapBlock(g, hub("map.title"), List.of(new LegendItem(KingdomMap.DOMAIN, hub("map.domain"), false),
                new LegendItem(KingdomMap.VASSAL, hub("map.vassal"), false), new LegendItem(KingdomMap.KNOWN, hub("map.known"), false),
                new LegendItem(Kit.BLOQUE_TEXTE, hub("map.unknown"), true)), bottomH, null);
        int y = mapBottom + 10, bottom = ry + rh - 8;
        if (wide) {
            int cw = (w - 20) / 3;
            renderDomain(g, x, y, cw, bottom, true);
            renderCouncilSummary(g, x + cw + 10, y, cw, bottom);
            renderVassalSummary(g, x + 2 * (cw + 10), y, cw, bottom, true);
        } else {
            int cw = (w - 10) / 2;
            int end = renderDomain(g, x, y, cw, bottom, false);
            renderVassalSummary(g, x, end + 4, cw, bottom, false);
            renderCouncilSummary(g, x + cw + 10, y, cw, bottom);
        }
    }

    private int renderDomain(GuiGraphics g, int x, int y, int w, int bottom, boolean full) {
        Kit.section(g, font, hub("domain"), x, y, w, null, 0);
        y += 14;
        ColonyInfo c = view.capital();
        if (c == null || c.isMissing()) {
            text(g, hub("domain.none"), x, y, Kit.GRIS_DISCRET);
            return y + 12;
        }
        Component chip = hub("domain.capital");
        int chipW = font.width(chip) + 6;
        Component name = fitBold(c.name(), w - chipW - 5);
        text(g, name, x, y, Kit.VALEUR);
        Kit.chip(g, font, chip, x + font.width(name) + 5, y - 2, Kit.Chip.GOLD);
        y += 13;
        if (full) {
            Component hall = hub("domain.hall", c.townHallLevel());
            if (font.width(hall) > w) hall = hub("domain.hall.short", c.townHallLevel());
            text(g, fit(hall.getString(), w), x, y, Kit.GRIS_DISCRET);
            y += 12;
        }
        String pop = c.citizens() + " / " + c.maxCitizens();
        text(g, fit(hub("domain.citizens").getString(), w - font.width(pop) - 4), x, y, Kit.GRIS_PERLE);
        text(g, pop, x + w - font.width(pop), y, Kit.AMBRE);
        y += 10;
        Kit.gauge(g, x, y, w, c.maxCitizens() <= 0 ? 0 : c.citizens() / (double) c.maxCitizens(), 0xFFC9A24A);
        y += 8;
        double h = c.happiness();
        Component mood = hub(h < 4 ? "happiness.low" : h < 6.5 ? "happiness.mid" : "happiness.high");
        text(g, hub("domain.happiness"), x, y, Kit.GRIS_PERLE);
        right(g, mood, x + w, y, Ui.happinessColor(h));
        tip(x, y - 1, w, 18, tr("crusadecraft.realm.colony.happiness", String.format(Locale.ROOT, "%.1f", h)));
        y += 10;
        Kit.gauge(g, x, y, w, h / 10.0, Ui.happinessColor(h));
        y += 8;
        int more = view.colonies().size() - 1;
        if (more > 0 && y + 9 <= bottom) {
            Component m = hub("domain.more", more);
            link(g, m, x, y, () -> activate(4));
            y += 11;
        }
        return y;
    }

    private void renderCouncilSummary(GuiGraphics g, int x, int y, int w, int bottom) {
        sectionLink(g, hub("council"), x, y, w, hub("link.open"), () -> setTab(Tab.CONSEIL));
        y += 14;
        for (Post p : Post.values()) {
            if (!p.isCouncil() || y + 9 > bottom) continue;
            RealmView.Seat s = holderOf(p);
            if (s == null) {
                g.fill(x, y, x + 8, y + 8, Kit.CARTE_CLAIR);
                text(g, fit(tr(p.key(false)).getString() + " · " + tr("crusadecraft.appoint.vacant").getString(), w - 12), x + 12, y, Kit.GRIS_DISCRET);
            } else {
                Faces.draw(g, s.name(), s.female(), x, y, 1);
                String op = signed(s.opinion());
                text(g, bold(op), x + w - font.width(bold(op)), y, opinionColor(s.opinion()));
                text(g, fit(s.name(), w - 12 - font.width(bold(op)) - 4), x + 12, y, Kit.CREME);
                tip(x, y - 1, w, 10, tr(p.key(s.female())).withStyle(ChatFormatting.GOLD), Component.literal(s.name()),
                        opinionWord(s.opinion()).copy().withStyle(ChatFormatting.GRAY));
            }
            y += 12;
        }
    }

    private Component vassalName(ChanceryView.Vassal v) {
        return v.ai() ? AiNames.realm(v.rank(), v.label()) : Component.literal(v.label());
    }

    private void renderVassalSummary(GuiGraphics g, int x, int y, int w, int bottom, boolean full) {
        Kit.section(g, font, hub("vassals"), x, y, w, chancery == null ? null : Component.literal(String.valueOf(chancery.vassals().size())), Kit.GRIS_DISCRET);
        y += 14;
        if (chancery == null) {
            loading(g, x, y);
            return;
        }
        if (chancery.vassals().isEmpty()) {
            text(g, hub("vassals.none"), x, y, Kit.GRIS_DISCRET);
            return;
        }
        ChanceryView.Vassal v = chancery.vassals().get(0);
        g.fill(x, y, x + 8, y + 8, Kit.CONTOUR);
        g.fill(x + 1, y + 1, x + 7, y + 7, 0xFF000000 | v.color());
        text(g, fitBold(vassalName(v).getString(), w - 12), x + 12, y, Kit.VALEUR);
        y += 12;
        if (full) {
            text(g, hub("vassal.tax"), x, y, Kit.GRIS_PERLE);
            right(g, Component.literal(v.tax() + " %"), x + w, y, Kit.CREME);
            y += 11;
            text(g, hub("vassal.levy"), x, y, Kit.GRIS_PERLE);
            right(g, Component.literal(v.levy() + " %"), x + w, y, Kit.CREME);
            y += 11;
        } else {
            text(g, hub("vassal.tax.short").getString() + " " + v.tax() + " % · " + hub("vassal.levy.short").getString() + " " + v.levy() + " %", x, y, Kit.GRIS_PERLE);
            y += 11;
        }
        if (chancery.vassals().size() > 1 && y + 9 <= bottom) {
            text(g, hub("more", chancery.vassals().size() - 1), x, y, Kit.GRIS_DISCRET);
            y += 11;
        }
        Component settle = font.width(hub("link.settle")) <= w ? hub("link.settle") : hub("rail.chancellerie");
        if (y + 9 <= bottom) link(g, settle, x, y + 2, () -> setTab(Tab.VASSAUX));
    }

    // ------------------------------------------------------------------ Conseil

    /** Une ligne de la liste du conseil : les faits du royaume, complétés par la tâche si le conseil est arrivé. */
    private record SeatRow(Post post, CitizenRef holder, String name, boolean female, int opinion, String place, CouncilTask task, boolean taskKnown,
                           int stat) {}

    private List<SeatRow> councilRows() {
        List<SeatRow> rows = new ArrayList<>();
        for (Post p : Post.values()) {
            if (!p.isCouncil()) continue;
            RealmView.Seat s = holderOf(p);
            CouncilView.Seat cs = null;
            if (council != null) for (CouncilView.Seat c : council.seats()) if (c.post() == p) cs = c;
            if (s == null && cs != null && cs.holder() != null) {
                rows.add(new SeatRow(p, cs.holder(), cs.name(), cs.female(), cs.opinion(), "", cs.task(), true, cs.stat()));
            } else if (s == null) {
                rows.add(new SeatRow(p, null, "", false, 0, "", null, council != null, -1));
            } else {
                rows.add(new SeatRow(p, s.holder(), s.name(), s.female(), s.opinion(), "", cs == null ? null : cs.task(), cs != null, cs == null ? -1 : cs.stat()));
            }
        }
        return rows;
    }

    private List<SeatRow> mayorRows() {
        List<SeatRow> rows = new ArrayList<>();
        for (RealmView.Seat s : view.seats()) {
            if (s.post() == Post.MAIRE) rows.add(new SeatRow(Post.MAIRE, s.holder(), s.name(), s.female(), s.opinion(), s.place(), null, true, -1));
        }
        return rows;
    }

    private void renderCouncil(GuiGraphics g) {
        Kit.anchored(g, lx, ly, lw, lh);
        Kit.anchored(g, rx, ry, rw, rh);
        int x = lx + 10, w = lw - 20, y = ly + 10;
        pageHeader(g, Kit.Icon.CONSEIL, hub("rail.conseil"), hub("council.sub"), x, y, w);
        y += 43;
        List<SeatRow> seats = councilRows(), mayors = mayorRows();
        int filled = 0;
        for (SeatRow r : seats) if (r.holder() != null) filled++;
        // Les deux onglets de texte, et les intrigues à droite.
        Component t1 = hub("council.seats", filled), t2 = hub("council.mayors", mayors.size());
        textTab(g, t1, x, y, !mayorsTab, () -> mayorsTab = false);
        textTab(g, t2, x + font.width(t1) + 12, y, mayorsTab, () -> mayorsTab = true);
        Component intr = tr("crusadecraft.intrigue.open");
        int bw = font.width(intr) + 22;
        boolean iconOnly = font.width(t1) + 12 + font.width(t2) + 8 + bw > w;
        if (iconOnly) bw = 18;
        boolean hover = over(x + w - bw, y - 4, bw, 15);
        Kit.button(g, x + w - bw, y - 4, bw, 15, hover ? Kit.State.HOVER : Kit.State.NORMAL, false);
        Kit.icon(g, Kit.Icon.INTRIGUES, x + w - bw + (iconOnly ? 4 : 5), y - 1, hover ? Kit.SURVOL_TEXTE : Kit.CREME);
        if (!iconOnly) text(g, intr, x + w - bw + 17, y, hover ? Kit.SURVOL_TEXTE : Kit.CREME);
        click(x + w - bw, y - 4, bw, 15, () -> send(new fr.crusadecraft.network.packet.IntriguePackets.IntrigueAction(
                fr.crusadecraft.network.packet.IntriguePackets.IntrigueAction.Kind.REFRESH, null)));
        tip(x + w - bw, y - 4, bw, 15, tr("crusadecraft.intrigue.open.tip"));
        y += 16;

        List<SeatRow> rows = mayorsTab ? mayors : seats;
        int bottom = ly + lh - 8;
        int step = Mth.clamp((bottom - y) / 5, 24, 34);
        SeatRow shown = null;
        if (mayorsTab) {
            if (selectedMayor >= rows.size()) selectedMayor = -1;
            if (selectedMayor < 0 && !rows.isEmpty()) selectedMayor = 0;
            if (selectedMayor >= 0) shown = rows.get(selectedMayor);
            if (rows.isEmpty()) text(g, hub("council.no_mayor"), x + 2, y + 4, Kit.GRIS_DISCRET);
        } else {
            if (selectedPost == null || !selectedPost.isCouncil()) {
                selectedPost = Post.CHANCELIER;
                for (SeatRow r : rows) {
                    if (r.holder() != null) {
                        selectedPost = r.post();
                        break;
                    }
                }
            }
            for (SeatRow r : rows) if (r.post() == selectedPost) shown = r;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (y + step - 2 > bottom) {
                text(g, hub("more", rows.size() - i), x + 2, y + 2, Kit.GRIS_DISCRET);
                break;
            }
            SeatRow r = rows.get(i);
            boolean selected = r == shown;
            seatRow(g, r, x, y, w, step - 2, selected);
            int index = i;
            click(x, y, w, step - 2, () -> {
                if (mayorsTab) selectedMayor = index;
                else selectedPost = r.post();
            });
            y += step;
        }
        renderSeatDetail(g, shown);
    }

    private void textTab(GuiGraphics g, Component label, int x, int y, boolean active, Runnable action) {
        int w = font.width(label);
        boolean hover = over(x, y - 2, w, 12);
        text(g, label, x, y, active ? Kit.OR : hover ? Kit.CREME : Kit.GRIS_DISCRET);
        if (active) g.fill(x, y + 10, x + w, y + 11, Kit.OR);
        click(x, y - 2, w, 12, action);
    }

    private void seatRow(GuiGraphics g, SeatRow r, int x, int y, int w, int h, boolean selected) {
        if (selected) Kit.selection(g, x, y, w, h);
        else if (over(x, y, w, h)) g.fill(x, y, x + w, y + h, 0xFF1B2026);
        else g.fill(x + 2, y + h, x + w - 2, y + h + 1, 0xFF1B2026);
        int face = h >= 26 ? 20 : 16;
        int fx = x + 4, fy = y + (h - face) / 2;
        Kit.portraitFrame(g, fx, fy, face, face, false);
        if (r.holder() != null) Faces.draw(g, r.name(), r.female(), fx + (face - 16) / 2, fy + (face - 16) / 2, 2);
        else Kit.icon(g, Kit.Icon.CONSEIL, fx + (face - 10) / 2, fy + (face - 10) / 2, Kit.GRIS_DISCRET);
        if (r.holder() != null && r.post().isCouncil() && r.taskKnown() && r.task() == null) {
            g.fill(fx + face - 5, fy - 2, fx + face + 2, fy + 7, Kit.CONTOUR);
            g.fill(fx + face - 4, fy - 1, fx + face + 1, fy + 6, Kit.AMBRE);
            text(g, "!", fx + face - 3, fy - 1, Kit.ENCRE);
        }
        int tx = fx + face + 6;
        int lines = h >= 28 ? 3 : 2;
        int ty = y + (h - lines * 9) / 2 + 1;
        String op = r.holder() == null ? "" : signed(r.opinion());
        int opW = r.holder() == null ? 0 : Math.max(font.width(bold(op)), font.width(opinionWord(r.opinion()))) + 6;
        int room = x + w - 4 - tx - opW;
        String head = r.post() == Post.MAIRE ? r.place() : tr(r.post().key(r.female())).getString();
        text(g, fit(head, room), tx, ty, Kit.OR);
        if (r.holder() == null) {
            text(g, fit(hub("council.vacant").getString(), room), tx, ty + 9, Kit.GRIS_DISCRET);
            return;
        }
        text(g, fitBold(r.name(), room), tx, ty + 9, Kit.VALEUR);
        if (lines == 3 && r.post().isCouncil()) {
            Component task = r.task() != null ? tr(r.task().key()) : r.taskKnown() ? hub("council.no_task") : Component.literal("…");
            text(g, fit(task.getString(), room), tx, ty + 18, r.task() == null && r.taskKnown() ? Kit.AMBRE : Kit.GRIS_DISCRET);
        }
        right(g, bold(op), x + w - 5, ty + (lines == 3 ? 3 : 0), opinionColor(r.opinion()));
        right(g, opinionWord(r.opinion()), x + w - 5, ty + (lines == 3 ? 13 : 9), Kit.GRIS_DISCRET);
    }

    private void renderSeatDetail(GuiGraphics g, SeatRow r) {
        int x = rx + 10, w = rw - 20, y = ry + 10;
        if (r == null) return;
        int frame = rw >= 300 ? 44 : 36;
        int scale = frame >= 44 ? 4 : 3;
        Kit.portraitFrame(g, x, y, frame, frame, true);
        if (r.holder() != null) Faces.draw(g, r.name(), r.female(), x + (frame - 8 * scale) / 2, y + (frame - 8 * scale) / 2, scale);
        else Kit.icon(g, Kit.Icon.CONSEIL, x + frame / 2 - 5, y + frame / 2 - 5, Kit.GRIS_DISCRET);
        int tx = x + frame + 10;
        // Le poste, en petite gothique, et le bouton de la fiche.
        Component post = r.post() == Post.MAIRE ? hub("council.mayor_of", r.place()) : tr(r.post().key(r.female()));
        Kit.icon(g, r.post() == Post.MAIRE ? Kit.Icon.DOMAINE : Kit.Icon.CONSEIL, tx, y, Kit.OR);
        int sheetW = r.holder() == null ? 0 : font.width(hub("council.sheet")) + 12;
        Kit.title(g, font, fitGothic(post.getString(), x + w - tx - 14 - sheetW - 6), tx + 14, y + 2, 1, Kit.OR, false);
        if (r.holder() != null) {
            CitizenRef ref = r.holder();
            button(g, x + w - sheetW, y - 2, sheetW, 14, hub("council.sheet"), true, false, () -> send(new fr.crusadecraft.network.packet.RequestCharacterPacket(
                    ref, fr.crusadecraft.network.packet.RequestCharacterPacket.Mode.SHEET)), hub("council.sheet.tip", r.name()));
        }
        if (r.holder() == null) {
            text(g, hub("council.vacant.body"), tx, y + 18, Kit.GRIS_DISCRET);
        } else {
            Component name = Component.literal(r.name());
            int scaleName = Kit.titleWidth(font, name, 2) <= x + w - tx ? 2 : 1;
            Kit.title(g, font, name, tx, y + (scaleName == 2 ? 16 : 17), scaleName, Kit.VALEUR, true);
            // L'opinion : la valeur, le mot, et la jauge de -100 à +100.
            int oy = y + frame - 10;
            Component op = bold(signed(r.opinion()));
            text(g, op, tx, oy, opinionColor(r.opinion()));
            Component word = opinionWord(r.opinion());
            text(g, word, tx + font.width(op) + 5, oy, Kit.GRIS_PERLE);
            int bx = tx + font.width(op) + 5 + font.width(word) + 10;
            int bw = Math.min(160, x + w - bx);
            if (bw >= 60) {
                g.fill(bx, oy, bx + bw, oy + 6, Kit.CONTOUR);
                g.fill(bx + 1, oy + 1, bx + bw - 1, oy + 5, 0xFF2A323B);
                int mid = bx + bw / 2;
                int len = (int) Math.round((bw / 2.0 - 1) * Math.max(-100, Math.min(100, r.opinion())) / 100.0);
                if (len > 0) g.fill(mid, oy + 1, mid + len, oy + 5, 0xFFC9A24A);
                else if (len < 0) g.fill(mid + len, oy + 1, mid, oy + 5, Kit.CIRE);
                g.fill(mid, oy - 1, mid + 1, oy + 7, Kit.GRIS_PERLE);
            }
        }
        y += frame + 10;

        // Fonction
        Kit.section(g, font, hub("council.function"), x, y, w, null, 0);
        y += 14;
        y += 10 * wrap(g, hub("post." + r.post().name().toLowerCase(Locale.ROOT) + ".desc"), x, y, w, Kit.CREME, 3);
        if (r.stat() >= 0) {
            text(g, tr("crusadecraft.council.seat.stat", tr(r.post().stat().key()), r.stat()), x, y, Kit.GRIS_DISCRET);
            y += 10;
        }
        y += 9;

        // Le geste pour nommer, mesuré d'abord : la place des tâches en dépend.
        Component shift = minecraft == null ? Component.literal("Maj") : minecraft.options.keyShift.getTranslatedKeyMessage();
        List<Object[]> gesture = List.of(new Object[]{true, hub("council.sceptre")}, new Object[]{false, hub("council.in_hand")}, new Object[]{true, shift},
                new Object[]{false, Component.literal("+")}, new Object[]{true, hub("council.right_click")}, new Object[]{false, hub("council.on_npc")});
        int hy = ry + rh - 8 - 13 - 12 * flow(g, gesture, x, 0, w, false);

        // Tâche
        int bottom = hy - 6;
        if (r.post().isCouncil()) {
            Component current = r.task() != null ? tr(r.task().key()) : hub("council.no_task");
            Kit.section(g, font, hub("council.task"), x, y, w, r.holder() == null ? null : current, Kit.AMBRE);
            y += 14;
            if (r.holder() != null && council == null) {
                loading(g, x, y);
            } else {
                // Un siège vacant montre ce que son titulaire pourra faire, grisé.
                List<CouncilTask> tasks = CouncilTask.of(r.post());
                int n = tasks.size(), gap = 6;
                int cw = (w - gap * (n - 1)) / Math.max(1, n);
                int ch = Mth.clamp(bottom - y - 4, 34, 70);
                for (int i = 0; i < n; i++) {
                    taskCard(g, r, tasks.get(i), x + i * (cw + gap), y, cw, ch, r.holder() != null);
                }
            }
        } else {
            Kit.section(g, font, hub("council.task"), x, y, w, null, 0);
            wrap(g, hub("council.tasks.mayor"), x, y + 14, w, Kit.GRIS_DISCRET, 2);
        }

        // Remplacer : le geste, touche par touche.
        Kit.section(g, font, hub(r.holder() == null ? "council.appoint" : "council.replace"), x, hy, w, null, 0);
        flow(g, gesture, x, hy + 14, w, true);
    }

    /** Une ligne de texte et de touches, qui passe à la ligne quand il le faut. Renvoie le nombre de lignes. */
    private int flow(GuiGraphics g, List<Object[]> parts, int x, int y, int w, boolean draw) {
        int cx = x, cy = y;
        for (Object[] p : parts) {
            Component c = (Component) p[1];
            if ((boolean) p[0]) {
                int kw = Math.max(11, font.width(c) + 6);
                if (cx + kw > x + w && cx > x) {
                    cx = x;
                    cy += 12;
                }
                if (draw) Kit.key(g, font, c, cx, cy - 1);
                cx += kw + 4;
            } else {
                for (String word : c.getString().split(" ")) {
                    if (word.isEmpty()) continue;
                    int ww = font.width(word);
                    if (cx + ww > x + w && cx > x) {
                        cx = x;
                        cy += 12;
                    }
                    if (draw) text(g, word, cx, cy, Kit.CREME);
                    cx += ww + font.width(" ");
                }
                cx += 1;
            }
        }
        return (cy - y) / 12 + 1;
    }

    private static Kit.Icon taskIcon(CouncilTask t) {
        return switch (t) {
            case GERER_LA_COUR -> Kit.Icon.ROYAUME;
            case AMELIORER_RELATIONS -> Kit.Icon.LETTRE;
            case FABRIQUER_REVENDICATION -> Kit.Icon.CHANCELLERIE;
            case ENTRAINER_TROUPES -> Kit.Icon.ARMEE;
            case ORGANISER_LEVEES -> Kit.Icon.MAISON;
            case MAINTENIR_ORDRE -> Kit.Icon.SCEPTRE;
            case GERER_LE_DOMAINE -> Kit.Icon.DOMAINE;
            case DEVELOPPER_PROVINCE -> Kit.Icon.HAUSSE;
            case COLLECTER_IMPOTS -> Kit.Icon.TRESOR;
            case SURVEILLER_LES_TERRES -> Kit.Icon.INTRIGUES;
            case DEBUSQUER_COMPLOTS -> Kit.Icon.VERROU;
            case ESPIONNER_RIVAL -> Kit.Icon.SUIVANT;
            case LEGITIMITE -> Kit.Icon.CHANCELLERIE;
            case BONHEUR_DU_PEUPLE -> Kit.Icon.HAUSSE;
            case RECHERCHE -> Kit.Icon.CHRONIQUE;
        };
    }

    private void taskCard(GuiGraphics g, SeatRow r, CouncilTask t, int x, int y, int w, int h, boolean held) {
        boolean current = held && r.task() == t;
        boolean usable = held && t.available() && !current;
        boolean hover = usable && over(x, y, w, h);
        if (!t.available() || !held) {
            g.fill(x, y, x + w, y + h, Kit.CONTOUR);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Kit.BLOQUE);
        } else {
            Kit.card(g, x, y, w, h);
        }
        if (current) {
            g.renderOutline(x, y, w, h, Kit.CONTOUR);
            g.renderOutline(x + 1, y + 1, w - 2, h - 2, Kit.SELECTION_LISERE);
        } else if (hover) {
            g.renderOutline(x, y, w, h, Kit.SURVOL);
        }
        int color = t.available() ? Kit.CREME : Kit.BLOQUE_TEXTE;
        Kit.icon(g, taskIcon(t), x + 6, y + 6, t.available() ? Kit.OR : Kit.BLOQUE_TEXTE);
        if (!t.available()) {
            Component soon = hub("council.soon");
            right(g, soon, x + w - 5, y + 7, Kit.BLOQUE_TEXTE);
        }
        int ty = y + 19;
        int nameLines = wrap(g, bold(tr(t.key()).getString()), x + 6, ty, w - 12, color, h >= 50 ? 2 : 1);
        ty += nameLines * 10 + 1;
        int left = (y + h - 4 - ty) / 10;
        if (left > 0) wrap(g, tr(t.key() + ".short"), x + 6, ty, w - 12, t.available() ? Kit.GRIS_DISCRET : Kit.BLOQUE_TEXTE, left);
        if (usable) {
            Post post = r.post();
            click(x, y, w, h, () -> send(new CourtPackets.CouncilAction(CourtPackets.CouncilAction.Kind.SET_TASK, post, t, null)));
        }
        List<Component> lines = new ArrayList<>();
        lines.add(tr(t.key()).withStyle(ChatFormatting.GOLD));
        lines.add(tr(t.descriptionKey()).withStyle(ChatFormatting.GRAY));
        if (!t.available()) lines.add(tr("crusadecraft.council.task_unavailable", tr(t.key())).withStyle(ChatFormatting.RED));
        else if (usable) lines.add(tr("crusadecraft.council.click_to_change").withStyle(ChatFormatting.DARK_GRAY));
        if (over(x, y, w, h)) tip = lines;
    }

    // ------------------------------------------------------------------ Lois

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

    private void renderLaws(GuiGraphics g) {
        Kit.anchored(g, lx, ly, lw, lh);
        Kit.anchored(g, rx, ry, rw, rh);
        int x = lx + 10, w = lw - 20, y = ly + 10;
        Component meta = laws == null ? null : tr(laws.councilSize() == 0 ? "crusadecraft.law.meta.alone" : "crusadecraft.law.meta",
                laws.prestige(), laws.councilSize());
        pageHeader(g, Kit.Icon.LOIS, hub("rail.lois"), meta, x, y, w);
        y += 42;
        if (laws == null) {
            loading(g, x, y);
            return;
        }
        if (focusLaw == null || lawLine(focusLaw) == null) focusLaw = laws.laws().isEmpty() ? null : laws.laws().get(0).law();
        int bottom = ly + lh - 22;
        int n = Math.max(1, laws.laws().size());
        int step = Mth.clamp((bottom - y) / n, 20, 34);
        for (LawsView.LawLine line : laws.laws()) {
            if (y + step - 2 > bottom) break;
            lawRow(g, line, x, y, w, step - 3);
            y += step;
        }
        // La légende des pastilles.
        int legY = ly + lh - 16;
        List<LegendItem> items = List.of(new LegendItem(Kit.OR_BOUTON, hub("legend.in_force"), false), new LegendItem(Kit.AZUR_LISERE, hub("legend.proposal"), true));
        legend(g, items, x, legY);
        int lockX = x + legendWidth(items) + 8;
        Component locked = hub("legend.locked");
        if (lockX + 12 + font.width(locked) <= x + w) {
            Kit.icon(g, Kit.Icon.VERROU, lockX, legY - 1, Kit.GRIS_PERLE);
            text(g, locked, lockX + 12, legY, Kit.GRIS_PERLE);
        }
        renderLawDetail(g);
    }

    private void lawRow(GuiGraphics g, LawsView.LawLine line, int x, int y, int w, int h) {
        Law law = line.law();
        boolean focus = law == focusLaw;
        if (focus) Kit.selection(g, x, y, w, h);
        else if (over(x, y, w, h)) g.fill(x, y, x + w, y + h, 0xFF1B2026);
        else g.fill(x + 2, y + h + 1, x + w - 2, y + h + 2, 0xFF1B2026);
        // Les pastilles à droite : le niveau en vigueur, et la proposition s'il y en a une.
        Component level = tr(law.levelKey(line.level()));
        int cy = y + (h - 11) / 2;
        int cx = x + w - 5 - (font.width(level) + 6);
        Kit.chip(g, font, level, cx, cy, Kit.Chip.SOLID);
        if (law == selectedLaw && selectedOption() != null) {
            Component p = hub("law.proposed");
            cx -= font.width(p) + 6 + 4;
            Kit.chip(g, font, p, cx, cy, Kit.Chip.PROPOSAL);
        }
        int room = cx - 6 - (x + 6);
        boolean twoLines = h >= 24;
        int ty = twoLines ? y + 6 : y + (h - 8) / 2 + 1;
        Kit.title(g, font, fitGothic(tr(law.key()).getString(), room), x + 6, ty, 1, line.cooldownLeft() > 0 ? Kit.AMBRE : Kit.OR, false);
        if (twoLines) text(g, fit(hub("law." + law.name().toLowerCase(Locale.ROOT) + ".sub").getString(), room), x + 6, ty + 11, Kit.GRIS_DISCRET);
        click(x, y, w, h, () -> {
            if (focusLaw != law) {
                focusLaw = law;
                if (selectedLaw != law) {
                    selectedLaw = null;
                    selectedLevel = -1;
                }
            }
        });
        if (line.cooldownLeft() > 0) tip(x, y, cx - x, h, tr("crusadecraft.law.cooldown", line.cooldownLeft()));
    }

    private void renderLawDetail(GuiGraphics g) {
        LawsView.LawLine line = lawLine(focusLaw);
        if (line == null) return;
        Law law = line.law();
        int x = rx + 10, w = rw - 20, y = ry + 10;
        // Le titre de la loi, et le niveau en vigueur à droite.
        Component cur = tr(law.levelKey(line.level()));
        Component inForce = hub("law.in_force");
        int chipW = font.width(cur) + 6;
        int rightW = font.width(inForce) + 6 + chipW;
        Component title = tr(law.key());
        int scale = Kit.titleWidth(font, title, 2) <= w - rightW - 10 ? 2 : 1;
        Kit.title(g, font, title, x, y + (scale == 2 ? 3 : 4), scale, Kit.OR, true);
        text(g, fit(hub("law." + law.name().toLowerCase(Locale.ROOT) + ".sub").getString(), w - rightW - 10), x, y + (scale == 2 ? 24 : 16), Kit.GRIS_PERLE);
        text(g, inForce, x + w - rightW, y + (scale == 2 ? 22 : 14), Kit.GRIS_DISCRET);
        Kit.chip(g, font, cur, x + w - chipW, y + (scale == 2 ? 20 : 12), Kit.Chip.SOLID);
        y += scale == 2 ? 44 : 31;

        // Les niveaux
        Kit.section(g, font, hub("law.levels"), x, y, w, hub("law.levels.hint"), Kit.GRIS_DISCRET);
        y += 14;
        int n = line.options().size(), gap = 6;
        int cw = (w - gap * (n - 1)) / Math.max(1, n);
        // Les cartes prennent la place que le vote et la barre du bas leur laissent.
        int ch = Mth.clamp(ry + rh - 30 - 8 - 58 - 24 - y, 60, 140);
        for (LawsView.LevelOption o : line.options()) levelCard(g, line, o, x + o.level() * (cw + gap), y, cw, ch);
        y += ch + 10;

        // Le vote attendu
        LawsView.LevelOption option = law == selectedLaw ? selectedOption() : null;
        Component summary;
        int sumColor;
        if (option == null) {
            summary = hub("law.vote.pick");
            sumColor = Kit.GRIS_DISCRET;
        } else if (option.approve().isEmpty() && option.reject().isEmpty()) {
            summary = hub("law.vote.alone");
            sumColor = Kit.GRIS_PERLE;
        } else {
            int total = option.approve().size() + option.reject().size();
            summary = hub(option.passes() ? "law.vote.passes" : "law.vote.fails", option.approve().size(), total);
            sumColor = option.passes() ? Kit.SINOPLE : Kit.GUEULES;
        }
        Kit.section(g, font, hub("law.vote"), x, y, w, summary, sumColor);
        y += 14;
        int barY = ry + rh - 30;
        if (option != null) {
            List<Object[]> votes = new ArrayList<>();
            for (String name : option.approve()) votes.add(new Object[]{name, true});
            for (String name : option.reject()) votes.add(new Object[]{name, false});
            int vn = Math.max(1, votes.size());
            int vw = Math.min(90, (w - 5 * (vn - 1)) / vn);
            int vh = Mth.clamp(barY - 8 - y, 24, 44);
            for (int i = 0; i < votes.size(); i++) voteCard(g, (String) votes.get(i)[0], (boolean) votes.get(i)[1], x + i * (vw + 5), y, vw, vh);
        }

        // La barre du bas : le coût, Annuler, Soumettre (ou Imposer).
        g.fill(x, barY - 4, x + w, barY - 3, Kit.FILET);
        boolean force = option != null && !option.passes();
        int cost = option == null ? 0 : force ? option.forceCost() : 0;
        Component submit = force ? tr("crusadecraft.law.force", option.forceCost()) : hub("law.submit");
        int sw = font.width(submit) + 14, aw = font.width(hub("law.cancel")) + 14;
        int bx = x + w - sw;
        int limit = bx - aw - 10;
        Component costLabel = hub("law.cost");
        Component cv = bold(cost > 0 ? hub("law.cost.value", cost).getString() : hub("law.cost.none").getString());
        Component avail = hub("law.cost.of", laws.prestige());
        int cx = x;
        if (font.width(costLabel) + 6 + font.width(cv) <= limit - x) {
            text(g, costLabel, cx, barY + 5, Kit.GRIS_DISCRET);
            cx += font.width(costLabel) + 6;
        }
        if (cx + font.width(cv) <= limit) {
            text(g, cv, cx, barY + 5, cost > laws.prestige() ? Kit.GUEULES : Kit.VALEUR);
            cx += font.width(cv) + 5;
            if (cx + font.width(avail) <= limit) text(g, avail, cx, barY + 5, Kit.GRIS_DISCRET);
        }
        boolean can = option != null && option.unlocked() && line.cooldownLeft() == 0 && (!force || laws.prestige() >= option.forceCost());
        Law l = selectedLaw;
        int lvl = selectedLevel;
        button(g, bx, barY, sw, 17, submit, can, true, () -> {
            send(new EconomyPackets.ProposeLaw(l, lvl, force));
            selectedLaw = null;
            selectedLevel = -1;
        }, option == null ? null : tr(force ? "crusadecraft.law.force.tip" : "crusadecraft.law.propose.tip"));
        button(g, bx - 6 - aw, barY, aw, 17, hub("law.cancel"), option != null, false, () -> {
            selectedLaw = null;
            selectedLevel = -1;
        }, null);
    }

    private void levelCard(GuiGraphics g, LawsView.LawLine line, LawsView.LevelOption o, int x, int y, int w, int h) {
        Law law = line.law();
        boolean current = o.level() == line.level();
        boolean selected = law == selectedLaw && o.level() == selectedLevel && !current;
        boolean usable = !current && o.unlocked();
        boolean hover = usable && !selected && over(x, y, w, h);
        if (!o.unlocked() && !current) {
            g.fill(x, y, x + w, y + h, Kit.CONTOUR);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, Kit.BLOQUE);
        } else {
            Kit.card(g, x, y, w, h);
        }
        if (current) {
            g.renderOutline(x, y, w, h, Kit.CONTOUR);
            g.renderOutline(x + 1, y + 1, w - 2, h - 2, Kit.CADRE_OR);
        } else if (selected) {
            g.renderOutline(x, y, w, h, Kit.AZUR_LISERE);
            g.renderOutline(x + 1, y + 1, w - 2, h - 2, Kit.AZUR_INTERIEUR);
        } else if (hover) {
            g.renderOutline(x, y, w, h, Kit.SURVOL);
        }
        int ty = y + 5;
        if (current) Kit.chip(g, font, hub("law.in_force"), x + 5, ty, Kit.Chip.SOLID);
        else if (selected) Kit.chip(g, font, hub("law.proposed"), x + 5, ty, Kit.Chip.PROPOSAL);
        else if (!o.unlocked()) Kit.icon(g, Kit.Icon.VERROU, x + 5, ty, Kit.BLOQUE_TEXTE);
        ty += 14;
        int nameColor = o.unlocked() || current ? Kit.VALEUR : Kit.BLOQUE_TEXTE;
        text(g, fitBold(tr(law.levelKey(o.level())).getString(), w - 10), x + 5, ty, nameColor);
        ty += 12;
        // Les effets, ligne par ligne, tant qu'il reste de la place au-dessus du pied.
        int footY = y + h - 12;
        int max = (footY - 2 - ty) / 10;
        for (String part : tr(law.levelKey(o.level()) + ".desc").getString().split("\\n")) {
            if (max <= 0 || part.isBlank()) continue;
            int used = wrap(g, Component.literal(part), x + 5, ty, w - 10, o.unlocked() || current ? Kit.GRIS_PERLE : Kit.BLOQUE_TEXTE, max);
            ty += used * 10;
            max -= used;
        }
        Component foot;
        int footColor;
        if (current) {
            foot = hub("law.current");
            footColor = Kit.OR;
        } else if (!o.unlocked()) {
            foot = hub("law.research");
            footColor = Kit.GUEULES;
        } else if (o.passes()) {
            foot = o.approve().isEmpty() && o.reject().isEmpty() ? hub("law.vote.alone") : hub("law.approves");
            footColor = Kit.SINOPLE;
        } else {
            foot = hub("law.force_cost", o.forceCost());
            footColor = Kit.AMBRE;
        }
        text(g, fit(foot.getString(), w - 10), x + 5, footY, footColor);
        if (usable) {
            int level = o.level();
            click(x, y, w, h, () -> {
                selectedLaw = law;
                selectedLevel = level;
            });
        }
        if (over(x, y, w, h)) tip = levelTooltip(law, line, o);
    }

    private void voteCard(GuiGraphics g, String name, boolean pour, int x, int y, int w, int h) {
        Kit.card(g, x, y, w, h);
        boolean roomy = h >= 38;
        int face = roomy ? 16 : 8;
        Faces.draw(g, name, false, x + (w - face) / 2, y + 4, roomy ? 2 : 1);
        int ty = y + 4 + face + 2;
        String n = fit(name, w - 6);
        text(g, n, x + (w - font.width(n)) / 2, ty, Kit.CREME);
        Component verdict = hub(pour ? "law.for" : "law.against");
        int vw = 12 + font.width(verdict);
        if (ty + 10 + 8 <= y + h) {
            Kit.check(g, pour, x + (w - vw) / 2, ty + 9);
            text(g, verdict, x + (w - vw) / 2 + 12, ty + 10, pour ? Kit.SINOPLE : Kit.GUEULES);
        }
        tip(x, y, w, h, Component.literal(name), verdict.copy().withStyle(pour ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private List<Component> levelTooltip(Law law, LawsView.LawLine line, LawsView.LevelOption o) {
        List<Component> lines = new ArrayList<>();
        lines.add(tr(law.levelKey(o.level())).withStyle(ChatFormatting.GOLD));
        for (String part : tr(law.levelKey(o.level()) + ".desc").getString().split("\\n")) lines.add(Component.literal(part).withStyle(ChatFormatting.GRAY));
        if (o.level() == line.level()) {
            lines.add(tr("crusadecraft.law.in_force").withStyle(ChatFormatting.GREEN));
            return lines;
        }
        if (!o.unlocked()) {
            lines.add(tr("crusadecraft.law.locked.tip", tr(researchKey(law, o.level()))).withStyle(ChatFormatting.RED));
            return lines;
        }
        if (!o.approve().isEmpty()) lines.add(tr("crusadecraft.law.for", String.join(", ", o.approve())).withStyle(ChatFormatting.GREEN));
        if (!o.reject().isEmpty()) lines.add(tr("crusadecraft.law.against", String.join(", ", o.reject())).withStyle(ChatFormatting.RED));
        if (o.approve().isEmpty() && o.reject().isEmpty()) lines.add(tr("crusadecraft.law.no_council").withStyle(ChatFormatting.GRAY));
        if (!o.passes()) lines.add(tr("crusadecraft.law.force.cost", o.forceCost()).withStyle(ChatFormatting.YELLOW));
        if (line.cooldownLeft() > 0) lines.add(tr("crusadecraft.law.cooldown", line.cooldownLeft()).withStyle(ChatFormatting.RED));
        lines.add(tr("crusadecraft.law.click").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** Clé du nom court de la recherche qui ouvre un niveau de loi. */
    private static String researchKey(Law law, int level) {
        var research = law.levels().get(level).research();
        return research == null ? "" : "crusadecraft.research.short." + research.getPath().replace("effects/", "");
    }

    // ------------------------------------------------------------------ Chancellerie

    private void renderChancery(GuiGraphics g) {
        Kit.anchored(g, lx, ly, lw, lh);
        Kit.anchored(g, rx, ry, rw, rh);
        int x = lx + 10, w = lw - 20, y = ly + 10;
        pageHeader(g, Kit.Icon.CHANCELLERIE, hub("rail.chancellerie"), hub("chancery.sub"), x, y, w);
        y += 42;
        if (chancery == null) {
            loading(g, x, y);
            return;
        }
        ChanceryView c = chancery;
        // Le sceau absent : un blocage dit en clair.
        if (!c.sealOk()) {
            Kit.alert(g, x, y, w, 26);
            Kit.resource(g, Kit.Resource.SCEAU, x + 5, y + 7);
            text(g, fitBold(hub("chancery.seal.title").getString(), w - 26), x + 22, y + 4, 0xFFF2C9BF);
            String why = c.sealProblem().isEmpty() ? hub("chancery.seal.body").getString() : c.sealProblem();
            text(g, fit(why, w - 26), x + 22, y + 15, Kit.GRIS_PERLE);
            tip(x, y, w, 26, Component.literal(why));
            y += 32;
        }
        // La frise des rangs : l'actuel, le suivant, ceux d'après.
        List<TitleRank> ranks = new ArrayList<>();
        for (TitleRank r = c.rank(); r != null && ranks.size() < 4; r = r.next()) ranks.add(r);
        int span = w - 24;
        while (ranks.size() > 2 && span / (ranks.size() - 1) < 52) ranks.remove(ranks.size() - 1);
        int n = ranks.size();
        int[] cxs = new int[n];
        for (int i = 0; i < n; i++) cxs[i] = x + 12 + (n == 1 ? span / 2 : i * span / (n - 1));
        for (int i = 0; i + 1 < n; i++) g.fill(cxs[i] + 4, y + 3, cxs[i + 1] - 4, y + 5, i == 0 ? Kit.DORURE : Kit.FILET);
        for (int i = 0; i < n; i++) {
            int nx = cxs[i] - 4;
            if (c.freeHand() && i > 0) {
                // En créatif, chaque cran se clique : le titre est accordé tout de suite.
                int slotW = n == 1 ? w : span / (n - 1);
                TitleRank rank = ranks.get(i);
                click(cxs[i] - slotW / 2, y - 2, slotW, 34, () -> send(new DiplomacyPackets.ChanceryAction(
                        DiplomacyPackets.ChanceryAction.Kind.GRANT_TITLE, null, rank.ordinal(), 0, 0)));
                tip(cxs[i] - slotW / 2, y - 2, slotW, 34, hub("chancery.grant", tr(rank.titleKey())), hub("chancery.free").withStyle(ChatFormatting.GRAY));
                if (over(cxs[i] - slotW / 2, y - 2, slotW, 34)) g.fill(nx - 2, y - 2, nx + 10, y + 10, Kit.SURVOL);
            }
            g.fill(nx, y, nx + 8, y + 8, Kit.CONTOUR);
            if (i == 0) {
                g.fill(nx + 1, y + 1, nx + 7, y + 7, 0xFFC9A24A);
            } else {
                g.fill(nx + 1, y + 1, nx + 7, y + 7, Kit.PUITS);
                g.renderOutline(nx + 1, y + 1, 6, 6, i == 1 ? Kit.OR : Kit.FILET);
            }
            int slot = n == 1 ? w : span / (n - 1);
            Component label = Component.literal(fit(tr(ranks.get(i).titleKey()).getString(), slot - 2));
            Component name = i == 0 ? bold(label.getString()) : label;
            int nameX = Mth.clamp(cxs[i] - font.width(name) / 2, x, x + w - font.width(name));
            g.drawString(font, name, nameX, y + 12, i == 0 ? Kit.VALEUR : i == 1 ? Kit.OR : Kit.BLOQUE_TEXTE, false);
            Component when = hub(i == 0 ? "rank.current" : i == 1 ? "rank.next" : "rank.later");
            int whenX = Mth.clamp(cxs[i] - font.width(when) / 2, x, x + w - font.width(when));
            // « plus tard » une seule fois : deux de suite se touchent au bord du panneau.
            if (i <= 2 && font.width(when) <= slot - 4) g.drawString(font, when, whenX, y + 22, Kit.GRIS_DISCRET, false);
        }
        y += 36;
        // L'acte du prochain titre, sur parchemin.
        int bottom = ly + lh - 8;
        if (c.nextRank() == null) {
            Kit.deed(g, x, y, w, 26);
            text(g, fit(hub("realm.top").getString(), w - 16), x + 8, y + 9, Kit.ENCRE);
            renderChanceryRight(g);
            return;
        }
        int dh = Math.min(bottom - y, 112);
        Kit.deed(g, x, y, w, dh);
        Component deedTitle = Component.literal(tr(c.nextRank().titleKey()).getString() + " " + of(view.name()).getString());
        int ts = Kit.titleWidth(font, deedTitle, 2) <= w - 16 && dh >= 110 ? 2 : 1;
        Kit.title(g, font, fitGothic(deedTitle.getString(), (w - 16) / ts), x + 8, y + (ts == 2 ? 9 : 8), ts, Kit.ENCRE, false);
        int ry2 = y + (ts == 2 ? 28 : 21);
        List<Object[]> reqs = requirements(c);
        reqs.set(3, new Object[]{c.sealOk(), hub("req.seal.long"), hub(c.sealOk() ? "req.seal.ok" : "req.seal.missing").getString()});
        for (Object[] r : reqs) {
            boolean ok = (boolean) r[0];
            Kit.icon(g, ok ? Kit.Icon.REMPLI : Kit.Icon.FERMER, x + 8, ry2 - 1, ok ? INK_OK : INK_KO);
            text(g, (Component) r[1], x + 21, ry2, ok ? Kit.ENCRE : INK_KO);
            String v = (String) r[2];
            text(g, v, x + w - 8 - font.width(v), ry2, ok ? Kit.ENCRE : INK_KO);
            ry2 += 11;
        }
        ry2 += 3;
        Component create = c.freeHand() ? hub("chancery.grant", tr(c.nextRank().titleKey())) : tr("crusadecraft.chancery.create", tr(c.nextRank().titleKey()));
        button(g, x + 8, ry2, w - 16, 16, create, c.canRankUp(), true,
                () -> send(new DiplomacyPackets.ChanceryAction(DiplomacyPackets.ChanceryAction.Kind.CREATE_TITLE, null, -1, 0, 0)),
                tr(c.sealOk() ? "crusadecraft.chancery.create.tip" : "crusadecraft.chancery.seal_needed"));
        ry2 += 20;
        if (ry2 + 9 <= y + dh) wrap(g, c.freeHand() ? hub("chancery.free") : missing(c), x + 8, ry2, w - 16, INK_SOFT, Math.max(1, (y + dh - ry2) / 10));
        renderChanceryRight(g);
    }

    /** « Il manque 37 de prestige et le sceau royal. » */
    private Component missing(ChanceryView c) {
        List<String> parts = new ArrayList<>();
        if (c.provinces() < c.needProvinces()) parts.add(hub("chancery.missing.provinces", c.needProvinces() - c.provinces()).getString());
        if (c.gold() < c.needGold()) parts.add(hub("chancery.missing.gold", group(c.needGold() - c.gold())).getString());
        if (c.prestige() < c.needPrestige()) parts.add(hub("chancery.missing.prestige", c.needPrestige() - c.prestige()).getString());
        if (!c.sealOk()) parts.add(hub("chancery.missing.seal").getString());
        if (parts.isEmpty()) return hub("chancery.ready");
        String and = hub("chancery.and").getString();
        String joined = parts.size() == 1 ? parts.get(0)
                : String.join(", ", parts.subList(0, parts.size() - 1)) + and + parts.get(parts.size() - 1);
        return hub("chancery.missing", joined);
    }

    private void renderChanceryRight(GuiGraphics g) {
        ChanceryView c = chancery;
        Set<UUID> vassals = new HashSet<>();
        for (ChanceryView.Vassal v : c.vassals()) vassals.add(v.id());
        Set<String> claiming = new HashSet<>(), claimed = new HashSet<>();
        for (ChanceryView.Claim cl : c.claims()) claimed.add(cl.name());
        for (ChanceryView.Claim cl : c.candidates()) if (cl.province() == c.claimTarget()) claiming.add(cl.name());
        map.configure(vassals, claiming, claimed);
        List<LegendItem> legend = new ArrayList<>(List.of(new LegendItem(KingdomMap.DOMAIN, hub("map.domain"), false),
                new LegendItem(KingdomMap.VASSAL, hub("map.vassal"), false), new LegendItem(KingdomMap.KNOWN, hub("map.claimable"), false),
                new LegendItem(KingdomMap.CLAIMING, hub("map.claiming"), false)));
        if (!claimed.isEmpty()) legend.add(new LegendItem(KingdomMap.CLAIMED, hub("map.claimed"), false));
        int bottomH = Mth.clamp((int) (rh * 0.45), 100, 140);
        int mapBottom = mapBlock(g, hub("map.claims"), legend, bottomH, c.hasChancellor() ? null : hub("map.need_chancellor"));
        int x = rx + 10, w = rw - 20, y = mapBottom + 10, bottom = ry + rh - 8;
        int cw = (w - 12) / 2;
        renderClaims(g, c, x, y, cw, bottom);
        renderVassalContracts(g, c, x + cw + 12, y, cw, bottom);
    }

    private void renderClaims(GuiGraphics g, ChanceryView c, int x, int y, int w, int bottom) {
        Kit.section(g, font, hub("claims"), x, y, w, hub("claims.count", c.claims().size()), Kit.GRIS_DISCRET);
        y += 14;
        // Le chancelier, et ce qu'il fait.
        RealmView.Seat chan = holderOf(Post.CHANCELIER);
        if (chan == null) {
            text(g, fit(hub("claims.no_chancellor").getString(), w), x, y + 2, Kit.GUEULES);
            y += 14;
        } else {
            Kit.portraitFrame(g, x, y - 1, 18, 18, false);
            Faces.draw(g, chan.name(), chan.female(), x + 1, y, 2);
            Component chip = c.claimTarget() < 0 ? hub("claims.idle") : hub("claims.working", c.claimProgress());
            int chipW = font.width(chip) + 6;
            int room = w - 24 - chipW - 4;
            text(g, fitBold(chan.name(), room), x + 23, y, Kit.VALEUR);
            text(g, fit(tr(Post.CHANCELIER.key(chan.female())).getString(), room), x + 23, y + 9, Kit.GRIS_DISCRET);
            Kit.chip(g, font, chip, x + w - chipW, y + 2, c.claimTarget() < 0 ? Kit.Chip.AMBER : Kit.Chip.PROPOSAL);
            y += 22;
        }
        if (c.candidates().isEmpty()) {
            wrap(g, hub("claims.none"), x, y, w, Kit.GRIS_DISCRET, 2);
            return;
        }
        Component claim = tr("crusadecraft.chancery.claim");
        int bw = font.width(claim) + 10;
        for (int i = 0; i < c.candidates().size(); i++) {
            ChanceryView.Claim cl = c.candidates().get(i);
            if (y + 14 > bottom) {
                text(g, hub("more", c.candidates().size() - i), x, y + 2, Kit.GRIS_DISCRET);
                break;
            }
            Kit.icon(g, Kit.Icon.DOMAINE, x, y + 2, Kit.GRIS_PERLE);
            boolean working = c.claimTarget() == cl.province();
            int room = w - 14 - (working ? 44 : bw) - 4;
            text(g, fit(cl.name(), room), x + 14, y + 3, Kit.CREME);
            tip(x, y, room + 14, 13, Component.literal(cl.name()), ChanceryScreen.realmName(cl.realmPlace(), cl.realmRank()).copy().withStyle(ChatFormatting.GRAY),
                    tr("crusadecraft.chancery.distance", cl.distance()).withStyle(ChatFormatting.DARK_GRAY));
            if (working) {
                Kit.gauge(g, x + w - 40, y + 5, 40, c.claimProgress() / 100.0, Kit.AZUR_LISERE);
            } else {
                int province = cl.province();
                button(g, x + w - bw, y, bw, 13, claim, c.hasChancellor(), false,
                        () -> send(new DiplomacyPackets.ChanceryAction(DiplomacyPackets.ChanceryAction.Kind.CLAIM, null, province, 0, 0)),
                        tr(c.hasChancellor() ? "crusadecraft.chancery.claim.tip" : "crusadecraft.diplo.no_chancellor"));
            }
            y += 15;
        }
    }

    private void renderVassalContracts(GuiGraphics g, ChanceryView c, int x, int y, int w, int bottom) {
        Kit.section(g, font, hub("vassals"), x, y, w, Component.literal(String.valueOf(c.vassals().size())), Kit.GRIS_DISCRET);
        y += 14;
        if (c.vassals().isEmpty()) {
            wrap(g, tr("crusadecraft.chancery.no_vassals"), x, y, w, Kit.GRIS_DISCRET, 4);
            return;
        }
        int lockY = c.sealOk() ? bottom : bottom - 11;
        for (int i = 0; i < c.vassals().size(); i++) {
            ChanceryView.Vassal v = c.vassals().get(i);
            if (y + 40 > lockY) {
                text(g, hub("more", c.vassals().size() - i), x, y, Kit.GRIS_DISCRET);
                break;
            }
            g.fill(x, y, x + 8, y + 8, Kit.CONTOUR);
            g.fill(x + 1, y + 1, x + 7, y + 7, 0xFF000000 | v.color());
            Component share = hub("vassals.share");
            boolean showShare = font.width(share) + 70 <= w;
            text(g, fitBold(vassalName(v).getString(), w - 12 - (showShare ? font.width(share) + 6 : 0)), x + 12, y, Kit.VALEUR);
            if (showShare) right(g, share, x + w, y, Kit.GRIS_DISCRET);
            tip(x, y - 1, w, 10, vassalName(v), tr("crusadecraft.chancery.vassal.share", v.dailyShare()).withStyle(ChatFormatting.GRAY),
                    tr("crusadecraft.chancery.vassal.provinces", v.provinces()).withStyle(ChatFormatting.DARK_GRAY));
            y += 13;
            stepper(g, hub("vassal.tax.short"), v.tax(), x, y, w, c.sealOk(), d -> send(new DiplomacyPackets.ChanceryAction(
                    DiplomacyPackets.ChanceryAction.Kind.SET_CONTRACT, v.id(), -1, Mth.clamp(v.tax() + d, 0, 100), v.levy())), c);
            y += 15;
            stepper(g, hub("vassal.levy.short"), v.levy(), x, y, w, c.sealOk(), d -> send(new DiplomacyPackets.ChanceryAction(
                    DiplomacyPackets.ChanceryAction.Kind.SET_CONTRACT, v.id(), -1, v.tax(), Mth.clamp(v.levy() + d, 0, 100))), c);
            y += 18;
        }
        if (!c.sealOk()) {
            Kit.icon(g, Kit.Icon.VERROU, x, bottom - 10, Kit.GRIS_DISCRET);
            text(g, fit(hub("vassals.locked").getString(), w - 13), x + 13, bottom - 9, Kit.GRIS_DISCRET);
        }
    }

    private void stepper(GuiGraphics g, Component label, int value, int x, int y, int w, boolean active, java.util.function.IntConsumer change, ChanceryView c) {
        text(g, label, x, y + 2, Kit.GRIS_PERLE);
        Component tipText = active ? null : Component.literal(c.sealProblem().isEmpty() ? hub("chancery.seal.body").getString() : c.sealProblem());
        int bx = x + w - 13;
        button(g, bx, y, 13, 12, Component.literal("+"), active, false, () -> change.accept(5), tipText);
        int vw = 32;
        Kit.well(g, bx - 3 - vw, y, vw, 12);
        String v = value + "%";
        text(g, bold(v), bx - 3 - vw + (vw - font.width(bold(v))) / 2, y + 2, Kit.VALEUR);
        button(g, bx - 6 - vw - 13, y, 13, 12, Component.literal("−"), active, false, () -> change.accept(-5), tipText);
    }

    // ------------------------------------------------------------------ les pages larges

    private void renderWide(GuiGraphics g) {
        int x = lx, y = ly, w = rx + rw - lx, h = lh;
        Kit.anchored(g, x, y, w, h);
        Kit.Icon icon = switch (tab) {
            case TRESOR -> Kit.Icon.TRESOR;
            case GUERRE -> Kit.Icon.ARMEE;
            case DIPLOMATIE -> Kit.Icon.LETTRE;
            default -> Kit.Icon.CHRONIQUE;
        };
        String key = switch (tab) {
            case TRESOR -> "tresor";
            case GUERRE -> "armee";
            case DIPLOMATIE -> "voisins";
            default -> "chronique";
        };
        String sub = switch (tab) {
            case TRESOR -> "treasury.sub";
            case GUERRE -> "war.sub";
            case DIPLOMATIE -> "diplo.sub";
            default -> "chronicle.sub";
        };
        int hx = x + 10, hw = w - 20;
        if (tab == Tab.CHRONIQUES && (news != null || chronicle != null)) {
            // Les deux registres de la chronique, à droite de l'en-tête.
            Component reign = tr("crusadecraft.chronicle.reign"), world = tr("crusadecraft.chronicle.world");
            int bw = Math.max(font.width(reign), font.width(world)) + 14;
            button(g, hx + hw - bw, y + 14, bw, 15, world, !showWorld, false, () -> {
                showWorld = true;
                newsScroll = 0;
            }, null);
            button(g, hx + hw - 2 * bw - 4, y + 14, bw, 15, reign, showWorld, false, () -> {
                showWorld = false;
                newsScroll = 0;
            }, null);
            pageHeader(g, icon, hub("rail." + key), hub(sub), hx, y + 10, hw - 2 * bw - 12, hw);
        } else {
            pageHeader(g, icon, hub("rail." + key), hub(sub), hx, y + 10, hw);
        }
        int cx = x + 4, cy = y + 50, cw = w - 8, ch = h - 54;
        switch (tab) {
            case TRESOR -> renderTreasuryTab(g, cx, cy, cw, ch);
            case GUERRE -> renderWarTab(g, cx, cy, cw, ch);
            case DIPLOMATIE -> renderDiplomacyTab(g, cx, cy, cw, ch);
            default -> renderNewsTab(g, cx, cy, cw, ch);
        }
    }

    private void renderLoading(GuiGraphics g, int x, int y, int cw, int ch) {
        Ui.centered(g, font, tr("crusadecraft.tab.loading"), x + cw / 2, y + ch / 2 - 4, Ui.TEXT_MUTED);
    }

    private void renderTreasuryTab(GuiGraphics g, int x, int y, int cw, int ch) {
        if (treasury == null) {
            renderLoading(g, x, y, cw, ch);
            return;
        }
        boolean roomy = ch >= 150;
        List<Component> t = TreasuryPanel.render(g, font, treasury, x, y, cw, roomy ? ch - 10 : ch, mx, my);
        if (roomy) {
            String hint = font.plainSubstrByWidth(tr("crusadecraft.treasury.hint").getString(), cw - 16);
            Ui.text(g, font, Component.literal(hint), x + 8, y + ch - 11, Ui.TEXT_MUTED);
        }
        if (t != null) tip = t;
    }

    private void renderDiplomacyTab(GuiGraphics g, int x, int y, int cw, int ch) {
        if (diplomacy == null) {
            renderLoading(g, x, y, cw, ch);
            return;
        }
        Ui.text(g, font, tr("crusadecraft.diplo.known", diplomacy.entries().size()), x + 8, y + 7, Ui.TEXT_TITLE);
        if (diplomacy.mailArrived() > 0) {
            Component mail = tr("crusadecraft.diplo.mail_waiting", diplomacy.mailArrived());
            Ui.text(g, font, mail, x + cw - 8 - font.width(mail), y + 7, Kit.OR);
        }
        int ry0 = y + 20;
        int colW = (cw - 24) / 2;
        int i = 0;
        for (fr.crusadecraft.core.diplomacy.DiplomacyView.Entry e : diplomacy.entries()) {
            int col = i % 2, row = i / 2;
            int ex = x + 8 + col * (colW + 8), ey = ry0 + row * 14;
            if (ey > y + ch - 24) break;
            g.fill(ex, ey + 2, ex + 7, ey + 9, 0xFF000000 | e.color());
            String name = font.plainSubstrByWidth(DiplomacyScreen.entryName(e).getString(), colW - 90);
            Ui.text(g, font, Component.literal(name), ex + 10, ey + 1, Ui.TEXT);
            Component st = tr(e.status().key());
            Ui.text(g, font, st, ex + colW - font.width(st) - (e.ai() ? 26 : 0), ey + 1, e.status().color());
            if (e.ai()) {
                String op = signed(e.opinion());
                Ui.text(g, font, Component.literal(op), ex + colW - font.width(op), ey + 1, opinionColor(e.opinion()));
            }
            i++;
        }
        if (diplomacy.entries().isEmpty()) Ui.wrapped(g, font, tr("crusadecraft.diplo.nobody"), x + 8, ry0, cw - 16, Ui.TEXT_MUTED);
        Ui.text(g, font, tr("crusadecraft.diplo.hint"), x + 8, y + ch - 11, Ui.TEXT_MUTED);
    }

    /** Les nouvelles du monde : guerres, paix, frontières qui bougent, alliances, coalitions. */
    private void renderNewsTab(GuiGraphics g, int x, int y, int cw, int ch) {
        if (news == null && chronicle == null) {
            renderLoading(g, x, y, cw, ch);
            return;
        }
        List<WorldPackets.NewsLine> lines = showWorld ? this.news : this.chronicle;
        if (lines == null) lines = List.of();
        Ui.text(g, font, tr(showWorld ? "crusadecraft.news.title" : "crusadecraft.chronicle.title"), x + 8, y + 7, Ui.TEXT_TITLE);
        if (lines.isEmpty()) {
            Ui.wrapped(g, font, tr(showWorld ? "crusadecraft.news.none" : "crusadecraft.chronicle.none"), x + 8, y + 24, cw - 16, Ui.TEXT_MUTED);
            return;
        }
        int ly0 = y + 21;
        int bottom = y + ch - 4;
        int visible = 0;
        newsScroll = Math.max(0, Math.min(newsScroll, lines.size() - 1));
        for (int i = newsScroll; i < lines.size(); i++) {
            WorldPackets.NewsLine line = lines.get(i);
            Component day = tr("crusadecraft.war.log.day", line.day());
            int dw = font.width(day) + 4;
            List<FormattedCharSequence> parts = font.split(line.text(), cw - 16 - dw);
            if (ly0 + parts.size() * 10 > bottom) break;
            Ui.text(g, font, day, x + 8, ly0, Ui.TEXT_MUTED);
            for (FormattedCharSequence part : parts) {
                g.drawString(font, part, x + 8 + dw, ly0, Ui.TEXT, false);
                ly0 += 10;
            }
            ly0 += 3;
            visible++;
        }
        if (newsScroll + visible < lines.size()) {
            Component more = tr("crusadecraft.news.more", lines.size() - newsScroll - visible);
            Ui.text(g, font, more, x + cw - 8 - font.width(more), y + 7, Ui.TEXT_MUTED);
        }
    }

    /** L'onglet Guerre : l'armée et les guerres en cours ; les ordres se donnent à la Table de guerre. */
    private void renderWarTab(GuiGraphics g, int x, int y, int cw, int ch) {
        if (war == null) {
            renderLoading(g, x, y, cw, ch);
            return;
        }
        Component men = tr("crusadecraft.war.army.men", war.men(), war.maxMen());
        Ui.text(g, font, men, x + 8, y + 7, Ui.TEXT_TITLE);
        Component state = war.raised() ? tr("crusadecraft.war.army.state.raised", war.upkeep()) : tr("crusadecraft.war.army.state.home");
        Ui.text(g, font, state, x + cw - 8 - font.width(state), y + 7, war.raised() ? Kit.OR : Ui.TEXT_MUTED);
        int wy = y + 22;
        Ui.text(g, font, tr("crusadecraft.war.list.wars", war.wars().size()), x + 8, wy, Ui.TEXT_TITLE);
        wy += 13;
        if (war.wars().isEmpty()) Ui.wrapped(g, font, tr("crusadecraft.war.peaceful"), x + 8, wy, cw - 16, Ui.TEXT_MUTED);
        for (fr.crusadecraft.core.war.WarView.WarLine line : war.wars()) {
            if (wy > y + ch - 34) break;
            fr.crusadecraft.core.war.WarView.Party enemy = WarScreen.enemy(line);
            g.fill(x + 8, wy + 2, x + 15, wy + 9, 0xFF000000 | enemy.color());
            Ui.text(g, font, tr("crusadecraft.war.against", WarScreen.partyName(enemy)), x + 18, wy + 1, Ui.TEXT);
            int score = WarScreen.myScore(line);
            Component sc = tr("crusadecraft.war.score", WarScreen.signed(score));
            Ui.text(g, font, sc, x + cw - 8 - font.width(sc), wy + 1, WarScreen.scoreColor(score));
            Component goal = line.target().isEmpty() ? tr(line.cb().key()) : tr("crusadecraft.war.goal", tr(line.cb().key()), line.target());
            Component meta = tr("crusadecraft.war.meta", goal, line.days());
            Ui.text(g, font, Component.literal(font.plainSubstrByWidth(meta.getString(), cw - 34)), x + 18, wy + 11, Ui.TEXT_MUTED);
            wy += 24;
        }
        Ui.text(g, font, Component.literal(font.plainSubstrByWidth(tr("crusadecraft.war.tab.hint").getString(), cw - 16)), x + 8, y + ch - 11, Ui.TEXT_MUTED);
    }

    // ------------------------------------------------------------------ les gestes

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (Ui.inside(mouseX, mouseY, h.x(), h.y(), h.w(), h.h())) {
                    if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    h.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.CHRONIQUES && (news != null || chronicle != null)) {
            newsScroll -= (int) Math.signum(delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (fr.crusadecraft.client.KeyBindings.REALM_MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (modifiers == 0 && keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode == GLFW.GLFW_KEY_0 ? 9 : keyCode - GLFW.GLFW_KEY_1;
            activate(index);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
