package com.andrews.spdownloader.gui.widget;

import java.util.ArrayList;
import java.util.List;

import com.andrews.spdownloader.gui.theme.UITheme;
import com.andrews.spdownloader.util.RenderUtil;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

public class ShareProjectsWarningPopup implements Drawable, Element {
    private static final int POPUP_WIDTH = 520;

    private final String title;
    private final List<String> messageLines;
    private final Runnable onVisit;
    private final Runnable onAcknowledge;

    private final int x;
    private final int y;
    private final int popupHeight;
    private final int visibleMessageHeight;
    private final int actualMessageHeight;
    private double scrollOffset = 0;
    private final ScrollBar scrollBar;

    private final CustomButton visitButton;
    private final CustomButton acknowledgeButton;

    public ShareProjectsWarningPopup(String title, String message, Runnable onVisit, Runnable onAcknowledge) {
        this.title = title;
        this.onVisit = onVisit;
        this.onAcknowledge = onAcknowledge;

        MinecraftClient client = MinecraftClient.getInstance();
        int scrollbarPadding = UITheme.Dimensions.SCROLLBAR_WIDTH + UITheme.Dimensions.PADDING;
        this.messageLines = wrapText(message, POPUP_WIDTH - UITheme.Dimensions.PADDING * 2 - scrollbarPadding, client);

        int screenHeight = client.getWindow().getScaledHeight();
        int screenWidth = client.getWindow().getScaledWidth();

        int chromeHeight = UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING +
            UITheme.Dimensions.BUTTON_HEIGHT + UITheme.Dimensions.PADDING;
        this.actualMessageHeight = messageLines.size() * UITheme.Typography.LINE_HEIGHT;

        int margin = Math.max(12, UITheme.Dimensions.PADDING);
        int maxPopupHeight = Math.max(120, screenHeight - margin * 2);
        int availableForMessage = Math.max(80, maxPopupHeight - chromeHeight);
        this.visibleMessageHeight = Math.min(actualMessageHeight, availableForMessage);
        this.popupHeight = chromeHeight + visibleMessageHeight;

        this.x = (screenWidth - POPUP_WIDTH) / 2;
        this.y = Math.max(margin, (screenHeight - popupHeight) / 2);

        this.scrollBar = new ScrollBar(
            x + POPUP_WIDTH - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH,
            y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING,
            visibleMessageHeight
        );
        this.scrollBar.setScrollData(actualMessageHeight, visibleMessageHeight);

        int buttonWidth = (POPUP_WIDTH - UITheme.Dimensions.PADDING * 3) / 2;
        int buttonY = y + popupHeight - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT;
        this.visitButton = new CustomButton(
            x + UITheme.Dimensions.PADDING,
            buttonY,
            buttonWidth,
            UITheme.Dimensions.BUTTON_HEIGHT,
            Text.of("Visit storagetech2.org"),
            b -> openSite()
        );
        this.acknowledgeButton = new CustomButton(
            x + UITheme.Dimensions.PADDING * 2 + buttonWidth,
            buttonY,
            buttonWidth,
            UITheme.Dimensions.BUTTON_HEIGHT,
            Text.of("I understand"),
            b -> {
                if (onAcknowledge != null) {
                    onAcknowledge.run();
                }
            }
        );
    }

    private List<String> wrapText(String text, int maxWidth, MinecraftClient client) {
        List<String> lines = new ArrayList<>();
        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            String[] words = paragraph.split(" ");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String test = current.length() == 0 ? word : current + " " + word;
                if (client.textRenderer.getWidth(test) <= maxWidth) {
                    current = new StringBuilder(test);
                } else {
                    if (current.length() > 0) {
                        lines.add(current.toString());
                    }
                    current = new StringBuilder(word);
                }
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
        }
        return lines.isEmpty() ? List.of(text) : lines;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        RenderUtil.fillRect(context, 0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), UITheme.Colors.OVERLAY_BG);
        RenderUtil.fillRect(context, x, y, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BG_DISABLED);

        RenderUtil.fillRect(context, x, y, x + POPUP_WIDTH, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y + popupHeight - UITheme.Dimensions.BORDER_WIDTH, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);
        RenderUtil.fillRect(context, x + POPUP_WIDTH - UITheme.Dimensions.BORDER_WIDTH, y, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);

        RenderUtil.drawCenteredString(
            context,
            client.textRenderer,
            title,
            x + POPUP_WIDTH / 2,
            y + UITheme.Dimensions.PADDING,
            UITheme.Colors.TEXT_PRIMARY
        );

        int messageAreaY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING;
        int messageAreaHeight = visibleMessageHeight;
        int scissorRight = x + POPUP_WIDTH - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH;
        RenderUtil.enableScissor(context, x + UITheme.Dimensions.PADDING, messageAreaY, scissorRight, messageAreaY + messageAreaHeight);

        int lineY = messageAreaY - (int) scrollOffset;
        for (String line : messageLines) {
            RenderUtil.drawString(
                context,
                client.textRenderer,
                line,
                x + UITheme.Dimensions.PADDING,
                lineY,
                UITheme.Colors.TEXT_TAG
            );
            lineY += UITheme.Typography.LINE_HEIGHT;
        }

        RenderUtil.disableScissor(context);

        scrollBar.render(context, mouseX, mouseY, delta);
        visitButton.render(context, mouseX, mouseY, delta);
        acknowledgeButton.render(context, mouseX, mouseY, delta);
    }

    private void openSite() {
        if (onVisit != null) {
            onVisit.run();
        }
        try {
            Util.getOperatingSystem().open("https://storagetech2.org");
        } catch (Exception e) {
            System.err.println("Failed to open storagetech2.org: " + e.getMessage());
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scrollBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (visitButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (acknowledgeButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double scrollable = Math.max(0, actualMessageHeight - visibleMessageHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset - verticalAmount * 12, scrollable));
        scrollBar.setScrollPercentage(scrollable > 0 ? scrollOffset / scrollable : 0);
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            double scrollable = Math.max(0, actualMessageHeight - visibleMessageHeight);
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
