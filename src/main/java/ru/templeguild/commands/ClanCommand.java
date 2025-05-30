package ru.templeguild.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.templeguild.TempleGuild;
import ru.templeguild.clans.Clan;
import ru.templeguild.clans.ClanManager;
import ru.templeguild.utils.ChatUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ClanCommand implements CommandExecutor, TabCompleter {

    private final TempleGuild plugin;
    private final ClanManager clanManager;

    public ClanCommand(TempleGuild plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.players_only", "&cOnly players can use this command.")));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // Send help message (to be implemented later)
            player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_help", "&eUsage: /clan <subcommand>")));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("create")) {
            if (args.length < 2) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_create_usage", "&cUsage: /clan create <name>")));
                return true;
            }
            if (!player.hasPermission("templeguild.clan.create")) {
                 player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.no_permission", "&cYou don't have permission to do that.")));
                 return true;
            }

            String clanName = args[1];

            // Validate clan name (e.g., length, characters - basic for now)
            if (clanName.length() < 3 || clanName.length() > 16) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_name_length", "&cClan name must be between 3 and 16 characters.")));
                return true;
            }
            if (!clanName.matches("^[a-zA-Z0-9_]+$")) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_name_invalid_chars", "&cClan name can only contain letters, numbers, and underscores.")));
                return true;
            }

            if (clanManager.isClanNameTaken(clanName)) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_name_taken", "&cThat clan name is already taken.")));
                return true;
            }

            if (clanManager.getClanByPlayer(player.getUniqueId()) != null) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.already_in_clan", "&cYou are already in a clan.")));
                return true;
            }

            clanManager.createClan(clanName, player.getUniqueId());
            String createdMessage = plugin.getConfig().getString("messages.clan_created", "&#00FF00Your clan {clan_name} has been created!")
                                        .replace("{clan_name}", clanName);
            player.sendMessage(ChatUtils.format(createdMessage));
            return true;
        } else if (subCommand.equals("invite")) {
            if (!player.hasPermission("templeguild.clan.invite")) {
                 player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.no_permission", "&cYou don't have permission to do that.")));
                 return true;
            }
            Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
            if (playerClan == null) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.not_in_clan", "&cYou are not in a clan.")));
                return true;
            }
            // Add leader/officer permission check here later if needed
            // if (!playerClan.getLeader().equals(player.getUniqueId()) && !playerClan.isOfficer(player.getUniqueId())) {
            //    player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.no_invite_permission", "&cYou don't have permission to invite players to this clan.")));
            //    return true;
            // }
            if (args.length < 2) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_invite_usage", "&cUsage: /clan invite <player>")));
                return true;
            }
            Player invitedPlayer = plugin.getServer().getPlayerExact(args[1]); // Changed from Bukkit.getPlayerExact
            if (invitedPlayer == null || !invitedPlayer.isOnline()) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.player_not_online", "&cPlayer {player_name} is not online.")
                        .replace("{player_name}", args[1])));
                return true;
            }
            if (invitedPlayer.equals(player)) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.cannot_invite_self", "&cYou cannot invite yourself.")));
                return true;
            }
            clanManager.sendInvite(playerClan, player, invitedPlayer);
            return true;
        } else if (subCommand.equals("accept")) {
             if (!player.hasPermission("templeguild.clan.accept")) {
                 player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.no_permission", "&cYou don't have permission to do that.")));
                 return true;
            }
            if (args.length < 2) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_accept_usage", "&cUsage: /clan accept <clan_name>")));
                return true;
            }
            String clanToAccept = args[1];
            clanManager.acceptInvite(player, clanToAccept);
            return true;
        } else if (subCommand.equals("decline")) {
             if (!player.hasPermission("templeguild.clan.decline")) {
                 player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.no_permission", "&cYou don't have permission to do that.")));
                 return true;
            }
            if (args.length < 2) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.clan_decline_usage", "&cUsage: /clan decline <clan_name>")));
                return true;
            }
            String clanToDecline = args[1];
            clanManager.declineInvite(player, clanToDecline);
            return true;
        } else if (subCommand.equals("chat") || subCommand.equals("c")) { // Added alias "c"
            if (!player.hasPermission("templeguild.clan.chat")) {
                 player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.no_permission", "&cYou don't have permission to do that.")));
                 return true;
            }
            if (clanManager.getClanByPlayer(player.getUniqueId()) == null) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.not_in_clan_to_chat", "&cYou must be in a clan to use clan chat.")));
                return true;
            }

            if (args.length == 1) {
                // Toggle clan chat
                clanManager.toggleClanChat(player);
            } else {
                // Send a single clan chat message
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                clanManager.sendClanChatMessage(player, message);
            }
            return true;
        } else if (subCommand.equals("pvp")) {
            if (!player.hasPermission("templeguild.clan.pvp.toggle")) {
                 player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.no_permission", "&cYou don't have permission to do that.")));
                 return true;
            }
            // Ensure player is in a clan before attempting to toggle PvP
            if (clanManager.getClanByPlayer(player.getUniqueId()) == null) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.not_in_clan", "&cYou are not in a clan.")));
                return true;
            }
            clanManager.toggleClanPvp(player); // Logic is handled in ClanManager
            return true;
        } else if (subCommand.equals("storage")) {
            if (!player.hasPermission("templeguild.clan.storage.access")) {
                 player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.no_permission", "&cYou don't have permission to do that.")));
                 return true;
            }
            Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
            if (playerClan == null) {
                player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.not_in_clan", "&cYou are not in a clan.")));
                return true;
            }
            // Permission for specific storage actions (e.g. only leader can modify?) can be added here
            // For now, any member can access if they have the base permission.

            Inventory clanInv = clanManager.getClanInventory(playerClan);
            player.openInventory(clanInv);
            return true;
        }
        // ... other subcommands later

        player.sendMessage(ChatUtils.format(plugin.getConfig().getString("messages.unknown_subcommand", "&cUnknown subcommand. Use /clan help.")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("create", "invite", "accept", "decline", "chat", "pvp", "storage" /*, help, leave, disband, etc. */); // Added "accept", "decline"
            return subcommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                return Arrays.asList("<clan_name>");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
                return plugin.getServer().getOnlinePlayers().stream() // Changed from Bukkit.getOnlinePlayers()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("accept") || args[0].equalsIgnoreCase("decline"))) {
                // Suggest clan names from pending invites for the player
                // This requires ClanManager to expose a method to get pending invite names for a player
                // For now, simple placeholder:
                // List<String> pendingInviteClanNames = clanManager.getPendingInviteClanNamesForPlayer(((Player) sender).getUniqueId());
                // if (pendingInviteClanNames != null && !pendingInviteClanNames.isEmpty()) {
                //    return pendingInviteClanNames.stream()
                //            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                //            .collect(Collectors.toList());
                // }
            return Arrays.asList("<clan_name>");
        }
        // Add more tab completions for other commands later
        return new ArrayList<>();
    }
}
