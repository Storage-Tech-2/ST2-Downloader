package com.andrews.models;

public record ArchiveAttachment(
	String name,
	String downloadUrl,
	String contentType,
	boolean canDownload,
	String sizeText,
	String description,
	LitematicInfo litematic,
	WdlInfo wdl
) {
	public boolean isDownloadable() {
		return canDownload && downloadUrl != null && !downloadUrl.isEmpty();
	}

	public record LitematicInfo(String version, String size, String error) {}

	public record WdlInfo(String version, String error) {}
}
