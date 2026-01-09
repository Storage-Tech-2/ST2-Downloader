package com.andrews.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.andrews.config.DownloadSettings;
import com.andrews.gui.theme.UITheme;
import com.andrews.gui.widget.ChannelDescriptionWidget;
import com.andrews.gui.widget.ChannelFilterPanel;
import com.andrews.gui.widget.CustomButton;
import com.andrews.gui.widget.CustomTextField;
import com.andrews.gui.widget.DiscordJoinPopup;
import com.andrews.gui.widget.LoadingSpinner;
import com.andrews.gui.widget.PostDetailPanel;
import com.andrews.gui.widget.PostGridWidget;
import com.andrews.gui.widget.TagFilterWidget;
import com.andrews.models.ArchiveChannel;
import com.andrews.models.ArchivePostSummary;
import com.andrews.models.ArchiveSearchResult;
import com.andrews.network.ArchiveNetworkManager;

public class LitematicDownloaderScreen extends Screen {
    private static final int SEARCH_BAR_HEIGHT = 20;
    private static final int PADDING = 10;
    private static final int SIDEBAR_WIDTH = 200;
    private static final String DISCORD_INVITE_URL = "https://discord.gg/hztJMTsx2m";
    private static final String SUBMISSIONS_URL = "https://discord.com/channels/1375556143186837695/1375575317007040654";

    private CustomTextField searchField;
    private PostGridWidget postGrid;
    private PostDetailPanel detailPanel;
    private ChannelFilterPanel channelPanel;
    private ChannelDescriptionWidget channelDescriptionWidget;
    private TagFilterWidget tagFilterWidget;
    private CustomButton channelToggleButton;
    private CustomButton closeButton;
    private CustomButton submissionsButton;
    private CustomButton detailCloseButton;
    private LoadingSpinner loadingSpinner;
    private DiscordJoinPopup discordPopup;
    private String pendingDiscordUrl;

    private int currentPage = 1;
    private int totalPages = 1;
    private int totalItems = 0;
    private int itemsPerPage = 20;
    private boolean isLoading = false;
    private boolean isLoadingMore = false;
    private String currentSearchQuery = "";
    private String currentTagFilter = "";
    private String selectedSort = "newest";
    private String selectedChannelPath = null;
    private boolean noResultsFound = false;
    private boolean initialized = false;
    private List<ArchiveChannel> channels = new ArrayList<>();
    private List<ArchivePostSummary> currentPosts = new ArrayList<>();
    private boolean showDetailOverlay = false;
    private boolean showChannelPanel = false;
    private ArchiveChannel hoveredChannel = null;
    private enum TagState { INCLUDE, EXCLUDE }
    private final Map<String, TagState> tagStates = new HashMap<>();
    private final Map<String, Integer> tagCounts = new HashMap<>();
    private final Map<String, Integer> channelCounts = new HashMap<>();

    public LitematicDownloaderScreen() {
        super(Component.nullToEmpty("Litematic Downloader"));
    }

