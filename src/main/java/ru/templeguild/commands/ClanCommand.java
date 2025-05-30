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
import org.bukkit.configuration.ConfigurationSection; // Added for help entries

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
            ChatUtils.sendMessages(sender, plugin, "messages.players_only");
            return true;
        }

        // Allow console to use /clan help or list clans, etc. if we add such features.
        // For now, many commands are player-centric.
        // Let's allow console for help.
        if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
            displayHelp(sender);
            return true;
        }
        if (!(sender instanceof Player)) {
            ChatUtils.sendMessages(sender, plugin, "messages.players_only_for_most_commands");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            displayHelp(player); // Display help if just /clan is typed
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("help")) {
            // Potentially add help for a specific command: /clan help <subcommand_name>
            // For now, general help is displayed.
            displayHelp(player);
            return true;
        } else if (subCommand.equals("create")) {
            if (args.length < 2) {
                ChatUtils.sendMessages(player, plugin, "messages.clan_create_usage");
                return true;
            }
            if (!player.hasPermission("templeguild.clan.create")) {
                 ChatUtils.sendMessages(player, plugin, "messages.no_permission");
                 return true;
            }

            String clanName = args[1];

            if (clanName.length() < 3 || clanName.length() > 16) {
                ChatUtils.sendMessages(player, plugin, "messages.clan_name_length");
                return true;
            }
            if (!clanName.matches("^[a-zA-Z0-9_]+$")) {
                ChatUtils.sendMessages(player, plugin, "messages.clan_name_invalid_chars");
                return true;
            }

            if (clanManager.isClanNameTaken(clanName)) {
                ChatUtils.sendMessages(player, plugin, "messages.clan_name_taken");
                return true;
            }

            if (clanManager.getClanByPlayer(player.getUniqueId()) != null) {
                ChatUtils.sendMessages(player, plugin, "messages.already_in_clan");
                return true;
            }

            clanManager.createClan(clanName, player.getUniqueId());
            ChatUtils.sendMessages(player, plugin, "messages.clan_created", "{clan_name}", clanName);
            return true;
        } else if (subCommand.equals("invite")) {
            if (!player.hasPermission("templeguild.clan.invite")) {
                 ChatUtils.sendMessages(player, plugin, "messages.no_permission");
                 return true;
            }
            Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
            if (playerClan == null) {
                ChatUtils.sendMessages(player, plugin, "messages.not_in_clan");
                return true;
            }

            if (args.length < 2) {
                ChatUtils.sendMessages(player, plugin, "messages.clan_invite_usage");
                return true;
            }
            Player invitedPlayer = plugin.getServer().getPlayerExact(args[1]);
            if (invitedPlayer == null || !invitedPlayer.isOnline()) {
                ChatUtils.sendMessages(player, plugin, "messages.player_not_online", "{player_name}", args[1]);
                return true;
            }
            if (invitedPlayer.equals(player)) {
                ChatUtils.sendMessages(player, plugin, "messages.cannot_invite_self");
                return true;
            }
            clanManager.sendInvite(playerClan, player, invitedPlayer);
            return true;
        } else if (subCommand.equals("accept")) {
             if (!player.hasPermission("templeguild.clan.accept")) {
                 ChatUtils.sendMessages(player, plugin, "messages.no_permission");
                 return true;
            }
            if (args.length < 2) {
                ChatUtils.sendMessages(player, plugin, "messages.clan_accept_usage");
                return true;
            }
            String clanToAccept = args[1];
            clanManager.acceptInvite(player, clanToAccept);
            return true;
        } else if (subCommand.equals("decline")) {
             if (!player.hasPermission("templeguild.clan.decline")) {
                 ChatUtils.sendMessages(player, plugin, "messages.no_permission");
                 return true;
            }
            if (args.length < 2) {
                ChatUtils.sendMessages(player, plugin, "messages.clan_decline_usage");
                return true;
            }
            String clanToDecline = args[1];
            clanManager.declineInvite(player, clanToDecline);
            return true;
        } else if (subCommand.equals("chat") || subCommand.equals("c")) {
            if (!player.hasPermission("templeguild.clan.chat")) {
                 ChatUtils.sendMessages(player, plugin, "messages.no_permission");
                 return true;
            }
            if (clanManager.getClanByPlayer(player.getUniqueId()) == null) { // Handled by ClanManager too, but good for early exit
                ChatUtils.sendMessages(player, plugin, "messages.not_in_clan_to_chat");
                return true;
            }

            if (args.length == 1) {
                clanManager.toggleClanChat(player);
            } else {
                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                clanManager.sendClanChatMessage(player, message);
            }
            return true;
        } else if (subCommand.equals("pvp")) {
            if (!player.hasPermission("templeguild.clan.pvp.toggle")) {
                 ChatUtils.sendMessages(player, plugin, "messages.no_permission");
                 return true;
            }
            if (clanManager.getClanByPlayer(player.getUniqueId()) == null) { // Handled by ClanManager too
                ChatUtils.sendMessages(player, plugin, "messages.not_in_clan");
                return true;
            }
            clanManager.toggleClanPvp(player);
            return true;
        } else if (subCommand.equals("storage")) {
            if (!player.hasPermission("templeguild.clan.storage.access")) {
                 ChatUtils.sendMessages(player, plugin, "messages.no_permission");
                 return true;
            }
            Clan playerClan = clanManager.getClanByPlayer(player.getUniqueId());
            if (playerClan == null) {
                ChatUtils.sendMessages(player, plugin, "messages.not_in_clan");
                return true;
            }

            Inventory clanInv = clanManager.getClanInventory(playerClan);
            player.openInventory(clanInv);
            return true;
        }

        ChatUtils.sendMessages(player, plugin, "messages.unknown_subcommand", "{command}", subCommand);
        return true;
    }

    private void displayHelp(CommandSender sender) {
        ChatUtils.sendMessages(sender, plugin, "messages.clan_help_header");

        ConfigurationSection helpEntries = plugin.getConfig().getConfigurationSection("messages.clan_help_entries");
        if (helpEntries != null) {
            List<String> formatList = plugin.getConfig().getStringList("messages.clan_help_format");
            if (formatList.isEmpty()) { // Fallback if format is missing
                formatList.add("&6/clan {command} {arguments} &7- {description}");
            }

            for (String key : helpEntries.getKeys(false)) {
                String permission = helpEntries.getString(key + ".permission");
                if (permission != null && !permission.isEmpty() && !(sender instanceof org.bukkit.command.ConsoleCommandSender) && !sender.hasPermission(permission)) {
                    // Skip showing commands they can't use, unless it's console
                    continue;
                }

                String commandName = key;
                String arguments = helpEntries.getString(key + ".arguments", "");
                String description = helpEntries.getString(key + ".description", "No description available.");

                for (String formatLine : formatList) {
                    String helpLine = formatLine.replace("{command}", commandName)
                                                .replace("{arguments}", arguments)
                                                .replace("{description}", description);
                    sender.sendMessage(ChatUtils.format(helpLine)); // Use ChatUtils.format directly for single lines from loop
                }
            }
        } else {
            ChatUtils.sendMessages(sender, plugin, "messages.clan_help_unavailable");
        }

        ChatUtils.sendMessages(sender, plugin, "messages.clan_help_footer");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subcommands = Arrays.asList("create", "invite", "accept", "decline", "chat", "pvp", "storage", "help" /*, help, leave, disband, etc. */);
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
