package com.andrews.gui.widget;

import com.andrews.gui.theme.UITheme;
import com.andrews.models.ArchiveAttachment;
import com.andrews.models.ArchiveImageInfo;
import com.andrews.models.ArchivePostDetail;
import com.andrews.models.ArchivePostSummary;
import com.andrews.models.ArchiveRecordSection;
import com.andrews.network.ArchiveNetworkManager;
import com.andrews.util.AttachmentSaver;
import com.andrews.util.LitematicaAutoLoader;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;


public class PostDetailPanel implements Drawable, Element {
    
    
    
    private static final int TAG_BG_COLOR = UITheme.Colors.BUTTON_BG;
    
    private static final int MAX_IMAGE_SIZE = 120;
    

    private int x;
    private int y;
    private int width;
    private int height;

    private ArchivePostSummary postInfo;
    private ArchivePostDetail postDetail;
    private List<ArchiveImageInfo> imageInfos = new ArrayList<>();
    private String currentImageDescription = "";
    private boolean isLoadingDetails = false;
    private boolean isLoadingImage = false;

    private String[] imageUrls;
    private int currentImageIndex = 0;
    private Identifier currentImageTexture;
    private final Map<String, Identifier> imageCache = new ConcurrentHashMap<>();
    private final Set<String> preloadingImages = ConcurrentHashMap.newKeySet();
    private String loadingImageUrl = null;
    private int originalImageWidth = 0;
    private int originalImageHeight = 0;
    private final Map<String, int[]> imageDimensionsCache = new ConcurrentHashMap<>();

    private final MinecraftClient client;
    private double scrollOffset = 0;
    private int contentHeight = 0;

    private final LoadingSpinner imageLoadingSpinner;

    private CustomButton prevImageButton;
    private CustomButton nextImageButton;
    private CustomButton discordThreadButton;
    private CustomButton websiteButton;

    private ScrollBar scrollBar;

    private List<ArchiveAttachment> availableFiles;
    private String downloadStatus = "";
    private final List<AttachmentHitbox> attachmentHitboxes = new ArrayList<>();

    private ImageViewerWidget imageViewer;
    private Consumer<String> discordLinkOpener;

    public PostDetailPanel(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.client = MinecraftClient.getInstance();
        this.imageLoadingSpinner = new LoadingSpinner(0, 0);
        int scrollBarYOffset = 30;
        this.scrollBar = new ScrollBar(x + width - 8, y + scrollBarYOffset, height - scrollBarYOffset);
    }

    public void setDimensions(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        int scrollBarYOffset = 30;
        this.scrollBar = new ScrollBar(x + width - 8, y + scrollBarYOffset, height - scrollBarYOffset);
    }

    public void setDiscordLinkOpener(Consumer<String> opener) {
        this.discordLinkOpener = opener;
    }

    private int getDisplayImageWidth() {
        return width - 10;
    }

    private int getDisplayImageHeight() {
        return Math.min(MAX_IMAGE_SIZE, height / 2);
    }

    private int getActualImageWidth() {
        if (originalImageWidth <= 0 || originalImageHeight <= 0) {
            return getDisplayImageWidth();
        }

        int containerWidth = getDisplayImageWidth();
        int containerHeight = getDisplayImageHeight();

        int widthAtContainerHeight = (int) ((float) originalImageWidth / originalImageHeight * containerHeight);

        if (widthAtContainerHeight <= containerWidth) {
            return Math.min(originalImageWidth, widthAtContainerHeight);
        } else {
            return Math.min(originalImageWidth, containerWidth);
        }
    }

    private int getActualImageHeight() {
        if (originalImageWidth <= 0 || originalImageHeight <= 0) {
            return getDisplayImageHeight();
        }

        int containerHeight = getDisplayImageHeight();

        int actualWidth = getActualImageWidth();
        int calculatedHeight = (int) ((float) originalImageHeight / originalImageWidth * actualWidth);

        return Math.min(originalImageHeight, Math.min(containerHeight, calculatedHeight));
    }

    private boolean isCompactMode() {
        return width < 200;
    }

    private void updateCarouselButtons(int imageNavY) {
        if (imageUrls == null || imageUrls.length <= 1) {
            prevImageButton = null;
            nextImageButton = null;
            return;
        }

        boolean compact = isCompactMode();
        int btnWidth = compact ? 18 : 25;
        int btnHeight = compact ? 14 : 16;
        int btnSpacing = compact ? 5 : 10;

        String indicator = String.format("%d / %d", currentImageIndex + 1, imageUrls.length);
        int indicatorWidth = client.textRenderer.getWidth(indicator);
        int indicatorX = x + (width - indicatorWidth) / 2;

        int prevBtnX = indicatorX - btnWidth - btnSpacing;
        int nextBtnX = indicatorX + indicatorWidth + btnSpacing;

        if (prevImageButton == null) {
            prevImageButton = new CustomButton(prevBtnX, imageNavY, btnWidth, btnHeight,
                    Text.of("<"), btn -> previousImage());
        } else {
            prevImageButton.setX(prevBtnX);
            prevImageButton.setY(imageNavY);
            prevImageButton.setWidth(btnWidth);
        }

        if (nextImageButton == null) {
            nextImageButton = new CustomButton(nextBtnX, imageNavY, btnWidth, btnHeight,
                    Text.of(">"), btn -> nextImage());
        } else {
            nextImageButton.setX(nextBtnX);
            nextImageButton.setY(imageNavY);
            nextImageButton.setWidth(btnWidth);
        }
    }

