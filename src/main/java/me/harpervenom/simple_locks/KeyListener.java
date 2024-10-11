package me.harpervenom.simple_locks;

import me.harpervenom.simple_locks.classes.Key;
import me.harpervenom.simple_locks.classes.Lock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ShapelessRecipe;

import static me.harpervenom.simple_locks.LockBlocksListener.getLock;
import static me.harpervenom.simple_locks.classes.Key.getKey;

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

        Block b = e.getClickedBlock();
        if (b == null) return;
        Lock lock = getLock(b);
        if (lock == null) return;

        Key key = getKey(p.getInventory().getItemInMainHand());
        if (key == null) return;

        p.sendMessage("connect key");
        key.connectToBlock(lock);
    }

}
