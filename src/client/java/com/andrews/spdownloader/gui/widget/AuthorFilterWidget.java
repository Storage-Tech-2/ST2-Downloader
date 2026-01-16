package com.andrews.spdownloader.gui.widget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.andrews.spdownloader.gui.theme.UITheme;
import com.andrews.spdownloader.util.RenderUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;

public class AuthorFilterWidget implements Drawable, Element {
    private static final int ITEM_HEIGHT = 20;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<AuthorEntry> authors = new ArrayList<>();
    private final List<AuthorEntry> filtered = new ArrayList<>();
    private Consumer<String> onSelectionChanged;

    private int x;
    private int y;
    private int width;
    private int height;
    private double scrollOffset = 0;
    private int contentHeight = 0;
    private ScrollBar scrollBar;
    private CustomTextField searchField;
    private String selectedAuthor = null;

    private record AuthorEntry(String name, int count) {}

    public AuthorFilterWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        createScrollBar();
        initSearchField();
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        createScrollBar();
        initSearchField();
    }

    private void createScrollBar() {
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH, y + UITheme.Dimensions.BUTTON_HEIGHT + UITheme.Dimensions.PADDING * 2, height - UITheme.Dimensions.BUTTON_HEIGHT - UITheme.Dimensions.PADDING * 2);
    }

    private void initSearchField() {
        int fieldX = x + UITheme.Dimensions.PADDING;
        int fieldY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + 2;
        int fieldWidth = width - UITheme.Dimensions.PADDING * 2 - UITheme.Dimensions.SCROLLBAR_WIDTH / 2;
        int fieldHeight = UITheme.Dimensions.BUTTON_HEIGHT;
        if (searchField == null) {
            searchField = new CustomTextField(client, fieldX, fieldY, fieldWidth, fieldHeight, net.minecraft.text.Text.of("Search authors"));
            searchField.setPlaceholder(net.minecraft.text.Text.of("Search authors"));
            searchField.setOnChanged(() -> {
                scrollOffset = 0;
                refilter();
            });
        } else {
            searchField.setX(fieldX);
            searchField.setY(fieldY);
            searchField.setWidth(fieldWidth);
            searchField.setHeight(fieldHeight);
        }
    }

    private int getListStartY() {
        int base = searchField != null ? searchField.getY() + searchField.getHeight() : y + UITheme.Dimensions.PADDING * 2;
        return base + UITheme.Dimensions.PADDING;
    }

    private int getVisibleListHeight() {
        int header = getListStartY() - y;
        return height - header - UITheme.Dimensions.PADDING;
    }

    public void setAuthors(Map<String, Integer> counts) {
        authors.clear();
        if (counts != null) {
            counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
                .forEach(e -> authors.add(new AuthorEntry(e.getKey(), e.getValue())));
        }
        refilter();
    }

    public void setSelectedAuthor(String author) {
        this.selectedAuthor = author;
    }

    public void setOnSelectionChanged(Consumer<String> callback) {
        this.onSelectionChanged = callback;
    }

    private void refilter() {
        filtered.clear();
        String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        for (AuthorEntry entry : authors) {
            if (query.isEmpty() || entry.name.toLowerCase().contains(query)) {
                filtered.add(entry);
            }
        }
        scrollOffset = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderUtil.fillRect(context, x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);
        RenderUtil.fillRect(context, x, y, x + width, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + height, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x + width - UITheme.Dimensions.BORDER_WIDTH, y, x + width, y + height, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y + height - UITheme.Dimensions.BORDER_WIDTH, x + width, y + height, UITheme.Colors.BUTTON_BORDER);

        RenderUtil.drawScaledString(context, "Authors", x + UITheme.Dimensions.PADDING, y + UITheme.Dimensions.PADDING, UITheme.Colors.TEXT_PRIMARY, 0.95f);
        String reset = "Reset";
        int resetWidth = client.textRenderer.getWidth(reset);
        int resetX = x + width - resetWidth - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH / 2;
        int resetY = y + UITheme.Dimensions.PADDING;
        RenderUtil.drawScaledString(context, reset, resetX, resetY, UITheme.Colors.TEXT_PRIMARY, 0.9f);

        if (searchField != null) {
            searchField.render(context, mouseX, mouseY, delta);
        }

        int listStartY = getListStartY();
        contentHeight = filtered.size() * ITEM_HEIGHT;
        int visibleHeight = getVisibleListHeight();
        scrollBar.setScrollData(contentHeight, visibleHeight);
        int scrollbarWidth = scrollBar.isVisible() ? UITheme.Dimensions.SCROLLBAR_WIDTH : 0;
        RenderUtil.enableScissor(context, x + UITheme.Dimensions.PADDING / 2, listStartY, x + width - scrollbarWidth - UITheme.Dimensions.PADDING / 2, y + height - UITheme.Dimensions.PADDING);

        int currentY = listStartY - (int) scrollOffset;
        for (AuthorEntry entry : filtered) {
            boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= currentY && mouseY < currentY + ITEM_HEIGHT;
            boolean selected = selectedAuthor != null && selectedAuthor.equalsIgnoreCase(entry.name());
            int bg = hovered || selected ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.PANEL_BG;
            RenderUtil.fillRect(context, x + UITheme.Dimensions.PADDING / 2, currentY, x + width - scrollbarWidth - UITheme.Dimensions.PADDING / 2, currentY + ITEM_HEIGHT - 2, bg);
            RenderUtil.drawScaledString(context, entry.name(), x + UITheme.Dimensions.PADDING, currentY + 6, UITheme.Colors.TEXT_PRIMARY, 0.9f);
            String count = String.valueOf(entry.count());
            int countWidth = client.textRenderer.getWidth(count);
            RenderUtil.drawScaledString(context, count, x + width - scrollbarWidth - UITheme.Dimensions.PADDING - countWidth - 4, currentY + 6, UITheme.Colors.TEXT_SUBTITLE, 0.9f);
            currentY += ITEM_HEIGHT;
        }

        RenderUtil.disableScissor(context);

        double scrollable = Math.max(0, contentHeight - visibleHeight);
        if (scrollable > 0) {
            scrollBar.setScrollPercentage(scrollOffset / scrollable);
        }

        if (client != null && client.getWindow() != null) {
            boolean changed = scrollBar.updateAndRender(context, mouseX, mouseY, delta, client.getWindow().getHandle());
            if (changed || scrollBar.isDragging()) {
                scrollOffset = scrollBar.getScrollPercentage() * scrollable;
            }
        } else {
            scrollBar.render(context, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (searchField != null && searchField.isMouseOver(mouseX, mouseY)) {
            searchField.setFocused(true);
            return true;
        } else if (searchField != null) {
            searchField.setFocused(false);
        }

        String reset = "Reset";
        int resetWidth = client.textRenderer.getWidth(reset);
        int resetX = x + width - resetWidth - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH / 2;
        int resetY = y + UITheme.Dimensions.PADDING;
        if (mouseX >= resetX && mouseX <= resetX + resetWidth && mouseY >= resetY && mouseY <= resetY + UITheme.Typography.TEXT_HEIGHT) {
            selectedAuthor = null;
            if (onSelectionChanged != null) {
                onSelectionChanged.accept(null);
            }
            return true;
        }

        int listStartY = getListStartY();
        int currentY = listStartY - (int) scrollOffset;
        for (AuthorEntry entry : filtered) {
            if (mouseY >= currentY && mouseY < currentY + ITEM_HEIGHT) {
                if (selectedAuthor != null && selectedAuthor.equalsIgnoreCase(entry.name())) {
                    selectedAuthor = null;
                } else {
                    selectedAuthor = entry.name();
                }
                if (onSelectionChanged != null) {
                    onSelectionChanged.accept(selectedAuthor);
                }
                return true;
            }
            currentY += ITEM_HEIGHT;
        }

        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            double scrollable = Math.max(0, contentHeight - getVisibleListHeight());
            scrollOffset = Math.max(0, Math.min(scrollOffset - verticalAmount * 12, scrollable));
            scrollBar.setScrollPercentage((float) (scrollable > 0 ? scrollOffset / scrollable : 0));
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            double scrollable = Math.max(0, contentHeight - getVisibleListHeight());
            scrollOffset = scrollBar.getScrollPercentage() * scrollable;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        scrollBar.mouseReleased(mouseX, mouseY, button);
        return false;
    }

    public void setFocused(boolean focused) {}

    public boolean isFocused() {
        return false;
    }
}