    @Override
    protected void init() {
        super.init();

        String previousSearchText = (searchField != null) ? searchField.getValue() : "";

        int headerSpacing = 8;
        int closeButtonSize = 20;
        int submissionsWidth = 50;

        loadingSpinner = new LoadingSpinner(this.width / 2 - 16, this.height / 2 - 16);

        if (this.minecraft != null) {
            int startX = PADDING + 110 + headerSpacing; // toggle width + spacing
            int rightReserve = PADDING + closeButtonSize + headerSpacing + submissionsWidth + headerSpacing;
            int availableWidth = Math.max(60, this.width - startX - rightReserve);
            int searchBarWidth = Math.max(120, availableWidth);

            searchField = new CustomTextField(
                this.minecraft,
                startX,
                PADDING,
                searchBarWidth,
                SEARCH_BAR_HEIGHT,
                Component.nullToEmpty("Search")
            );
            searchField.setHint(Component.nullToEmpty("Search posts, codes, tags"));
            searchField.setOnEnterPressed(this::performSearch);
            searchField.setOnClearPressed(this::performSearch);
            searchField.setOnChanged(() -> {
                currentPage = 1;
                performSearch();
            });
            if (!previousSearchText.isEmpty()) {
                searchField.setValue(previousSearchText);
            }

            submissionsButton = new CustomButton(
                startX + searchBarWidth + headerSpacing,
                PADDING,
                submissionsWidth,
                SEARCH_BAR_HEIGHT,
                Component.nullToEmpty("Submit"),
                button -> requestDiscordLink(SUBMISSIONS_URL)
            );
        }

        channelToggleButton = new CustomButton(
            PADDING,
            PADDING,
            110,
            SEARCH_BAR_HEIGHT,
            Component.nullToEmpty(showChannelPanel ? "Hide Channels" : "Show Channels"),
            button -> {
                showChannelPanel = !showChannelPanel;
                this.init(this.width, this.height);
            }
        );
        int gridY = PADDING + PADDING/2 + SEARCH_BAR_HEIGHT;
        int gridHeight = this.height - gridY - PADDING;
        int gridWidth = this.width - PADDING;

        if (postGrid == null) {
            postGrid = new PostGridWidget(PADDING / 2, gridY, gridWidth, gridHeight, this::onPostClick);
            postGrid.setOnEndReached(this::loadNextPage);
        } else {
            postGrid.setDimensions(PADDING / 2, gridY, gridWidth, gridHeight);
            postGrid.setOnEndReached(this::loadNextPage);
        }

        if (detailPanel == null) {
            detailPanel = new PostDetailPanel(PADDING, gridY, gridWidth, gridHeight);
            detailPanel.setDiscordLinkOpener(this::requestDiscordLink);
        } else {
            detailPanel.setDimensions(PADDING, gridY, gridWidth, gridHeight);
            detailPanel.setDiscordLinkOpener(this::requestDiscordLink);
        }

        int detailCloseSize = 20;
        if (detailCloseButton == null) {
            detailCloseButton = new CustomButton(
                this.width - PADDING - detailCloseSize,
                PADDING,
                detailCloseSize,
                detailCloseSize,
                Component.nullToEmpty("X"),
                button -> {
                    showDetailOverlay = false;
                    if (detailPanel != null) {
                        detailPanel.closeDropdown();
                    }
                }
            );
            detailCloseButton.setRenderAsXIcon(true);
        } else {
            detailCloseButton.setX(this.width - PADDING - detailCloseSize);
            detailCloseButton.setY(PADDING);
        }

        if (showChannelPanel) {
            int channelHeight = this.height - (PADDING * 3 + SEARCH_BAR_HEIGHT);
            if (channelPanel == null) {
                channelPanel = new ChannelFilterPanel(PADDING, PADDING * 2 + SEARCH_BAR_HEIGHT, SIDEBAR_WIDTH - PADDING, channelHeight);
                channelPanel.setOnSelectionChanged(path -> {
                    selectedChannelPath = path;
                    currentPage = 1;
                    resetTagStatesForChannel(path);
                    performSearch();
                });
                channelPanel.setOnHoverChanged(channel -> hoveredChannel = channel);
                channelPanel.setChannels(channels);
                channelPanel.setChannelCounts(channelCounts);
            } else {
                channelPanel.setDimensions(PADDING, PADDING * 2 + SEARCH_BAR_HEIGHT, SIDEBAR_WIDTH - PADDING, channelHeight);
                channelPanel.setChannelCounts(channelCounts);
            }

            if (channelDescriptionWidget == null) {
                channelDescriptionWidget = new ChannelDescriptionWidget();
            }
            if (tagFilterWidget == null) {
                tagFilterWidget = new TagFilterWidget();
                tagFilterWidget.setOnToggle((tag, state) -> {
                    String key = tag != null ? tag.toLowerCase() : "";
                    if (key.isEmpty()) return;
                    if (state == null) {
                        tagStates.remove(key);
                    } else {
                        tagStates.put(key, state == TagFilterWidget.TagState.INCLUDE ? TagState.INCLUDE : TagState.EXCLUDE);
                    }
                    currentPage = 1;
                    performSearch();
                });
            }
        }

        closeButton = new CustomButton(
            this.width - PADDING - closeButtonSize,
            PADDING,
            closeButtonSize,
            closeButtonSize,
            Component.nullToEmpty("X"),
            button -> this.onClose()
        );
        closeButton.setRenderAsXIcon(true);
        if (!initialized) {
            initialized = true;
            loadChannels();
            performSearch();
        } else {
            updatePaginationButtons();
        }
    }

