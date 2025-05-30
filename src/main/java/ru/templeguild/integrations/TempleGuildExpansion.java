package ru.templeguild.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.templeguild.TempleGuild;
import ru.templeguild.clans.Clan;
import ru.templeguild.clans.ClanManager;

public class TempleGuildExpansion extends PlaceholderExpansion {

    private final TempleGuild plugin;
    private final ClanManager clanManager;

    public TempleGuildExpansion(TempleGuild plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
    }

    @Override
    public String getIdentifier() {
        return "tclan"; // This is the prefix, e.g., %tclan_name%
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Placeholders will be available even if the plugin is reloaded.
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (offlinePlayer == null) {
            return "";
        }
        // Player player = offlinePlayer.getPlayer(); // Can be null if offlinePlayer is actually offline

        Clan playerClan = clanManager.getClanByPlayer(offlinePlayer.getUniqueId());

        if (params.equalsIgnoreCase("name")) {
            return playerClan != null ? playerClan.getName() : ChatUtils.getFormattedString(plugin, "placeholderapi.no_clan_name");
        }

        if (params.equalsIgnoreCase("in_clan")) {
            return playerClan != null ? "true" : "false"; // This is fine as non-message
        }

        if (params.equalsIgnoreCase("check_pvp")) {
            if (playerClan != null) {
                return playerClan.isPvpEnabled() ? "true" : "false"; // Fine as non-message
            }
            return ChatUtils.getFormattedString(plugin, "placeholderapi.pvp_status_no_clan");
        }

        if (params.equalsIgnoreCase("leader_name")) {
            if (playerClan != null) {
                OfflinePlayer leader = Bukkit.getOfflinePlayer(playerClan.getLeader());
                return leader != null && leader.getName() != null ? leader.getName() : ChatUtils.getFormattedString(plugin, "placeholderapi.leader_name_unknown");
            }
            return ChatUtils.getFormattedString(plugin, "placeholderapi.leader_name_no_clan");
        }

        return null;
    }
}
