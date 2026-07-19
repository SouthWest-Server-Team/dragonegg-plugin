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
 * Runs every 30 seconds and can be triggered manually via /dragon scan.
 */
public class EggScanner implements Runnable {

    private final DragonEggTracker tracker;
    private final Logger logger;
    private int taskId = -1;

    public EggScanner(DragonEggTracker tracker, Logger logger) {
        this.tracker = tracker;
        this.logger = logger;
    }

    /**
     * Start the scheduled scanning task.
     */
    public void start() {
        // Run every 30 seconds = 600 ticks
        taskId = Bukkit.getScheduler().runTaskTimer(
                Bukkit.getPluginManager().getPlugin("DragonEggGuard"),
                this,
                600L,  // 30 seconds initial delay
                600L   // every 30 seconds
        ).getTaskId();
        logger.info("Egg scanner started (interval: 30s)");
    }

    /**
     * Stop the scheduled scanning task.
     */
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
     * Perform a full scan immediately.
     * @param reporter optional CommandSender to receive scan results
     */
    public void scanNow(CommandSender reporter) {
        List<EggFoundEntry> allEggs = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                // Scan blocks
                scanBlocks(chunk, allEggs);

                // Scan entities (item frames, dropped items)
                scanEntities(chunk, allEggs);

                // Scan containers
                scanContainers(chunk, allEggs);
            }
        }

        // Scan online players' inventories
        scanPlayers(allEggs);

        int total = allEggs.size();

        if (total == 0) {
            // No eggs found
            if (tracker.isTracked()) {
                tracker.clear();
            }
            if (reporter != null) {
                reporter.sendMessage("§e扫描完成：未发现龙蛋，追踪器已清空");
            }
            return;
        }

        if (total == 1) {
            // Exactly one egg: ensure tracker matches
            EggFoundEntry egg = allEggs.get(0);
            updateTrackerFromScan(egg);
            if (reporter != null) {
                reporter.sendMessage("§a扫描完成：发现1个龙蛋 - " + egg.getDescription());
            }
            return;
        }

        // Multiple eggs found: keep the one matching the tracker, remove others
        logger.warning("Found " + total + " dragon eggs! Removing duplicates...");

        EggFoundEntry keeper = null;
        List<EggFoundEntry> duplicates = new ArrayList<>();

        // Try to find the egg that matches the tracker
        for (EggFoundEntry egg : allEggs) {
            if (matchesTracker(egg)) {
                keeper = egg;
            } else {
                duplicates.add(egg);
            }
        }

        if (keeper == null) {
            // None match tracker; keep the first one found, remove rest
            keeper = allEggs.get(0);
            duplicates = allEggs.subList(1, allEggs.size());
            logger.info("No egg matched tracker; keeping first found: " + keeper.getDescription());
        }

        // Remove duplicates
        for (EggFoundEntry dup : duplicates) {
            removeEgg(dup);
            logger.info("Removed duplicate egg: " + dup.getDescription());
        }

        // Update tracker to the kept egg
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
     * Scan all DRAGON_EGG blocks in a chunk.
     */
    private void scanBlocks(Chunk chunk, List<EggFoundEntry> results) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == Material.DRAGON_EGG) {
                        results.add(new EggFoundEntry(
                                EggLocationType.BLOCK,
                                world.getName(),
                                chunkX + x, y, chunkZ + z,
                                null, null, null, null
                        ));
                    }
                }
            }
        }
    }

    /**
     * Scan entities (Item, ItemFrame) in a chunk.
     */
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
                }
            }
        }
    }

    /**
     * Scan container blocks in a chunk for dragon eggs.
     */
    private void scanContainers(Chunk chunk, List<EggFoundEntry> results) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof InventoryHolder holder) {
                // Skip player inventories (handled separately)
                if (holder instanceof Player) continue;

                for (ItemStack item : holder.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.DRAGON_EGG) {
                        String containerType = state.getType().name();
                        results.add(new EggFoundEntry(
                                EggLocationType.CONTAINER,
                                state.getWorld().getName(),
                                state.getX(), state.getY(), state.getZ(),
                                null, null, containerType, null
                        ));
                        break; // One egg per container is enough for counting
                    }
                }
            }
        }
    }

    /**
     * Scan online players' inventories.
     */
    private void scanPlayers(List<EggFoundEntry> results) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.DRAGON_EGG) {
                    results.add(new EggFoundEntry(
                            EggLocationType.PLAYER_INVENTORY,
                            null, 0, 0, 0,
                            null, player.getUniqueId(), null, player.getName()
                    ));
                    break;
                }
            }
        }
    }

    /**
     * Check if a scanned egg entry matches the current tracker.
     */
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

    /**
     * Update tracker to match a scanned egg entry.
     */
    private void updateTrackerFromScan(EggFoundEntry egg) {
        switch (egg.type) {
            case BLOCK:
                World w = Bukkit.getWorld(egg.worldName);
                if (w != null) {
                    tracker.setBlockLocation(w, egg.x, egg.y, egg.z);
                }
                break;
            case ITEM_ENTITY:
                World w2 = Bukkit.getWorld(egg.worldName);
                if (w2 != null) {
                    tracker.setItemEntityLocation(w2, egg.x, egg.y, egg.z, egg.entityUUID);
                }
                break;
            case PLAYER_INVENTORY:
                if (egg.playerUUID != null) {
                    tracker.setPlayerInventory(egg.playerUUID);
                }
                break;
            case CONTAINER:
                World w3 = Bukkit.getWorld(egg.worldName);
                if (w3 != null) {
                    tracker.setContainerLocation(w3, egg.x, egg.y, egg.z, egg.containerType);
                }
                break;
            case ITEM_FRAME:
                World w4 = Bukkit.getWorld(egg.worldName);
                if (w4 != null) {
                    tracker.setItemFrameLocation(w4, egg.x, egg.y, egg.z, egg.entityUUID);
                }
                break;
        }
    }

    /**
     * Remove a dragon egg from the given entry.
     */
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

    /**
     * Internal data class for scan results.
     */
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
