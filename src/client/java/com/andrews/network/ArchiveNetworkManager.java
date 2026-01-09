package com.andrews.network;

import com.andrews.config.ServerDictionary;
import com.andrews.config.ServerDictionary.ServerEntry;
import com.andrews.models.ArchiveAttachment;
import com.andrews.models.ArchiveChannel;
import com.andrews.models.ArchiveImageInfo;
import com.andrews.models.ArchivePostDetail;
import com.andrews.models.ArchivePostSummary;
import com.andrews.models.ArchiveRecordSection;
import com.andrews.models.ArchiveSearchResult;
import com.andrews.models.DiscordPostReference;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

public class ArchiveNetworkManager {
	private static final String DEFAULT_BRANCH = "main";
	private static final String RAW_BASE = "https://raw.githubusercontent.com";
	public static final String USER_AGENT = "ST2Downloader/1.0 (+https://github.com/Storage-Tech-2/ST2-Downloader)";

	private static final int TIMEOUT_SECONDS = 10;
	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
		.build();

	private static final Map<String, ArchiveIndexCache> CACHED_INDEXES = new ConcurrentHashMap<>();
	private static final Map<String, CompletableFuture<ArchiveIndexCache>> INDEX_FUTURES = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, StyleInfo>> CACHED_SCHEMA_STYLES = new ConcurrentHashMap<>();

	public static CompletableFuture<ArchiveSearchResult> searchPosts(
		ServerEntry server,
		String query,
		String sort,
		String tag,
		List<String> includeTags,
		List<String> excludeTags,
		List<String> channelPaths,
		int page,
		int itemsPerPage
	) {
		ServerEntry targetServer = normalizeServer(server);
		return ensureIndexLoaded(targetServer).thenApply(index -> {
			List<ArchivePostSummary> filtered = new ArrayList<>(filterPosts(index.posts(), query, tag, includeTags, excludeTags, channelPaths));
			sortPosts(filtered, sort);

			Map<String, Integer> channelCounts = new LinkedHashMap<>();
			for (ArchiveChannel channel : index.channels()) {
				if (channel != null && channel.path() != null) {
					channelCounts.put(channel.path(), 0);
				}
			}
			for (ArchivePostSummary post : filtered) {
				if (post == null || post.channelPath() == null) continue;
				String path = post.channelPath();
				channelCounts.put(path, channelCounts.getOrDefault(path, 0) + 1);
			}

			int totalItems = filtered.size();
			int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) Math.max(itemsPerPage, 1)));

			int startIndex = Math.max(0, (page - 1) * Math.max(itemsPerPage, 1));
			int endIndex = Math.min(filtered.size(), startIndex + Math.max(itemsPerPage, 1));
			List<ArchivePostSummary> pageItems = filtered.subList(
				Math.min(startIndex, filtered.size()),
				Math.min(endIndex, filtered.size())
			);

