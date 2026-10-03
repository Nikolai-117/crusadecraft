package fr.crusadecraft.client.screen;

import fr.crusadecraft.network.packet.WorldPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Le livre des chroniques, mis en pages comme un livre écrit : une page de titre, puis les faits du règne jour après
 * jour, coupés à la largeur d'une page de livre.
 */
public final class ChronicleBook implements BookViewScreen.BookAccess {
    private static final int PAGE_WIDTH = 114;
    private static final int PAGE_LINES = 14;

    private final List<FormattedText> pages = new ArrayList<>();

    public ChronicleBook(String realm, String house, List<WorldPackets.NewsLine> lines) {
        Font font = Minecraft.getInstance().font;
        MutableComponent title = Component.literal("\n\n").append(Component.literal(realm).withStyle(ChatFormatting.BOLD))
                .append("\n\n").append(Component.translatable("crusadecraft.chronicle.title").withStyle(ChatFormatting.ITALIC))
                .append("\n\n").append(Component.translatable("crusadecraft.chronicles.house", house));
        pages.add(title);
        MutableComponent page = Component.empty();
        int used = 0;
        if (lines.isEmpty()) page.append(Component.translatable("crusadecraft.chronicle.none").withStyle(ChatFormatting.ITALIC));
        for (WorldPackets.NewsLine l : lines) {
            Component entry = Component.translatable("crusadecraft.chronicles.entry", l.day(), l.text());
            int height = font.split(entry, PAGE_WIDTH).size() + 1;
            if (used > 0 && used + height > PAGE_LINES) {
                pages.add(page);
                page = Component.empty();
                used = 0;
            }
            if (used > 0) page.append("\n\n");
            page.append(entry);
            used += height;
        }
        pages.add(page);
    }

    @Override
    public int getPageCount() {
        return pages.size();
    }

    @Override
    public FormattedText getPageRaw(int index) {
        return pages.get(index);
    }
}
