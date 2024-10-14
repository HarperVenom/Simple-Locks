package me.harpervenom.SimpleLocks.classes;

import me.harpervenom.SimpleLocks.SimpleLocks;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import static me.harpervenom.SimpleLocks.classes.Lock.getNeighbour;

public class Key{

    private static final String INTEGER_KEY_PREFIX = "lockId_";

    private final ItemStack keyItem;
    private List<Integer> lockIds;

    public Key() {
        ItemStack blankKey = new ItemStack(Material.TRIPWIRE_HOOK);

        ItemMeta meta = blankKey.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(SimpleLocks.getPlugin(),"key"), PersistentDataType.BOOLEAN,true);
        meta.setDisplayName(ChatColor.GOLD + "Key");

        ArrayList<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "blank");
        meta.setLore(lore);
        blankKey.setItemMeta(meta);

        keyItem = blankKey;
        lockIds = new ArrayList<>();
    }

    public Key(ItemStack item) {
        keyItem = item;
        lockIds = new ArrayList<>();  // Initialize empty list

        // Extract the stored integers from the ItemStack's PersistentDataContainer
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();

            // Iterate over all possible stored integers and add them to the list
            int index = 0;
            while (container.has(new NamespacedKey(SimpleLocks.getPlugin(), INTEGER_KEY_PREFIX + index), PersistentDataType.INTEGER)) {
                Integer value = container.get(new NamespacedKey(SimpleLocks.getPlugin(), INTEGER_KEY_PREFIX + index), PersistentDataType.INTEGER);
                if (value != null) {
                    lockIds.add(value);  // Add the integer to the list
                }
                index++;
            }
        }
    }

    public static Key getKey(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer()
                .has(new NamespacedKey(SimpleLocks.getPlugin(),"key"), PersistentDataType.BOOLEAN)) return null;

        return new Key(item);
    }

    public ItemStack getItem() {
        return keyItem;
    }

    public void connectToLock(Lock lock) {
        Integer lockId = lock.getKeyId();
        if (lockId == null) return;
        lock.setConnected(true);

        addVisualKey();
        addConnection(lockId);

        Lock nextLock = getNeighbour(lock.getLoc());
        if (nextLock == null) return;

        nextLock.setKeyId(lockId);
        nextLock.setConnected(true);
    }

    public void addConnection(int id) {
        lockIds.add(id);

        ItemMeta meta = keyItem.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();

            // Find the next available index (starting from 0)
            int size = getAmountOfConnections(); // We infer the current size
            container.set(new NamespacedKey(SimpleLocks.getPlugin(), INTEGER_KEY_PREFIX + size), PersistentDataType.INTEGER, id);

            keyItem.setItemMeta(meta);
        }
    }

    public void addVisualKey() {
        ItemMeta meta = keyItem.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (getAmountOfConnections() == 0 || lore == null) {
                lore = new ArrayList<>();
            }
            lore.add(ChatColor.GRAY + generateName());
            meta.setLore(lore);

            keyItem.setItemMeta(meta);
        }
    }

    public boolean hasConnection(int id) {
        return lockIds.contains(id);
    }

    public int getAmountOfConnections() {
        ItemMeta meta = keyItem.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();

            // Count the stored integers by checking the keys sequentially
            int count = 0;
            while (container.has(new NamespacedKey(SimpleLocks.getPlugin(), INTEGER_KEY_PREFIX + count), PersistentDataType.INTEGER)) {
                count++;
            }
            return count;
        }
        return 0;
    }



    public String generateName(){
        String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        int STRING_LENGTH = 5; // Adjust this as needed
        StringBuilder stringBuilder = new StringBuilder(STRING_LENGTH);
        Random random = new Random();

        for (int i = 0; i < STRING_LENGTH; i++) {
            int randomIndex = random.nextInt(CHARACTERS.length());
            char randomChar = CHARACTERS.charAt(randomIndex);
            stringBuilder.append(randomChar);
        }
        return stringBuilder.toString();
    }
}
