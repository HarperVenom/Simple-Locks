package me.harpervenom.SimpleLocks;

import me.harpervenom.SimpleLocks.classes.Key;
import me.harpervenom.SimpleLocks.classes.Lock;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static me.harpervenom.SimpleLocks.ChunksListener.chunkNotLoaded;
import static me.harpervenom.SimpleLocks.LockListener.*;
import static me.harpervenom.SimpleLocks.classes.Key.getKey;
import static me.harpervenom.SimpleLocks.classes.Lock.getLock;

public class KeyListener implements Listener {

    public KeyListener(){
        Key blankKey = new Key();

        NamespacedKey NKEmptyKey = new NamespacedKey(SimpleLocks.getPlugin(), "emptyKey");
        ShapelessRecipe emptyKeyRecipe = new ShapelessRecipe(NKEmptyKey, blankKey.getItem());
        emptyKeyRecipe.addIngredient(Material.TRIPWIRE_HOOK);
        emptyKeyRecipe.addIngredient(Material.IRON_INGOT);

        NamespacedKey NSClearedKey = new NamespacedKey(SimpleLocks.getPlugin(), "clearedKey");
        ShapelessRecipe clearedKey = new ShapelessRecipe(NSClearedKey, blankKey.getItem());
        clearedKey.addIngredient(Material.TRIPWIRE_HOOK);
        clearedKey.addIngredient(Material.IRON_NUGGET);

        blankKey.getItem().setAmount(2);

        NamespacedKey NSDuplicateKeys = new NamespacedKey(SimpleLocks.getPlugin(), "duplicateKeys");
        ShapelessRecipe duplicateKeys = new ShapelessRecipe(NSDuplicateKeys, blankKey.getItem());
        duplicateKeys.addIngredient(Material.TRIPWIRE_HOOK);
        duplicateKeys.addIngredient(Material.TRIPWIRE_HOOK);

        Bukkit.addRecipe(emptyKeyRecipe);
        Bukkit.addRecipe(clearedKey);
        Bukkit.addRecipe(duplicateKeys);
    }

    @EventHandler
    public void KeyConnect(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!p.isSneaking()) return;

        Block b =  e.getClickedBlock();
        if (b == null) return;
        b = getMainBlock(b);

        Chunk chunk = b.getChunk();
        if (chunkNotLoaded(p, chunk)) return;

        Lock lock = getLock(b);
        if (lock == null) return;

        Key key = getKey(p.getInventory().getItemInMainHand());
        if (key == null) return;

        e.setCancelled(true);

        if (!lock.getOwnerId().equals(p.getUniqueId().toString())) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Not your block."));
            return;
        }

        if (key.getAmountOfConnections() > 5 - 1) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "The key has reached its connection limit."));
            return;
        }

        if (lock.getKeyId() != null && key.hasConnection(lock.getKeyId())) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.YELLOW + "Already connected."));
            return;
        }

        if (key.getItem().getAmount() > 1) {
            ItemStack restOfKeys = new ItemStack(key.getItem());
            restOfKeys.setAmount(restOfKeys.getAmount() -1);

            key.getItem().setAmount(1);
            key.connectToLock(lock);

            boolean hasFree = false;
            for (int i = 0; i < 36; i++){
                if (p.getInventory().getItem(i) == null){
                    p.getInventory().setItem(i, restOfKeys);
                    hasFree = true;
                    break;
                }
            }

            if (!hasFree){
                p.getWorld().dropItemNaturally(p.getLocation(), restOfKeys);
            }

        } else {
            key.connectToLock(lock);
        }
        lock.setLocked(true, p);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "Key has been connected."));
    }

    @EventHandler
    public void KeyUse(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Block b = e.getClickedBlock();
        if (b == null) return;

        if (p.isSneaking() && e.isBlockInHand()) {
            return;
        }

        b = getMainBlock(b);
        Lock lock = getLock(b);
        if (lock == null || !lock.isConnected()) return;

        Chunk chunk = b.getChunk();
        if (chunkNotLoaded(p, chunk)) return;

        boolean hasKey = hasKeyFor(lock, p);

        if (!hasKey) {
            e.setCancelled(true);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "You don't have a key."));
            return;
        }

        if (!p.isSneaking()) {
            if (lock.isLocked()) {
                if (b.getBlockData() instanceof Door door) {
                    if (door.isOpen()) {
                        e.setCancelled(true);
                    }

                    long startTime = System.nanoTime();

                    if (isCurrentlyPoweredAdjacent(b) || isCurrentlyPoweredAdjacent(b.getRelative(BlockFace.UP))) {
                        p.swingMainHand();
                        BlockState bs = b.getState();
                        door.setPowered(true);
                        door.setOpen(true);
                        bs.setBlockData(door);
                        bs.update();
                        if (b.getType().name().contains("IRON")) p.getWorld().playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN,1,1);
                    }

                    long endTime = System.nanoTime(); // or System.currentTimeMillis();
                    long duration = endTime - startTime;

                            Bukkit.broadcastMessage(duration + "");
                }

                lock.setLocked(false, p);

                if (b.getType().name().contains("CHEST") || b.getType() == Material.BARREL) {
                    quickOpen.put(p.getUniqueId(), lock);
                }
            } else {
                if (b.getBlockData() instanceof Door door) {
                    lock.setLocked(true, p);

                    e.setCancelled(true);
                    if (door.isOpen()) {
                        BlockState bs = b.getState();
                        door.setPowered(false);
                        door.setOpen(false);
                        bs.setBlockData(door);
                        bs.update();
                        if (b.getType().name().contains("IRON")) p.getWorld().playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE,1,1);
                    }

                }
//                if (b.getBlockData() instanceof TrapDoor door) {
//                    lock.setLocked(true, p);
//
//                    BlockState bs = b.getState();
//                    door.setPowered(false);
//                    b.setBlockData(door);
//                    bs.update();
//                }
            }
        } else {
            Key key = getKey(p.getInventory().getItemInMainHand());
            if (key != null) return;

            e.setCancelled(true);
            lock.setLocked(!lock.isLocked(), p);
        }
    }

    HashMap<UUID, Lock> quickOpen = new HashMap<>();

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {

            if (quickOpen.containsKey(p.getUniqueId())) {
                Lock lock = quickOpen.get(p.getUniqueId());
                quickOpen.get(p.getUniqueId()).setLocked(true, p);
                quickOpen.remove(p.getUniqueId());

                List<HumanEntity> viewers = new ArrayList<>(e.getViewers());
                for (HumanEntity viewer : viewers) {
                    Player player = (Player) viewer;
                    player.closeInventory();
                }
            }
        }
    }

    @EventHandler
    public void PreventKeyPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        Key key = getKey(item);
        if (key != null) {
            e.setCancelled(true);
        }
    }

    public boolean hasKeyFor(Lock lock, Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < 41; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) continue;
            Key key = getKey(item);
            if (key == null) continue;
            if (!key.hasConnection(lock.getKeyId())) continue;
            return true;
        }
        return false;
    }

}
