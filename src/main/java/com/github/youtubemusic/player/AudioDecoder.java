package com.github.youtubemusic.player;

import com.github.youtubemusic.main.YoutubeMusicMachineMain;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

import java.util.function.BiConsumer;

public class AudioDecoder {

    private static AudioPlayerManager playerManager;

    public AudioDecoder() {
        if (playerManager == null) {
            playerManager = new DefaultAudioPlayerManager();
            playerManager.registerSourceManager(new HttpAudioSourceManager());
            YoutubeMusicMachineMain.getInstance().getLogger().info("AudioDecoder initialized");
        }
    }

    public AudioPlayer createPlayer() {
        return playerManager.createPlayer();
    }

    public void loadTrack(String url, AudioPlayer player, BiConsumer<String, Throwable> callback) {
        playerManager.loadItemOrdered(this, url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                player.playTrack(track);
                if (callback != null) callback.accept(track.getInfo().title, null);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (!playlist.getTracks().isEmpty()) {
                    player.playTrack(playlist.getTracks().get(0));
                    if (callback != null) callback.accept(playlist.getTracks().get(0).getInfo().title, null);
                }
            }

            @Override
            public void noMatches() {
                if (callback != null) callback.accept(null, new RuntimeException("No results for: " + url));
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                String errorMsg = exception.getMessage();
                if (errorMsg == null && exception.getCause() != null) {
                    errorMsg = exception.getCause().getMessage();
                }
                if (callback != null) callback.accept(null, new RuntimeException(errorMsg != null ? errorMsg : "Unknown error", exception));
            }
        });
    }

    public static AudioPlayerManager getPlayerManager() {
        return playerManager;
    }
}
