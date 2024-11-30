package me.harpervenom.SimpleLocks;

import me.harpervenom.SimpleLocks.classes.Lock;
import me.harpervenom.SimpleLocks.database.Database;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SimpleLocks extends JavaPlugin {

    private Database db;
    static SimpleLocks plugin;
    private static FileConfiguration languageConfig;

    public static SimpleLocks getPlugin() {
        return plugin;
    }

    @Override
    public void onEnable() {
        plugin = this;

        db = new Database();
        db.init();

        Lock.db = db;

        saveDefaultConfig();

        loadLanguageFile(getConfig().getString("language"));

        getServer().getPluginManager().registerEvents(new LockListener(), this);
        getServer().getPluginManager().registerEvents(new ChunksListener(db), this);
        getServer().getPluginManager().registerEvents(new KeyListener(), this);

        System.out.println("[SimpleLocks] " + getMessage("messages.plugin_started"));
    }

    private void loadLanguageFile(String lang) {
        File langFile = new File(getDataFolder(), lang + ".yml");

        if (!langFile.exists()) {
            langFile.getParentFile().mkdirs();
            saveResource(lang + ".yml", false);  // Copy default language file from resources
        }

        languageConfig = YamlConfiguration.loadConfiguration(langFile);
    }

    // Getter for the language configuration
    public static String getMessage(String path) {
        return languageConfig.getString(path);
    }

    @Override
    public void onDisable() {
        db.close();
    }
}
