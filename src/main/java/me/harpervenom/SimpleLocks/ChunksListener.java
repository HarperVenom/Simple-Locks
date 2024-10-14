package me.harpervenom.SimpleLocks;

import me.harpervenom.SimpleLocks.database.Database;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

import static me.harpervenom.SimpleLocks.LockListener.locks;

public class ChunksListener implements Listener {

    private static Database db;

    public ChunksListener(Database db) {
        ChunksListener.db = db;
    }

    HashMap<Chunk, Set<Player>> activeChunks = new HashMap<>();

    @EventHandler
    public void PlayerMoveEvent(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        Chunk oldChunk = e.getFrom().getChunk();
        Chunk chunk = e.getTo().getChunk();

        List<Chunk> oldActiveChunks = getActiveChunks(oldChunk);
        List<Chunk> newActiveChunks = getActiveChunks(chunk);

        if (!oldChunk.equals(chunk)) {

            onPlayerLeaveChunks(p, oldActiveChunks.stream().filter(currentChunk -> !newActiveChunks.contains(currentChunk)).collect(Collectors.toList()));

            Bukkit.getServer().getScheduler().runTaskLater(SimpleLocks.getPlugin(), () -> {
                Chunk newChunk = p.getLocation().getChunk();
                if (newChunk.equals(chunk)){
                    onPlayerEnterChunks(p, newActiveChunks);
                }
            }, 20);
        }
    }

    @EventHandler
    public void PlayerJoin(PlayerJoinEvent e){
        Player p = e.getPlayer();
        Chunk chunk = p.getLocation().getChunk();

        onPlayerEnterChunks(p, getActiveChunks(chunk));
    }

    public static void loadWildChunks(List<Chunk> chunks) {
        World world = chunks.get(0).getWorld();

        for (Chunk currentChunk : chunks) {
            int chunkX = currentChunk.getX();
            int chunkZ = currentChunk.getZ();

            int minX = chunkX * 16;
            int maxX = minX + 15;
            int minZ = chunkZ * 16;
            int maxZ = minZ + 15;

            if (!locks.containsKey(currentChunk)) {
                db.getLocks(minX, minZ, maxX, maxZ, world.getName())
                        .thenAccept(blocks -> locks.put(currentChunk, blocks));
            }
        }
    }

    private final HashMap<Chunk, BukkitTask> chunkUnloadTasks = new HashMap<>();

    // When a player enters a chunk
    public void onPlayerEnterChunks(Player player, List<Chunk> chunks) {
        loadWildChunks(chunks);

        for (Chunk currentChunk : chunks) {
            if (chunkUnloadTasks.containsKey(currentChunk)) {
                chunkUnloadTasks.get(currentChunk).cancel();
                chunkUnloadTasks.remove(currentChunk);
            }

            // Add the player to the chunk's player set
            activeChunks.computeIfAbsent(currentChunk, k -> new HashSet<>()).add(player);
        }
    }

    // When a player leaves a chunk
    public void onPlayerLeaveChunks(Player player, List<Chunk> chunks) {
        for (Chunk currentChunk : chunks) {
            Set<Player> playersInChunk = activeChunks.get(currentChunk);

            if (playersInChunk != null) {
                playersInChunk.remove(player);

                // If no players are left in the chunk, schedule it for unloading
                if (playersInChunk.isEmpty()) {
                    scheduleChunkUnload(currentChunk);
                }
            }
        }
    }

    private void scheduleChunkUnload(Chunk chunk) {
        int UNLOAD_DELAY = 60;
        BukkitTask unloadTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Unload the chunk if no players are in it
                if (!activeChunks.containsKey(chunk) || activeChunks.get(chunk).isEmpty()) {
                    activeChunks.remove(chunk);  // Remove from tracking
                    chunkUnloadTasks.remove(chunk);  // Remove the task
                    locks.remove(chunk);
                }
            }
        }.runTaskLater(SimpleLocks.getPlugin(), UNLOAD_DELAY * 20L); // Schedule for UNLOAD_DELAY seconds later

        chunkUnloadTasks.put(chunk, unloadTask);  // Track the unload task
    }

    public static List<Chunk> getActiveChunks(Chunk chunk) {
        List<Chunk> chunks = new ArrayList<>();

        int x = chunk.getX();
        int z = chunk.getZ();
        World world = chunk.getWorld();

        chunks.add(chunk);
        chunks.add(world.getChunkAt(x+1,z));
        chunks.add(world.getChunkAt(x,z+1));
        chunks.add(world.getChunkAt(x+1,z+1));
        chunks.add(world.getChunkAt(x-1,z));
        chunks.add(world.getChunkAt(x,z-1));
        chunks.add(world.getChunkAt(x-1,z-1));
        chunks.add(world.getChunkAt(x+1,z-1));
        chunks.add(world.getChunkAt(x-1,z+1));

        return chunks;
    }

    public static boolean chunkNotLoaded(Player p, Chunk chunk) {
        if (!(locks.containsKey(chunk))) {
            if (p != null) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Chunk is not loaded."));
            }
            loadWildChunks(getActiveChunks(chunk));
            return true;
        }
        return false;
    }
}
