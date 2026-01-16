package com.andrews.st2downloader.models;

public record ArchiveChannel(
	String id,
	String name,
	String code,
	String category,
	String path,
	String description,
	int entryCount,
	java.util.List<String> availableTags
) {
}
