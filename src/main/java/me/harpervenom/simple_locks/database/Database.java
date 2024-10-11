package me.harpervenom.simple_locks.database;

import me.harpervenom.simple_locks.SimpleLocks;
import me.harpervenom.simple_locks.classes.Lock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Database {

    private Connection connection;

    public void init() {
        try {
            if (!SimpleLocks.getPlugin().getDataFolder().exists()) {
                SimpleLocks.getPlugin().getDataFolder().mkdirs();
            }
            connection = DriverManager.getConnection(
                    "jdbc:sqlite:" + SimpleLocks.getPlugin().getDataFolder().getAbsolutePath()
                            + "/simplelocks.db");

            try (Statement statement = connection.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS locks (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "owner_id TEXT NOT NULL, " +
                        "x INTEGER NOT NULL, " +
                        "y INTEGER NOT NULL, " +
                        "z INTEGER NOT NULL, " +
                        "world TEXT NOT NULL, " +
                        "connected BOOLEAN NOT NULL, " +
                        "locked BOOLEAN NOT NULL, " +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";
                statement.executeUpdate(sql);
            }

        }catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<Integer> createLock(String ownerId, int x, int y, int z, String world) {
        Player p = Bukkit.getPlayer(ownerId);
        return CompletableFuture.supplyAsync(() -> {
            String regionSql = "INSERT INTO locks (owner_id, x, y, z, world, connected, locked) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement psRegion = connection.prepareStatement(regionSql, Statement.RETURN_GENERATED_KEYS)) {
                psRegion.setString(1, ownerId);
                psRegion.setInt(2, x);
                psRegion.setInt(3, y);
                psRegion.setInt(4, z);
                psRegion.setString(5, world);
                psRegion.setBoolean(6, false);
                psRegion.setBoolean(7, false);

                int affectedRows = psRegion.executeUpdate();

                if (affectedRows > 0) {
                    try (ResultSet rs = psRegion.getGeneratedKeys()) {
                        if (rs.next()) {
                            return rs.getInt(1);
                        }
                    }
                }

                return null;
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> deleteLockRecord(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM locks WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, id);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<List<Lock>> getLocks(int x1, int z1, int x2, int z2, String world) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM locks WHERE x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND world = ?";
            List<Lock> blocks = new ArrayList<>();

            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, Math.min(x1, x2));
                ps.setInt(2, Math.max(x1, x2));
                ps.setInt(3, Math.min(z1, z2));
                ps.setInt(4, Math.max(z1, z2));
                ps.setString(5, world);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Lock block = new Lock(rs.getInt("id"), rs.getString("owner_id"), rs.getInt("x"),
                                rs.getInt("y"), rs.getInt("z"), rs.getString("world"),
                                rs.getBoolean("connected"), rs.getBoolean("locked"));

                        blocks.add(block);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return blocks;
        });
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()){
                connection.close();
            }
        }catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
