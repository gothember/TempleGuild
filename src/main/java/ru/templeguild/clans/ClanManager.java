package ru.templeguild.clans;

import ru.templeguild.TempleGuild;
import ru.templeguild.storage.DataStorage;
import org.bukkit.entity.Player; // Added import for Player
import org.bukkit.Bukkit; // Added for Bukkit.getPlayer
import ru.templeguild.utils.ChatUtils; // Added for ChatUtils
import org.bukkit.inventory.Inventory; // Added for Inventory
import org.bukkit.inventory.ItemStack; // Added for ItemStack
import org.bukkit.inventory.meta.ItemMeta; // Added for ItemMeta
import org.bukkit.Material; // Added for Material
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

    /**
     * Internal method to handle removing a player from a clan.
     * Updates clan object, data storage, caches, and chat toggle.
     * Does NOT handle disbanding if leader leaves or is last member - that's for the caller to decide.
     *
     * @param playerUUID The UUID of the player to remove.
     * @param clan The clan from which the player is to be removed.
     */
    private void removePlayerFromClanInternal(UUID playerUUID, Clan clan) {
        if (clan == null || !clan.isMember(playerUUID)) {
            plugin.getLogger().warning("Attempted to remove player " + playerUUID + " from clan " + (clan != null ? clan.getName() : "null") + " but they are not a member or clan is null.");
            return;
        }

        String clanName = clan.getName();

        clan.removeMember(playerUUID);
        dataStorage.removePlayerFromClan(playerUUID);

        plugin.getLogger().info("Player " + playerUUID + " removed from clan " + clanName);
    }

    // This method is for when a player chooses to leave.
    public void playerLeaveClan(Player player) {
        Clan clan = getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            ChatUtils.sendMessages(player, plugin, "messages.not_in_clan");
            return;
        }

        UUID playerUUID = player.getUniqueId();

        if (clan.getLeader().equals(playerUUID)) {
            if (clan.getMembers().size() == 1) {
                ChatUtils.sendMessages(player, plugin, "messages.clan_leave_leader_last_member_disband", "{clan_name}", clan.getName());
                disbandClan(clan.getName(), player);
                return;
            } else {
                ChatUtils.sendMessages(player, plugin, "messages.clan_leave_leader_must_disband_or_transfer", "{clan_name}", clan.getName());
                return;
            }
        }

        removePlayerFromClanInternal(playerUUID, clan);

        ChatUtils.sendMessages(player, plugin, "messages.clan_leave_success", "{clan_name}", clan.getName());

        String leftMessage = ChatUtils.getFormattedString(plugin, "messages.clan_member_left_notification",
                "{player_name}", player.getName(),
                "{clan_name}", clan.getName()
        );
        clan.getMembers().stream()
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .forEach(member -> member.sendMessage(leftMessage));
    }

    public void disbandClan(String clanName, CommandSender initiator) {
        Clan clan = getClan(clanName);
        if (clan == null) {
            if (initiator != null) {
                 ChatUtils.sendMessages(initiator, plugin, "messages.clan_not_found_for_disband", "{clan_name}", clanName);
            } else {
                plugin.getLogger().warning("Attempted to disband non-existent clan: " + clanName);
            }
            return;
        }

        plugin.getLogger().info("Disbanding clan: " + clan.getName() + (initiator != null ? " by " + initiator.getName() : ""));

        List<UUID> membersToNotify = new ArrayList<>(clan.getMembers());
        String disbandedMessage = ChatUtils.getFormattedString(plugin, "messages.clan_disbanded_notification_members", "{clan_name}", clan.getName());
        for (UUID memberUUID : membersToNotify) {
            Player memberPlayer = Bukkit.getPlayer(memberUUID);
            if (memberPlayer != null && memberPlayer.isOnline()) {
                memberPlayer.sendMessage(disbandedMessage);
            }
        }

        for (UUID memberUUID : new ArrayList<>(clan.getMembers())) {
            removePlayerFromClanInternal(memberUUID, clan);
        }

        if (clanInventories.containsKey(clan.getName().toLowerCase())) {
            clan.setSerializedStorage(null);
            clanInventories.remove(clan.getName().toLowerCase());
            plugin.getLogger().info("Removed in-memory storage for disbanded clan: " + clan.getName());
        }

        dataStorage.deleteClan(clan.getName());
        clansMap.remove(clan.getName().toLowerCase());


        if (initiator != null) {
            ChatUtils.sendMessages(initiator, plugin, "messages.clan_disband_success", "{clan_name}", clan.getName());
        }
        plugin.getLogger().info("Clan " + clan.getName() + " disbanded successfully.");
    }

    public void kickPlayerFromClan(Player kicker, Player targetToKick, Clan clan) {
        // Double check conditions, though command should pre-validate most
        if (!clan.getLeader().equals(kicker.getUniqueId())) {
            ChatUtils.sendMessages(kicker, plugin, "messages.clan_kick_no_leader_permission"); // Should be caught by command too
            return;
        }
        if (targetToKick.getUniqueId().equals(kicker.getUniqueId())) {
            ChatUtils.sendMessages(kicker, plugin, "messages.clan_kick_cannot_kick_self"); // Should be caught by command
            return;
        }
        if (!clan.isMember(targetToKick.getUniqueId())) {
            ChatUtils.sendMessages(kicker, plugin, "messages.clan_kick_target_not_in_your_clan", "{target_player_name}", targetToKick.getName()); // Should be caught by command
            return;
        }
        if (targetToKick.getUniqueId().equals(clan.getLeader())) {
            // This case should technically not happen if kicker is leader and target is leader (means target == kicker)
            // But as a safeguard if officer roles are added later and can kick members but not leader.
            ChatUtils.sendMessages(kicker, plugin, "messages.clan_kick_cannot_kick_leader");
            return;
        }


        // Perform the removal
        removePlayerFromClanInternal(targetToKick.getUniqueId(), clan);

        // Send confirmation to kicker
        ChatUtils.sendMessages(kicker, plugin, "messages.clan_kick_success_kicker", "{target_player_name}", targetToKick.getName(), "{clan_name}", clan.getName());

        // Send notification to the kicked player
        if (targetToKick.isOnline()) { // Check if still online
            ChatUtils.sendMessages(targetToKick, plugin, "messages.clan_kick_notification_kicked_player", "{clan_name}", clan.getName(), "{kicker_name}", kicker.getName());
        }

        // Notify other clan members
        String kickNotificationMessage = ChatUtils.getFormattedString(plugin, "messages.clan_kick_notification_members",
                "{target_player_name}", targetToKick.getName(),
                "{kicker_name}", kicker.getName()
        );
        clan.getMembers().stream() // targetToKick is already removed from clan.getMembers()
            .map(Bukkit::getPlayer)
            .filter(java.util.Objects::nonNull)
            .forEach(member -> member.sendMessage(kickNotificationMessage));
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

    private int getMaxMembers() {
        int max = plugin.getConfig().getInt("clan_settings.max_members", 10);
        if (max < 0) {
            plugin.getLogger().warning(ChatUtils.getFormattedString(plugin, "messages.max_members_config_invalid"));
            return 0;
        }
        return max;
    }

    public void sendInvite(Clan clan, Player inviter, Player invitedPlayer) {
        // Existing checks
        if (clan.isMember(invitedPlayer.getUniqueId())) {
            ChatUtils.sendMessages(inviter, plugin, "messages.invite_already_member", "{player_name}", invitedPlayer.getName());
            return;
        }
        if (getClanByPlayer(invitedPlayer.getUniqueId()) != null) {
            ChatUtils.sendMessages(inviter, plugin, "messages.invite_player_in_another_clan", "{player_name}", invitedPlayer.getName());
            return;
        }

        // New check: Max members
        int maxMembers = getMaxMembers();
        if (maxMembers > 0 && clan.getMembers().size() >= maxMembers) {
            ChatUtils.sendMessages(inviter, plugin, "messages.clan_is_full_on_invite", "{clan_name}", clan.getName());
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

        Clan clanToJoin = getClan(acceptedInvite.getClanName());
        if (clanToJoin == null) {
            ChatUtils.sendMessages(player, plugin, "messages.clan_disbanded_on_accept", "{clan_name}", acceptedInvite.getClanName());
            invites.remove(acceptedInvite);
            if (invites.isEmpty()) pendingInvites.remove(player.getUniqueId());
            return false;
        }

        if (getClanByPlayer(player.getUniqueId()) != null) { // Check if player joined another clan while invite was pending
            ChatUtils.sendMessages(player, plugin, "messages.already_in_clan_on_accept");
            invites.remove(acceptedInvite);
            if (invites.isEmpty()) pendingInvites.remove(player.getUniqueId());
            return false;
        }

        int maxMembers = getMaxMembers();
        if (maxMembers > 0 && clanToJoin.getMembers().size() >= maxMembers) {
            ChatUtils.sendMessages(player, plugin, "messages.clan_is_full_on_accept", "{clan_name}", clanToJoin.getName());
            invites.remove(acceptedInvite);
            if (invites.isEmpty()) pendingInvites.remove(player.getUniqueId());
            return false;
        }

        dataStorage.addPlayerToClan(player.getUniqueId(), clanToJoin.getName());
        clanToJoin.addMember(player.getUniqueId());
        playerClanCache.put(player.getUniqueId(), clanToJoin.getName().toLowerCase());

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
            // Try to load from clan's serialized data first if it exists but isn't in memory map yet
            // This case should ideally be covered by loadClans(), but as a safeguard:
            if (clan.getSerializedStorage() != null && !clan.getSerializedStorage().isEmpty()) {
                try {
                    String inventoryTitle = ChatUtils.getFormattedString(plugin, "messages.clan_storage_title", "{clan_name}", clan.getName());
                    Inventory inv = InventoryUtils.base64ToInventory(clan.getSerializedStorage(), inventoryTitle);
                    clanInventories.put(clanNameLower, inv); // Cache it
                    return inv;
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not deserialize inventory for clan " + clan.getName() + " during getClanInventory. Creating new.", e);
                    // Fall through to create a new one if deserialization fails
                }
            }

            // Create a new inventory
            int size = plugin.getConfig().getInt("clan_storage.default_size", 27);
            if (size % 9 != 0 || size <= 0 || size > 54) { // Ensure size is valid (multiple of 9, positive, not > 54)
                plugin.getLogger().warning("Invalid clan_storage.default_size: " + size + ". Defaulting to 27.");
                size = 27;
            }

            String inventoryTitle = ChatUtils.getFormattedString(plugin, "messages.clan_storage_title", "{clan_name}", clan.getName());
            Inventory inv = Bukkit.createInventory(null, size, inventoryTitle);

            // Check if we should fill the new inventory with a filler item
            if (plugin.getConfig().getBoolean("clan_storage.fill_new_storage_with_filler_item", false)) {
                String materialName = plugin.getConfig().getString("clan_storage.filler_item.material", "GRAY_STAINED_GLASS_PANE");
                Material fillerMaterial = Material.matchMaterial(materialName);
                if (fillerMaterial == null) {
                    plugin.getLogger().warning("Invalid material specified for clan_storage.filler_item.material: " + materialName + ". Using GRAY_STAINED_GLASS_PANE as fallback.");
                    fillerMaterial = Material.GRAY_STAINED_GLASS_PANE;
                }

                ItemStack fillerItem = new ItemStack(fillerMaterial);
                ItemMeta meta = fillerItem.getItemMeta();

                if (meta != null) {
                    String itemName = plugin.getConfig().getString("clan_storage.filler_item.name");
                    if (itemName != null && !itemName.isEmpty()) {
                        meta.setDisplayName(ChatUtils.format(itemName));
                    }

                    List<String> loreLines = plugin.getConfig().getStringList("clan_storage.filler_item.lore");
                    if (loreLines != null && !loreLines.isEmpty()) {
                        List<String> formattedLore = new ArrayList<>();
                        for (String line : loreLines) {
                            formattedLore.add(ChatUtils.format(line));
                        }
                        meta.setLore(formattedLore);
                    }
                    fillerItem.setItemMeta(meta);
                }

                for (int i = 0; i < inv.getSize(); i++) {
                    inv.setItem(i, fillerItem.clone());
                }
            }

            clanInventories.put(clanNameLower, inv);
            saveClanInventory(clan); // Save the newly created (and possibly filled) inventory

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

    public void incrementClanKills(String clanName, int amount) {
        Clan clan = getClan(clanName);
        if (clan != null) {
            clan.incrementKills(amount);
            dataStorage.updateClanKills(clan.getName(), clan.getKills());
        }
    }

    public Map<String, Integer> getTopClans(int limit) {
        return dataStorage.getTopClansByKills(limit);
    }
}
