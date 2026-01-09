package com.andrews.gui.widget;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.andrews.config.DownloadSettings;
import com.andrews.gui.theme.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class ToastManager {
	private final List<Toast> toasts = new ArrayList<>();
	private final Minecraft client;
	private static final int TOP_PADDING = UITheme.Dimensions.PADDING;
	private static final int TOAST_MIN_HEIGHT = 70;

	public ToastManager(Minecraft client) {
		this.client = client;
	}

	public void showToast(String message, Toast.Type type) {
		showToast(message, type, false, null);
	}

	public void showToast(String message, Toast.Type type, boolean hasCopyButton, String copyText) {
		if (!isToastTypeEnabled(type)) {
			return;
		}

		if (client.getWindow() == null) {
			return;
		}

		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		int yPosition = calculateToastPosition(screenHeight);

		toasts.add(new Toast(message, type, screenWidth, yPosition, hasCopyButton, copyText));
	}

	public void showSuccess(String message) {
		showToast(message, Toast.Type.SUCCESS);
	}

	public void showError(String message) {
		showToast(message, Toast.Type.ERROR);
	}

	public void showError(String message, String fullErrorText) {
		showToast(message, Toast.Type.ERROR, true, fullErrorText);
	}

	public void showInfo(String message) {
		showToast(message, Toast.Type.INFO);
	}

	public void showWarning(String message) {
		showToast(message, Toast.Type.WARNING);
	}

	public void render(GuiGraphics context, float delta, int mouseX, int mouseY) {
		if (toasts.isEmpty()) {
			return;
		}

		Toast.updateMousePosition(mouseX, mouseY);

		Iterator<Toast> iterator = toasts.iterator();
		boolean toastRemoved = false;

		while (iterator.hasNext()) {
			Toast toast = iterator.next();
			toast.setHovered(toast.isHovering(mouseX, mouseY));

			if (toast.render(context, client.font)) {
				iterator.remove();
				toastRemoved = true;
			}
		}

		if (toastRemoved && !toasts.isEmpty()) {
			recalculatePositions();
		}
	}

	public void clear() {
		toasts.clear();
	}

	public boolean hasToasts() {
		return !toasts.isEmpty();
	}

	public boolean isMouseOverToast(double mouseX, double mouseY) {
		for (Toast toast : toasts) {
			if (toast.isHovering(mouseX, mouseY)) {
				return true;
			}
		}
		return false;
	}

	public boolean mouseClicked(double mouseX, double mouseY) {
		for (Toast toast : toasts) {
			if (handleCloseButton(toast, mouseX, mouseY)) {
				return true;
			}
			if (handleCopyButton(toast, mouseX, mouseY)) {
				return true;
			}
		}
		return false;
	}

	private boolean isToastTypeEnabled(Toast.Type type) {
		DownloadSettings settings = DownloadSettings.getInstance();
		return switch (type) {
			case SUCCESS -> settings.isSuccessToastsEnabled();
			case ERROR -> settings.isErrorToastsEnabled();
			case INFO -> settings.isInfoToastsEnabled();
			case WARNING -> settings.isWarningToastsEnabled();
		};
	}

	private int calculateToastPosition(int screenHeight) {
		int yPosition = TOP_PADDING;
		for (Toast toast : toasts) {
			yPosition += toast.getHeight();
		}

		if (yPosition + TOAST_MIN_HEIGHT > screenHeight && !toasts.isEmpty()) {
			toasts.remove(0);
			yPosition = TOP_PADDING;
			for (Toast toast : toasts) {
				yPosition += toast.getHeight();
			}
		}

		return yPosition;
	}

	private void recalculatePositions() {
		int newY = TOP_PADDING;
		for (Toast toast : toasts) {
			toast.setTargetYPosition(newY);
			newY += toast.getHeight();
		}
	}

	private boolean handleCloseButton(Toast toast, double mouseX, double mouseY) {
		if (toast.isCloseButtonClicked(mouseX, mouseY)) {
			toast.dismiss();
			return true;
		}
		return false;
	}

	private boolean handleCopyButton(Toast toast, double mouseX, double mouseY) {
		if (toast.isCopyButtonClicked(mouseX, mouseY)) {
			String textToCopy = toast.getCopyText();
			if (client.keyboardHandler != null) {
				client.keyboardHandler.setClipboard(textToCopy);
				showSuccess("Error copied to clipboard!");
				return true;
			}
		}
		return false;
	}
}

