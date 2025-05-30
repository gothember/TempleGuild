package ru.templeguild.clans;

import ru.templeguild.TempleGuild;
import ru.templeguild.storage.DataStorage;
import org.bukkit.entity.Player; // Added import for Player
import org.bukkit.Bukkit; // Added for Bukkit.getPlayer
import ru.templeguild.utils.ChatUtils; // Added for ChatUtils
import org.bukkit.inventory.Inventory; // Added for Inventory
import ru.templeguild.utils.InventoryUtils; // Our new utility
import java.io.IOException; // Added for IOException

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.List; // Added for List
import java.util.ArrayList; // Added for ArrayList
import java.util.Iterator; // Added for Iterator
import java.util.HashSet; // Added for HashSet
import java.util.concurrent.ConcurrentHashMap; // Added for ConcurrentHashMap

public class ClanManager {

    private final TempleGuild plugin;
    private final DataStorage dataStorage;
    private final Map<String, Clan> clansMap; // Stores clans by lowercase name for quick lookup
    private final Map<UUID, List<ClanInvite>> pendingInvites = new HashMap<>(); // Key: Invited Player UUID
    private final long inviteTimeoutMillis = 60000; // 60 seconds, make configurable later if needed
    private final Set<UUID> clanChatToggled = new HashSet<>();
    // Store clan inventories in memory. Key: lowercase clan name
    private final Map<String, Inventory> clanInventories = new HashMap<>();
    private final Map<UUID, String> playerClanCache = new ConcurrentHashMap<>();

    public ClanManager(TempleGuild plugin, DataStorage dataStorage) {
        this.plugin = plugin;
        this.dataStorage = dataStorage;
        this.clansMap = new HashMap<>();
    }

