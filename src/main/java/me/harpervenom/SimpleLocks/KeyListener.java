package me.harpervenom.SimpleLocks;

import me.harpervenom.SimpleLocks.classes.Key;
import me.harpervenom.SimpleLocks.classes.Lock;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static me.harpervenom.SimpleLocks.ChunksListener.chunkNotLoaded;
import static me.harpervenom.SimpleLocks.LockListener.*;
import static me.harpervenom.SimpleLocks.SimpleLocks.getMessage;
import static me.harpervenom.SimpleLocks.classes.Key.getKey;
import static me.harpervenom.SimpleLocks.classes.Lock.getLock;

public class KeyListener implements Listener {

    NamespacedKey duplicateRecipe;

    public KeyListener(){
        Key blankKey = new Key();

        NamespacedKey NKEmptyKey = new NamespacedKey(SimpleLocks.getPlugin(), "emptyKey");
        ShapelessRecipe emptyKeyRecipe = new ShapelessRecipe(NKEmptyKey, blankKey.getItem());
        emptyKeyRecipe.addIngredient(Material.TRIPWIRE_HOOK);
        emptyKeyRecipe.addIngredient(Material.IRON_NUGGET);

        blankKey.getItem().setAmount(2);

        duplicateRecipe = new NamespacedKey(SimpleLocks.getPlugin(), "duplicateKeys");
        ShapelessRecipe duplicateKeys = new ShapelessRecipe(duplicateRecipe, blankKey.getItem());
        duplicateKeys.addIngredient(Material.TRIPWIRE_HOOK);
        duplicateKeys.addIngredient(Material.TRIPWIRE_HOOK);
        duplicateKeys.addIngredient(Material.IRON_NUGGET);

        Bukkit.addRecipe(emptyKeyRecipe);
        Bukkit.addRecipe(duplicateKeys);
    }

