package me.harpervenom.SimpleLocks;

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

        if (type.name().contains("IRON")) return SimpleLocks.getPlugin().getConfig().getInt("iron_door_health");
        if (type.name().contains("COPPER")) return SimpleLocks.getPlugin().getConfig().getInt("copper_door_health");
        if (type.name().contains("DOOR")) return SimpleLocks.getPlugin().getConfig().getInt("wooden_door_health");

        return SimpleLocks.getPlugin().getConfig().getInt("chest_health");
    }

    public static int getToolDamage() {
        return SimpleLocks.getPlugin().getConfig().getInt("tool_damage");
    }

    public static int getToolAttackDamage(ItemStack tool) {
        Material type = tool.getType();
        if (type.name().contains("WOODEN")) return SimpleLocks.getPlugin().getConfig().getInt("wooden_attack_damage");
        if (type.name().contains("STONE")) return SimpleLocks.getPlugin().getConfig().getInt("stone_attack_damage");
        if (type.name().contains("GOLDEN")) return SimpleLocks.getPlugin().getConfig().getInt("golden_attack_damage");
        if (type.name().contains("IRON")) return SimpleLocks.getPlugin().getConfig().getInt("iron_attack_damage");
        if (type.name().contains("DIAMOND")) return SimpleLocks.getPlugin().getConfig().getInt("diamond_attack_damage");
        if (type.name().contains("NETHERITE")) return SimpleLocks.getPlugin().getConfig().getInt("netherite_attack_damage");
        return 0;
    }
}
