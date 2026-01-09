package com.andrews;

import com.andrews.gui.LitematicDownloaderScreen;
import com.andrews.keybind.ModKeybindings;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

public class ST2DownloaderClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModKeybindings.register();
		registerScreenToggleHandler();
	}

	private static void registerScreenToggleHandler() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ModKeybindings.openMenuKey.isDown()) {
				toggleLitematicDownloaderScreen(Minecraft.getInstance());
			}
		});
	}

	private static void toggleLitematicDownloaderScreen(Minecraft client) {
		if (client.screen instanceof LitematicDownloaderScreen) {
			client.setScreen(null);
		} else {
			client.setScreen(new LitematicDownloaderScreen());
		}
	}
}
