package me.jrt.keyallmc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class KeyallMC extends JavaPlugin {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private FileConfiguration messages, keyallSettings, messageSettings;
    private final Map<String, Long> nextRuns = new ConcurrentHashMap<>();
    private BukkitTask timerTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("settings/keyall.yml");
        saveResourceIfMissing("settings/message-settings.yml");
        loadSeparatedConfigs();
        var command = getCommand("keyall");
        if (command != null) command.setExecutor(this::onCommand);
        if (getConfig().getBoolean("enabled", true)) startTimers();
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) new KeyallPlaceholder(this).register();
        getLogger().info("KeyallMC enabled.");
    }
    @Override public void onDisable() { if (timerTask != null) timerTask.cancel(); }

    private void saveResourceIfMissing(String path) {
        File f = new File(getDataFolder(), path); if (!f.exists()) saveResource(path, false);
    }
    private void loadSeparatedConfigs() {
        messages = loadYamlSafely("messages.yml"); keyallSettings = loadYamlSafely("settings/keyall.yml");
        messageSettings = loadYamlSafely("settings/message-settings.yml");
    }
    private FileConfiguration loadYamlSafely(String path) {
        try { return YamlConfiguration.loadConfiguration(new File(getDataFolder(), path)); }
        catch (Exception ex) {
            getLogger().warning("Invalid "+path+"; using bundled defaults: "+ex.getMessage());
            try (var in=getResource(path)) { return in==null?new YamlConfiguration():YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)); }
            catch(Exception fallback){ return new YamlConfiguration(); }
        }
    }
    public String timerPlaceholder(String id) {
        Long end=nextRuns.get(id.toLowerCase(Locale.ROOT)); if(end==null) return "N/A";
        long seconds=Math.max(0,(end-System.currentTimeMillis())/1000);
        return String.format("%02d:%02d:%02d",seconds/3600,(seconds%3600)/60,seconds%60);
    }
    private void startTimers() {
        if(timerTask!=null) timerTask.cancel(); nextRuns.clear();
        ConfigurationSection schedules=keyallSettings.getConfigurationSection("keyalls");
        if(schedules==null) return;
        for(String id:schedules.getKeys(false)) {
            ConfigurationSection s=schedules.getConfigurationSection(id);
            if(s!=null && s.getBoolean("enabled",true)) nextRuns.put(id.toLowerCase(Locale.ROOT),
                System.currentTimeMillis()+Math.max(1,s.getInt("interval-minutes",30))*60_000L);
        }
        timerTask=Bukkit.getScheduler().runTaskTimer(this,()->{
            if(!getConfig().getBoolean("enabled",true)) return;
            for(String id:nextRuns.keySet().toArray(String[]::new)) {
                ConfigurationSection s=keyallSettings.getConfigurationSection("keyalls."+id);
                if(s==null || !s.getBoolean("enabled",true)) continue;
                long remaining=nextRuns.getOrDefault(id,0L)-System.currentTimeMillis();
                if(remaining<=0) {
                    distribute(s.getString("key","key"),Math.max(1,s.getInt("amount",1)),id);
                    nextRuns.put(id,System.currentTimeMillis()+Math.max(1,s.getInt("interval-minutes",30))*60_000L);
                } else {
                    int announceSeconds=Math.max(0,s.getInt("announce-seconds",10));
                    if(remaining<=announceSeconds*1000L) {
                        String msg=keyallSettings.getString("timer-message","<yellow>You're about to get %key name% %amount%</yellow>")
                            .replace("%key name%",s.getString("key","key")).replace("%key%",s.getString("key","key"))
                            .replace("%amount%",Integer.toString(Math.max(1,s.getInt("amount",1))));
                        for(Player p:Bukkit.getOnlinePlayers()) p.sendActionBar(deserialize(msg));
                    }
                }
            }
        },20L,20L);
    }
    private void distribute(String key,int amount,String id) {
        String cmd=keyallSettings.getString("reward-command","smpcrate keyall keyall %player%").trim();
        if(cmd.isBlank()) { getLogger().warning("Reward command is blank; schedule "+id+" skipped."); return; }
        for(Player p:Bukkit.getOnlinePlayers()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
            cmd.replace("%player%",p.getName()).replace("%key%",key).replace("%amount%",String.valueOf(amount)));
        if(keyallSettings.getBoolean("announce",true)) {
            String ann=keyallSettings.getString("announcement","").replace("%key%",key).replace("%amount%",String.valueOf(amount)).replace("%players%",String.valueOf(Bukkit.getOnlinePlayers().size()));
            if(!ann.isBlank()) for(Player p:Bukkit.getOnlinePlayers()) sendRaw(p,ann);
        }
    }
    private boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if(!sender.hasPermission("keyallmc.admin")) { sendRaw(sender,message("no-permission")); return true; }
        if(args.length==1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig(); loadSeparatedConfigs(); startTimers(); sendRaw(sender,message("reloaded")); return true;
        }
        if(args.length==2 && args[0].equalsIgnoreCase("run")) {
            ConfigurationSection s=keyallSettings.getConfigurationSection("keyalls."+args[1]);
            if(s==null){sendRaw(sender,message("unknown-schedule"));return true;}
            distribute(s.getString("key","key"),Math.max(1,s.getInt("amount",1)),args[1]); sendRaw(sender,message("schedule-run")); return true;
        }
        if(!getConfig().getBoolean("enabled",true)){sendRaw(sender,message("disabled"));return true;}
        if(args.length<1 || args.length>2){sendRaw(sender,message("usage"));return true;}
        String key=args[0].trim(); int amount=1;
        if(args.length==2) try{amount=Integer.parseInt(args[1]);if(amount<=0)throw new NumberFormatException();}catch(NumberFormatException e){sendRaw(sender,message("invalid-amount"));return true;}
        if(Bukkit.getOnlinePlayers().isEmpty()){sendRaw(sender,message("no-players"));return true;}
        distribute(key,amount,"manual");
        sendRaw(sender,message("success").replace("%amount%",String.valueOf(amount)).replace("%key%",key).replace("%players%",String.valueOf(Bukkit.getOnlinePlayers().size())));
        return true;
    }
    private String message(String k){return messages.getString(k,"<red>Missing message: "+k+"</red>");}
    private void sendRaw(CommandSender sender,String raw){if(sender instanceof Player p) {
        String mode=messageSettings.getString("destination","ACTION_BAR").toUpperCase(Locale.ROOT); Component c=deserialize(raw);
        switch(mode){case "CHAT"->p.sendMessage(c);case "TITLE"->p.showTitle(net.kyori.adventure.title.Title.title(c,deserialize(messageSettings.getString("title-subtitle","")),net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(Math.max(0,messageSettings.getInt("title-fade-in",5))*50L),java.time.Duration.ofMillis(Math.max(0,messageSettings.getInt("title-stay",40))*50L),java.time.Duration.ofMillis(Math.max(0,messageSettings.getInt("title-fade-out",10))*50L))));default->p.sendActionBar(c);}
    } else sender.sendMessage(deserialize(raw));}
    public Component deserialize(String raw){try{return MINI.deserialize(raw);}catch(Exception e){return LEGACY.deserialize(raw);}}
}