    public void setPost(ArchivePostSummary post) {
        if (post == null) {
            clear();
            return;
        }

        if (this.postInfo != null && post.id() != null && post.id().equals(this.postInfo.id())) {
            return;
        }

        clearDownloadState();

        this.postInfo = post;
        this.postDetail = null;
        this.isLoadingDetails = true;
        this.currentImageIndex = 0;
        this.currentImageTexture = null;
        this.originalImageWidth = 0;
        this.originalImageHeight = 0;
        this.scrollOffset = 0;
        this.imageInfos = new ArrayList<>();
        this.currentImageDescription = "";
        this.attachmentHitboxes.clear();
        this.downloadStatus = "";

        ArchiveNetworkManager.getPostDetails(post)
            .thenAccept(this::handlePostDetailLoaded)
            .exceptionally(throwable -> {
                if (client != null) {
                    client.execute(() -> {
                        isLoadingDetails = false;
                        System.err.println("[PostDetailPanel] Failed to load post details: " + throwable.getMessage());
                    });
                }
                return null;
            });

        this.imageUrls = new String[0];
    }

    private void handlePostDetailLoaded(ArchivePostDetail detail) {
        if (client != null) {
            client.execute(() -> {
                this.postDetail = detail;
                this.isLoadingDetails = false;
                this.availableFiles = detail.attachments() != null ? new ArrayList<>(detail.attachments()) : new ArrayList<>();
                this.imageInfos = detail.imageInfos() != null ? new ArrayList<>(detail.imageInfos()) : new ArrayList<>();

                List<String> detailImages = detail.images();
                if (detailImages != null && !detailImages.isEmpty()) {
                    this.imageUrls = detailImages.toArray(new String[0]);
                    if (currentImageIndex >= imageUrls.length) {
                        currentImageIndex = 0;
                    }
                    updateCurrentImageDescription(imageUrls[currentImageIndex]);
                    if (currentImageTexture == null && imageUrls.length > 0) {
                        loadImage(imageUrls[currentImageIndex]);
                    }
                    preloadNextImage(false);
                }
            });
        }
    }

    private void loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;

        if (imageCache.containsKey(imageUrl)) {
            currentImageTexture = imageCache.get(imageUrl);
            int[] dims = imageDimensionsCache.get(imageUrl);
            if (dims != null) {
                originalImageWidth = dims[0];
                originalImageHeight = dims[1];
            }
            updateCurrentImageDescription(imageUrl);
            isLoadingImage = false;
            return;
        }

        isLoadingImage = true;
        loadingImageUrl = imageUrl;

