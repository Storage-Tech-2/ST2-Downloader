package com.andrews.st2downloader;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ST2Downloader implements ModInitializer {
	public static final String MOD_ID = "st2-downloader";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("ST2 Downloader mod initialized");
	}
}

