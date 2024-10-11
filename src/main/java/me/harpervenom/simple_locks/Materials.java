package me.harpervenom.simple_locks;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Materials {

    private static final List<Material> tools;
    private static final List<Material> lockBlocks;

    static {
        tools = Arrays.stream(Material.values())
                .filter(material ->
                        (material.name().endsWith("PICKAXE") || material.name().endsWith("AXE")))
                .collect(Collectors.toList());

        lockBlocks = Arrays.stream(Material.values())
                .filter(material ->
                        (material.name().contains("DOOR") || material.name().contains("CHEST") || material == Material.BARREL))
                .collect(Collectors.toList());
    }

    public static List<Material> getTools() {
        return tools;
    }
    public static List<Material> getLockBlocks() {
        return lockBlocks;
    }

    public static int getMaxBlockHealth(Block b) {
        Material type = b.getType();

        if (type.name().contains("IRON")) return 10;
        if (type.name().contains("COPPER")) return 3;
        if (type.name().contains("DOOR")) return 3;

        return 5;
    }

    public static int getToolDamage(Block b, ItemStack tool) {
        return 384;
    }
}
