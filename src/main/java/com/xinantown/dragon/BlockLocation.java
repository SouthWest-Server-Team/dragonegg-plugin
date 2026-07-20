package com.xinantown.dragon;

import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.Map;

public record BlockLocation(String worldName, int x, int y, int z) implements EggLocation {

    @Override public String typeName() { return "BLOCK"; }

    @Override
    public boolean verify() {
        var w = Bukkit.getWorld(worldName);
        if (w == null) return false;
        return w.getBlockAt(x, y, z).getType() == Material.DRAGON_EGG;
    }

    @Override
    public void remove() {
        var w = Bukkit.getWorld(worldName);
        if (w != null && w.isChunkLoaded(x >> 4, z >> 4)) {
            var block = w.getBlockAt(x, y, z);
            if (block.getType() == Material.DRAGON_EGG) {
                block.setType(Material.AIR);
            }
        }
    }

    @Override
    public String describe() {
        return "龙蛋方块在 " + worldName + " (" + x + "," + y + "," + z + ")";
    }

    @Override
    public boolean matches(EggLocation other) {
        if (other instanceof BlockLocation b) {
            return worldName.equals(b.worldName) && x == b.x && y == b.y && z == b.z;
        }
        if (other instanceof ContainerLocation c) {
            return worldName.equals(c.worldName()) && x == c.x() && y == c.y() && z == c.z();
        }
        return false;
    }

    @Override
    public void appendDetailsTo(StringBuilder sb) {
        sb.append("§e世界: §f").append(worldName).append("\n");
        sb.append("§e坐标: §f(").append(x).append(", ").append(y).append(", ").append(z).append(")\n");
    }

    @Override
    public Map<String, Object> serialize() {
        return Map.of("type", "BLOCK", "world", worldName, "x", x, "y", y, "z", z);
    }
}
