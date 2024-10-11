package me.harpervenom.simple_locks.classes;

import me.harpervenom.simple_locks.SimpleLocks;
import me.harpervenom.simple_locks.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static me.harpervenom.simple_locks.LockBlocksListener.locks;

public class Lock {

    public static Database db;

    private int id;
    private final String ownerId;
    private final Location loc;
    private boolean isConnected;
    private boolean isLocked;

    private final static List<Lock> placedBatch = new ArrayList<>();
    private final static List<Lock> destroyedBatch = new ArrayList<>();

    static {
        startBatchProcessing();
    }

    public Lock(Player p, Block b) {
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

        queueForPlacedBatch();
    }

    public Lock(int id, String ownerId, int x, int y, int z, String world, boolean connected, boolean locked) {
        this.id = id;
        this.ownerId = ownerId;
        this.loc = new Location(Bukkit.getWorld(world), x, y, z);
        this.isConnected = connected;
        this.isLocked = locked;
    }

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public boolean isConnected(){
        return isConnected;
    }
    public void setConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }

    public boolean isLocked() {
        return isLocked;
    }
    public void setLocked(boolean isLocked) {
        this.isLocked = isLocked;
    }

    public void remove() {
        Chunk chunk = loc.getChunk();
        locks.put(chunk, locks.get(chunk).stream().filter(currentLockBlock -> !currentLockBlock.equals(this)).toList());

        queueForDestroyedBatch(this);
    }

    public Location getLoc() {
        return loc;
    }

    private void queueForPlacedBatch() {
        placedBatch.add(this);

        int BATCH_SIZE = 50;
        if (placedBatch.size() >= BATCH_SIZE) {
            flushPlacedBatch();
        }
    }

    private void queueForDestroyedBatch(Lock l) {
        boolean existed = placedBatch.removeIf(block -> block.getLoc().equals(l.getLoc()));
        if (existed) return;

        destroyedBatch.add(l);

        int BATCH_SIZE = 50;
        if (destroyedBatch.size() >= BATCH_SIZE) {
            flushDestroyedBatch();
        }
    }

    private static void flushPlacedBatch() {
        List<Lock> locksToSave;

        locksToSave = new ArrayList<>(placedBatch);
        placedBatch.clear();

        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (Lock lock : locksToSave) {
            CompletableFuture<Integer> future = db.createLock(
                    lock.getOwnerId(),
                    lock.getLoc().getBlockX(),
                    lock.getLoc().getBlockY(),
                    lock.getLoc().getBlockZ(),
                    lock.getLoc().getWorld().getName()
            );
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenAccept(v -> {
            // Assigns blocks' generated ids
            for (int i = 0; i < futures.size(); i++) {
                Lock b = locksToSave.get(i);  // Get corresponding block
                CompletableFuture<Integer> future = futures.get(i);

                try {
                    Integer result = future.join();
                    b.setId(result);
                } catch (CompletionException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private static void flushDestroyedBatch() {
        List<Lock> locksToRemove;
        locksToRemove = new ArrayList<>(destroyedBatch);
        destroyedBatch.clear();

        for (Lock lock : locksToRemove) {
            CompletableFuture<Boolean> future = db.deleteLockRecord(lock.getId());
        }
    }

    private static void startBatchProcessing() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(SimpleLocks.getPlugin(),
                () -> {
                    flushPlacedBatch();
                    flushDestroyedBatch();
                }
                , 20L * 10, 20L * 10);
    }

}