    public void loadClans() {
        clansMap.clear();
        Set<String> clanNames = dataStorage.getAllClanNames();
        plugin.getLogger().info("Loading " + clanNames.size() + " clan(s)...");
        for (String clanName : clanNames) {
            Clan clan = dataStorage.getClan(clanName);
            if (clan != null) {
                // Ensure all members are loaded into the clan object from player data
                // This is important because YamlStorage.getClan() loads members itself.
                // If it didn't, we would do it here:
                // Set<UUID> members = dataStorage.getClanMembers(clan.getName());
                // members.forEach(clan::addMember); // Already handled by YamlStorage.getClan() logic

                clansMap.put(clan.getName().toLowerCase(), clan);
                plugin.getLogger().info("Loaded clan: " + clan.getName());
                if (clan.getSerializedStorage() != null && !clan.getSerializedStorage().isEmpty()) {
                    try {
                        String inventoryTitle = ChatUtils.format(plugin.getConfig().getString("messages.clan_storage_title", "&8Clan Storage: {clan_name}")
                                                            .replace("{clan_name}", clan.getName()));
                        Inventory inv = InventoryUtils.base64ToInventory(clan.getSerializedStorage(), inventoryTitle);
                        clanInventories.put(clan.getName().toLowerCase(), inv);
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.SEVERE, "Could not deserialize_inventory for clan " + clan.getName(), e);
                    }
                }
                // Populate playerClanCache
                for (UUID memberUUID : clan.getMembers()) {
                    playerClanCache.put(memberUUID, clan.getName().toLowerCase());
                }
            } else {
                plugin.getLogger().warning("Failed to load clan data for: " + clanName);
            }
        }
        plugin.getLogger().info("Clan loading complete.");
    }

    public void createClan(String name, UUID leader) {
        if (isClanNameTaken(name)) {
            // This check should ideally be done before calling this method, e.g., in command logic
            plugin.getLogger().warning("Attempted to create a clan with an existing name: " + name);
            return;
        }
        Clan clan = new Clan(name, leader);
        dataStorage.createClan(clan); // This also adds leader to player data via DataStorage
        clansMap.put(name.toLowerCase(), clan);
        playerClanCache.put(leader, name.toLowerCase()); // Add leader to cache
        plugin.getLogger().info("Clan '" + name + "' created by " + leader.toString());
    }

    public Clan getClan(String name) {
        return clansMap.get(name.toLowerCase());
    }

    public Clan getClanByPlayer(UUID playerUUID) {
        String cachedClanNameLower = playerClanCache.get(playerUUID);
        if (cachedClanNameLower != null) {
            return clansMap.get(cachedClanNameLower); // clansMap stores by lowercase name
        }
        return null;
    }

    public boolean isPlayerInClanCached(UUID playerUUID) {
        return playerClanCache.containsKey(playerUUID);
    }

    public void clearPlayerClanCache(UUID playerUUID) {
        playerClanCache.remove(playerUUID);
    }

    public boolean isClanNameTaken(String name) {
        return clansMap.containsKey(name.toLowerCase());
    }

    public void deleteClan(String clanName) {
        Clan clan = getClan(clanName);
        if (clan == null) {
            plugin.getLogger().warning("Attempted to delete a non-existent clan: " + clanName);
            return;
        }

        // Remove all members from the clan in DataStorage first
        // This is handled by dataStorage.deleteClan() which calls removePlayerFromClan for all members
        dataStorage.deleteClan(clanName); // This will trigger cache removal via DataStorage -> clearPlayerClanCache
        Clan clanToRemove = clansMap.remove(clanName.toLowerCase());
        if (clanToRemove != null) {
            for (UUID memberUUID : clanToRemove.getMembers()) {
                playerClanCache.remove(memberUUID); // Ensure all members are cleared from cache
            }
        }
        clanInventories.remove(clanName.toLowerCase());
        plugin.getLogger().info("Clan '" + clanName + "' deleted.");
    }

    public void addPlayerToClan(Player player, Clan clan) {
        // Logic to add player to clan, update storage, and in-memory map
        // This method might be more complex depending on invitation system etc.
        // For now, a direct add:
        dataStorage.addPlayerToClan(player.getUniqueId(), clan.getName());
        clan.addMember(player.getUniqueId()); // Update in-memory object
    }

    public void removePlayerFromClan(Player player) {
        // Logic to remove player from clan
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan != null) {
            dataStorage.removePlayerFromClan(player.getUniqueId()); // This will call removeFromClanChatToggleOnLeave via DataStorage
            clan.removeMember(player.getUniqueId()); // Update in-memory object
            // If leader leaves, specific logic might be needed (e.g., disband or promote)
        }
    }

    public void updateClanPVP(String clanName, boolean pvpStatus) {
        Clan clan = getClan(clanName);
        if (clan != null) {
            clan.setPvpEnabled(pvpStatus);
            dataStorage.updateClan(clan);
        }
    }

    // ... other clan management methods as needed (e.g., promotions, alliances etc.)

    public void sendInvite(Clan clan, Player inviter, Player invitedPlayer) {
        if (clan.isMember(invitedPlayer.getUniqueId())) {
            inviter.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_already_member", "&c{player_name} is already in your clan.")
                    .replace("{player_name}", invitedPlayer.getName())));
            return;
        }

        if (getClanByPlayer(invitedPlayer.getUniqueId()) != null) {
            inviter.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_player_in_another_clan", "&c{player_name} is already in another clan.")
                    .replace("{player_name}", invitedPlayer.getName())));
            return;
        }

        // Check if player already has a pending invite from this clan
        List<ClanInvite> playerInvites = pendingInvites.getOrDefault(invitedPlayer.getUniqueId(), new ArrayList<>());
        for (ClanInvite existingInvite : playerInvites) {
            if (existingInvite.getClanName().equalsIgnoreCase(clan.getName()) && !existingInvite.isExpired(inviteTimeoutMillis)) {
                inviter.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_already_pending", "&cAn invite to {clan_name} for {player_name} is already pending.")
                        .replace("{clan_name}", clan.getName())
                        .replace("{player_name}", invitedPlayer.getName())));
                return;
            }
        }

        ClanInvite invite = new ClanInvite(clan.getName(), invitedPlayer.getUniqueId(), inviter.getUniqueId());
        playerInvites.removeIf(i -> i.getClanName().equalsIgnoreCase(clan.getName())); // Remove old/expired invite for this clan
        playerInvites.add(invite);
        pendingInvites.put(invitedPlayer.getUniqueId(), playerInvites);

        inviter.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_sent", "&aInvite sent to {player_name} to join {clan_name}.")
                .replace("{player_name}", invitedPlayer.getName())
                .replace("{clan_name}", clan.getName())));

        invitedPlayer.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_received", "&6You have been invited to join clan {clan_name} by {inviter_name}. Type &e/clan accept {clan_name} &6or &e/clan decline {clan_name}&6.")
                .replace("{clan_name}", clan.getName())
                .replace("{inviter_name}", inviter.getName())));
    }

    public boolean acceptInvite(Player player, String clanNameToAccept) {
        cleanupExpiredInvites(player.getUniqueId());
        List<ClanInvite> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null || invites.isEmpty()) {
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_none_pending", "&cYou have no pending clan invitations.")));
            return false;
        }

        ClanInvite acceptedInvite = null;
        for (ClanInvite invite : invites) {
            if (invite.getClanName().equalsIgnoreCase(clanNameToAccept)) {
                acceptedInvite = invite;
                break;
            }
        }

        if (acceptedInvite == null) {
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_not_found_for_clan", "&cYou don't have an invite from clan {clan_name}.")
                    .replace("{clan_name}", clanNameToAccept)));
            return false;
        }

        Clan clan = getClan(acceptedInvite.getClanName());
        if (clan == null) {
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_disbanded_on_accept", "&cThe clan {clan_name} seems to have been disbanded.")
                    .replace("{clan_name}", acceptedInvite.getClanName())));
            invites.remove(acceptedInvite);
            return false;
        }

        if (getClanByPlayer(player.getUniqueId()) != null) {
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.already_in_clan_on_accept", "&cYou joined another clan before accepting this invite.")));
            invites.remove(acceptedInvite);
            return false;
        }

        // Add player to clan
        dataStorage.addPlayerToClan(player.getUniqueId(), clan.getName());
        clan.addMember(player.getUniqueId()); // Update in-memory clan object
        playerClanCache.put(player.getUniqueId(), clan.getName().toLowerCase()); // Update cache

        // Notify clan members (optional, can be noisy)
        String joinMessage = ChatUtils.format(plugin.getConfig().getString("messages.player_joined_clan", "&e{player_name} has joined the clan!")
                                        .replace("{player_name}", player.getName()));
        clan.getMembers().stream()
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .forEach(member -> member.sendMessage(joinMessage));


        player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_accepted", "&aYou have joined clan {clan_name}!")
                .replace("{clan_name}", clan.getName())));

        invites.remove(acceptedInvite);
        if (invites.isEmpty()) {
            pendingInvites.remove(player.getUniqueId());
        }
        return true;
    }

    public boolean declineInvite(Player player, String clanNameToDecline) {
        cleanupExpiredInvites(player.getUniqueId());
        List<ClanInvite> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null) {
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_none_pending", "&cYou have no pending clan invitations.")));
            return false;
        }

        ClanInvite declinedInvite = null;
        for (ClanInvite invite : invites) {
            if (invite.getClanName().equalsIgnoreCase(clanNameToDecline)) {
                declinedInvite = invite;
                break;
            }
        }

        if (declinedInvite == null) {
             player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_not_found_for_clan", "&cYou don't have an invite from clan {clan_name}.")
                    .replace("{clan_name}", clanNameToDecline)));
            return false;
        }

        invites.remove(declinedInvite);
        if (invites.isEmpty()) {
            pendingInvites.remove(player.getUniqueId());
        }

        Player inviter = Bukkit.getPlayer(declinedInvite.getInviterUUID());
        if(inviter != null && inviter.isOnline()){
            inviter.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_declined_to_inviter", "&e{player_name} declined your invitation to join {clan_name}.")
                .replace("{player_name}", player.getName())
                .replace("{clan_name}", declinedInvite.getClanName())));
        }

        player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.invite_declined", "&aYou have declined the invitation from clan {clan_name}.")
                .replace("{clan_name}", declinedInvite.getClanName())));
        return true;
    }

    public void cleanupExpiredInvites(UUID playerUUID) {
        List<ClanInvite> invites = pendingInvites.get(playerUUID);
        if (invites != null) {
            invites.removeIf(invite -> invite.isExpired(inviteTimeoutMillis));
            if (invites.isEmpty()) {
                pendingInvites.remove(playerUUID);
            }
        }
    }

    // Periodically cleanup all expired invites (e.g., in a BukkitRunnable)
    public void cleanupAllExpiredInvites() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, List<ClanInvite>>> iterator = pendingInvites.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, List<ClanInvite>> entry = iterator.next();
            entry.getValue().removeIf(invite -> (now - invite.getTimestamp()) > inviteTimeoutMillis);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
         plugin.getLogger().info("Cleaned up expired clan invitations.");
    }

    public boolean toggleClanChat(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (getClanByPlayer(playerUUID) == null) {
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.not_in_clan_for_chat_toggle", "&cYou must be in a clan to toggle clan chat.")));
            return false; // Not really a failure of toggle, but user can't use it
        }

        if (clanChatToggled.contains(playerUUID)) {
            clanChatToggled.remove(playerUUID);
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_chat_toggled_off", "&aClan chat toggled &cOFF&a.")));
            return false; // Indicates now off
        } else {
            clanChatToggled.add(playerUUID);
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_chat_toggled_on", "&aClan chat toggled &2ON&a.")));
            return true; // Indicates now on
        }
    }

    public boolean isClanChatToggled(UUID playerUUID) {
        return clanChatToggled.contains(playerUUID);
    }

    public void removeFromClanChatToggleOnLeave(UUID playerUUID) {
        clanChatToggled.remove(playerUUID);
    }

    public void sendClanChatMessage(Player sender, String message) {
        Clan clan = getClanByPlayer(sender.getUniqueId());
        if (clan == null) {
            sender.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.not_in_clan_to_chat", "&cYou are not in a clan to send a message.")));
            // If they were toggled but left/got kicked, untoggle them
            clanChatToggled.remove(sender.getUniqueId());
            return;
        }

        String format = plugin.getConfig().getString("messages.clan_chat_format", "&8[&aClan&8] &7{player_name}: &f{message}");
        String formattedMessage = ChatUtils.format(format
                .replace("{clan_name}", clan.getName()) // In case you want to use {clan_name} in format
                .replace("{player_name}", sender.getName())
                .replace("{message}", message));

        clan.getMembers().stream()
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull) // Ensure player is online
            .forEach(member -> member.sendMessage(formattedMessage));

        // Optional: Log to console
        // plugin.getLogger().info("[ClanChat] " + clan.getName() + " | " + sender.getName() + ": " + message);
    }

    public void toggleClanPvp(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.not_in_clan", "&cYou are not in a clan.")));
            return;
        }

        // Permission check: Only leader (or officers, to be added later) can toggle PvP
        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.no_pvp_toggle_permission", "&cOnly the clan leader can toggle PvP status.")));
            return;
        }

        boolean newPvpState = !clan.isPvpEnabled();
        clan.setPvpEnabled(newPvpState);
        dataStorage.updateClan(clan); // Persist the change

        String messagePath = newPvpState ? "messages.clan_pvp_enabled" : "messages.clan_pvp_disabled";
        String message = ChatUtils.format(plugin.getConfig().getString(messagePath)
                                        .replace("{clan_name}", clan.getName()));

        // Notify all clan members
        clan.getMembers().stream()
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .forEach(member -> member.sendMessage(message));
    }

    // Method to get or create clan inventory
    public Inventory getClanInventory(Clan clan) {
        String clanNameLower = clan.getName().toLowerCase();
        if (clanInventories.containsKey(clanNameLower)) {
            return clanInventories.get(clanNameLower);
        } else {
            // Create a new inventory if not found (e.g. first time or if failed to load)
            int size = plugin.getConfig().getInt("clan_storage.default_size", 27); // Default 3 rows, make configurable
            if (size % 9 != 0 || size > 54) size = 27; // Validate size

            String inventoryTitle = ChatUtils.format(plugin.getConfig().getString("messages.clan_storage_title", "&8Clan Storage: {clan_name}")
                                                .replace("{clan_name}", clan.getName()));
            Inventory inv = Bukkit.createInventory(null, size, inventoryTitle);
            clanInventories.put(clanNameLower, inv);
            // Optionally save this newly created (empty) inventory back to storage immediately
            // saveClanInventory(clan, inv); // Or rely on saving when plugin disables or on specific events
            return inv;
        }
    }

    // Method to save a clan's inventory
    public void saveClanInventory(Clan clan) {
        String clanNameLower = clan.getName().toLowerCase();
        Inventory inv = clanInventories.get(clanNameLower);
        if (inv != null) { // Only save if it's loaded/exists
            try {
                clan.setSerializedStorage(InventoryUtils.inventoryToBase64(inv));
                dataStorage.updateClan(clan); // Persist the change
            } catch (IllegalStateException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not serialize inventory for clan " + clan.getName(), e);
            }
        }
    }

    // Method to save all loaded clan inventories (e.g., onDisable)
    public void saveAllClanInventories() {
        plugin.getLogger().info("Saving all loaded clan inventories...");
        for (Map.Entry<String, Clan> entry : clansMap.entrySet()) {
            Clan clan = entry.getValue();
            // Ensure inventory is loaded if it wasn't already (though usually it would be if accessed)
            // Inventory inv = getClanInventory(clan); // This might create a new one if not loaded
            Inventory inv = clanInventories.get(clan.getName().toLowerCase()); // More direct
            if (inv != null) { // Only save if it was loaded/created
                 saveClanInventory(clan);
            }
        }
        plugin.getLogger().info("Clan inventories saving complete.");
    }
}
