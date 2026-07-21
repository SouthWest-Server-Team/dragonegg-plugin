package com.xinantown.dragon;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Tracks the unique dragon egg's location across the server.
 * Holds a single polymorphic EggLocation and handles YAML persistence.
 */
public class DragonEggTracker {

    private final DragonEggGuard plugin;
    private final File dataFile;
    private EggLocation current;

    public DragonEggTracker(DragonEggGuard plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    // ===== State access =====

    public void set(EggLocation location) {
        this.current = location;
        save();
    }

    public EggLocation getCurrent() {
        return current;
    }

    public void clear() {
        this.current = null;
        save();
    }

    public boolean isTracked() {
        return current != null;
    }

    public boolean verify() {
        if (current == null) return false;
        try {
            return current.verify();
        } catch (Exception e) {
            return false;
        }
    }

    public void removeEgg() {
        if (current != null) {
            try { current.remove(); } catch (Exception ignored) {}
            clear();
        }
    }

    public String getLocationDescription() {
        if (current == null) {
            return "龙蛋不存在，使用 /dragon give <player> 创建";
        }
        if (!verify()) {
            return "龙蛋可能已丢失（记录位置无效），使用 /dragon scan 重新扫描";
        }
        return current.describe();
    }

    public String getDetailedInfo() {
        if (current == null) return "§e当前没有追踪的龙蛋";
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== 龙蛋详细信息 ===\n");
        sb.append("§e类型: §f").append(current.typeName()).append("\n");
        current.appendDetailsTo(sb);
        sb.append("§e已验证存在: §f").append(verify() ? "是" : "否").append("\n");
        return sb.toString();
    }

    // ===== Persistence =====

    public void load() {
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

        // Try new format: top-level keys from EggLocation.serialize()
        if (cfg.contains("type")) {
            try {
                this.current = EggLocation.deserialize(cfg.getValues(false));
                return;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load data.yml in new format, trying legacy: " + e.getMessage());
            }
        }

        // Fall back to legacy format: nested under "egg."
        if (cfg.contains("egg.type")) {
            loadLegacy(cfg);
        }
    }

    private void loadLegacy(FileConfiguration cfg) {
        String typeStr = cfg.getString("egg.type", "NONE");
        if ("NONE".equalsIgnoreCase(typeStr)) {
            this.current = null;
            return;
        }

        var map = new java.util.HashMap<String, Object>();
        map.put("type", typeStr.toUpperCase());
        map.put("world", cfg.getString("egg.world"));
        map.put("x", cfg.getInt("egg.x"));
        map.put("y", cfg.getInt("egg.y"));
        map.put("z", cfg.getInt("egg.z"));

        String puid = cfg.getString("egg.player-uuid");
        if (puid != null && !puid.isEmpty()) map.put("player-uuid", puid);
        String euid = cfg.getString("egg.entity-uuid");
        if (euid != null && !euid.isEmpty()) map.put("entity-uuid", euid);
        String containerType = cfg.getString("egg.container-type");
        if (containerType != null) map.put("container-type", containerType);

        this.current = EggLocation.deserialize(map);
    }

    public void save() {
        if (current == null) {
            // Clear persisted state — delete data.yml so stale egg data isn't retained
            if (dataFile.exists()) dataFile.delete();
            return;
        }
        Map<String, Object> map = current.serialize();
        FileConfiguration cfg = new YamlConfiguration();
        for (var entry : map.entrySet()) {
            cfg.set(entry.getKey(), entry.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save data.yml: " + e.getMessage());
        }
    }
}