        loadImageAsync(imageUrl).thenAccept(texId -> {
            if (client != null) {
                client.execute(() -> {
                    if (imageUrl.equals(loadingImageUrl)) {
                        currentImageTexture = texId;
                        int[] dims = imageDimensionsCache.get(imageUrl);
                        if (dims != null) {
                            originalImageWidth = dims[0];
                            originalImageHeight = dims[1];
                        }
                        updateCurrentImageDescription(imageUrl);
                        isLoadingImage = false;
                    }
                });
            }
        }).exceptionally(ex -> {
            if (client != null) {
                client.execute(() -> {
                    isLoadingImage = false;
                    System.err.println("Failed to load image: " + ex.getMessage());
                });
            }
            return null;
        });
    }

    private void preloadNextImage(boolean reversed) {
        if (imageUrls == null || imageUrls.length <= 1) {
            return;
        }

        int nextIndex = (currentImageIndex + (reversed ? -1 : 1) + imageUrls.length) % imageUrls.length;
        String nextUrl = imageUrls[nextIndex];
        if (nextUrl == null || nextUrl.isEmpty()) {
            return;
        }
        if (imageCache.containsKey(nextUrl) || nextUrl.equals(loadingImageUrl) || preloadingImages.contains(nextUrl)) {
            return;
        }

        preloadingImages.add(nextUrl);
        loadImageAsync(nextUrl).handle((tex, ex) -> {
            preloadingImages.remove(nextUrl);
            if (ex != null && client != null) {
                client.execute(() -> System.err.println("Failed to preload image: " + ex.getMessage()));
            }
            return null;
        });
    }

    private CompletableFuture<Identifier> loadImageAsync(String imageUrl) {
        if (imageCache.containsKey(imageUrl)) {
            return CompletableFuture.completedFuture(imageCache.get(imageUrl));
        }

        String encodedUrl = encodeImageUrl(imageUrl);

        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(encodedUrl))
            .GET()
            .header("User-Agent", ArchiveNetworkManager.USER_AGENT)
            .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new CompletionException(new RuntimeException("HTTP error: " + response.statusCode()));
                }

                byte[] imageData = response.body();
                byte[] pngBytes;
                try {
                    pngBytes = convertImageToPng(imageData);
                } catch (Exception e) {
                    throw new CompletionException(e);
                }

                NativeImage nativeImage;
                try {
                    nativeImage = NativeImage.read(new ByteArrayInputStream(pngBytes));
                } catch (Exception e) {
                    throw new CompletionException(e);
                }

                int imgWidth = nativeImage.getWidth();
                int imgHeight = nativeImage.getHeight();

                if (imgWidth <= 0 || imgHeight <= 0 || imgWidth > 4096 || imgHeight > 4096) {
                    nativeImage.close();
                    throw new CompletionException(new RuntimeException("Invalid image dimensions"));
                }

                imageDimensionsCache.put(imageUrl, new int[]{imgWidth, imgHeight});

                final String uniqueId = UUID.randomUUID().toString().replace("-", "");
                final Identifier texId = Identifier.of("litematicdownloader", "textures/dynamic/" + uniqueId);

                if (client != null) {
                    client.execute(() -> {
                        client.getTextureManager().registerTexture(
                            texId,
                            new NativeImageBackedTexture(nativeImage)
                        );
                        imageCache.put(imageUrl, texId);
                    });
                }
                return texId;
            });
    }

    private void updateCurrentImageDescription(String imageUrl) {
        currentImageDescription = "";
        if (imageUrl == null) return;
        for (ArchiveImageInfo info : imageInfos) {
            if (info != null && imageUrl.equals(info.url())) {
                currentImageDescription = info.description() != null ? info.description() : "";
                break;
            }
        }
    }

    private String getCurrentImageDescription() {
        return currentImageDescription != null ? currentImageDescription : "";
    }

    private String encodeImageUrl(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            String encodedPath = path.replace(" ", "%20");
            return uri.getScheme() + "://" + uri.getHost() +
                   (uri.getPort() != -1 ? ":" + uri.getPort() : "") +
                   encodedPath +
                   (uri.getQuery() != null ? "?" + uri.getQuery() : "");
        } catch (Exception e) {
            return url.replace(" ", "%20");
        }
    }

    private byte[] convertImageToPng(byte[] imageData) throws Exception {
        BufferedImage bufferedImage = null;

        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(imageData))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis);
                    bufferedImage = reader.read(0);
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
        }

        if (bufferedImage == null) {
            bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
        }

        if (bufferedImage == null) {
            throw new Exception("Failed to decode image");
        }

        if (bufferedImage.getType() != BufferedImage.TYPE_INT_RGB &&
            bufferedImage.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage converted = new BufferedImage(
                bufferedImage.getWidth(),
                bufferedImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB
            );
            Graphics2D g2d = converted.createGraphics();
            g2d.drawImage(bufferedImage, 0, 0, null);
            g2d.dispose();
            bufferedImage = converted;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "PNG", baos);
        return baos.toByteArray();
    }

    public void clear() {
        this.postInfo = null;
        this.postDetail = null;
        this.isLoadingDetails = false;
        this.isLoadingImage = false;
        this.currentImageTexture = null;
        this.originalImageWidth = 0;
        this.originalImageHeight = 0;
        this.imageUrls = null;
        this.currentImageIndex = 0;
        this.scrollOffset = 0;
        clearDownloadState();
    }

    private void clearDownloadState() {
        this.availableFiles = new ArrayList<>();
        this.downloadStatus = "";
        this.discordThreadButton = null;
        this.websiteButton = null;
        this.preloadingImages.clear();
        this.imageCache.clear();
    }

    private void downloadSchematic(ArchiveAttachment file) {
        downloadStatus = "Downloading...";

        CompletableFuture.runAsync(() -> {
            String downloadUrl = file.downloadUrl();
            if (downloadUrl == null || downloadUrl.isEmpty()) {
                client.execute(() -> {
                    downloadStatus = "Invalid download URL";
                });
                System.err.println("[Download] Invalid download URL");
                return;
            }

            System.out.println("[Download] Starting download from: " + downloadUrl);
            System.out.println("[Download] File: " + file.name());

            String encodedUrl = downloadUrl.replace(" ", "%20");

            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(encodedUrl))
                .GET()
                .header("User-Agent", ArchiveNetworkManager.USER_AGENT)
                .build();

            System.out.println("[Download] Sending request...");
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> handleDownloadResponse(file, response))
                .exceptionally(e -> {
                    handleDownloadError(e);
                    return null;
                });
        });
    }

    private void handleDownloadResponse(ArchiveAttachment file, HttpResponse<byte[]> response) {
        System.out.println("[Download] Response status: " + response.statusCode());
        System.out.println("[Download] Content length: " + response.body().length);

        if (response.statusCode() != 200) {
            String errorMsg;
            switch (response.statusCode()) {
                case 404:
                    errorMsg = "✗ Error: File not found on server";
                    break;
                case 403:
                    errorMsg = "✗ Error: Access denied";
                    break;
                case 500:
                case 502:
                case 503:
                    errorMsg = "✗ Error: Server error (" + response.statusCode() + ")";
                    break;
                case 429:
                    errorMsg = "✗ Error: Too many requests, try again later";
                    break;
                default:
                    errorMsg = "✗ Download failed: HTTP " + response.statusCode();
            }
            System.err.println("[Download] " + errorMsg);
            client.execute(() -> {
                downloadStatus = errorMsg;
            });
            return;
        }

        AttachmentSaver.saveAsync(file, response.body())
            .thenAccept(result -> {
                final String finalFileName = result.fileName();
                final Path finalPath = result.path();
                client.execute(() -> {
                    boolean attemptedAutoLoad = shouldAutoLoadSchematic(file, finalPath);
                    boolean autoLoaded = attemptedAutoLoad && LitematicaAutoLoader.loadIntoWorld(finalPath);
                    downloadStatus = buildDownloadStatus(result, finalFileName, attemptedAutoLoad, autoLoaded);
                    System.out.println("Downloaded to: " + finalPath.toAbsolutePath());
                });
            })
            .exceptionally(ex -> {
                handleDownloadError(ex);
                return null;
            });
    }

    private void handleDownloadError(Throwable throwable) {
        Throwable e = throwable instanceof CompletionException && throwable.getCause() != null ? throwable.getCause() : throwable;
        client.execute(() -> {
            String errorMsg;
            if (e instanceof java.net.UnknownHostException) {
                errorMsg = "✗ Error: No internet connection";
            } else if (e instanceof java.net.SocketTimeoutException) {
                errorMsg = "✗ Error: Connection timeout";
            } else if (e instanceof java.io.FileNotFoundException) {
                errorMsg = "✗ Error: File not found";
            } else if (e instanceof java.io.IOException && e.getMessage().contains("Permission denied")) {
                errorMsg = "✗ Error: Cannot write to disk (permission denied)";
            } else if (e instanceof java.io.IOException && e.getMessage().contains("No space")) {
                errorMsg = "✗ Error: Not enough disk space";
            } else {
                String msg = e.getMessage();
                if (msg != null && msg.length() > 40) {
                    msg = msg.substring(0, 37) + "...";
                }
                errorMsg = "✗ Error: " + (msg != null ? msg : "Unknown error");
            }

            downloadStatus = errorMsg;
            System.err.println("Failed to download schematic: " + e.getMessage());
            e.printStackTrace();
        });
    }

    private boolean shouldAutoLoadSchematic(ArchiveAttachment attachment, Path savedPath) {
        if (attachment != null && attachment.wdl() != null) {
            return false;
        }
        if (savedPath == null || savedPath.getFileName() == null) {
            return false;
        }
        String fileName = savedPath.getFileName().toString().toLowerCase(Locale.ROOT);
        return LitematicaAutoLoader.isAvailable() && fileName.endsWith(".litematic");
    }

    private String buildDownloadStatus(AttachmentSaver.SaveResult result, String fileName, boolean attemptedAutoLoad, boolean autoLoaded) {
        String base = result.isWorldDownload()
            ? "✓ World saved: saves/" + fileName
            : "✓ Downloaded: " + fileName;
        if (result.isWorldDownload() || !attemptedAutoLoad) {
            return base;
        }
        return autoLoaded ? ("✓ Loaded " + fileName) : "- Downloaded (but failed to load): " + fileName;
    }

    private boolean hasDiscordThread() {
        String url = getDiscordThreadUrl();
        return url != null && !url.isBlank();
    }

    private boolean hasWebsiteLink() {
        String id = getCurrentPostId();
        return id != null && !id.isBlank();
    }

    public String getCurrentPostId() {
        return postInfo != null ? postInfo.id() : null;
    }

    private String getDiscordThreadUrl() {
        if (postDetail != null && postDetail.discordPost() != null) {
            return postDetail.discordPost().threadURL();
        }
        return null;
    }

    private void ensureDiscordButton(int width, int xPos, int yPos) {
        if (discordThreadButton == null) {
            discordThreadButton = new CustomButton(
                xPos,
                yPos,
                width,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of("Open Discord Thread"),
                button -> openDiscordThread()
            );
        }
        discordThreadButton.active = hasDiscordThread();
        discordThreadButton.setWidth(width);
        discordThreadButton.setHeight(UITheme.Dimensions.BUTTON_HEIGHT);
        discordThreadButton.setX(xPos);
        discordThreadButton.setY(yPos);
    }

    private void ensureWebsiteButton(int width, int xPos, int yPos) {
        if (websiteButton == null) {
            websiteButton = new CustomButton(
                xPos,
                yPos,
                width,
                UITheme.Dimensions.BUTTON_HEIGHT,
                Text.of("Open On Website"),
                button -> openWebsiteLink()
            );
        }
        websiteButton.active = hasWebsiteLink();
        websiteButton.setWidth(width);
        websiteButton.setHeight(UITheme.Dimensions.BUTTON_HEIGHT);
        websiteButton.setX(xPos);
        websiteButton.setY(yPos);
    }

    private void openDiscordThread() {
        String url = getDiscordThreadUrl();
        if (url == null || url.isBlank()) {
            return;
        }
        if (discordLinkOpener != null) {
            discordLinkOpener.accept(url);
            return;
        }
        try {
            Util.getOperatingSystem().open(url);
        } catch (Exception e) {
            System.err.println("Failed to open Discord thread: " + e.getMessage());
        }
    }

    private void openWebsiteLink() {
        String id = getCurrentPostId();
        if (id == null || id.isBlank()) {
            return;
        }
        String url = "http://storagetech2.org/?id=" + id;
        try {
            Util.getOperatingSystem().open(url);
        } catch (Exception e) {
            System.err.println("Failed to open website: " + e.getMessage());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int renderMouseX = mouseX;
        int renderMouseY = mouseY;
        context.fill(x, y, x + width, y + height, UITheme.Colors.PANEL_BG_SECONDARY);

        context.fill(x, y, x + 1, y + height, UITheme.Colors.BUTTON_BORDER);

        if (postInfo == null) {
            String text = "Select a schematic to view details";
            int textWidth = client.textRenderer.getWidth(text);
            context.drawText(client.textRenderer, text,
                x + (width - textWidth) / 2, y + height / 2 - 4, UITheme.Colors.TEXT_SUBTITLE, false);
            return;
        }

        int contentStartY = y + UITheme.Dimensions.PADDING;
        context.enableScissor(x + 1, contentStartY, x + width, y + height);

        int currentY = contentStartY + UITheme.Dimensions.PADDING - (int) scrollOffset;
        contentHeight = 0;
        attachmentHitboxes.clear();

        int containerWidth = getDisplayImageWidth();
        int containerHeight = getDisplayImageHeight();

        int actualImageWidth = getActualImageWidth();
        int actualImageHeight = getActualImageHeight();

        int containerX = x + 1;
        int containerY = currentY;

        int imageX = containerX + (containerWidth - actualImageWidth) / 2;
        int imageY = containerY + (containerHeight - actualImageHeight) / 2;

        if (isLoadingImage) {
            context.fill(containerX, containerY, containerX + containerWidth, containerY + containerHeight, UITheme.Colors.CONTAINER_BG);
            imageLoadingSpinner.setPosition(
                containerX + containerWidth / 2 - imageLoadingSpinner.getWidth() / 2,
                containerY + containerHeight / 2 - imageLoadingSpinner.getHeight() / 2
            );
            imageLoadingSpinner.render(context, mouseX, mouseY, delta);
        } else if (currentImageTexture != null) {
            context.fill(containerX, containerY, containerX + containerWidth, containerY + containerHeight, UITheme.Colors.PANEL_BG);
            context.drawTexture(
                RenderLayer::getGuiTextured,
                currentImageTexture,
                imageX, imageY,
                0, 0,
                actualImageWidth, actualImageHeight,
                actualImageWidth, actualImageHeight
            );
        } else {
            context.fill(containerX, containerY, containerX + containerWidth, containerY + containerHeight, UITheme.Colors.CONTAINER_BG);
            String noImg = isCompactMode() ? "..." : "No image";
            int tw = client.textRenderer.getWidth(noImg);
            context.drawText(client.textRenderer, noImg,
                containerX + (containerWidth - tw) / 2, containerY + containerHeight / 2 - 4, UITheme.Colors.TEXT_SUBTITLE, false);
        }

        currentY += containerHeight + UITheme.Dimensions.PADDING;
        contentHeight += containerHeight + UITheme.Dimensions.PADDING;

        String imageDescription = getCurrentImageDescription();
        if (imageDescription != null && !imageDescription.isEmpty()) {
            int descWidth = width - UITheme.Dimensions.PADDING * 2;
            drawWrappedText(context, imageDescription, x + UITheme.Dimensions.PADDING, currentY, descWidth, UITheme.Colors.TEXT_SUBTITLE);
            int descHeight = getWrappedTextHeight(imageDescription, descWidth);
            currentY += descHeight + 6;
            contentHeight += descHeight + 6;
        }

        if (imageUrls != null && imageUrls.length > 1) {
            String indicator = String.format("%d / %d", currentImageIndex + 1, imageUrls.length);
            int indicatorWidth = client.textRenderer.getWidth(indicator);
            int indicatorX = x + (width - indicatorWidth) / 2;
            int btnY = currentY;

            updateCarouselButtons(btnY);

            if (prevImageButton != null) {
                prevImageButton.render(context, renderMouseX, renderMouseY, delta);
            }

            context.drawText(client.textRenderer, indicator, indicatorX, btnY + 4, UITheme.Colors.TEXT_SUBTITLE, false);

            if (nextImageButton != null) {
                nextImageButton.render(context, renderMouseX, renderMouseY, delta);
            }

            currentY += 16 + UITheme.Dimensions.PADDING;
            contentHeight += 16 + UITheme.Dimensions.PADDING;
        }

        String title = postInfo.title() != null ? postInfo.title() : "Untitled";
        drawWrappedText(context, title, x + UITheme.Dimensions.PADDING, currentY, width - UITheme.Dimensions.PADDING * 2, UITheme.Colors.TEXT_PRIMARY);
        int titleHeight = getWrappedTextHeight(title, width - UITheme.Dimensions.PADDING * 2);
        currentY += titleHeight + 8;
        contentHeight += titleHeight + 8;

        String metaLine = buildMetaLine();
        context.drawText(client.textRenderer, metaLine, x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE, false);
        currentY += 16;
        contentHeight += 16;

        if (postDetail != null && postDetail.authors() != null && !postDetail.authors().isEmpty()) {
            String authorLine = "By: " + String.join(", ", postDetail.authors());
            drawWrappedText(context, authorLine, x + UITheme.Dimensions.PADDING, currentY, width - UITheme.Dimensions.PADDING * 2, UITheme.Colors.TEXT_SUBTITLE);
            int authorHeight = getWrappedTextHeight(authorLine, width - UITheme.Dimensions.PADDING * 2);
            currentY += authorHeight + 4;
            contentHeight += authorHeight + 4;
        }

        String[] tags = postInfo.tags();
        if (tags != null && tags.length > 0) {
            context.drawText(client.textRenderer, "Tags:", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE, false);
            currentY += 12;
            contentHeight += 12;

            int tagX = x + UITheme.Dimensions.PADDING;
            for (String tag : orderTags(tags)) {
                int tagWidth = client.textRenderer.getWidth(tag) + 8;
                if (tagX + tagWidth > x + width - UITheme.Dimensions.PADDING) {
                    tagX = x + UITheme.Dimensions.PADDING;
                    currentY += 14;
                    contentHeight += 14;
                }
                context.fill(tagX, currentY, tagX + tagWidth, currentY + 12, getTagColor(tag));
                context.drawText(client.textRenderer, tag, tagX + 4, currentY + 2, UITheme.Colors.TEXT_TAG, false);
                tagX += tagWidth + 4;
            }
            currentY += 16;
            contentHeight += 16;
        }

        if (postDetail != null && postDetail.recordSections() != null && !postDetail.recordSections().isEmpty()) {
            currentY += 8;
            contentHeight += 8;
            for (ArchiveRecordSection section : postDetail.recordSections()) {
                if (section == null) continue;
                String header = section.title() != null ? section.title() : "Details";
                context.drawText(client.textRenderer, header + ":", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE, false);
                currentY += 12;
                contentHeight += 12;

                List<String> lines = section.lines();
                if (lines != null) {
                    for (String line : lines) {
                        if (line == null || line.isEmpty()) continue;
                        drawWrappedText(context, line, x + UITheme.Dimensions.PADDING, currentY, width - UITheme.Dimensions.PADDING * 2, UITheme.Colors.TEXT_TAG);
                        int lineHeight = getWrappedTextHeight(line, width - UITheme.Dimensions.PADDING * 2);
                        currentY += lineHeight;
                        contentHeight += lineHeight;
                    }
                }
                currentY += 6;
                contentHeight += 6;
            }
        } else if (isLoadingDetails) {
            currentY += 8;
            context.drawText(client.textRenderer, "Loading details...", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE, false);
            contentHeight += 20;
        }

        if (availableFiles != null && !availableFiles.isEmpty()) {
            context.drawText(client.textRenderer, "Attachments:", x + UITheme.Dimensions.PADDING, currentY, UITheme.Colors.TEXT_SUBTITLE, false);
            currentY += 12;
            contentHeight += 12;

            int rowWidth = width - UITheme.Dimensions.PADDING * 2;
            int rowX = x + UITheme.Dimensions.PADDING;
            for (ArchiveAttachment attachment : availableFiles) {
                if (attachment == null) continue;
                int rowY = currentY;
                String nameText = attachment.name() != null ? attachment.name() : "Attachment";
                String meta = buildAttachmentMeta(attachment);
                int nameHeight = (int) (client.textRenderer.fontHeight * 0.85f) + 6;
                int metaHeight = (meta != null && !meta.isEmpty()) ? client.textRenderer.fontHeight + 2 : 0;
                int descWidth = rowWidth - 12;
                int descHeight = 0;
                if (attachment.description() != null && !attachment.description().isEmpty()) {
                    descHeight = getWrappedTextHeight(attachment.description(), descWidth) + 2;
                }
                int rowHeight = nameHeight + metaHeight + descHeight + 4;

                boolean isHover = renderMouseX >= rowX && renderMouseX <= rowX + rowWidth &&
                                  renderMouseY >= rowY && renderMouseY <= rowY + rowHeight;
                int bgColor = isHover ? UITheme.Colors.BUTTON_BG_HOVER : UITheme.Colors.BUTTON_BG;
                context.fill(rowX, rowY, rowX + rowWidth, rowY + rowHeight, bgColor);

                int cursorY = rowY + 3;
                RenderUtil.drawScaledString(context, nameText, rowX + 6, cursorY, UITheme.Colors.TEXT_PRIMARY, 0.85f, rowWidth - 12);
                cursorY += nameHeight;

                if (metaHeight > 0) {
                    context.drawText(client.textRenderer, meta, rowX + 6, cursorY - 2, UITheme.Colors.TEXT_SUBTITLE, false);
                    cursorY += metaHeight;
                }

                if (descHeight > 0) {
                    drawWrappedText(context, attachment.description(), rowX + 6, cursorY, descWidth, UITheme.Colors.TEXT_TAG);
                    cursorY += descHeight;
                }

                attachmentHitboxes.add(new AttachmentHitbox(rowX, rowY, rowX + rowWidth, rowY + rowHeight, attachment));
                currentY += rowHeight + 6;
                contentHeight += rowHeight + 6;
            }

            if (downloadStatus != null && !downloadStatus.isEmpty()) {
                int statusWidth = rowWidth - 4;
                drawWrappedText(context, downloadStatus, rowX, currentY, statusWidth, UITheme.Colors.TEXT_SUBTITLE);
                int statusHeight = getWrappedTextHeight(downloadStatus, statusWidth);
                currentY += statusHeight + 4;
                contentHeight += statusHeight + 4;
            }
        }

        if (hasWebsiteLink()) {
            int buttonWidth = Math.min(200, width - UITheme.Dimensions.PADDING * 2);
            int buttonX = x + UITheme.Dimensions.PADDING;
            int buttonY = currentY + 4;
            ensureWebsiteButton(buttonWidth, buttonX, buttonY);
            websiteButton.render(context, mouseX, mouseY, delta);
            currentY += UITheme.Dimensions.BUTTON_HEIGHT + 6;
            contentHeight += UITheme.Dimensions.BUTTON_HEIGHT + 6;
        }

        if (hasDiscordThread()) {
            int buttonWidth = Math.min(200, width - UITheme.Dimensions.PADDING * 2);
            int buttonX = x + UITheme.Dimensions.PADDING;
            int buttonY = currentY + 4;
            ensureDiscordButton(buttonWidth, buttonX, buttonY);
            discordThreadButton.render(context, mouseX, mouseY, delta);
            currentY += UITheme.Dimensions.BUTTON_HEIGHT + 10;
            contentHeight += UITheme.Dimensions.BUTTON_HEIGHT + 10;
        }

        contentHeight += UITheme.Dimensions.PADDING * 2;

        context.disableScissor();

        if (contentHeight > height) {
            scrollBar.setScrollData(contentHeight, height);
            scrollBar.setScrollPercentage(scrollOffset / Math.max(1, contentHeight - height));

            if (client != null && client.getWindow() != null) {
                long windowHandle = client.getWindow().getHandle();
                if (scrollBar.updateAndRender(context, mouseX, mouseY, delta, windowHandle)) {
                    double maxScroll = Math.max(0, contentHeight - height);
                    scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
                }
            } else {
                scrollBar.render(context, mouseX, mouseY, delta);
            }
        }

    }

    public boolean hasImageViewerOpen() {
        return imageViewer != null;
    }

    public void renderImageViewer(DrawContext context, int mouseX, int mouseY, float delta) {
        if (imageViewer != null) {
            imageViewer.render(context, mouseX, mouseY, delta);
        }
    }

    private void drawWrappedText(DrawContext context, String text, int textX, int textY, int maxWidth, int color) {
        if (text == null || text.isEmpty()) return;

        int lineY = textY;

        String[] paragraphs = text.split("\\r?\\n");

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lineY += 10;
                continue;
            }

            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();

            for (String word : words) {
                String testLine = !line.isEmpty() ? line + " " + word : word;
                int testWidth = client.textRenderer.getWidth(testLine);

                if (testWidth > maxWidth && !line.isEmpty()) {
                    context.drawText(client.textRenderer, line.toString(), textX, lineY, color, false);
                    line = new StringBuilder(word);
                    lineY += 10;
                } else {
                    line = new StringBuilder(testLine);
                }
            }

            if (!line.isEmpty()) {
                context.drawText(client.textRenderer, line.toString(), textX, lineY, color, false);
                lineY += 10;
            }
        }
    }

    private int getWrappedTextHeight(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return 10;

        int lines = 0;

        String[] paragraphs = text.split("\\r?\\n");

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines++;
                continue;
            }

            String[] words = paragraph.split(" ");
            StringBuilder line = new StringBuilder();
            int paragraphLines = 1;

            for (String word : words) {
                String testLine = !line.isEmpty() ? line + " " + word : word;
                int testWidth = client.textRenderer.getWidth(testLine);

                if (testWidth > maxWidth && !line.isEmpty()) {
                    line = new StringBuilder(word);
                    paragraphLines++;
                } else {
                    line = new StringBuilder(testLine);
                }
            }

            lines += paragraphLines;
        }

        return Math.max(lines, 1) * 10;
    }

    private String buildMetaLine() {
        String channel = postInfo.channelName() != null ? postInfo.channelName() : "Archive";
        String code = postInfo.code() != null ? postInfo.code() : "";
        long updatedAt = postDetail != null ? postDetail.updatedAt() : postInfo.updatedAt();
        long archivedAt = postDetail != null ? postDetail.archivedAt() : postInfo.archivedAt();

        String dateText = updatedAt > 0
            ? "Updated: " + formatDate(updatedAt)
            : (archivedAt > 0 ? "Archived: " + formatDate(archivedAt) : "Date unknown");

        if (!code.isEmpty()) {
            return String.format("%s • %s • %s", channel, code, dateText);
        }
        return String.format("%s • %s", channel, dateText);
    }

    private String formatDate(long millis) {
        if (millis <= 0) {
            return "Unknown";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(millis));
    }

    private String buildAttachmentMeta(ArchiveAttachment attachment) {
        if (attachment == null) return "";
        if (attachment.youtube() != null) {
            ArchiveAttachment.YoutubeInfo yt = attachment.youtube();
            List<String> parts = new ArrayList<>();
            if (yt.title() != null && !yt.title().isEmpty()) parts.add(yt.title());
            if (yt.authorName() != null && !yt.authorName().isEmpty()) parts.add("by " + yt.authorName());
            return parts.isEmpty() ? "YouTube" : "YouTube • " + String.join(" • ", parts);
        }
        if (attachment.litematic() != null) {
            ArchiveAttachment.LitematicInfo info = attachment.litematic();
            List<String> parts = new ArrayList<>();
            if (info.version() != null && !info.version().isEmpty()) parts.add("Version " + info.version());
            if (info.size() != null && !info.size().isEmpty()) parts.add(info.size());
            if (info.error() != null && !info.error().isEmpty()) parts.add("Error: " + info.error());
            return parts.isEmpty() ? "Litematic" : "Litematic • " + String.join(" • ", parts);
        }
        if (attachment.wdl() != null) {
            ArchiveAttachment.WdlInfo info = attachment.wdl();
            List<String> parts = new ArrayList<>();
            if (info.version() != null && !info.version().isEmpty()) parts.add("Version " + info.version());
            if (info.error() != null && !info.error().isEmpty()) parts.add("Error: " + info.error());
            return parts.isEmpty() ? "WorldDL" : "WorldDL • " + String.join(" • ", parts);
        }
        return attachment.contentType() != null ? attachment.contentType() : "";
    }

    private void openAttachmentUrl(ArchiveAttachment attachment) {
        if (attachment == null) return;
        String url = attachment.downloadUrl();
        if (url == null || url.isEmpty()) {
            return;
        }
        try {
            Util.getOperatingSystem().open(url);
        } catch (Exception e) {
            System.err.println("Failed to open attachment URL: " + e.getMessage());
        }
    }

    private int getTagColor(String tag) {
        if (tag == null) return TAG_BG_COLOR;
        String lower = tag.toLowerCase();
        if (lower.contains("untested")) return 0xFF8C6E00;
        if (lower.contains("broken")) return 0xFF8B1A1A;
        if (lower.contains("tested") || lower.contains("functional")) return 0xFF1E7F1E;
        if (lower.contains("recommend")) return 0xFFB8860B;
        return TAG_BG_COLOR;
    }

    private List<String> orderTags(String[] tags) {
        List<String> list = new ArrayList<>();
        if (tags == null) return list;
        List<String> specials = List.of("untested", "broken", "tested & functional", "recommended");
        Set<String> seen = new HashSet<>();
        for (String s : specials) {
            for (String tag : tags) {
                if (tag == null) continue;
                if (tag.toLowerCase().equals(s) && seen.add(tag.toLowerCase())) {
                    list.add(tag);
                }
            }
        }
        for (String tag : tags) {
            if (tag == null) continue;
            String key = tag.toLowerCase();
            if (seen.add(key)) {
                list.add(tag);
            }
        }
        return list;
    }

    private record AttachmentHitbox(int x1, int y1, int x2, int y2, ArchiveAttachment attachment) {
        boolean contains(double px, double py) {
            return px >= x1 && px <= x2 && py >= y1 && py <= y2;
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (imageViewer != null) {
            return imageViewer.mouseClicked(mouseX, mouseY, button);
        }

        if (websiteButton != null && websiteButton.active && button == 0) {
            if (mouseX >= websiteButton.getX() &&
                mouseX < websiteButton.getX() + websiteButton.getWidth() &&
                mouseY >= websiteButton.getY() &&
                mouseY < websiteButton.getY() + websiteButton.getHeight()) {
                openWebsiteLink();
                return true;
            }
        }

        if (discordThreadButton != null && discordThreadButton.active && button == 0) {
            if (mouseX >= discordThreadButton.getX() &&
                mouseX < discordThreadButton.getX() + discordThreadButton.getWidth() &&
                mouseY >= discordThreadButton.getY() &&
                mouseY < discordThreadButton.getY() + discordThreadButton.getHeight()) {
                openDiscordThread();
                return true;
            }
        }

        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return false;
        }

        if (scrollBar != null && scrollBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && currentImageTexture != null && !isLoadingImage && postInfo != null) {
            int contentStartY = y + UITheme.Dimensions.PADDING;
            int currentY = contentStartY + UITheme.Dimensions.PADDING - (int) scrollOffset;

            int containerWidth = getDisplayImageWidth();
            int containerHeight = getDisplayImageHeight();
            int actualImageWidth = getActualImageWidth();
            int actualImageHeight = getActualImageHeight();

            int containerX = x + 1;
            int containerY = currentY;
            int imageX = containerX + (containerWidth - actualImageWidth) / 2;
            int imageY = containerY + (containerHeight - actualImageHeight) / 2;

            if (mouseX >= imageX && mouseX < imageX + actualImageWidth &&
                mouseY >= imageY && mouseY < imageY + actualImageHeight &&
                mouseY >= contentStartY && mouseY < y + height) {
                openImageViewer();
                return true;
            }
        }

        if (button == 0 && imageUrls != null && imageUrls.length > 1) {
            if (prevImageButton != null) {
                boolean isOverPrev = mouseX >= prevImageButton.getX() &&
                                    mouseX < prevImageButton.getX() + prevImageButton.getWidth() &&
                                    mouseY >= prevImageButton.getY() &&
                                    mouseY < prevImageButton.getY() + prevImageButton.getHeight();
                if (isOverPrev) {
                    previousImage();
                    return true;
                }
            }

            if (nextImageButton != null) {
                boolean isOverNext = mouseX >= nextImageButton.getX() &&
                                    mouseX < nextImageButton.getX() + nextImageButton.getWidth() &&
                                    mouseY >= nextImageButton.getY() &&
                                    mouseY < nextImageButton.getY() + nextImageButton.getHeight();
                if (isOverNext) {
                    nextImage();
                    return true;
                }
            }
        }

        if (button == 0 && !attachmentHitboxes.isEmpty()) {
            for (AttachmentHitbox hit : attachmentHitboxes) {
                if (hit.contains(mouseX, mouseY) && hit.attachment() != null) {
                    if (hit.attachment().isDownloadable()) {
                        downloadSchematic(hit.attachment());
                    } else {
                        openAttachmentUrl(hit.attachment());
                    }
                    return true;
                }
            }
        }

        return true;
    }

    private void previousImage() {
        if (imageUrls != null && imageUrls.length > 1) {
            currentImageIndex = (currentImageIndex - 1 + imageUrls.length) % imageUrls.length;
            updateCurrentImageDescription(imageUrls[currentImageIndex]);
            loadImage(imageUrls[currentImageIndex]);
            preloadNextImage(true);
        }
    }

    private void nextImage() {
        if (imageUrls != null && imageUrls.length > 1) {
            currentImageIndex = (currentImageIndex + 1) % imageUrls.length;
            updateCurrentImageDescription(imageUrls[currentImageIndex]);
            loadImage(imageUrls[currentImageIndex]);
            preloadNextImage(false);
        }
    }

    private void openImageViewer() {
        if (currentImageTexture != null && client != null && client.getWindow() != null) {
            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            int totalImages = (imageUrls != null) ? imageUrls.length : 1;

            imageViewer = new ImageViewerWidget(
                client,
                currentImageTexture,
                originalImageWidth,
                originalImageHeight,
                currentImageIndex,
                totalImages,
                this::previousImageInViewer,
                this::nextImageInViewer,
                this::closeImageViewer
            );
            imageViewer.updateLayout(screenWidth, screenHeight);
        }
    }

    private void previousImageInViewer() {
        if (imageUrls != null && imageUrls.length > 1) {
            currentImageIndex = (currentImageIndex - 1 + imageUrls.length) % imageUrls.length;
            loadImage(imageUrls[currentImageIndex]);
            closeImageViewer();
            openImageViewer();
        }
    }

    private void nextImageInViewer() {
        if (imageUrls != null && imageUrls.length > 1) {
            currentImageIndex = (currentImageIndex + 1) % imageUrls.length;
            loadImage(imageUrls[currentImageIndex]);
            closeImageViewer();
            openImageViewer();
        }
    }

    private void closeImageViewer() {
        imageViewer = null;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollBar != null && (scrollBar.isDragging() || scrollBar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY))) {
            double maxScroll = Math.max(0, contentHeight - height);
            scrollOffset = scrollBar.getScrollPercentage() * maxScroll;
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (imageViewer != null) {
            return imageViewer.mouseReleased(mouseX, mouseY, button);
        }

        if (scrollBar != null && scrollBar.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (imageViewer != null) {
            return true;
        }

        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            double maxScroll = Math.max(0, contentHeight - height + UITheme.Dimensions.PADDING);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 20));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (imageViewer != null) {
            return imageViewer.keyPressed(keyCode, scanCode, modifiers);
        }

        if (imageUrls != null && imageUrls.length > 1) {
            if (keyCode == 263) { // Left arrow
                previousImage();
                return true;
            } else if (keyCode == 262) { // Right arrow
                nextImage();
                return true;
            }
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return false;
    }
}
