package me.harpervenom.SimpleLocks;

import me.harpervenom.SimpleLocks.classes.Lock;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static me.harpervenom.SimpleLocks.ChunksListener.chunkNotLoaded;
import static me.harpervenom.SimpleLocks.Materials.*;
import static me.harpervenom.SimpleLocks.classes.Lock.getLock;
import static me.harpervenom.SimpleLocks.classes.Lock.getNeighbour;

public class LockListener implements Listener {

    public static HashMap<Chunk, List<Lock>> locks = new HashMap<>();

    @EventHandler
    public void BlockPlaceEvent(BlockPlaceEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();

        if (!getLockBlocks().contains(b.getType())) return;

        Chunk chunk = b.getChunk();
        if (chunkNotLoaded(p, chunk)){
            e.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTaskLater(SimpleLocks.getPlugin(), () -> {
            Lock nextLock = getNeighbour(b.getLocation());
            if (nextLock != null && !nextLock.getOwnerId().equals(p.getUniqueId().toString())) {
                e.setCancelled(true);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "You can't place the block next to someone else's"));
            }

            Lock lock = new Lock(p, b);

            if (nextLock != null && nextLock.isConnected()) {
                lock.setConnected(true);
                lock.setLocked(nextLock.isLocked());
                lock.setKeyId(nextLock.getKeyId());
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "Block has been connected to the next one."));
            }
        }, 1);
    }

    @EventHandler
    public void BlockBreakEvent(BlockBreakEvent e) {
        Block b = getMainBlock(e.getBlock());
        Player p = e.getPlayer();

        if (!getLockBlocks().contains(b.getType())) return;

        Chunk chunk = b.getChunk();
        if (chunkNotLoaded(p, chunk)){
            e.setCancelled(true);
            return;
        }

        Lock lock = getLock(b);

        if (lock == null) {
            return;
        }

        if (!lock.getOwnerId().equals(p.getUniqueId().toString()) || p.getGameMode() == GameMode.CREATIVE) {
            lock.remove();
        } else {
            boolean destroyed = hitBlock(p, b);
            if (!destroyed) {
                e.setCancelled(true);
            } else {
                lock.remove();
            }
        }
    }

    @EventHandler
    public void BreakBlockUnderDoor(BlockBreakEvent e) {
        Block door = e.getBlock().getLocation().clone().add(0, 1, 0).getBlock();
        Player p = e.getPlayer();

        if (door.getType().toString().endsWith("_DOOR")){
            e.setCancelled(true);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "You can't break blocks under doors."));
        }
    }

    @EventHandler
    public void StickUseEvent(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        if (b == null) return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK
                || e.getAction() == Action.LEFT_CLICK_AIR) return;

        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        if (!p.isSneaking()) return;
        e.setCancelled(true);

        showInfo(b, p);
    }

    public static HashMap<Block, Integer> damagedBlocks = new HashMap<>();
    private final HashMap<Block, BukkitTask> restoreHealthTasks = new HashMap<>();

    public boolean hitBlock(Player p, Block b) {
        boolean hasProperTool = false;
        ItemStack tool = p.getInventory().getItemInMainHand();
        Material itemType = tool.getType();

        for (Material toolType : getTools()) {
            if (itemType == toolType && b.isPreferredTool(tool)) {
                hasProperTool = true;
                break;
            }
        }

        if (!hasProperTool) {
            p.damage(0.5);
            return false;
        }

        int toolDamage = getToolDamage(b, tool);
        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable damageable){

            if (tool.getType().getMaxDurability()-damageable.getDamage() <= toolDamage){
                p.getInventory().removeItem(tool);
                p.getWorld().playSound(p,Sound.ENTITY_ITEM_BREAK,1,1);
            } else {
                damageable.setDamage(toolDamage + damageable.getDamage());
                tool.setItemMeta(meta);
                p.getWorld().playSound(p,Sound.ENTITY_ITEM_BREAK,0.1f,1.4f);
            }
        }

        BlockData blockData = b.getBlockData();
        Sound breakingSound = blockData.getSoundGroup().getBreakSound();

        int blockMaxHealth = getMaxBlockHealth(b);

        if (!damagedBlocks.containsKey(b)) {
            damagedBlocks.put(b, blockMaxHealth -1);
            b.getWorld().playSound(b.getLocation(), breakingSound, 0.7f, 1.5f);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "" + (blockMaxHealth-1) + "/" + blockMaxHealth));
            scheduleRestoreHealth(b);
        } else {
            damagedBlocks.put(b, damagedBlocks.getOrDefault(b, 0) - 1);

            if (damagedBlocks.get(b) == 0) {
                damagedBlocks.remove(b);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                return true;
            }

            b.getWorld().playSound(b.getLocation(), breakingSound, 0.7f, 1.5f);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "" + damagedBlocks.get(b) + "/" + blockMaxHealth));
            scheduleRestoreHealth(b);
        }

        return false;
    }

    private void scheduleRestoreHealth(Block b) {
        int UNLOAD_DELAY = 180;

        if (restoreHealthTasks.containsKey(b)) {
            restoreHealthTasks.get(b).cancel();
        }

        BukkitTask unloadTask = new BukkitRunnable() {
            @Override
            public void run() {
                damagedBlocks.put(b, getMaxBlockHealth(b));
                restoreHealthTasks.remove(b);
            }
        }.runTaskLater(SimpleLocks.getPlugin(), UNLOAD_DELAY * 20L);

        restoreHealthTasks.put(b, unloadTask);
    }

    public void showInfo(Block b, Player p) {
        b = getMainBlock(b);
        Chunk chunk = b.getChunk();

        if (chunkNotLoaded(p, chunk)) return;

        Lock lock = getLock(b);

        if (lock == null) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GRAY + "No owner."));
            return;
        }

        int maxBlockHealth = getMaxBlockHealth(b);
        int health = maxBlockHealth;
        if (damagedBlocks.containsKey(b)) {
            health = damagedBlocks.get(b);
        }
        p.sendMessage(ChatColor.YELLOW + "ID: " + lock.getId());
        p.sendMessage(ChatColor.YELLOW + "Owner: " + Bukkit.getOfflinePlayer(UUID.fromString(lock.getOwnerId())).getName());
        p.sendMessage(ChatColor.YELLOW + "Connected: " + lock.isConnected());
        p.sendMessage(ChatColor.YELLOW + "Health: " + health + "/" + maxBlockHealth);
    }

    public static Block getMainBlock(Block b) {
        BlockData blockData = b.getBlockData();

        if (blockData instanceof Door door) {
            if (door.getHalf() == Bisected.Half.TOP) {
                return b.getRelative(BlockFace.DOWN);
            }
        }

        return b;
    }
}
