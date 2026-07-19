package com.xinantown.dragon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Tracks the unique dragon egg's location across the server.
 * Handles persistence via data.yml.
 */
public class DragonEggTracker {

    private final DragonEggGuard plugin;
    private final File dataFile;

    private EggLocationType type = EggLocationType.NONE;
    private String worldName;
    private int x, y, z;
    private UUID playerUUID;
    private UUID entityUUID;
    private String containerType;

    public DragonEggTracker(DragonEggGuard plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    /**
     * Load the tracker state from data.yml.
     */
    public void load() {
        if (!dataFile.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.contains("egg.type")) {
            return;
        }
        String typeStr = cfg.getString("egg.type", "NONE");
        try {
            this.type = EggLocationType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.type = EggLocationType.NONE;
        }
        this.worldName = cfg.getString("egg.world");
        this.x = cfg.getInt("egg.x");
        this.y = cfg.getInt("egg.y");
        this.z = cfg.getInt("egg.z");
        String puid = cfg.getString("egg.player-uuid");
        this.playerUUID = (puid != null && !puid.isEmpty()) ? UUID.fromString(puid) : null;
        String euid = cfg.getString("egg.entity-uuid");
        this.entityUUID = (euid != null && !euid.isEmpty()) ? UUID.fromString(euid) : null;
        this.containerType = cfg.getString("egg.container-type");
    }

    /**
     * Save the tracker state to data.yml.
     */
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("egg.type", type.name());
        cfg.set("egg.world", worldName);
        cfg.set("egg.x", x);
        cfg.set("egg.y", y);
        cfg.set("egg.z", z);
        cfg.set("egg.player-uuid", playerUUID != null ? playerUUID.toString() : null);
        cfg.set("egg.entity-uuid", entityUUID != null ? entityUUID.toString() : null);
        cfg.set("egg.container-type", containerType);
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save data.yml: " + e.getMessage());
        }
    }

    // ----- Setters -----

    public void setBlockLocation(World world, int x, int y, int z) {
        this.type = EggLocationType.BLOCK;
        this.worldName = world.getName();
        this.x = x;
        this.y = y;
        this.z = z;
        this.playerUUID = null;
        this.entityUUID = null;
        this.containerType = null;
        save();
    }

    public void setItemEntityLocation(World world, int x, int y, int z, UUID entityUUID) {
        this.type = EggLocationType.ITEM_ENTITY;
        this.worldName = world.getName();
        this.x = x;
        this.y = y;
        this.z = z;
        this.playerUUID = null;
        this.entityUUID = entityUUID;
        this.containerType = null;
        save();
    }

    public void setPlayerInventory(UUID playerUUID) {
        this.type = EggLocationType.PLAYER_INVENTORY;
        this.worldName = null;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.playerUUID = playerUUID;
        this.entityUUID = null;
        this.containerType = null;
        save();
    }

    public void setContainerLocation(World world, int x, int y, int z, String containerType) {
        this.type = EggLocationType.CONTAINER;
        this.worldName = world.getName();
        this.x = x;
        this.y = y;
        this.z = z;
        this.playerUUID = null;
        this.entityUUID = null;
        this.containerType = containerType;
        save();
    }

    public void setItemFrameLocation(World world, int x, int y, int z, UUID entityUUID) {
        this.type = EggLocationType.ITEM_FRAME;
        this.worldName = world.getName();
        this.x = x;
        this.y = y;
        this.z = z;
        this.playerUUID = null;
        this.entityUUID = entityUUID;
        this.containerType = null;
        save();
    }

    public void clear() {
        this.type = EggLocationType.NONE;
        this.worldName = null;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.playerUUID = null;
        this.entityUUID = null;
        this.containerType = null;
        save();
    }

    // ----- Getters -----

    public EggLocationType getType() {
        return type;
    }

    public String getWorldName() {
        return worldName;
    }

    public World getWorld() {
        return worldName != null ? Bukkit.getWorld(worldName) : null;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public UUID getEntityUUID() {
        return entityUUID;
    }

    public String getContainerType() {
        return containerType;
    }

    public Location getLocation() {
        World w = getWorld();
        if (w == null) return null;
        return new Location(w, x, y, z);
    }

    public boolean isTracked() {
        return type != EggLocationType.NONE;
    }

    /**
     * Verify that the tracked egg still exists at its recorded location.
     * Returns true if the egg is confirmed present.
     */
    public boolean verify() {
        if (type == EggLocationType.NONE) return false;

        switch (type) {
            case BLOCK: {
                World w = getWorld();
                if (w == null) return false;
                return w.getBlockAt(x, y, z).getType() == org.bukkit.Material.DRAGON_EGG;
            }
            case ITEM_ENTITY: {
                World w = getWorld();
                if (w == null) return false;
                if (entityUUID != null) {
                    org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUUID);
                    if (entity instanceof org.bukkit.entity.Item item) {
                        return item.getItemStack().getType() == org.bukkit.Material.DRAGON_EGG;
                    }
                }
                // Fallback: check for item entity at location
                for (org.bukkit.entity.Entity e : w.getNearbyEntities(new Location(w, x, y, z), 2, 2, 2)) {
                    if (e instanceof org.bukkit.entity.Item item
                            && item.getItemStack().getType() == org.bukkit.Material.DRAGON_EGG) {
                        return true;
                    }
                }
                return false;
            }
            case PLAYER_INVENTORY: {
                if (playerUUID == null) return false;
                Player player = Bukkit.getPlayer(playerUUID);
                if (player == null || !player.isOnline()) return false;
                for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == org.bukkit.Material.DRAGON_EGG) {
                        return true;
                    }
                }
                return false;
            }
            case CONTAINER: {
                World w = getWorld();
                if (w == null) return false;
                if (!w.isChunkLoaded(x >> 4, z >> 4)) return false;
                org.bukkit.block.Block block = w.getBlockAt(x, y, z);
                if (block.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
                    for (org.bukkit.inventory.ItemStack item : holder.getInventory().getContents()) {
                        if (item != null && item.getType() == org.bukkit.Material.DRAGON_EGG) {
                            return true;
                        }
                    }
                }
                return false;
            }
            case ITEM_FRAME: {
                World w = getWorld();
                if (w == null) return false;
                if (entityUUID != null) {
                    org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUUID);
                    if (entity instanceof org.bukkit.entity.ItemFrame frame
                            && frame.getItem().getType() == org.bukkit.Material.DRAGON_EGG) {
                        return true;
                    }
                }
                // Fallback: check nearby item frames
                for (org.bukkit.entity.Entity e : w.getNearbyEntities(new Location(w, x, y, z), 2, 2, 2)) {
                    if (e instanceof org.bukkit.entity.ItemFrame frame
                            && frame.getItem().getType() == org.bukkit.Material.DRAGON_EGG) {
                        return true;
                    }
                }
                return false;
            }
            default:
                return false;
        }
    }

    /**
     * Get a human-readable location description for /dragon locate.
     */
    public String getLocationDescription() {
        if (type == EggLocationType.NONE) {
            return "龙蛋不存在，使用 /dragon give <player> 创建";
        }

        if (!verify()) {
            return "龙蛋可能已丢失（记录位置无效），使用 /dragon scan 重新扫描";
        }

        switch (type) {
            case BLOCK:
                return "龙蛋方块在 " + worldName + " (" + x + "," + y + "," + z + ")";

            case ITEM_ENTITY:
                return "龙蛋掉落物在 " + worldName + " (" + x + "," + y + "," + z + ")";

            case PLAYER_INVENTORY: {
                Player player = playerUUID != null ? Bukkit.getPlayer(playerUUID) : null;
                String playerName = player != null ? player.getName() : (playerUUID != null ? playerUUID.toString() : "未知");
                if (player != null && player.isOnline()) {
                    org.bukkit.inventory.PlayerInventory inv = player.getInventory();
                    // Check main hand
                    if (inv.getItemInMainHand().getType() == org.bukkit.Material.DRAGON_EGG) {
                        return "龙蛋在玩家 " + playerName + " 的主手";
                    }
                    // Check off hand
                    if (inv.getItemInOffHand().getType() == org.bukkit.Material.DRAGON_EGG) {
                        return "龙蛋在玩家 " + playerName + " 的副手";
                    }
                    // Check inventory slots
                    org.bukkit.inventory.ItemStack[] contents = inv.getContents();
                    for (int i = 0; i < contents.length; i++) {
                        if (contents[i] != null && contents[i].getType() == org.bukkit.Material.DRAGON_EGG) {
                            return "龙蛋在玩家 " + playerName + " 的" + slotToDescription(i);
                        }
                    }
                }
                return "龙蛋在玩家 " + playerName + " 的背包中";
            }

            case CONTAINER:
                return "龙蛋在 " + worldName + " (" + x + "," + y + "," + z + ") 的"
                        + (containerType != null ? containerType : "容器") + " 中";

            case ITEM_FRAME:
                return "龙蛋在 " + worldName + " (" + x + "," + y + "," + z + ") 的物品展示框中";

            default:
                return "未知状态";
        }
    }

    /**
     * Convert inventory slot index to a Chinese description.
     */
    private String slotToDescription(int slot) {
        if (slot >= 0 && slot <= 8) {
            return "快捷栏第" + (slot + 1) + "格";
        } else if (slot >= 9 && slot <= 35) {
            return "物品栏第" + (slot - 8) + "格";
        } else if (slot == 36) {
            return "靴子槽";
        } else if (slot == 37) {
            return "护腿槽";
        } else if (slot == 38) {
            return "胸甲槽";
        } else if (slot == 39) {
            return "头盔槽";
        } else if (slot == 40) {
            return "副手";
        } else {
            return "物品栏第" + slot + "格";
        }
    }

    /**
     * Get the detailed info string for /dragon info.
     */
    public String getDetailedInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== 龙蛋详细信息 ===\n");
        sb.append("§e类型: §f").append(type).append("\n");
        if (worldName != null) {
            sb.append("§e世界: §f").append(worldName).append("\n");
            sb.append("§e坐标: §f(").append(x).append(", ").append(y).append(", ").append(z).append(")\n");
        }
        if (playerUUID != null) {
            Player player = Bukkit.getPlayer(playerUUID);
            sb.append("§e玩家UUID: §f").append(playerUUID).append("\n");
            if (player != null) {
                sb.append("§e玩家名: §f").append(player.getName()).append("\n");
            }
        }
        if (entityUUID != null) {
            sb.append("§e实体UUID: §f").append(entityUUID).append("\n");
        }
        if (containerType != null) {
            sb.append("§e容器类型: §f").append(containerType).append("\n");
        }
        sb.append("§e已验证存在: §f").append(verify() ? "是" : "否").append("\n");
        return sb.toString();
    }

    /**
     * Remove the tracked dragon egg from the world.
     */
    public void removeEgg() {
        if (type == EggLocationType.NONE) return;

        switch (type) {
            case BLOCK: {
                World w = getWorld();
                if (w != null && w.getBlockAt(x, y, z).getType() == org.bukkit.Material.DRAGON_EGG) {
                    w.getBlockAt(x, y, z).setType(org.bukkit.Material.AIR);
                }
                break;
            }
            case ITEM_ENTITY: {
                if (entityUUID != null) {
                    org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUUID);
                    if (entity instanceof org.bukkit.entity.Item item
                            && item.getItemStack().getType() == org.bukkit.Material.DRAGON_EGG) {
                        entity.remove();
                    }
                }
                break;
            }
            case PLAYER_INVENTORY: {
                if (playerUUID != null) {
                    Player player = Bukkit.getPlayer(playerUUID);
                    if (player != null && player.isOnline()) {
                        player.getInventory().remove(org.bukkit.Material.DRAGON_EGG);
                    }
                }
                break;
            }
            case CONTAINER: {
                World w = getWorld();
                if (w != null && w.isChunkLoaded(x >> 4, z >> 4)) {
                    org.bukkit.block.Block block = w.getBlockAt(x, y, z);
                    if (block.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
                        holder.getInventory().remove(org.bukkit.Material.DRAGON_EGG);
                    }
                }
                break;
            }
            case ITEM_FRAME: {
                if (entityUUID != null) {
                    org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUUID);
                    if (entity instanceof org.bukkit.entity.ItemFrame frame
                            && frame.getItem().getType() == org.bukkit.Material.DRAGON_EGG) {
                        frame.setItem(null);
                    }
                }
                break;
            }
        }
        clear();
    }
}
