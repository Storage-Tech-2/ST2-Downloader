package com.andrews.gui.widget;

import org.lwjgl.glfw.GLFW;

import com.andrews.gui.theme.UITheme;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class TextInputPopup implements Drawable, Element {
    private static final int POPUP_WIDTH = 300;
    private static final int POPUP_HEIGHT = 120;
    private static final String[] INVALID_CHARS = {"/", "\\", ":", "*", "?", "\"", "<", ">", "|"};
    private static final String[] RESERVED_NAMES = {"CON", "PRN", "AUX", "NUL"};

    private final String title;
    private final Consumer<String> onConfirm;
    private final Runnable onCancel;
    private final String confirmButtonText;
    private final int x;
    private final int y;

    private CustomTextField textField;
    private CustomButton confirmButton;
    private CustomButton cancelButton;
    private String errorMessage = "";
    private boolean wasEscapePressed;

    public TextInputPopup(Screen parent, String title, Consumer<String> onConfirm, Runnable onCancel) {
        this(title, "Create", onConfirm, onCancel);
    }

    public TextInputPopup(Screen parent, String title, String confirmButtonText, Consumer<String> onConfirm, Runnable onCancel) {
        this(title, confirmButtonText, onConfirm, onCancel);
    }

    public TextInputPopup(String title, String confirmButtonText, Consumer<String> onConfirm, Runnable onCancel) {
        this.title = title;
        this.confirmButtonText = confirmButtonText;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        MinecraftClient client = MinecraftClient.getInstance();
        this.x = (client.getWindow().getScaledWidth() - POPUP_WIDTH) / 2;
        this.y = (client.getWindow().getScaledHeight() - POPUP_HEIGHT) / 2;

        initWidgets();
    }

    private void initWidgets() {
        MinecraftClient client = MinecraftClient.getInstance();
        int fieldY = y + UITheme.Dimensions.PADDING * 3;

        textField = new CustomTextField(
                client,
                x + UITheme.Dimensions.PADDING,
                fieldY,
                POPUP_WIDTH - UITheme.Dimensions.PADDING * 2,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of("")
        );
        textField.setPlaceholder(Text.of("Enter name..."));
        textField.setFocused(true);
        textField.setOnChanged(() -> errorMessage = "");
        textField.setOnEnterPressed(this::handleConfirm);

        int buttonY = y + POPUP_HEIGHT - UITheme.Dimensions.PADDING - UITheme.Dimensions.BUTTON_HEIGHT;
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
                x + UITheme.Dimensions.PADDING * 2 + buttonWidth,
                buttonY,
                buttonWidth,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of(confirmButtonText),
                button -> handleConfirm()
        );
    }

    public void setErrorMessage(String error) {
        this.errorMessage = error;
    }

    public void setText(String text) {
        if (textField != null) {
            textField.setText(text);
        }
    }

    private List<String> wrapText(String text, int maxWidth, MinecraftClient client) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            int width = client.textRenderer.getWidth(testLine);

            if (width <= maxWidth) {
                if (!currentLine.isEmpty()) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines.isEmpty() ? List.of(text) : lines;
    }

    private void handleConfirm() {
        String text = textField.getText().trim();

        if (text.isEmpty()) {
            setErrorMessage("Folder name cannot be empty");
            return;
        }

        if (containsInvalidChars(text)) {
            setErrorMessage("Invalid characters in folder name");
            return;
        }

        if (isReservedName(text)) {
            setErrorMessage("Reserved folder name");
            return;
        }

        onConfirm.accept(text);
    }

    private boolean containsInvalidChars(String text) {
        for (String invalidChar : INVALID_CHARS) {
            if (text.contains(invalidChar)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReservedName(String text) {
        String upperText = text.toUpperCase();

        for (String reserved : RESERVED_NAMES) {
            if (upperText.equals(reserved)) {
                return true;
            }
        }

        return upperText.matches("COM[0-9]") || upperText.matches("LPT[0-9]");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        handleEscapeKey();

        MinecraftClient client = MinecraftClient.getInstance();

        drawOverlay(context, client);
        drawPopupBackground(context);
        drawTitle(context, client);

        if (textField != null) {
            textField.setFocused(true);
            textField.render(context, mouseX, mouseY, delta);
        }

        if (!errorMessage.isEmpty()) {
            drawErrorMessage(context, client);
        }

        if (cancelButton != null) {
            cancelButton.render(context, mouseX, mouseY, delta);
        }

        if (confirmButton != null) {
            confirmButton.render(context, mouseX, mouseY, delta);
        }
    }

    private void handleEscapeKey() {
        MinecraftClient client = MinecraftClient.getInstance();
        long windowHandle = client.getWindow() != null ? client.getWindow().getHandle() : 0;

        if (windowHandle != 0) {
            boolean escapePressed = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

            if (escapePressed && !wasEscapePressed) {
                onCancel.run();
            }

            wasEscapePressed = escapePressed;
        }
    }

    private void drawOverlay(DrawContext context, MinecraftClient client) {
        context.fill(0, 0, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight(), UITheme.Colors.OVERLAY_BG);
    }

    private void drawPopupBackground(DrawContext context) {
        context.fill(x, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, UITheme.Colors.BUTTON_BG_DISABLED);
        context.fill(x, y, x + POPUP_WIDTH, y + UITheme.Dimensions.BORDER_WIDTH, UITheme.Colors.BUTTON_BORDER);
        context.fill(x, y + POPUP_HEIGHT - UITheme.Dimensions.BORDER_WIDTH, x + POPUP_WIDTH, y + POPUP_HEIGHT, UITheme.Colors.BUTTON_BORDER);
        context.fill(x, y, x + UITheme.Dimensions.BORDER_WIDTH, y + POPUP_HEIGHT, UITheme.Colors.BUTTON_BORDER);
        context.fill(x + POPUP_WIDTH - UITheme.Dimensions.BORDER_WIDTH, y, x + POPUP_WIDTH, y + POPUP_HEIGHT, UITheme.Colors.BUTTON_BORDER);
    }

    private void drawTitle(DrawContext context, MinecraftClient client) {
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                title,
                x + POPUP_WIDTH / 2,
                y + UITheme.Dimensions.PADDING,
                UITheme.Colors.TEXT_PRIMARY
        );
    }

    private void drawErrorMessage(DrawContext context, MinecraftClient client) {
        List<String> wrappedError = wrapText(errorMessage, POPUP_WIDTH - UITheme.Dimensions.PADDING * 2, client);
        int errorY = y + UITheme.Dimensions.PADDING * 3 + 25;

        for (String line : wrappedError) {
            context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    line,
                    x + POPUP_WIDTH / 2,
                    errorY,
                    UITheme.Colors.ERROR_TEXT
            );
            errorY += UITheme.Typography.LINE_HEIGHT - 2;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOverPopup(mouseX, mouseY)) {
            onCancel.run();
            return true;
        }

        if (isMouseOverWidget(textField, mouseX, mouseY)) {
            textField.setFocused(true);
            return true;
        }

        if (isMouseOverWidget(cancelButton, mouseX, mouseY)) {
            onCancel.run();
            return true;
        }

        if (isMouseOverWidget(confirmButton, mouseX, mouseY)) {
            handleConfirm();
            return true;
        }

        return true;
    }

    private boolean isMouseOverPopup(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + POPUP_WIDTH && mouseY >= y && mouseY <= y + POPUP_HEIGHT;
    }

    private boolean isMouseOverWidget(Element widget, double mouseX, double mouseY) {
        if (widget == null) {
            return false;
        }

        return switch (widget) {
            case CustomTextField field -> mouseX >= field.getX() &&
                                          mouseX < field.getX() + field.getWidth() &&
                                          mouseY >= field.getY() &&
                                          mouseY < field.getY() + field.getHeight();
            case CustomButton button -> mouseX >= button.getX() &&
                                        mouseX < button.getX() + button.getWidth() &&
                                        mouseY >= button.getY() &&
                                        mouseY < button.getY() + button.getHeight();
            default -> false;
        };
    }

    @Override
    public void setFocused(boolean focused) {
        if (textField != null) {
            textField.setFocused(focused);
        }
    }

    @Override
    public boolean isFocused() {
        return textField != null && textField.isFocused();
    }
}
