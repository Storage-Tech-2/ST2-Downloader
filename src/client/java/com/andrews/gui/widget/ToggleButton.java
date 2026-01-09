package com.andrews.gui.widget;

import java.util.function.Consumer;

import com.andrews.gui.theme.UITheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;

public class ToggleButton extends Button {
	private static final int TOGGLE_WIDTH = 40;
	private static final int TOGGLE_HEIGHT = 20;
	private static final int TRACK_PADDING = 2;
	private static final int TRACK_VERTICAL_PADDING = 3;
	private static final int KNOB_PADDING = 4;
	private static final int KNOB_OFFSET = 2;

	private boolean toggled;
	private final Consumer<Boolean> onToggle;

	public ToggleButton(int x, int y, boolean initialState, Consumer<Boolean> onToggle) {
		super(x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, net.minecraft.network.chat.Component.literal(" "),
			button -> ((ToggleButton) button).toggle(),
			DEFAULT_NARRATION);
		this.toggled = initialState;
		this.onToggle = onToggle;
	}

	private void toggle() {
		this.toggled = !this.toggled;
		if (this.onToggle != null) {
			this.onToggle.accept(this.toggled);
		}
	}

	public boolean isToggled() {
		return toggled;
	}

	public void setToggled(boolean toggled) {
		this.toggled = toggled;
	}

	@Override
	protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
		boolean isHovered = isMouseOver(mouseX, mouseY);
		int trackColor = getTrackColor(isHovered);
		int trackY = getY() + TRACK_VERTICAL_PADDING;
		int trackHeight = getHeight() - (TRACK_VERTICAL_PADDING * 2);

		drawTrack(context, trackColor, trackY, trackHeight);
		drawBorder(context, trackY, trackHeight);
		drawKnob(context, trackY, trackHeight);
	}

	private int getTrackColor(boolean isHovered) {
		if (toggled) {
			return isHovered ? UITheme.Colors.TOGGLE_ON_HOVER : UITheme.Colors.TOGGLE_ON;
		}
		return isHovered ? UITheme.Colors.TOGGLE_OFF_HOVER : UITheme.Colors.BUTTON_BG_HOVER;
	}

	private void drawTrack(GuiGraphics context, int color, int trackY, int trackHeight) {
		context.fill(
			getX() + TRACK_PADDING,
			trackY,
			getX() + getWidth() - TRACK_PADDING,
			trackY + trackHeight,
			color
		);
	}

	private boolean isMouseOver(int mouseX, int mouseY) {
		return mouseX >= getX() && mouseY >= getY() &&
			   mouseX < getX() + getWidth() && mouseY < getY() + getHeight();
	}

	private void drawBorder(GuiGraphics context, int trackY, int trackHeight) {
		int x = getX();
		int width = getWidth();
		int borderWidth = UITheme.Dimensions.BORDER_WIDTH;
		int borderColor = UITheme.Colors.BUTTON_BORDER;

		context.fill(x, trackY, x + width, trackY + borderWidth, borderColor);
		context.fill(x, trackY + trackHeight - borderWidth, x + width, trackY + trackHeight, borderColor);
		context.fill(x, trackY, x + borderWidth, trackY + trackHeight, borderColor);
		context.fill(x + width - borderWidth, trackY, x + width, trackY + trackHeight, borderColor);
	}

	private void drawKnob(GuiGraphics context, int trackY, int trackHeight) {
		int knobSize = trackHeight - KNOB_PADDING;
		int knobX = toggled
			? getX() + getWidth() - knobSize - KNOB_PADDING
			: getX() + KNOB_PADDING;
		int knobY = trackY + KNOB_OFFSET;

		context.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, UITheme.Colors.TEXT_PRIMARY);
	}
}
