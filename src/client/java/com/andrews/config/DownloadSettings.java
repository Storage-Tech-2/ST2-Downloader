package com.andrews.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class DownloadSettings {
	private static final String CONFIG_FILE = "st2-downloader-settings.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static DownloadSettings INSTANCE;

	private JsonObject config;

	private DownloadSettings() {
		this.config = loadConfig();
		applyDefaults();
	}

	public static DownloadSettings getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new DownloadSettings();
		}
		return INSTANCE;
	}

	private JsonObject loadConfig() {
		File configFile = getConfigFile();
		if (!configFile.exists()) {
			return new JsonObject();
		}
		try (FileReader reader = new FileReader(configFile)) {
			JsonObject json = GSON.fromJson(reader, JsonObject.class);
			return json != null ? json : new JsonObject();
		} catch (IOException e) {
			System.err.println("Failed to load settings: " + e.getMessage());
			return new JsonObject();
		}
	}

	private void applyDefaults() {
		
		setDefault("downloadPath", Path.of("schematics","ST2").toString());
		setDefault("successToastsEnabled", false);
		setDefault("errorToastsEnabled", true);
		setDefault("infoToastsEnabled", false);
		setDefault("warningToastsEnabled", true);
		setDefault("sortOption", "newest");
		setDefault("itemsPerPage", 20);
		setDefault("tagFilter", "");
		setDefault("joinedDiscord", false);
	}

	private void setDefault(String key, Object value) {
		if (!config.has(key)) {
			if (value instanceof String) {
				config.addProperty(key, (String) value);
			} else if (value instanceof Number) {
				config.addProperty(key, (Number) value);
			} else if (value instanceof Boolean) {
				config.addProperty(key, (Boolean) value);
			}
		}
	}

	public String getDownloadPath() {
		return config.get("downloadPath").getAsString();
	}

	public String getAbsoluteDownloadPath() {
		Path gamePath = FabricLoader.getInstance().getGameDir();
		return gamePath.resolve(getDownloadPath()).toString();
	}

	public String getGameDirectory() {
		return FabricLoader.getInstance().getGameDir().toString();
	}

	private boolean isToastEnabled(String type) {
		return config.get(type + "ToastsEnabled").getAsBoolean();
	}

	public String getSortOption() {
		return config.get("sortOption").getAsString();
	}

	public int getItemsPerPage() {
		return config.get("itemsPerPage").getAsInt();
	}

	public String getTagFilter() {
		return config.get("tagFilter").getAsString();
	}

	private void set(String key, Object value) {
		if (value instanceof String) {
			config.addProperty(key, (String) value);
		} else if (value instanceof Number) {
			config.addProperty(key, (Number) value);
		} else if (value instanceof Boolean) {
			config.addProperty(key, (Boolean) value);
		}
		save();
	}

	public boolean isSuccessToastsEnabled() {
		return isToastEnabled("success");
	}

	public void setSuccessToastsEnabled(boolean enabled) {
		set("successToastsEnabled", enabled);
	}

	public boolean isErrorToastsEnabled() {
		return isToastEnabled("error");
	}

	public void setErrorToastsEnabled(boolean enabled) {
		set("errorToastsEnabled", enabled);
	}

	public boolean isInfoToastsEnabled() {
		return isToastEnabled("info");
	}

	public void setInfoToastsEnabled(boolean enabled) {
		set("infoToastsEnabled", enabled);
	}

	public boolean isWarningToastsEnabled() {
		return isToastEnabled("warning");
	}

	public void setWarningToastsEnabled(boolean enabled) {
		set("warningToastsEnabled", enabled);
	}

	public void setDownloadPath(String path) {
		set("downloadPath", path);
	}

	public void setSortOption(String sortOption) {
		set("sortOption", sortOption);
	}

	public void setItemsPerPage(int itemsPerPage) {
		set("itemsPerPage", itemsPerPage);
	}

	public void setTagFilter(String tagFilter) {
		set("tagFilter", tagFilter != null ? tagFilter : "");
	}

	public boolean hasJoinedDiscord() {
		return config.has("joinedDiscord") && config.get("joinedDiscord").getAsBoolean();
	}

	public void setJoinedDiscord(boolean joined) {
		set("joinedDiscord", joined);
	}

	private File getConfigFile() {
		Path configDir = FabricLoader.getInstance().getConfigDir();
		return configDir.resolve(CONFIG_FILE).toFile();
	}

	private void save() {
		try {
			File configFile = getConfigFile();
			File parentDir = configFile.getParentFile();
			if (parentDir != null && !parentDir.exists()) {
				if (!parentDir.mkdirs()) {
					System.err.println("Failed to create config directory");
					return;
				}
			}
			try (FileWriter writer = new FileWriter(configFile)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			System.err.println("Failed to save settings: " + e.getMessage());
		}
	}
}
