package ru.templeguild.clans;

import ru.templeguild.TempleGuild;
import ru.templeguild.storage.DataStorage;
import org.bukkit.entity.Player; // Added import for Player
import org.bukkit.Bukkit; // Added for Bukkit.getPlayer
import ru.templeguild.utils.ChatUtils; // Added for ChatUtils
import org.bukkit.inventory.Inventory; // Added for Inventory
import ru.templeguild.utils.InventoryUtils; // Our new utility
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text; // For modern HoverEvent content
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
        plugin.getLogger().info(plugin.getConfig().getString("messages.loading_clans_count", "Loading {count} clan(s)...")
                                .replace("{count}", String.valueOf(clanNames.size())));
        for (String clanName : clanNames) {
            Clan clan = dataStorage.getClan(clanName);
            if (clan != null) {
                // Ensure all members are loaded into the clan object from player data
                // This is important because YamlStorage.getClan() loads members itself.
                // If it didn't, we would do it here:
                // Set<UUID> members = dataStorage.getClanMembers(clan.getName());
                // members.forEach(clan::addMember); // Already handled by YamlStorage.getClan() logic

                clansMap.put(clan.getName().toLowerCase(), clan);
                plugin.getLogger().info(plugin.getConfig().getString("messages.loaded_clan_specific", "Loaded clan: {name}")
                                        .replace("{name}", clan.getName()));
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
                plugin.getLogger().warning("Failed to load clan data for: " + clanName); // Dynamic, keep as is
            }
        }
        plugin.getLogger().info(plugin.getConfig().getString("messages.clan_loading_complete", "Clan loading complete."));
    }

    public void createClan(String name, UUID leader) {
        if (isClanNameTaken(name)) {
            // This check should ideally be done before calling this method, e.g., in command logic
            plugin.getLogger().warning("Attempted to create a clan with an existing name: " + name); // Dynamic, keep as is
            return;
        }
        Clan clan = new Clan(name, leader);
        dataStorage.createClan(clan); // This also adds leader to player data via DataStorage
        clansMap.put(name.toLowerCase(), clan);
        playerClanCache.put(leader, name.toLowerCase()); // Add leader to cache
        plugin.getLogger().info("Clan '" + name + "' created by " + leader.toString()); // Dynamic, keep as is for now, or use complex formatter
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
        plugin.getLogger().info("Clan '" + clanName + "' deleted."); // Dynamic, keep as is for now
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
            ChatUtils.sendMessages(inviter, plugin, "messages.invite_already_member", "{player_name}", invitedPlayer.getName());
            return;
        }

        if (getClanByPlayer(invitedPlayer.getUniqueId()) != null) {
            ChatUtils.sendMessages(inviter, plugin, "messages.invite_player_in_another_clan", "{player_name}", invitedPlayer.getName());
            return;
        }

        List<ClanInvite> playerInvites = pendingInvites.getOrDefault(invitedPlayer.getUniqueId(), new ArrayList<>());
        for (ClanInvite existingInvite : playerInvites) {
            if (existingInvite.getClanName().equalsIgnoreCase(clan.getName()) && !existingInvite.isExpired(inviteTimeoutMillis)) {
                ChatUtils.sendMessages(inviter, plugin, "messages.invite_already_pending", "{clan_name}", clan.getName(), "{player_name}", invitedPlayer.getName());
                return;
            }
        }

        ClanInvite invite = new ClanInvite(clan.getName(), invitedPlayer.getUniqueId(), inviter.getUniqueId());
        playerInvites.removeIf(i -> i.getClanName().equalsIgnoreCase(clan.getName()) && i.isExpired(inviteTimeoutMillis)); // Clean up old/expired for this specific clan before adding new
        playerInvites.add(invite);
        pendingInvites.put(invitedPlayer.getUniqueId(), playerInvites);


        // Notify inviter (remains unchanged)
        ChatUtils.sendMessages(inviter, plugin, "messages.invite_sent",
            "{player_name}", invitedPlayer.getName(),
            "{clan_name}", clan.getName());

        // Construct and send the JSON message to the invitedPlayer
        String baseInviteMessage = ChatUtils.getFormattedString(plugin, "messages.invite_received_base",
                "{clan_name}", clan.getName(),
                "{inviter_name}", inviter.getName());

        TextComponent mainMessageComponent = new TextComponent(TextComponent.fromLegacyText(ChatUtils.format(baseInviteMessage))); // Properly parse colors for the base message
        mainMessageComponent.addExtra(" "); // Add space before buttons

        // Create [Accept] button
        String acceptButtonText = ChatUtils.getFormattedString(plugin, "messages.invite_button_accept_text");
        TextComponent acceptButton = new TextComponent(TextComponent.fromLegacyText(ChatUtils.format(acceptButtonText))); // Parse colors
        acceptButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan accept " + clan.getName()));
        String acceptHoverText = ChatUtils.getFormattedString(plugin, "messages.invite_button_accept_hover", "{clan_name}", clan.getName());
        acceptButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatUtils.format(acceptHoverText)))); // Parse colors for hover

        // Create [Decline] button
        String declineButtonText = ChatUtils.getFormattedString(plugin, "messages.invite_button_decline_text");
        TextComponent declineButton = new TextComponent(TextComponent.fromLegacyText(ChatUtils.format(declineButtonText))); // Parse colors
        declineButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan decline " + clan.getName()));
        String declineHoverText = ChatUtils.getFormattedString(plugin, "messages.invite_button_decline_hover", "{clan_name}", clan.getName());
        declineButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatUtils.format(declineHoverText)))); // Parse colors for hover

        // Assemble the message
        mainMessageComponent.addExtra(acceptButton);
        mainMessageComponent.addExtra(" "); // Space between buttons
        mainMessageComponent.addExtra(declineButton);

        // Send the JSON message
        invitedPlayer.spigot().sendMessage(mainMessageComponent);

        // Send additional instruction lines if defined (from the rest of invite_received_instructions list)
        // These are sent as separate, normal messages after the clickable component.
        List<String> additionalInstructions = plugin.getConfig().getStringList("messages.invite_received_instructions");
        for (String line : additionalInstructions) {
            if (line != null && !line.isEmpty()) { // Ensure line is not null or empty
                String processedLine = line.replace("{clan_name}", clan.getName()).replace("{inviter_name}", inviter.getName());
                invitedPlayer.sendMessage(ChatUtils.format(processedLine));
            }
        }
    }

    public boolean acceptInvite(Player player, String clanNameToAccept) {
        cleanupExpiredInvites(player.getUniqueId());
        List<ClanInvite> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null || invites.isEmpty()) {
            ChatUtils.sendMessages(player, plugin, "messages.invite_none_pending");
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
            ChatUtils.sendMessages(player, plugin, "messages.invite_not_found_for_clan", "{clan_name}", clanNameToAccept);
            return false;
        }

        Clan clan = getClan(acceptedInvite.getClanName());
        if (clan == null) {
            ChatUtils.sendMessages(player, plugin, "messages.clan_disbanded_on_accept", "{clan_name}", acceptedInvite.getClanName());
            invites.remove(acceptedInvite);
            return false;
        }

        if (getClanByPlayer(player.getUniqueId()) != null) {
            ChatUtils.sendMessages(player, plugin, "messages.already_in_clan_on_accept");
            invites.remove(acceptedInvite);
            return false;
        }

        dataStorage.addPlayerToClan(player.getUniqueId(), clan.getName());
        clan.addMember(player.getUniqueId());
        playerClanCache.put(player.getUniqueId(), clan.getName().toLowerCase());

        String joinMessage = ChatUtils.getFormattedString(plugin, "messages.player_joined_clan", "{player_name}", player.getName());
        clan.getMembers().stream()
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .forEach(member -> member.sendMessage(joinMessage)); // Already formatted

        ChatUtils.sendMessages(player, plugin, "messages.invite_accepted", "{clan_name}", clan.getName());

        invites.remove(acceptedInvite);
        if (invites.isEmpty()) {
            pendingInvites.remove(player.getUniqueId());
        }
        return true;
    }

    public boolean declineInvite(Player player, String clanNameToDecline) {
        cleanupExpiredInvites(player.getUniqueId());
        List<ClanInvite> invites = pendingInvites.get(player.getUniqueId());
        if (invites == null) { // Should be invites.isEmpty() or check after getOrDefault
            ChatUtils.sendMessages(player, plugin, "messages.invite_none_pending");
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
             ChatUtils.sendMessages(player, plugin, "messages.invite_not_found_for_clan", "{clan_name}", clanNameToDecline);
            return false;
        }

        invites.remove(declinedInvite);
        if (invites.isEmpty()) {
            pendingInvites.remove(player.getUniqueId());
        }

        Player inviter = Bukkit.getPlayer(declinedInvite.getInviterUUID());
        if(inviter != null && inviter.isOnline()){
            ChatUtils.sendMessages(inviter, plugin, "messages.invite_declined_to_inviter", "{player_name}", player.getName(), "{clan_name}", declinedInvite.getClanName());
        }

        ChatUtils.sendMessages(player, plugin, "messages.invite_declined", "{clan_name}", declinedInvite.getClanName());
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
         plugin.getLogger().info(plugin.getConfig().getString("messages.expired_invites_cleaned", "Cleaned up expired clan invitations."));
    }

    public boolean toggleClanChat(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (getClanByPlayer(playerUUID) == null) {
            ChatUtils.sendMessages(player, plugin, "messages.not_in_clan_for_chat_toggle");
            return false;
        }

        if (clanChatToggled.contains(playerUUID)) {
            clanChatToggled.remove(playerUUID);
            ChatUtils.sendMessages(player, plugin, "messages.clan_chat_toggled_off");
            return false;
        } else {
            clanChatToggled.add(playerUUID);
            ChatUtils.sendMessages(player, plugin, "messages.clan_chat_toggled_on");
            return true;
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
            ChatUtils.sendMessages(sender, plugin, "messages.not_in_clan_to_chat");
            clanChatToggled.remove(sender.getUniqueId());
            return;
        }

        String formattedMessage = ChatUtils.getFormattedString(plugin, "messages.clan_chat_format",
                "{clan_name}", clan.getName(),
                "{player_name}", sender.getName(),
                "{message}", message);

        clan.getMembers().stream()
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .forEach(member -> member.sendMessage(formattedMessage)); // Already formatted
    }

    public void toggleClanPvp(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            ChatUtils.sendMessages(player, plugin, "messages.not_in_clan");
            return;
        }

        if (!clan.getLeader().equals(player.getUniqueId())) {
            ChatUtils.sendMessages(player, plugin, "messages.no_pvp_toggle_permission");
            return;
        }

        boolean newPvpState = !clan.isPvpEnabled();
        clan.setPvpEnabled(newPvpState);
        dataStorage.updateClan(clan);

        String messagePath = newPvpState ? "messages.clan_pvp_enabled" : "messages.clan_pvp_disabled";
        String formattedClanMessage = ChatUtils.getFormattedString(plugin, messagePath, "{clan_name}", clan.getName());

        clan.getMembers().stream()
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .forEach(member -> member.sendMessage(formattedClanMessage)); // Already formatted
    }

    // Method to get or create clan inventory
    public Inventory getClanInventory(Clan clan) {
        String clanNameLower = clan.getName().toLowerCase();
        if (clanInventories.containsKey(clanNameLower)) {
            return clanInventories.get(clanNameLower);
        } else {
            int size = plugin.getConfig().getInt("clan_storage.default_size", 27);
            if (size % 9 != 0 || size > 54) size = 27;

            String inventoryTitle = ChatUtils.getFormattedString(plugin, "messages.clan_storage_title", "{clan_name}", clan.getName());
            Inventory inv = Bukkit.createInventory(null, size, inventoryTitle); // Title is already formatted
            clanInventories.put(clanNameLower, inv);
            return inv;
        }
    }

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
        plugin.getLogger().info(plugin.getConfig().getString("messages.log_saving_all_inventories", "Saving all loaded clan inventories..."));
        for (Map.Entry<String, Clan> entry : clansMap.entrySet()) {
            Clan clan = entry.getValue();
            // Ensure inventory is loaded if it wasn't already (though usually it would be if accessed)
            // Inventory inv = getClanInventory(clan); // This might create a new one if not loaded
            Inventory inv = clanInventories.get(clan.getName().toLowerCase()); // More direct
            if (inv != null) { // Only save if it was loaded/created
                 saveClanInventory(clan);
            }
        }
        plugin.getLogger().info(plugin.getConfig().getString("messages.log_inventories_saving_complete", "Clan inventories saving complete."));
    }
}
