package crosis47.minecraft.smartpack.compatibility;

import crosis47.minecraft.smartpack.SmartPack;
import crosis47.minecraft.smartpack.commands.PackCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Logger;

/** Uses real Paper inventories and menu handlers with a simulated player, not a Bedrock client. */
final class ExclusionMenuProbe {
    private final SmartPack plugin;
    private PackCommand handler;
    private final Logger logger;
    private final UUID playerId = UUID.fromString("742139be-01a8-4c57-a745-52474c050010");
    private final Player player;
    private final List<String> messages = new ArrayList<>();
    private boolean bedrockMenu;
    private InventoryView view;

    ExclusionMenuProbe(final SmartPack plugin, final PackCommand handler, final Logger logger) {
        this.plugin = plugin;
        this.handler = handler;
        this.logger = logger;
        player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "hasPermission" -> "smartpack.use".equals(args[0]);
                    case "openInventory" -> view = createView((Inventory) args[0]);
                    case "sendMessage" -> {
                        if (args[0] instanceof Component message) {
                            messages.add(PlainTextComponentSerializer.plainText().serialize(message));
                        }
                        yield null;
                    }
                    case "closeInventory" -> null;
                    case "toString" -> "SmartPack exclusion test player";
                    case "hashCode" -> playerId.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("Unexpected player call: " + method);
                });
    }

    void run() throws ReflectiveOperationException {
        ConfigurationSection originalPack = plugin.getConfig().getConfigurationSection("pack");
        try {
            plugin.replacePackInputExcludedPersistent(playerId, Set.of());
            plugin.replacePackInputExcludedNextRun(playerId, Set.of());
            configurePagination();
            checkJavaMenu();
            // Inject only the client classification; all rendering/click/storage paths stay real.
            var constructor = PackCommand.class.getDeclaredConstructor(SmartPack.class, Predicate.class);
            constructor.setAccessible(true);
            handler = constructor.newInstance(plugin, (Predicate<UUID>) ignored -> true);
            bedrockMenu = true;
            messages.clear();
            open();
            Inventory top = view.getTopInventory();
            require(top.getSize() == 54 && top.getItem(45) == null && top.getItem(46) == null
                            && top.getItem(48) == null && top.getItem(51) == null,
                    "Bedrock exclusions keep the double chest size with empty unused control slots");
            Material material = view.getTopInventory().getItem(0).getType();
            click(0, ClickType.LEFT);
            require(plugin.isPackInputExcludedNextRun(playerId, material)
                    && !plugin.isPackInputExcluded(playerId, material), "default clicks still toggle next-run skips");
            control("Edit Mode: Next Run Only");
            require(plugin.isPackInputExcludedNextRun(playerId, material)
                    && !plugin.isPackInputExcluded(playerId, material), "switching edit mode does not change exclusions");
            require(lore(0).contains("Click to make the exclusion permanent."), "item guidance follows Permanent mode");
            click(0, ClickType.LEFT);
            require(plugin.isPackInputExcluded(playerId, material)
                    && !plugin.isPackInputExcludedNextRun(playerId, material), "left-click promotes temporary exclusion to permanent");
            control("Next Page");
            require(findControl("Edit Mode: Permanent") >= 0, "Permanent mode survives next-page navigation");
            control("Previous Page");
            click(0, ClickType.LEFT);
            require(!plugin.isPackInputExcluded(playerId, material), "left-click removes permanent exclusion after navigation");
            click(0, ClickType.LEFT);
            int beforeApply = messages.size();
            control("Apply Changes");
            require(messages.size() == beforeApply + 2
                    && messages.get(beforeApply).endsWith(" is now persistently excluded from packing.")
                    && messages.get(beforeApply + 1).equals("Exclusion changes applied."),
                    "Apply reports only the final permanent change, not intermediate toggles");
            close();
            require(messages.size() == beforeApply + 2, "Apply followed by close does not duplicate messages");
            plugin.reloadPluginConfig();
            require(plugin.isPackInputExcluded(playerId, material), "left-click permanent exclusion survives Apply and SQLite reload");
            configurePagination();

            open();
            require(findControl("Edit Mode: Next Run Only") >= 0, "reopened menu starts in Next Run Only mode");
            int slot = findMaterial(material);
            click(slot, ClickType.LEFT);
            require(plugin.isPackInputExcluded(playerId, material)
                    && !plugin.isPackInputExcludedNextRun(playerId, material), "Next Run Only mode protects existing permanent exclusions");
            control("Edit Mode: Next Run Only");
            click(slot, ClickType.LEFT);
            control("Cancel Changes");
            close();
            plugin.reloadPluginConfig();
            require(plugin.isPackInputExcluded(playerId, material), "Cancel restores permanent exclusions in memory and SQLite");
            configurePagination();

            open();
            slot = findMaterial(material);
            control("Edit Mode: Next Run Only");
            click(slot, ClickType.LEFT);
            int beforeClose = messages.size();
            close();
            require(messages.size() == beforeClose + 2
                    && messages.get(beforeClose).endsWith(" is no longer persistently excluded."),
                    "closing the menu reports the final applied removal");
            plugin.reloadPluginConfig();
            require(!plugin.isPackInputExcluded(playerId, material), "closing menu saves removal of permanent exclusion");
            configurePagination();

            open();
            slot = findMaterial(material);
            click(slot, ClickType.RIGHT);
            require(plugin.isPackInputExcludedNextRun(playerId, material)
                    && !plugin.isPackInputExcluded(playerId, material), "Bedrock actions follow selected mode regardless of mouse button");
            click(slot, ClickType.RIGHT);
            require(!plugin.isPackInputExcludedNextRun(playerId, material), "Bedrock alternate-button click toggles the same next-run action");
            click(slot, ClickType.NUMBER_KEY);
            require(!plugin.isPackInputExcluded(playerId, material)
                    && !plugin.isPackInputExcludedNextRun(playerId, material), "hotbar-key actions do not edit exclusions");
            click(slot, ClickType.LEFT);
            control("Edit Mode: Next Run Only");
            click(slot, ClickType.LEFT);
            control("Cancel Changes");
            close();
            require(!plugin.isPackInputExcluded(playerId, material)
                    && !plugin.isPackInputExcludedNextRun(playerId, material), "Cancel restores both exclusion types after promotion");

            open();
            slot = findMaterial(material);
            click(slot, ClickType.LEFT);
            click(slot, ClickType.LEFT);
            beforeApply = messages.size();
            control("Apply Changes");
            close();
            require(messages.size() == beforeApply + 1 && messages.get(beforeApply).equals("Exclusion changes applied."),
                    "toggling an item back to its original setting does not report a change");

            open();
            slot = findMaterial(material);
            click(slot, ClickType.LEFT);
            beforeApply = messages.size();
            control("Apply Changes");
            close();
            require(messages.size() == beforeApply + 2
                    && messages.get(beforeApply).endsWith(" will be skipped on the next pack run only."),
                    "next-run change messages are deferred until Apply");
            plugin.clearAllPackInputsExcludedNextRun(playerId);
        } finally {
            if (view != null) {
                close();
            }
            plugin.getConfig().set("pack", originalPack);
        }
    }

    private void checkJavaMenu() {
        open();
        Material material = view.getTopInventory().getItem(0).getType();
        require(view.getTopInventory().getItem(47).getType() == Material.GRAY_STAINED_GLASS_PANE,
                "Java menu has no edit-mode button when Bedrock is not detected");
        require(lore(0).contains("Left-click") && lore(0).contains("Right-click"), "Java item instructions retain both click actions");
        require(lore(49).contains("Right-click") && !lore(49).contains("mode button"), "Java help describes original controls");
        click(47, ClickType.LEFT);
        click(0, ClickType.LEFT);
        require(plugin.isPackInputExcludedNextRun(playerId, material)
                && !plugin.isPackInputExcluded(playerId, material), "Java filler cannot switch left-click away from next-run skips");
        click(0, ClickType.RIGHT);
        require(plugin.isPackInputExcluded(playerId, material)
                && !plugin.isPackInputExcludedNextRun(playerId, material), "Java right-click promotes exclusion to permanent");
        click(0, ClickType.LEFT);
        require(plugin.isPackInputExcluded(playerId, material), "Java left-click protects permanent exclusions");
        click(0, ClickType.RIGHT);
        require(!plugin.isPackInputExcluded(playerId, material), "Java right-click removes permanent exclusion");
        control("Next Page");
        require(view.getTopInventory().getItem(47).getType() == Material.GRAY_STAINED_GLASS_PANE,
                "Java pagination does not introduce an edit-mode button");
        control("Cancel Changes");
        close();
    }

    private void configurePagination() {
        // Enough entries to exercise pagination, without persisting this test configuration.
        plugin.getConfig().set("pack", null);
        Arrays.stream(Material.values()).filter(material -> material.isItem() && !material.isAir()).limit(46)
                .forEach(material -> plugin.getConfig().set("pack." + material.name() + ".output", "STONE"));
    }

    private void open() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(handler.createCommand());
        CommandSourceStack source = (CommandSourceStack) Proxy.newProxyInstance(CommandSourceStack.class.getClassLoader(),
                new Class<?>[]{CommandSourceStack.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getSender")) {
                        return player;
                    }
                    throw new UnsupportedOperationException("Unexpected command source call: " + method);
                });
        try {
            dispatcher.execute("pack exclude", source);
        } catch (CommandSyntaxException ex) {
            throw new AssertionError("Could not run /pack exclude", ex);
        }
        checkBedrockText();
    }

    private void close() {
        handler.onInventoryClose(new InventoryCloseEvent(view));
        view = null;
    }

    private void control(final String name) {
        int before = messages.size();
        click(findControl(name), ClickType.LEFT);
        if (name.equals("Cancel Changes")) {
            require(messages.size() == before + 1 && messages.get(before).equals("Exclusion changes canceled."),
                    "Cancel prints no item change messages");
        }
    }

    private int findControl(final String name) {
        for (int slot = 45; slot < 54; slot++) {
            ItemStack item = view.getTopInventory().getItem(slot);
            if (item != null && name.equals(PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()))) {
                return slot;
            }
        }
        throw new AssertionError("Missing menu control: " + name);
    }

    private int findMaterial(final Material material) {
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = view.getTopInventory().getItem(slot);
            if (item != null && item.getType() == material) {
                return slot;
            }
        }
        throw new AssertionError("Missing menu material: " + material);
    }

    private String lore(final int slot) {
        return view.getTopInventory().getItem(slot).getItemMeta().lore().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize).reduce("", (a, b) -> a + "\n" + b);
    }

    private void click(final int slot, final ClickType click) {
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                slot, click, InventoryAction.PICKUP_ALL);
        int before = messages.size();
        handler.onInventoryClick(event);
        require(event.isCancelled(), "menu cancels inventory movement for " + click);
        if (slot != 52 && slot != 53 && messages.size() != before) {
            throw new AssertionError("Editing items or navigating the menu printed a chat message before Apply");
        }
        checkBedrockText();
    }

    private void checkBedrockText() {
        if (!bedrockMenu) {
            return;
        }
        for (ItemStack item : view.getTopInventory().getContents()) {
            if (item != null && item.getItemMeta().hasLore()) {
                for (Component line : item.getItemMeta().lore()) {
                    if (PlainTextComponentSerializer.plainText().serialize(line).toLowerCase(java.util.Locale.ROOT).contains("right-click")) {
                        throw new AssertionError("Bedrock menu contains right-click guidance");
                    }
                }
            }
        }
        if (messages.stream().anyMatch(message -> message.toLowerCase(java.util.Locale.ROOT).contains("right-click"))) {
            throw new AssertionError("Bedrock feedback contains right-click guidance");
        }
    }

    private InventoryView createView(final Inventory top) {
        Inventory bottom = Bukkit.createInventory(null, 36);
        return (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(), new Class<?>[]{InventoryView.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getTopInventory" -> top;
                    case "getBottomInventory" -> bottom;
                    case "getPlayer" -> player;
                    case "getType" -> InventoryType.CHEST;
                    case "getInventory" -> (int) args[0] < 0 ? null : (int) args[0] < top.getSize() ? top : bottom;
                    case "convertSlot" -> (int) args[0] < top.getSize() ? args[0] : (int) args[0] - top.getSize();
                    case "getItem" -> top.getItem((int) args[0]);
                    default -> throw new UnsupportedOperationException("Unexpected view call: " + method);
                });
    }

    private void require(final boolean condition, final String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        logger.info("PASS: " + message);
    }
}
