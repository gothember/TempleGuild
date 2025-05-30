package ru.templeguild.storage;

import ru.templeguild.TempleGuild;
import ru.templeguild.clans.Clan;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class SqliteStorage implements DataStorage {

    private final TempleGuild plugin;
    private Connection connection;
    private final String dbName = "clandata.db";

    public SqliteStorage(TempleGuild plugin) {
        this.plugin = plugin;
    }

    private Connection getSQLConnection() {
        File dataFolder = new File(plugin.getDataFolder(), dbName);
        if (!dataFolder.exists()) {
            try {
                // Ensure parent directory exists
                if (!dataFolder.getParentFile().exists()) {
                    dataFolder.getParentFile().mkdirs();
                }
                dataFolder.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "File write error: " + dbName, e);
            }
        }
        try {
            if (connection != null && !connection.isClosed()) {
                return connection;
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);
            return connection;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "SQLite exception on initialize", ex);
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().log(Level.SEVERE, "You need the SQLite JDBC library. Please ensure it is installed.", ex);
        }
        return null;
    }

    @Override
    public void initialize() {
        connection = getSQLConnection();
        if (connection == null) {
            plugin.getLogger().severe("SQLite connection failed. Cannot initialize tables.");
            // Consider throwing an exception here to halt plugin loading if DB is critical
            return;
        }
        try (Statement statement = connection.createStatement()) {
            // Clans table: clan_name is primary key (case-insensitive)
            statement.execute("CREATE TABLE IF NOT EXISTS clans (" +
                    "clan_name TEXT PRIMARY KEY COLLATE NOCASE, " +
                    "original_name TEXT NOT NULL, " + // To store original casing
                    "leader_uuid TEXT NOT NULL, " +
                        "pvp_enabled BOOLEAN NOT NULL DEFAULT 0, " +
                        "storage_data TEXT NULL)");

            // Players table: player_uuid is primary key
            statement.execute("CREATE TABLE IF NOT EXISTS players (" +
                    "player_uuid TEXT PRIMARY KEY, " +
                    "clan_name TEXT, " +
                    "FOREIGN KEY(clan_name) REFERENCES clans(clan_name) ON DELETE SET NULL ON UPDATE CASCADE)");

            plugin.getLogger().info(plugin.getConfig().getString("messages.sqlite_storage_initialized_tables_ok", "SQLite storage initialized and tables created/verified."));

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating SQLite tables", e);
        }
    }

    @Override
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info(plugin.getConfig().getString("messages.sqlite_connection_closed", "SQLite connection closed."));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing SQLite connection", e);
        }
    }

    @Override
    public void createClan(Clan clan) {
        String sql = "INSERT INTO clans(clan_name, original_name, leader_uuid, pvp_enabled, storage_data) VALUES(?,?,?,?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, clan.getName().toLowerCase());
            pstmt.setString(2, clan.getName());
            pstmt.setString(3, clan.getLeader().toString());
            pstmt.setBoolean(4, clan.isPvpEnabled());
            pstmt.setString(5, clan.getSerializedStorage()); // Can be null
            pstmt.executeUpdate();
            // Add leader to player data
            addPlayerToClan(clan.getLeader(), clan.getName());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create clan: " + clan.getName(), e);
        }
    }

    @Override
    public Clan getClan(String clanName) {
        String sql = "SELECT original_name, leader_uuid, pvp_enabled, storage_data FROM clans WHERE clan_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, clanName.toLowerCase());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String originalName = rs.getString("original_name");
                UUID leader = UUID.fromString(rs.getString("leader_uuid"));
                boolean pvpEnabled = rs.getBoolean("pvp_enabled");
                Clan clan = new Clan(originalName, leader);
                clan.setPvpEnabled(pvpEnabled);
                clan.setSerializedStorage(rs.getString("storage_data")); // Can be null
                // Load members into the clan object
                getClanMembers(originalName).forEach(clan::addMember);
                return clan;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve clan: " + clanName, e);
        }
        return null;
    }

    @Override
    public void updateClan(Clan clan) {
        String sql = "UPDATE clans SET original_name = ?, leader_uuid = ?, pvp_enabled = ?, storage_data = ? WHERE clan_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, clan.getName());
            pstmt.setString(2, clan.getLeader().toString());
            pstmt.setBoolean(3, clan.isPvpEnabled());
            pstmt.setString(4, clan.getSerializedStorage());
            pstmt.setString(5, clan.getName().toLowerCase());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not update clan: " + clan.getName(), e);
        }
    }

    @Override
    public void deleteClan(String clanName) {
        // Important: Clear cache for all members BEFORE deleting the clan record,
        // as we need to know who the members were.
        Set<UUID> members = getClanMembers(clanName); // Get members before they are disassociated by DB or clan deletion
        for (UUID memberUUID : members) {
            plugin.getClanManager().clearPlayerClanCache(memberUUID);
            plugin.getClanManager().removeFromClanChatToggleOnLeave(memberUUID); // Also ensure chat toggle is off
        }

        String sql = "DELETE FROM clans WHERE clan_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, clanName.toLowerCase());
            pstmt.executeUpdate();
            // Player disassociation is handled by ON DELETE SET NULL/CASCADE in players table schema
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete clan: " + clanName, e);
        }
    }

    @Override
    public Set<String> getAllClanNames() {
        Set<String> clanNames = new HashSet<>();
        String sql = "SELECT original_name FROM clans";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                clanNames.add(rs.getString("original_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve all clan names", e);
        }
        return clanNames;
    }

    @Override
    public void addPlayerToClan(UUID playerUUID, String clanName) {
        // Use REPLACE to handle both insert and update (if player was in another clan)
        String sql = "REPLACE INTO players(player_uuid, clan_name) VALUES(?,?)"; // Simpler query
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.setString(2, clanName.toLowerCase()); // Store lowercase clan name
            pstmt.executeUpdate();
             // Update in-memory clan object
            Clan clan = TempleGuild.getInstance().getClanManager().getClan(clanName); // getClan uses original case
            if (clan != null) {
                clan.addMember(playerUUID);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not add player " + playerUUID + " to clan " + clanName, e);
        }
    }

    @Override
    public void removePlayerFromClan(UUID playerUUID) {
        String clanName = getClanNameForPlayer(playerUUID); // Get clan name before removing

        String sql = "UPDATE players SET clan_name = NULL WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            pstmt.executeUpdate();
                TempleGuild.getInstance().getClanManager().removeFromClanChatToggleOnLeave(playerUUID);
                TempleGuild.getInstance().getClanManager().clearPlayerClanCache(playerUUID); // Add this

            if (clanName != null) {
                 Clan clan = TempleGuild.getInstance().getClanManager().getClan(clanName);
                 if (clan != null) {
                     clan.removeMember(playerUUID);
                 }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not remove player " + playerUUID + " from clan", e);
        }
    }

    @Override
    public String getClanNameForPlayer(UUID playerUUID) {
        String sql = "SELECT c.original_name FROM players p JOIN clans c ON p.clan_name = c.clan_name WHERE p.player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("original_name");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve clan for player: " + playerUUID, e);
        }
        return null;
    }

    @Override
    public boolean isPlayerInClan(UUID playerUUID) {
        return getClanNameForPlayer(playerUUID) != null;
    }

    @Override
    public Set<UUID> getClanMembers(String clanName) {
        Set<UUID> members = new HashSet<>();
        String sql = "SELECT player_uuid FROM players WHERE clan_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, clanName.toLowerCase()); // Query with lowercase name
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                try {
                    members.add(UUID.fromString(rs.getString("player_uuid")));
                } catch (IllegalArgumentException e) {
                     plugin.getLogger().warning("Invalid UUID format in database for clan " + clanName + ": " + rs.getString("player_uuid")); // Log with original name
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not retrieve members for clan: " + clanName, e);
        }
        return members;
    }
}
