package com.andrews.gui.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import com.andrews.gui.theme.UITheme;

public class DropdownWidget implements Drawable, Element {
    private static final int ITEM_HEIGHT = 24;
    private static final int MAX_VISIBLE_ITEMS = 6;
    private static final int SCROLLBAR_SIDE_SPACING = 2;

    private final MinecraftClient client;
    private int x;
    private int y;
    private int width;
    private final List<DropdownItem> items;
    private final Consumer<DropdownItem> onSelect;
    private boolean isOpen;
    private int hoveredIndex = -1;
    private double scrollOffset = 0;
    private int maxScrollOffset = 0;
    private String statusMessage = "";
    private ScrollBar scrollBar;
    private int lastScrollBarX = -1;
    private int lastScrollBarY = -1;
    private int lastScrollBarHeight = -1;

    public DropdownWidget(int x, int y, int width, Consumer<DropdownItem> onSelect) {
        this.client = MinecraftClient.getInstance();
        this.x = x;
        this.y = y;
        this.width = width;
        this.items = new ArrayList<>();
        this.onSelect = onSelect;
        this.isOpen = false;
        this.scrollBar = new ScrollBar(x + width - 10, y + 1, 100);
    }

    public void setStatusMessage(String message) {
        this.statusMessage = message != null ? message : "";
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setItems(List<DropdownItem> items) {
        this.items.clear();
        this.items.addAll(items);
        this.scrollOffset = 0;
        updateScrollBounds();
    }

    public void open() {
        this.isOpen = true;
    }

    public void close() {
        this.isOpen = false;
        this.hoveredIndex = -1;
    }

    public boolean isOpen() {
        return isOpen;
    }

    private void updateScrollBounds() {
        int visibleHeight = Math.min(items.size(), MAX_VISIBLE_ITEMS) * ITEM_HEIGHT;
        int contentHeight = items.size() * ITEM_HEIGHT;
        maxScrollOffset = Math.max(0, contentHeight - visibleHeight);
    }

    private int getDropdownHeight() {
        if (!isOpen || items.isEmpty()) return 0;
        int itemCount = Math.min(items.size(), MAX_VISIBLE_ITEMS);
        int baseHeight = itemCount * ITEM_HEIGHT + UITheme.Dimensions.BORDER_WIDTH * 2;
        if (!statusMessage.isEmpty()) {
            baseHeight += ITEM_HEIGHT;
        }
        return baseHeight;
    }

    private int getRenderWidth() {
        return width + 1;
    }

    private int getItemRightEdge(boolean hasScrollbar) {
        int renderWidth = getRenderWidth();
        if (hasScrollbar) {
            return x + renderWidth - UITheme.Dimensions.SCROLLBAR_WIDTH - SCROLLBAR_SIDE_SPACING - 1;
        }
        return x + renderWidth - 1;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isOpen || items.isEmpty()) return;

        int height = getDropdownHeight();
        int renderWidth = getRenderWidth();

        drawBackground(context, height, renderWidth);
        drawBorder(context, height, renderWidth);
        renderItems(context, mouseX, mouseY, renderWidth);
        renderStatusMessage(context, renderWidth);
        renderScrollbar(context, mouseX, mouseY, delta);
    }

    private void drawBackground(DrawContext context, int height, int renderWidth) {
        context.fill(x, y, x + renderWidth, y + height, UITheme.Colors.PANEL_BG_SECONDARY);
    }

    private void drawBorder(DrawContext context, int height, int renderWidth) {
        int borderColor = UITheme.Colors.BUTTON_BORDER;
        int borderWidth = UITheme.Dimensions.BORDER_WIDTH;

        context.fill(x, y, x + renderWidth, y + borderWidth, borderColor);
        context.fill(x, y + height - borderWidth, x + renderWidth, y + height, borderColor);
        context.fill(x, y, x + borderWidth, y + height, borderColor);
        context.fill(x + renderWidth - borderWidth, y, x + renderWidth, y + height, borderColor);
    }