			return new ArchiveSearchResult(pageItems, totalPages, totalItems, channelCounts);
		});
	}

	public static CompletableFuture<ArchiveSearchResult> searchPosts(
		String query,
		String sort,
		String tag,
		List<String> includeTags,
		List<String> excludeTags,
		List<String> channelPaths,
		int page,
		int itemsPerPage
	) {
		return searchPosts(ServerDictionary.getDefaultServer(), query, sort, tag, includeTags, excludeTags, channelPaths, page, itemsPerPage);
	}

	public static CompletableFuture<ArchiveSearchResult> searchPosts(
		String query,
		String sort,
		String tag,
		int page,
		int itemsPerPage
	) {
		return searchPosts(ServerDictionary.getDefaultServer(), query, sort, tag, null, null, null, page, itemsPerPage);
	}

	public static CompletableFuture<ArchivePostDetail> getPostDetails(ServerEntry server, ArchivePostSummary summary) {
		ServerEntry targetServer = normalizeServer(server);
		return fetchEntryDataAsync(targetServer, summary.channelPath(), summary.entryPath())
			.thenApply(data -> toPostDetail(targetServer, summary, data));
	}

	public static CompletableFuture<ArchivePostDetail> getPostDetails(ArchivePostSummary summary) {
		return getPostDetails(ServerDictionary.getDefaultServer(), summary);
	}

	public static CompletableFuture<List<ArchiveChannel>> getChannels(ServerEntry server) {
		return ensureIndexLoaded(normalizeServer(server)).thenApply(ArchiveIndexCache::channels);
	}

	public static CompletableFuture<List<ArchiveChannel>> getChannels() {
		return getChannels(ServerDictionary.getDefaultServer());
	}

	public static void clearCache(ServerEntry server) {
		String key = serverKey(normalizeServer(server));
		CACHED_INDEXES.remove(key);
		INDEX_FUTURES.remove(key);
		CACHED_SCHEMA_STYLES.remove(key);
	}

	public static void clearCache() {
		CACHED_INDEXES.clear();
		INDEX_FUTURES.clear();
		CACHED_SCHEMA_STYLES.clear();
	}

	private static CompletableFuture<ArchiveIndexCache> loadIndexAsync(ServerEntry server) {
		ServerEntry targetServer = normalizeServer(server);
		String key = serverKey(targetServer);

		return fetchConfigAsync(targetServer).thenCompose(config -> {
			Map<String, StyleInfo> styles = config.postStyle != null ? config.postStyle : Map.of();
			CACHED_SCHEMA_STYLES.put(key, styles);

			List<ArchivePostSummary> posts = new ArrayList<>();
			List<ArchiveChannel> channels = new ArrayList<>();

			if (config.archiveChannels == null || config.archiveChannels.isEmpty()) {
				ArchiveIndexCache cache = new ArchiveIndexCache(posts, channels);
				return CompletableFuture.completedFuture(cache);
			}

			List<CompletableFuture<ChannelBundle>> futures = new ArrayList<>();
			for (ChannelRef channel : config.archiveChannels) {
				futures.add(fetchChannelDataAsync(targetServer, channel.path).thenApply(data -> new ChannelBundle(channel, data)));
			}

			return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
				.thenApply(v -> {
					for (CompletableFuture<ChannelBundle> future : futures) {
						ChannelBundle bundle = future.join();
						ChannelRef channel = bundle.channel();
						ChannelData channelData = bundle.data();
						int entryCount = 0;
						if (channelData.entries != null) {
							for (EntryRef entry : channelData.entries) {
								posts.add(toSummary(channel, entry));
							}
							entryCount = channelData.entries.size();
						}
						channels.add(new ArchiveChannel(
							channel.id,
							channel.name,
							channel.code,
							channel.category,
							channel.path,
							channel.description,
							entryCount,
							channel.availableTags != null ? channel.availableTags : List.of()
						));
					}
					return new ArchiveIndexCache(posts, channels);
				});
		}).whenComplete((cache, throwable) -> {
			if (throwable == null && cache != null) {
				CACHED_INDEXES.put(key, cache);
			} else {
				INDEX_FUTURES.remove(key);
			}
		});
	}

	private static CompletableFuture<ArchiveIndexCache> ensureIndexLoaded(ServerEntry server) {
		ServerEntry targetServer = normalizeServer(server);
		String key = serverKey(targetServer);
		ArchiveIndexCache cached = CACHED_INDEXES.get(key);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}

		return INDEX_FUTURES.computeIfAbsent(key, k -> loadIndexAsync(targetServer));
	}

	private static ServerEntry normalizeServer(ServerEntry server) {
		return server != null ? server : ServerDictionary.getDefaultServer();
	}

	private static String serverKey(ServerEntry server) {
		ServerEntry target = normalizeServer(server);
		if (target.id() != null && !target.id().isBlank()) {
			return target.id().toLowerCase(Locale.ROOT);
		}
		return target.name() != null ? target.name().toLowerCase(Locale.ROOT) : "default";
	}

	private static Map<String, StyleInfo> getSchemaStyles(ServerEntry server) {
		return CACHED_SCHEMA_STYLES.getOrDefault(serverKey(server), Map.of());
	}

	private static List<ArchivePostSummary> filterPosts(List<ArchivePostSummary> posts, String query, String tagFilter, List<String> includeTags, List<String> excludeTags, List<String> channelPaths) {
		String normalizedQuery = query != null ? query.toLowerCase(Locale.ROOT).trim() : "";
		String normalizedTag = tagFilter != null ? tagFilter.toLowerCase(Locale.ROOT).trim() : "";
		List<String> normalizedChannels = channelPaths != null
			? channelPaths.stream().map(p -> p != null ? p.toLowerCase(Locale.ROOT) : "").toList()
			: List.of();
		List<String> normalizedInclude = includeTags != null
			? includeTags.stream().filter(t -> t != null && !t.isEmpty()).map(t -> t.toLowerCase(Locale.ROOT)).toList()
			: List.of();
		List<String> normalizedExclude = excludeTags != null
			? excludeTags.stream().filter(t -> t != null && !t.isEmpty()).map(t -> t.toLowerCase(Locale.ROOT)).toList()
			: List.of();

		return posts.stream()
			.filter(post -> normalizedQuery.isEmpty() ||
				(post.title() != null && post.title().toLowerCase(Locale.ROOT).contains(normalizedQuery)) ||
				(post.code() != null && post.code().toLowerCase(Locale.ROOT).contains(normalizedQuery)))
			.filter(post -> normalizedTag.isEmpty() || hasTagMatch(post, normalizedTag))
			.filter(post -> normalizedInclude.isEmpty() || hasAllTags(post, normalizedInclude))
			.filter(post -> normalizedExclude.isEmpty() || !hasAnyTag(post, normalizedExclude))
			.filter(post -> normalizedChannels.isEmpty() ||
				(post.channelPath() != null && normalizedChannels.contains(post.channelPath().toLowerCase(Locale.ROOT))))
			.toList();
	}

	private static boolean hasTagMatch(ArchivePostSummary post, String normalizedTag) {
		for (String tag : post.tags()) {
			if (tag != null && tag.toLowerCase(Locale.ROOT).contains(normalizedTag)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasAllTags(ArchivePostSummary post, List<String> required) {
		for (String req : required) {
			boolean found = false;
			for (String tag : post.tags()) {
				if (tag != null && tag.toLowerCase(Locale.ROOT).equals(req)) {
					found = true;
					break;
				}
			}
			if (!found) return false;
		}
		return true;
	}

	private static boolean hasAnyTag(ArchivePostSummary post, List<String> tags) {
		for (String tag : post.tags()) {
			if (tag == null) continue;
			String lowered = tag.toLowerCase(Locale.ROOT);
			if (tags.contains(lowered)) return true;
		}
		return false;
	}

	private static void sortPosts(List<ArchivePostSummary> posts, String sort) {
		String selectedSort = (sort == null || sort.isEmpty()) ? "newest" : sort;
		Comparator<ArchivePostSummary> comparator;

		switch (selectedSort) {
			case "updated" -> comparator = Comparator.comparingLong(ArchiveNetworkManager::getUpdatedTimestamp).reversed();
			case "name" -> comparator = Comparator.comparing(
				p -> p.title() != null ? p.title().toLowerCase(Locale.ROOT) : ""
			);
			case "code" -> comparator = Comparator.comparing(
				p -> p.code() != null ? p.code().toLowerCase(Locale.ROOT) : ""
			);
			default -> comparator = Comparator.comparingLong(ArchiveNetworkManager::getUpdatedTimestamp).reversed();
		}

		posts.sort(comparator);
	}

	private static long getUpdatedTimestamp(ArchivePostSummary post) {
		if (post == null) return 0L;
		long updated = post.updatedAt();
		if (updated > 0) return updated;
		return post.archivedAt();
	}

	private static ArchivePostSummary toSummary(ChannelRef channel, EntryRef entry) {
		String[] tags = entry.tags != null ? entry.tags.toArray(new String[0]) : new String[0];
		long updatedAt = entry.updatedAt != null && entry.updatedAt > 0
			? entry.updatedAt
			: (entry.archivedAt != null ? entry.archivedAt : 0L);
		long archivedAt = entry.archivedAt != null ? entry.archivedAt : 0L;

		return new ArchivePostSummary(
			entry.id,
			entry.name,
			channel.name,
			channel.code,
			channel.category,
			channel.path,
			entry.path,
			entry.code,
			tags,
			archivedAt,
			updatedAt
		);
	}

	private static ArchivePostDetail toPostDetail(ServerEntry server, ArchivePostSummary summary, ArchiveEntryData data) {
		List<String> authors = new ArrayList<>();
		if (data.authors != null) {
			for (ArchiveAuthor author : data.authors) {
				if (author != null) {
					String name = author.displayName != null && !author.displayName.isEmpty()
						? author.displayName
						: author.username;
					if (name != null && !name.isEmpty()) {
						authors.add(name);
					}
				}
			}
		}

		List<String> images = new ArrayList<>();
		List<ArchiveImageInfo> imageInfos = new ArrayList<>();
		if (data.images != null) {
			for (ArchiveImageData image : data.images) {
				String url = resolveImagePath(server, image.path, summary.channelPath(), summary.entryPath());
				if (url != null && !url.isEmpty()) {
					images.add(url);
					imageInfos.add(new ArchiveImageInfo(
						url,
						image.description,
						image.width,
						image.height
					));
				}
			}
		}

		List<ArchiveAttachment> attachments = new ArrayList<>();
		if (data.attachments != null) {
			for (ArchiveAttachmentData attachment : data.attachments) {
				boolean canDownload = attachment.canDownload == null || attachment.canDownload;
				String downloadUrl = canDownload ? resolveAttachmentPath(server, attachment.path, summary.channelPath(), summary.entryPath()) : attachment.url;
				String sizeText = attachment.litematic != null ? attachment.litematic.size : null;
				ArchiveAttachment.YoutubeInfo youtubeInfo = null;
				if (attachment.youtube != null) {
					youtubeInfo = new ArchiveAttachment.YoutubeInfo(
						attachment.youtube.title,
						attachment.youtube.author_name,
						attachment.youtube.author_url
					);
				}
				attachments.add(new ArchiveAttachment(
					attachment.name != null ? attachment.name : "Attachment",
					downloadUrl,
					attachment.contentType,
					canDownload,
					sizeText,
					attachment.description,
					attachment.litematic != null ? new ArchiveAttachment.LitematicInfo(
						attachment.litematic.version,
						attachment.litematic.size,
						attachment.litematic.error
					) : null,
					attachment.wdl != null ? new ArchiveAttachment.WdlInfo(
						attachment.wdl.version,
						attachment.wdl.error
					) : null,
					youtubeInfo
				));
			}
		}

		List<ArchiveRecordSection> recordSections = toRecordSections(
			data.records,
			getSchemaStyles(server),
			data.styles != null ? data.styles : Map.of()
		);

		long archivedAt = data.archivedAt != null ? data.archivedAt : summary.archivedAt();
		long updatedAt = data.updatedAt != null ? data.updatedAt : summary.updatedAt();
		DiscordPostReference discordPost = toDiscordPostReference(data.post);

		return new ArchivePostDetail(
			summary,
			authors,
			images,
			imageInfos,
			attachments,
			discordPost,
			recordSections,
			archivedAt,
			updatedAt
		);
	}

	private static DiscordPostReference toDiscordPostReference(ArchiveDiscordPostReference post) {
		if (post == null) {
			return null;
		}
		List<String> continuing = post.continuingMessageIds != null ? post.continuingMessageIds : List.of();
		return new DiscordPostReference(
			post.forumId,
			post.threadId,
			continuing,
			post.threadURL,
			post.attachmentMessageId,
			post.uploadMessageId
		);
	}

	private static List<ArchiveRecordSection> toRecordSections(JsonObject records, Map<String, StyleInfo> schemaStyles, Map<String, StyleInfo> recordStyles) {
		List<ArchiveRecordSection> sections = new ArrayList<>();
		if (records == null || records.entrySet().isEmpty()) {
			return sections;
		}

		LinkedHashMap<String, SectionLines> orderedSections = new LinkedHashMap<>();
		boolean isFirst = true;

		for (Map.Entry<String, JsonElement> entry : records.entrySet()) {
			String key = entry.getKey();
			ensureParentSections(key, orderedSections, schemaStyles, recordStyles);

			StyleInfo style = getEffectiveStyle(key, schemaStyles, recordStyles);
			String text = submissionRecordToMarkdown(entry.getValue(), style);
			if (text.isEmpty()) {
				continue;
			}

			String headerText = style.headerText.isBlank() && isFirst
				? "Description"
				: style.headerText;
			SectionLines section = orderedSections.computeIfAbsent(key, k -> new SectionLines(headerText));
			section.addLines(text.split("\\r?\\n"));
			isFirst = false;
		}

		for (SectionLines value : orderedSections.values()) {
			if (!value.lines.isEmpty()) {
				sections.add(new ArchiveRecordSection(value.title, new ArrayList<>(value.lines)));
			}
		}
		return sections;
	}

	private static void ensureParentSections(String key, LinkedHashMap<String, SectionLines> map, Map<String, StyleInfo> schemaStyles, Map<String, StyleInfo> recordStyles) {
		String[] parts = key.split(":");
		if (parts.length <= 1) return;
		for (int i = 1; i < parts.length; i++) {
			String parentKey = String.join(":", Arrays.copyOfRange(parts, 0, i));
			if (!map.containsKey(parentKey)) {
				StyleInfo parentStyle = getEffectiveStyle(parentKey, schemaStyles, recordStyles);
				map.put(parentKey, new SectionLines(parentStyle.headerText));
			}
		}
	}

	private static StyleInfo getEffectiveStyle(String key, Map<String, StyleInfo> schemaStyles, Map<String, StyleInfo> recordStyles) {
		StyleInfo style = new StyleInfo();
		style.depth = 2;
		style.headerText = capitalizeFirstSegment(key);
		style.isOrdered = false;

		StyleInfo schemaStyle = schemaStyles != null ? schemaStyles.get(key) : null;
		if (schemaStyle != null) {
			if (schemaStyle.depth != null) style.depth = schemaStyle.depth;
			if (schemaStyle.headerText != null) style.headerText = schemaStyle.headerText;
			if (schemaStyle.isOrdered != null) style.isOrdered = schemaStyle.isOrdered;
		}

		StyleInfo recordStyle = recordStyles != null ? recordStyles.get(key) : null;
		if (recordStyle != null) {
			if (recordStyle.depth != null) style.depth = recordStyle.depth;
			if (recordStyle.headerText != null) style.headerText = recordStyle.headerText;
			if (recordStyle.isOrdered != null) style.isOrdered = recordStyle.isOrdered;
		}
		return style;
	}

	private static String capitalizeFirstSegment(String key) {
		if (key == null || key.isEmpty()) return "";
		String[] parts = key.split(":");
		String target = parts[parts.length - 1];
		if (target.isEmpty()) return "";
		return target.substring(0, 1).toUpperCase(Locale.ROOT) + target.substring(1);
	}

	private static String submissionRecordToMarkdown(JsonElement value, StyleInfo style) {
		if (value == null || value.isJsonNull()) return "";
		StringBuilder markdown = new StringBuilder();
		if (value.isJsonArray()) {
			JsonArray array = value.getAsJsonArray();
			for (int i = 0; i < array.size(); i++) {
				JsonElement item = array.get(i);
				boolean ordered = style.isOrdered != null && style.isOrdered;
				String prefix = ordered ? (i + 1) + ". " : "- ";
				if (item.isJsonPrimitive()) {
					markdown.append(prefix).append(stripUrls(item.getAsString())).append("\n");
				} else if (item.isJsonObject()) {
					JsonObject obj = item.getAsJsonObject();
					markdown.append(prefix);
					if (obj.has("title")) {
						markdown.append(stripUrls(obj.get("title").getAsString())).append("\n");
					}
					if (obj.has("items")) {
						markdown.append(nestedListToMarkdown(obj, ordered ? 2 : 1));
					}
				}
			}
		} else if (value.isJsonObject()) {
			JsonObject obj = value.getAsJsonObject();
			if (obj.has("items")) {
				markdown.append(nestedListToMarkdown(obj, 0));
			} else {
				markdown.append(stripUrls(obj.toString()));
			}
		} else {
			markdown.append(stripUrls(value.getAsString()));
		}
		return stripUrls(markdown.toString().trim());
	}

	private static String nestedListToMarkdown(JsonObject nestedList, int indentLevel) {
		StringBuilder markdown = new StringBuilder();
		boolean isOrdered = nestedList.has("isOrdered") && nestedList.get("isOrdered").getAsBoolean();
		JsonArray items = nestedList.has("items") && nestedList.get("items").isJsonArray() ? nestedList.getAsJsonArray("items") : new JsonArray();
		String indent = "  ".repeat(Math.max(indentLevel, 0));

		for (int i = 0; i < items.size(); i++) {
			JsonElement item = items.get(i);
			String prefix = isOrdered ? (indent + (i + 1) + ". ") : (indent + "- ");
			if (item.isJsonPrimitive()) {
				markdown.append(prefix).append(stripUrls(item.getAsString())).append("\n");
			} else if (item.isJsonObject()) {
				JsonObject child = item.getAsJsonObject();
				if (child.has("title")) {
					markdown.append(prefix).append(stripUrls(child.get("title").getAsString())).append("\n");
				}
				if (child.has("items")) {
					markdown.append(nestedListToMarkdown(child, indentLevel + (isOrdered ? 2 : 1)));
				}
			}
		}
		return stripUrls(markdown.toString());
	}

	private static String stripUrls(String text) {
		if (text == null || text.isEmpty()) return "";
		String withoutMarkdownLinks = text.replaceAll("\\[([^\\]]+)\\]\\(https?://[^\\s)]+\\)", "$1 (link removed)");
		return withoutMarkdownLinks.replaceAll("https?://\\S+", "(link removed)");
	}

	private static CompletableFuture<ArchiveConfig> fetchConfigAsync(ServerEntry server) {
		return fetchJsonAsync(server, "config.json").thenApply(json -> GSON.fromJson(json, ArchiveConfig.class));
	}

	private static CompletableFuture<ChannelData> fetchChannelDataAsync(ServerEntry server, String channelPath) {
		String path = normalizePath(channelPath) + "/data.json";
		return fetchJsonAsync(server, path).thenApply(json -> GSON.fromJson(json, ChannelData.class));
	}

	private static CompletableFuture<ArchiveEntryData> fetchEntryDataAsync(ServerEntry server, String channelPath, String entryPath) {
		String path = normalizePath(channelPath) + "/" + normalizePath(entryPath) + "/data.json";
		return fetchJsonAsync(server, path).thenApply(json -> GSON.fromJson(json, ArchiveEntryData.class));
	}

	private static CompletableFuture<String> fetchJsonAsync(ServerEntry server, String path) {
		String url = buildRawUrl(server, path);
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.header("Accept", "application/json")
			.header("User-Agent", USER_AGENT)
			.GET()
			.build();

		return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenApply(response -> {
				if (response.statusCode() != 200) {
					throw new CompletionException(new RuntimeException("HTTP error: " + response.statusCode() + " for " + url));
				}
				return response.body();
			});
	}

	private static String resolveImagePath(ServerEntry server, String path, String channelPath, String entryPath) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		String basePath = normalizePath(channelPath) + "/" + normalizePath(entryPath);
		return buildRawUrl(server, basePath + "/" + path);
	}

	private static String resolveAttachmentPath(ServerEntry server, String path, String channelPath, String entryPath) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		String basePath = normalizePath(channelPath) + "/" + normalizePath(entryPath);
		return buildRawUrl(server, basePath + "/" + path);
	}

	private static String buildRawUrl(ServerEntry server, String path) {
		ServerEntry target = normalizeServer(server);
		String owner = target.owner() != null && !target.owner().isBlank() ? target.owner() : "Storage-Tech-2";
		String repo = target.repo() != null && !target.repo().isBlank() ? target.repo() : "Archive";
		String branch = target.branch() != null && !target.branch().isBlank() ? target.branch() : DEFAULT_BRANCH;
		return RAW_BASE + "/" + owner + "/" + repo + "/" + branch + "/" + normalizePath(path);
	}

	private static String normalizePath(String path) {
		if (path == null) {
			return "";
		}
		return path.startsWith("/") ? path.substring(1) : path;
	}

	private record ArchiveIndexCache(List<ArchivePostSummary> posts, List<ArchiveChannel> channels) {
	}

	private static class ArchiveConfig {
		List<ChannelRef> archiveChannels;
		Map<String, StyleInfo> postStyle;
	}

	private static class ChannelRef {
		String id;
		String name;
		String code;
		String category;
		String path;
		String description;
		List<String> availableTags;
	}

	private static class ChannelData {
		@SuppressWarnings("unused")
		String id;
		@SuppressWarnings("unused")
		String name;
		@SuppressWarnings("unused")
		String code;
		@SuppressWarnings("unused")
		String category;
		@SuppressWarnings("unused")
		String description;
		@SuppressWarnings("unused")
		Integer currentCodeId;
		@SuppressWarnings("unused")
		List<String> availableTags;
		List<EntryRef> entries;
	}

	private static class EntryRef {
		String id;
		String name;
		String code;
		Long archivedAt;
		Long updatedAt;
		String path;
		List<String> tags;
	}

	private static class ArchiveEntryData {
		@SuppressWarnings("unused")
		String id;
		@SuppressWarnings("unused")
		String name;
		@SuppressWarnings("unused")
		String code;
		List<ArchiveAuthor> authors;
		@SuppressWarnings("unused")
		List<ArchiveAuthor> endorsers;
		@SuppressWarnings("unused")
		List<ArchiveTag> tags;
		List<ArchiveImageData> images;
		List<ArchiveAttachmentData> attachments;
		ArchiveDiscordPostReference post;
		JsonObject records;
		Map<String, StyleInfo> styles;
		Long archivedAt;
		Long updatedAt;
	}

	private static class ArchiveImageData {
		@SuppressWarnings("unused")
		String name;
		@SuppressWarnings("unused")
		String url;
		String description;
		@SuppressWarnings("unused")
		String contentType;
		@SuppressWarnings("unused")
		Boolean canDownload;
		String path;
		Integer width;
		Integer height;
	}

	private static class ArchiveAttachmentData {
		@SuppressWarnings("unused")
		String id;
		String name;
		String url;
		String description;
		String contentType;
		ArchiveLitematicInfo litematic;
		ArchiveWdlInfo wdl;
		ArchiveYoutubeInfo youtube;
		Boolean canDownload;
		String path;
	}

	private static class ArchiveLitematicInfo {
		String version;
		String size;
		String error;
	}

	private static class ArchiveWdlInfo {
		String version;
		String error;
	}

	private static class ArchiveYoutubeInfo {
		String title;
		String author_name;
		String author_url;
		@SuppressWarnings("unused")
		String thumbnail_url;
		@SuppressWarnings("unused")
		Integer thumbnail_width;
		@SuppressWarnings("unused")
		Integer thumbnail_height;
		@SuppressWarnings("unused")
		Integer width;
		@SuppressWarnings("unused")
		Integer height;
	}

	private static class ArchiveAuthor {
		String username;
		String displayName;
		@SuppressWarnings("unused")
		String url;
		@SuppressWarnings("unused")
		String iconURL;
	}

	private static class ArchiveTag {
		@SuppressWarnings("unused")
		String id;
		@SuppressWarnings("unused")
		String name;
	}

	private static class ArchiveDiscordPostReference {
		String forumId;
		String threadId;
		List<String> continuingMessageIds;
		String threadURL;
		String attachmentMessageId;
		String uploadMessageId;
	}

	private record ChannelBundle(ChannelRef channel, ChannelData data) {}

	private static class StyleInfo {
		Integer depth;
		String headerText;
		Boolean isOrdered;
	}

	private static class SectionLines {
		final String title;
		final List<String> lines = new ArrayList<>();

		SectionLines(String title) {
			this.title = title != null ? title : "";
		}

		void addLines(String... items) {
			if (items == null) return;
			for (String item : items) {
				if (item == null) continue;
				String trimmed = item.stripTrailing();
				if (!trimmed.isEmpty()) {
					lines.add(trimmed);
				}
			}
		}
	}
}
