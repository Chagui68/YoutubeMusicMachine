package com.github.youtubemusic.player;

import com.github.youtubemusic.main.YoutubeMusicMachineMain;
import dev.arbjerg.lavalink.client.LavalinkClient;
import dev.arbjerg.lavalink.client.NodeOptions;
import dev.arbjerg.lavalink.client.event.ReadyEvent;
import dev.arbjerg.lavalink.client.player.LavalinkLoadResult;
import dev.arbjerg.lavalink.client.player.LoadFailed;
import dev.arbjerg.lavalink.client.player.NoMatches;
import dev.arbjerg.lavalink.client.player.PlaylistLoaded;
import dev.arbjerg.lavalink.client.player.SearchResult;
import dev.arbjerg.lavalink.client.player.Track;
import dev.arbjerg.lavalink.client.player.TrackLoaded;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LavalinkService {

    private static LavalinkClient client;
    private static volatile boolean ready = false;
    private static final List<String> activeNodeNames = new ArrayList<>();

    public static synchronized void initialize(YoutubeMusicMachineMain plugin) {
        if (client != null) return;

        client = new LavalinkClient(0L);

        client.on(ReadyEvent.class).subscribe(event -> {
            String name = event.getNode() != null ? event.getNode().getName() : "?";
            plugin.getLogger().info("Lavalink node ready: " + name);
            synchronized (activeNodeNames) {
                if (!activeNodeNames.contains(name)) {
                    activeNodeNames.add(name);
                }
            }
            ready = true;
        });

        List<Map<?, ?>> configured = plugin.getConfig().getMapList("lavalink.nodes");

        for (Map<?, ?> cfg : configured) {
            Object nameRaw = cfg.get("name");
            String name = nameRaw instanceof String s ? s : "unknown";
            Object addrRaw = cfg.get("address");
            String address = addrRaw instanceof String s ? s : null;
            Object pwdRaw = cfg.get("password");
            String password = pwdRaw instanceof String s ? s : "youshallnotpass";
            if (address == null || address.isEmpty()) continue;

            try {
                var builder = new NodeOptions.Builder()
                        .setName(name)
                        .setServerUri(URI.create(address))
                        .setPassword(password);

                client.addNode(builder.build());
                plugin.getLogger().info("Lavalink node registered: " + name + " (" + address + ")");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to register Lavalink node " + name + ": " + e.getMessage());
            }
        }

        if (configured.isEmpty()) {
            plugin.getLogger().info("No Lavalink nodes configured. Using REST fallback (Piped/Invidious).");
            return;
        }

        plugin.getLogger().info("Waiting for Lavalink node(s) to connect...");
        try { Thread.sleep(TimeUnit.SECONDS.toMillis(3)); } catch (InterruptedException ignored) {}

        synchronized (activeNodeNames) {
            if (!activeNodeNames.isEmpty()) {
                plugin.getLogger().info("Lavalink ready (" + activeNodeNames.size() + " node(s)): " + String.join(", ", activeNodeNames));
            } else {
                plugin.getLogger().warning("No Lavalink nodes connected. Using REST fallback (Piped/Invidious).");
            }
        }
    }

    public static LoadResult resolve(String query, YoutubeMusicMachineMain plugin) {
        if (!ready || client == null) return null;

        boolean isSearch = !query.startsWith("http://") && !query.startsWith("https://");
        String identifier = isSearch ? "ytsearch:" + query : query;

        for (var node : client.getNodes()) {
            if (!node.getAvailable()) continue;
            try {
                LavalinkLoadResult result = node.loadItem(identifier)
                        .block(Duration.ofSeconds(10));

                if (result instanceof TrackLoaded t) {
                    return buildResult(t.getTrack(), plugin);
                } else if (result instanceof SearchResult s) {
                    var tracks = s.getTracks();
                    if (tracks != null && !tracks.isEmpty()) {
                        return buildResult(tracks.get(0), plugin);
                    }
                } else if (result instanceof PlaylistLoaded p) {
                    var tracks = p.getTracks();
                    if (tracks != null && !tracks.isEmpty()) {
                        return buildResult(tracks.get(0), plugin);
                    }
                } else if (result instanceof NoMatches) {
                    plugin.getLogger().warning("Lavalink: no matches for " + identifier);
                } else if (result instanceof LoadFailed f) {
                    plugin.getLogger().warning("Lavalink load failed: " + f.getException().getMessage());
                }
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
                plugin.getLogger().warning("Lavalink error: " + (msg != null ? msg : e.getClass().getSimpleName()));
            }
        }

        return null;
    }

    private static LoadResult buildResult(Track track, YoutubeMusicMachineMain plugin) {
        String url = track.getInfo().getUri();
        if (url == null || url.isEmpty()) return null;
        plugin.getLogger().info("Lavalink: " + track.getInfo().getTitle());
        return new LoadResult(url, track.getInfo().getTitle(), track.getEncoded());
    }

    public static void shutdown() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) {}
            client = null;
        }
        ready = false;
        activeNodeNames.clear();
    }

    public static boolean isAvailable() { return ready; }

    public static class LoadResult {
        public final String url;
        public final String title;
        public final String encoded;
        public LoadResult(String url, String title, String encoded) {
            this.url = url;
            this.title = title;
            this.encoded = encoded;
        }
    }
}
