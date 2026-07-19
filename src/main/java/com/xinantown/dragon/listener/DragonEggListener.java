package com.xinantown.dragon.listener;

import com.xinantown.dragon.DragonEggTracker;
import com.xinantown.dragon.EggLocationType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

/**
 * Event listener that enforces the single dragon egg rule
 * and updates the egg tracker in real time.
 */
public class DragonEggListener implements Listener {

    private final DragonEggTracker tracker;
    private final Logger logger;

    public DragonEggListener(DragonEggTracker tracker, Logger logger) {
        this.tracker = tracker;
        this.logger = logger;
    }

    // ========== Block Events ==========

    /**
     * When a dragon egg block is placed, only allow if no egg already exists.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.DRAGON_EGG) return;

        if (tracker.isTracked() && tracker.verify()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c全服已存在一个龙蛋，不能放置第二个！");
            logger.info("Blocked duplicate dragon egg placement by " + event.getPlayer().getName());
        } else {
            // First egg or old egg no longer exists
            Block block = event.getBlock();
            tracker.setBlockLocation(block.getWorld(), block.getX(), block.getY(), block.getZ());
            logger.info("Dragon egg placed at " + formatLoc(block.getLocation()));
        }
    }

    /**
     * When a dragon egg block is broken, clear the tracker (item spawn will update it).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.DRAGON_EGG) return;

        if (tracker.getType() == EggLocationType.BLOCK
                && isSameBlock(event.getBlock(), tracker.getWorldName(), tracker.getX(), tracker.getY(), tracker.getZ())) {
            // Clear tracker; the dropped item will be picked up by ItemSpawnEvent
            tracker.clear();
            logger.info("Dragon egg block broken at " + formatLoc(event.getBlock().getLocation()));
        }
    }

    /**
     * Handle dragon egg teleport (EntityChangeBlockEvent when falling block lands).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getTo() != Material.DRAGON_EGG) return;

        Block block = event.getBlock();
        World world = block.getWorld();

        if (tracker.isTracked()) {
            // This is the egg teleporting
            // If another egg already exists at destination, cancel
            if (tracker.getType() == EggLocationType.BLOCK
                    && !isSameBlock(block, tracker.getWorldName(), tracker.getX(), tracker.getY(), tracker.getZ())) {
                // Check if there's already a dragon egg at the destination (not the tracked one)
                // If so, cancel to prevent duplicates
                if (world.getBlockAt(block.getX(), block.getY(), block.getZ()).getType() == Material.DRAGON_EGG) {
                    // Egg already at destination that isn't the tracked one
                    // This shouldn't normally happen; let it through and scanner will fix
                    logger.warning("Possible duplicate dragon egg after teleport at " + formatLoc(block.getLocation()));
                }
            }
            // Update tracker to new teleport location
            tracker.setBlockLocation(world, block.getX(), block.getY(), block.getZ());
        } else {
            // New egg appeared via falling block (shouldn't normally happen without existing egg)
            tracker.setBlockLocation(world, block.getX(), block.getY(), block.getZ());
            logger.info("Dragon egg appeared via falling block at " + formatLoc(block.getLocation()));
        }
    }

    // ========== Item Events ==========

    /**
     * When a dragon egg item entity spawns, update tracker.
     * If a tracked egg already exists, remove the new item.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (item.getType() != Material.DRAGON_EGG) return;

        Item entity = event.getEntity();

        if (tracker.isTracked() && tracker.verify()) {
            // Already have a tracked egg, remove this new one
            event.setCancelled(true);
            logger.info("Prevented duplicate dragon egg item spawn at " + formatLoc(entity.getLocation()));
            return;
        }

        // If tracker is NONE or old egg was lost, this becomes the new egg
        if (!tracker.isTracked() || !tracker.verify()) {
            tracker.setItemEntityLocation(
                    entity.getWorld(),
                    entity.getLocation().getBlockX(),
                    entity.getLocation().getBlockY(),
                    entity.getLocation().getBlockZ(),
                    entity.getUniqueId()
            );
            logger.info("Dragon egg item tracked at " + formatLoc(entity.getLocation()));
        }
    }

    /**
     * When a player picks up a dragon egg, update tracker to that player.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (item.getType() != Material.DRAGON_EGG) return;

        // Only track if this is the known egg
        if (tracker.getType() == EggLocationType.ITEM_ENTITY
                && tracker.getEntityUUID() != null
                && tracker.getEntityUUID().equals(event.getItem().getUniqueId())) {
            tracker.setPlayerInventory(player.getUniqueId());
            logger.info("Dragon egg picked up by " + player.getName());
        } else if (!tracker.isTracked() || !tracker.verify()) {
            // Egg was not tracked, now it is
            tracker.setPlayerInventory(player.getUniqueId());
            logger.info("New dragon egg tracked, picked up by " + player.getName());
        } else {
            // Someone picked up a second egg - remove it
            // Schedule removal after pickup completes
            event.getItem().remove();
            logger.info("Removed duplicate dragon egg picked up by " + player.getName());
        }
    }

    /**
     * When a player drops a dragon egg, update tracker to item entity.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.getType() != Material.DRAGON_EGG) return;

        if (tracker.getType() == EggLocationType.PLAYER_INVENTORY
                && tracker.getPlayerUUID() != null
                && tracker.getPlayerUUID().equals(event.getPlayer().getUniqueId())) {
            // Don't set yet - ItemSpawnEvent will fire right after and handle it
            tracker.clear();
            logger.info("Dragon egg dropped by " + event.getPlayer().getName());
        }
    }

    // ========== Player Interact ==========

    /**
     * Handle right-click on dragon egg (triggers teleport).
     * EntityChangeBlockEvent handles the actual location update.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.DRAGON_EGG) return;

        // The teleport will be handled by EntityChangeBlockEvent
        // Nothing to do here except log
        logger.fine("Player " + event.getPlayer().getName() + " interacted with dragon egg at " + formatLoc(clicked.getLocation()));
    }

    // ========== Inventory Events ==========

    /**
     * Track dragon egg movement in inventories.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null && event.getCursor() == null) return;

        boolean currentIsEgg = event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.DRAGON_EGG;
        boolean cursorIsEgg = event.getCursor() != null
                && event.getCursor().getType() == Material.DRAGON_EGG;
        boolean hotbarIsEgg = false;
        int hotbarSlot = event.getHotbarButton();
        if (hotbarSlot >= 0 && event.getWhoClicked() instanceof Player player) {
            ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
            hotbarIsEgg = hotbarItem != null && hotbarItem.getType() == Material.DRAGON_EGG;
        }

        if (!currentIsEgg && !cursorIsEgg && !hotbarIsEgg) return;

        // Schedule a delayed check to update tracker after inventory changes settle
        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(
                        com.xinantown.dragon.DragonEggGuard.class),
                () -> updateTrackerFromInventory(event.getInventory(), event.getView().getTopInventory())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        for (ItemStack item : event.getNewItems().values()) {
            if (item.getType() == Material.DRAGON_EGG) {
                org.bukkit.Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getPlugin(
                                com.xinantown.dragon.DragonEggGuard.class),
                        () -> updateTrackerFromInventory(event.getInventory(), event.getView().getTopInventory())
                );
                return;
            }
        }
    }

    /**
     * Handle hopper/dropper moving dragon eggs between inventories.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.getItem().getType() != Material.DRAGON_EGG) return;

        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(
                        com.xinantown.dragon.DragonEggGuard.class),
                () -> updateTrackerFromInventory(event.getSource(), event.getDestination())
        );
    }

    /**
     * After inventory changes, update tracker to reflect where the egg ended up.
     */
    private void updateTrackerFromInventory(Inventory... inventories) {
        for (Inventory inv : inventories) {
            for (ItemStack item : inv.getContents()) {
                if (item != null && item.getType() == Material.DRAGON_EGG) {
                    InventoryHolder holder = inv.getHolder();
                    if (holder instanceof Player player) {
                        tracker.setPlayerInventory(player.getUniqueId());
                    } else if (holder instanceof org.bukkit.block.BlockState blockState) {
                        String containerType = blockState.getType().name();
                        tracker.setContainerLocation(
                                blockState.getWorld(),
                                blockState.getX(),
                                blockState.getY(),
                                blockState.getZ(),
                                containerType
                        );
                    } else if (holder instanceof org.bukkit.entity.Entity entity) {
                        // Entity-based container (e.g., chest minecart)
                        if (entity instanceof org.bukkit.entity.minecart.StorageMinecart) {
                            tracker.setContainerLocation(
                                    entity.getWorld(),
                                    entity.getLocation().getBlockX(),
                                    entity.getLocation().getBlockY(),
                                    entity.getLocation().getBlockZ(),
                                    "运输矿车"
                            );
                        } else if (entity instanceof org.bukkit.entity.minecart.HopperMinecart) {
                            tracker.setContainerLocation(
                                    entity.getWorld(),
                                    entity.getLocation().getBlockX(),
                                    entity.getLocation().getBlockY(),
                                    entity.getLocation().getBlockZ(),
                                    "漏斗矿车"
                            );
                        }
                    } else {
                        // Fallback: holder with no location
                        tracker.setContainerLocation(
                                org.bukkit.Bukkit.getWorlds().get(0),
                                0, 0, 0,
                                holder != null ? holder.getClass().getSimpleName() : "未知容器"
                        );
                    }
                    return;
                }
            }
        }
    }

