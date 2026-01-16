package com.andrews.spdownloader.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.andrews.spdownloader.config.DownloadSettings;
import com.andrews.spdownloader.config.ServerDictionary;
import com.andrews.spdownloader.gui.theme.UITheme;
import com.andrews.spdownloader.gui.widget.AuthorFilterWidget;
import com.andrews.spdownloader.gui.widget.CustomButton;
import com.andrews.spdownloader.gui.widget.CustomTextField;
import com.andrews.spdownloader.gui.widget.LoadingSpinner;
import com.andrews.spdownloader.gui.widget.PostGridWidget;
import com.andrews.spdownloader.gui.widget.ShareProjectsWarningPopup;
import com.andrews.spdownloader.models.ArchiveAttachment;
import com.andrews.spdownloader.models.ShareProjectEntry;
import com.andrews.spdownloader.models.ShareProjectSearchResult;
import com.andrews.spdownloader.network.ShareProjectsNetworkManager;
import com.andrews.spdownloader.util.AttachmentManager;
import com.andrews.spdownloader.util.RenderUtil;

public class LitematicDownloaderScreen extends Screen {
    private static final int SEARCH_BAR_HEIGHT = 20;
    private static final int PADDING = 10;
    private static final int SIDEBAR_WIDTH = 240;
    private static final String WARNING_TITLE = "Use at your own risk";
    private static final String WARNING_BODY = """
Use at your own risk:
This tool indexes devices publicly listed in the Storage Tech Discord's share-projects channel posted before Dec 18 2025. This tool is not officially sanctioned by the Storage Tech Discord.

As anyone can post their devices in share-projects, there is no guarantee that the devices listed by this tool are functional.

This is for learning purposes only!

For a collection of documented designs you can use, visit storagetech2.org.""";

    private CustomTextField searchField;
    private CustomButton authorButton;
    private CustomButton closeButton;
    private LoadingSpinner loadingSpinner;
    private PostGridWidget postGrid;
    private AuthorFilterWidget authorFilterWidget;
    private ShareProjectsWarningPopup warningPopup;

    private boolean showAuthorPanel = false;
    private boolean isLoading = false;
    private boolean isLoadingMore = false;
    private boolean noResultsFound = false;
    private boolean initialized = false;

    private int currentPage = 1;
    private int totalPages = 1;
    private int totalItems = 0;
    private int itemsPerPage = DownloadSettings.getInstance().getItemsPerPage();
    private String currentSearchQuery = "";
    private String selectedAuthor = null;

    private final List<ShareProjectEntry> currentPosts = new ArrayList<>();
    private final Map<String, Integer> authorCounts = new HashMap<>();
    private final AttachmentManager attachmentManager = new AttachmentManager(MinecraftClient.getInstance());

    public LitematicDownloaderScreen() {
        super(Text.of("Share-Projects Browser"));
    }

    @Override
    protected void init() {
        super.init();

        String previousSearchText = (searchField != null) ? searchField.getText() : "";

        int closeButtonSize = 20;
        int authorButtonWidth = 100;
        int headerSpacing = 8;

        loadingSpinner = new LoadingSpinner(this.width / 2 - 16, this.height / 2 - 16);

        int rightReserve = PADDING + closeButtonSize;
        int searchBarWidth = Math.max(160, this.width - rightReserve - authorButtonWidth - headerSpacing - PADDING * 2);

        searchField = new CustomTextField(
            this.client,
            PADDING,
            PADDING,
            searchBarWidth,
            SEARCH_BAR_HEIGHT,
            Text.of("Search")
        );
        searchField.setPlaceholder(Text.of("Search schematics or authors"));
        searchField.setOnEnterPressed(this::performSearch);
        searchField.setOnClearPressed(this::performSearch);
        searchField.setOnChanged(() -> {
            currentPage = 1;
            performSearch();
        });
        if (!previousSearchText.isEmpty()) {
            searchField.setText(previousSearchText);
        }

        authorButton = new CustomButton(
            PADDING + searchBarWidth + headerSpacing,
            PADDING,
            authorButtonWidth,
            SEARCH_BAR_HEIGHT,
            Text.of(getAuthorButtonLabel()),
            button -> {
                showAuthorPanel = !showAuthorPanel;
            }
        );

        closeButton = new CustomButton(
            this.width - PADDING - closeButtonSize,
            PADDING,
            closeButtonSize,
            closeButtonSize,
            Text.of("X"),
            button -> this.close()
        );
        closeButton.setRenderAsXIcon(true);

        int gridY = PADDING * 2 + SEARCH_BAR_HEIGHT;
        int gridHeight = this.height - gridY - PADDING;
        int gridWidth = this.width - PADDING * 2;

        if (postGrid == null) {
            postGrid = new PostGridWidget(PADDING, gridY, gridWidth, gridHeight, this::onPostClick);
            postGrid.setOnEndReached(this::loadNextPage);
        } else {
            postGrid.setDimensions(PADDING, gridY, gridWidth, gridHeight);
            postGrid.setOnEndReached(this::loadNextPage);
        }

        if (authorFilterWidget == null) {
            authorFilterWidget = new AuthorFilterWidget(PADDING, gridY, SIDEBAR_WIDTH, gridHeight);
            authorFilterWidget.setOnSelectionChanged(this::onAuthorSelected);
        } else {
            authorFilterWidget.setBounds(PADDING, gridY, SIDEBAR_WIDTH, gridHeight);
            authorFilterWidget.setOnSelectionChanged(this::onAuthorSelected);
        }

        postGrid.setExpectedTotalPosts(totalItems);
        postGrid.setBlocked(showAuthorPanel || warningPopup != null);

        if (!initialized) {
            initialized = true;
            maybeShowWarning();
            performSearch();
        }
    }

