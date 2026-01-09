package com.andrews.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import org.joml.Matrix3x2fStack;

public final class RenderUtil {
    private RenderUtil() {}

    public static void drawScaledString(GuiGraphics context, String text, int x, int y, int color, float scale) {
        if (text == null || text.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font font = client.font;
        Matrix3x2fStack matrix = context.pose().pushMatrix();
        context.pose().translate(x, y, matrix);
        context.pose().scale(scale, scale, matrix);
        context.drawString(font, text, 0, 0, color);
        context.pose().popMatrix();
    }

    public static void drawScaledString(GuiGraphics context, String text, int x, int y, int color, float scale, int maxWidth) {
        if (text == null || text.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font font = client.font;
        String clipped = text;
        if (maxWidth > 0) {
            float scaledWidth = font.width(text) * scale;
            if (scaledWidth > maxWidth) {
                while (!clipped.isEmpty() && font.width(clipped + "...") * scale > maxWidth) {
                    clipped = clipped.substring(0, clipped.length() - 1);
                }
                clipped = clipped + "...";
            }
        }
        drawScaledString(context, clipped, x, y, color, scale);
    }
}
