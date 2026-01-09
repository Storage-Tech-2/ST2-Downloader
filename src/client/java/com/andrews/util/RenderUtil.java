package com.andrews.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.pipeline.RenderPipeline;

public final class RenderUtil {
    private RenderUtil() {}

    public static void fillRect(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y2, color);
    }

    public static void enableScissor(GuiGraphics context, int x1, int y1, int x2, int y2) {
        context.enableScissor(x1, y1, x2, y2);
    }

    public static void disableScissor(GuiGraphics context) {
        context.disableScissor();
    }

    public static void drawString(GuiGraphics context, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return;
        context.drawString(font, text, x, y, color);
    }

    public static void drawString(GuiGraphics context, Font font, Component text, int x, int y, int color) {
        if (text == null) return;
        context.drawString(font, text, x, y, color);
    }

    public static void drawCenteredString(GuiGraphics context, Font font, String text, int centerX, int y, int color) {
        if (text == null || text.isEmpty()) return;
        context.drawCenteredString(font, text, centerX, y, color);
    }

    public static void blit(GuiGraphics context, RenderPipeline pipeline, Identifier texture, int x, int y, int u, int v, int width, int height, int texWidth, int texHeight) {
        context.blit(pipeline, texture, x, y, u, v, width, height, texWidth, texHeight);
    }

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
