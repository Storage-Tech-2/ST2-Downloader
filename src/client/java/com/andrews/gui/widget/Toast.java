package com.andrews.gui.widget;

import com.andrews.gui.theme.UITheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class Toast {
    public enum Type {
        SUCCESS,
        ERROR,
        INFO,
        WARNING
    }

    private static final int TOAST_WIDTH = UITheme.Dimensions.TOAST_MAX_WIDTH;
    private static final int TOAST_HEIGHT = UITheme.Dimensions.TOAST_HEIGHT;
    private static final long SLIDE_DURATION = 300;
    private static final long ERROR_DISPLAY_DURATION = 8000;
    private static final int ACCENT_BORDER_WIDTH = 4;
    private static final int BUTTON_SPACING = 4;
    private static final int CLOSE_BUTTON_SIZE = 16;
    private static final int COPY_BUTTON_WIDTH = 50;
    private static final int COPY_BUTTON_HEIGHT = 18;
    private static final int TOAST_EXTRA_HEIGHT = 20;
    private static final int Y_TRANSITION_DURATION = 400;
    private static final int TOAST_SPACING = 5;

    private static double mouseX;
    private static double mouseY;

    private final String message;
    private final Type type;
    private final long createdTime;
    private final int screenWidth;
    private final boolean hasCopyButton;
    private final String copyText;

    private int yPosition;
    private int targetYPosition;
    private long lastYPositionChange;
    private CustomButton copyButton;
    private final CustomButton closeButton;
    private boolean dismissed;
    private boolean hovered;
    private long pausedTime;
    private long hoverStartTime;

    public Toast(String message, Type type, int screenWidth, int yPosition) {
        this(message, type, screenWidth, yPosition, false, null);
    }

    public Toast(String message, Type type, int screenWidth, int yPosition, boolean hasCopyButton, String copyText) {
        this.message = message;
        this.type = type;
        this.screenWidth = screenWidth;
        this.yPosition = yPosition;
        this.targetYPosition = yPosition;
        this.createdTime = System.currentTimeMillis();
        this.lastYPositionChange = createdTime;
        this.hasCopyButton = hasCopyButton;
        this.copyText = copyText != null ? copyText : message;

        closeButton = new CustomButton(0, 0, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE, Component.nullToEmpty("×"), btn -> {});

        if (hasCopyButton) {
            copyButton = new CustomButton(0, 0, COPY_BUTTON_WIDTH, COPY_BUTTON_HEIGHT, Component.nullToEmpty("Copy"), btn -> {});
        }
    }

    private int getAccentColor() {
        return switch (type) {
            case SUCCESS -> UITheme.Colors.TOAST_ACCENT_SUCCESS;
            case ERROR -> UITheme.Colors.TOAST_ACCENT_ERROR;
            case INFO -> UITheme.Colors.TOAST_ACCENT_INFO;
            case WARNING -> UITheme.Colors.TOAST_ACCENT_WARNING;
        };
    }

    public void setTargetYPosition(int targetY) {
        if (this.targetYPosition != targetY) {
            this.targetYPosition = targetY;
            this.lastYPositionChange = System.currentTimeMillis();
        }
    }

    public boolean render(GuiGraphics context, net.minecraft.client.gui.Font textRenderer) {
        long now = System.currentTimeMillis();
        long elapsed = getEffectiveElapsedTime(now);
        long displayDuration = getDisplayDuration();

        if (shouldRemove(elapsed, displayDuration)) {
            return true;
        }

        updateYPosition(now);

        float slideProgress = calculateSlideProgress(elapsed);
        float fadeProgress = calculateFadeProgress(elapsed, displayDuration);
        int alpha = (int) (255 * fadeProgress);

        if (alpha <= 0) {
            return true;
        }

        int currentX = calculateXPosition(slideProgress);
        int toastHeight = getToastHeight();

        renderToastBackground(context, currentX, toastHeight, alpha);
        renderIcon(context, textRenderer, currentX, alpha);
        renderMessage(context, textRenderer, currentX, alpha);
        renderCloseButton(context, currentX);

        if (hasCopyButton) {
            renderCopyButton(context, currentX, toastHeight);
        }

        return false;
    }

    private long getEffectiveElapsedTime(long now) {
        long totalPausedTime = pausedTime;
        if (hovered && hoverStartTime > 0) {
            totalPausedTime += now - hoverStartTime;
        }
        return now - createdTime - totalPausedTime;
    }

    private long getDisplayDuration() {
        return hasCopyButton ? ERROR_DISPLAY_DURATION : UITheme.Animation.TOAST_DISPLAY_DURATION;
    }

    private boolean shouldRemove(long elapsed, long displayDuration) {
        return dismissed || elapsed > SLIDE_DURATION + displayDuration + UITheme.Animation.TOAST_FADE_DURATION;
    }

    private void updateYPosition(long now) {
        if (yPosition != targetYPosition) {
            long yTransitionElapsed = now - lastYPositionChange;
            float yTransitionProgress = Math.min(1.0f, yTransitionElapsed / (float) Y_TRANSITION_DURATION);
            yTransitionProgress = (float)(1 - Math.pow(1 - yTransitionProgress, 3));
            yPosition = (int)(yPosition + (targetYPosition - yPosition) * yTransitionProgress);

            if (Math.abs(yPosition - targetYPosition) < 1) {
                yPosition = targetYPosition;
            }
        }
    }

    private float calculateSlideProgress(long elapsed) {
        float progress = Math.min(1.0f, elapsed / (float) SLIDE_DURATION);
        return 1 - (float) Math.pow(1 - progress, 3);
    }

    private float calculateFadeProgress(long elapsed, long displayDuration) {
        if (elapsed > SLIDE_DURATION + displayDuration) {
            long fadeElapsed = elapsed - SLIDE_DURATION - displayDuration;
            return 1.0f - (fadeElapsed / (float) UITheme.Animation.TOAST_FADE_DURATION);
        }
        return 1.0f;
    }

    private int calculateXPosition(float slideProgress) {
        int targetX = screenWidth - TOAST_WIDTH - UITheme.Dimensions.PADDING;
        int startX = screenWidth + TOAST_WIDTH;
        return (int) (startX + (targetX - startX) * slideProgress);
    }

    private int getToastHeight() {
        return hasCopyButton ? TOAST_HEIGHT + TOAST_EXTRA_HEIGHT : TOAST_HEIGHT;
    }

    private void renderToastBackground(GuiGraphics context, int currentX, int toastHeight, int alpha) {
        int bgColor = UITheme.Colors.BUTTON_BG_DISABLED;
        int bgColorWithAlpha = (alpha << 24) | (bgColor & 0x00FFFFFF);
        context.fill(currentX, yPosition, currentX + TOAST_WIDTH, yPosition + toastHeight, bgColorWithAlpha);

        int borderColor = getAccentColor();
        int borderColorWithAlpha = (alpha << 24) | (borderColor & 0x00FFFFFF);
        context.fill(currentX, yPosition, currentX + ACCENT_BORDER_WIDTH, yPosition + toastHeight, borderColorWithAlpha);

        int topBorderColor = UITheme.Colors.PANEL_BORDER;
        int topBorderColorWithAlpha = (alpha << 24) | (topBorderColor & 0x00FFFFFF);
        context.fill(currentX, yPosition, currentX + TOAST_WIDTH, yPosition + UITheme.Dimensions.BORDER_WIDTH, topBorderColorWithAlpha);
        context.fill(currentX, yPosition + toastHeight - UITheme.Dimensions.BORDER_WIDTH, currentX + TOAST_WIDTH, yPosition + toastHeight, topBorderColorWithAlpha);
        context.fill(currentX + TOAST_WIDTH - UITheme.Dimensions.BORDER_WIDTH, yPosition, currentX + TOAST_WIDTH, yPosition + toastHeight, topBorderColorWithAlpha);
    }

    private void renderIcon(GuiGraphics context, net.minecraft.client.gui.Font textRenderer, int currentX, int alpha) {
        String icon = getTypeIcon();
        int iconColor = getAccentColor();
        int iconColorWithAlpha = (alpha << 24) | (iconColor & 0x00FFFFFF);
        context.drawString(
                textRenderer,
                icon,
                currentX + UITheme.Typography.LINE_HEIGHT,
                yPosition + UITheme.Typography.TEXT_HEIGHT,
                iconColorWithAlpha,
                false
        );
    }

    private String getTypeIcon() {
        return switch (type) {
            case SUCCESS -> "✓";
            case ERROR -> "✗";
            case WARNING -> "⚠";
            case INFO -> "ℹ";
        };
    }

    private void renderMessage(GuiGraphics context, net.minecraft.client.gui.Font textRenderer, int currentX, int alpha) {
        int textX = currentX + 28;
        int textY = yPosition + UITheme.Typography.TEXT_HEIGHT;
        int maxTextWidth = TOAST_WIDTH - 40;

        String displayText = truncateText(textRenderer, message, maxTextWidth);

        int textColor = UITheme.Colors.TEXT_PRIMARY;
        int textColorWithAlpha = (alpha << 24) | (textColor & 0x00FFFFFF);

        context.drawString(
                textRenderer,
                displayText,
                textX,
                textY,
                textColorWithAlpha,
                false
        );
    }

    private String truncateText(net.minecraft.client.gui.Font textRenderer, String text, int maxWidth) {
        String displayText = text;
        int textWidth = textRenderer.width(displayText);

        if (textWidth > maxWidth) {
            while (textWidth > maxWidth - UITheme.Dimensions.PADDING && displayText.length() > 3) {
                displayText = displayText.substring(0, displayText.length() - 1);
                textWidth = textRenderer.width(displayText + "...");
            }
            displayText += "...";
        }

        return displayText;
    }

    private void renderCloseButton(GuiGraphics context, int currentX) {
        if (closeButton != null) {
            int closeButtonX = currentX + TOAST_WIDTH - CLOSE_BUTTON_SIZE - BUTTON_SPACING;
            int closeButtonY = yPosition + BUTTON_SPACING;

            closeButton.setX(closeButtonX);
            closeButton.setY(closeButtonY);
            closeButton.setWidth(CLOSE_BUTTON_SIZE);
            closeButton.setHeight(CLOSE_BUTTON_SIZE);

            closeButton.render(context, (int)mouseX, (int)mouseY, 0);
        }
    }

    private void renderCopyButton(GuiGraphics context, int currentX, int toastHeight) {
        if (copyButton != null) {
            int buttonX = currentX + TOAST_WIDTH - COPY_BUTTON_WIDTH - UITheme.Dimensions.PADDING_SMALL - 3;
            int buttonY = yPosition + toastHeight - COPY_BUTTON_HEIGHT - 6;

            copyButton.setX(buttonX);
            copyButton.setY(buttonY);
            copyButton.setWidth(COPY_BUTTON_WIDTH);
            copyButton.setHeight(COPY_BUTTON_HEIGHT);

            copyButton.render(context, (int)mouseX, (int)mouseY, 0);
        }
    }

    public boolean isHovering(double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        long elapsed = now - createdTime;
        long displayDuration = getDisplayDuration();

        if (dismissed || elapsed > SLIDE_DURATION + displayDuration) {
            return false;
        }

        float slideProgress = calculateSlideProgress(elapsed);
        int currentX = calculateXPosition(slideProgress);
        int toastHeight = getToastHeight();

        return mouseX >= currentX && mouseX < currentX + TOAST_WIDTH &&
               mouseY >= yPosition && mouseY < yPosition + toastHeight;
    }

    public boolean isCloseButtonClicked(double mouseX, double mouseY) {
        if (closeButton == null) {
            return false;
        }
        return isWithinBounds(mouseX, mouseY, closeButton.getX(), closeButton.getY(), closeButton.getWidth(), closeButton.getHeight());
    }

    public boolean isCopyButtonClicked(double mouseX, double mouseY) {
        if (!hasCopyButton || copyButton == null) {
            return false;
        }
        return isWithinBounds(mouseX, mouseY, copyButton.getX(), copyButton.getY(), copyButton.getWidth(), copyButton.getHeight());
    }

    private boolean isWithinBounds(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static void updateMousePosition(double x, double y) {
        mouseX = x;
        mouseY = y;
    }

    public void dismiss() {
        this.dismissed = true;
    }

    public void setHovered(boolean hovered) {
        if (hovered && !this.hovered) {
            this.hoverStartTime = System.currentTimeMillis();
        } else if (!hovered && this.hovered) {
            this.pausedTime += System.currentTimeMillis() - this.hoverStartTime;
        }
        this.hovered = hovered;
    }

    public boolean isHovered() {
        return hovered;
    }

    public int getHeight() {
        return getToastHeight() + TOAST_SPACING;
    }

    public String getCopyText() {
        return copyText;
    }
}

