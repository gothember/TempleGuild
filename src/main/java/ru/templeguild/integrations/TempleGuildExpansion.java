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
            return playerClan != null ? playerClan.getName() : plugin.getConfig().getString("placeholderapi.no_clan_name", "No Clan");
        }

        if (params.equalsIgnoreCase("in_clan")) {
            return playerClan != null ? "true" : "false";
        }

        if (params.equalsIgnoreCase("check_pvp")) {
            if (playerClan != null) {
                return playerClan.isPvpEnabled() ? "true" : "false";
            }
            return plugin.getConfig().getString("placeholderapi.pvp_status_no_clan", "false"); // Or some other default
        }

        if (params.equalsIgnoreCase("leader_name")) {
            if (playerClan != null) {
                OfflinePlayer leader = Bukkit.getOfflinePlayer(playerClan.getLeader());
                return leader != null ? leader.getName() : plugin.getConfig().getString("placeholderapi.leader_name_unknown", "Unknown");
            }
            return plugin.getConfig().getString("placeholderapi.leader_name_no_clan", "No Clan");
        }

        return null; // Placeholder not found
    }
}
