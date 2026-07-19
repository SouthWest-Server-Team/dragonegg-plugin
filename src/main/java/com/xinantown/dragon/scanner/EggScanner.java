package com.xinantown.dragon.scanner;

import com.xinantown.dragon.DragonEggTracker;
import com.xinantown.dragon.EggLocationType;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Periodic scanner that ensures only one dragon egg exists server-wide.
 * Runs periodically and can be triggered manually via /dragon scan.
 *
 * Performance optimized: increased interval, early-exit when known egg location
 * is verified, reduced per-chunk overhead.
 */
public class EggScanner implements Runnable {

    private final DragonEggTracker tracker;
    private final Logger logger;
    private int taskId = -1;

    // Increased from 30s to 5min for production performance
    private static final long SCAN_INTERVAL_TICKS = 6000L; // 5 minutes

    public EggScanner(DragonEggTracker tracker, Logger logger) {
        this.tracker = tracker;
        this.logger = logger;
    }

    public void start() {
        taskId = Bukkit.getScheduler().runTaskTimer(
                Bukkit.getPluginManager().getPlugin("DragonEggGuard"),
                this,
                SCAN_INTERVAL_TICKS,  // first delay: 5 min
                SCAN_INTERVAL_TICKS   // repeat: 5 min
        ).getTaskId();
        logger.info("Egg scanner started (interval: 5min)");
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
            logger.info("Egg scanner stopped");
        }
    }

    @Override
    public void run() {
        scanNow(null);
    }

    /**
     * Perform a scan. Uses early-exit strategy: first verifies the known
     * tracker location, only falls back to full scan if the egg has moved.
     */
    public void scanNow(CommandSender reporter) {
        // Early exit: if tracker has a known location, verify it first
        if (tracker.isTracked() && verifyKnownEgg()) {
            if (reporter != null) {
                reporter.sendMessage("§a扫描完成：龙蛋仍在追踪位置，无需全量扫描");
            }
            return;
        }

        // Full scan fallback
        performFullScan(reporter);
    }

    /**
     * Quick verification of the currently tracked egg location.
     * Returns true if the egg is still where the tracker expects it.
     */
    private boolean verifyKnownEgg() {
        try {
            switch (tracker.getType()) {
                case BLOCK: {
                    World w = Bukkit.getWorld(tracker.getWorldName());
                    if (w == null) return false;
                    Block block = w.getBlockAt(tracker.getX(), tracker.getY(), tracker.getZ());
                    return block.getType() == Material.DRAGON_EGG;
                }
                case ITEM_ENTITY: {
                    if (tracker.getEntityUUID() == null) return false;
                    Entity entity = Bukkit.getEntity(tracker.getEntityUUID());
                    if (entity instanceof Item item) {
                        return item.getItemStack().getType() == Material.DRAGON_EGG;
                    }
                    return false;
                }
                case ITEM_FRAME: {
                    if (tracker.getEntityUUID() == null) return false;
                    Entity entity = Bukkit.getEntity(tracker.getEntityUUID());
                    if (entity instanceof ItemFrame frame) {
                        return frame.getItem().getType() == Material.DRAGON_EGG;
                    }
                    return false;
                }
                case PLAYER_INVENTORY: {
                    if (tracker.getPlayerUUID() == null) return false;
                    Player player = Bukkit.getPlayer(tracker.getPlayerUUID());
                    if (player == null || !player.isOnline()) return false;
                    return player.getInventory().contains(Material.DRAGON_EGG);
                }
                case CONTAINER: {
                    World w = Bukkit.getWorld(tracker.getWorldName());
                    if (w == null) return false;
                    Block block = w.getBlockAt(tracker.getX(), tracker.getY(), tracker.getZ());
                    if (block.getState() instanceof InventoryHolder holder) {
                        return holder.getInventory().contains(Material.DRAGON_EGG);
                    }
                    return false;
                }
                default:
                    return false;
            }
        } catch (Exception e) {
            return false; // Chunk unloaded or other error — fall back to full scan
        }
    }

    /**
     * Full scan of all loaded chunks (fallback when verifyKnownEgg fails).
     */
    private void performFullScan(CommandSender reporter) {
        List<EggFoundEntry> allEggs = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                // Stop scanning this chunk once we've found enough eggs
                // (optimization: break early since we only care about duplicates)
                scanBlocks(chunk, allEggs);

                if (allEggs.isEmpty()) {
                    scanEntities(chunk, allEggs);
                }
                if (allEggs.isEmpty()) {
                    scanContainers(chunk, allEggs);
                }
            }
        }

        scanPlayers(allEggs);

        int total = allEggs.size();

        if (total == 0) {
            if (tracker.isTracked()) {
                tracker.clear();
            }
            if (reporter != null) {
                reporter.sendMessage("§e扫描完成：未发现龙蛋，追踪器已清空");
            }
            return;
        }

        if (total == 1) {
            EggFoundEntry egg = allEggs.get(0);
            updateTrackerFromScan(egg);
            if (reporter != null) {
                reporter.sendMessage("§a扫描完成：发现1个龙蛋 - " + egg.getDescription());
            }
            return;
        }

        // Multiple eggs: deduplicate
        logger.warning("Found " + total + " dragon eggs! Removing duplicates...");

        EggFoundEntry keeper = null;
        List<EggFoundEntry> duplicates = new ArrayList<>();

        for (EggFoundEntry egg : allEggs) {
            if (matchesTracker(egg)) {
                keeper = egg;
            } else {
                duplicates.add(egg);
            }
        }

        if (keeper == null) {
            keeper = allEggs.get(0);
            duplicates = allEggs.subList(1, allEggs.size());
            logger.info("No egg matched tracker; keeping first found: " + keeper.getDescription());
        }

        for (EggFoundEntry dup : duplicates) {
            removeEgg(dup);
            logger.info("Removed duplicate egg: " + dup.getDescription());
        }

        updateTrackerFromScan(keeper);

        String resultMsg = "§a扫描完成：发现" + total + "个龙蛋，已保留1个，删除" + duplicates.size() + "个";
        if (reporter != null) {
            reporter.sendMessage(resultMsg);
        }
        if (duplicates.size() > 0) {
            logger.warning(resultMsg);
        }
    }

    /**
     * Scan for DRAGON_EGG blocks in a chunk.
     * Uses getBlock which is lighter than getTileEntities.
     */
    private void scanBlocks(Chunk chunk, List<EggFoundEntry> results) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == Material.DRAGON_EGG) {
                        results.add(new EggFoundEntry(
                                EggLocationType.BLOCK,
                                world.getName(),
                                chunkX + x, y, chunkZ + z,
                                null, null, null, null
                        ));
                        return; // Found one — no need to scan more blocks in this chunk
                    }
                }
            }
        }
    }

    private void scanEntities(Chunk chunk, List<EggFoundEntry> results) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Item item) {
                if (item.getItemStack().getType() == Material.DRAGON_EGG) {
                    results.add(new EggFoundEntry(
                            EggLocationType.ITEM_ENTITY,
                            entity.getWorld().getName(),
                            entity.getLocation().getBlockX(),
                            entity.getLocation().getBlockY(),
                            entity.getLocation().getBlockZ(),
                            entity.getUniqueId(),
                            null, null, null
                    ));
                    return;
                }
            } else if (entity instanceof ItemFrame frame) {
                ItemStack frameItem = frame.getItem();
                if (frameItem.getType() == Material.DRAGON_EGG) {
                    results.add(new EggFoundEntry(
                            EggLocationType.ITEM_FRAME,
                            entity.getWorld().getName(),
                            entity.getLocation().getBlockX(),
                            entity.getLocation().getBlockY(),
                            entity.getLocation().getBlockZ(),
                            entity.getUniqueId(),
                            null, null, null
                    ));
                    return;
                }
            }
        }
    }

    private void scanContainers(Chunk chunk, List<EggFoundEntry> results) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof InventoryHolder holder) {
                if (holder instanceof Player) continue;

                for (ItemStack item : holder.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.DRAGON_EGG) {
                        results.add(new EggFoundEntry(
                                EggLocationType.CONTAINER,
                                state.getWorld().getName(),
                                state.getX(), state.getY(), state.getZ(),
                                null, null, state.getType().name(), null
                        ));
                        return;
                    }
                }
            }
        }
    }

    private void scanPlayers(List<EggFoundEntry> results) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.DRAGON_EGG) {
                    results.add(new EggFoundEntry(
                            EggLocationType.PLAYER_INVENTORY,
                            null, 0, 0, 0,
                            null, player.getUniqueId(), null, player.getName()
                    ));
                    return;
                }
            }
        }
    }

    private boolean matchesTracker(EggFoundEntry egg) {
        if (egg.type != tracker.getType()) return false;

        switch (egg.type) {
            case BLOCK:
            case CONTAINER:
                return egg.worldName != null
                        && egg.worldName.equals(tracker.getWorldName())
                        && egg.x == tracker.getX()
                        && egg.y == tracker.getY()
                        && egg.z == tracker.getZ();
            case ITEM_ENTITY:
            case ITEM_FRAME:
                if (egg.entityUUID != null && tracker.getEntityUUID() != null) {
                    return egg.entityUUID.equals(tracker.getEntityUUID());
                }
                return egg.worldName != null
                        && egg.worldName.equals(tracker.getWorldName())
                        && Math.abs(egg.x - tracker.getX()) <= 2
                        && Math.abs(egg.y - tracker.getY()) <= 2
                        && Math.abs(egg.z - tracker.getZ()) <= 2;
            case PLAYER_INVENTORY:
                return egg.playerUUID != null
                        && egg.playerUUID.equals(tracker.getPlayerUUID());
            default:
                return false;
        }
    }

    private void updateTrackerFromScan(EggFoundEntry egg) {
        switch (egg.type) {
            case BLOCK:
                World w = Bukkit.getWorld(egg.worldName);
                if (w != null) tracker.setBlockLocation(w, egg.x, egg.y, egg.z);
                break;
            case ITEM_ENTITY:
                World w2 = Bukkit.getWorld(egg.worldName);
                if (w2 != null) tracker.setItemEntityLocation(w2, egg.x, egg.y, egg.z, egg.entityUUID);
                break;
            case PLAYER_INVENTORY:
                if (egg.playerUUID != null) tracker.setPlayerInventory(egg.playerUUID);
                break;
            case CONTAINER:
                World w3 = Bukkit.getWorld(egg.worldName);
                if (w3 != null) tracker.setContainerLocation(w3, egg.x, egg.y, egg.z, egg.containerType);
                break;
            case ITEM_FRAME:
                World w4 = Bukkit.getWorld(egg.worldName);
                if (w4 != null) tracker.setItemFrameLocation(w4, egg.x, egg.y, egg.z, egg.entityUUID);
                break;
        }
    }

    private void removeEgg(EggFoundEntry egg) {
        switch (egg.type) {
            case BLOCK: {
                World w = Bukkit.getWorld(egg.worldName);
                if (w != null && w.isChunkLoaded(egg.x >> 4, egg.z >> 4)) {
                    Block block = w.getBlockAt(egg.x, egg.y, egg.z);
                    if (block.getType() == Material.DRAGON_EGG) {
                        block.setType(Material.AIR);
                    }
                }
                break;
            }
            case ITEM_ENTITY: {
                if (egg.entityUUID != null) {
                    Entity entity = Bukkit.getEntity(egg.entityUUID);
                    if (entity instanceof Item item
                            && item.getItemStack().getType() == Material.DRAGON_EGG) {
                        entity.remove();
                    }
                }
                break;
            }
            case PLAYER_INVENTORY: {
                if (egg.playerUUID != null) {
                    Player player = Bukkit.getPlayer(egg.playerUUID);
                    if (player != null && player.isOnline()) {
                        player.getInventory().remove(Material.DRAGON_EGG);
                    }
                }
                break;
            }
            case CONTAINER: {
                World w = Bukkit.getWorld(egg.worldName);
                if (w != null && w.isChunkLoaded(egg.x >> 4, egg.z >> 4)) {
                    Block block = w.getBlockAt(egg.x, egg.y, egg.z);
                    if (block.getState() instanceof InventoryHolder holder) {
                        holder.getInventory().remove(Material.DRAGON_EGG);
                    }
                }
                break;
            }
            case ITEM_FRAME: {
                if (egg.entityUUID != null) {
                    Entity entity = Bukkit.getEntity(egg.entityUUID);
                    if (entity instanceof ItemFrame frame
                            && frame.getItem().getType() == Material.DRAGON_EGG) {
                        frame.setItem(null);
                    }
                }
                break;
            }
        }
    }

    private static class EggFoundEntry {
        final EggLocationType type;
        final String worldName;
        final int x, y, z;
        final java.util.UUID entityUUID;
        final java.util.UUID playerUUID;
        final String containerType;
        final String playerName;

        EggFoundEntry(EggLocationType type, String worldName, int x, int y, int z,
                      java.util.UUID entityUUID, java.util.UUID playerUUID,
                      String containerType, String playerName) {
            this.type = type;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.entityUUID = entityUUID;
            this.playerUUID = playerUUID;
            this.containerType = containerType;
            this.playerName = playerName;
        }

        String getDescription() {
            switch (type) {
                case BLOCK:
                    return "方块(" + worldName + "," + x + "," + y + "," + z + ")";
                case ITEM_ENTITY:
                    return "掉落物(" + worldName + "," + x + "," + y + "," + z + ")";
                case PLAYER_INVENTORY:
                    return "玩家(" + (playerName != null ? playerName : playerUUID) + ")";
                case CONTAINER:
                    return "容器(" + worldName + "," + x + "," + y + "," + z + ") " + containerType;
                case ITEM_FRAME:
                    return "展示框(" + worldName + "," + x + "," + y + "," + z + ")";
                default:
                    return "未知";
            }
        }
    }
}
