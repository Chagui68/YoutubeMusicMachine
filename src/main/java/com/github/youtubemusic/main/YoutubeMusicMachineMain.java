package com.github.youtubemusic.main;

import com.github.drakescraft_labs.slimefun4.api.SlimefunAddon;
import com.github.youtubemusic.player.LavalinkService;
import com.github.youtubemusic.player.MusicPlayer;
import com.github.youtubemusic.player.MusicPlayerManager;
import com.github.youtubemusic.slimefun.MusicMachineGUI;
import com.github.youtubemusic.voicechat.VoiceChatEventHandler;
import com.github.youtubemusic.voicechat.VoiceChatIntegration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class YoutubeMusicMachineMain extends JavaPlugin implements SlimefunAddon {

    private static YoutubeMusicMachineMain instance;
    private MusicPlayerManager musicPlayerManager;
    private VoiceChatIntegration voiceChatIntegration;
    private boolean voiceChatAvailable = false;
    private boolean voiceChatPluginInstalled = false;
    private final Set<UUID> mutedPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        voiceChatPluginInstalled = Bukkit.getPluginManager().isPluginEnabled("voicechat");
        voiceChatIntegration = new VoiceChatIntegration(this);

        if (voiceChatPluginInstalled) {
            voiceChatIntegration.initialize();
            voiceChatAvailable = true;
            if (voiceChatIntegration.isReady()) {
                getLogger().info("Simple Voice Chat detected - 3D audio streaming enabled");
            } else {
                getLogger().info("Simple Voice Chat available - waiting for API callback...");
            }
        } else {
            getLogger().info("Simple Voice Chat plugin not found - using fallback audio");
            voiceChatAvailable = false;
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Slimefun")) {
            registerSlimefunItems();
            getLogger().info("Slimefun detected - music machines registered");
        } else {
            getLogger().severe("Slimefun not found! This plugin requires Slimefun4");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        musicPlayerManager = new MusicPlayerManager(this);
        LavalinkService.initialize(this);
        registerEvents();
        Bukkit.getScheduler().runTaskTimer(this, this::updateAllPlayers, 0L, 5L);

        getLogger().info("YoutubeMusicMachine enabled!");
    }

    @Override
    public void onDisable() {
        if (musicPlayerManager != null) musicPlayerManager.disposeAll();
        if (voiceChatIntegration != null) voiceChatIntegration.shutdown();
        LavalinkService.shutdown();
        getLogger().info("YoutubeMusicMachine disabled!");
    }

    private void registerSlimefunItems() {
        new MusicMachineGUI(this);
        MusicMachineGUI.registerInteractListener(this);
    }

    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        if (voiceChatPluginInstalled) {
            Bukkit.getPluginManager().registerEvents(new VoiceChatEventHandler(this), this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores pueden usar este comando.");
            return true;
        }
        return switch (label.toLowerCase()) {
            case "playmusic" -> handlePlaymusic(player, args);
            case "stopmusic" -> handleStopmusic(player);
            case "musicinfo" -> handleMusicinfo(player);
            default -> false;
        };
    }

    private boolean handlePlaymusic(Player player, String[] args) {
        if (!player.hasPermission("youtubemusic.use")) {
            player.sendMessage("§cNo tienes permiso.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§eUso: /playmusic <url o nombre>");
            return true;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != org.bukkit.Material.GOLD_BLOCK) {
            player.sendMessage("§cMira hacia una máquina de música (bloque de oro).");
            return true;
        }

        Location loc = target.getLocation();
        MusicPlayer musicPlayer = MusicMachineGUI.getActivePlayer(loc);
        if (musicPlayer == null) {
            musicPlayer = new MusicPlayer(this, loc);
            MusicMachineGUI.setActivePlayer(loc, musicPlayer);
        }

        String input = String.join(" ", args);
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = "ytsearch:" + input;
        }

        musicPlayer.play(input);
        player.sendMessage("§a✓ Reproduciendo: " + (input.startsWith("ytsearch:") ? String.join(" ", args) : input));
        return true;
    }

    private boolean handleStopmusic(Player player) {
        if (!player.hasPermission("youtubemusic.use")) {
            player.sendMessage("§cNo tienes permiso.");
            return true;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != org.bukkit.Material.GOLD_BLOCK) {
            player.sendMessage("§cMira hacia una máquina de música (bloque de oro).");
            return true;
        }

        Location loc = target.getLocation();
        MusicPlayer musicPlayer = MusicMachineGUI.getActivePlayer(loc);
        if (musicPlayer == null || !musicPlayer.isPlaying()) {
            player.sendMessage("§cNo hay música reproduciéndose aquí.");
            return true;
        }

        musicPlayer.stop();
        MusicMachineGUI.setActivePlayer(loc, null);
        player.sendMessage("§cMúsica detenida.");
        return true;
    }

    private boolean handleMusicinfo(Player player) {
        if (!player.hasPermission("youtubemusic.use")) {
            player.sendMessage("§cNo tienes permiso.");
            return true;
        }

        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != org.bukkit.Material.GOLD_BLOCK) {
            player.sendMessage("§cMira hacia una máquina de música (bloque de oro).");
            return true;
        }

        Location loc = target.getLocation();
        MusicPlayer musicPlayer = MusicMachineGUI.getActivePlayer(loc);
        if (musicPlayer == null || !musicPlayer.isPlaying()) {
            player.sendMessage("§cNo hay música reproduciéndose aquí.");
            return true;
        }

        player.sendMessage("§6=== Información ===");
        player.sendMessage("§eCanción: " + musicPlayer.getCurrentTitle());
        player.sendMessage("§eDuración: " + musicPlayer.getCurrentDuration());
        player.sendMessage("§eUbicación: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
        return true;
    }

    private class PlayerJoinListener implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (!voiceChatPluginInstalled && player.hasPermission("youtubemusic.admin") && !mutedPlayers.contains(player.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(YoutubeMusicMachineMain.this, () -> {
                    player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage("§c⚠ §lMEJORA RECOMENDADA");
                    player.sendMessage("");
                    player.sendMessage("§fPara una mejor experiencia con las");
                    player.sendMessage("§fmáquinas de música, instala:");
                    player.sendMessage("");
                    player.sendMessage("§eEn el servidor:");
                    player.sendMessage("§f• VoiceChat plugin");
                    player.sendMessage("§f• https://modrinth.com/plugin/simple-voice-chat");
                    player.sendMessage("");
                    player.sendMessage("§7Este mensaje solo lo ves tú (admin).");
                    player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                }, 20L);
            }
        }
    }

    private void updateAllPlayers() {
        if (musicPlayerManager != null) musicPlayerManager.updateAllPlayers();
    }

    public static YoutubeMusicMachineMain getInstance() { return instance; }
    public MusicPlayerManager getMusicPlayerManager() { return musicPlayerManager; }
    public VoiceChatIntegration getVoiceChatIntegration() { return voiceChatIntegration; }
    public boolean isVoiceChatAvailable() { return voiceChatAvailable; }
    public boolean isVoiceChatPluginInstalled() { return voiceChatPluginInstalled; }
    public String getJavaVersion() { return "21"; }
    public String getBugTrackerURL() { return null; }
    public JavaPlugin getJavaPlugin() { return this; }
}