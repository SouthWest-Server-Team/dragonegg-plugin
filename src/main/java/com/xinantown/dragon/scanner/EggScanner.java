package com.xinantown.dragon.scanner;

import com.xinantown.dragon.*;
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
                SCAN_INTERVAL_TICKS,
                SCAN_INTERVAL_TICKS
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
     * tracker location via the polymorphic EggLocation, only falls back
     * to full scan if the egg has moved.
     */
    public void scanNow(CommandSender reporter) {
        if (tracker.isTracked() && verifyKnownEgg()) {
            if (reporter != null) {
                reporter.sendMessage("§a扫描完成：龙蛋仍在追踪位置，无需全量扫描");
            }
            return;
        }
        performFullScan(reporter);
    }

    private boolean verifyKnownEgg() {
        try {
            EggLocation current = tracker.getCurrent();
            return current != null && current.verify();
        } catch (Exception e) {
            return false;
        }
    }

    private void performFullScan(CommandSender reporter) {
        List<EggLocation> allEggs = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanBlocks(chunk, allEggs);
                scanEntities(chunk, allEggs);
                scanContainers(chunk, allEggs);
            }
        }
        scanPlayers(allEggs);

        int total = allEggs.size();

        if (total == 0) {
            if (tracker.isTracked()) tracker.clear();
            if (reporter != null) reporter.sendMessage("§e扫描完成：未发现龙蛋，追踪器已清空");
            return;
        }

        if (total == 1) {
            EggLocation egg = allEggs.get(0);
            tracker.set(egg);
            if (reporter != null) reporter.sendMessage("§a扫描完成：发现1个龙蛋 - " + egg.describe());
            return;
        }

        // Multiple eggs: deduplicate
        logger.warning("Found " + total + " dragon eggs! Removing duplicates...");

        EggLocation current = tracker.getCurrent();
        EggLocation keeper = null;
        List<EggLocation> duplicates = new ArrayList<>();

        for (EggLocation egg : allEggs) {
            if (current != null && egg.matches(current)) {
                keeper = egg;
            } else {
                duplicates.add(egg);
            }
        }

        if (keeper == null) {
            keeper = allEggs.get(0);
            duplicates = allEggs.subList(1, allEggs.size());
        }

        for (EggLocation dup : duplicates) {
            try { dup.remove(); } catch (Exception ignored) {}
            logger.info("Removed duplicate egg: " + dup.describe());
        }

        tracker.set(keeper);

        String resultMsg = "§a扫描完成：发现" + total + "个龙蛋，已保留1个，删除" + duplicates.size() + "个";
        if (reporter != null) reporter.sendMessage(resultMsg);
        if (!duplicates.isEmpty()) logger.warning(resultMsg);
    }

    private void scanBlocks(Chunk chunk, List<EggLocation> results) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    if (chunk.getBlock(x, y, z).getType() == Material.DRAGON_EGG) {
                        results.add(new BlockLocation(world.getName(), chunkX + x, y, chunkZ + z));
                        return;
                    }
                }
            }
        }
    }

    private void scanEntities(Chunk chunk, List<EggLocation> results) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Item item && item.getItemStack().getType() == Material.DRAGON_EGG) {
                results.add(new ItemEntityLocation(
                        entity.getWorld().getName(),
                        entity.getLocation().getBlockX(),
                        entity.getLocation().getBlockY(),
                        entity.getLocation().getBlockZ(),
                        entity.getUniqueId()));
                return;
            }
            if (entity instanceof ItemFrame frame && frame.getItem().getType() == Material.DRAGON_EGG) {
                results.add(new ItemFrameLocation(
                        entity.getWorld().getName(),
                        entity.getLocation().getBlockX(),
                        entity.getLocation().getBlockY(),
                        entity.getLocation().getBlockZ(),
                        entity.getUniqueId()));
                return;
            }
        }
    }

    private void scanContainers(Chunk chunk, List<EggLocation> results) {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof InventoryHolder holder && !(holder instanceof Player)) {
                for (ItemStack item : holder.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.DRAGON_EGG) {
                        results.add(new ContainerLocation(
                                state.getWorld().getName(),
                                state.getX(), state.getY(), state.getZ(),
                                state.getType().name()));
                        return;
                    }
                }
            }
        }
    }

    private void scanPlayers(List<EggLocation> results) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == Material.DRAGON_EGG) {
                    results.add(new PlayerInventoryLocation(player.getUniqueId()));
                    return;
                }
            }
        }
    }
}