    // ========== Hanging (Item Frame) Events ==========

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getEntity().getType() != EntityType.ITEM_FRAME
                && event.getEntity().getType() != EntityType.GLOW_ITEM_FRAME) return;

        ItemFrame frame = (ItemFrame) event.getEntity();
        ItemStack item = frame.getItem();
        if (item.getType() != Material.DRAGON_EGG) return;

        // Check if a dragon egg is already being placed in the frame
        if (event.getPlayer() != null) {
            ItemStack handItem = event.getPlayer().getInventory().getItemInMainHand();
            if (handItem.getType() != Material.DRAGON_EGG) {
                handItem = event.getPlayer().getInventory().getItemInOffHand();
            }
            if (handItem.getType() == Material.DRAGON_EGG) {
                if (tracker.isTracked() && tracker.verify()) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§c全服已存在一个龙蛋，不能放置第二个！");
                    logger.info("Blocked duplicate dragon egg in item frame by " + event.getPlayer().getName());
                    return;
                }
            }
        }

        // New egg in item frame
        tracker.setItemFrameLocation(
                frame.getWorld(),
                frame.getLocation().getBlockX(),
                frame.getLocation().getBlockY(),
                frame.getLocation().getBlockZ(),
                frame.getUniqueId()
        );
        logger.info("Dragon egg placed in item frame at " + formatLoc(frame.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.getEntity().getType() != EntityType.ITEM_FRAME
                && event.getEntity().getType() != EntityType.GLOW_ITEM_FRAME) return;

        ItemFrame frame = (ItemFrame) event.getEntity();
        if (frame.getItem().getType() != Material.DRAGON_EGG) return;

        if (tracker.getType() == EggLocationType.ITEM_FRAME
                && tracker.getEntityUUID() != null
                && tracker.getEntityUUID().equals(frame.getUniqueId())) {
            // Egg will drop; ItemSpawnEvent will handle re-tracking
            tracker.clear();
            logger.info("Dragon egg item frame broken at " + formatLoc(frame.getLocation()));
        }
    }

    // ========== Crafting Prevention ==========

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (event.getInventory().getMatrix() == null) return;

        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item != null && item.getType() == Material.DRAGON_EGG) {
                event.getInventory().setResult(null);
                // Notify nearby players
                for (Entity entity : event.getInventory().getViewers().stream()
                        .filter(e -> e instanceof Player).toList()) {
                    ((Player) entity).sendMessage("§c龙蛋不能用于合成！");
                }
                return;
            }
        }
    }

    // ========== Join/Quit ==========

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // If the tracked egg is in a player's inventory, verify it's still there
        if (tracker.getType() == EggLocationType.PLAYER_INVENTORY
                && tracker.getPlayerUUID() != null
                && tracker.getPlayerUUID().equals(event.getPlayer().getUniqueId())) {
            // Player rejoined - verify the egg is still in their inventory
            if (!tracker.verify()) {
                logger.warning("Tracked dragon egg not found in " + event.getPlayer().getName() + "'s inventory on join");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // If the player carrying the egg quits, keep tracker pointing to them
        // The scanner will handle if the egg disappears
        if (tracker.getType() == EggLocationType.PLAYER_INVENTORY
                && tracker.getPlayerUUID() != null
                && tracker.getPlayerUUID().equals(event.getPlayer().getUniqueId())) {
            logger.info("Player " + event.getPlayer().getName() + " carrying dragon egg disconnected");
        }
    }

    // ========== Helpers ==========

    private boolean isSameBlock(Block block, String worldName, int x, int y, int z) {
        return block.getWorld().getName().equals(worldName)
                && block.getX() == x
                && block.getY() == y
                && block.getZ() == z;
    }

    private String formatLoc(Location loc) {
        return loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }
}
