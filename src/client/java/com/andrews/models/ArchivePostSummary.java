package com.andrews.models;

public record ArchivePostSummary(
	String id,
	String title,
	String channelName,
	String channelCode,
	String channelCategory,
	String channelPath,
	String entryPath,
	String code,
	String[] tags,
	long archivedAt,
	long updatedAt
) {
	public ArchivePostSummary {
		tags = tags != null ? tags : new String[0];
	}
}
