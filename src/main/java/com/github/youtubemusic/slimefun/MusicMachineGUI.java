package com.github.youtubemusic.slimefun;

import com.github.youtubemusic.main.YoutubeMusicMachineMain;
import com.github.youtubemusic.player.MusicPlayer;
import com.github.drakescraft_labs.slimefun4.api.items.ItemGroup;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItem;
import com.github.drakescraft_labs.slimefun4.api.items.SlimefunItemStack;
import com.github.drakescraft_labs.slimefun4.api.recipes.RecipeType;
import com.github.drakescraft_labs.slimefun4.core.handlers.BlockBreakHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MusicMachineGUI {

    private static final Map<Location, MusicPlayer> activePlayers = new HashMap<>();
    private static final Map<Player, Location> playerMenuLocation = new HashMap<>();
    private static ItemGroup ITEM_GROUP;
    private static final Set<Player> waitingForUrl = ConcurrentHashMap.newKeySet();

    public MusicMachineGUI(YoutubeMusicMachineMain plugin) {
        if (ITEM_GROUP == null) {
            ITEM_GROUP = new ItemGroup(new NamespacedKey(plugin, "youtube_music"), new ItemStack(Material.GOLD_BLOCK));
            ITEM_GROUP.register(plugin);
        }

        SlimefunItemStack itemStack = new SlimefunItemStack(
                "YOUTUBE_MUSIC_MACHINE",
                Material.GOLD_BLOCK,
                "&6YouTube Music Machine",
                "",
                "&7Plays YouTube music with 3D audio",
                "&7Requires Simple Voice Chat client",
                "",
                "&eRight-click to open GUI"
        );

        SlimefunItem item = new SlimefunItem(ITEM_GROUP, itemStack, RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[]{
                null, null, null,
                null, new ItemStack(Material.NOTE_BLOCK), null,
                new ItemStack(Material.REDSTONE), new ItemStack(Material.GOLD_BLOCK), new ItemStack(Material.REDSTONE)
        });

        item.addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull BlockBreakEvent e, @Nonnull ItemStack item, @Nonnull List<ItemStack> drops) {
                Block b = e.getBlock();
                Location loc = b.getLocation();
                MusicPlayer player = activePlayers.remove(loc);
                if (player != null) {
                    player.stop();
                }
            }
        });

        item.register(plugin);
    }

    public static void registerInteractListener(YoutubeMusicMachineMain plugin) {
        plugin.getServer().getPluginManager().registerEvents(new MusicMachineInteractListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new GuiClickListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new UrlInputListener(plugin), plugin);
    }

    public static void requestUrlInput(Player player, Location loc) {
        waitingForUrl.add(player);
        playerMenuLocation.put(player, loc);
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§e§lIngresa una canción o URL:");
        player.sendMessage("§f• §eURL§f: pega un link de YouTube/SoundCloud/etc");
        player.sendMessage("§f• §eNombre§f: busca por nombre (YouTube)");
        player.sendMessage("§fEjemplo: §bnever gonna give you up");
        player.sendMessage("§fEjemplo: §bhttps://youtu.be/dQw4w9WgXcQ");
        player.sendMessage("§eEscribe 'cancel' para cancelar");
        player.sendMessage("§6━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    public static boolean isWaitingForUrl(Player player) {
        return waitingForUrl.contains(player);
    }

    public static void removeWaitingPlayer(Player player) {
        waitingForUrl.remove(player);
        playerMenuLocation.remove(player);
    }

    public static MusicPlayer getActivePlayer(Location loc) {
        return activePlayers.get(loc);
    }

    public static void setActivePlayer(Location loc, MusicPlayer player) {
        activePlayers.put(loc, player);
    }

    public static class MusicMachineInteractListener implements org.bukkit.event.Listener {

        private final YoutubeMusicMachineMain plugin;

        public MusicMachineInteractListener(YoutubeMusicMachineMain plugin) {
            this.plugin = plugin;
        }

        @org.bukkit.event.EventHandler
        public void onPlayerInteract(PlayerInteractEvent e) {
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
                return;
            }

            Player p = e.getPlayer();
            Block b = e.getClickedBlock();
            if (b == null) return;

            if (b.getType() == Material.GOLD_BLOCK) {
                e.setCancelled(true);
                Location loc = b.getLocation();
                openMenu(p, loc, plugin);
            }
        }
    }

    public static class GuiClickListener implements org.bukkit.event.Listener {

        private final YoutubeMusicMachineMain plugin;

        public GuiClickListener(YoutubeMusicMachineMain plugin) {
            this.plugin = plugin;
        }

        @org.bukkit.event.EventHandler
        public void onInventoryClick(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player player)) return;
            if (e.getView().getTitle() == null || !e.getView().getTitle().contains("YouTube Music Machine")) return;

            e.setCancelled(true);

            Location loc = playerMenuLocation.get(player);
            if (loc == null) {
                player.closeInventory();
                return;
            }

            MusicPlayer musicPlayer = activePlayers.get(loc);
            ItemStack clicked = e.getCurrentItem();

            if (clicked == null || clicked.getType() == Material.AIR) return;

            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;
            String name = meta.getDisplayName();

            if (name.contains("Reproducir")) {
                player.closeInventory();
                requestUrlInput(player, loc);
            } else if (name.contains("Detener")) {
                if (musicPlayer != null) {
                    musicPlayer.stop();
                    activePlayers.remove(loc);
                    player.sendMessage("§cMúsica detenida.");
                } else {
                    player.sendMessage("§cNo hay música reproduciéndose.");
                }
            } else if (name.contains("Información")) {
                if (musicPlayer != null && musicPlayer.isPlaying()) {
                    player.sendMessage("§6=== Información ===");
                    player.sendMessage("§eCanción: " + musicPlayer.getCurrentTitle());
                    player.sendMessage("§eDuración: " + musicPlayer.getCurrentDuration());
                    player.sendMessage("§eUbicación: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                } else {
                    player.sendMessage("§cNo hay música reproduciéndose.");
                }
            }
        }
    }

    public static class UrlInputListener implements org.bukkit.event.Listener {

        private final YoutubeMusicMachineMain plugin;

        public UrlInputListener(YoutubeMusicMachineMain plugin) {
            this.plugin = plugin;
        }

        @org.bukkit.event.EventHandler
        public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
            Player player = e.getPlayer();
            String message = e.getMessage().trim();

            if (waitingForUrl.contains(player)) {
                e.setCancelled(true);
                waitingForUrl.remove(player);
                Location loc = playerMenuLocation.remove(player);

                if (message.equalsIgnoreCase("cancel")) {
                    player.sendMessage("§cOperación cancelada.");
                    return;
                }

                if (loc == null) {
                    player.sendMessage("§cError: No se encontró la ubicación.");
                    return;
                }

                MusicPlayer musicPlayer = activePlayers.get(loc);
                if (musicPlayer == null) {
                    musicPlayer = new MusicPlayer(plugin, loc);
                    activePlayers.put(loc, musicPlayer);
                }

                String input = message;
                if (!message.startsWith("http://") && !message.startsWith("https://") && !message.startsWith("www.") && !message.startsWith("ytsearch:") && !message.startsWith("scsearch:") && !message.startsWith("ytmusic:")) {
                    input = "ytsearch:" + message;
                }

                musicPlayer.play(input);
                player.sendMessage("§a✓ Reproduciendo: " + (input.startsWith("ytsearch:") ? message : input));
            }
        }
    }

    private static void openMenu(Player p, Location loc, YoutubeMusicMachineMain plugin) {
        playerMenuLocation.put(p, loc);

        org.bukkit.inventory.Inventory gui = org.bukkit.Bukkit.createInventory(null, 9, "§6YouTube Music Machine");

        ItemStack playButton = createItem(Material.LIME_DYE, "§a§lReproducir Música", "§7Haz clic para ingresar una URL", "", "§eClick para continuar");
        ItemStack stopButton = createItem(Material.RED_DYE, "§c§lDetener Música", "§7Detiene la música actual", "", "§eClick para detener");
        ItemStack infoButton = createItem(Material.BOOK, "§b§lInformación", "§7Muestra información de la canción", "", "§eClick para ver info");

        MusicPlayer player = activePlayers.get(loc);
        if (player != null && player.isPlaying()) {
            ItemStack trackInfo = createItem(Material.NOTE_BLOCK, "§e§lCanción Actual", "§7" + player.getCurrentTitle(), "§7Duración: " + player.getCurrentDuration(), "", "§aReproduciendo...");
            gui.setItem(4, trackInfo);
        }

        gui.setItem(2, playButton);
        gui.setItem(4, stopButton);
        gui.setItem(6, infoButton);

        p.openInventory(gui);
    }

    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(line);
                }
                meta.setLore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
