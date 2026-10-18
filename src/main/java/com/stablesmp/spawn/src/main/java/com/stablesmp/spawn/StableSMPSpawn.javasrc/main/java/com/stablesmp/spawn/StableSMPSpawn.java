package com.stablesmp.spawn;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public final class StableSMPSpawn extends JavaPlugin implements Listener, CommandExecutor {
    private NamespacedKey npcKey;
    private final Set<UUID> duelQueue = new LinkedHashSet<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, UUID> opponents = new HashMap<>();
    private final Map<UUID, BukkitTask> queueTimeouts = new HashMap<>();
    private final Map<UUID, ItemStack[]> oldInventory = new HashMap<>();
    private final Map<UUID, ItemStack[]> oldArmor = new HashMap<>();
    private final Map<UUID, Integer> oldLevels = new HashMap<>();
    private final Map<UUID, Float> oldExp = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig(); npcKey = new NamespacedKey(this, "stable_npc");
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("stablespawn")).setExecutor(this);
        getLogger().info("StableSMPSpawn enabled. Run /stablespawn setup as an operator.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Run this command in-game."); return true; }
        if (!p.hasPermission("stablesmp.admin")) { p.sendMessage("§cYou need stablesmp.admin."); return true; }
        if (args.length == 0) { p.sendMessage("§b/stablespawn setup §7| §bspawn §7| §bsetarena1 §7| §bsetarena2 §7| §breload"); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setup" -> setupSpawn(p);
            case "spawn" -> p.teleport(spawnLocation());
            case "setarena1" -> setArena(p, "arena1");
            case "setarena2" -> setArena(p, "arena2");
            case "reload" -> { reloadConfig(); p.sendMessage("§aStableSMPSpawn config reloaded."); }
            default -> p.sendMessage("§cUnknown subcommand. Use /stablespawn");
        }
        return true;
    }

    private void setupSpawn(Player p) {
        World w = p.getWorld(); int y = Math.min(w.getMaxHeight() - 25, Math.max(150, p.getLocation().getBlockY() + 25));
        int x = p.getLocation().getBlockX(), z = p.getLocation().getBlockZ();
        getConfig().set("spawn.world", w.getName()); getConfig().set("spawn.x", x + .5); getConfig().set("spawn.y", y + 1.0); getConfig().set("spawn.z", z + .5);
        getConfig().set("npcs.sell.x", x - 6.5); getConfig().set("npcs.sell.y", y + 1.0); getConfig().set("npcs.sell.z", z + .5);
        getConfig().set("npcs.auction.x", x + .5); getConfig().set("npcs.auction.y", y + 1.0); getConfig().set("npcs.auction.z", z + 6.5);
        getConfig().set("npcs.duel.x", x + 6.5); getConfig().set("npcs.duel.y", y + 1.0); getConfig().set("npcs.duel.z", z + .5);
        saveConfig();
        buildIsland(w, x, y, z); buildText(w, x, y + 11, z - 10, "STABLE SMP");
        spawnNpc("sell", "§a§lSELL", w); spawnNpc("auction", "§6§lAUCTION HOUSE", w); spawnNpc("duel", "§c§lCRYSTAL DUELS", w);
        w.setSpawnLocation(x, y + 1, z);
        p.teleport(spawnLocation());
        p.sendMessage("§b§lStableSMP §7spawn built and global world spawn set! §eProtect radius: " + getConfig().getInt("spawn.protection-radius", 32));
        p.sendMessage("§7Now set arena spots: stand at first duel spawn and run §f/stablespawn setarena1§7, then repeat with §fsetarena2§7.");
    }

    private void buildIsland(World w, int x, int y, int z) {
        for (int dy = -5; dy <= 0; dy++) {
            int r = dy == 0 ? 15 : Math.max(2, 13 + dy * 2);
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                Material m;
                if (dy == 0) m = (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) ? Material.QUARTZ_BLOCK : Material.SMOOTH_QUARTZ;
                else if (dy == -1) m = Material.POLISHED_BLACKSTONE;
                else if (dy == -2 && ((dx + dz) % 5 == 0)) m = Material.GOLD_BLOCK;
                else m = Material.DEEPSLATE_BRICKS;
                w.getBlockAt(x + dx, y + dy, z + dz).setType(m, false);
            }
        }
        for (int dx = -13; dx <= 13; dx++) for (int dz = -13; dz <= 13; dz++) {
            if (dx * dx + dz * dz > 169 || (Math.abs(dx) < 2 || Math.abs(dz) < 2)) continue;
            if ((dx * dx + dz * dz) % 11 == 0) w.getBlockAt(x + dx, y + 1, z + dz).setType(Material.SEA_LANTERN, false);
        }
        // Gold-and-quartz center emblem
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            if (Math.abs(dx) == 3 || Math.abs(dz) == 3) w.getBlockAt(x + dx, y + 1, z + dz).setType(Material.GOLD_BLOCK, false);
        }
        w.getBlockAt(x, y + 1, z).setType(Material.BEACON, false);
        w.getBlockAt(x, y + 2, z).setType(Material.AIR, false);
    }

    private void buildText(World w, int centerX, int baseY, int planeZ, String text) {
        Map<Character, String[]> font = font();
        int width = text.length() * 6 - 1, startX = centerX - width / 2;
        for (int i = 0; i < text.length(); i++) {
            String[] glyph = font.getOrDefault(text.charAt(i), font.get(' '));
            for (int row = 0; row < 7; row++) for (int col = 0; col < 5; col++) {
                if (glyph[row].charAt(col) == '1') {
                    int bx = startX + i * 6 + col, by = baseY + (6 - row);
                    w.getBlockAt(bx, by, planeZ).setType(Material.GOLD_BLOCK, false);
                    w.getBlockAt(bx, by, planeZ - 1).setType(Material.SEA_LANTERN, false);
                }
            }
        }
    }
    private Map<Character, String[]> font() {
        Map<Character, String[]> f = new HashMap<>();
        f.put('S', new String[]{"11111","10000","10000","11111","00001","00001","11111"});
        f.put('T', new String[]{"11111","00100","00100","00100","00100","00100","00100"});
        f.put('A', new String[]{"01110","10001","10001","11111","10001","10001","10001"});
        f.put('B', new String[]{"11110","10001","10001","11110","10001","10001","11110"});
        f.put('L', new String[]{"10000","10000","10000","10000","10000","10000","11111"});
        f.put('E', new String[]{"11111","10000","10000","11110","10000","10000","11111"});
        f.put('M', new String[]{"10001","11011","10101","10101","10001","10001","10001"});
        f.put('P', new String[]{"11110","10001","10001","11110","10000","10000","10000"});
        f.put(' ', new String[]{"00000","00000","00000","00000","00000","00000","00000"}); return f;
    }

    private void spawnNpc(String id, String title, World w) {
        Location loc = npcLocation(id, w); if (loc == null) return;
        for (Entity e : w.getNearbyEntities(loc, 1.5, 2.5, 1.5)) if (e.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING) && id.equals(e.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING))) e.remove();
        Villager v = (Villager) w.spawnEntity(loc, EntityType.VILLAGER);
        v.setAI(false); v.setInvulnerable(true); v.setSilent(true); v.setCollidable(false); v.setPersistent(true); v.setProfession(id.equals("sell") ? Villager.Profession.FLETCHER : id.equals("auction") ? Villager.Profession.LIBRARIAN : Villager.Profession.WEAPONSMITH);
        v.customName(net.kyori.adventure.text.Component.text(title)); v.setCustomNameVisible(true); v.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, id);
    }
    private Location npcLocation(String id, World fallback) {
        String path = "npcs." + id; String worldName = getConfig().getString("spawn.world", fallback.getName()); World w = Bukkit.getWorld(worldName); if (w == null) w = fallback;
        return new Location(w, getConfig().getDouble(path + ".x"), getConfig().getDouble(path + ".y"), getConfig().getDouble(path + ".z"));
    }
    private Location spawnLocation() {
        World w = Bukkit.getWorld(getConfig().getString("spawn.world", "")); if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w, getConfig().getDouble("spawn.x", .5), getConfig().getDouble("spawn.y", 181), getConfig().getDouble("spawn.z", .5));
    }
    private void setArena(Player p, String which) {
        Location l = p.getLocation(); getConfig().set("duel.world", p.getWorld().getName()); String path = "duel." + which;
        getConfig().set(path + ".x", l.getX()); getConfig().set(path + ".y", l.getY()); getConfig().set(path + ".z", l.getZ()); getConfig().set(path + ".yaw", l.getYaw()); getConfig().set(path + ".pitch", l.getPitch()); saveConfig();
        p.sendMessage("§aDuel arena point " + which + " saved here.");
    }

    @EventHandler public void onNpcClick(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) return;
        e.setCancelled(true); Player p = e.getPlayer(); String id = e.getRightClicked().getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if ("sell".equals(id)) { p.performCommand("sell"); return; }
        if ("auction".equals(id)) { p.openInventory(auctionMenu()); return; }
        if ("duel".equals(id)) { p.openInventory(duelMenu()); }
    }
    private Inventory auctionMenu() {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lStableSMP Auction House");
        put(inv, 11, Material.GOLD_INGOT, "§eOpen Auction House", "§7Browse player listings", "§aClick to open /ah");
        put(inv, 15, Material.CHEST, "§bHow to sell items", "§7Use the auction command", "§eClick to open /ah");
        put(inv, 22, Material.BARRIER, "§cClose", "§7Close this menu"); return inv;
    }
    private Inventory duelMenu() {
        Inventory inv = Bukkit.createInventory(null, 27, "§c§lStableSMP Duels");
        put(inv, 13, Material.END_CRYSTAL, "§d§lCrystal Duel", "§7Netherite crystal kit", "§7Obsidian • End Crystals", "§7Totems • Golden Apples", "§eClick to join the duel queue");
        put(inv, 22, Material.BARRIER, "§cClose", "§7Close this menu"); return inv;
    }
    private void put(Inventory inv, int slot, Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat); ItemMeta meta = item.getItemMeta(); meta.displayName(net.kyori.adventure.text.Component.text(name));
        List<net.kyori.adventure.text.Component> lines = new ArrayList<>(); for (String line : lore) lines.add(net.kyori.adventure.text.Component.text(line)); meta.lore(lines); item.setItemMeta(meta); inv.setItem(slot, item);
    }
    @EventHandler public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return; String title = e.getView().getTitle();
        if (!title.equals("§6§lStableSMP Auction House") && !title.equals("§c§lStableSMP Duels")) return;
        e.setCancelled(true); ItemStack item = e.getCurrentItem(); if (item == null || !item.hasItemMeta()) return;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        if (name.equals("Close")) { p.closeInventory(); return; }
        if (title.equals("§6§lStableSMP Auction House")) { p.closeInventory(); p.performCommand("ah"); return; }
        if (item.getType() == Material.END_CRYSTAL) { p.closeInventory(); joinDuelQueue(p); }
    }
    private void joinDuelQueue(Player p) {
        if (opponents.containsKey(p.getUniqueId())) { p.sendMessage("§cYou're already in a duel."); return; }
        if (duelQueue.contains(p.getUniqueId())) { p.sendMessage("§eYou're already queued for a crystal duel."); return; }
        if (getConfig().getString("duel.world", "").isBlank()) { p.sendMessage("§cDuel arena isn't configured. An admin must set both arena points."); return; }
        duelQueue.add(p.getUniqueId()); p.sendMessage("§d§lCRYSTAL DUELS §7» §fQueued! Waiting for another player...");
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> { if (duelQueue.remove(p.getUniqueId())) p.sendMessage("§cDuel queue timed out. Click the Duel NPC to try again."); queueTimeouts.remove(p.getUniqueId()); }, 20L * getConfig().getInt("duel.queue-timeout-seconds", 180)); queueTimeouts.put(p.getUniqueId(), task);
        pairPlayers();
    }
    private void pairPlayers() {
        while (duelQueue.size() >= 2) {
            Iterator<UUID> it = duelQueue.iterator(); UUID a = it.next(), b = it.next(); it.remove(); it.remove();
            cancelTimeout(a); cancelTimeout(b); Player p1 = Bukkit.getPlayer(a), p2 = Bukkit.getPlayer(b); if (p1 == null || p2 == null) continue;
            startDuel(p1, p2);
        }
    }
    private void cancelTimeout(UUID id) { BukkitTask t = queueTimeouts.remove(id); if (t != null) t.cancel(); }
    private Location arenaLocation(String which) {
        String path = "duel." + which; World w = Bukkit.getWorld(getConfig().getString("duel.world", "")); if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w, getConfig().getDouble(path + ".x"), getConfig().getDouble(path + ".y"), getConfig().getDouble(path + ".z"), (float)getConfig().getDouble(path + ".yaw"), (float)getConfig().getDouble(path + ".pitch"));
    }
    private void startDuel(Player a, Player b) {
        savePlayer(a); savePlayer(b); opponents.put(a.getUniqueId(), b.getUniqueId()); opponents.put(b.getUniqueId(), a.getUniqueId());
        a.teleport(arenaLocation("arena1")); b.teleport(arenaLocation("arena2")); giveCrystalKit(a); giveCrystalKit(b);
        a.sendMessage("§dCrystal duel started against §f" + b.getName()); b.sendMessage("§dCrystal duel started against §f" + a.getName());
    }
    private void savePlayer(Player p) { UUID id=p.getUniqueId(); returnLocations.put(id,p.getLocation()); oldInventory.put(id,p.getInventory().getContents()); oldArmor.put(id,p.getInventory().getArmorContents()); oldLevels.put(id,p.getLevel()); oldExp.put(id,p.getExp()); p.getInventory().clear(); p.getInventory().setArmorContents(null); }
    private void giveCrystalKit(Player p) {
        PlayerInventory inv=p.getInventory(); inv.clear(); inv.setHelmet(enchant(new ItemStack(Material.NETHERITE_HELMET), Enchantment.PROTECTION, 4)); inv.setChestplate(enchant(new ItemStack(Material.NETHERITE_CHESTPLATE), Enchantment.PROTECTION, 4)); inv.setLeggings(enchant(new ItemStack(Material.NETHERITE_LEGGINGS), Enchantment.PROTECTION, 4)); inv.setBoots(enchant(new ItemStack(Material.NETHERITE_BOOTS), Enchantment.PROTECTION, 4));
        inv.setItem(0,enchant(new ItemStack(Material.NETHERITE_SWORD),Enchantment.SHARPNESS,5)); inv.setItem(1,new ItemStack(Material.END_CRYSTAL,64)); inv.setItem(2,new ItemStack(Material.OBSIDIAN,64)); inv.setItem(3,new ItemStack(Material.TOTEM_OF_UNDYING,1)); inv.setItem(4,new ItemStack(Material.TOTEM_OF_UNDYING,1)); inv.setItem(5,new ItemStack(Material.GOLDEN_APPLE,16)); inv.setItem(6,new ItemStack(Material.ENCHANTED_GOLDEN_APPLE,4)); inv.setItem(7,new ItemStack(Material.EXPERIENCE_BOTTLE,64)); inv.setItem(8,new ItemStack(Material.ENDER_PEARL,16)); p.setHealth(20); p.setFoodLevel(20); p.setSaturation(20); p.setFireTicks(0); p.setFallDistance(0); p.setGameMode(GameMode.SURVIVAL);
    }
    private ItemStack enchant(ItemStack item, Enchantment ench, int level) { item.addUnsafeEnchantment(ench, level); return item; }
    @EventHandler public void onDamage(EntityDamageEvent e) { if (e.getEntity() instanceof Villager v && v.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) e.setCancelled(true); }
    @EventHandler public void onBreak(BlockBreakEvent e) { if (inSpawnProtection(e.getBlock().getLocation()) && !e.getPlayer().hasPermission("stablesmp.admin")) e.setCancelled(true); }
    @EventHandler public void onPlace(BlockPlaceEvent e) { if (inSpawnProtection(e.getBlock().getLocation()) && !e.getPlayer().hasPermission("stablesmp.admin")) e.setCancelled(true); }
    @EventHandler public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> inSpawnProtection(b.getLocation())); }
    private boolean inSpawnProtection(Location l) { if (!l.getWorld().getName().equals(getConfig().getString("spawn.world", ""))) return false; Location s=spawnLocation(); return l.distanceSquared(s) <= Math.pow(getConfig().getInt("spawn.protection-radius",32),2); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { UUID id=e.getPlayer().getUniqueId(); if (duelQueue.remove(id)) cancelTimeout(id); UUID other=opponents.get(id); if(other!=null) endDuel(e.getPlayer(), Bukkit.getPlayer(other), "§cOpponent disconnected."); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { /* Paper's bed respawn takes priority; world spawn is the fallback. */ }
    @EventHandler public void onDeath(org.bukkit.event.entity.PlayerDeathEvent e) { Player loser=e.getEntity(); UUID id=loser.getUniqueId(); UUID other=opponents.get(id); if(other!=null) { Player winner=Bukkit.getPlayer(other); Bukkit.getScheduler().runTask(this, () -> endDuel(loser,winner,"§aDuel finished.")); } }
    private void endDuel(Player p, Player other, String message) {
        UUID id=p.getUniqueId(); UUID oid=opponents.remove(id); if(oid!=null) opponents.remove(oid);
        if(other!=null) { other.sendMessage(message); restorePlayer(other); }
        restorePlayer(p); if(p.isOnline()) p.sendMessage(message);
    }
    private void restorePlayer(Player p) {
        UUID id=p.getUniqueId(); Location back=returnLocations.remove(id); ItemStack[] contents=oldInventory.remove(id), armor=oldArmor.remove(id); Integer level=oldLevels.remove(id); Float xp=oldExp.remove(id);
        if(back!=null && p.isOnline()) p.teleport(back); if(contents!=null) p.getInventory().setContents(contents); if(armor!=null) p.getInventory().setArmorContents(armor); if(level!=null) p.setLevel(level); if(xp!=null) p.setExp(xp);
    }
}
￼Enter
