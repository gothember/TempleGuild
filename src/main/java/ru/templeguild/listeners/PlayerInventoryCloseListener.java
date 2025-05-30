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
        // This comparison is a bit fragile if titles change dynamically beyond clan name.
        // A more robust way would be to tag inventories or check instance if inventories are unique.
        String expectedTitle = ChatUtils.format(plugin.getConfig().getString("messages.clan_storage_title", "&8Clan Storage: {clan_name}")
                                            .replace("{clan_name}", playerClan.getName()));
        String actualTitle = ChatUtils.format(event.getView().getTitle()); // event.getView().getTitle() is better

        if (actualTitle.equals(expectedTitle)) { // Check title first for performance
             // Check if this inventory instance is the one managed by ClanManager
             // This is a direct object comparison, safer.
             Inventory managedInv = clanManager.getClanInventory(playerClan);
             if (closedInventory == managedInv) { // Ensure it's the exact same inventory object
                clanManager.saveClanInventory(playerClan);
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_storage_saved", "&aClan storage saved.")));
             }
        }
    }
}
