package com.xinantown.dragon.listener;

import com.xinantown.dragon.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
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
import org.bukkit.event.inventory.*;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.DRAGON_EGG) return;

        EggLocation current = tracker.getCurrent();
        if (current != null && current.verify()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c全服已存在一个龙蛋，不能放置第二个！");
            logger.info("Blocked duplicate dragon egg placement by " + event.getPlayer().getName());
        } else {
            Block block = event.getBlock();
            tracker.set(new BlockLocation(block.getWorld().getName(), block.getX(), block.getY(), block.getZ()));
            logger.info("Dragon egg placed at " + formatLoc(block.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.DRAGON_EGG) return;

        EggLocation current = tracker.getCurrent();
        if (current instanceof BlockLocation b
                && b.worldName().equals(event.getBlock().getWorld().getName())
                && b.x() == event.getBlock().getX()
                && b.y() == event.getBlock().getY()
                && b.z() == event.getBlock().getZ()) {
            tracker.clear();
            logger.info("Dragon egg block broken at " + formatLoc(event.getBlock().getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getTo() != Material.DRAGON_EGG) return;

        Block block = event.getBlock();
        World world = block.getWorld();

        EggLocation current = tracker.getCurrent();
        if (current != null) {
            if (current instanceof BlockLocation b
                    && !(b.worldName().equals(world.getName()) && b.x() == block.getX() && b.y() == block.getY() && b.z() == block.getZ())) {
                if (world.getBlockAt(block.getX(), block.getY(), block.getZ()).getType() == Material.DRAGON_EGG) {
                    logger.warning("Possible duplicate dragon egg after teleport at " + formatLoc(block.getLocation()));
                }
            }
            tracker.set(new BlockLocation(world.getName(), block.getX(), block.getY(), block.getZ()));
        } else {
            tracker.set(new BlockLocation(world.getName(), block.getX(), block.getY(), block.getZ()));
            logger.info("Dragon egg appeared via falling block at " + formatLoc(block.getLocation()));
        }
    }

    // ========== Item Events ==========

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        ItemStack item = event.getEntity().getItemStack();
        if (item.getType() != Material.DRAGON_EGG) return;

        Item entity = event.getEntity();
        EggLocation current = tracker.getCurrent();

        if (current != null && current.verify()) {
            event.setCancelled(true);
            logger.info("Prevented duplicate dragon egg item spawn at " + formatLoc(entity.getLocation()));
            return;
        }

        if (current == null || !current.verify()) {
            tracker.set(new ItemEntityLocation(
                    entity.getWorld().getName(),
                    entity.getLocation().getBlockX(),
                    entity.getLocation().getBlockY(),
                    entity.getLocation().getBlockZ(),
                    entity.getUniqueId()));
            logger.info("Dragon egg item tracked at " + formatLoc(entity.getLocation()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (item.getType() != Material.DRAGON_EGG) return;

        EggLocation current = tracker.getCurrent();
        if (current instanceof ItemEntityLocation ie
                && ie.entityUUID() != null
                && ie.entityUUID().equals(event.getItem().getUniqueId())) {
            tracker.set(new PlayerInventoryLocation(player.getUniqueId()));
            logger.info("Dragon egg picked up by " + player.getName());
        } else if (current == null || !current.verify()) {
            tracker.set(new PlayerInventoryLocation(player.getUniqueId()));
            logger.info("New dragon egg tracked, picked up by " + player.getName());
        } else {
            event.getItem().remove();
            logger.info("Removed duplicate dragon egg picked up by " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.getType() != Material.DRAGON_EGG) return;

        EggLocation current = tracker.getCurrent();
        if (current instanceof PlayerInventoryLocation pi
                && pi.playerUUID() != null
                && pi.playerUUID().equals(event.getPlayer().getUniqueId())) {
            tracker.clear();
            logger.info("Dragon egg dropped by " + event.getPlayer().getName());
        }
    }

    // ========== Player Interact ==========

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.DRAGON_EGG) return;
        logger.fine("Player " + event.getPlayer().getName() + " interacted with dragon egg at " + formatLoc(clicked.getLocation()));
    }

    // ========== Inventory Events ==========

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

        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(DragonEggGuard.class),
                () -> updateTrackerFromInventory(event.getInventory(), event.getView().getTopInventory()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        for (ItemStack item : event.getNewItems().values()) {
            if (item.getType() == Material.DRAGON_EGG) {
                org.bukkit.Bukkit.getScheduler().runTask(
                        org.bukkit.plugin.java.JavaPlugin.getPlugin(DragonEggGuard.class),
                        () -> updateTrackerFromInventory(event.getInventory(), event.getView().getTopInventory()));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.getItem().getType() != Material.DRAGON_EGG) return;
        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getPlugin(DragonEggGuard.class),
                () -> updateTrackerFromInventory(event.getSource(), event.getDestination()));
    }

    private void updateTrackerFromInventory(Inventory... inventories) {
        for (Inventory inv : inventories) {
            for (ItemStack item : inv.getContents()) {
                if (item != null && item.getType() == Material.DRAGON_EGG) {
                    InventoryHolder holder = inv.getHolder();
                    if (holder instanceof Player player) {
                        tracker.set(new PlayerInventoryLocation(player.getUniqueId()));
                    } else if (holder instanceof org.bukkit.block.BlockState blockState) {
                        tracker.set(new ContainerLocation(
                                blockState.getWorld().getName(),
                                blockState.getX(), blockState.getY(), blockState.getZ(),
                                blockState.getType().name()));
                    } else if (holder instanceof org.bukkit.entity.minecart.StorageMinecart cart) {
                        tracker.set(new ContainerLocation(
                                cart.getWorld().getName(),
                                cart.getLocation().getBlockX(),
                                cart.getLocation().getBlockY(),
                                cart.getLocation().getBlockZ(),
                                "运输矿车"));
                    } else if (holder instanceof org.bukkit.entity.minecart.HopperMinecart cart) {
                        tracker.set(new ContainerLocation(
                                cart.getWorld().getName(),
                                cart.getLocation().getBlockX(),
                                cart.getLocation().getBlockY(),
                                cart.getLocation().getBlockZ(),
                                "漏斗矿车"));
                    } else {
                        tracker.set(new ContainerLocation(
                                org.bukkit.Bukkit.getWorlds().get(0).getName(),
                                0, 0, 0,
                                holder != null ? holder.getClass().getSimpleName() : "未知容器"));
                    }
                    return;
                }
            }
        }
    }

    // ========== Hanging Events ==========

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getEntity().getType() != EntityType.ITEM_FRAME
                && event.getEntity().getType() != EntityType.GLOW_ITEM_FRAME) return;

        ItemFrame frame = (ItemFrame) event.getEntity();
        if (frame.getItem().getType() != Material.DRAGON_EGG) return;

        if (event.getPlayer() != null) {
            ItemStack handItem = event.getPlayer().getInventory().getItemInMainHand();
            if (handItem.getType() != Material.DRAGON_EGG) {
                handItem = event.getPlayer().getInventory().getItemInOffHand();
            }
            if (handItem.getType() == Material.DRAGON_EGG) {
                EggLocation current = tracker.getCurrent();
                if (current != null && current.verify()) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§c全服已存在一个龙蛋，不能放置第二个！");
                    logger.info("Blocked duplicate dragon egg in item frame by " + event.getPlayer().getName());
                    return;
                }
            }
        }

        tracker.set(new ItemFrameLocation(
                frame.getWorld().getName(),
                frame.getLocation().getBlockX(),
                frame.getLocation().getBlockY(),
                frame.getLocation().getBlockZ(),
                frame.getUniqueId()));
        logger.info("Dragon egg placed in item frame at " + formatLoc(frame.getLocation()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.getEntity().getType() != EntityType.ITEM_FRAME
                && event.getEntity().getType() != EntityType.GLOW_ITEM_FRAME) return;

        ItemFrame frame = (ItemFrame) event.getEntity();
        if (frame.getItem().getType() != Material.DRAGON_EGG) return;

        EggLocation current = tracker.getCurrent();
        if (current instanceof ItemFrameLocation f
                && f.entityUUID() != null
                && f.entityUUID().equals(frame.getUniqueId())) {
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
        EggLocation current = tracker.getCurrent();
        if (current instanceof PlayerInventoryLocation pi
                && pi.playerUUID() != null
                && pi.playerUUID().equals(event.getPlayer().getUniqueId())) {
            if (!current.verify()) {
                logger.warning("Tracked dragon egg not found in " + event.getPlayer().getName() + "'s inventory on join");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        EggLocation current = tracker.getCurrent();
        if (current instanceof PlayerInventoryLocation pi
                && pi.playerUUID() != null
                && pi.playerUUID().equals(event.getPlayer().getUniqueId())) {
            logger.info("Player " + event.getPlayer().getName() + " carrying dragon egg disconnected");
        }
    }

    // ========== Helpers ==========

    private String formatLoc(Location loc) {
        return loc.getWorld().getName() + " (" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")";
    }
}
