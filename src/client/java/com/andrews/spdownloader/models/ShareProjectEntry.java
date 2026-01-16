package com.andrews.spdownloader.models;

public record ShareProjectEntry(
    String id,
    String fileName,
    String displayName,
    String author,
    long createdAt,
    long fileSizeBytes,
    String version,
    String sizeText,
    Dimensions dimensions,
    boolean hasImage,
    String renderUrl,
    String downloadUrl
) {
    public record Dimensions(int x, int y, int z) {}

    public ShareProjectEntry {
        fileName = fileName != null ? fileName : id;
        displayName = displayName != null ? displayName : fileName;
        author = author != null && !author.isBlank() ? author : "Unknown";
        version = version != null ? version : "";
        sizeText = sizeText != null ? sizeText : "";
        dimensions = dimensions != null ? dimensions : new Dimensions(0, 0, 0);
        renderUrl = renderUrl != null && !renderUrl.isBlank() ? renderUrl : null;
        downloadUrl = downloadUrl != null && !downloadUrl.isBlank() ? downloadUrl : null;
    }
}
