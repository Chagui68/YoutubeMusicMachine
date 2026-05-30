package com.github.youtubemusic.player;

import com.github.youtubemusic.main.YoutubeMusicMachineMain;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MusicPlayerManager {

    private final YoutubeMusicMachineMain plugin;
    private final Map<UUID, MusicPlayer> players;
    private final Map<String, UUID> locationIndex;

    public MusicPlayerManager(YoutubeMusicMachineMain plugin) {
        this.plugin = plugin;
        this.players = new ConcurrentHashMap<>();
        this.locationIndex = new ConcurrentHashMap<>();
    }

    public MusicPlayer getOrCreatePlayer(Location loc) {
        String key = locationToString(loc);
        UUID id = locationIndex.get(key);

        if (id != null && players.containsKey(id)) {
            return players.get(id);
        }

        MusicPlayer player = new MusicPlayer(plugin, loc);
        UUID playerId = UUID.randomUUID();
        players.put(playerId, player);
        locationIndex.put(key, playerId);

        return player;
    }

    public MusicPlayer getPlayer(Location loc) {
        String key = locationToString(loc);
        UUID id = locationIndex.get(key);

        if (id != null && players.containsKey(id)) {
            return players.get(id);
        }

        return null;
    }

    public void removePlayer(Location loc) {
        String key = locationToString(loc);
        UUID id = locationIndex.remove(key);

        if (id != null) {
            MusicPlayer player = players.remove(id);
            if (player != null) {
                player.dispose();
            }
        }
    }

    public void updateAllPlayers() {
        Iterator<Map.Entry<UUID, MusicPlayer>> iterator = players.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, MusicPlayer> entry = iterator.next();
            MusicPlayer player = entry.getValue();

            if (!player.isPlaying()) {
                player.dispose();
                iterator.remove();

                String key = locationToString(player.getLocation());
                locationIndex.remove(key);
                continue;
            }

            World world = player.getLocation().getWorld();
            if (world == null || !world.getPlayers().isEmpty()) {
                player.getLocation().getWorld().getPlayers().forEach(p -> {
                    if (p.getLocation().distance(player.getLocation()) <=
                            plugin.getConfig().getInt("audio-range.max-distance", 32)) {
                        // Player is in range, audio is being streamed
                    }
                });
            }
        }
    }

    public void disposeAll() {
        for (MusicPlayer player : players.values()) {
            player.dispose();
        }
        players.clear();
        locationIndex.clear();
    }

    public Collection<MusicPlayer> getAllPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public int getPlayerCount() {
        return players.size();
    }

    private String locationToString(Location loc) {
        if (loc == null) {
            return "null";
        }
        return String.format("%s:%d:%d:%d",
                loc.getWorld() != null ? loc.getWorld().getName() : "unknown",
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ());
    }
}
