package me.harpervenom.SimpleLocks;

import me.harpervenom.SimpleLocks.classes.Lock;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityBreakDoorEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static me.harpervenom.SimpleLocks.ChunksListener.chunkNotLoaded;
import static me.harpervenom.SimpleLocks.Materials.*;
import static me.harpervenom.SimpleLocks.SimpleLocks.getMessage;
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
                b.breakNaturally();
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + getMessage("messages.block_next_to_another")));
                return;
            }

            Lock lock = new Lock(p, b);

            if (nextLock != null && nextLock.isConnected()) {
                lock.setConnected(true);
                lock.setLocked(nextLock.isLocked());
                lock.setKeyId(nextLock.getKeyId());
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + getMessage("messages.block_connected")));
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

        if (lock.getOwnerId().equals(p.getUniqueId().toString()) || p.getGameMode() == GameMode.CREATIVE) {
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
        Block b =  e.getBlock();
        Player p = e.getPlayer();

        if (isUnderDoorBlock(b)){
            e.setCancelled(true);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + getMessage("messages.cant_break_under_doors")));
        }
    }

    @EventHandler
    public void VillagerOpenDoorEvent(EntityInteractEvent e){
        if (!(e.getEntity() instanceof Villager)) return;
        Block b = e.getBlock();
        if (!(b.getBlockData() instanceof Door)) return;
        Lock door = getLock(b);
        if (door == null) return;
        if (!door.isLocked()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent e) {
        if (!SimpleLocks.getPlugin().getConfig().getBoolean("doors_explode")) return;
        List<Block> blocksToRemove = new ArrayList<>();

        for (Block block : e.blockList()) {
            Block mainBlock = getMainBlock(block);
            Lock lock = getLock(mainBlock);
            if (lock != null || isUnderDoorBlock(block)) {
                blocksToRemove.add(block);
            }
        }

        e.blockList().removeAll(blocksToRemove);
    }

    @EventHandler
    public void onZombieBreakDoor(EntityBreakDoorEvent e) {
        if (!SimpleLocks.getPlugin().getConfig().getBoolean("zombie_break_doors")) return;
        Entity entity = e.getEntity();
        if (entity instanceof Zombie) {
            Block b = e.getBlock();
            Lock lock = getLock(b);
            if (lock == null) return;
            e.setCancelled(true);
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
        if (!getLockBlocks().contains(b.getType())) return;
        e.setCancelled(true);

        if (!p.isOp()) return;

        showInfo(b, p);
    }

    public boolean isUnderDoorBlock(Block b) {
        Block door = b.getLocation().clone().add(0, 1, 0).getBlock();

        return !b.getType().toString().endsWith("_DOOR") && door.getType().toString().endsWith("_DOOR");
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

        boolean dealDamage = true;

        int toolDamage = getToolDamage();
        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable damageable){

            if (tool.getType().getMaxDurability() - damageable.getDamage() <= toolDamage){
                p.getInventory().removeItem(tool);
                p.getWorld().playSound(p,Sound.ENTITY_ITEM_BREAK,1,1);
                if (tool.getType().getMaxDurability() - damageable.getDamage() < toolDamage) {
                    dealDamage = false;
                }
            } else {
                damageable.setDamage(toolDamage + damageable.getDamage());
                tool.setItemMeta(meta);
                p.getWorld().playSound(p,Sound.ENTITY_ITEM_BREAK,0.1f,1.4f);
            }
        }

        BlockData blockData = b.getBlockData();
        Sound breakingSound = blockData.getSoundGroup().getBreakSound();

        int blockMaxHealth = getMaxBlockHealth(b);

        int attackDamage = getToolAttackDamage(tool);

        if (!damagedBlocks.containsKey(b)) {
            damagedBlocks.put(b, dealDamage ? (blockMaxHealth - attackDamage) : blockMaxHealth);
        } else {
            if (dealDamage) damagedBlocks.put(b, damagedBlocks.getOrDefault(b, 0) - attackDamage);

            if (damagedBlocks.get(b) == 0) {
                damagedBlocks.remove(b);

                if (b.getType().name().contains("IRON") || b.getType().name().contains("COPPER")) {
                    b.getWorld().playSound(b.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,1,1);
                } else {
                    b.getWorld().playSound(b.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR,1,1);
                }

                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                return true;
            }

        }
        b.getWorld().playSound(b.getLocation(), breakingSound, 0.7f, 1.5f);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "" + damagedBlocks.get(b) + "/" + blockMaxHealth));
        scheduleRestoreHealth(b);

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
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GRAY + getMessage("info.no_owner")));
            return;
        }

        int maxBlockHealth = getMaxBlockHealth(b);
        int health = maxBlockHealth;
        if (damagedBlocks.containsKey(b)) {
            health = damagedBlocks.get(b);
        }
        p.sendMessage(ChatColor.YELLOW + "-----------------");
        p.sendMessage(ChatColor.YELLOW + "ID: " + lock.getId());
        p.sendMessage(ChatColor.YELLOW + getMessage("info.owner") + ": " + Bukkit.getOfflinePlayer(UUID.fromString(lock.getOwnerId())).getName());
        p.sendMessage(ChatColor.YELLOW + getMessage("info.connected") + ": " + lock.isConnected());
        p.sendMessage(ChatColor.YELLOW + getMessage("info.locked") + ": " + lock.isLocked());
        p.sendMessage(ChatColor.YELLOW + getMessage("info.health") + ": " + health + "/" + maxBlockHealth);
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

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent e) {
        InventoryHolder source = e.getSource().getHolder();
        InventoryHolder destination = e.getDestination().getHolder();

        if ((source instanceof Chest || source instanceof Barrel || destination instanceof Chest || destination instanceof Barrel) &&
                (source instanceof Hopper || destination instanceof Hopper)) {

            Block b = (source instanceof Hopper) ? e.getDestination().getLocation().getBlock() : e.getSource().getLocation().getBlock();
            Lock lock = getLock(b);

            if (lock != null && lock.isLocked()) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void RedstoneEvent(BlockRedstoneEvent e) {
        Block b = getMainBlock(e.getBlock());
        Lock lock = getLock(b);
        if (lock == null) return;
        if (lock.isLocked()) {
            e.setNewCurrent(0);
        }
    }

    public static boolean isCurrentlyPoweredAdjacent(Block block) {
        // Directions to check for adjacent blocks
        Block[] adjacentBlocks = new Block[]{
                block.getRelative(BlockFace.NORTH),
                block.getRelative(BlockFace.SOUTH),
                block.getRelative(BlockFace.EAST),
                block.getRelative(BlockFace.WEST),
                block.getRelative(BlockFace.UP),
                block.getRelative(BlockFace.DOWN)
        };

        // Check each adjacent block for active power
        for (Block adjacentBlock : adjacentBlocks) {
            // If the adjacent block is currently powering the door, return true
            if (isBlockCurrentlyPowering(adjacentBlock)) {
                return true; // Found a block currently transmitting power
            }

            // Check if there are levers on adjacent blocks
            for (BlockFace face : BlockFace.values()) {
                Block attachedBlock = adjacentBlock.getRelative(face);
                if (attachedBlock.getType() == Material.LEVER && attachedBlock.getBlockData().getAsString().contains("powered=true")) {
                    return true; // Lever is powered
                }
            }
        }

        return false; // No adjacent blocks actively transmitting power
    }

    private static boolean isBlockCurrentlyPowering(Block block) {
        // Check if the block is a redstone block (always powers adjacent blocks)
        if (block.getType() == Material.REDSTONE_BLOCK) {
            return true; // Redstone block actively powers adjacent blocks
        }

        // Check if the block is a button (any type)
        if (block.getType().toString().contains("BUTTON")) {
            return block.getBlockData().getAsString().contains("powered=true"); // Check if button is powered
        }

        // Check for powered components that can affect the door
        return switch (block.getType()) {
            case LEVER -> block.getBlockData().getAsString().contains("powered=true"); // Check if lever is powered
            case DAYLIGHT_DETECTOR -> block.getBlockData().getAsString().contains("power=") &&
                    Integer.parseInt(block.getBlockData().getAsString().split("=")[1]) > 0;
            case REDSTONE_TORCH -> true; // Always powers adjacent blocks when active
            case REDSTONE_WIRE -> block.getBlockData().getAsString().contains("power=") &&
                    Integer.parseInt(block.getBlockData().getAsString().split("=")[1]) > 0; // Must have power
            default -> false; // Not a power-transmitting block
        };
    }

}
