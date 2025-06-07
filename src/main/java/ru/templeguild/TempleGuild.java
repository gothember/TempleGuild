package ru.templeguild;

import org.bukkit.plugin.java.JavaPlugin;
import ru.templeguild.clans.ClanManager; // Import
import ru.templeguild.commands.ClanCommand; // Import ClanCommand
import ru.templeguild.storage.DataStorage;
import ru.templeguild.storage.YamlStorage;
import ru.templeguild.storage.SqliteStorage; // Add this import
import java.util.logging.Level; // Import for Level

public final class TempleGuild extends JavaPlugin {

    private static TempleGuild instance;
    private DataStorage dataStorage;
    private ClanManager clanManager; // Add ClanManager field

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        String storageType = getConfig().getString("storage_type", "yaml").toLowerCase();
        if (storageType.equals("sqlite")) {
                this.dataStorage = new SqliteStorage(this); // Use SqliteStorage
                getLogger().info(getConfig().getString("messages.sqlite_selected", "SQLite storage selected."));
        } else {
            this.dataStorage = new YamlStorage(this);
                getLogger().info(getConfig().getString("messages.yaml_selected", "YAML storage selected."));
        }

        try {
                this.dataStorage.initialize(); // This will now call SqliteStorage.initialize() if selected
                getLogger().info(getConfig().getString("messages.data_storage_init_success", "Data storage initialized successfully using {storage_type}.")
                                .replace("{storage_type}", storageType));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize data storage: " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize ClanManager AFTER DataStorage
        this.clanManager = new ClanManager(this, this.dataStorage);
        try {
            this.clanManager.loadClans(); // Load clans into memory
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load clan data: " + e.getMessage(), e);
            // Depending on severity, you might want to disable the plugin
        }

        // Register commands
        ClanCommand clanCommand = new ClanCommand(this);
        getCommand("clan").setExecutor(clanCommand);
        getCommand("clan").setTabCompleter(clanCommand);

        // Register listeners
        getServer().getPluginManager().registerEvents(new ru.templeguild.listeners.PlayerChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ru.templeguild.listeners.PlayerDamageListener(this), this);
        getServer().getPluginManager().registerEvents(new ru.templeguild.listeners.PlayerInventoryCloseListener(this), this);
        getServer().getPluginManager().registerEvents(new ru.templeguild.listeners.PlayerDeathListener(this), this); // Add this line

        // Hook into PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ru.templeguild.integrations.TempleGuildExpansion(this).register();
            getLogger().info(getConfig().getString("messages.papi_hook_success", "Successfully hooked into PlaceholderAPI and registered placeholders."));
        } else {
            getLogger().info(getConfig().getString("messages.papi_hook_fail_not_found", "PlaceholderAPI not found, placeholders will not be available."));
        }

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (clanManager != null) {
                    clanManager.cleanupAllExpiredInvites();
                }
            }
        }.runTaskTimerAsynchronously(this, 20L * 60 * 5, 20L * 60 * 5); // Every 5 minutes

        getLogger().info(getConfig().getString("messages.plugin_enabled", "TempleGuild plugin enabled!"));
    }

    @Override
    public void onDisable() {
        // Shutdown clan manager related tasks if any (e.g. saving pending changes not directly tied to datastorage.shutdown)
        // For now, dataStorage.shutdown() handles all persistence.
        if (clanManager != null) {
            clanManager.saveAllClanInventories(); // Add this line
        }
        if (dataStorage != null) {
            try {
                dataStorage.shutdown();
                getLogger().info(getConfig().getString("messages.data_storage_shutdown_success", "Data storage shut down successfully."));
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to shut down data storage: " + e.getMessage(), e);
            }
        }
        getLogger().info(getConfig().getString("messages.plugin_disabled", "TempleGuild plugin disabled!"));
        instance = null;
    }

    public static TempleGuild getInstance() {
        return instance;
    }

    public DataStorage getDataStorage() {
        return dataStorage;
    }

    public ClanManager getClanManager() { // Add getter for ClanManager
        return clanManager;
    }
}
