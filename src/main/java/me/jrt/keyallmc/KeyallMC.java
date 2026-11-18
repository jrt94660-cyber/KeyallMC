package me.jrt.keyallmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class KeyallMC extends JavaPlugin {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private FileConfiguration messages;
    private FileConfiguration keyallSettings;
    private FileConfiguration messageSettings;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("settings/keyall.yml");
        saveResourceIfMissing("settings/message-settings.yml");
        loadSeparatedConfigs();

        PluginCommand command = getCommand("keyall");
        if (command != null) command.setExecutor(this::onCommand);
        else getLogger().severe("Command 'keyall' is missing from plugin.yml; plugin remains enabled but command is unavailable.");

        if (!getConfig().getBoolean("enabled", true)) {
            getLogger().info("KeyallMC is disabled in config.yml.");
        } else {
            getLogger().info("KeyallMC enabled.");
        }
    }

    private void saveResourceIfMissing(String path) {
        File target = new File(getDataFolder(), path);
        if (!target.exists()) saveResource(path, false);
    }

    private void loadSeparatedConfigs() {
        messages = loadYamlSafely("messages.yml");
        keyallSettings = loadYamlSafely("settings/keyall.yml");
        messageSettings = loadYamlSafely("settings/message-settings.yml");
    }

    private FileConfiguration loadYamlSafely(String path) {
        File file = new File(getDataFolder(), path);
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (Exception ex) {
            getLogger().warning("Could not read " + path + ": " + ex.getMessage() + ". Using defaults for this file.");
            try (var stream = getResource(path)) {
                if (stream == null) return new YamlConfiguration();
                return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (Exception fallbackEx) {
                getLogger().warning("Bundled defaults unavailable for " + path + ": " + fallbackEx.getMessage());
                return new YamlConfiguration();
            }
        }
    }

    private boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("keyallmc.admin")) {
            sendMessage(sender, "no-permission");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadSeparatedConfigs();
            sendMessage(sender, "reloaded");
            return true;
        }
        if (!getConfig().getBoolean("enabled", true)) {
            sendMessage(sender, "disabled");
            return true;
        }
        if (args.length < 1 || args.length > 2) {
            sendMessage(sender, "usage");
            return true;
        }

        String key = args[0].trim();
        if (key.isEmpty() || key.equalsIgnoreCase("reload")) {
            sendMessage(sender, "unknown-key");
            return true;
        }
        int amount = 1;
        if (args.length == 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount <= 0) throw new NumberFormatException("not positive");
            } catch (NumberFormatException ex) {
                sendMessage(sender, "invalid-amount");
                return true;
            }
        }
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            sendMessage(sender, "no-players");
            return true;
        }

        String rewardCommand = keyallSettings.getString("reward-command", "crate givekey %player% %key% %amount%");
        if (rewardCommand == null || rewardCommand.isBlank()) {
            getLogger().warning("settings/keyall.yml reward-command is blank. No rewards were run.");
            sendMessage(sender, "usage");
            return true;
        }

        int recipients = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String expanded = rewardCommand.replace("%player%", player.getName())
                    .replace("%key%", key)
                    .replace("%amount%", Integer.toString(amount));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), expanded);
            recipients++;
        }

        String success = message("success").replace("%amount%", Integer.toString(amount))
                .replace("%key%", key).replace("%players%", Integer.toString(recipients));
        sendRaw(sender, success);
        if (keyallSettings.getBoolean("announce", true)) {
            String announcement = keyallSettings.getString("announcement", "")
                    .replace("%amount%", Integer.toString(amount))
                    .replace("%key%", key).replace("%players%", Integer.toString(recipients));
            if (!announcement.isBlank()) {
                for (Player online : Bukkit.getOnlinePlayers()) sendRaw(online, announcement);
            }
        }
        return true;
    }

    private void sendMessage(CommandSender sender, String key) {
        sendRaw(sender, message(key));
    }

    private String message(String key) {
        String value = messages.getString(key);
        if (value == null) return "<red>Missing message: " + key + "</red>";
        return value;
    }

    private void sendRaw(CommandSender sender, String raw) {
        if (sender instanceof Player player) {
            String destination = messageSettings.getString("destination", "ACTION_BAR").toUpperCase(Locale.ROOT);
            Component component = deserialize(raw);
            switch (destination) {
                case "CHAT" -> player.sendMessage(component);
                case "TITLE" -> player.showTitle(net.kyori.adventure.title.Title.title(
                        component, deserialize(messageSettings.getString("title-subtitle", "")),
                        net.kyori.adventure.title.Title.Times.times(
                                java.time.Duration.ofMillis(Math.max(0, messageSettings.getInt("title-fade-in", 5)) * 50L),
                                java.time.Duration.ofMillis(Math.max(0, messageSettings.getInt("title-stay", 40)) * 50L),
                                java.time.Duration.ofMillis(Math.max(0, messageSettings.getInt("title-fade-out", 10)) * 50L))));
                default -> player.sendActionBar(component);
            }
        } else {
            sender.sendMessage(deserialize(raw));
        }
    }
}
