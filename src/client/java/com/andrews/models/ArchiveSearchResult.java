package com.andrews.models;

import java.util.List;
import java.util.Map;

public record ArchiveSearchResult(
    List<ArchivePostSummary> posts,
    int totalPages,
    int totalItems,
    Map<String, Integer> channelCounts
) {
}
