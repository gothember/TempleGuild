package ru.templeguild.utils;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender; // For sending messages directly
import ru.templeguild.TempleGuild; // For accessing config

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public static String format(String message) {
        if (message == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer(message.length() + 4 * 8); // Pre-size for hex
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.COLOR_CHAR + "x"
                    + ChatColor.COLOR_CHAR + group.charAt(0) + ChatColor.COLOR_CHAR + group.charAt(1)
                    + ChatColor.COLOR_CHAR + group.charAt(2) + ChatColor.COLOR_CHAR + group.charAt(3)
                    + ChatColor.COLOR_CHAR + group.charAt(4) + ChatColor.COLOR_CHAR + group.charAt(5)
            );
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    // New method/overload for handling lists and sending to player
    public static void sendMessages(CommandSender sender, TempleGuild plugin, String configPath, String... replacements) {
        Object messageOrList = plugin.getConfig().get(configPath);

        if (messageOrList instanceof String) {
            String message = (String) messageOrList;
            for (int i = 0; i < replacements.length; i += 2) {
                if (i + 1 < replacements.length) {
                    message = message.replace(replacements[i], replacements[i+1]);
                }
            }
            sender.sendMessage(format(message));
        } else if (messageOrList instanceof List) {
            List<?> list = (List<?>) messageOrList;
            for (Object lineObj : list) {
                if (lineObj instanceof String) {
                    String line = (String) lineObj;
                    for (int i = 0; i < replacements.length; i += 2) {
                        if (i + 1 < replacements.length) {
                            line = line.replace(replacements[i], replacements[i+1]);
                        }
                    }
                    sender.sendMessage(format(line));
                }
            }
        } else {
            // Fallback or error: message not found or incorrect type
            String defaultMessage = "&cMessage not found or has incorrect type in config: " + configPath;
            if (messageOrList == null && plugin.getConfig().getDefaults() != null) {
                 Object defaultObj = plugin.getConfig().getDefaults().get(configPath);
                 if(defaultObj instanceof String) {
                    String tempMessage = (String) defaultObj;
                     for (int i = 0; i < replacements.length; i += 2) {
                         if (i + 1 < replacements.length) {
                             tempMessage = tempMessage.replace(replacements[i], replacements[i+1]);
                         }
                     }
                     sender.sendMessage(format(tempMessage + " &7(default, path missing)"));
                     return;
                 } else if (defaultObj instanceof List) {
                     List<?> defaultList = (List<?>) defaultObj;
                     for(Object lineObj : defaultList) {
                         if(lineObj instanceof String) {
                             String line = (String) lineObj;
                             for (int i = 0; i < replacements.length; i += 2) {
                                 if (i + 1 < replacements.length) {
                                     line = line.replace(replacements[i], replacements[i+1]);
                                 }
                             }
                             sender.sendMessage(format(line + " &7(default, path missing)"));
                         }
                     }
                     return;
                 }
            }
            sender.sendMessage(format(defaultMessage));
        }
    }

    // Helper to get a formatted string (single or first line of list) for non-chat uses
    // (e.g. inventory titles, PAPI placeholders if they shouldn't be multi-line)
    public static String getFormattedString(TempleGuild plugin, String configPath, String... replacements) {
        Object messageOrList = plugin.getConfig().get(configPath);
        String message = "";

        if (messageOrList instanceof String) {
            message = (String) messageOrList;
        } else if (messageOrList instanceof List) {
            List<?> list = (List<?>) messageOrList;
            if (!list.isEmpty() && list.get(0) instanceof String) {
                message = (String) list.get(0);
            }
        } else {
            // Try to get default from config defaults if path is completely missing
            if (plugin.getConfig().getDefaults() != null) {
                 Object defaultObj = plugin.getConfig().getDefaults().get(configPath);
                 if(defaultObj instanceof String) message = (String) defaultObj + " (default)";
                 else if (defaultObj instanceof List && !((List<?>)defaultObj).isEmpty() && ((List<?>)defaultObj).get(0) instanceof String) {
                     message = (String)((List<?>)defaultObj).get(0) + " (default)";
                 } else {
                     message = "&cConfig missing: " + configPath; // Path exists but wrong type or default also missing
                 }
            } else {
                message = "&cConfig missing: " + configPath; // No defaults to check
            }
        }

        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i+1]);
            }
        }
        return format(message);
    }
}
