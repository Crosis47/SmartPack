package crosis47.minecraft.smartpack.compatibility;

import crosis47.minecraft.smartpack.SmartPack;
import crosis47.minecraft.smartpack.commands.PackCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/** Runs inside a disposable Paper server; never packaged in the production plugin. */
public final class CompatibilityProbe extends JavaPlugin {

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                checkCompatibility();
                new SmartPackerProbe(this, (SmartPack) Bukkit.getPluginManager().getPlugin("SmartPack")).run(this::finish);
            } catch (Throwable failure) {
                finish(failure);
            }
        });
    }

    private void finish(final Throwable failure) {
        if (failure == null) {
            getLogger().info("SMARTPACK_COMPATIBILITY_PASS " + Bukkit.getMinecraftVersion());
        } else {
            getLogger().severe("SMARTPACK_COMPATIBILITY_FAIL");
            failure.printStackTrace();
        }
        Bukkit.shutdown();
    }

    private void checkCompatibility() throws Exception {
        SmartPack plugin = (SmartPack) Bukkit.getPluginManager().getPlugin("SmartPack");
        require(plugin != null && plugin.isEnabled(), "SmartPack enabled with SQLite storage");
        require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pack reload"), "reload command registered");
        require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "smartpack:pack reload"), "namespaced reload command registered");

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
        checkBedrockForms();

        PackCommand handler = HandlerList.getRegisteredListeners(plugin).stream().map(listener -> listener.getListener())
                .filter(PackCommand.class::isInstance).map(PackCommand.class::cast).findFirst().orElseThrow();
        checkPackSubcommands(plugin, handler);
        new ExclusionMenuProbe(plugin, handler, getLogger()).run();
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

    private void checkPackSubcommands(final SmartPack plugin, final PackCommand handler) {
        var config = plugin.getConfig();
        Object oldMode = config.get("activation.mode");
        Object oldAuto = config.get("auto_pack.enabled");
        Object oldChest = config.get("chest_pack.enabled");
        Object oldChestCommand = config.get("chest_pack.command");
        try {
            require(visibleSubcommands(handler, Set.of("smartpack.use", "smartpack.chest", "smartpack.auto"))
                    .equals(Set.of("exclude")), "item mode advertises exclude to regular players");
            config.set("activation.mode", "COMMAND");
            config.set("auto_pack.enabled", true);
            config.set("chest_pack.enabled", true);
            config.set("chest_pack.command", true);
            require(visibleSubcommands(handler, Set.of("smartpack.use", "smartpack.chest", "smartpack.auto", "smartpack.reload"))
                    .equals(Set.of("exclude", "chest", "auto", "reload")), "command mode advertises all permitted literal subcommands");
            require(visibleSubcommands(handler, Set.of("smartpack.use", "smartpack.chest"))
                    .equals(Set.of("exclude", "chest")), "subcommand suggestions respect permissions");
            config.set("auto_pack.enabled", false);
            config.set("chest_pack.command", false);
            require(visibleSubcommands(handler, Set.of("smartpack.use", "smartpack.chest", "smartpack.auto"))
                    .equals(Set.of("exclude")), "disabled features are omitted from command suggestions");
        } finally {
            config.set("activation.mode", oldMode);
            config.set("auto_pack.enabled", oldAuto);
            config.set("chest_pack.enabled", oldChest);
            config.set("chest_pack.command", oldChestCommand);
        }
    }

    private Set<String> visibleSubcommands(final PackCommand handler, final Set<String> permissions) {
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> permissions.contains(args[0]));
        CommandSourceStack source = (CommandSourceStack) Proxy.newProxyInstance(CommandSourceStack.class.getClassLoader(),
                new Class<?>[]{CommandSourceStack.class}, (proxy, method, args) -> sender);
        return handler.createCommand().getChildren().stream().filter(node -> node.canUse(source))
                .map(node -> node.getName()).collect(Collectors.toSet());
    }

    private void checkBedrockForms() throws ReflectiveOperationException {
        Method build = BedrockForms.class.getDeclaredMethod("createSimpleForm", ClassLoader.class, String.class,
                String.class, List.class, IntConsumer.class, Runnable.class);
        build.setAccessible(true);
        for (String name : List.of("Geyser-Spigot", "floodgate")) {
            var provider = Bukkit.getPluginManager().getPlugin(name);
            if (provider == null || !provider.isEnabled()) {
                continue;
            }
            ClassLoader loader = provider.getClass().getClassLoader();
            Class<?> definitionType = loader.loadClass("org.geysermc.cumulus.form.impl.FormDefinition");
            Class<?> formType = loader.loadClass("org.geysermc.cumulus.form.Form");
            Object definition = loader.loadClass("org.geysermc.cumulus.form.impl.simple.SimpleFormDefinition")
                    .getMethod("instance").invoke(null);
            Method respond = definitionType.getMethod("handleFormResponse", formType, String.class);
            List<String> responses = new ArrayList<>();
            IntConsumer select = button -> responses.add(Integer.toString(button));
            Runnable dismiss = () -> responses.add("dismissed");
            List<BedrockForms.Button> buttons = List.of(
                    new BedrockForms.Button("Pack Inventory", "textures/items/bundle"),
                    new BedrockForms.Button("Pack Chest", "textures/blocks/chest_front"),
                    new BedrockForms.Button("Disable Auto", "textures/items/dye_powder_red"),
                    new BedrockForms.Button("Close", "textures/ui/cancel"));
            Object form = build.invoke(null, loader, "SmartPacker", "Auto mode: Enabled", buttons, select, dismiss);
            Object codec = definitionType.getMethod("codec").invoke(definition);
            String json = (String) loader.loadClass("org.geysermc.cumulus.form.util.FormCodec")
                    .getMethod("jsonData", formType).invoke(codec, form);
            require(json.contains("Auto mode: Enabled") && json.contains("\"type\":\"path\"")
                            && buttons.stream().allMatch(button -> json.contains(button.label()) && json.contains(button.texturePath())),
                    name + " encodes the SmartPacker status and four buttons with built-in icons");
            respond.invoke(definition, form, "0");
            respond.invoke(definition, form, "1");
            respond.invoke(definition, form, "2");
            respond.invoke(definition, form, "3");
            respond.invoke(definition, form, (String) null);
            Object packingOnly = build.invoke(null, loader, "SmartPacker", "Choose what to pack.",
                    List.of(buttons.get(0), buttons.get(1), buttons.get(3)), select, dismiss);
            respond.invoke(definition, packingOnly, "2");
            require(responses.equals(List.of("0", "1", "2", "3", "dismissed", "2")),
                    name + " returns the selected button and handles dismissals");
        }
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
