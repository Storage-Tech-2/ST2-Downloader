package com.andrews.gui.widget;

import com.andrews.gui.theme.UITheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

public class CustomButton extends ButtonWidget {
    private boolean renderAsXIcon = false;
    private boolean renderAsDownloadIcon = false;

    public CustomButton(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    public void setRenderAsXIcon(boolean renderAsXIcon) {
        this.renderAsXIcon = renderAsXIcon;
    }

    public void setRenderAsDownloadIcon(boolean renderAsDownloadIcon) {
        this.renderAsDownloadIcon = renderAsDownloadIcon;
    }

    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        int bgColor = getBackgroundColor(mouseX, mouseY);
        drawBackground(context, bgColor);
        drawBorder(context);
        drawText(context);
    }

    private int getBackgroundColor(int mouseX, int mouseY) {
        if (!this.active) {
            return UITheme.Colors.BUTTON_BG_DISABLED;
        }

        boolean isHovered = mouseX >= this.getX() && mouseY >= this.getY() &&
                           mouseX < this.getX() + this.getWidth() && mouseY < this.getY() + this.getHeight();

        return isHovered ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.BUTTON_BG;
    }

    private void drawBackground(DrawContext context, int color) {
        context.fill(this.getX(), this.getY(), this.getX() + this.getWidth(),
                    this.getY() + this.getHeight(), color);
    }

    private void drawBorder(DrawContext context) {
        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = x1 + this.getWidth();
        int y2 = y1 + this.getHeight();
        int borderColor = UITheme.Colors.BUTTON_BORDER;
        int borderWidth = UITheme.Dimensions.BORDER_WIDTH;

        context.fill(x1, y1, x2, y1 + borderWidth, borderColor);
        context.fill(x1, y2 - borderWidth, x2, y2, borderColor);
        context.fill(x1, y1, x1 + borderWidth, y2, borderColor);
        context.fill(x2 - borderWidth, y1, x2, y2, borderColor);
    }

    private void drawText(DrawContext context) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int textColor = this.active ? UITheme.Colors.TEXT_PRIMARY : UITheme.Colors.TEXT_DISABLED;

        String text = getDisplayText();
        int yOffset = this.getMessage().getString().equals("⚙") ? 0 : 1;
        int centerX = this.getX() + this.getWidth() / 2;
        int centerY = this.getY() + (this.getHeight() - UITheme.Typography.TEXT_HEIGHT) / 2 + yOffset;

        context.drawCenteredTextWithShadow(tr, text, centerX, centerY, textColor);
    }

    private String getDisplayText() {
        if (renderAsXIcon) {
            return "✕";
        } else if (renderAsDownloadIcon) {
            return "💾";
        }
        return this.getMessage().getString();
    }
}
