package ru.templeguild.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.templeguild.TempleGuild;
import ru.templeguild.clans.Clan;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.logging.Level;

public class YamlStorage implements DataStorage {

    private final TempleGuild plugin;
    private File clansFile;
    private FileConfiguration clansConfig;
    private File playersFile;
    private FileConfiguration playersConfig;

    public YamlStorage(TempleGuild plugin) {
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        clansFile = new File(plugin.getDataFolder(), "clans.yml");
        if (!clansFile.exists()) {
            plugin.saveResource("clans.yml", false); // Save an empty default if not present
        }
        clansConfig = YamlConfiguration.loadConfiguration(clansFile);

        playersFile = new File(plugin.getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            plugin.saveResource("players.yml", false); // Save an empty default if not present
        }
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        plugin.getLogger().info("YAML storage initialized.");
    }

    @Override
    public void shutdown() {
        saveClans();
        savePlayers();
        plugin.getLogger().info("YAML storage shutdown complete. Data saved.");
    }

    private void saveClans() {
        try {
            clansConfig.save(clansFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save clans.yml", e);
        }
    }

    private void savePlayers() {
        try {
            playersConfig.save(playersFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save players.yml", e);
        }
    }

    @Override
    public void createClan(Clan clan) {
        String clanNameLower = clan.getName().toLowerCase();
        ConfigurationSection clanSection = clansConfig.createSection("clans." + clanNameLower);
        clanSection.set("name", clan.getName());
        clanSection.set("leader", clan.getLeader().toString());
        clanSection.set("pvpEnabled", clan.isPvpEnabled());
        if (clan.getSerializedStorage() != null) { // Should be null on creation
            clanSection.set("storage", clan.getSerializedStorage());
        }
        // Members are implicitly managed via players.yml for YAML to simplify,
        // but could be stored here as a list too. For now, we'll derive members from players.yml.
        saveClans();
        // Add leader to player data
        addPlayerToClan(clan.getLeader(), clan.getName());
    }

    @Override
    public Clan getClan(String clanName) {
        String clanNameLower = clanName.toLowerCase();
        if (!clansConfig.isConfigurationSection("clans." + clanNameLower)) {
            return null;
        }
        ConfigurationSection clanSection = clansConfig.getConfigurationSection("clans." + clanNameLower);
        String name = clanSection.getString("name");
        UUID leader = UUID.fromString(clanSection.getString("leader"));
        boolean pvpEnabled = clanSection.getBoolean("pvpEnabled", false);

        Clan clan = new Clan(name, leader);
        clan.setPvpEnabled(pvpEnabled);
        clan.setSerializedStorage(clanSection.getString("storage")); // Can be null

        // Load members from players.yml
        getClanMembers(name).forEach(clan::addMember); // Ensure members are loaded into the clan object
        return clan;
    }

    @Override
    public void updateClan(Clan clan) {
        String clanNameLower = clan.getName().toLowerCase();
        ConfigurationSection clanSection = clansConfig.getConfigurationSection("clans." + clanNameLower);
        if (clanSection == null) {
            clanSection = clansConfig.createSection("clans." + clanNameLower);
        }
        clanSection.set("name", clan.getName()); // In case of case change, though name is key
        clanSection.set("leader", clan.getLeader().toString());
        clanSection.set("pvpEnabled", clan.isPvpEnabled());
        clanSection.set("storage", clan.getSerializedStorage()); // Store it, can be null
        saveClans();
    }

    @Override
    public void deleteClan(String clanName) {
        String clanNameLower = clanName.toLowerCase();
        clansConfig.set("clans." + clanNameLower, null);
        saveClans();
        // Remove all players associated with this clan
        Set<UUID> members = getClanMembers(clanName);
        members.forEach(this::removePlayerFromClan); // This will also savePlayers()
    }

    @Override
    public Set<String> getAllClanNames() {
        if (clansConfig.isConfigurationSection("clans")) {
            return clansConfig.getConfigurationSection("clans").getKeys(false)
                .stream()
                .map(key -> clansConfig.getString("clans." + key + ".name", key)) // Get original casing
                .collect(Collectors.toSet());
        }
        return new HashSet<>();
    }

    @Override
    public void addPlayerToClan(UUID playerUUID, String clanName) {
        playersConfig.set("players." + playerUUID.toString() + ".clan", clanName);
        savePlayers();
        // Also update the Clan object in memory if it's loaded
        Clan clan = TempleGuild.getInstance().getClanManager().getClan(clanName); // Assuming ClanManager exists
        if (clan != null) {
            clan.addMember(playerUUID);
        }
    }

    @Override
    public void removePlayerFromClan(UUID playerUUID) {
        String clanName = getClanNameForPlayer(playerUUID);
        playersConfig.set("players." + playerUUID.toString(), null); // Remove player entry or just clan field
        TempleGuild.getInstance().getClanManager().removeFromClanChatToggleOnLeave(playerUUID);
        TempleGuild.getInstance().getClanManager().clearPlayerClanCache(playerUUID); // Add this
        savePlayers();
        // Also update the Clan object in memory
        if (clanName != null) {
            Clan clan = TempleGuild.getInstance().getClanManager().getClan(clanName); // Assuming ClanManager exists
            if (clan != null) {
                clan.removeMember(playerUUID);
            }
        }
    }

    @Override
    public String getClanNameForPlayer(UUID playerUUID) {
        return playersConfig.getString("players." + playerUUID.toString() + ".clan");
    }

    @Override
    public boolean isPlayerInClan(UUID playerUUID) {
        return playersConfig.contains("players." + playerUUID.toString() + ".clan");
    }

    @Override
    public Set<UUID> getClanMembers(String clanName) {
        Set<UUID> members = new HashSet<>();
        if (playersConfig.isConfigurationSection("players")) {
            ConfigurationSection playersSection = playersConfig.getConfigurationSection("players");
            for (String uuidString : playersSection.getKeys(false)) {
                if (clanName.equalsIgnoreCase(playersSection.getString(uuidString + ".clan"))) {
                    try {
                        members.add(UUID.fromString(uuidString));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID format in players.yml: " + uuidString);
                    }
                }
            }
        }
        return members;
    }
}
