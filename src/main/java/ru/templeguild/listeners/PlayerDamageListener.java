package ru.templeguild.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.templeguild.TempleGuild;
import ru.templeguild.clans.Clan;
import ru.templeguild.clans.ClanManager;
import ru.templeguild.utils.ChatUtils; // Added for optional message

public class PlayerDamageListener implements Listener {

    private final ClanManager clanManager;
    private final TempleGuild plugin; // Added to access config

    public PlayerDamageListener(TempleGuild plugin) {
        this.plugin = plugin; // Store plugin instance
        this.clanManager = plugin.getClanManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return; // Only interested in Player vs Player damage
        }

        Player victim = (Player) event.getEntity();
        Player attacker = (Player) event.getDamager();

        Clan victimClan = clanManager.getClanByPlayer(victim.getUniqueId());
        Clan attackerClan = clanManager.getClanByPlayer(attacker.getUniqueId());

        // Check if both players are in the same clan
        if (victimClan != null && victimClan.equals(attackerClan)) {
            // Both in the same clan, check clan's PvP status
            if (!victimClan.isPvpEnabled()) {
                event.setCancelled(true);
                ChatUtils.sendMessages(attacker, plugin, "messages.pvp_disabled_feedback_attacker");
            }
        }
    }
}