    @EventHandler
    public void DuplicateCraft(PrepareItemCraftEvent e){
        Recipe recipe = e.getRecipe();

        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            NamespacedKey recipeKey = shapelessRecipe.getKey();

            if (recipeKey.equals(duplicateRecipe)) {
                ItemStack[] ingredients = e.getInventory().getMatrix();
                int c = 0;
                ItemStack result = null;

                for (ItemStack ingredient : ingredients) {
                    if (ingredient == null) continue;
                    if (ingredient.getType().equals(Material.TRIPWIRE_HOOK)) {
                        Key key = getKey(ingredient);
                        if (key == null) {
                            e.getInventory().setResult(null);
                            return;
                        }
                        if (key.getAmountOfConnections() > 0) {
                            result = new ItemStack(ingredient);
                            result.setAmount(2);
                            c++;
                        }
                    }
                }

                if (c == 2) {
                    result = null;
                }

                e.getInventory().setResult(result);
            }
        }
    }

    @EventHandler
    public void onAnvilRename(PrepareAnvilEvent e) {
        ItemStack resultItem = e.getResult();
        if (resultItem == null) return;
        Key key = getKey(resultItem);
        if (key == null) return;

        ItemMeta meta = resultItem.getItemMeta();
        if (meta == null) return;

        String newName = meta.getDisplayName();

        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.ITALIC + newName);
        resultItem.setItemMeta(meta);
    }

    @EventHandler
    public void DuplicateCraftEvent(CraftItemEvent e) {
        Player p = (Player) e.getViewers().get(0);
        ItemStack result = e.getCurrentItem();
        if (result == null) return;
        Key key = getKey(result);
        if (key == null) return;

        if (key.getOwnerID() != null && !key.getOwnerID().equals(p.getUniqueId().toString())) {
            e.setCancelled(true);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + getMessage("messages.not_your_key")));
        }
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

        if (key.getOwnerID() != null && !key.getOwnerID().equals(p.getUniqueId().toString())) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + getMessage("messages.not_your_key")));
            return;
        }

        if (!lock.getOwnerId().equals(p.getUniqueId().toString())) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + getMessage("messages.not_your_block")));
            return;
        }

        if (key.getAmountOfConnections() > SimpleLocks.getPlugin().getConfig().getInt("max_connections_per_key") - 1) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + getMessage("messages.key_limit")));
            return;
        }

        if (lock.getKeyId() != null && key.hasConnection(lock.getKeyId())) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.YELLOW + getMessage("messages.already_connected")));
            return;
        }

        if (key.getItem().getAmount() > 1) {
            ItemStack restOfKeys = new ItemStack(key.getItem());
            restOfKeys.setAmount(restOfKeys.getAmount() -1);

            key.getItem().setAmount(1);
            key.connectToLock(lock, p);

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
            key.connectToLock(lock, p);
        }
        lock.setLocked(true, p);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + getMessage("messages.key_connected")));
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
            if (lock.isLocked()) {
                e.setCancelled(true);
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + getMessage("messages.no_key")));
                return;
            }
            return;
        }

        if (!p.isSneaking()) {
            if (lock.isLocked()) {
                if (b.getBlockData() instanceof Door door) {
                    if (door.isOpen()) {
                        e.setCancelled(true);
                    }

                    if (isCurrentlyPoweredAdjacent(b) || isCurrentlyPoweredAdjacent(b.getRelative(BlockFace.UP))) {
                        p.swingMainHand();
                        door.setPowered(true);
                        door.setOpen(true);
                        b.setBlockData(door);
                        if (b.getType().name().contains("IRON")) p.getWorld().playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN,1,1);
                    }
                }

                if (b.getBlockData() instanceof TrapDoor door) {
                    if (door.isOpen()) {
                        e.setCancelled(true);
                    }

                    if (isCurrentlyPoweredAdjacent(b)) {
                        p.swingMainHand();
                        door.setPowered(true);
                        door.setOpen(true);
                        b.setBlockData(door);
                        if (b.getType().name().contains("IRON")) p.getWorld().playSound(b.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN,1,1);
                    }
                }

                lock.setLocked(false, p);

                if (b.getType().name().contains("CHEST") || b.getType() == Material.BARREL) {
                    quickOpen.put(p.getUniqueId(), lock);
                }
            } else {
                if (lock.getType().equals("container")) return;

                lock.setLocked(true, p);
                e.setCancelled(true);
                if (b.getBlockData() instanceof Door door) {

                    if (door.isOpen()) e.setCancelled(false);

                    if (!b.getType().name().contains("IRON")) return;
                    if (door.isOpen()) {
                        p.swingMainHand();
                        door.setPowered(false);
                        door.setOpen(false);
                        b.setBlockData(door);
                        if (b.getType().name().contains("IRON")) p.getWorld().playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE,1,1);
                    }

                }
                if (b.getBlockData() instanceof TrapDoor door) {

                    if (door.isOpen()) e.setCancelled(false);

                    if (!b.getType().name().contains("IRON")) return;
                    if (door.isOpen()) {
                        p.swingMainHand();
                        door.setPowered(false);
                        door.setOpen(false);
                        b.setBlockData(door);
                        if (b.getType().name().contains("IRON")) p.getWorld().playSound(b.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE,1,1);
                    }
                }
            }
        } else {
            Key key = getKey(p.getInventory().getItemInMainHand());
            if (key != null) return;

            e.setCancelled(true);
            lock.setLocked(!lock.isLocked(), p);

            if (lock.isLocked() && lock.getType().equals("container")) {
                Inventory inventory = ((Container) b.getState()).getInventory();
                List<HumanEntity> viewers = new ArrayList<>(inventory.getViewers());
                for (HumanEntity viewer : viewers) {
                    if (viewer instanceof Player player) {
                        player.closeInventory();
                    }
                }
            }
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

            if (item.getType().toString().contains("BUNDLE")) {
                if (item.hasItemMeta() && item.getItemMeta() instanceof BundleMeta bundleMeta) {
                    // Scan all items inside the bundle
                    for (ItemStack bundleItem : bundleMeta.getItems()) {
                        if (bundleItem == null) continue;
                        Key bundleKey = getKey(bundleItem);
                        if (bundleKey != null && bundleKey.hasConnection(lock.getKeyId())) {
                            return true; // Found a valid key in the bundle
                        }
                    }
                }
                continue;
            }

            Key key = getKey(item);
            if (key == null) continue;
            if (!key.hasConnection(lock.getKeyId())) continue;
            return true;
        }
        return false;
    }
}