package me.harpervenom.SimpleLocks.database;

import me.harpervenom.SimpleLocks.SimpleLocks;
import me.harpervenom.SimpleLocks.classes.Lock;
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
                        "key_id INTEGER, " +
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
        return createLock(ownerId, x, y, z, world, false, false);
    }

    public CompletableFuture<Integer> createLock(String ownerId, int x, int y, int z, String world, boolean isConnected, boolean isLocked) {
        return CompletableFuture.supplyAsync(() -> {
            String insertSql = "INSERT INTO locks (owner_id, x, y, z, world, connected, locked) VALUES (?, ?, ?, ?, ?, ?, ?)";
            String updateSql = "UPDATE locks SET key_id = ? WHERE id = ?";

            try (PreparedStatement psInsert = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                // Insert the lock without the key_id first
                psInsert.setString(1, ownerId);
                psInsert.setInt(2, x);
                psInsert.setInt(3, y);
                psInsert.setInt(4, z);
                psInsert.setString(5, world);
                psInsert.setBoolean(6, isConnected);  // isConnected
                psInsert.setBoolean(7, isLocked);  // isLocked

                int affectedRows = psInsert.executeUpdate();

                if (affectedRows > 0) {
                    try (ResultSet rs = psInsert.getGeneratedKeys()) {
                        if (rs.next()) {
                            int generatedId = rs.getInt(1);  // Get the generated id

                            // Now update the key_id with the generated id
                            try (PreparedStatement psUpdate = connection.prepareStatement(updateSql)) {
                                psUpdate.setInt(1, generatedId);  // Set key_id as the generated id
                                psUpdate.setInt(2, generatedId);  // Update the row where id = generated id

                                psUpdate.executeUpdate();
                            }

                            return generatedId;  // Return the generated id
                        }
                    }
                }

                return null;  // If no rows were affected or no keys were generated
            } catch (SQLException e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    public CompletableFuture<Boolean> updateLock(int id, Integer keyId, boolean isConnected, boolean isLocked) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE locks SET key_id = ?, connected = ?, locked = ? WHERE id = ? ";

            try (PreparedStatement psRegion = connection.prepareStatement(sql)) {
                psRegion.setObject(1, keyId);
                psRegion.setBoolean(2, isConnected);
                psRegion.setBoolean(3, isLocked);
                psRegion.setInt(4, id);

                psRegion.executeUpdate();

                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
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
                        Lock block = new Lock(rs.getInt("id"), rs.getInt("key_id"), rs.getString("owner_id"), rs.getInt("x"),
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
