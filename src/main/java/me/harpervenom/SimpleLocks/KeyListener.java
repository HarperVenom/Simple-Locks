package me.harpervenom.SimpleLocks;

import me.harpervenom.SimpleLocks.classes.Key;
import me.harpervenom.SimpleLocks.classes.Lock;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

import static me.harpervenom.SimpleLocks.ChunksListener.chunkNotLoaded;
import static me.harpervenom.SimpleLocks.LockListener.getMainBlock;
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

        p.sendMessage(key.getAmountOfConnections() + "");

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
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "Key has been connected."));
    }

    @EventHandler
    public void PreventKeyPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        Key key = getKey(item);
        if (key != null) {
            e.setCancelled(true);
        }
    }

}
