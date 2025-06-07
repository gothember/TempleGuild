package ru.templeguild.storage;

import ru.templeguild.clans.Clan; // We'll create this class soon
import java.util.UUID;
import java.util.Set;

public interface DataStorage {
    void initialize(); // To set up connections or load files
    void shutdown();   // To close connections or save files

    void createClan(Clan clan);
    Clan getClan(String clanName);
    void updateClan(Clan clan);
    void deleteClan(String clanName);
    Set<String> getAllClanNames();

    // Player specific data (linking player to clan)
    void addPlayerToClan(UUID playerUUID, String clanName);
    void removePlayerFromClan(UUID playerUUID);
    String getClanNameForPlayer(UUID playerUUID);
    boolean isPlayerInClan(UUID playerUUID);
    Set<UUID> getClanMembers(String clanName);

    void updateClanKills(String clanName, int kills);
    java.util.Map<String, Integer> getTopClansByKills(int limit); // Use java.util.Map

    void removeAllPlayersFromClanAndClearCaches(String clanName, java.util.Set<java.util.UUID> memberUUIDs);
}