    private boolean isMouseOverButton(CustomButton button, double mouseX, double mouseY) {
        return button != null &&
               mouseX >= button.getX() &&
               mouseX < button.getX() + button.getWidth() &&
               mouseY >= button.getY() &&
               mouseY < button.getY() + button.getHeight();
    }

    private void performSearch() {
        if (isLoading) return;

        currentSearchQuery = searchField != null ? searchField.getValue().trim() : "";
        currentTagFilter = "";
        currentPage = 1;
        currentPosts.clear();

        if (detailPanel != null) {
            detailPanel.clear();
        }

        loadPage(false);
    }

    private void loadPage(boolean append) {
        if (isLoading || isLoadingMore) {
            return;
        }

        if (append) {
            isLoadingMore = true;
        } else {
            isLoading = true;
            currentPosts.clear();
            if (postGrid != null) {
                postGrid.resetPosts(new ArrayList<>());
            }
        }

        List<String> channelFilter = selectedChannelPath != null ? List.of(selectedChannelPath) : null;
        List<String> includeTags = getTagList(TagState.INCLUDE);
        List<String> excludeTags = getTagList(TagState.EXCLUDE);

        ArchiveNetworkManager.searchPosts(currentSearchQuery, selectedSort, currentTagFilter, includeTags, excludeTags, channelFilter, currentPage, itemsPerPage)
            .thenAccept(this::handleSearchResponse)
            .exceptionally(throwable -> {
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        isLoading = false;
                        isLoadingMore = false;
                        updatePaginationButtons();

                        String errorMessage = throwable.getMessage();
                        String userMessage;

                        if (errorMessage != null) {
                            if (errorMessage.contains("UnknownHostException") ||
                                errorMessage.contains("ConnectException") ||
                                errorMessage.contains("SocketTimeoutException") ||
                                errorMessage.contains("NoRouteToHostException")) {
                                userMessage = "Network error: No internet connection";
                            } else if (errorMessage.contains("HTTP error")) {
                                userMessage = "Server error: " + errorMessage;
                            } else {
                                userMessage = "Search failed: " + errorMessage;
                            }
                        } else {
                            userMessage = "Search failed: Unknown error";
                        }

                        System.err.println("Error loading posts: " + errorMessage);
                    });
                }
                return null;
            });
    }

    private void handleSearchResponse(ArchiveSearchResult response) {
        if (this.minecraft != null) {
            this.minecraft.execute(() -> {
                totalPages = response.totalPages();
                totalItems = response.totalItems();
                channelCounts.clear();
                if (response.channelCounts() != null) {
                    channelCounts.putAll(response.channelCounts());
                }

                List<ArchivePostSummary> posts = response.posts();
                if (posts != null) {
                    if (isLoadingMore) {
                        currentPosts.addAll(posts);
                        if (postGrid != null) {
                            postGrid.appendPosts(posts);
                            postGrid.setExpectedTotalPosts(totalItems);
                        }
                    } else {
                        currentPosts.clear();
                        currentPosts.addAll(posts);
                        if (postGrid != null) {
                            postGrid.resetPosts(new ArrayList<>(currentPosts));
                            postGrid.setExpectedTotalPosts(totalItems);
                        }
                    }
                } else if (!isLoadingMore && postGrid != null) {
                    postGrid.resetPosts(new ArrayList<>());
                    postGrid.setExpectedTotalPosts(totalItems);
                }
                if (channelPanel != null) {
                    channelPanel.setChannelCounts(channelCounts);
                }
                updateTagCounts();

                isLoading = false;
                isLoadingMore = false;
                updatePaginationButtons();

                noResultsFound = (totalItems == 0);
            });
        }
    }

    private void updatePaginationButtons() {
        // no-op with infinite scroll
    }

    private void loadNextPage() {
        if (isLoadingMore || isLoading) return;
        if (currentPage >= totalPages) return;
        currentPage++;
        loadPage(true);
    }

    private void loadChannels() {
        ArchiveNetworkManager.getChannels()
            .thenAccept(list -> {
                if (this.minecraft != null) {
                    this.minecraft.execute(() -> {
                        channels = list != null ? list.stream()
                            .sorted(Comparator.comparing(ArchiveChannel::category).thenComparing(ArchiveChannel::name))
                            .collect(Collectors.toList()) : new ArrayList<>();
                        for (ArchiveChannel channel : channels) {
                            if (channel != null && channel.path() != null) {
                                channelCounts.putIfAbsent(channel.path(), channel.entryCount());
                            }
                        }
                        if (channelPanel != null) {
                            channelPanel.setChannels(channels);
                            channelPanel.setChannelCounts(channelCounts);
                        }
                    });
                }
            })
            .exceptionally(throwable -> {
                System.err.println("Failed to load channels: " + throwable.getMessage());
                return null;
            });
    }

    private void onPostClick(ArchivePostSummary post) {
        if (detailPanel != null && post != null) {
            detailPanel.setDimensions(PADDING, PADDING, this.width - PADDING * 2, this.height - PADDING * 2);
            detailPanel.setPost(post);
            showDetailOverlay = true;
        }
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int leftPanelWidth = showChannelPanel ? SIDEBAR_WIDTH : 0;
        super.render(context, mouseX, mouseY, delta);

        if (postGrid != null) {
            postGrid.setBlocked(showChannelPanel);
            postGrid.render(context, mouseX, mouseY, delta);
        }

        if (isLoading) {
            loadingSpinner.render(context, mouseX, mouseY, delta);
        }

        if (noResultsFound) {
            String noResultsText = "No results found :(";
            context.drawString(
                this.font,
                noResultsText,
                leftPanelWidth + PADDING + 20,
                this.height / 2 + 10,
                0xFFFFFFFF
            );
        }

        if (showChannelPanel && channelPanel != null) {
            context.fill(0, 0, this.width, this.height, 0x55000000);
            channelPanel.render(context, mouseX, mouseY, delta);
            renderChannelDescription(context, mouseX, mouseY, delta);
            
        }

        // Header controls rendered last so they remain visible and bright even when overlay dimming is active
        if (channelToggleButton != null) {
            channelToggleButton.render(context, mouseX, mouseY, delta);
        }

        if (submissionsButton != null) {
            submissionsButton.render(context, mouseX, mouseY, delta);
        }

        if (closeButton != null) {
            closeButton.render(context, mouseX, mouseY, delta);
        }

        if (searchField != null) {
            searchField.render(context, mouseX, mouseY, delta);
        }

        if (showDetailOverlay && detailPanel != null) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            detailPanel.render(context, mouseX, mouseY, delta);
            if (detailCloseButton != null) {
                detailCloseButton.render(context, mouseX, mouseY, delta);
            }
        }

        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            detailPanel.renderImageViewer(context, mouseX, mouseY, delta);
        }

        if (discordPopup != null) {
            discordPopup.render(context, mouseX, mouseY, delta);
        }
    }


    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        boolean channelOverlayOpen = showChannelPanel && channelPanel != null;

        if (discordPopup != null) {
            return discordPopup.mouseClicked(mouseX, mouseY, button);
        }

        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            return detailPanel.mouseClicked(mouseX, mouseY, button);
        }

        if (showDetailOverlay && button == 0 && detailCloseButton != null && isMouseOverButton(detailCloseButton, mouseX, mouseY)) {
            if (this.minecraft != null) {
                detailCloseButton.playDownSound(this.minecraft.getSoundManager());
            }
            showDetailOverlay = false;
            return true;
        }

        if (showDetailOverlay && detailPanel != null) {
            if (detailPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            showDetailOverlay = false;
            return true;
        }

        if (button == 0 && channelToggleButton != null && isMouseOverButton(channelToggleButton, mouseX, mouseY)) {
            if (channelToggleButton.active) {
                if (this.minecraft != null) {
                    channelToggleButton.playDownSound(this.minecraft.getSoundManager());
                }
                channelToggleButton.onPress(click);
            }
            return true;
        }

        if (button == 0 && isMouseOverButton(closeButton, mouseX, mouseY)) {
            this.onClose();
            return true;
        }

        if (button == 0 && isMouseOverButton(submissionsButton, mouseX, mouseY)) {
            requestDiscordLink(SUBMISSIONS_URL);
            return true;
        }

        if (button == 0 && searchField != null) {
            if (searchField.isMouseOver(mouseX, mouseY)) {
                searchField.setFocused(true);
                return true;
            } else {
                searchField.setFocused(false);
            }
        }

        // tagField removed

        if (channelOverlayOpen) {
            if (channelPanel != null && channelPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            if (tagFilterWidget != null && tagFilterWidget.handleClick(mouseX, mouseY)) {
                return true;
            }
            showChannelPanel = false;
            return true;
        }

        if (postGrid != null && postGrid.mouseClicked(click, doubled)) {
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (discordPopup != null) {
            return true;
        }
        if (showDetailOverlay && detailPanel != null && detailPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        boolean channelOverlayOpen = showChannelPanel && channelPanel != null;
        if (channelPanel != null && channelPanel.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        if (channelOverlayOpen) {
            return true;
        }
        if (postGrid != null && postGrid.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (discordPopup != null) {
            return true;
        }
        if (showDetailOverlay && detailPanel != null && detailPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        boolean channelOverlayOpen = showChannelPanel && channelPanel != null;
        if (channelPanel != null && channelPanel.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (channelOverlayOpen) {
            return true;
        }
        if (postGrid != null && postGrid.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (discordPopup != null) {
            if (discordPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
            return true;
        }
        if (showChannelPanel && channelPanel != null) {
            if (channelPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
            if (tagFilterWidget != null && tagFilterWidget.handleScroll(mouseX, mouseY, verticalAmount)) {
                return true;
            }
            return true;
        }
        if (showDetailOverlay && detailPanel != null && detailPanel.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        if (postGrid != null && postGrid.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (discordPopup != null) {
            clearDiscordPopup();
            return false;
        }
        if (detailPanel != null && detailPanel.hasImageViewerOpen()) {
            detailPanel.keyPressed(256, 0, 0); // 256 = GLFW_KEY_ESCAPE
            return false;
        }
        if (showDetailOverlay) {
            showDetailOverlay = false;
            return false;
        }
        return super.shouldCloseOnEsc();
    }

    @Override
    public void onClose() {
        clearDiscordPopup();
        ArchiveNetworkManager.clearCache();
        super.onClose();
    }

    private void renderChannelDescription(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (channelPanel == null) return;
        ArchiveChannel channel = hoveredChannel != null ? hoveredChannel : channels.stream()
            .filter(c -> selectedChannelPath != null && selectedChannelPath.equals(c.path()))
            .findFirst()
            .orElse(null);

        int desiredWidth = 260;
        int available = this.width - (channelPanel != null ? channelPanel.getX() + channelPanel.getWidth() + PADDING * 3 : PADDING * 2);
        int boxWidth = Math.min(desiredWidth, available);
        int boxX = channelPanel != null ? channelPanel.getX() + channelPanel.getWidth() + PADDING * 2 : this.width - boxWidth - PADDING;
        if (boxX + boxWidth > this.width - PADDING) {
            boxX = Math.max(PADDING, this.width - boxWidth - PADDING);
        }
        int boxHeight = 60;
        int boxY = PADDING + SEARCH_BAR_HEIGHT + PADDING;

        if (channelDescriptionWidget != null) {
            channelDescriptionWidget.setBounds(boxX, boxY, boxWidth, boxHeight);
            channelDescriptionWidget.setChannel(channel);
            channelDescriptionWidget.render(context, this.font);
        }

        int tagY = boxY + boxHeight + UITheme.Dimensions.PADDING;
        int tagHeight = this.height - tagY - PADDING;
        if (tagFilterWidget != null) {
            tagFilterWidget.setBounds(boxX, tagY, boxWidth, tagHeight);
            tagFilterWidget.setData(getDisplayedTags(), tagCounts, convertTagStates());
            long windowHandle = this.minecraft != null ? this.minecraft.getWindow().handle() : 0L;
            tagFilterWidget.render(context, this.font, mouseX, mouseY, delta, windowHandle);
        }
    }

    // Tag rendering handled by TagFilterWidget; this method kept for compatibility.

    private List<String> getDisplayedTags() {
        if (selectedChannelPath != null) {
            List<String> tags = channels.stream()
                .filter(c -> selectedChannelPath.equals(c.path()))
                .findFirst()
                .map(c -> c.availableTags() != null ? c.availableTags() : List.<String>of())
                .orElse(List.of());
            return orderTags(tags);
        }
        return orderTags(List.of("Untested", "Broken", "Tested & Functional", "Recommended"));
    }

    private List<String> getTagList(TagState state) {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, TagState> entry : tagStates.entrySet()) {
            if (entry.getValue() == state) {
                list.add(entry.getKey());
            }
        }
        return list;
    }

    private void resetTagStatesForChannel(String path) {
        tagStates.clear();
        updateTagCounts();
    }

    private void updateTagCounts() {
        tagCounts.clear();
        for (ArchivePostSummary post : currentPosts) {
            if (post == null || post.tags() == null) continue;
            for (String tag : post.tags()) {
                if (tag == null) continue;
                String key = tag.toLowerCase();
                tagCounts.put(key, tagCounts.getOrDefault(key, 0) + 1);
            }
        }
        for (String tag : getDisplayedTags()) {
            tagCounts.putIfAbsent(tag.toLowerCase(), 0);
        }
        if (tagFilterWidget != null) {
            tagFilterWidget.setData(getDisplayedTags(), tagCounts, convertTagStates());
        }
    }

    private List<String> orderTags(List<String> tags) {
        if (tags == null) return List.of();
        List<String> specials = List.of("untested", "broken", "tested & functional", "recommended");
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String s : specials) {
            for (String tag : tags) {
                if (tag == null) continue;
                if (tag.toLowerCase().equals(s) && seen.add(tag.toLowerCase())) {
                    ordered.add(tag);
                }
            }
        }
        for (String tag : tags) {
            if (tag == null) continue;
            String key = tag.toLowerCase();
            if (!seen.contains(key)) {
                ordered.add(tag);
                seen.add(key);
            }
        }
        return ordered;
    }

    private Map<String, TagFilterWidget.TagState> convertTagStates() {
        Map<String, TagFilterWidget.TagState> map = new HashMap<>();
        for (Map.Entry<String, TagState> entry : tagStates.entrySet()) {
            map.put(entry.getKey(), entry.getValue() == TagState.INCLUDE
                ? TagFilterWidget.TagState.INCLUDE
                : TagFilterWidget.TagState.EXCLUDE);
        }
        return map;
    }

    private void requestDiscordLink(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (DownloadSettings.getInstance().hasJoinedDiscord()) {
            openUrlSafe(url);
            return;
        }

        pendingDiscordUrl = url;
        String message = "These links live in the Storage Tech 2 Discord. Please join before continuing.";
        discordPopup = new DiscordJoinPopup(
            "Join Storage Tech 2 Discord?",
            message,
            () -> {
                DownloadSettings.getInstance().setJoinedDiscord(true);
                openUrlSafe(pendingDiscordUrl);
                clearDiscordPopup();
            },
            () -> openUrlSafe(DISCORD_INVITE_URL),
            this::clearDiscordPopup
        );
    }

    private void openUrlSafe(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            Util.getPlatform().openUri(url);
        } catch (Exception e) {
            System.err.println("Failed to open link: " + e.getMessage());
        }
    }

    private void clearDiscordPopup() {
        discordPopup = null;
        pendingDiscordUrl = null;
    }
}
