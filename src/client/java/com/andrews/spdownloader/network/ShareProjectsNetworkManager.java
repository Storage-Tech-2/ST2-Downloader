package com.andrews.spdownloader.network;

import com.andrews.spdownloader.models.ShareProjectEntry;
import com.andrews.spdownloader.models.ShareProjectSearchResult;
import com.andrews.spdownloader.models.ShareProjectEntry.Dimensions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ShareProjectsNetworkManager {
    private static final String BASE_URL = "https://andrews54757.github.io/st-shared-projects-browser/";
    private static final String INDEX_URL = BASE_URL + "litematics-processed.json";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();
    private static final Gson GSON = new Gson();

    private static volatile List<ShareProjectEntry> CACHED = null;
    private static CompletableFuture<List<ShareProjectEntry>> LOADING = null;

    public static CompletableFuture<ShareProjectSearchResult> search(String query, String authorFilter, int page, int itemsPerPage) {
        return ensureEntriesLoaded().thenApply(entries -> {
            String normalizedQuery = normalize(query);
            String normalizedAuthor = normalize(authorFilter);

            List<ShareProjectEntry> filteredByQuery = entries.stream()
                .filter(entry -> matchesQuery(entry, normalizedQuery))
                .toList();

            Map<String, Integer> authorCounts = buildAuthorCounts(filteredByQuery);

            List<ShareProjectEntry> filtered = normalizedAuthor.isEmpty()
                ? filteredByQuery
                : filteredByQuery.stream()
                    .filter(e -> normalize(e.author()).equals(normalizedAuthor))
                    .toList();

            List<ShareProjectEntry> sorted = new ArrayList<>(filtered);
            sorted.sort(Comparator.comparingLong(ShareProjectsNetworkManager::getTimestamp).reversed()
                .thenComparing(e -> e.displayName().toLowerCase(Locale.ROOT)));

            int safeItemsPerPage = Math.max(1, itemsPerPage);
            int totalItems = sorted.size();
            int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) safeItemsPerPage));
            int start = Math.max(0, Math.min((page - 1) * safeItemsPerPage, Math.max(0, totalItems - 1)));
            int end = Math.min(totalItems, start + safeItemsPerPage);
            List<ShareProjectEntry> pageItems = sorted.subList(start, end);

            return new ShareProjectSearchResult(pageItems, totalPages, totalItems, authorCounts);
        });
    }

    public static void clearCache() {
        CACHED = null;
        LOADING = null;
    }

    private static CompletableFuture<List<ShareProjectEntry>> ensureEntriesLoaded() {
        List<ShareProjectEntry> cached = CACHED;
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        synchronized (ShareProjectsNetworkManager.class) {
            if (CACHED != null) {
                return CompletableFuture.completedFuture(CACHED);
            }
            if (LOADING == null) {
                LOADING = fetchEntries();
            }
            return LOADING;
        }
    }

    private static CompletableFuture<List<ShareProjectEntry>> fetchEntries() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(INDEX_URL))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", ArchiveNetworkManager.USER_AGENT)
            .GET()
            .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new CompletionException(new IllegalStateException("HTTP " + response.statusCode() + " loading index"));
                }
                return parseEntries(response.body());
            })
            .whenComplete((result, throwable) -> {
                synchronized (ShareProjectsNetworkManager.class) {
                    if (throwable == null && result != null) {
                        CACHED = result;
                    }
                    LOADING = null;
                }
            });
    }

    private static List<ShareProjectEntry> parseEntries(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("entries") || !root.get("entries").isJsonArray()) {
                return List.of();
            }
            JsonArray arr = root.getAsJsonArray("entries");
            List<ShareProjectEntry> entries = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                ShareProjectEntry entry = parseEntry(el.getAsJsonObject());
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return entries;
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private static ShareProjectEntry parseEntry(JsonObject obj) {
        String file = getString(obj, "file");
        if (file == null || file.isBlank()) {
            return null;
        }
        String displayName = buildDisplayName(file);
        long fileSize = getLong(obj.get("fileSizeBytes"));
        JsonObject dimsObj = obj.has("dimensions") && obj.get("dimensions").isJsonObject()
            ? obj.getAsJsonObject("dimensions")
            : null;
        Dimensions dims = new Dimensions(
            dimsObj != null ? getInt(dimsObj.get("x")) : 0,
            dimsObj != null ? getInt(dimsObj.get("y")) : 0,
            dimsObj != null ? getInt(dimsObj.get("z")) : 0
        );

        String sizeText = getString(obj, "size");
        String version = getString(obj, "version");
        long createdAt = getLong(obj.get("timeCreated"));
        String author = getString(obj, "author");
        boolean hasImage = obj.has("has_image") && obj.get("has_image").getAsBoolean();

        String pngName = file.replaceAll("(?i)\\.litematic$", ".png");
        String encodedPng = encodePathSegment(pngName);
        String encodedFile = encodePathSegment(file);

        String renderUrl = hasImage ? BASE_URL + "litematic-renders-cropped/" + encodedPng : null;
        String downloadUrl = BASE_URL + "litematic-files/" + encodedFile;

        return new ShareProjectEntry(
            file,
            file,
            displayName,
            author,
            createdAt,
            fileSize,
            version,
            sizeText,
            dims,
            hasImage,
            renderUrl,
            downloadUrl
        );
    }

    private static Map<String, Integer> buildAuthorCounts(List<ShareProjectEntry> entries) {
        Map<String, Integer> counts = new HashMap<>();
        for (ShareProjectEntry entry : entries) {
            String author = entry.author() != null && !entry.author().isBlank() ? entry.author() : "Unknown";
            counts.put(author, counts.getOrDefault(author, 0) + 1);
        }
        return counts.entrySet().stream()
            .sorted((a, b) -> {
                int cmp = Integer.compare(b.getValue(), a.getValue());
                if (cmp != 0) return cmp;
                return a.getKey().compareToIgnoreCase(b.getKey());
            })
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (a, b) -> a,
                LinkedHashMap::new
            ));
    }

    private static boolean matchesQuery(ShareProjectEntry entry, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        if (contains(entry.displayName(), normalizedQuery)) return true;
        if (contains(entry.fileName(), normalizedQuery)) return true;
        return contains(entry.author(), normalizedQuery);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static long getTimestamp(ShareProjectEntry entry) {
        return entry != null ? entry.createdAt() : 0L;
    }

    private static String normalize(String value) {
        return value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        if (el.isJsonNull()) return null;
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return el.getAsJsonPrimitive().getAsNumber().toString();
        }
        return el.getAsString();
    }

    private static long getLong(JsonElement el) {
        if (el == null || el.isJsonNull()) return 0L;
        try {
            if (el.isJsonPrimitive()) {
                var prim = el.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    return prim.getAsLong();
                }
                if (prim.isString()) {
                    String s = prim.getAsString();
                    if (s.matches("\\d+")) {
                        return Long.parseLong(s);
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private static int getInt(JsonElement el) {
        if (el == null || el.isJsonNull()) return 0;
        try {
            if (el.isJsonPrimitive()) {
                var prim = el.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    return prim.getAsInt();
                }
                if (prim.isString() && prim.getAsString().matches("-?\\d+")) {
                    return Integer.parseInt(prim.getAsString());
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static String buildDisplayName(String file) {
        if (file == null) return "";
        String base = file.replaceAll("(?i)\\.litematic$", "");
        String withoutSnowflake = base.replaceAll("[-_]*\\d{8,}$", "").replaceAll("[-_]+$", "");
        String cleaned = !withoutSnowflake.isEmpty() ? withoutSnowflake : base;
        return cleaned.replace("_", " ").trim();
    }

    private static String encodePathSegment(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
