package com.xinantown.dragon;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;

import java.util.Map;
import java.util.UUID;

public record ItemFrameLocation(String worldName, int x, int y, int z, UUID entityUUID) implements EggLocation {

    @Override public String typeName() { return "ITEM_FRAME"; }

    @Override
    public boolean verify() {
        if (entityUUID != null) {
            var entity = Bukkit.getEntity(entityUUID);
            if (entity instanceof ItemFrame frame
                    && frame.getItem().getType() == Material.DRAGON_EGG) {
                return true;
            }
        }
        // fallback: check nearby item frames
        var w = Bukkit.getWorld(worldName);
        if (w == null) return false;
        for (var e : w.getNearbyEntities(new org.bukkit.Location(w, x, y, z), 2, 2, 2)) {
            if (e instanceof ItemFrame frame && frame.getItem().getType() == Material.DRAGON_EGG) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void remove() {
        if (entityUUID != null) {
            var entity = Bukkit.getEntity(entityUUID);
            if (entity instanceof ItemFrame frame && frame.getItem().getType() == Material.DRAGON_EGG) {
                frame.setItem(null);
            }
        }
    }

    @Override
    public String describe() {
        return "龙蛋在 " + worldName + " (" + x + "," + y + "," + z + ") 的物品展示框中";
    }

    @Override
    public boolean matches(EggLocation other) {
        if (!(other instanceof ItemFrameLocation ie)) return false;
        if (entityUUID != null && ie.entityUUID != null && entityUUID.equals(ie.entityUUID)) return true;
        return worldName.equals(ie.worldName)
                && Math.abs(x - ie.x) <= 2
                && Math.abs(y - ie.y) <= 2
                && Math.abs(z - ie.z) <= 2;
    }

    @Override
    public void appendDetailsTo(StringBuilder sb) {
        sb.append("§e世界: §f").append(worldName).append("\n");
        sb.append("§e坐标: §f(").append(x).append(", ").append(y).append(", ").append(z).append(")\n");
        sb.append("§e实体UUID: §f").append(entityUUID).append("\n");
    }

    @Override
    public Map<String, Object> serialize() {
        return Map.of("type", "ITEM_FRAME", "world", worldName, "x", x, "y", y, "z", z,
                "entity-uuid", entityUUID != null ? entityUUID.toString() : "");
    }
}
