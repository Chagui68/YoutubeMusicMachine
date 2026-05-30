package com.github.youtubemusic.voicechat;

import com.github.youtubemusic.main.YoutubeMusicMachineMain;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class VoiceChatEventHandler implements Listener {

    private final YoutubeMusicMachineMain plugin;

    public VoiceChatEventHandler(YoutubeMusicMachineMain plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Notify nearby music machines about new player
        if (plugin.getVoiceChatIntegration() != null && plugin.getVoiceChatIntegration().isReady()) {
            plugin.getMusicPlayerManager().getAllPlayers().forEach(player -> {
                if (player.isPlaying()) {
                    // New player is within range of active music machine
                    // Audio will be picked up on next update cycle
                }
            });
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up player from any active audio streams
    }
}