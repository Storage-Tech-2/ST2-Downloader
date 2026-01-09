package com.andrews.gui.widget;

import com.andrews.gui.theme.UITheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

public class CustomButton extends ButtonWidget {
    private boolean renderAsXIcon = false;

    public CustomButton(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    public void setRenderAsXIcon(boolean renderAsXIcon) {
        this.renderAsXIcon = renderAsXIcon;
    }

    @Override
    public void drawMessage(DrawContext context, TextRenderer textRenderer, int color) {
       
        String text = getDisplayText();
        int yOffset = 0;
        int centerX = this.getX() + this.getWidth() / 2;
        int centerY = this.getY() + (this.getHeight() - UITheme.Typography.TEXT_HEIGHT) / 2 + yOffset;

        RenderUtil.drawCenteredText(context, textRenderer, text, centerX, centerY, color);
    }

    private String getDisplayText() {
        if (renderAsXIcon) {
            return "✕";
        }
        return this.getMessage().getString();
    }
}
