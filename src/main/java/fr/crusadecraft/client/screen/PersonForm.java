package fr.crusadecraft.client.screen;

import fr.crusadecraft.core.character.Trait;
import fr.crusadecraft.core.lineage.LineageView;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.LineagePackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * La fiche d'une personne du registre (P20) : prénom, âge, jusqu'à quatre traits de caractère (deux contraires ne
 * vont pas ensemble) et une biographie libre. Le serveur revérifie tout et renvoie le registre à jour.
 */
public class PersonForm extends Screen {
    private final LineageScreen parent;
    private final LineageView.Row person;
    private final String firstName;
    private EditBox name, age;
    private MultiLineEditBox bio;
    private int mask;
    private int left, top, fw, fh;

    public PersonForm(LineageScreen parent, LineageView.Row person, String firstName) {
        super(Component.translatable("crusadecraft.person.title"));
        this.parent = parent;
        this.person = person;
        this.firstName = firstName;
        this.mask = person.traitMask();
    }

    public LineageScreen parent() {
        return parent;
    }

    private static boolean has(int mask, Trait t) {
        return (mask & (1 << t.ordinal())) != 0;
    }

    private int count() {
        return Integer.bitCount(mask);
    }

    @Override
    protected void init() {
        fw = Math.min(width - 16, 420);
        fh = Math.min(height - 16, 250);
        left = (width - fw) / 2;
        top = (height - fh) / 2;
        String n = name == null ? firstName : name.getValue();
        String a = age == null ? String.valueOf(person.age()) : age.getValue();
        String b = bio == null ? person.bio() : bio.getValue();
        name = addRenderableWidget(new EditBox(font, left + 60, top + 24, 120, 16, Component.translatable("crusadecraft.person.name")));
        name.setMaxLength(24);
        name.setValue(n);
        age = addRenderableWidget(new EditBox(font, left + fw - 60, top + 24, 46, 16, Component.translatable("crusadecraft.person.age")));
        age.setMaxLength(3);
        age.setFilter(s -> s.chars().allMatch(Character::isDigit));
        age.setValue(a);

        // Les traits, en grille : un clic les prend ou les rend ; un contraire est grisé.
        Trait[] all = Trait.values();
        int cols = 5, bw = (fw - 24 - (cols - 1) * 2) / cols, y0 = top + 56;
        for (int i = 0; i < all.length; i++) {
            Trait t = all[i];
            boolean on = has(mask, t);
            boolean blocked = !on && (count() >= Person.MAX_TRAITS || (t.opposite() != null && has(mask, t.opposite())));
            Component label = Component.translatable(t.key()).withStyle(on ? ChatFormatting.GOLD : ChatFormatting.RESET);
            Button button = Button.builder(Component.literal(font.plainSubstrByWidth((on ? "✔ " : "") + label.getString(), bw - 6)), x -> {
                mask ^= 1 << t.ordinal();
                rebuildWidgets();
            }).bounds(left + 12 + (i % cols) * (bw + 2), y0 + (i / cols) * 15, bw, 14).build();
            button.active = on || !blocked;
            button.setTooltip(Tooltip.create(Component.translatable(t.descriptionKey())));
            addRenderableWidget(button);
        }
        int rows = (all.length + cols - 1) / cols;
        int bioTop = y0 + rows * 15 + 14;
        bio = addRenderableWidget(new MultiLineEditBox(font, left + 12, bioTop, fw - 24, Math.max(30, top + fh - 30 - bioTop),
                Component.translatable("crusadecraft.person.bio.hint"), Component.translatable("crusadecraft.person.bio")));
        bio.setCharacterLimit(Person.BIO_MAX);
        bio.setValue(b);
        addRenderableWidget(Button.builder(Component.translatable("crusadecraft.person.save"), x -> {
            int years = age.getValue().isEmpty() ? person.age() : Math.min(120, Integer.parseInt(age.getValue()));
            CrusadeNetwork.toServer(new LineagePackets.Action(LineagePackets.Action.Op.EDIT, person.id(), name.getValue() + "\n" + bio.getValue(),
                    years, mask, null, parent.shownForForm()));
            onClose();
        }).bounds(left + fw / 2 - 94, top + fh - 24, 90, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), x -> onClose()).bounds(left + fw / 2 + 4, top + fh - 24, 90, 18).build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        Ui.panel(g, left, top, fw, fh);
        Ui.scaled(g, font, Component.literal(person.name()), left + 12, top + 8, 1.2f, Ui.TEXT_TITLE, false);
        Ui.text(g, font, Component.translatable("crusadecraft.person.name"), left + 12, top + 28, Ui.TEXT);
        Ui.text(g, font, Component.translatable("crusadecraft.person.age"), left + fw - 60 - 6 - font.width(Component.translatable("crusadecraft.person.age")),
                top + 28, Ui.TEXT);
        Ui.text(g, font, Component.translatable("crusadecraft.person.traits", count(), Person.MAX_TRAITS), left + 12, top + 45, Ui.TEXT_MUTED);
        int rows = (Trait.values().length + 4) / 5;
        Ui.text(g, font, Component.translatable("crusadecraft.person.bio"), left + 12, top + 56 + rows * 15 + 3, Ui.TEXT_MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
