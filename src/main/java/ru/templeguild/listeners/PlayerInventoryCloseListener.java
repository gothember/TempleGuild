package ru.templeguild.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import ru.templeguild.TempleGuild;
import ru.templeguild.clans.Clan;
import ru.templeguild.clans.ClanManager;
import ru.templeguild.utils.ChatUtils; // For title comparison

public class PlayerInventoryCloseListener implements Listener { // Renamed for clarity

    private final ClanManager clanManager;
    private final TempleGuild plugin;

    public PlayerInventoryCloseListener(TempleGuild plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        Inventory closedInventory = event.getInventory();

        Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
        if (playerClan == null) return; // Not in a clan, or left while storage was open

        // Check if the closed inventory is a clan storage
        String expectedTitle = ChatUtils.getFormattedString(plugin, "messages.clan_storage_title", "{clan_name}", playerClan.getName());
        String actualTitle = event.getView().getTitle(); // Title from event is already formatted if it was set with formatted string

        if (actualTitle.equals(expectedTitle)) {
             Inventory managedInv = clanManager.getClanInventory(playerClan);
             if (closedInventory == managedInv) {
                clanManager.saveClanInventory(playerClan);
                ChatUtils.sendMessages(player, plugin, "messages.clan_storage_saved");
             }
        }
    }
}
