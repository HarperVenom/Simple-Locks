package me.harpervenom.SimpleLocks;

import me.harpervenom.SimpleLocks.classes.Lock;
import me.harpervenom.SimpleLocks.database.Database;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleLocks extends JavaPlugin {

    private Database db;
    static SimpleLocks plugin;

    public static SimpleLocks getPlugin() {
        return plugin;
    }

    @Override
    public void onEnable() {
        plugin = this;

        db = new Database();
        db.init();

        Lock.db = db;

        getServer().getPluginManager().registerEvents(new LockListener(), this);
        getServer().getPluginManager().registerEvents(new ChunksListener(db), this);
        getServer().getPluginManager().registerEvents(new KeyListener(), this);

        System.out.println("[SimpleLocks] Plugin has started.");
    }

    @Override
    public void onDisable() {
        db.close();
    }
}
