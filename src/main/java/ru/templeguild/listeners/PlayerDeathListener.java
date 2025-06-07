package ru.templeguild.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.templeguild.TempleGuild;
import ru.templeguild.clans.Clan;
import ru.templeguild.clans.ClanManager;
import ru.templeguild.utils.ChatUtils; // Required for sending messages

public class PlayerDeathListener implements Listener {

    private final TempleGuild plugin;
    private final ClanManager clanManager;

    public PlayerDeathListener(TempleGuild plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller(); // This can be null if death was not by a player

        if (killer == null) {
            return; // Not a PvP kill
        }

        // Optional: Don't record self-kills or kills by console/commands if killer is not really a player instance
        // (killer == victim check is a good idea if self-kills shouldn't count)
        if (killer.equals(victim)) {
            return; // Self-kill, don't count for clan top
        }

        Clan killerClan = clanManager.getClanByPlayer(killer.getUniqueId());

        if (killerClan == null) {
            return; // Killer is not in a clan
        }

        // Optional: Prevent kill count if victim is in the same clan (friendly fire)
        boolean countFriendlyFire = plugin.getConfig().getBoolean("clan_settings.kill_tracking.count_friendly_fire", false);
        if (!countFriendlyFire) {
            Clan victimClan = clanManager.getClanByPlayer(victim.getUniqueId());
            if (victimClan != null && victimClan.getName().equalsIgnoreCase(killerClan.getName())) {
                // Friendly fire, and it's configured not to count
                return;
            }
        }

        // Increment kills for the killer's clan
        clanManager.incrementClanKills(killerClan.getName(), 1);

        // Optional: Send a message to the killer
        boolean showKillMessage = plugin.getConfig().getBoolean("clan_settings.kill_tracking.show_kill_message_to_killer", true);
        if (showKillMessage) {
            ChatUtils.sendMessages(killer, plugin, "messages.clan_kill_awarded",
                "{victim_name}", victim.getName(),
                "{clan_name}", killerClan.getName()
            );
        }
    }
}
