package com.andrews.gui.widget;

import org.lwjgl.glfw.GLFW;

import com.andrews.gui.theme.UITheme;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.text.Text;

public class ConfirmPopup implements Drawable, Element {
    private static final int POPUP_WIDTH = 400;
    private static final int MAX_MESSAGE_HEIGHT = 300;

    private final String title;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    private CustomButton confirmButton;
    private CustomButton cancelButton;

    private boolean wasEnterPressed = false;
    private boolean wasEscapePressed = false;

    private final int x;
    private final int y;
    private final int popupHeight;
    private final List<String> wrappedMessage;
    private final int actualMessageHeight;
    private final int visibleMessageHeight;
    private ScrollBar scrollBar;
    private double scrollOffset = 0;
    private final String confirmButtonText;

    public ConfirmPopup(String title, String message, Runnable onConfirm, Runnable onCancel) {
        this(title, message, onConfirm, onCancel, "Delete");
    }

    public ConfirmPopup(String title, String message, Runnable onConfirm, Runnable onCancel, String confirmButtonText) {
      
        this.title = title;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.confirmButtonText = confirmButtonText;

        MinecraftClient client = MinecraftClient.getInstance();

        this.wrappedMessage = wrapText(message, POPUP_WIDTH - UITheme.Dimensions.PADDING * 2, client);

        int screenHeight = client.getWindow().getScaledHeight();
        int screenWidth = client.getWindow().getScaledWidth();

        int verticalMargin = 40;
        int popupChrome = UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING + UITheme.Dimensions.PADDING + UITheme.Dimensions.BUTTON_HEIGHT + UITheme.Dimensions.PADDING;
        int maxAvailableMessageHeight = screenHeight - (verticalMargin * 2) - popupChrome;

        int effectiveMaxHeight = Math.min(MAX_MESSAGE_HEIGHT, maxAvailableMessageHeight);

        this.actualMessageHeight = wrappedMessage.size() * UITheme.Typography.LINE_HEIGHT;
        this.visibleMessageHeight = Math.min(actualMessageHeight, effectiveMaxHeight);
        this.popupHeight = UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING + visibleMessageHeight + UITheme.Dimensions.PADDING + UITheme.Dimensions.BUTTON_HEIGHT + UITheme.Dimensions.PADDING;

        this.x = (screenWidth - POPUP_WIDTH) / 2;
        this.y = (screenHeight - popupHeight) / 2;

        if (actualMessageHeight > visibleMessageHeight) {
            int messageAreaY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING;
            int scrollBarX = x + POPUP_WIDTH - UITheme.Dimensions.PADDING - UITheme.Dimensions.SCROLLBAR_WIDTH;
            this.scrollBar = new ScrollBar(scrollBarX, messageAreaY, visibleMessageHeight);
            this.scrollBar.setScrollData(actualMessageHeight, visibleMessageHeight);
        }

        initWidgets();
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
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
                int width = client.textRenderer.getWidth(testLine);

                if (width <= maxWidth) {
                    if (currentLine.length() > 0) {
                        currentLine.append(" ");
                    }
                    currentLine.append(word);
                } else {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                    }
                    currentLine = new StringBuilder(word);
                }
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }

        return lines.isEmpty() ? List.of(text) : lines;
    }

    private void initWidgets() {
        int buttonY = y + popupHeight - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT;
        int buttonWidth = (POPUP_WIDTH - UITheme.Dimensions.PADDING * 3) / 2;

        cancelButton = new CustomButton(
                x + UITheme.Dimensions.PADDING,
                buttonY,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of("Cancel"),
                button -> onCancel.run()
        );

        confirmButton = new CustomButton(
                x + POPUP_WIDTH - UITheme.Dimensions.PADDING - buttonWidth,
                buttonY,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of(confirmButtonText),
                button -> onConfirm.run()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        long windowHandle = client.getWindow() != null ? client.getWindow().getHandle() : 0;

        if (windowHandle != 0) {
            boolean enterPressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ENTER) == GLFW.GLFW_PRESS ||
                                  GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_KP_ENTER) == GLFW.GLFW_PRESS;
            boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

            if (enterPressed && !wasEnterPressed) {
                onConfirm.run();
            }
            if (escapePressed && !wasEscapePressed) {
                onCancel.run();
            }

            wasEnterPressed = enterPressed;
            wasEscapePressed = escapePressed;
        }

        context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), UITheme.Colors.OVERLAY_BG);

        context.fill(x, y, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BG_DISABLED);

        context.fill(x, y, x + POPUP_WIDTH, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        context.fill(x, y + popupHeight - UITheme.Dimensions.BORDER_WIDTH, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);
        context.fill(x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);
        context.fill(x + POPUP_WIDTH - UITheme.Dimensions.BORDER_WIDTH, y, x + POPUP_WIDTH, y + popupHeight, UITheme.Colors.BUTTON_BORDER);

        context.drawCenteredTextWithShadow(
                client.textRenderer,
                title,
                x + POPUP_WIDTH / 2,
                y + UITheme.Dimensions.PADDING,
                UITheme.Colors.TEXT_PRIMARY
        );

        int messageAreaY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING;
        int messageAreaHeight = visibleMessageHeight;

        context.enableScissor(x + UITheme.Dimensions.PADDING, messageAreaY, x + POPUP_WIDTH - UITheme.Dimensions.PADDING, messageAreaY + messageAreaHeight);

        int messageY = messageAreaY - (int)scrollOffset;
        for (String line : wrappedMessage) {
            if (messageY + UITheme.Typography.LINE_HEIGHT >= messageAreaY && messageY < messageAreaY + messageAreaHeight) {
                context.drawTextWithShadow(
                        client.textRenderer,
                        line,
                        x + UITheme.Dimensions.PADDING,
                        messageY,
                        UITheme.Colors.TEXT_TAG
                );
            }
            messageY += UITheme.Typography.LINE_HEIGHT;
        }

        context.disableScissor();

        if (scrollBar != null && client.getWindow() != null) {
            scrollBar.setScrollPercentage(scrollOffset / Math.max(1, actualMessageHeight - visibleMessageHeight));
            boolean scrollChanged = scrollBar.updateAndRender(context, mouseX, mouseY, delta, client.getWindow().getHandle());

            if (scrollChanged || scrollBar.isDragging()) {
                double maxScroll = actualMessageHeight - visibleMessageHeight;
                scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            }
        }

        if (cancelButton != null) {
            cancelButton.render(context, mouseX, mouseY, delta);
        }
        if (confirmButton != null) {
            confirmButton.render(context, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < x || mouseX > x + POPUP_WIDTH || mouseY < y || mouseY > y + popupHeight) {
            onCancel.run();
            return true;
        }

        if (cancelButton != null) {
            boolean isOverCancel = mouseX >= cancelButton.getX() &&
                                  mouseX < cancelButton.getX() + cancelButton.getWidth() &&
                                  mouseY >= cancelButton.getY() &&
                                  mouseY < cancelButton.getY() + cancelButton.getHeight();
            if (isOverCancel) {
                onCancel.run();
                return true;
            }
        }

        if (confirmButton != null) {
            boolean isOverConfirm = mouseX >= confirmButton.getX() &&
                                   mouseX < confirmButton.getX() + confirmButton.getWidth() &&
                                   mouseY >= confirmButton.getY() &&
                                   mouseY < confirmButton.getY() + confirmButton.getHeight();
            if (isOverConfirm) {
                onConfirm.run();
                return true;
            }
        }

        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (scrollBar != null) {
            int messageAreaY = y + UITheme.Dimensions.PADDING + UITheme.Typography.LINE_HEIGHT + UITheme.Dimensions.PADDING;
            if (mouseX >= x && mouseX < x + POPUP_WIDTH &&
                mouseY >= messageAreaY && mouseY < messageAreaY + visibleMessageHeight) {

                double maxScroll = actualMessageHeight - visibleMessageHeight;
                scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * UITheme.Typography.LINE_HEIGHT));

                return true;
            }
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }
}

