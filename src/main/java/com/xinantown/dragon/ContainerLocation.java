package com.xinantown.dragon;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public record ContainerLocation(String worldName, int x, int y, int z, String containerType) implements EggLocation {

    @Override public String typeName() { return "CONTAINER"; }

    @Override
    public boolean verify() {
        var w = Bukkit.getWorld(worldName);
        if (w == null || !w.isChunkLoaded(x >> 4, z >> 4)) return false;
        var block = w.getBlockAt(x, y, z);
        if (block.getState() instanceof InventoryHolder holder) {
            return holder.getInventory().contains(Material.DRAGON_EGG);
        }
        return false;
    }

    @Override
    public void remove() {
        var w = Bukkit.getWorld(worldName);
        if (w != null && w.isChunkLoaded(x >> 4, z >> 4)) {
            var block = w.getBlockAt(x, y, z);
            if (block.getState() instanceof InventoryHolder holder) {
                holder.getInventory().remove(Material.DRAGON_EGG);
            }
        }
    }

    @Override
    public String describe() {
        return "龙蛋在 " + worldName + " (" + x + "," + y + "," + z + ") 的"
                + (containerType != null ? containerType : "容器") + " 中";
    }

    @Override
    public boolean matches(EggLocation other) {
        if (other instanceof BlockLocation b) {
            return worldName.equals(b.worldName())
                    && x == b.x() && y == b.y() && z == b.z();
        }
        if (other instanceof ContainerLocation c) {
            return worldName.equals(c.worldName())
                    && x == c.x() && y == c.y() && z == c.z();
        }
        return false;
    }

    @Override
    public void appendDetailsTo(StringBuilder sb) {
        sb.append("§e世界: §f").append(worldName).append("\n");
        sb.append("§e坐标: §f(").append(x).append(", ").append(y).append(", ").append(z).append(")\n");
        sb.append("§e容器类型: §f").append(containerType).append("\n");
    }

    @Override
    public Map<String, Object> serialize() {
        return Map.of("type", "CONTAINER", "world", worldName, "x", x, "y", y, "z", z,
                "container-type", containerType);
    }
}