    private void maybeShowWarning() {
        if (DownloadSettings.getInstance().hasAcknowledgedShareProjectsWarning()) {
            return;
        }
        warningPopup = new ShareProjectsWarningPopup(
            WARNING_TITLE,
            WARNING_BODY,
            () -> {},
            () -> {
                DownloadSettings.getInstance().setAcknowledgedShareProjectsWarning(true);
                warningPopup = null;
            }
        );
    }

    private void performSearch() {
        if (isLoading) return;

        currentSearchQuery = searchField != null ? searchField.getText().trim() : "";
        currentPage = 1;
        currentPosts.clear();
        noResultsFound = false;

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
        }

        ShareProjectsNetworkManager.search(currentSearchQuery, selectedAuthor, currentPage, itemsPerPage)
            .thenAccept(this::handleSearchResponse)
            .exceptionally(throwable -> {
                if (this.client != null) {
                    this.client.execute(() -> {
                        isLoading = false;
                        isLoadingMore = false;
                        System.err.println("Search failed: " + throwable.getMessage());
                    });
                }
                return null;
            });
    }

    private void handleSearchResponse(ShareProjectSearchResult response) {
        if (this.client != null) {
            this.client.execute(() -> {
                if (response == null) {
                    isLoading = false;
                    isLoadingMore = false;
                    return;
                }

                totalPages = response.totalPages();
                totalItems = response.totalItems();
                authorCounts.clear();
                authorCounts.putAll(response.authorCounts());
                authorFilterWidget.setAuthors(authorCounts);
                if (selectedAuthor != null) {
                    authorFilterWidget.setSelectedAuthor(selectedAuthor);
                }

                List<ShareProjectEntry> posts = response.posts();
                if (posts != null) {
                    if (isLoadingMore) {
                        currentPosts.addAll(posts);
                        postGrid.appendPosts(posts);
                    } else {
                        currentPosts.clear();
                        currentPosts.addAll(posts);
                        postGrid.resetPosts(new ArrayList<>(currentPosts));
                    }
                    postGrid.setExpectedTotalPosts(totalItems);
                } else if (!isLoadingMore) {
                    postGrid.resetPosts(new ArrayList<>());
                    postGrid.setExpectedTotalPosts(totalItems);
                }

                isLoading = false;
                isLoadingMore = false;
                noResultsFound = (totalItems == 0);
                if (authorButton != null) {
                    authorButton.setMessage(Text.of(getAuthorButtonLabel()));
                }
            });
        }
    }

    private void loadNextPage() {
        if (isLoadingMore || isLoading) return;
        if (currentPage >= totalPages) return;
        currentPage++;
        loadPage(true);
    }

    private void onPostClick(ShareProjectEntry post) {
        if (post == null) return;
        if (showAuthorPanel) {
            showAuthorPanel = false;
        }
        downloadSchematic(post);
    }

    private void downloadSchematic(ShareProjectEntry entry) {
        ArchiveAttachment attachment = new ArchiveAttachment(
            entry.displayName(),
            entry.downloadUrl(),
            "application/x-litematic",
            true,
            formatSize(entry.fileSizeBytes()),
            null,
            new ArchiveAttachment.LitematicInfo(entry.version(), entry.sizeText(), null),
            null,
            null
        );
        attachmentManager.setAvailableFiles(List.of(attachment));
        attachmentManager.setServer(ServerDictionary.getDefaultServer());
        attachmentManager.handleAttachmentClick(attachment);
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "";
        double b = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int idx = 0;
        while (b >= 1024 && idx < units.length - 1) {
            b /= 1024;
            idx++;
        }
        return String.format(java.util.Locale.ROOT, "%.1f %s", b, units[idx]);
    }

    private void onAuthorSelected(String author) {
        selectedAuthor = author;
        if (authorButton != null) {
            authorButton.setMessage(Text.of(getAuthorButtonLabel()));
        }
        showAuthorPanel = false;
        currentPage = 1;
        performSearch();
    }

    private String getAuthorButtonLabel() {
        return selectedAuthor == null || selectedAuthor.isBlank() ? "Authors" : selectedAuthor;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        if (postGrid != null) {
            postGrid.setBlocked(showAuthorPanel || warningPopup != null);
            postGrid.render(context, mouseX, mouseY, delta);
        }

        if (isLoading) {
            loadingSpinner.render(context, mouseX, mouseY, delta);
        }

        if (noResultsFound) {
            String noResultsText = "No schematics found";
            RenderUtil.drawString(
                context,
                this.textRenderer,
                noResultsText,
                PADDING + 20,
                this.height / 2 + 10,
                0xFFFFFFFF
            );
        }

        if (searchField != null) {
            searchField.render(context, mouseX, mouseY, delta);
        }

        if (authorButton != null) {
            authorButton.render(context, mouseX, mouseY, delta);
        }

        if (closeButton != null) {
            closeButton.render(context, mouseX, mouseY, delta);
        }

        if (showAuthorPanel && authorFilterWidget != null) {
            RenderUtil.fillRect(context, 0, 0, this.width, this.height, 0x55000000);
            authorFilterWidget.render(context, mouseX, mouseY, delta);
        }

        String status = attachmentManager.getDownloadStatus();
        if (status != null && !status.isEmpty()) {
            RenderUtil.drawScaledString(context, status, PADDING, this.height - PADDING - UITheme.Typography.LINE_HEIGHT, UITheme.Colors.TEXT_PRIMARY, 0.9f);
        }

        if (warningPopup != null) {
            warningPopup.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (warningPopup != null) {
            return warningPopup.mouseClicked(mouseX, mouseY, button);
        }

        if (showAuthorPanel) {
            if (authorFilterWidget != null && authorFilterWidget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            showAuthorPanel = false;
            return true;
        }

        if (button == 0 && authorButton != null && isMouseOverButton(authorButton, mouseX, mouseY)) {
            if (authorButton.active && this.client != null) {
                authorButton.playDownSound(this.client.getSoundManager());
            }
            showAuthorPanel = !showAuthorPanel;
            return true;
        }

        if (button == 0 && isMouseOverButton(closeButton, mouseX, mouseY)) {
            this.close();
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

        if (postGrid != null && postGrid.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (warningPopup != null) {
            return warningPopup.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (showAuthorPanel && authorFilterWidget != null) {
            return authorFilterWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (postGrid != null && postGrid.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (warningPopup != null) {
            warningPopup.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        if (showAuthorPanel && authorFilterWidget != null) {
            authorFilterWidget.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        if (postGrid != null && postGrid.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (warningPopup != null) {
            return warningPopup.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        if (showAuthorPanel && authorFilterWidget != null) {
            if (authorFilterWidget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
            return true;
        }
        if (postGrid != null && postGrid.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        if (warningPopup != null) {
            return true;
        }
        if (showAuthorPanel) {
            showAuthorPanel = false;
            return false;
        }
        return super.shouldCloseOnEsc();
    }

    @Override
    public void close() {
        warningPopup = null;
        ShareProjectsNetworkManager.clearCache();
        super.close();
    }

    private boolean isMouseOverButton(CustomButton button, double mouseX, double mouseY) {
        return button != null &&
               mouseX >= button.getX() &&
               mouseX < button.getX() + button.getWidth() &&
               mouseY >= button.getY() &&
               mouseY < button.getY() + button.getHeight();
    }
}
