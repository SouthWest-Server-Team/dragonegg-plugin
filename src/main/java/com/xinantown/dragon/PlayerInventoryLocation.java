package com.xinantown.dragon;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public record PlayerInventoryLocation(UUID playerUUID) implements EggLocation {

    @Override public String typeName() { return "PLAYER_INVENTORY"; }

    @Override
    public boolean verify() {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player == null || !player.isOnline()) return false;
        return player.getInventory().contains(Material.DRAGON_EGG);
    }

    @Override
    public void remove() {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            player.getInventory().remove(Material.DRAGON_EGG);
        }
    }

    @Override
    public String describe() {
        var name = playerUUID.toString();
        try {
            var player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                name = player.getName();
                if (player.isOnline()) {
                    var inv = player.getInventory();
                    if (inv.getItemInMainHand().getType() == Material.DRAGON_EGG) {
                        return "龙蛋在玩家 " + name + " 的主手";
                    }
                    if (inv.getItemInOffHand().getType() == Material.DRAGON_EGG) {
                        return "龙蛋在玩家 " + name + " 的副手";
                    }
                    var contents = inv.getContents();
                    for (int i = 0; i < contents.length; i++) {
                        if (contents[i] != null && contents[i].getType() == Material.DRAGON_EGG) {
                            return "龙蛋在玩家 " + name + " 的" + slotToDescription(i);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Bukkit not available (test environment)
        }
        return "龙蛋在玩家 " + name + " 的背包中";
    }

    private static String slotToDescription(int slot) {
        if (slot >= 0 && slot <= 8) return "快捷栏第" + (slot + 1) + "格";
        if (slot >= 9 && slot <= 35) return "物品栏第" + (slot - 8) + "格";
        if (slot == 36) return "靴子槽";
        if (slot == 37) return "护腿槽";
        if (slot == 38) return "胸甲槽";
        if (slot == 39) return "头盔槽";
        if (slot == 40) return "副手";
        return "物品栏第" + slot + "格";
    }

    @Override
    public boolean matches(EggLocation other) {
        return other instanceof PlayerInventoryLocation pi
                && playerUUID.equals(pi.playerUUID);
    }

    @Override
    public void appendDetailsTo(StringBuilder sb) {
        sb.append("§e玩家UUID: §f").append(playerUUID).append("\n");
        try {
            var p = Bukkit.getPlayer(playerUUID);
            if (p != null) sb.append("§e玩家名: §f").append(p.getName()).append("\n");
        } catch (Exception ignored) {}
    }

    @Override
    public Map<String, Object> serialize() {
        return Map.of("type", "PLAYER_INVENTORY", "player-uuid", playerUUID.toString());
    }
}
