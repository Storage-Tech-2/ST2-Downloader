package com.andrews.spdownloader;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SPDownloader implements ModInitializer {
	public static final String MOD_ID = "share-projects-downloader";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Share-Projects Downloader mod initialized");
	}
}

