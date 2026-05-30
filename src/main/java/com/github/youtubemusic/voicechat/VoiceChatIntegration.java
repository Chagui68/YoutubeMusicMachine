package com.github.youtubemusic.voicechat;

import com.github.youtubemusic.main.YoutubeMusicMachineMain;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class VoiceChatIntegration {

    private final YoutubeMusicMachineMain plugin;
    private volatile Object voicechatApi;
    private boolean initialized = false;

    private Method createLocationalAudioChannel;
    private Method createPosition;
    private Method fromServerLevel;
    private Method createAudioPlayer;
    private Method createEncoder;
    private Method encoderWithMode;
    private Method playerStartPlaying;
    private Method playerStop;
    private Object audioOpusMode;

    public VoiceChatIntegration(YoutubeMusicMachineMain plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (!Bukkit.getPluginManager().isPluginEnabled("voicechat")) {
            plugin.getLogger().info("Simple Voice Chat not found - audio features disabled");
            return;
        }

        if (registerDelayedPlugin()) {
            plugin.getLogger().info("Registered VoiceChat plugin - API will be available after server starts");
        } else {
            plugin.getLogger().warning("Could not register with Simple Voice Chat");
            initialized = false;
        }
    }

    private boolean registerDelayedPlugin() {
        try {
            Class<?> serviceClass = Class.forName("de.maxhenkel.voicechat.api.BukkitVoicechatService");
            Object service = Bukkit.getServicesManager().load(serviceClass);
            if (service == null) return false;

            Class<?> pluginClass = Class.forName("de.maxhenkel.voicechat.api.VoicechatPlugin");
            Class<?> apiClass = Class.forName("de.maxhenkel.voicechat.api.VoicechatApi");

            Object proxy = Proxy.newProxyInstance(
                    pluginClass.getClassLoader(),
                    new Class<?>[]{pluginClass},
                    (proxyObj, method, args) -> {
                        switch (method.getName()) {
                            case "getPluginId":
                                return "youtube-music-machine";
                            case "initialize":
                                plugin.getLogger().info("VoiceChat API callback received!");
                                voicechatApi = args[0];
                                setupApiMethods();
                                return null;
                            default:
                                return null;
                        }
                    }
            );

            Method registerMethod = serviceClass.getMethod("registerPlugin", pluginClass);
            registerMethod.invoke(service, proxy);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register VoiceChat plugin: " + e.getMessage());
            return false;
        }
    }

    private void setupApiMethods() {
        try {
            Class<?> serverApiClass = Class.forName("de.maxhenkel.voicechat.api.VoicechatServerApi");

            createLocationalAudioChannel = serverApiClass.getMethod(
                    "createLocationalAudioChannel", UUID.class,
                    Class.forName("de.maxhenkel.voicechat.api.ServerLevel"),
                    Class.forName("de.maxhenkel.voicechat.api.Position"));

            createAudioPlayer = serverApiClass.getMethod("createAudioPlayer",
                    Class.forName("de.maxhenkel.voicechat.api.audiochannel.AudioChannel"),
                    Class.forName("de.maxhenkel.voicechat.api.opus.OpusEncoder"),
                    java.util.function.Supplier.class);

            Class<?> apiClass = voicechatApi.getClass();
            createPosition = apiClass.getMethod("createPosition", double.class, double.class, double.class);
            fromServerLevel = apiClass.getMethod("fromServerLevel", Object.class);

            Class<?> opusEncoderModeClass = Class.forName("de.maxhenkel.voicechat.api.opus.OpusEncoderMode");
            audioOpusMode = opusEncoderModeClass.getField("AUDIO").get(null);
            createEncoder = apiClass.getMethod("createEncoder");
            encoderWithMode = apiClass.getMethod("createEncoder", opusEncoderModeClass);

            Class<?> audioPlayerClass = createAudioPlayer.getReturnType();
            playerStartPlaying = audioPlayerClass.getMethod("startPlaying");
            playerStop = audioPlayerClass.getMethod("stopPlaying");

            initialized = true;
            plugin.getLogger().info("Successfully initialized Simple Voice Chat API");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to setup API methods: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
        }
    }

    public AudioPlayerSession createSession(Location location) {
        if (!initialized || voicechatApi == null) return null;
        try {
            UUID channelId = UUID.randomUUID();

            Object serverLevel = fromServerLevel.invoke(voicechatApi, location.getWorld());
            Object position = createPosition.invoke(voicechatApi, location.getX(), location.getY(), location.getZ());
            Object channel = createLocationalAudioChannel.invoke(voicechatApi, channelId, serverLevel, position);

            Object encoder = encoderWithMode.invoke(voicechatApi, audioOpusMode);

            BlockingQueue<short[]> audioQueue = new LinkedBlockingQueue<>();
            AudioPlayerSession session = new AudioPlayerSession(null, audioQueue);

            java.util.function.Supplier<short[]> supplier = () -> {
                if (session.stopped.get()) return null;
                try {
                    short[] frame = audioQueue.poll(5, TimeUnit.SECONDS);
                    if (frame == null && session.stopped.get()) return null;
                    return frame;
                } catch (InterruptedException e) {
                    return null;
                }
            };

            Object audioPlayer = createAudioPlayer.invoke(voicechatApi, channel, encoder, supplier);
            session.audioPlayer = audioPlayer;
            playerStartPlaying.invoke(audioPlayer);

            return session;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create audio session: " + e.getMessage());
            if (plugin.getConfig().getBoolean("debug", false)) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public void sendFrame(AudioPlayerSession session, byte[] pcmFrame) {
        if (!initialized || session == null || session.audioPlayer == null) return;
        try {
            int frameSampleCount = pcmFrame.length / 4;

            short[] monoShorts = new short[frameSampleCount];
            ByteBuffer bb = ByteBuffer.wrap(pcmFrame).order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < frameSampleCount; i++) {
                int left = (int) bb.getShort(i * 4);
                int right = (int) bb.getShort(i * 4 + 2);
                monoShorts[i] = (short) ((left + right) / 2);
            }

            session.audioQueue.put(monoShorts);
        } catch (Exception e) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().log(Level.WARNING, "Error sending audio frame", e);
            }
        }
    }

    public void sendRawFrames(AudioPlayerSession session, short[] monoShorts) {
        if (!initialized || session == null || session.audioPlayer == null) return;
        try {
            session.audioQueue.put(monoShorts);
        } catch (Exception e) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().log(Level.WARNING, "Error sending raw audio frame", e);
            }
        }
    }

    public void stopSession(AudioPlayerSession session) {
        if (session == null) return;
        try {
            session.stopped.set(true);
            if (session.audioPlayer != null) {
                playerStop.invoke(session.audioPlayer);
            }
        } catch (Exception ignored) {}
        session.audioPlayer = null;
    }

    public boolean isReady() {
        return initialized && voicechatApi != null;
    }

    public void shutdown() {
        initialized = false;
        voicechatApi = null;
    }

    public class AudioPlayerSession {
        Object audioPlayer;
        BlockingQueue<short[]> audioQueue;
        AtomicBoolean stopped = new AtomicBoolean(false);

        AudioPlayerSession(Object audioPlayer, BlockingQueue<short[]> audioQueue) {
            this.audioPlayer = audioPlayer;
            this.audioQueue = audioQueue;
        }
    }
}
