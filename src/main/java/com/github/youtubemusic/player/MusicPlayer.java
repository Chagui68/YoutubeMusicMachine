package com.github.youtubemusic.player;

import com.github.youtubemusic.main.YoutubeMusicMachineMain;
import com.github.youtubemusic.voicechat.VoiceChatIntegration;
import com.github.youtubemusic.voicechat.VoiceChatIntegration.AudioPlayerSession;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.bukkit.Location;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MusicPlayer {

    private final YoutubeMusicMachineMain plugin;
    private final Location location;
    private final Queue<String> playlist;
    private volatile String currentTitle;
    private volatile String currentDuration;
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private AudioPlayer audioPlayer;
    private Thread audioThread;
    private AudioPlayerSession currentSession;

    public MusicPlayer(YoutubeMusicMachineMain plugin, Location location) {
        this.plugin = plugin;
        this.location = location;
        this.playlist = new ConcurrentLinkedQueue<>();
        this.currentTitle = "Ninguna";
        this.currentDuration = "0:00";
    }

    public void play(String youtubeUrl) {
        if (stopped.get()) stopped.set(false);
        playlist.add(youtubeUrl);
        if (playing.compareAndSet(false, true)) {
            audioThread = new Thread(this::processPlaylist,
                    "youtube-music-" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
            audioThread.setDaemon(true);
            audioThread.start();
        }
    }

    private void processPlaylist() {
        try {
            while (!stopped.get()) {
                String url = playlist.poll();
                if (url == null) break;
                String resolved = resolveOne(url);
                if (resolved != null) tryPlayUrl(resolved);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Audio thread error: " + e.getMessage());
        } finally {
            playing.set(false);
            audioThread = null;
        }
    }

    private String resolveOne(String input) {
        if (LavalinkService.isAvailable()) {
            String clean = input.replaceFirst("^(ytsearch:|ytmsearch:|scsearch:|ytmusic:)", "");
            var result = LavalinkService.resolve(clean, plugin);
            if (result != null) {
                currentTitle = result.title;
                return result.url;
            }
        }

        boolean isYt = input.startsWith("ytsearch:") || input.startsWith("ytmsearch:")
                || input.contains("youtube.com") || input.contains("youtu.be");

        if (isYt) {
            String ytUrl = YtDlpManager.resolve(input);
            if (ytUrl != null) return ytUrl;

            String clean = input.replaceFirst("^(ytsearch:|ytmsearch:|scsearch:|ytmusic:)", "");
            String fallback = MusicAPIManager.resolve(clean);
            if (fallback != null) return fallback;

            plugin.getLogger().severe("All resolution methods failed for: " + input);
            return null;
        }

        return input;
    }

    private boolean tryPlayUrl(String url) {
        boolean success = false;
        try {
            AudioDecoder decoder = new AudioDecoder();
            audioPlayer = decoder.createPlayer();

            CountDownLatch latch = new CountDownLatch(1);
            Throwable[] loadError = new Throwable[1];

            decoder.loadTrack(url, audioPlayer, (title, error) -> {
                if (error != null) {
                    loadError[0] = error;
                } else if (title != null) {
                    currentTitle = title;
                }
                latch.countDown();
            });

            if (!latch.await(15, TimeUnit.SECONDS)) {
                plugin.getLogger().severe("Timeout loading track");
                return false;
            }
            if (loadError[0] != null) {
                String msg = loadError[0].getMessage();
                if (msg == null && loadError[0].getCause() != null)
                    msg = loadError[0].getCause().getMessage();
                plugin.getLogger().severe("Error: " + (msg != null ? msg : "Unknown"));
                return false;
            }
            if (audioPlayer.getPlayingTrack() == null) {
                plugin.getLogger().severe("No track loaded");
                return false;
            }

            var info = audioPlayer.getPlayingTrack().getInfo();
            currentTitle = info.title;
            currentDuration = formatDuration(info.length);
            plugin.getLogger().info("Playing: " + currentTitle + " (" + currentDuration + ")");

            VoiceChatIntegration integration = plugin.getVoiceChatIntegration();
            if (integration != null && plugin.isVoiceChatAvailable()) {
                for (int i = 0; i < 100 && !integration.isReady(); i++) {
                    try { Thread.sleep(100); } catch (InterruptedException ex) { break; }
                }
                if (integration.isReady()) {
                    if (!streamToVoiceChat(integration)) {
                        plugin.getLogger().warning("No audio from VoiceChat");
                        return false;
                    }
                } else {
                    consumeLocally();
                }
            } else {
                consumeLocally();
            }
            success = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null && e.getCause() != null) msg = e.getCause().getMessage();
            plugin.getLogger().severe("Error: " + (msg != null ? msg : "Unknown"));
        } finally {
            if (audioPlayer != null) { audioPlayer.destroy(); audioPlayer = null; }
        }
        return success;
    }

    private void consumeLocally() {
        boolean played = false;
        while (playing.get() && audioPlayer != null && audioPlayer.getPlayingTrack() != null) {
            if (audioPlayer.provide() != null) played = true;
            try { Thread.sleep(100); } catch (InterruptedException e) { break; }
        }
    }

    private String formatDuration(long duration) {
        if (duration < 0) return "En vivo";
        long seconds = duration / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private boolean streamToVoiceChat(VoiceChatIntegration integration) {
        currentSession = integration.createSession(location);
        if (currentSession == null) return false;

        boolean played = false;
        ArrayList<Short> buffer = new ArrayList<>(960);
        while (playing.get() && audioPlayer != null && audioPlayer.getPlayingTrack() != null) {
            AudioFrame frame = audioPlayer.provide();
            if (frame != null) {
                byte[] data = frame.getData();
                int pairs = data.length / 4;
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
                for (int i = 0; i < pairs; i++) {
                    int left = bb.getShort(i * 4);
                    int right = bb.getShort(i * 4 + 2);
                    buffer.add((short) ((left + right) / 2));
                }
                while (buffer.size() >= 960) {
                    short[] block = new short[960];
                    for (int i = 0; i < 960; i++) block[i] = buffer.remove(0);
                    integration.sendRawFrames(currentSession, block);
                    played = true;
                }
            }
            try { Thread.sleep(20); } catch (InterruptedException e) { break; }
        }
        integration.stopSession(currentSession);
        currentSession = null;
        return played;
    }

    public void stop() {
        stopped.set(true);
        playlist.clear();
        if (currentSession != null) {
            var integration = plugin.getVoiceChatIntegration();
            if (integration != null) integration.stopSession(currentSession);
            currentSession = null;
        }
        if (audioPlayer != null) {
            audioPlayer.stopTrack();
            audioPlayer.destroy();
            audioPlayer = null;
        }
        Thread thread = audioThread;
        audioThread = null;
        if (thread != null && thread.isAlive()) thread.interrupt();
    }

    public boolean isPlaying() { return playing.get(); }
    public Location getLocation() { return location; }
    public String getCurrentTitle() { return currentTitle != null ? currentTitle : "Desconocida"; }
    public String getCurrentDuration() { return currentDuration != null ? currentDuration : "0:00"; }
    public int getPlaylistSize() { return playlist.size(); }
    public void dispose() { stop(); }
}