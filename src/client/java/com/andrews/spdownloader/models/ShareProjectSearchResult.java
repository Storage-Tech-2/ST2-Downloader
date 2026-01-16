package com.andrews.spdownloader.models;

import java.util.List;
import java.util.Map;

public record ShareProjectSearchResult(
    List<ShareProjectEntry> posts,
    int totalPages,
    int totalItems,
    Map<String, Integer> authorCounts
) {
    public ShareProjectSearchResult {
        posts = posts != null ? posts : List.of();
        authorCounts = authorCounts != null ? authorCounts : Map.of();
        totalPages = Math.max(1, totalPages);
        totalItems = Math.max(0, totalItems);
    }
}