    private void renderItems(DrawContext context, int mouseX, int mouseY, int renderWidth) {
        int contentY = y + UITheme.Dimensions.BORDER_WIDTH;
        int itemCount = Math.min(items.size(), MAX_VISIBLE_ITEMS);
        int visibleItemsHeight = itemCount * ITEM_HEIGHT;

        context.enableScissor(x, contentY, x + renderWidth, contentY + visibleItemsHeight);

        hoveredIndex = -1;
        boolean hasScrollbar = items.size() > MAX_VISIBLE_ITEMS;
        int itemRightEdge = getItemRightEdge(hasScrollbar);

        for (int i = 0; i < items.size(); i++) {
            int itemY = contentY + i * ITEM_HEIGHT - (int) scrollOffset;

            if (itemY + ITEM_HEIGHT < contentY || itemY > contentY + visibleItemsHeight) {
                continue;
            }

            renderSingleItem(context, mouseX, mouseY, i, itemY, itemRightEdge);
        }

        context.disableScissor();
    }

    private void renderSingleItem(DrawContext context, int mouseX, int mouseY, int index, int itemY, int itemRightEdge) {
        DropdownItem item = items.get(index);
        boolean isHovered = mouseX >= x && mouseX < itemRightEdge &&
                          mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

        int itemBgColor = isHovered ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.BUTTON_BG;
        int itemX = x + UITheme.Dimensions.BORDER_WIDTH;
        int itemWidth = itemRightEdge - itemX;

        context.fill(itemX, itemY, itemX + itemWidth, itemY + ITEM_HEIGHT, itemBgColor);

        if (index < items.size() - 1) {
            context.fill(itemX, itemY + ITEM_HEIGHT - UITheme.Dimensions.BORDER_WIDTH,
                        itemX + itemWidth, itemY + ITEM_HEIGHT, UITheme.Colors.BUTTON_BORDER);
        }

        if (isHovered) {
            hoveredIndex = index;
        }

        renderItemText(context, item, itemX, itemY, itemWidth);
    }

    private void renderItemText(DrawContext context, DropdownItem item, int itemX, int itemY, int itemWidth) {
        String displayText = item.getDisplayText();
        int textX = itemX + UITheme.Dimensions.PADDING + 2;
        int textY = itemY + (ITEM_HEIGHT - client.textRenderer.fontHeight) / 2;

        int maxTextWidth = itemWidth - UITheme.Dimensions.PADDING * 2 - 4;
        if (client.textRenderer.getWidth(displayText) > maxTextWidth) {
            displayText = client.textRenderer.trimToWidth(displayText, maxTextWidth - 10) + "...";
        }

        context.drawText(client.textRenderer, displayText, textX, textY, UITheme.Colors.TEXT_PRIMARY, false);
    }

    private void renderStatusMessage(DrawContext context, int renderWidth) {
        if (statusMessage.isEmpty()) return;

        int itemCount = Math.min(items.size(), MAX_VISIBLE_ITEMS);
        int contentY = y + UITheme.Dimensions.BORDER_WIDTH;
        int visibleItemsHeight = itemCount * ITEM_HEIGHT;
        int statusY = contentY + visibleItemsHeight;
        int statusX = x + UITheme.Dimensions.BORDER_WIDTH;
        int statusWidth = renderWidth - UITheme.Dimensions.BORDER_WIDTH * 2;

        context.fill(statusX, statusY, statusX + statusWidth, statusY + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);

        int maxStatusWidth = statusWidth - UITheme.Dimensions.PADDING * 2;
        String displayStatus = statusMessage;

        if (client.textRenderer.getWidth(statusMessage) > maxStatusWidth) {
            displayStatus = client.textRenderer.trimToWidth(statusMessage, maxStatusWidth - client.textRenderer.getWidth("...")) + "...";
        }

        int statusColor = getStatusColor();
        int finalStatusWidth = client.textRenderer.getWidth(displayStatus);
        int statusTextX = x + (renderWidth - finalStatusWidth) / 2;
        int statusTextY = statusY + UITheme.Dimensions.BORDER_WIDTH + (ITEM_HEIGHT - client.textRenderer.fontHeight) / 2;

        context.drawText(client.textRenderer, displayStatus, statusTextX, statusTextY, statusColor, false);
    }

