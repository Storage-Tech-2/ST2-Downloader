package com.andrews.gui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public final class RenderUtil {
    private RenderUtil() {}

    public static void drawScaledString(DrawContext context, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer font = client.textRenderer;
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1);
        context.drawText(font, text, 0, 0, color, false);
        context.getMatrices().pop();
    }

    public static void drawScaledString(DrawContext context, String text, int x, int y, int color, float scale, int maxWidth) {
        if (text == null || text.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer font = client.textRenderer;
        String clipped = text;
        if (maxWidth > 0) {
            float scaledWidth = font.getWidth(text) * scale;
            if (scaledWidth > maxWidth) {
                while (!clipped.isEmpty() && font.getWidth(clipped + "...") * scale > maxWidth) {
                    clipped = clipped.substring(0, clipped.length() - 1);
                }
                clipped = clipped + "...";
            }
        }
        drawScaledString(context, clipped, x, y, color, scale);
    }
}
