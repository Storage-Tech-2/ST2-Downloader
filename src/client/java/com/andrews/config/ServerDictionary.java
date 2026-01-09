package com.andrews.config;

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
            "st2",
            "Storage Tech 2",
            "Storage-Tech-2",
            "Archive",
            "main",
            "Official Storage Tech 2 archive",
            "https://discord.gg/hztJMTsx2m",
            "https://discord.com/channels/1375556143186837695/1375575317007040654",
            "st2"
        ),
        new ServerEntry(
            "soon",
            "Soontech",
            "Soontech-Annals",
            "SoonNowArchive",
            "main",
            "Encoded storage community archives",
            "https://discord.gg/dkSM2PyzJe",
            "https://discord.com/channels/1325008017015701504/1390380935148601426",
            "soon"
        ),
        new ServerEntry(
            "wither",
            "Wither Archive",
            "DuskScorpio",
            "wither-archive",
            "main",
            "Wither technology",
            "https://discord.gg/wd594eEtfm",
            "https://discord.com/channels/913065809096638494/1391650300510867487",
            "wither"
        ),
        new ServerEntry(
            "autocraft",
            "Autocraft Archive",
            "XPBot1",
            "Autocrafting-Archive",
            "main",
            "Autocrafting community",
            "https://discord.gg/guZdbQ9KQe",
            "https://discord.com/channels/856232076252282890/1452066872366206977",
            "autocraft"
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
