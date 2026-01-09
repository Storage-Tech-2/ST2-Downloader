package com.andrews.gui.widget;

import java.util.ArrayList;
import java.util.List;

import com.andrews.gui.theme.UITheme;
import com.andrews.models.ArchivePostSummary;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

public class PostListWidget extends AbstractWidget {
    private static final int ENTRY_SPACING = 2;
    private static final int SCROLL_SPEED = 20;

    private final List<PostEntryButton> entries;
    private double scrollAmount = 0;
    private final OnPostClickListener onPostClick;
    private ScrollBar scrollBar;

    public interface OnPostClickListener {
        void onPostClick(ArchivePostSummary post);
    }

    public PostListWidget(int x, int y, int width, int height, OnPostClickListener onPostClick) {
        super(x, y, width, height, Component.empty());
        this.entries = new ArrayList<>();
        this.onPostClick = onPostClick;
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH, y, height);
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.setX(x);
        this.setY(y);
        this.width = width;
        this.height = height;
        this.scrollBar = new ScrollBar(x + width - UITheme.Dimensions.SCROLLBAR_WIDTH, y, height);
        double savedScroll = this.scrollAmount;
        rebuildEntries();
        this.scrollAmount = savedScroll;
        updateScrollBar();
    }

    public double getScrollAmount() {
        return scrollAmount;
    }

    public void setScrollAmount(double scrollAmount) {
        this.scrollAmount = scrollAmount;
        updateScrollBar();
    }

    private void rebuildEntries() {
        List<ArchivePostSummary> posts = new ArrayList<>();
        for (PostEntryButton entry : entries) {
            posts.add(entry.getPost());
        }
        if (!posts.isEmpty()) {
            setPosts(posts);
        }
    }

    public void setPosts(List<ArchivePostSummary> posts) {
        this.entries.clear();
        this.scrollAmount = 0;

        int currentY = 0;
        for (ArchivePostSummary post : posts) {
            int rowWidth = Math.max(0, width - UITheme.Dimensions.PADDING);
            Runnable clickAction = () -> {
                if (onPostClick != null) {
                    onPostClick.onPostClick(post);
                }
            };
            PostEntryWidget visual = new PostEntryWidget(
                post,
                getX(),
                getY() + currentY,
                rowWidth,
                clickAction
            );
            PostEntryButton button = new PostEntryButton(visual, clickAction);
            entries.add(button);
            currentY += button.getRowHeight() + ENTRY_SPACING;
        }

        updateScrollBar();
    }

    public void clear() {
        this.entries.clear();
        this.scrollAmount = 0;
        updateScrollBar();
    }

    private void updateScrollBar() {
        double contentHeight = getTotalContentHeight();
        scrollBar.setScrollData(contentHeight, height);
        scrollBar.setScrollPercentage(contentHeight > height ? scrollAmount / (contentHeight - height) : 0);
    }

    private double getTotalContentHeight() {
        int totalHeight = 0;
        for (PostEntryButton entry : entries) {
            totalHeight += entry.getRowHeight() + ENTRY_SPACING;
        }
        return totalHeight;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.enableScissor(getX(), getY(), getX() + width, getY() + height);

        int offsetY = (int) scrollAmount;
        int currentY = 0;
        int entryWidth = getEntryWidth();

        for (PostEntryButton entry : entries) {
            int entryY = getY() + currentY - offsetY;

            if (isEntryVisible(entryY, entry.getRowHeight())) {
                entry.updateBounds(getX(), entryY, entryWidth);
                entry.render(context, mouseX, mouseY, delta);
            }

            currentY += entry.getRowHeight() + ENTRY_SPACING;
        }

        context.disableScissor();

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getWindow() != null) {
            long windowHandle = client.getWindow().handle();
            if (scrollBar.updateAndRender(context, mouseX, mouseY, delta, windowHandle)) {
                double maxScroll = getMaxScroll();
                scrollAmount = scrollBar.getScrollPercentage() * maxScroll;
            }
        } else {
            scrollBar.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            int offsetY = (int) scrollAmount;
            int currentY = 0;
            int entryWidth = getEntryWidth();

            for (PostEntryButton entry : entries) {
                int entryY = getY() + currentY - offsetY;

                if (isEntryVisible(entryY, entry.getRowHeight())) {
                    entry.updateBounds(getX(), entryY, entryWidth);
                    if (entry.handlePress(mouseX, mouseY, button)) {
                        return true;
                    }
                }

                currentY += entry.getRowHeight() + ENTRY_SPACING;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width &&
               mouseY >= getY() && mouseY < getY() + height;
    }

    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (scrollBar.isDragging() || scrollBar.mouseDragged(mouseX, mouseY, 0, deltaX, deltaY)) {
            scrollBar.mouseDragged(mouseX, mouseY, 0, deltaX, deltaY);
            double maxScroll = getMaxScroll();
            scrollAmount = scrollBar.getScrollPercentage() * maxScroll;
        }
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollBar.isDragging()) {
            return scrollBar.mouseReleased(mouseX, mouseY, button);
        }

        if (isMouseOver(mouseX, mouseY)) {
            int offsetY = (int) scrollAmount;
            int currentY = 0;
            int entryWidth = getEntryWidth();

            for (PostEntryButton entry : entries) {
                int entryY = getY() + currentY - offsetY;

                if (isEntryVisible(entryY, entry.getRowHeight())) {
                    entry.updateBounds(getX(), entryY, entryWidth);
                    if (entry.handleRelease(mouseX, mouseY, button)) {
                        return true;
                    }
                }

                currentY += entry.getRowHeight() + ENTRY_SPACING;
            }
        }

        return scrollBar.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollBar.isDragging() || scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            double maxScroll = getMaxScroll();
            scrollAmount = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOver(mouseX, mouseY)) {
            double maxScroll = getMaxScroll();
            scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount - verticalAmount * SCROLL_SPEED));
            updateScrollBar();
            return true;
        }
        return false;
    }

    private double getMaxScroll() {
        return Math.max(0, getTotalContentHeight() - height);
    }

    private boolean isEntryVisible(int entryY, int entryHeight) {
        return entryY + entryHeight >= getY() && entryY < getY() + height;
    }

    private int getEntryWidth() {
        return Math.max(0, width - UITheme.Dimensions.PADDING);
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
    }

    private static final class PostEntryButton extends AbstractWidget {
        private final PostEntryWidget visual;
        private final ArchivePostSummary post;
        private final Runnable clickAction;
        private boolean pressed;

        private PostEntryButton(PostEntryWidget visual, Runnable clickAction) {
            super(visual.getX(), visual.getY(), visual.getWidth(), visual.getHeight(), Component.empty());
            this.visual = visual;
            this.post = visual.getPost();
            this.clickAction = clickAction;
        }

        private void updateBounds(int x, int y, int width) {
            this.setX(x);
            this.setY(y);
            this.setWidth(width);
            this.height = visual.getHeight();
            visual.setX(x);
            visual.setWidth(width);
            visual.setY(y);
        }

        private int getRowHeight() {
            return visual.getHeight();
        }

        private ArchivePostSummary getPost() {
            return post;
        }

        private boolean handlePress(double mouseX, double mouseY, int button) {
            if (button != 0 || !isPointInside(mouseX, mouseY)) {
                return false;
            }
            pressed = true;
            visual.setPressed(true);
            this.setFocused(true);
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getSoundManager() != null) {
                this.playDownSound(client.getSoundManager());
            }
            if (clickAction != null) {
                clickAction.run();
            }
            return true;
        }

        private boolean handleRelease(double mouseX, double mouseY, int button) {
            if (!pressed) {
                return false;
            }
            pressed = false;
            visual.setPressed(false);
            return true;
        }

        private boolean isPointInside(double mouseX, double mouseY) {
            return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight();
        }

        @Override
        public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            visual.render(context, mouseX, mouseY, delta);
            if (this.isHovered()) {
                context.fill(getX(), getY(), getX() + this.width, getY() + this.height, 0x30FFFFFF);
            }
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput builder) {
            builder.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, Component.nullToEmpty("Post entry"));
        }
    }
}
