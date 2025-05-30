package ru.templeguild.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.templeguild.TempleGuild;
import ru.templeguild.clans.ClanManager;

public class PlayerChatListener implements Listener {

    private final TempleGuild plugin;
    private final ClanManager clanManager;

    public PlayerChatListener(TempleGuild plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (clanManager.isClanChatToggled(player.getUniqueId())) {
            // Player has clan chat toggled on
            if (clanManager.getClanByPlayer(player.getUniqueId()) == null) {
                // Safety check: if somehow toggled but not in clan, untoggle and let normal chat proceed
                clanManager.removeFromClanChatToggleOnLeave(player.getUniqueId());
                return;
            }

            event.setCancelled(true); // Cancel normal chat message
            clanManager.sendClanChatMessage(player, event.getMessage());
        }
    }
}
