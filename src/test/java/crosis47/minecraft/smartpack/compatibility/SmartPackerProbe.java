package crosis47.minecraft.smartpack.compatibility;

import crosis47.minecraft.smartpack.SmartPack;
import crosis47.minecraft.smartpack.commands.PackCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Real menu events and scheduler callbacks with a simulated player; not a Bedrock-client test. */
final class SmartPackerProbe {
    private final JavaPlugin owner;
    private final SmartPack plugin;
    private final UUID playerId = UUID.fromString("742139be-01a8-4c57-a745-52474c050011");
    private final List<String> messages = new ArrayList<>();
    private final Set<String> permissions = new HashSet<>(Set.of("smartpack.use", "smartpack.chest", "smartpack.auto"));
    private final Inventory storage = Bukkit.createInventory(null, 36);
    private final PlayerInventory inventory;
    private final Player player;
    private final Block target = Bukkit.getWorlds().getFirst().getBlockAt(0, 0, 0);
    private final List<Runnable> steps = new ArrayList<>();
    private InventoryView view;
    private PackCommand handler;
    private int opens;

    SmartPackerProbe(final JavaPlugin owner, final SmartPack plugin) {
        this.owner = owner;
        this.plugin = plugin;
        inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getStorageContents", "getContents" -> storage.getContents();
                    case "setStorageContents", "setContents" -> { storage.setContents((ItemStack[]) args[0]); yield null; }
                    case "getItem" -> storage.getItem((int) args[0]);
                    case "setItem" -> { storage.setItem((int) args[0], (ItemStack) args[1]); yield null; }
                    case "getSize" -> 36;
                    default -> throw new UnsupportedOperationException("Unexpected inventory call: " + method);
                });
        player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "isOnline" -> true;
                    case "hasPermission" -> permissions.contains(args[0]);
                    case "getInventory" -> inventory;
                    case "getOpenInventory" -> view;
                    case "openInventory" -> { opens++; yield view = createView((Inventory) args[0]); }
                    case "closeInventory" -> { view = createView(Bukkit.createInventory(null, 9)); yield null; }
                    case "getItemOnCursor" -> new ItemStack(Material.AIR);
                    case "setItemOnCursor", "updateInventory" -> null;
                    case "getAttribute" -> null;
                    case "rayTraceBlocks" -> new RayTraceResult(target.getLocation().toVector(), target, BlockFace.UP);
                    case "sendMessage", "sendActionBar" -> {
                        messages.add(args[0] instanceof Component c ? PlainTextComponentSerializer.plainText().serialize(c) : args[0].toString());
                        yield null;
                    }
                    case "hashCode" -> playerId.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "SmartPacker test player";
                    default -> throw new UnsupportedOperationException("Unexpected player call: " + method);
                });
        view = createView(Bukkit.createInventory(null, 9));
    }

    void run(final Consumer<Throwable> finished) throws ReflectiveOperationException {
        plugin.getConfig().set("activation.mode", "SMART_PACKER_ITEM");
        plugin.getConfig().set("activation.smart_packer_item.cooldown.enabled", false);
        plugin.getConfig().set("auto_pack.enabled", true);
        storage.setItem(0, plugin.createSmartPackerItem());
        checkLore();
        var constructor = PackCommand.class.getDeclaredConstructor(SmartPack.class, Predicate.class);
        constructor.setAccessible(true);
        PackCommand javaHandler = constructor.newInstance(plugin, (Predicate<UUID>) id -> false);
        handler = constructor.newInstance(plugin, (Predicate<UUID>) id -> true);
        checkAutoControlVisibility();
        require(!click(javaHandler, 9, ClickType.LEFT).isCancelled(), "Java left-click still moves SmartPacker");
        require(click(javaHandler, 9, ClickType.RIGHT).isCancelled(), "Java right-click still activates SmartPacker");
        require(!click(handler, 9, ClickType.LEFT).isCancelled()
                && !click(handler, 9, ClickType.RIGHT).isCancelled(), "Bedrock inventory clicks can move SmartPacker");
        require(!interact(javaHandler, EquipmentSlot.HAND, storage.getItem(0)).isCancelled(), "Java held-item use unchanged");
        require(!interact(handler, EquipmentSlot.OFF_HAND, storage.getItem(0)).isCancelled(), "offhand does not open menu");
        require(!interact(handler, EquipmentSlot.HAND, new ItemStack(Material.CRAFTING_TABLE)).isCancelled(),
                "ordinary crafting tables do not open menu");

        steps.add(() -> {
            // The Java task from above has completed before these Bedrock checks.
            messages.clear();
            require(interact(handler, EquipmentSlot.HAND, storage.getItem(0)).isCancelled(), "Bedrock use prevents placement");
            interact(handler, EquipmentSlot.HAND, storage.getItem(0));
        });
        steps.add(() -> {
            require(opens == 1 && view.getTopInventory().getSize() == 27, "duplicate use opens one Bedrock action menu");
            for (ItemStack item : view.getTopInventory().getContents()) {
                require(!text(item).contains("right-click"), "action menu has no right-click hints");
            }
            require(click(handler, 0, ClickType.LEFT).isCancelled()
                    && click(handler, 27, ClickType.SHIFT_LEFT).isCancelled(), "menu blocks filler and player-inventory transfers");
            permissions.remove("smartpack.use");
            click(handler, 11, ClickType.LEFT);
            click(handler, 11, ClickType.LEFT);
        });
        steps.add(() -> {
            require(messages.size() == 1 && messages.getFirst().contains("permission"), "inventory action checks permission once");
            permissions.add("smartpack.use");
            interact(handler, EquipmentSlot.HAND, storage.getItem(0));
        });
        steps.add(() -> {
            require(view.getTopInventory().getItem(15).getType() == Material.GRAY_DYE
                    && text(view.getTopInventory().getItem(15)).contains("Enable Auto"), "disabled auto button uses dye and offers enable");
            click(handler, 15, ClickType.LEFT);
        });
        steps.add(() -> {
            require(plugin.isAutoPackEnabledForPlayer(playerId), "ordinary menu click enables auto mode");
            require(text(storage.getItem(0)).contains("Auto mode: Enabled"), "enabling auto immediately updates item status");
            javaHandler.onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));
            require(text(storage.getItem(0)).contains("Right-click to pack")
                    && text(storage.getItem(0)).contains("Auto mode: Enabled"), "Java join preserves player's enabled status");
            handler.onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));
            require(text(storage.getItem(0)).contains("Open SmartPacker actions to change auto mode."),
                    "Bedrock lore points to SmartPacker actions for auto mode");
            interact(handler, EquipmentSlot.HAND, storage.getItem(0));
        });
        steps.add(() -> {
            require(view.getTopInventory().getItem(15).getType() == Material.LIME_DYE
                    && text(view.getTopInventory().getItem(15)).contains("Disable Auto"), "enabled auto button uses dye and offers disable");
            click(handler, 15, ClickType.LEFT);
            click(handler, 15, ClickType.LEFT);
        });
        steps.add(() -> {
            require(!plugin.isAutoPackEnabledForPlayer(playerId)
                    && text(storage.getItem(0)).contains("Auto mode: Disabled"), "Bedrock disables auto once and immediately refreshes lore");
            require(messages.stream().anyMatch(s -> s.contains("Auto-pack disabled")), "disabling auto sends disabled feedback");
            click(javaHandler, 9, ClickType.SHIFT_RIGHT);
        });
        steps.add(() -> {
            require(plugin.isAutoPackEnabledForPlayer(playerId)
                    && text(storage.getItem(0)).contains("Auto mode: Enabled"), "Java shift-right-click enables auto");
            click(javaHandler, 9, ClickType.SHIFT_RIGHT);
        });
        steps.add(() -> {
            require(!plugin.isAutoPackEnabledForPlayer(playerId)
                    && text(storage.getItem(0)).contains("Auto mode: Disabled"), "Java shift-right-click disables auto");
            handler.onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));
            require(text(storage.getItem(0)).contains("Hold and use on a block")
                    && text(storage.getItem(0)).contains("Auto mode: Disabled"), "Bedrock join refreshes same item with player's disabled status");
            plugin.getConfig().set("activation.smart_packer_item.cooldown.enabled", true);
            plugin.getConfig().set("activation.smart_packer_item.cooldown.seconds", 10);
            interact(handler, EquipmentSlot.HAND, storage.getItem(0));
        });
        steps.add(() -> {
            require(view.getTopInventory().getItem(15).getType() == Material.GRAY_STAINED_GLASS_PANE,
                    "cooldown mode hides auto button");
            click(handler, 15, ClickType.LEFT);
        });
        steps.add(() -> {
            require(!plugin.isAutoPackEnabledForPlayer(playerId), "cooldown mode prevents menu auto activation");
            interact(handler, EquipmentSlot.HAND, storage.getItem(0));
        });
        steps.add(() -> click(handler, 11, ClickType.LEFT));
        steps.add(() -> {
            require(text(storage.getItem(0)).contains("Cooldown: 10s"), "inventory action starts cooldown with Bedrock lore");
            require(!text(storage.getItem(0)).contains("right-click"), "cooldown refresh keeps Bedrock instructions");
            messages.clear();
            interact(handler, EquipmentSlot.HAND, storage.getItem(0));
        });
        steps.add(() -> click(handler, 11, ClickType.LEFT));
        steps.add(() -> {
            require(messages.stream().anyMatch(s -> s.contains("cooling down")), "repeated menu action honors cooldown");
            plugin.getConfig().set("chest_pack.smart_packer_item", false);
            messages.clear();
            interact(handler, EquipmentSlot.HAND, storage.getItem(0));
        });
        steps.add(() -> click(handler, 13, ClickType.LEFT));
        steps.add(() -> {
            require(messages.stream().anyMatch(s -> s.contains("Chest packing is disabled")), "menu respects chest packing setting");
            plugin.getConfig().set("chest_pack.smart_packer_item", true);
            messages.clear();
            interact(handler, EquipmentSlot.HAND, storage.getItem(0));
        });
        steps.add(() -> click(handler, 13, ClickType.LEFT));
        steps.add(() -> {
            require(messages.stream().anyMatch(s -> s.contains("Look at a chest")), "chest action does not silently pack player inventory");
            messages.clear();
            interact(handler, EquipmentSlot.HAND, storage.getItem(0));
        });
        steps.add(() -> {
            storage.clear();
            click(handler, 15, ClickType.LEFT);
        });
        steps.add(() -> require(!plugin.isAutoPackEnabledForPlayer(playerId) && messages.isEmpty(),
                "removing SmartPacker before choosing action prevents activation"));
        next(0, finished);
    }

    private void next(final int index, final Consumer<Throwable> finished) {
        Bukkit.getScheduler().runTaskLater(owner, () -> {
            try {
                if (index == steps.size()) {
                    finished.accept(null);
                    return;
                }
                steps.get(index).run();
                next(index + 1, finished);
            } catch (Throwable failure) {
                finished.accept(failure);
            }
        }, 2L);
    }

    private void checkAutoControlVisibility() throws ReflectiveOperationException {
        var create = PackCommand.class.getDeclaredMethod("createSmartPackerForm", Player.class);
        create.setAccessible(true);
        boolean previous = plugin.isAutoPackEnabledForPlayer(playerId);
        plugin.setAutoPackEnabledForPlayer(playerId, true);
        try {
            Object enabledForm = create.invoke(handler, player);
            var buttons = enabledForm.getClass().getDeclaredMethod("buttons");
            var content = enabledForm.getClass().getDeclaredMethod("content");
            buttons.setAccessible(true);
            content.setAccessible(true);
            require(((List<?>) buttons.invoke(enabledForm)).size() == 4
                            && content.invoke(enabledForm).toString().contains("Auto mode: Enabled"),
                    "available auto mode adds a button and status to the native form");

            plugin.getConfig().set("auto_pack.enabled", false);
            Object disabledForm = create.invoke(handler, player);
            require(((List<?>) buttons.invoke(disabledForm)).size() == 3
                            && !content.invoke(disabledForm).toString().contains("Auto mode:"),
                    "server-disabled auto-pack hides the native button and status despite a saved enabled preference");
        } finally {
            plugin.getConfig().set("auto_pack.enabled", true);
            plugin.setAutoPackEnabledForPlayer(playerId, previous);
        }
    }

    private void checkLore() {
        ItemStack item = storage.getItem(0);
        plugin.updateSmartPackerItemLore(item, 0, true);
        require(text(item).contains("Hold and use on a block") && !text(item).contains("right-click"), "Bedrock item lore uses action menu");
        plugin.updateSmartPackerItemLore(item, 0, false);
        require(text(item).contains("Right-click to pack") && text(item).contains("shift-right-click"), "same item switches back to Java lore");
        for (boolean bedrock : new boolean[]{false, true}) {
            plugin.updateSmartPackerItemLore(item, 0, bedrock, true);
            require(text(item).contains("Auto mode: Enabled"), "enabled player status appears for " + (bedrock ? "Bedrock" : "Java"));
            plugin.updateSmartPackerItemLore(item, 0, bedrock, false);
            require(text(item).contains("Auto mode: Disabled"), "disabled player status appears for " + (bedrock ? "Bedrock" : "Java"));
        }
        plugin.getConfig().set("auto_pack.enabled", false);
        for (boolean bedrock : new boolean[]{false, true}) {
            plugin.updateSmartPackerItemLore(item, 0, bedrock, true);
            require(!text(item).contains("Auto mode"), "disabled auto-pack removes all auto lore for " + (bedrock ? "Bedrock" : "Java"));
        }
        plugin.getConfig().set("auto_pack.enabled", true);
        plugin.updateSmartPackerItemLore(item, 0, true);
        require(text(item).contains("Open SmartPacker actions to change auto mode.")
                        && text(item).contains("Auto mode packs after item pickups."),
                "re-enabling auto-pack restores instructions on the same item");
        plugin.getConfig().set("activation.smart_packer_item.cooldown.enabled", true);
        plugin.updateSmartPackerItemLore(item, 100, true);
        require(text(item).contains("Cooldown: 5s") && !text(item).contains("change auto mode"), "Bedrock cooldown replaces auto instructions");
        require(plugin.isSmartPackerItem(item), "client lore changes preserve item marker");
        plugin.getConfig().set("activation.smart_packer_item.cooldown.enabled", false);
    }

    private PlayerInteractEvent interact(final PackCommand targetHandler, final EquipmentSlot hand, final ItemStack item) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, item, target, BlockFace.UP, hand);
        targetHandler.onPlayerInteract(event);
        return event;
    }

    private InventoryClickEvent click(final PackCommand targetHandler, final int slot, final ClickType type) {
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        targetHandler.onInventoryClick(event);
        return event;
    }

    private InventoryView createView(final Inventory top) {
        return (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(), new Class<?>[]{InventoryView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTopInventory" -> top;
                    case "getBottomInventory" -> inventory;
                    case "getPlayer" -> player;
                    case "getType" -> InventoryType.CHEST;
                    case "getInventory" -> (int) args[0] < 0 ? null : (int) args[0] < top.getSize() ? top : inventory;
                    case "convertSlot" -> (int) args[0] < top.getSize() ? args[0] : (int) args[0] - top.getSize();
                    case "getItem" -> (int) args[0] < top.getSize() ? top.getItem((int) args[0]) : storage.getItem((int) args[0] - top.getSize());
                    default -> throw new UnsupportedOperationException("Unexpected view call: " + method);
                });
    }

    private String text(final ItemStack item) {
        var meta = item.getItemMeta();
        String text = meta.hasDisplayName() ? PlainTextComponentSerializer.plainText().serialize(meta.displayName()) : "";
        if (meta.hasLore()) {
            for (Component line : meta.lore()) {
                text += "\n" + PlainTextComponentSerializer.plainText().serialize(line);
            }
        }
        return text;
    }

    private void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        owner.getLogger().info("PASS: " + message);
    }
}
