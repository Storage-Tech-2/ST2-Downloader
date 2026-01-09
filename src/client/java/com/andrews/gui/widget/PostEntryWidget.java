package com.andrews.gui.widget;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.andrews.gui.theme.UITheme;
import com.andrews.models.ArchivePostSummary;
import com.andrews.util.RenderUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;

public class PostEntryWidget implements Renderable, GuiEventListener {
    private static final int MIN_ENTRY_HEIGHT = 70;
    private static final int LINE_HEIGHT = UITheme.Typography.LINE_HEIGHT;
    private static final int CONTENT_SPACING = 5;
    private static final int ENTRY_BG_COLOR = UITheme.Colors.BUTTON_BG;
    private static final int ENTRY_HOVER_COLOR = UITheme.Colors.BUTTON_BG_HOVER;
    private static final int ENTRY_PRESSED_COLOR = UITheme.Colors.BUTTON_BG_DISABLED;
    private static final int TEXT_COLOR = UITheme.Colors.TEXT_PRIMARY;
    private static final int BORDER_COLOR = UITheme.Colors.BUTTON_BORDER;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault());

    private final ArchivePostSummary post;
    private int x;
    private int y;
    private int width;
    private final Minecraft client;
    private final Runnable onClick;
    private boolean isHovered = false;
    private boolean isPressed = false;
    private int calculatedHeight = MIN_ENTRY_HEIGHT;

    public PostEntryWidget(ArchivePostSummary post, int x, int y, int width, Runnable onClick) {
        this.post = post;
        this.x = x;
        this.y = y;
        this.width = width;
        this.client = Minecraft.getInstance();
        this.onClick = onClick;
        calculateHeight();
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setWidth(int width) {
        this.width = width;
        calculateHeight();
    }

    private void calculateHeight() {
        int currentY = UITheme.Dimensions.PADDING;
        int contentWidth = width - UITheme.Dimensions.PADDING * 2;

        if (post.title() != null && !post.title().isEmpty()) {
            currentY += RenderUtil.getWrappedTextHeight(client.font, post.title(), contentWidth) + CONTENT_SPACING;
        }

        currentY += LINE_HEIGHT + CONTENT_SPACING;

        if (post.tags() != null && post.tags().length > 0) {
            StringBuilder tags = new StringBuilder();
            for (int i = 0; i < post.tags().length; i++) {
                if (i > 0) tags.append(", ");
                tags.append(post.tags()[i]);
            }
            currentY += RenderUtil.getWrappedTextHeight(client.font, tags.toString(), contentWidth) + CONTENT_SPACING;
        }

        currentY += UITheme.Dimensions.PADDING;
        calculatedHeight = Math.max(MIN_ENTRY_HEIGHT, currentY);
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        updateHoverState(mouseX, mouseY);

        int bgColor = getBackgroundColor();
        drawBackground(context, bgColor);
        drawBorder(context);

        renderContent(context);
    }

    private void updateHoverState(int mouseX, int mouseY) {
        this.isHovered = mouseX >= x && mouseY >= y &&
                        mouseX < x + width && mouseY < y + calculatedHeight;
    }

    private int getBackgroundColor() {
        if (isPressed && isHovered) {
            return ENTRY_PRESSED_COLOR;
        } else if (isHovered) {
            return ENTRY_HOVER_COLOR;
        }
        return ENTRY_BG_COLOR;
    }

    private void drawBackground(GuiGraphics context, int bgColor) {
        RenderUtil.fillRect(context, x, y, x + width, y + calculatedHeight, bgColor);
    }

    private void drawBorder(GuiGraphics context) {
        int borderWidth = UITheme.Dimensions.BORDER_WIDTH;
        RenderUtil.fillRect(context, x, y, x + width, y + borderWidth, BORDER_COLOR);
        RenderUtil.fillRect(context, x, y + calculatedHeight - borderWidth, x + width, y + calculatedHeight, BORDER_COLOR);
        RenderUtil.fillRect(context, x, y, x + borderWidth, y + calculatedHeight, BORDER_COLOR);
        RenderUtil.fillRect(context, x + width - borderWidth, y, x + width, y + calculatedHeight, BORDER_COLOR);
    }

    private void renderContent(GuiGraphics context) {
        int currentY = y + UITheme.Dimensions.PADDING;
        int contentWidth = width - UITheme.Dimensions.PADDING * 2;

        currentY = renderTitle(context, currentY, contentWidth);
        currentY = renderInfo(context, currentY);
        renderTags(context, currentY, contentWidth);
    }

    private int renderTitle(GuiGraphics context, int currentY, int contentWidth) {
        String title = post.title();
        if (title != null && !title.isEmpty()) {
            RenderUtil.drawWrappedText(context, client.font, title, x + UITheme.Dimensions.PADDING, currentY, contentWidth, TEXT_COLOR);
            currentY += RenderUtil.getWrappedTextHeight(client.font, title, contentWidth) + CONTENT_SPACING;
        }
        return currentY;
    }

    private int renderInfo(GuiGraphics context, int currentY) {
        String channelLabel = post.channelCode() != null && !post.channelCode().isEmpty()
            ? post.channelCode()
            : (post.channelName() != null ? post.channelName() : "Archive");
        String codeLabel = post.code() != null ? post.code() : "";
        String updatedLabel = post.updatedAt() > 0
            ? "Updated: " + formatDate(post.updatedAt())
            : (post.archivedAt() > 0 ? "Archived: " + formatDate(post.archivedAt()) : "Date unknown");

        String info = codeLabel.isEmpty()
            ? String.format("%s | %s", channelLabel, updatedLabel)
            : String.format("%s (%s) | %s", channelLabel, codeLabel, updatedLabel);

        RenderUtil.drawString(context, client.font, info,
            x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE);
        return currentY + LINE_HEIGHT + CONTENT_SPACING;
    }

    private void renderTags(GuiGraphics context, int currentY, int contentWidth) {
        if (post.tags() != null && post.tags().length > 0) {
            StringBuilder tags = new StringBuilder();
            for (int i = 0; i < post.tags().length; i++) {
                if (i > 0) tags.append(", ");
                tags.append(post.tags()[i]);
            }
            RenderUtil.drawWrappedText(context, client.font, tags.toString(), x + UITheme.Dimensions.PADDING, currentY, contentWidth, UITheme.Colors.TEXT_SUBTITLE);
        }
    }

    public int getHeight() {
        return calculatedHeight;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }


    public ArchivePostSummary getPost() {
        return post;
    }

    public void setPressed(boolean pressed) {
        this.isPressed = pressed;
    }

    public boolean isPressed() {
        return isPressed;
    }

    public void setHovered(boolean hovered) {
        this.isHovered = hovered;
    }

    public boolean isHovered() {
        return isHovered;
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (mouseX >= x && mouseX < x + width &&
            mouseY >= y && mouseY < y + calculatedHeight) {
            isPressed = true;
            if (onClick != null) {
                onClick.run();
            }
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isPressed) {
            isPressed = false;
            return true;
        }
        return false;
    }

    private String formatDate(long millis) {
        if (millis <= 0) {
            return "Unknown";
        }
        return DATE_FORMATTER.format(Instant.ofEpochMilli(millis));
    }
}
