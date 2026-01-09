package com.andrews.gui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.andrews.config.DownloadSettings;
import com.andrews.gui.widget.CustomButton;
import com.andrews.gui.widget.ScrollBar;
import com.andrews.gui.widget.ToastManager;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DirectoryPickerScreen extends Screen {
    private static final int PADDING = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ITEM_HEIGHT = 25;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_PADDING = 2;

    private final Screen parentScreen;
    private final Consumer<String> onPathSelected;
    private File currentDirectory;
    private final List<File> directories = new ArrayList<>();
    private int scrollOffset = 0;
    private ScrollBar scrollBar;
    private int selectedIndex = -1;
    private ToastManager toastManager;

    public DirectoryPickerScreen(Screen parentScreen, String startPath, Consumer<String> onPathSelected) {
        super(Component.nullToEmpty("Select Directory"));
        this.parentScreen = parentScreen;
        this.onPathSelected = onPathSelected;

        File startDir = new File(startPath);
        if (!startDir.exists() || !startDir.isDirectory()) {
            startDir = new File(DownloadSettings.getInstance().getGameDirectory());
        }

        File parentDir = startDir.getParentFile();
        if (parentDir != null && parentDir.exists()) {
            this.currentDirectory = parentDir;
        } else {
            this.currentDirectory = startDir;
        }

        loadDirectories();
    }

    private int getListY() {
        return PADDING * 4 + BUTTON_HEIGHT + 10;
    }

    private int getListHeight() {
        return this.height - getListY() - BUTTON_HEIGHT - PADDING * 3;
    }

    private int getListRightEdge() {
        return this.width - PADDING - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;
    }

    private int getMaxVisibleItems() {
        return getListHeight() / ITEM_HEIGHT;
    }

    private int getMaxScroll() {
        int contentHeight = directories.size() * ITEM_HEIGHT;
        int listHeight = getListHeight();
        if (contentHeight <= listHeight) {
            return 0;
        }
        return Math.max(0, directories.size() - getMaxVisibleItems());
    }

    @Override
    protected void init() {
        super.init();

        int savedScrollOffset = scrollOffset;
        int savedSelectedIndex = selectedIndex;

        if (this.minecraft != null) {
            toastManager = new ToastManager(this.minecraft);
        }

        boolean isCompact = this.width < 400;
        boolean isVeryCompact = this.width < 300;

        int upButtonWidth = isVeryCompact ? 30 : (isCompact ? 45 : 60);
        int selectButtonWidth = isVeryCompact ? 100 : (isCompact ? 150 : 200);
        String upLabel = isVeryCompact ? "↑" : "Up ↑";
        String selectLabel = isVeryCompact ? "Select" : (isCompact ? "Select Folder" : "Select This Folder");

        CustomButton backButton = new CustomButton(
                PADDING,
                PADDING,
                BUTTON_HEIGHT,
                BUTTON_HEIGHT,
                Component.nullToEmpty("←"),
                button -> onClose()
        );
        this.addRenderableWidget(backButton);

        CustomButton upButton = new CustomButton(
                PADDING * 2 + BUTTON_HEIGHT,
                PADDING,
                upButtonWidth,
                BUTTON_HEIGHT,
                Component.nullToEmpty(upLabel),
                button -> goUpDirectory()
        );
        this.addRenderableWidget(upButton);

        CustomButton selectButton = new CustomButton(
                this.width / 2 - selectButtonWidth / 2,
                this.height - PADDING - BUTTON_HEIGHT - PADDING,
                selectButtonWidth,
                BUTTON_HEIGHT,
                Component.nullToEmpty(selectLabel),
                button -> selectCurrentDirectory()
        );
        this.addRenderableWidget(selectButton);

        int listY = getListY();
        int listHeight = getListHeight();
        scrollBar = new ScrollBar(this.width - PADDING - SCROLLBAR_WIDTH, listY, listHeight);

        int contentHeight = directories.size() * ITEM_HEIGHT;
        scrollBar.setScrollData(contentHeight, listHeight);

        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            scrollOffset = Math.min(savedScrollOffset, maxScroll);
            scrollBar.setScrollPercentage((double) scrollOffset / maxScroll);
        } else {
            scrollOffset = 0;
            scrollBar.setScrollPercentage(0.0);
        }
        selectedIndex = savedSelectedIndex;
    }

    private void loadDirectories() {
        directories.clear();
        selectedIndex = -1;

        if (currentDirectory != null && currentDirectory.exists()) {
            File[] files = currentDirectory.listFiles(File::isDirectory);
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                directories.addAll(Arrays.asList(files));
            }
        }

        updateScrollBar();
    }

    private void updateScrollBar() {
        if (scrollBar != null) {
            int listHeight = getListHeight();
            int contentHeight = directories.size() * ITEM_HEIGHT;

            scrollBar.setScrollData(contentHeight, listHeight);

            int maxScroll = getMaxScroll();
            if (maxScroll > 0) {
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
                scrollBar.setScrollPercentage((double) scrollOffset / maxScroll);
            } else {
                scrollOffset = 0;
                scrollBar.setScrollPercentage(0.0);
            }
        }
    }

    private void goUpDirectory() {
        if (currentDirectory != null) {
            File parent = currentDirectory.getParentFile();
            if (parent != null && parent.exists()) {
                currentDirectory = parent;
                loadDirectories();
                scrollOffset = 0;
            }
        }
    }

    private void selectCurrentDirectory() {
        File directoryToSelect;

        if (selectedIndex >= 0 && selectedIndex < directories.size()) {
            directoryToSelect = directories.get(selectedIndex);
        } else {
            directoryToSelect = currentDirectory;
        }

        if (directoryToSelect != null) {
            File gameDir = new File(DownloadSettings.getInstance().getGameDirectory());
            String relativePath = getRelativePath(gameDir, directoryToSelect);
            if (toastManager != null) {
                toastManager.showSuccess("Directory selected!");
            }
            onPathSelected.accept(relativePath);
            onClose();
        }
    }

    private String getRelativePath(File base, File target) {
        try {
            String basePath = base.getCanonicalPath();
            String targetPath = target.getCanonicalPath();

            if (targetPath.startsWith(basePath)) {
                String relative = targetPath.substring(basePath.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return relative.isEmpty() ? "." : relative;
            }

            return targetPath;
        } catch (Exception e) {
            return target.getAbsolutePath();
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF202020);

        super.render(context, mouseX, mouseY, delta);

        String currentPath = currentDirectory != null ? currentDirectory.getAbsolutePath() : "";
        context.drawString(
                this.font,
                "Current: " + currentPath,
                PADDING,
                PADDING * 3 + BUTTON_HEIGHT,
                0xFFFFFFFF
        );

        int listY = getListY();
        int listHeight = getListHeight();
        int maxVisibleItems = getMaxVisibleItems();
        int listRightEdge = getListRightEdge();

        context.fill(PADDING, listY, listRightEdge, listY + listHeight, 0xFF151515);

        context.enableScissor(PADDING, listY, listRightEdge, listY + listHeight);

        for (int i = scrollOffset; i < Math.min(directories.size(), scrollOffset + maxVisibleItems); i++) {
            File dir = directories.get(i);
            int itemY = listY + (i - scrollOffset) * ITEM_HEIGHT;

            boolean isHovered = mouseX >= PADDING && mouseX < listRightEdge &&
                              mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            boolean isSelected = i == selectedIndex;

            int bgColor = isSelected ? 0xFF404040 : (isHovered ? 0xFF2A2A2A : 0xFF1A1A1A);
            context.fill(PADDING + 2, itemY + 2, listRightEdge - 2, itemY + ITEM_HEIGHT - 2, bgColor);

            String displayName = "📁 " + dir.getName();
            context.drawString(
                    this.font,
                    displayName,
                    PADDING + 5,
                    itemY + 8,
                    0xFFFFFFFF
            );
        }

        context.disableScissor();

        if (scrollBar != null && scrollBar.isVisible() && this.minecraft != null) {
            boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta, this.minecraft.getWindow().handle());

            if (scrollChanged) {
                int maxScroll = getMaxScroll();
                scrollOffset = (int)(scrollBar.getScrollPercentage() * maxScroll);
            }
        }

        if (toastManager != null) {
            toastManager.render(context, delta, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        int listY = getListY();
        int listHeight = getListHeight();
        int listRightEdge = getListRightEdge();

        if (mouseX >= PADDING && mouseX < listRightEdge &&
            mouseY >= listY && mouseY < listY + listHeight) {

            int clickedIndex = scrollOffset + (int)((mouseY - listY) / ITEM_HEIGHT);

            if (clickedIndex >= 0 && clickedIndex < directories.size()) {
                if (button == 0) {
                    if (clickedIndex == selectedIndex && doubled) {
                        currentDirectory = directories.get(clickedIndex);
                        loadDirectories();
                        scrollOffset = 0;
                    } else {
                        selectedIndex = clickedIndex;
                    }
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = getMaxScroll();

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int)verticalAmount));

        if (scrollBar != null && maxScroll > 0) {
            scrollBar.setScrollPercentage((double) scrollOffset / maxScroll);
        }

        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parentScreen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

