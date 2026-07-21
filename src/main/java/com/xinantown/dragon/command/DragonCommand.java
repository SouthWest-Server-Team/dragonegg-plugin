package com.xinantown.dragon.command;

import com.xinantown.dragon.DragonEggTracker;
import com.xinantown.dragon.PlayerInventoryLocation;
import com.xinantown.dragon.scanner.EggScanner;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Handles /dragon command and subcommands.
 */
public class DragonCommand implements CommandExecutor, TabCompleter {

    private final DragonEggTracker tracker;
    private final EggScanner scanner;

    private static final List<String> COMMANDS = Arrays.asList(
            "locate", "give", "remove", "scan", "info", "help"
    );

    public DragonCommand(DragonEggTracker tracker, EggScanner scanner) {
        this.tracker = tracker;
        this.scanner = scanner;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "locate":
                return handleLocate(sender);
            case "give":
                return handleGive(sender, args);
            case "remove":
                return handleRemove(sender);
            case "scan":
                return handleScan(sender);
            case "info":
                return handleInfo(sender);
            case "help":
                sendHelp(sender);
                return true;
            default:
                sender.sendMessage("§c未知子命令: §e/dragon help §c查看帮助");
                return true;
        }
    }

    private boolean handleLocate(CommandSender sender) {
        if (!sender.hasPermission("dragonegg.command.locate")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        String desc = tracker.getLocationDescription();
        sender.sendMessage("§a" + desc);
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("dragonegg.command.give")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c用法: /dragon give <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§c玩家 " + args[1] + " 不在线或不存在！");
            return true;
        }

        // Remove existing egg if present
        if (tracker.isTracked()) {
            String oldLoc = tracker.getLocationDescription();
            tracker.removeEgg();
            sender.sendMessage("§e已移除旧的龙蛋: " + oldLoc);
        }

        // Give new egg to player
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        target.getInventory().addItem(egg);
        tracker.set(new PlayerInventoryLocation(target.getUniqueId()));
        sender.sendMessage("§a已给予玩家 " + target.getName() + " 一个龙蛋");
        target.sendMessage("§a你收到了一个龙蛋！");
        return true;
    }

    private boolean handleRemove(CommandSender sender) {
        if (!sender.hasPermission("dragonegg.command.remove")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        if (!tracker.isTracked()) {
            sender.sendMessage("§e当前没有龙蛋存在");
            return true;
        }

        String oldLoc = tracker.getLocationDescription();
        tracker.removeEgg();
        sender.sendMessage("§a已删除龙蛋: " + oldLoc);
        Bukkit.getLogger().info("[DragonEggGuard] " + sender.getName() + " removed dragon egg: " + oldLoc);
        return true;
    }

    private boolean handleScan(CommandSender sender) {
        if (!sender.hasPermission("dragonegg.command.scan")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        sender.sendMessage("§e正在扫描全服龙蛋...");
        scanner.scanNow(sender);
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        if (!sender.hasPermission("dragonegg.command.info")) {
            sender.sendMessage("§c你没有权限使用此命令！");
            return true;
        }

        if (!tracker.isTracked()) {
            sender.sendMessage("§e当前没有追踪的龙蛋");
            sender.sendMessage("§7使用 /dragon scan 扫描全服");
            return true;
        }

        String info = tracker.getDetailedInfo();
        sender.sendMessage(info);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== DragonEggGuard 帮助 ===");
        sender.sendMessage("§e/dragon locate §7- 定位当前唯一的龙蛋");
        sender.sendMessage("§e/dragon info §7- 查看龙蛋详细信息");
        if (sender.hasPermission("dragonegg.command.give")) {
            sender.sendMessage("§e/dragon give <player> §7- 给予玩家龙蛋");
        }
        if (sender.hasPermission("dragonegg.command.remove")) {
            sender.sendMessage("§e/dragon remove §7- 删除当前龙蛋");
        }
        if (sender.hasPermission("dragonegg.command.scan")) {
            sender.sendMessage("§e/dragon scan §7- 手动扫描清理多余龙蛋");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String cmd : COMMANDS) {
                if (cmd.startsWith(prefix)) {
                    // Filter by permission
                    switch (cmd) {
                        case "locate":
                        case "info":
                        case "help":
                            completions.add(cmd);
                            break;
                        case "give":
                            if (sender.hasPermission("dragonegg.command.give")) completions.add(cmd);
                            break;
                        case "remove":
                            if (sender.hasPermission("dragonegg.command.remove")) completions.add(cmd);
                            break;
                        case "scan":
                            if (sender.hasPermission("dragonegg.command.scan")) completions.add(cmd);
                            break;
                    }
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
