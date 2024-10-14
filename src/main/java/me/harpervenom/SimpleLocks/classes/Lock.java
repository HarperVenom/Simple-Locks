package me.harpervenom.SimpleLocks.classes;

import me.harpervenom.SimpleLocks.SimpleLocks;
import me.harpervenom.SimpleLocks.database.Database;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.type.Door;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static me.harpervenom.SimpleLocks.LockListener.locks;

public class Lock {

    public static Database db;

    private Integer id = null;
    private Integer keyId = null;
    private final String ownerId;
    private final Location loc;
    private boolean isConnected;
    private boolean isLocked;

    private BukkitTask pendingUpdate;
    private static final long DEBOUNCE_DELAY = 100L; // 100 ticks (~5 seconds)

    public Lock(OfflinePlayer p, Block b) {
        this.ownerId = p.getUniqueId().toString();
        this.loc = b.getLocation();
        this.isConnected = false;
        this.isLocked = true;

        Location loc = b.getLocation();
        Chunk chunk = b.getChunk();
        List<Lock> locksList = new ArrayList<>(locks.get(chunk));

        locksList.stream().filter(block -> block.getLoc().equals(loc)).forEach(Lock::remove);
        locksList.removeIf(block -> block.getLoc().equals(loc));

        locksList.add(this);
        locks.put(chunk, locksList);

        createInDB();
    }

    public Lock(int id, Integer keyId, String ownerId, int x, int y, int z, String world, boolean connected, boolean locked) {
        this.id = id;
        this.keyId = keyId;
        this.ownerId = ownerId;
        this.loc = new Location(Bukkit.getWorld(world), x, y, z);
        this.isConnected = connected;
        this.isLocked = locked;
    }

    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }
    public Integer getKeyId() {
        return keyId;
    }
    public void setKeyId(Integer id) {
        this.keyId = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public boolean isConnected(){
        return isConnected;
    }
    public void setConnected(boolean isConnected) {
        this.isConnected = isConnected;
        updateInDB();
    }

    public boolean isLocked() {
        return isLocked;
    }
    public void setLocked(boolean isLocked) {
        this.isLocked = isLocked;
        updateInDB();
    }

    public void remove() {
        Chunk chunk = loc.getChunk();
        locks.put(chunk, locks.get(chunk).stream().filter(currentLockBlock -> !currentLockBlock.equals(this)).toList());

        removeFromDB();
    }

    public Location getLoc() {
        return loc;
    }

    private void createInDB() {
            db.createLock(
                    ownerId,
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ(),
                    loc.getWorld().getName(),
                    isConnected,
                    isLocked
            ).thenAccept((result) -> {
                try {
                    setId(result);
                    if (keyId == null) setKeyId(result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
    }

    private void updateInDB() {
        if (id == null) return;

        if (pendingUpdate != null && !pendingUpdate.isCancelled()) {
            pendingUpdate.cancel();
        }

        pendingUpdate = Bukkit.getScheduler().runTaskLater(SimpleLocks.getPlugin(), () -> {
            db.updateLock(id, keyId, isConnected, isLocked);
        }, DEBOUNCE_DELAY);
    }

    private void removeFromDB() {
        db.deleteLockRecord(id);
    }

    public static Lock getNeighbour(Location loc){
        Block b = Bukkit.getWorld(loc.getWorld().getName()).getBlockAt(loc);

        //If Chest
        if ((b.getState() instanceof Chest chest)) {
            InventoryHolder invHolder = chest.getInventory().getHolder();

            if (invHolder instanceof DoubleChest doubleChest){
                Block nextLock = ((Chest) doubleChest.getLeftSide()).getBlock();

                if (nextLock.getLocation().equals(loc)){
                    nextLock = ((Chest) doubleChest.getRightSide()).getBlock();
                }
                return getLock(nextLock);
            }
        }

        //If Door
        if (b.getBlockData() instanceof Door door) {
            double X = 0, Z = 0;
            for (int i =0; i < 4; i++){
                switch (i) {
                    case 0 -> {
                        X = 1;
                        Z = 0;
                    }
                    case 1 -> X = -1;
                    case 2 -> {
                        X = 0;
                        Z = 1;
                    }
                    case 3 -> Z = -1;
                }

                Block block = Bukkit.getWorld(loc.getWorld().getName()).getBlockAt(loc.clone().add(new Vector(X, 0, Z)));

                if (!(block.getBlockData() instanceof Door nextDoor)) continue;
                Lock nextLock = getLock(block);
                if (nextLock == null) continue;
                if (!door.getFacing().equals((nextDoor.getFacing()))) continue;
                if (door.getHinge() == nextDoor.getHinge()) continue;

                return nextLock;
            }
        }

        return null;
    }

    public static Lock getLock(Block b) {
        Chunk chunk = b.getChunk();

        for (Lock block : locks.get(chunk)) {
            if (block.getLoc().equals(b.getLocation())) return block;
        }

        return null;
    }

}