    private int getStatusColor() {
        String lowerMessage = statusMessage.toLowerCase();

        if (lowerMessage.contains("error") || lowerMessage.contains("failed") ||
            lowerMessage.contains("fail") || lowerMessage.startsWith("✗") ||
            lowerMessage.contains("could not") || lowerMessage.contains("cannot") ||
            lowerMessage.contains("unable")) {
            return UITheme.Colors.ERROR_TEXT;
        }

        if (lowerMessage.contains("success") || lowerMessage.contains("complete") ||
            lowerMessage.contains("downloaded") || lowerMessage.startsWith("✓")) {
            return UITheme.Colors.SUCCESS_BG;
        }

        return UITheme.Colors.TEXT_PRIMARY;
    }

    private void renderScrollbar(DrawContext context, int mouseX, int mouseY, float delta) {
        if (items.size() <= MAX_VISIBLE_ITEMS) return;

        int renderWidth = getRenderWidth();
        int itemCount = Math.min(items.size(), MAX_VISIBLE_ITEMS);
        int visibleItemsHeight = itemCount * ITEM_HEIGHT;
        int scrollbarX = x + renderWidth - UITheme.Dimensions.SCROLLBAR_WIDTH - 1;
        int scrollbarY = y + UITheme.Dimensions.BORDER_WIDTH;

        if (scrollBar == null || lastScrollBarX != scrollbarX ||
            lastScrollBarY != scrollbarY || lastScrollBarHeight != visibleItemsHeight) {
            scrollBar = new ScrollBar(scrollbarX, scrollbarY, visibleItemsHeight);
            lastScrollBarX = scrollbarX;
            lastScrollBarY = scrollbarY;
            lastScrollBarHeight = visibleItemsHeight;
        }

        int contentHeight = items.size() * ITEM_HEIGHT;
        scrollBar.setScrollData(contentHeight, visibleItemsHeight);

        if (maxScrollOffset > 0) {
            scrollBar.setScrollPercentage(scrollOffset / maxScrollOffset);
        }

        if (client.getWindow() != null) {
            boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta, client.getWindow().getHandle());

            if (scrollChanged || scrollBar.isDragging()) {
                scrollOffset = (int)(scrollBar.getScrollPercentage() * maxScrollOffset);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen || items.isEmpty()) return false;

        int height = getDropdownHeight();
        int renderWidth = getRenderWidth();

        if (mouseX >= x && mouseX < x + renderWidth && mouseY >= y && mouseY < y + height) {
            int statusAreaStart = y + (Math.min(items.size(), MAX_VISIBLE_ITEMS) * ITEM_HEIGHT) + UITheme.Dimensions.BORDER_WIDTH;
            if (!statusMessage.isEmpty() && mouseY >= statusAreaStart) {
                return true;
            }

            if (hoveredIndex >= 0 && hoveredIndex < items.size()) {
                DropdownItem selectedItem = items.get(hoveredIndex);
                if (onSelect != null) {
                    onSelect.accept(selectedItem);
                }
                return true;
            }
        } else {
            close();
            return false;
        }

        return false;
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return isOpen;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!isOpen || items.isEmpty()) return false;

        int height = getDropdownHeight();
        int renderWidth = getRenderWidth();
        return mouseX >= x && mouseX < x + renderWidth &&
               mouseY >= y && mouseY < y + height;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isOpen || items.isEmpty()) return false;

        if (!isMouseOver(mouseX, mouseY)) return false;

        if (items.size() <= MAX_VISIBLE_ITEMS) return true;

        double scrollAmount = -verticalAmount * ITEM_HEIGHT;
        scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset + scrollAmount));

        return true;
    }

    public static class DropdownItem {
        private final String displayText;
        private final Object data;

        public DropdownItem(String displayText, Object data) {
            this.displayText = displayText;
            this.data = data;
        }

        public String getDisplayText() {
            return displayText;
        }

        public Object getData() {
            return data;
        }
    }
}

