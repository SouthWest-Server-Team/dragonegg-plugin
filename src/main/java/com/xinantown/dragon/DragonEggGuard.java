package com.xinantown.dragon;

import com.xinantown.dragon.command.DragonCommand;
import com.xinantown.dragon.listener.DragonEggListener;
import com.xinantown.dragon.scanner.EggScanner;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DragonEggGuard - A lightweight Bukkit/Paper plugin that ensures
 * only one dragon egg exists server-wide at any time.
 *
 * Features:
 * - Single dragon egg enforcement
 * - /dragon locate - find the egg
 * - /dragon give <player> - give egg to player
 * - /dragon remove - delete the egg
 * - /dragon scan - manual cleanup scan
 * - /dragon info - detailed egg info
 * - Automatic 30-second scanner for duplicate cleanup
 * - Persistent tracking via data.yml
 */
public class DragonEggGuard extends JavaPlugin {

    private DragonEggTracker tracker;
    private EggScanner scanner;

    @Override
    public void onEnable() {
        // Initialize tracker and load saved data
        tracker = new DragonEggTracker(this);
        tracker.load();

        // Initialize scanner
        scanner = new EggScanner(tracker, getLogger());

        // Register event listener
        DragonEggListener listener = new DragonEggListener(tracker, getLogger());
        getServer().getPluginManager().registerEvents(listener, this);

        // Register command
        DragonCommand commandExecutor = new DragonCommand(tracker, scanner);
        PluginCommand dragonCmd = getCommand("dragon");
        if (dragonCmd != null) {
            dragonCmd.setExecutor(commandExecutor);
            dragonCmd.setTabCompleter(commandExecutor);
        }

        // Start periodic scanner
        scanner.start();

        getLogger().info("DragonEggGuard v1.0.0 enabled!");
        if (tracker.isTracked()) {
            getLogger().info("Loaded existing egg tracker: " + tracker.getType()
                    + (tracker.getWorldName() != null ? " in " + tracker.getWorldName() : ""));
        }
    }

    @Override
    public void onDisable() {
        // Stop scanner
        if (scanner != null) {
            scanner.stop();
        }

        // Save tracker state
        if (tracker != null) {
            tracker.save();
        }

        getLogger().info("DragonEggGuard v1.0.0 disabled!");
    }

    public DragonEggTracker getTracker() {
        return tracker;
    }
}
