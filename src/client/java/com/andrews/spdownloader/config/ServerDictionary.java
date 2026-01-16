package com.andrews.spdownloader.config;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Static catalog of supported servers and their metadata.
 */
public final class ServerDictionary {

    public record ServerEntry(
        String id,
        String name,
        String owner,
        String repo,
        String branch,
        String description,
        String discordInviteUrl,
        String submissionsUrl,
        String downloadFolder
    ) {}

    private static final List<ServerEntry> SERVERS = List.of(
        new ServerEntry(
            "share-projects",
            "Share-Projects",
            "andrews54757",
            "st-shared-projects-browser",
            "main",
            "Browser for the Storage Tech share-projects archive",
            "",
            "",
            "share-projects"
        )
    );

    private ServerDictionary() {}

    public static List<ServerEntry> getServers() {
        return Collections.unmodifiableList(SERVERS);
    }

    public static ServerEntry getDefaultServer() {
        return SERVERS.get(0);
    }

    public static Optional<ServerEntry> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return SERVERS.stream()
            .filter(s -> id.equalsIgnoreCase(s.id()))
            .findFirst();
    }
}
