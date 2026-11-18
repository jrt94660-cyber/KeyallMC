package me.jrt.keyallmc;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class KeyallPlaceholder extends PlaceholderExpansion {
    private final KeyallMC plugin;
    public KeyallPlaceholder(KeyallMC plugin) { this.plugin = plugin; }
    @Override public @NotNull String getIdentifier() { return "keyallmc"; }
    @Override public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty() ? "KeyallMC" : plugin.getDescription().getAuthors().get(0);
    }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.toLowerCase(java.util.Locale.ROOT).startsWith("timer_")) {
            return plugin.timerPlaceholder(params.substring("timer_".length()));
        }
        return null;
    }
}
