package com.andrews.gui.widget;

import com.andrews.gui.theme.UITheme;
import com.andrews.models.ArchiveChannel;
import com.andrews.util.RenderUtil;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ChannelDescriptionWidget {
    private int x;
    private int y;
    private int width;
    private int height;
    private ArchiveChannel channel;

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setChannel(ArchiveChannel channel) {
        this.channel = channel;
    }

    public void render(GuiGraphics context, Font font) {
        RenderUtil.fillRect(context, x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);
        RenderUtil.fillRect(context, x, y, x + width, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + height, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x + width - UITheme.Dimensions.BORDER_WIDTH, y, x + width, y + height, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y + height - UITheme.Dimensions.BORDER_WIDTH, x + width, y + height, UITheme.Colors.BUTTON_BORDER);

        int textX = x + UITheme.Dimensions.PADDING;
        int textY = y + UITheme.Dimensions.PADDING;

        String desc = channel != null && channel.description() != null
            ? channel.description()
            : "Click on a channel to filter posts";
        int maxWidth = width - UITheme.Dimensions.PADDING * 2;
        drawWrappedText(context, font, desc, textX, textY, maxWidth, UITheme.Colors.TEXT_PRIMARY);
    }

    private void drawWrappedText(GuiGraphics context, Font font, String text, int x, int y, int maxWidth, int color) {
        if (text == null || text.isEmpty()) return;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int lineY = y;
        for (String word : words) {
            String test = line.length() > 0 ? line + " " + word : word;
            if (font.width(test) > maxWidth && line.length() > 0) {
                RenderUtil.drawString(context, font, line.toString(), x, lineY, color);
                line = new StringBuilder(word);
                lineY += font.lineHeight + 2;
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) {
            RenderUtil.drawString(context, font, line.toString(), x, lineY, color);
        }
    }
}
