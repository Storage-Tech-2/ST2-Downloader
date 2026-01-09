package com.andrews.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.andrews.config.DownloadSettings;
import com.andrews.models.ArchiveAttachment;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class AttachmentSaver {

    public record SaveResult(String fileName, Path path, boolean isWorldDownload) {}

    // Keep extraction constrained to avoid zip bombs and avoid blocking the common pool.
    private static final ExecutorService IO_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "LitematicDownloader-IO");
        t.setDaemon(true);
        return t;
    });
    private static final long MAX_TOTAL_UNZIPPED_BYTES = 1_000_000_000L; // ~1GB
    private static final long MAX_SINGLE_ENTRY_BYTES = 512L * 1024 * 1024; // 512MB per entry
    private static final int MAX_ENTRY_COUNT = 20000;

    public static CompletableFuture<SaveResult> saveAsync(ArchiveAttachment attachment, byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean isWorldDownload = attachment != null && attachment.wdl() != null;
                Path baseTargetDir = isWorldDownload
                    ? Paths.get(DownloadSettings.getInstance().getGameDirectory(), "saves")
                    : Paths.get(DownloadSettings.getInstance().getAbsoluteDownloadPath());
                Files.createDirectories(baseTargetDir);

                if (isWorldDownload) {
                    String worldName = (attachment != null && attachment.name() != null ? attachment.name() : "world")
                        .replaceAll("[\\\\/:*?\"<>|]", "_");
                    if (worldName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        worldName = worldName.substring(0, worldName.length() - 4);
                    }
                    Path primaryWorld = extractWorldsFromZip(data, baseTargetDir, worldName);
                    return new SaveResult(primaryWorld.getFileName().toString(), primaryWorld, true);
                }

                String baseName = attachment != null && attachment.name() != null ? attachment.name() : "download";
                if (!baseName.contains(".")) {
                    baseName += ".litematic";
                }

                Path existingIdentical = findIdenticalFile(baseTargetDir, baseName, data);
                if (existingIdentical != null) {
                    return new SaveResult(existingIdentical.getFileName().toString(), existingIdentical, false);
                }

                Path outputFile = ensureUniqueName(baseTargetDir, baseName);
                Files.write(outputFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return new SaveResult(outputFile.getFileName().toString(), outputFile, false);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, IO_EXECUTOR);
    }

    private static Path ensureUniqueName(Path dir, String fileName) throws Exception {
        Path candidate = dir.resolve(fileName);
        String baseName = fileName;
        int counter = 1;
        int dot = baseName.lastIndexOf('.');
        String nameOnly = dot > 0 ? baseName.substring(0, dot) : baseName;
        String ext = dot > 0 ? baseName.substring(dot) : "";

        while (Files.exists(candidate)) {
            candidate = dir.resolve(nameOnly + "_" + counter + ext);
            counter++;
        }
        return candidate;
    }

    private static Path ensureUniqueDirectory(Path dir, String name) throws Exception {
        Path candidate = dir.resolve(name);
        int counter = 1;
        while (Files.exists(candidate)) {
            candidate = dir.resolve(name + "_" + counter);
            counter++;
        }
        return candidate;
    }

    private static Path extractWorldsFromZip(byte[] zipBytes, Path baseTargetDir, String defaultWorldName) throws Exception {
        Path tempZip = Files.createTempFile("ldl_wdl_", ".zip");
        Path primaryWorldDir = null;
        long totalExtractedBytes = 0;
        try {
            Files.write(tempZip, zipBytes, StandardOpenOption.TRUNCATE_EXISTING);
            try (ZipFile zipFile = new ZipFile(tempZip.toFile())) {
                // Collect world roots (parents of level.dat)
                ArrayList<String> roots = new ArrayList<>();
                Enumeration<? extends ZipEntry> scan = zipFile.entries();
                while (scan.hasMoreElements()) {
                    ZipEntry entry = scan.nextElement();
                    if (entry.isDirectory()) continue;
                    String name = entry.getName().replace("\\", "/");
                    if (shouldIgnoreZipEntry(name)) continue;
                    if (name.endsWith("level.dat")) {
                        String parent = name.contains("/") ? name.substring(0, name.lastIndexOf('/')) : "";
                        if (!roots.contains(parent)) {
                            roots.add(parent);
                        }
                    }
                }

                if (roots.isEmpty()) {
                    throw new IllegalStateException("No world (level.dat) found in archive");
                }

                ArrayList<WorldTarget> worldTargets = new ArrayList<>();
                for (String root : roots) {
                    String worldName = root.isEmpty()
                        ? defaultWorldName
                        : root.substring(root.lastIndexOf('/') >= 0 ? root.lastIndexOf('/') + 1 : 0);
                    Path worldDir = ensureUniqueDirectory(baseTargetDir, worldName);
                    Files.createDirectories(worldDir);
                    worldTargets.add(new WorldTarget(root, worldDir));
                    if (primaryWorldDir == null) {
                        primaryWorldDir = worldDir;
                    }
                }

                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                int extractedEntries = 0;
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) continue;
                    String entryName = entry.getName().replace("\\", "/");
                    if (shouldIgnoreZipEntry(entryName)) continue;
                    extractedEntries++;
                    if (extractedEntries > MAX_ENTRY_COUNT) {
                        throw new IllegalStateException("Archive has too many files to extract");
                    }

                    WorldTarget target = matchWorld(entryName, worldTargets);
                    if (target == null) continue;

                    String relative = target.root().isEmpty() ? entryName : entryName.substring(target.root().length());
                    if (relative.startsWith("/")) {
                        relative = relative.substring(1);
                    }
                    Path resolved = target.dir().resolve(relative).normalize();
                    if (!resolved.startsWith(target.dir())) {
                        continue;
                    }
                    Files.createDirectories(resolved.getParent());
                    totalExtractedBytes += copyEntryWithLimit(zipFile, entry, resolved, totalExtractedBytes);
                    if (totalExtractedBytes > MAX_TOTAL_UNZIPPED_BYTES) {
                        throw new IllegalStateException("Archive is too large to extract");
                    }
                }
            }
        } finally {
            try {
                Files.deleteIfExists(tempZip);
            } catch (Exception ignored) {}
        }
        return primaryWorldDir != null ? primaryWorldDir : baseTargetDir;
    }

    private static WorldTarget matchWorld(String entryName, List<WorldTarget> targets) {
        if (targets.isEmpty()) return null;
        if (targets.size() == 1) return targets.get(0);

        for (WorldTarget target : targets) {
            String root = target.root();
            if (root.isEmpty()) {
                continue;
            } else if (entryName.equals(root) || entryName.startsWith(root + "/")) {
                return target;
            }
        }
        return targets.get(0);
    }

    private record WorldTarget(String root, Path dir) {}

    private static boolean shouldIgnoreZipEntry(String name) {
        String normalized = name.replace("\\", "/");
        return normalized.startsWith("__MACOSX/") || normalized.equals("__MACOSX") || normalized.contains("/__MACOSX/");
    }

    private static long copyEntryWithLimit(ZipFile zipFile, ZipEntry entry, Path destination, long totalSoFar) throws Exception {
        long declaredSize = entry.getSize();
        if (declaredSize > MAX_SINGLE_ENTRY_BYTES) {
            throw new IllegalStateException("Archive entry exceeds allowed size");
        }

        byte[] buffer = new byte[8192];
        long written = 0;
        try (var is = zipFile.getInputStream(entry);
             var os = Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            int read;
            while ((read = is.read(buffer)) != -1) {
                if (written + read > MAX_SINGLE_ENTRY_BYTES) {
                    throw new IllegalStateException("Archive entry exceeds allowed size");
                }
                if (totalSoFar + written + read > MAX_TOTAL_UNZIPPED_BYTES) {
                    throw new IllegalStateException("Archive is too large to extract");
                }
                os.write(buffer, 0, read);
                written += read;
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(destination);
            } catch (Exception ignored) {}
            throw e;
        }
        return written;
    }

    private static Path findIdenticalFile(Path dir, String fileName, byte[] data) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";

        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) continue;
                String name = path.getFileName().toString();
                if (!name.startsWith(base) || !name.endsWith(ext)) continue;
                // ensure pattern base or base_#
                String middle = name.substring(base.length(), name.length() - ext.length());
                if (!middle.isEmpty() && !middle.matches("_\\d+")) continue;
                try {
                    if (Files.size(path) == data.length && java.util.Arrays.equals(Files.readAllBytes(path), data)) {
                        return path;
                    }
                } catch (Exception ignored) {
                    // continue scanning
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
