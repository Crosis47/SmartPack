package crosis47.minecraft.smartpack.compatibility;

import crosis47.minecraft.smartpack.SmartPack;
import crosis47.minecraft.smartpack.commands.PackCommand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

/** Runs inside a disposable Paper server; never packaged in the production plugin. */
public final class CompatibilityProbe extends JavaPlugin {

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                checkCompatibility();
                getLogger().info("SMARTPACK_COMPATIBILITY_PASS " + Bukkit.getMinecraftVersion());
            } catch (Throwable failure) {
                getLogger().severe("SMARTPACK_COMPATIBILITY_FAIL");
                failure.printStackTrace();
            } finally {
                Bukkit.shutdown();
            }
        });
    }

    private void checkCompatibility() throws Exception {
        SmartPack plugin = (SmartPack) Bukkit.getPluginManager().getPlugin("SmartPack");
        require(plugin != null && plugin.isEnabled(), "SmartPack enabled with SQLite storage");
        require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pack reload"), "reload command registered");

        plugin.getConfig().set("activation.mode", "SMART_PACKER_ITEM");
        plugin.getConfig().set("activation.smart_packer_item.cooldown.enabled", true);
        plugin.saveConfig();
        plugin.reloadPluginConfig();
        ItemStack packer = plugin.createSmartPackerItem();
        require(plugin.isSmartPackerItem(packer), "Smart Packer persistent marker");
        require(plugin.updateSmartPackerItemLore(packer, 100), "cooldown tooltip update");
        require(plugin.isSmartPackerItem(packer), "marker survives tooltip update");
        NamespacedKey recipeKey = new NamespacedKey(plugin, "smart_packer");
        require(Bukkit.getRecipe(recipeKey) != null, "crafting recipe registered");

        PackCommand handler = (PackCommand) plugin.getCommand("pack").getExecutor();
        // Exercise the actual inventory transaction without requiring a connected player.
        Method tryPack = PackCommand.class.getDeclaredMethod("tryPack", Player.class, Inventory.class,
                Material.class, Material.class, int.class, int.class, boolean.class);
        tryPack.setAccessible(true);
        Inventory chest = Bukkit.createInventory(null, 27);
        chest.addItem(new ItemStack(Material.IRON_NUGGET, 64), new ItemStack(Material.IRON_NUGGET, 35));
        tryPack.invoke(handler, null, chest, Material.IRON_NUGGET, Material.IRON_INGOT, 9, 1, false);
        tryPack.invoke(handler, null, chest, Material.IRON_INGOT, Material.IRON_BLOCK, 9, 1, false);
        require(count(chest, Material.IRON_BLOCK) == 1 && count(chest, Material.IRON_INGOT) == 2
                && count(chest, Material.IRON_NUGGET) == 0, "tiered chest packing preserves totals");

        ItemStack[] full = new ItemStack[27];
        Arrays.setAll(full, index -> new ItemStack(Material.STONE, 64));
        full[0] = new ItemStack(Material.IRON_INGOT, 10);
        chest.setContents(full);
        ItemStack[] before = Arrays.stream(chest.getContents()).map(ItemStack::clone).toArray(ItemStack[]::new);
        tryPack.invoke(handler, null, chest, Material.IRON_INGOT, Material.IRON_BLOCK, 9, 1, false);
        require(Arrays.equals(before, chest.getContents()), "full inventory remains unchanged");

        UUID player = UUID.fromString("742139be-01a8-4c57-a745-52474c050009");
        plugin.setPackInputExcluded(player, Material.IRON_INGOT, true);
        plugin.savePackInputExcludedPersistentAsync(player);
        plugin.setAutoPackEnabledForPlayer(player, true);
        plugin.reloadPluginConfig();
        require(plugin.isPackInputExcluded(player, Material.IRON_INGOT), "SQLite exclusions survive reload");
        require(plugin.isAutoPackEnabledForPlayer(player), "SQLite auto-pack preference survives reload");
        plugin.setPackInputExcluded(player, Material.IRON_INGOT, false);
        plugin.savePackInputExcludedPersistentAsync(player);
        plugin.setAutoPackEnabledForPlayer(player, false);
        plugin.reloadPluginConfig();
        require(!plugin.isPackInputExcluded(player, Material.IRON_INGOT), "SQLite exclusion removal persists");
        require(!plugin.isAutoPackEnabledForPlayer(player), "SQLite auto-pack disable persists");

        plugin.getConfig().set("activation.mode", "COMMAND");
        plugin.saveConfig();
        plugin.reloadPluginConfig();
        require(Bukkit.getRecipe(recipeKey) == null, "item recipe removed in command mode");
    }

    private int count(final Inventory inventory, final Material material) {
        return Arrays.stream(inventory.getContents())
                .filter(item -> item != null && item.getType() == material)
                .mapToInt(ItemStack::getAmount).sum();
    }

    private void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        getLogger().info("PASS: " + message);
    }
}
