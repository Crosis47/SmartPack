package crosis47.minecraft.smartpack.commands;

import crosis47.minecraft.smartpack.SmartPack;
import crosis47.minecraft.smartpack.requirements.CraftingTableMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

public final class PackCommand implements TabExecutor, Listener {

    private static final int EXCLUDE_MENU_SIZE = 54;
    private static final int EXCLUDE_MENU_CONTENT_SIZE = 45;
    private static final int EXCLUDE_MENU_PREVIOUS_SLOT = 45;
    private static final int EXCLUDE_MENU_INFO_SLOT = 49;
    private static final int EXCLUDE_MENU_NEXT_SLOT = 50;
    private static final int EXCLUDE_MENU_APPLY_SLOT = 52;
    private static final int EXCLUDE_MENU_CANCEL_SLOT = 53;
    private static final long PACK_PASS_DELAY_TICKS = 1L;
    private static final int PACK_SETTLE_TICKS = 10;

    private final SmartPack plugin;
    private final Map<UUID, ExcludeMenuSession> excludeMenuSessions = new HashMap<>();
    private final Set<UUID> packInProgress = new HashSet<>();
    private final Set<UUID> pendingAutoPack = new HashSet<>();
    private final Map<UUID, Long> lastAutoPackTicks = new HashMap<>();
    private final Map<UUID, Long> lastAutoInventoryFullMessageTicks = new HashMap<>();
    private final Map<UUID, Long> smartPackerCooldownUntilTicks = new HashMap<>();

    public PackCommand(final SmartPack plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::refreshSmartPackerCooldownTooltips,
                20L,
                20L
        );
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("smartpack.reload")) {
                    sender.sendMessage(getMessage(
                            "message.error.no_permission",
                            "You do not have permission to use this command."
                    ));
                    return true;
                }

                plugin.reloadPluginConfig();
                sender.sendMessage(getMessage(
                        "message.reload",
                        "SmartPack config reloaded."
                ));
                return true;
            }

            if (args[0].equalsIgnoreCase("exclude")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This command can only be used by a player.");
                    return true;
                }

                openExcludeMenu(player, 0);
                return true;
            }

            if (args[0].equalsIgnoreCase("auto")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This command can only be used by a player.");
                    return true;
                }

                if (!player.hasPermission("smartpack.auto")) {
                    player.sendMessage(getMessage(
                            "message.error.no_permission",
                            "You do not have permission to use this command."
                    ));
                    return true;
                }

                if (!isAutoPackAvailableInCurrentMode()) {
                    sendAutoPackUnavailableForCommand(player);
                    return true;
                }

                if (plugin.isSmartPackerItemModeEnabled()) {
                    player.sendMessage(getMessage(
                            "message.info.auto_pack_smart_packer_item_required",
                            "Auto mode must be enabled through the Smart Packer item."
                    ));
                    return true;
                }

                boolean enabled = plugin.toggleAutoPackEnabledForPlayer(player.getUniqueId());
                sendAutoPackToggleMessage(player, enabled);
                return true;
            }

            if (args[0].equalsIgnoreCase("chest")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This command can only be used by a player.");
                    return true;
                }

                handleChestPackCommand(player);
                return true;
            }
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        if (plugin.isSmartPackerItemModeEnabled()) {
            if (!plugin.isSmartPackerCommandAllowed()) {
                player.sendMessage(getMessage(
                        "message.info.use_smart_packer_item",
                        "Use a Smart Packer from your inventory to pack materials."
                ));
                return true;
            }

            if (!plugin.hasSmartPackerItem(player.getInventory().getStorageContents())) {
                player.sendMessage(getMessage(
                        "message.error.smart_packer_item_required",
                        "You must have a Smart Packer in your inventory to use this command."
                ));
                return true;
            }

            if (!tryStartSmartPackerCooldown(player, true)) {
                return true;
            }
        }

        executePack(player, PackRequest.manual());
        return true;
    }

    private void handleChestPackCommand(final Player player) {
        if (plugin.isSmartPackerItemModeEnabled()) {
            player.sendMessage(getMessage(
                    "message.info.chest_pack_command_mode_required",
                    "Use the Smart Packer item while a chest is open to pack chest inventories."
            ));
            return;
        }

        if (!isChestPackCommandEnabled()) {
            sendChestPackDisabledMessage(player);
            return;
        }

        if (!player.hasPermission("smartpack.chest")) {
            player.sendMessage(getMessage(
                    "message.error.no_permission",
                    "You do not have permission to use this command."
            ));
            return;
        }

        Inventory chestInventory = getLookedAtChestInventory(player);
        if (chestInventory == null) {
            player.sendMessage(getMessage(
                    "message.error.no_chest_target",
                    "Look at a chest within interaction range to pack it."
            ));
            return;
        }

        executePack(player, PackRequest.chest(chestInventory));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ExcludeMenuHolder holder) {
            handleExcludeMenuClick(event, holder);
            return;
        }

        if (!plugin.isSmartPackerItemModeEnabled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getClickedInventory() instanceof PlayerInventory)) {
            return;
        }

        if (!event.getClick().isRightClick()) {
            return;
        }

        if (!plugin.isSmartPackerItem(event.getCurrentItem())) {
            return;
        }

        event.setCancelled(true);

        boolean enableAutoPack = event.getClick().isShiftClick();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (enableAutoPack) {
                enableAutoPackFromSmartPackerItem(player);
                return;
            }

            if (!player.hasPermission("smartpack.use")) {
                player.sendMessage(getMessage(
                        "message.error.no_permission",
                        "You do not have permission to use this command."
                ));
                return;
            }

            Inventory chestInventory = getOpenChestInventory(player);
            if (chestInventory != null && isChestPackSmartPackerItemEnabled()) {
                if (!player.hasPermission("smartpack.chest")) {
                    player.sendMessage(getMessage(
                            "message.error.no_permission",
                            "You do not have permission to use this command."
                    ));
                    return;
                }

                if (!tryStartSmartPackerCooldown(player, true)) {
                    return;
                }

                executePack(player, PackRequest.chest(chestInventory));
                return;
            }

            if (!tryStartSmartPackerCooldown(player, true)) {
                return;
            }

            executePack(player);
        });
    }

    private boolean isChestPackEnabled() {
        return plugin.getConfig().getBoolean("chest_pack.enabled", true);
    }

    private boolean isChestPackCommandEnabled() {
        return isChestPackEnabled() && plugin.getConfig().getBoolean("chest_pack.command", true);
    }

    private boolean isChestPackSmartPackerItemEnabled() {
        return isChestPackEnabled() && plugin.getConfig().getBoolean("chest_pack.smart_packer_item", true);
    }

    private void sendChestPackDisabledMessage(final Player player) {
        player.sendMessage(getMessage(
                "message.error.chest_pack_disabled",
                "Chest packing is disabled on this server."
        ));
    }

    private Inventory getLookedAtChestInventory(final Player player) {
        RayTraceResult result = player.rayTraceBlocks(getBlockInteractionRange(player), FluidCollisionMode.NEVER);
        if (result == null) {
            return null;
        }

        Block block = result.getHitBlock();
        if (block == null) {
            return null;
        }

        return getChestBlockInventory(block);
    }

    private double getBlockInteractionRange(final Player player) {
        AttributeInstance range = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (range == null) {
            return 4.5D;
        }

        return Math.max(0.1D, range.getValue());
    }

    private Inventory getOpenChestInventory(final Player player) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        return isChestInventory(inventory) ? inventory : null;
    }

    private boolean isChestInventory(final Inventory inventory) {
        if (inventory == null) {
            return false;
        }

        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Chest || holder instanceof DoubleChest) {
            return true;
        }

        Location location = inventory.getLocation();
        return location != null && getChestBlockInventory(location.getBlock()) != null;
    }

    private Inventory getChestBlockInventory(final Block block) {
        if (block == null || (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST)) {
            return null;
        }

        if (!(block.getState() instanceof Chest chest)) {
            return null;
        }

        return chest.getInventory();
    }

    private boolean tryStartSmartPackerCooldown(final Player player, final boolean notifyWhenCoolingDown) {
        if (!plugin.isSmartPackerCooldownModeEnabled()
                || player.hasPermission("smartpack.cooldown.bypass")) {
            refreshSmartPackerItems(player);
            return true;
        }

        long cooldownTicks = plugin.getSmartPackerCooldownTicks();
        if (cooldownTicks <= 0L) {
            refreshSmartPackerItems(player);
            return true;
        }

        UUID playerId = player.getUniqueId();
        long remainingTicks = getRemainingSmartPackerCooldownTicks(playerId);
        if (remainingTicks > 0L) {
            if (notifyWhenCoolingDown) {
                String message = getMessage(
                        "message.info.smart_packer_cooldown_active",
                        "Smart Packer cooling down: [time]."
                ).replace("[time]", formatCooldownTicks(remainingTicks));
                sendActionBar(player, message);
            }
            refreshSmartPackerItems(player);
            return false;
        }

        smartPackerCooldownUntilTicks.put(playerId, Bukkit.getCurrentTick() + cooldownTicks);
        refreshSmartPackerItems(player);
        return true;
    }

    private long getRemainingSmartPackerCooldownTicks(final UUID playerId) {
        Long cooldownUntilTick = smartPackerCooldownUntilTicks.get(playerId);
        if (cooldownUntilTick == null) {
            return 0L;
        }

        long remainingTicks = cooldownUntilTick - Bukkit.getCurrentTick();
        if (remainingTicks <= 0L) {
            smartPackerCooldownUntilTicks.remove(playerId);
            return 0L;
        }

        return remainingTicks;
    }

    private void refreshSmartPackerCooldownTooltips() {
        if (!plugin.isSmartPackerItemModeEnabled()) {
            smartPackerCooldownUntilTicks.clear();
            return;
        }

        if (!plugin.isSmartPackerCooldownModeEnabled()) {
            smartPackerCooldownUntilTicks.clear();
        } else {
            long currentTick = Bukkit.getCurrentTick();
            List<UUID> readyPlayerIds = new ArrayList<>();
            smartPackerCooldownUntilTicks.entrySet().removeIf(entry -> {
                if (entry.getValue() > currentTick) {
                    return false;
                }

                readyPlayerIds.add(entry.getKey());
                return true;
            });

            for (UUID playerId : readyPlayerIds) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline() && !player.hasPermission("smartpack.cooldown.bypass")) {
                    sendSmartPackerCooldownReadyMessage(player);
                }
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshSmartPackerItems(player);
        }
    }

    private void refreshSmartPackerItems(final Player player) {
        if (player == null || !plugin.isSmartPackerItemModeEnabled()) {
            return;
        }

        long remainingTicks = player.hasPermission("smartpack.cooldown.bypass")
                ? 0L
                : getRemainingSmartPackerCooldownTicks(player.getUniqueId());
        boolean updated = false;

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!plugin.isSmartPackerItem(item)) {
                continue;
            }

            if (plugin.updateSmartPackerItemLore(item, remainingTicks)) {
                inventory.setItem(slot, item);
                updated = true;
            }
        }

        ItemStack cursorItem = player.getItemOnCursor();
        if (plugin.isSmartPackerItem(cursorItem)) {
            updated |= plugin.updateSmartPackerItemLore(cursorItem, remainingTicks);
            player.setItemOnCursor(cursorItem);
        }

        if (updated) {
            player.updateInventory();
        }
    }

    private String formatCooldownTicks(final long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        if (seconds < 60L) {
            return seconds + "s";
        }

        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (remainingSeconds == 0L) {
            return minutes + "m";
        }

        return minutes + "m " + remainingSeconds + "s";
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPickupItem(final EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        queueAutoPack(player, AutoPackTrigger.PICKUP);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ExcludeMenuHolder)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ExcludeMenuHolder holder)) {
            return;
        }

        ExcludeMenuSession session = excludeMenuSessions.remove(holder.playerId());
        if (session == null) {
            return;
        }

        if (event.getPlayer() instanceof Player player) {
            persistPersistentExclusionsIfChanged(player, session);
            sendExclusionChangesAppliedMessage(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (!plugin.isSmartPackerItem(event.getItemInHand())) {
            if (event.getBlockPlaced().getType() == Material.CRAFTING_TABLE
                    && shouldCheckNearbyCraftingTableAutoTrigger(
                            event.getPlayer(),
                            AutoPackTrigger.CRAFTING_TABLE_PLACE
                    )) {
                queueAutoPack(event.getPlayer(), AutoPackTrigger.CRAFTING_TABLE_PLACE);
            }
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(final PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (!hasChangedBlock(from, to)) {
            return;
        }

        Player player = event.getPlayer();
        if (!shouldCheckNearbyCraftingTableAutoTrigger(player, AutoPackTrigger.CRAFTING_TABLE_NEARBY)) {
            return;
        }

        int range = getCraftingTableRange();
        if (isNearCraftingTable(from, range) || !isNearCraftingTable(to, range)) {
            return;
        }

        queueAutoPack(player, AutoPackTrigger.CRAFTING_TABLE_NEARBY);
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        plugin.cleanupSmartPackerItems(event.getPlayer());
        refreshSmartPackerItems(event.getPlayer());
        queueAutoPack(event.getPlayer(), AutoPackTrigger.JOIN);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingAutoPack.remove(playerId);
        lastAutoPackTicks.remove(playerId);
        lastAutoInventoryFullMessageTicks.remove(playerId);
        smartPackerCooldownUntilTicks.remove(playerId);
    }

    public void executePack(final Player player) {
        executePack(player, PackRequest.manual());
    }

    public void executeAutoPack(final Player player) {
        executePack(player, PackRequest.auto());
    }

    private void executePack(final Player player, final PackRequest request) {
        UUID playerId = player.getUniqueId();

        if (!packInProgress.add(playerId)) {
            if (!request.automatic()) {
                player.sendMessage(Component.text("SmartPack is already running.", NamedTextColor.YELLOW));
            }
            return;
        }

        continuePackExecution(playerId, new PackExecution(), request);
    }

    private void continuePackExecution(
            final UUID playerId,
            final PackExecution execution,
            final PackRequest request
    ) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            cleanupPackExecution(playerId);
            return;
        }

        try {
            CraftingRequirementState craftingState = getCraftingRequirementState(player);
            if (!craftingState.valid()) {
                if (!request.automatic()) {
                    player.sendMessage(craftingState.failureMessage());
                }
                cleanupPackExecution(playerId);
                return;
            }

            PackResult result = pack(player, request.targetInventory(player), craftingState, execution, request);
            execution.merge(result);

            if (result.totalProduced() > 0 || result.inventoryChangedDuringRun()) {
                execution.resetSettleTicks();
                scheduleNextPackExecution(playerId, execution, request);
                return;
            }

            if (execution.shouldWaitForInventoryToSettle()) {
                execution.consumeSettleTick();
                scheduleNextPackExecution(playerId, execution, request);
                return;
            }

            finishPackExecution(player, craftingState, execution, result, request);
        } catch (RuntimeException exception) {
            cleanupPackExecution(playerId);
            throw exception;
        }
    }

    private void scheduleNextPackExecution(
            final UUID playerId,
            final PackExecution execution,
            final PackRequest request
    ) {
        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> continuePackExecution(playerId, execution, request),
                PACK_PASS_DELAY_TICKS
        );
    }

    private void finishPackExecution(
            final Player player,
            final CraftingRequirementState craftingState,
            final PackExecution execution,
            final PackResult finalResult,
            final PackRequest request
    ) {
        try {
            if (request.automatic()) {
                sendAutoPackFeedback(player, execution, finalResult);
                return;
            }

            if (!execution.hasSuccessfulConversions()) {
                if (finalResult.totalAdditionalSlotsNeeded() > 0) {
                    sendInventoryFullMessages(player, finalResult.inventoryFailures());
                    sendInventoryFullSummary(player, finalResult.totalAdditionalSlotsNeeded());
                    sendSkippedExcludedMaterialsMessage(player, finalResult.skippedMaterials());
                    return;
                }

                if (execution.blockedByCraftingRequirement) {
                    player.sendMessage(buildCraftingRequirementFailureMessage(craftingState));
                    sendSkippedExcludedMaterialsMessage(player, finalResult.skippedMaterials());
                    return;
                }

                if (!execution.hadValidAttempt) {
                    String messagePath = request.chestTarget()
                            ? "message.error.chest_nothing_to_pack"
                            : "message.error.nothing_to_pack";
                    String fallback = request.chestTarget()
                            ? "Chest does not have any valid materials to pack."
                            : "You do not have any valid materials to pack.";
                    String message = getMessage(
                            messagePath,
                            fallback
                    );
                    player.sendMessage(message);
                }

                sendSkippedExcludedMaterialsMessage(player, finalResult.skippedMaterials());
                return;
            }

            Map<Material, OriginSummary> originSummaries = execution.buildOriginSummaries();
            sendPackListMessages(player, originSummaries);

            int totalInputCount = countOriginSummaryInputs(originSummaries);
            int totalOutputCount = countOriginSummaryOutputs(originSummaries);
            String packedItems = formatMaterialTotals(buildCombinedOriginTotals(originSummaries));

            String message = buildPackSummaryMessage(totalInputCount, totalOutputCount, packedItems);

            player.sendMessage(message);

            if (execution.usedSmallRecipeBypass && execution.blockedByCraftingRequirement) {
                String hint = getMessage(
                        "message.info.more_available_at_crafting_table",
                        "More materials can be packed at a crafting table."
                );
                player.sendMessage(hint);
            }

            if (finalResult.totalAdditionalSlotsNeeded() > 0) {
                sendInventoryFullMessages(player, finalResult.inventoryFailures());
                sendInventoryFullSummary(player, finalResult.totalAdditionalSlotsNeeded());
            }

            sendSkippedExcludedMaterialsMessage(player, finalResult.skippedMaterials());
        } finally {
            cleanupPackExecution(player.getUniqueId());
        }
    }

    private void cleanupPackExecution(final UUID playerId) {
        packInProgress.remove(playerId);
        plugin.clearAllPackInputsExcludedNextRun(playerId);
    }

    private void queueAutoPack(final Player player, final AutoPackTrigger trigger) {
        if (player == null || !isAutoPackTriggerEnabled(trigger) || !canAutoPack(player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!pendingAutoPack.add(playerId)) {
            return;
        }

        long delayTicks = Math.max(1L, plugin.getConfig().getLong("auto_pack.delay_ticks", 2L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingAutoPack.remove(playerId);

            Player onlinePlayer = Bukkit.getPlayer(playerId);
            if (onlinePlayer == null || !onlinePlayer.isOnline() || !canAutoPack(onlinePlayer)) {
                return;
            }

            if (isAutoPackCoolingDown(playerId)) {
                return;
            }

            markAutoPackRun(playerId);
            executeAutoPack(onlinePlayer);
        }, delayTicks);
    }

    private boolean isAutoPackTriggerEnabled(final AutoPackTrigger trigger) {
        if (!plugin.getConfig().getBoolean("auto_pack.enabled", false)) {
            return false;
        }

        return plugin.getConfig().getBoolean("auto_pack.triggers." + trigger.configKey(), trigger.defaultEnabled());
    }

    private boolean canAutoPack(final Player player) {
        if (plugin.isSmartPackerCooldownModeEnabled()) {
            return false;
        }

        if (!plugin.getConfig().getBoolean("auto_pack.enabled", false)) {
            return false;
        }

        if (!plugin.isAutoPackEnabledForPlayer(player.getUniqueId())) {
            return false;
        }

        if (!player.hasPermission("smartpack.use") || !player.hasPermission("smartpack.auto")) {
            return false;
        }

        if (plugin.isSmartPackerItemModeEnabled()) {
            return !plugin.getConfig().getBoolean("auto_pack.smart_packer_item.require_item", true)
                    || plugin.hasSmartPackerItem(player.getInventory().getStorageContents());
        }

        return true;
    }

    private boolean shouldCheckNearbyCraftingTableAutoTrigger(
            final Player player,
            final AutoPackTrigger trigger
    ) {
        if (plugin.isSmartPackerItemModeEnabled()) {
            return false;
        }

        if (!isAutoPackTriggerEnabled(trigger) || !canAutoPack(player)) {
            return false;
        }

        CraftingTableMode mode = getConfiguredCraftingTableMode();
        if (mode == CraftingTableMode.NEARBY_ONLY) {
            return true;
        }

        if (mode == CraftingTableMode.INVENTORY_OR_NEARBY) {
            return !hasCraftingTableInInventory(player.getInventory());
        }

        return false;
    }

    private void enableAutoPackFromSmartPackerItem(final Player player) {
        if (!player.hasPermission("smartpack.use") || !player.hasPermission("smartpack.auto")) {
            player.sendMessage(getMessage(
                    "message.error.no_permission",
                    "You do not have permission to use this command."
            ));
            return;
        }

        if (!isAutoPackAvailableInCurrentMode()) {
            sendAutoPackUnavailable(player);
            return;
        }

        plugin.setAutoPackEnabledForPlayer(player.getUniqueId(), true);
        player.sendMessage(getMessage(
                "message.info.auto_pack_enabled",
                "Auto-pack enabled."
        ));
    }

    private boolean isAutoPackAvailableInCurrentMode() {
        if (plugin.isSmartPackerCooldownModeEnabled()) {
            return false;
        }

        if (!plugin.getConfig().getBoolean("auto_pack.enabled", false)) {
            return false;
        }

        return true;
    }

    private void sendAutoPackUnavailable(final Player player) {
        if (plugin.isSmartPackerCooldownModeEnabled()) {
            player.sendMessage(getMessage(
                    "message.info.auto_pack_cooldown_mode_disabled",
                    "Auto-pack is disabled while Smart Packer cooldown mode is enabled."
            ));
            return;
        }

        player.sendMessage(getMessage(
                "message.info.auto_pack_unavailable",
                "Auto-pack is disabled on this server."
        ));
    }

    private void sendAutoPackUnavailableForCommand(final Player player) {
        if (plugin.isSmartPackerCooldownModeEnabled()) {
            sendAutoPackUnavailable(player);
            return;
        }

        if (!plugin.getConfig().getBoolean("auto_pack.enabled", false)) {
            sendAutoPackUnavailable(player);
            return;
        }

        if (plugin.isSmartPackerItemModeEnabled()) {
            player.sendMessage(getMessage(
                    "message.info.auto_pack_smart_packer_item_required",
                    "Auto mode must be enabled through the Smart Packer item."
            ));
            return;
        }

        sendAutoPackUnavailable(player);
    }

    private void sendAutoPackToggleMessage(final Player player, final boolean enabled) {
        String messagePath = enabled
                ? "message.info.auto_pack_enabled"
                : "message.info.auto_pack_disabled";
        String fallback = enabled
                ? "Auto-pack enabled."
                : "Auto-pack disabled.";

        player.sendMessage(getMessage(messagePath, fallback));
    }

    private boolean isAutoPackCoolingDown(final UUID playerId) {
        long cooldownTicks = Math.max(0L, plugin.getConfig().getLong("auto_pack.cooldown_ticks", 10L));
        if (cooldownTicks <= 0L) {
            return false;
        }

        long currentTick = Bukkit.getCurrentTick();
        Long lastRunTick = lastAutoPackTicks.get(playerId);
        return lastRunTick != null && currentTick - lastRunTick < cooldownTicks;
    }

    private void markAutoPackRun(final UUID playerId) {
        lastAutoPackTicks.put(playerId, (long) Bukkit.getCurrentTick());
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();

            if ("exclude".startsWith(input)) {
                completions.add("exclude");
            }
            if (!plugin.isSmartPackerItemModeEnabled()
                    && isChestPackCommandEnabled()
                    && sender.hasPermission("smartpack.chest")
                    && "chest".startsWith(input)) {
                completions.add("chest");
            }
            if (!plugin.isSmartPackerItemModeEnabled()
                    && sender.hasPermission("smartpack.auto")
                    && "auto".startsWith(input)) {
                completions.add("auto");
            }
            if (sender.hasPermission("smartpack.reload") && "reload".startsWith(input)) {
                completions.add("reload");
            }

            return completions;
        }

        return Collections.emptyList();
    }

    private CraftingRequirementState getCraftingRequirementState(final Player player) {
        if (plugin.isSmartPackerItemModeEnabled()) {
            return new CraftingRequirementState(
                    true,
                    "",
                    CraftingTableMode.DISABLED,
                    0,
                    false,
                    false,
                    false,
                    0
            );
        }

        String rawMode = plugin.getConfig().getString("requirements.crafting_table_mode", "DISABLED");
        int range = getCraftingTableRange();

        CraftingTableMode mode = CraftingTableMode.fromString(rawMode);
        if (mode == null) {
            plugin.getLogger().warning("Invalid requirements.crafting_table_mode value: " + rawMode);

            String message = getMessage(
                    "message.error.invalid_crafting_table_mode",
                    "This plugin is misconfigured: invalid crafting table mode '[mode]'. Please contact an admin."
            ).replace("[mode]", String.valueOf(rawMode));

            return new CraftingRequirementState(
                    false,
                    message,
                    CraftingTableMode.DISABLED,
                    range,
                    false,
                    false,
                    true,
                    4
            );
        }

        boolean hasInventoryTable = usesInventoryCraftingTableRequirement(mode)
                && hasCraftingTableInInventory(player.getInventory());
        boolean hasNearbyTable = switch (mode) {
            case NEARBY_ONLY -> isNearCraftingTable(player, range);
            case INVENTORY_OR_NEARBY -> !hasInventoryTable && isNearCraftingTable(player, range);
            case DISABLED, INVENTORY_ONLY -> false;
        };

        boolean bypassForSmallRecipes = plugin.getConfig().getBoolean(
                "requirements.bypass_crafting_table_for_small_recipes",
                true
        );
        int bypassMaxRatioIn = Math.max(0, plugin.getConfig().getInt(
                "requirements.small_recipe_bypass_max_ratio_in",
                4
        ));

        return new CraftingRequirementState(
                true,
                "",
                mode,
                range,
                hasInventoryTable,
                hasNearbyTable,
                bypassForSmallRecipes,
                bypassMaxRatioIn
        );
    }

    private CraftingTableMode getConfiguredCraftingTableMode() {
        String rawMode = plugin.getConfig().getString("requirements.crafting_table_mode", "DISABLED");
        return CraftingTableMode.fromString(rawMode);
    }

    private int getCraftingTableRange() {
        return Math.max(0, plugin.getConfig().getInt("requirements.crafting_table_range", 5));
    }

    private boolean usesInventoryCraftingTableRequirement(final CraftingTableMode mode) {
        return mode == CraftingTableMode.INVENTORY_ONLY || mode == CraftingTableMode.INVENTORY_OR_NEARBY;
    }

    private boolean hasCraftingTableInInventory(final PlayerInventory inventory) {
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            if (item.getType() == Material.CRAFTING_TABLE) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearCraftingTable(final Player player, final int range) {
        return isNearCraftingTable(player.getLocation(), range);
    }

    private boolean isNearCraftingTable(final Location location, final int range) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        World world = location.getWorld();

        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();

        int maxDistanceSquared = range * range;

        for (int x = baseX - range; x <= baseX + range; x++) {
            for (int y = baseY - range; y <= baseY + range; y++) {
                for (int z = baseZ - range; z <= baseZ + range; z++) {
                    int dx = x - baseX;
                    int dy = y - baseY;
                    int dz = z - baseZ;
                    int distanceSquared = (dx * dx) + (dy * dy) + (dz * dz);

                    if (distanceSquared > maxDistanceSquared) {
                        continue;
                    }

                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.CRAFTING_TABLE) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean hasChangedBlock(final Location from, final Location to) {
        if (from == null || to == null) {
            return true;
        }

        if (!Objects.equals(from.getWorld(), to.getWorld())) {
            return true;
        }

        return from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    private boolean isCraftingRequirementSatisfiedForRecipe(
            final CraftingRequirementState craftingState,
            final int ratioIn
    ) {
        if (craftingState.mode() == CraftingTableMode.DISABLED) {
            return true;
        }

        if (craftingState.bypassForSmallRecipes() && ratioIn <= craftingState.bypassMaxRatioIn()) {
            return true;
        }

        return switch (craftingState.mode()) {
            case DISABLED -> true;
            case INVENTORY_ONLY -> craftingState.hasInventoryTable();
            case NEARBY_ONLY -> craftingState.hasNearbyTable();
            case INVENTORY_OR_NEARBY -> craftingState.hasInventoryTable() || craftingState.hasNearbyTable();
        };
    }

    private String buildCraftingRequirementFailureMessage(final CraftingRequirementState craftingState) {
        return switch (craftingState.mode()) {
            case DISABLED -> "";
            case INVENTORY_ONLY -> getMessage(
                    "message.error.crafting_table_required_inventory",
                    "You must have a crafting table in your inventory to use this command."
            );
            case NEARBY_ONLY -> getMessage(
                    "message.error.crafting_table_required_nearby",
                    "You must be within [range] blocks of a crafting table to use this command."
            ).replace("[range]", String.valueOf(craftingState.range()));
            case INVENTORY_OR_NEARBY -> getMessage(
                    "message.error.crafting_table_required_inventory_or_nearby",
                    "You need a crafting table to use this command. Put one in your inventory or stand within [range] blocks of one."
            ).replace("[range]", String.valueOf(craftingState.range()));
        };
    }

    private PackResult pack(
            final Player player,
            final Inventory inventory,
            final CraftingRequirementState craftingState,
            final PackExecution execution,
            final PackRequest request
    ) {
        ConfigurationSection packSection = plugin.getConfig().getConfigurationSection("pack");
        if (packSection == null) {
            plugin.getLogger().warning("Missing 'pack' section in config.yml");
            return new PackResult(
                    0,
                    false,
                    false,
                    false,
                    false,
                    0,
                    Collections.emptyMap(),
                    Collections.emptyList()
            );
        }

        execution.syncTrackedInventory(
                countContentsByMaterial(inventory.getStorageContents()),
                collectTrackableInputMaterials(player.getUniqueId(), packSection)
        );

        int totalProduced = 0;
        boolean hadValidAttempt = false;
        boolean blockedByCraftingRequirement = false;
        boolean usedSmallRecipeBypass = false;
        boolean inventoryChangedDuringRun = false;

        while (true) {
            PassResult passResult = runPackPass(
                    player,
                    inventory,
                    packSection,
                    craftingState,
                    execution,
                    request
            );

            totalProduced += passResult.produced();
            hadValidAttempt |= passResult.hadValidAttempt();
            blockedByCraftingRequirement |= passResult.blockedByCraftingRequirement();
            usedSmallRecipeBypass |= passResult.usedSmallRecipeBypass();
            inventoryChangedDuringRun |= passResult.inventoryChangedDuringPass();

            if (!passResult.madeAnyChange()) {
                break;
            }
        }

        FailureSummary finalFailureSummary = evaluateRemainingInventoryFailures(
                player,
                player.getUniqueId(),
                inventory,
                packSection,
                craftingState,
                request.includeNearbyPickups()
        );
        List<SkippedMaterial> skippedMaterials = collectSkippedExcludedMaterialsForRun(
                player.getUniqueId(),
                inventory,
                packSection
        );

        return new PackResult(
                totalProduced,
                hadValidAttempt,
                blockedByCraftingRequirement,
                usedSmallRecipeBypass,
                inventoryChangedDuringRun,
                finalFailureSummary.totalAdditionalSlotsNeeded(),
                finalFailureSummary.failures(),
                skippedMaterials
        );
    }

    private PassResult runPackPass(
            final Player player,
            final Inventory inventory,
            final ConfigurationSection packSection,
            final CraftingRequirementState craftingState,
            final PackExecution execution,
            final PackRequest request
    ) {
        Map<Material, Integer> itemCounts = countContentsByMaterial(inventory.getStorageContents());

        int passProduced = 0;
        boolean hadValidAttempt = false;
        boolean madeAnyChange = false;
        boolean blockedByCraftingRequirement = false;
        boolean usedSmallRecipeBypass = false;
        boolean inventoryChangedDuringPass = false;

        for (String key : packSection.getKeys(false)) {
            ConfigurationSection rule = packSection.getConfigurationSection(key);
            if (rule == null) {
                plugin.getLogger().warning("Invalid pack rule section: " + key);
                continue;
            }

            Material input = Material.matchMaterial(key);
            if (input == null) {
                plugin.getLogger().warning("Invalid input material in config: " + key);
                continue;
            }

            if (plugin.isPackInputDisabled(input)
                    || plugin.isPackInputExcludedForRun(player.getUniqueId(), input)) {
                continue;
            }

            String outputName = rule.getString("output");
            Material output = outputName == null ? null : Material.matchMaterial(outputName);

            int ratioIn = rule.getInt("ratio_in");
            int ratioOut = rule.getInt("ratio_out");

            if (output == null) {
                plugin.getLogger().warning("Invalid output material for " + key + ": " + outputName);
                continue;
            }

            if (ratioIn <= 0 || ratioOut <= 0) {
                String message = getMessage(
                        "message.error.ratio_zero",
                        "Attention: the conversion ratio of [item1] is invalid."
                ).replace("[item1]", getItemName(input));

                if (!request.automatic()) {
                    player.sendMessage(message);
                }

                plugin.getLogger().warning("Invalid ratio for " + input + " -> " + output
                        + " (ratio_in=" + ratioIn + ", ratio_out=" + ratioOut + ")");
                continue;
            }

            int available = itemCounts.getOrDefault(input, 0);
            if (available < ratioIn) {
                continue;
            }

            hadValidAttempt = true;

            boolean bypassed = craftingState.bypassForSmallRecipes()
                    && ratioIn <= craftingState.bypassMaxRatioIn();

            if (!isCraftingRequirementSatisfiedForRecipe(craftingState, ratioIn)) {
                blockedByCraftingRequirement = true;
                continue;
            }

            AttemptResult attempt = tryPack(
                    player,
                    inventory,
                    input,
                    output,
                    ratioIn,
                    ratioOut,
                    false
            );

            if (attempt.inventoryChanged()) {
                inventoryChangedDuringPass = true;
                itemCounts = countContentsByMaterial(inventory.getStorageContents());
                continue;
            }

            if (attempt.produced() > 0) {
                passProduced += attempt.produced();
                madeAnyChange = true;

                if (bypassed) {
                    usedSmallRecipeBypass = true;
                }

                execution.recordSuccessfulConversion(
                        input,
                        output,
                        ratioIn,
                        ratioOut,
                        attempt.inputConsumed(),
                        attempt.produced()
                );
                itemCounts = countContentsByMaterial(inventory.getStorageContents());
            }
        }

        return new PassResult(
                passProduced,
                hadValidAttempt,
                madeAnyChange,
                blockedByCraftingRequirement,
                usedSmallRecipeBypass,
                inventoryChangedDuringPass
        );
    }

    private FailureSummary evaluateRemainingInventoryFailures(
            final Player player,
            final UUID playerId,
            final Inventory inventory,
            final ConfigurationSection packSection,
            final CraftingRequirementState craftingState,
            final boolean includeNearbyPickups
    ) {
        Map<Material, Integer> itemCounts = countContentsByMaterial(inventory.getStorageContents());

        Map<Material, InventoryFailure> failures = new EnumMap<>(Material.class);

        for (String key : packSection.getKeys(false)) {
            ConfigurationSection rule = packSection.getConfigurationSection(key);
            if (rule == null) {
                continue;
            }

            Material input = Material.matchMaterial(key);
            if (input == null
                    || plugin.isPackInputDisabled(input)
                    || plugin.isPackInputExcludedForRun(playerId, input)) {
                continue;
            }

            String outputName = rule.getString("output");
            Material output = outputName == null ? null : Material.matchMaterial(outputName);
            int ratioIn = rule.getInt("ratio_in");
            int ratioOut = rule.getInt("ratio_out");

            if (output == null || ratioIn <= 0 || ratioOut <= 0) {
                continue;
            }

            int available = itemCounts.getOrDefault(input, 0);
            if (available < ratioIn) {
                continue;
            }

            if (!isCraftingRequirementSatisfiedForRecipe(craftingState, ratioIn)) {
                continue;
            }

            AttemptResult attempt = tryPack(
                    null,
                    inventory,
                    input,
                    output,
                    ratioIn,
                    ratioOut,
                    false
            );

            if (attempt.additionalSlotsNeeded() > 0) {
                int crafts = available / ratioIn;
                int toProduce = crafts * ratioOut;
                int leftoverInput = available - (crafts * ratioIn);

                InventoryFailure failure = new InventoryFailure(
                        available,
                        input,
                        toProduce,
                        output,
                        leftoverInput,
                        attempt.additionalSlotsNeeded()
                );

                failures.put(input, failure);
            }
        }

        int totalAdditionalSlotsNeeded = failures.isEmpty()
                ? 0
                : estimateAdditionalSlotsNeededForPackedState(
                        player,
                        playerId,
                        cloneContents(inventory.getStorageContents()),
                        packSection,
                        craftingState,
                        includeNearbyPickups
                );

        return new FailureSummary(totalAdditionalSlotsNeeded, failures);
    }

    private List<SkippedMaterial> collectSkippedExcludedMaterialsForRun(
            final UUID playerId,
            final Inventory inventory,
            final ConfigurationSection packSection
    ) {
        Map<Material, Integer> itemCounts = countContentsByMaterial(inventory.getStorageContents());

        List<SkippedMaterial> skippedMaterials = new ArrayList<>();
        for (String key : packSection.getKeys(false)) {
            Material input = Material.matchMaterial(key);
            if (input == null
                    || plugin.isPackInputDisabled(input)
                    || !plugin.isPackInputExcludedForRun(playerId, input)) {
                continue;
            }

            int available = itemCounts.getOrDefault(input, 0);
            if (available <= 0) {
                continue;
            }

            skippedMaterials.add(new SkippedMaterial(available, input));
        }

        skippedMaterials.sort(Comparator.comparing(skippedMaterial -> getItemName(skippedMaterial.material())));
        return skippedMaterials;
    }

    private AttemptResult tryPack(
            final Player player,
            final Inventory inventory,
            final Material input,
            final Material output,
            final int ratioIn,
            final int ratioOut,
            final boolean sendInventoryFullMessages
    ) {
        ItemStack[] liveContents = cloneContents(inventory.getStorageContents());
        int availableInput = countMaterial(liveContents, input);
        int crafts = availableInput / ratioIn;
        if (crafts <= 0) {
            return new AttemptResult(0, 0, 0, 0, false);
        }

        int toConsume = crafts * ratioIn;
        int toProduce = crafts * ratioOut;
        int leftoverInput = availableInput - toConsume;

        ItemStack[] simulated = cloneContents(liveContents);

        boolean removed = removeFromContents(simulated, input, toConsume);
        if (!removed) {
            plugin.getLogger().warning("Failed to remove expected input items for " + input);
            return new AttemptResult(0, 0, 0, 0, false);
        }

        Map<Integer, ItemStack> leftovers = addToContents(simulated, new ItemStack(output, toProduce));
        if (!leftovers.isEmpty()) {
            int additionalSlotsNeeded = calculateAdditionalSlotsNeeded(leftovers);

            if (sendInventoryFullMessages && player != null && plugin.getConfig().getBoolean("display.list")) {
                String fullInput = formatItemAmount(availableInput, input);
                String result = formatPackResult(toProduce, output, leftoverInput, input);

                String message = getMessage(
                        "message.error.inventory_full",
                        "Inventory full: [item1] → [item2]"
                ).replace("[item1]", fullInput)
                 .replace("[item2]", result);

                message = normalizeDisplayArrows(message);

                player.sendMessage(message);
            }
            return new AttemptResult(0, 0, 0, additionalSlotsNeeded, false);
        }

        if (!storageContentsMatch(liveContents, inventory.getStorageContents())) {
            return new AttemptResult(0, 0, 0, 0, true);
        }

        inventory.setStorageContents(simulated);

        return new AttemptResult(toProduce, toConsume, leftoverInput, 0, false);
    }

    private void sendInventoryFullMessages(final Player player, final Map<Material, InventoryFailure> failures) {
        if (!plugin.getConfig().getBoolean("display.list")) {
            return;
        }

        for (InventoryFailure failure : failures.values()) {
            String fullInput = formatItemAmount(failure.availableInput(), failure.inputMaterial());
            String result = formatPackResult(
                    failure.producedAmount(),
                    failure.producedMaterial(),
                    failure.leftoverInput(),
                    failure.inputMaterial()
            );

            String message = getMessage(
                    "message.error.inventory_full",
                    "Inventory full: [item1] → [item2]"
            ).replace("[item1]", fullInput)
             .replace("[item2]", result);

            message = normalizeDisplayArrows(message);

            player.sendMessage(message);
        }
    }

    private void sendPackListMessages(
            final Player player,
            final Map<Material, OriginSummary> originSummaries
    ) {
        if (!plugin.getConfig().getBoolean("display.list") || originSummaries.isEmpty()) {
            return;
        }

        List<Map.Entry<Material, OriginSummary>> entries = new ArrayList<>(originSummaries.entrySet());
        entries.sort(Comparator
                .<Map.Entry<Material, OriginSummary>>comparingInt(entry -> entry.getValue().initialInputAmount())
                .reversed()
                .thenComparing(entry -> getItemName(entry.getKey())));

        for (Map.Entry<Material, OriginSummary> entry : entries) {
            Material origin = entry.getKey();
            OriginSummary summary = entry.getValue();

            String item1 = formatItemAmount(summary.initialInputAmount(), origin);
            String item2 = formatMaterialTotals(summary.finalMaterialTotals());

            String message = getMessage(
                    "message.pack.item",
                    "[item1] → [item2]"
            ).replace("[item1]", item1)
             .replace("[item2]", item2);

            message = normalizeDisplayArrows(message);

            player.sendMessage(message);
        }
    }

    private int countOriginSummaryInputs(final Map<Material, OriginSummary> originSummaries) {
        int total = 0;
        for (OriginSummary summary : originSummaries.values()) {
            total += summary.initialInputAmount();
        }
        return total;
    }

    private int countOriginSummaryOutputs(final Map<Material, OriginSummary> originSummaries) {
        int total = 0;
        for (OriginSummary summary : originSummaries.values()) {
            total += summary.totalFinalItems();
        }
        return total;
    }

    private Map<Material, Integer> buildCombinedOriginTotals(final Map<Material, OriginSummary> originSummaries) {
        Map<Material, Integer> combinedTotals = new EnumMap<>(Material.class);
        for (OriginSummary summary : originSummaries.values()) {
            for (Map.Entry<Material, Integer> entry : summary.finalMaterialTotals().entrySet()) {
                combinedTotals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        return combinedTotals;
    }

    private String buildPackSummaryMessage(
            final int totalInputCount,
            final int totalOutputCount,
            final String packedItems
    ) {
        String message = getMessage("message.pack.summary", "Packed [input] items down to [output].");
        if (message.contains("[items]") && !message.contains("[input]") && !message.contains("[output]")) {
            message = "Packed [input] items down to [output].";
        }

        return message
                .replace("[input]", String.valueOf(totalInputCount))
                .replace("[output]", String.valueOf(totalOutputCount))
                .replace("[items]", packedItems);
    }

    private void sendInventoryFullSummary(final Player player, final int totalAdditionalSlotsNeeded) {
        String summary = getMessage(
                "message.error.inventory_full_summary",
                "Inventory full summary: [slots] additional slot(s) would have been needed in total."
        ).replace("[slots]", String.valueOf(totalAdditionalSlotsNeeded));

        player.sendMessage(summary);
    }

    private void sendSkippedExcludedMaterialsMessage(
            final Player player,
            final List<SkippedMaterial> skippedMaterials
    ) {
        if (skippedMaterials.isEmpty()) {
            return;
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (SkippedMaterial skippedMaterial : skippedMaterials) {
            joiner.add(formatItemAmount(skippedMaterial.amount(), skippedMaterial.material()));
        }

        String message = getMessage(
                "message.info.skipped_excluded_materials",
                "Skipped excluded materials in your inventory: [items]."
        ).replace("[items]", joiner.toString());

        player.sendMessage(message);
    }

    private void sendAutoPackFeedback(
            final Player player,
            final PackExecution execution,
            final PackResult finalResult
    ) {
        boolean inventoryFull = finalResult.totalAdditionalSlotsNeeded() > 0;
        if (inventoryFull && plugin.getConfig().getBoolean(
                "auto_pack.feedback.inventory_full_actionbar",
                true
        )) {
            if (shouldSendAutoInventoryFullMessage(player.getUniqueId())) {
                String message = getMessage(
                        "message.auto_pack.inventory_full_actionbar",
                        "§6Pack blocked: need [slots] more slot(s)."
                ).replace("[slots]", String.valueOf(finalResult.totalAdditionalSlotsNeeded()));

                sendActionBar(player, message);
            }
            return;
        }

        if (!execution.hasSuccessfulConversions()
                || !plugin.getConfig().getBoolean("auto_pack.feedback.success_actionbar", true)) {
            return;
        }

        Map<Material, OriginSummary> originSummaries = execution.buildOriginSummaries();
        int totalInputCount = countOriginSummaryInputs(originSummaries);
        int totalOutputCount = countOriginSummaryOutputs(originSummaries);
        String packedItems = formatMaterialTotals(buildCombinedOriginTotals(originSummaries));

        String message = getMessage(
                "message.auto_pack.actionbar",
                "§aPacked into [items]."
        ).replace("[input]", String.valueOf(totalInputCount))
         .replace("[output]", String.valueOf(totalOutputCount))
         .replace("[items]", packedItems);

        sendActionBar(player, message);
    }

    private boolean shouldSendAutoInventoryFullMessage(final UUID playerId) {
        long cooldownTicks = Math.max(
                0L,
                plugin.getConfig().getLong("auto_pack.feedback.inventory_full_cooldown_ticks", 100L)
        );
        if (cooldownTicks <= 0L) {
            return true;
        }

        long currentTick = Bukkit.getCurrentTick();
        Long lastMessageTick = lastAutoInventoryFullMessageTicks.get(playerId);
        if (lastMessageTick != null && currentTick - lastMessageTick < cooldownTicks) {
            return false;
        }

        lastAutoInventoryFullMessageTicks.put(playerId, currentTick);
        return true;
    }

    private void sendActionBar(final Player player, final String message) {
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(normalizeDisplayArrows(message)));
    }

    private void sendSmartPackerCooldownReadyMessage(final Player player) {
        String message = getMessage(
                "message.info.smart_packer_cooldown_ready",
                "§aSmart Packer ready."
        );

        sendActionBar(player, message);
    }

    private void sendExclusionChangesAppliedMessage(final Player player) {
        player.sendMessage(Component.text("Exclusion changes applied.", NamedTextColor.GREEN));
    }

    private void persistPersistentExclusionsIfChanged(
            final Player player,
            final ExcludeMenuSession session
    ) {
        Set<Material> currentPersistent = plugin.getPackInputExcludedPersistentSnapshot(player.getUniqueId());
        if (currentPersistent.equals(session.initialPersistentExclusions())) {
            return;
        }

        plugin.savePackInputExcludedPersistentAsync(player.getUniqueId());
    }

    private int calculateAdditionalSlotsNeeded(final Map<Integer, ItemStack> leftovers) {
        int additionalSlotsNeeded = 0;

        for (ItemStack leftover : leftovers.values()) {
            if (leftover == null || leftover.getType() == Material.AIR || leftover.getAmount() <= 0) {
                continue;
            }

            int maxStackSize = Math.max(1, leftover.getMaxStackSize());
            additionalSlotsNeeded += (int) Math.ceil((double) leftover.getAmount() / maxStackSize);
        }

        return additionalSlotsNeeded;
    }

    private int estimateAdditionalSlotsNeededForPackedState(
            final Player player,
            final UUID playerId,
            final ItemStack[] simulatedContents,
            final ConfigurationSection packSection,
            final CraftingRequirementState craftingState,
            final boolean includeNearbyPickups
    ) {
        if (simulatedContents.length == 0) {
            return 0;
        }

        Map<Material, Long> materialCounts = countContentsByMaterialLong(simulatedContents);
        if (includeNearbyPickups) {
            mergeNearbyPickupMaterialCounts(player, materialCounts);
        }
        simulatePackedMaterialCounts(playerId, materialCounts, packSection, craftingState);

        long totalSlotsNeeded = calculateTotalSlotsNeeded(materialCounts);
        long additionalSlotsNeeded = totalSlotsNeeded - simulatedContents.length;
        if (additionalSlotsNeeded <= 0) {
            return 0;
        }

        return additionalSlotsNeeded >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) additionalSlotsNeeded;
    }

    private Map<Material, Integer> countContentsByMaterial(final ItemStack[] storage) {
        Map<Material, Integer> itemCounts = new EnumMap<>(Material.class);
        for (ItemStack item : storage) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            itemCounts.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return itemCounts;
    }

    private Map<Material, Long> countContentsByMaterialLong(final ItemStack[] storage) {
        Map<Material, Long> itemCounts = new EnumMap<>(Material.class);
        for (ItemStack item : storage) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                continue;
            }

            itemCounts.merge(item.getType(), (long) item.getAmount(), Long::sum);
        }
        return itemCounts;
    }

    private void mergeNearbyPickupMaterialCounts(
            final Player player,
            final Map<Material, Long> materialCounts
    ) {
        if (player == null || materialCounts == null) {
            return;
        }

        if (!plugin.getConfig().getBoolean("inventory_full.include_nearby_pickups", true)) {
            return;
        }

        double pickupRadius = Math.max(0.0D, plugin.getConfig().getDouble("inventory_full.pickup_radius", 2.0D));
        if (pickupRadius <= 0.0D) {
            return;
        }

        Location playerLocation = player.getLocation();
        double maxDistanceSquared = pickupRadius * pickupRadius;

        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(pickupRadius, pickupRadius, pickupRadius)) {
            if (!(entity instanceof Item itemEntity)) {
                continue;
            }

            if (!itemEntity.isValid() || itemEntity.isDead() || itemEntity.getPickupDelay() > 0) {
                continue;
            }

            if (itemEntity.getLocation().distanceSquared(playerLocation) > maxDistanceSquared) {
                continue;
            }

            ItemStack nearbyStack = itemEntity.getItemStack();
            if (nearbyStack == null || nearbyStack.getType() == Material.AIR || nearbyStack.getAmount() <= 0) {
                continue;
            }

            materialCounts.merge(nearbyStack.getType(), (long) nearbyStack.getAmount(), Long::sum);
        }
    }

    private void simulatePackedMaterialCounts(
            final UUID playerId,
            final Map<Material, Long> materialCounts,
            final ConfigurationSection packSection,
            final CraftingRequirementState craftingState
    ) {
        if (materialCounts.isEmpty()) {
            return;
        }

        List<PackRule> rules = getSimulationPackRules(playerId, packSection, craftingState);
        if (rules.isEmpty()) {
            return;
        }

        int maxPasses = Math.max(1, rules.size() * rules.size());
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean changed = false;

            for (PackRule rule : rules) {
                long available = materialCounts.getOrDefault(rule.input(), 0L);
                long crafts = available / rule.ratioIn();
                if (crafts <= 0) {
                    continue;
                }

                long consumed = crafts * rule.ratioIn();
                long produced = crafts * rule.ratioOut();

                long remainingInput = available - consumed;
                if (remainingInput > 0) {
                    materialCounts.put(rule.input(), remainingInput);
                } else {
                    materialCounts.remove(rule.input());
                }

                materialCounts.merge(rule.output(), produced, Long::sum);
                changed = true;
            }

            if (!changed) {
                return;
            }
        }
    }

    private List<PackRule> getSimulationPackRules(
            final UUID playerId,
            final ConfigurationSection packSection,
            final CraftingRequirementState craftingState
    ) {
        List<PackRule> rules = new ArrayList<>();

        for (String key : packSection.getKeys(false)) {
            ConfigurationSection ruleSection = packSection.getConfigurationSection(key);
            if (ruleSection == null) {
                continue;
            }

            Material input = Material.matchMaterial(key);
            if (input == null
                    || plugin.isPackInputDisabled(input)
                    || plugin.isPackInputExcludedForRun(playerId, input)) {
                continue;
            }

            String outputName = ruleSection.getString("output");
            Material output = outputName == null ? null : Material.matchMaterial(outputName);
            int ratioIn = ruleSection.getInt("ratio_in");
            int ratioOut = ruleSection.getInt("ratio_out");

            if (output == null || ratioIn <= 0 || ratioOut <= 0) {
                continue;
            }

            if (!isCraftingRequirementSatisfiedForRecipe(craftingState, ratioIn)) {
                continue;
            }

            rules.add(new PackRule(input, output, ratioIn, ratioOut));
        }

        rules.sort(Comparator.comparingInt(rule -> getPackDepth(rule.input())));
        return rules;
    }

    private long calculateTotalSlotsNeeded(final Map<Material, Long> materialCounts) {
        long totalSlotsNeeded = 0;

        for (Map.Entry<Material, Long> entry : materialCounts.entrySet()) {
            Material material = entry.getKey();
            long amount = entry.getValue();
            if (material == null || amount <= 0) {
                continue;
            }

            int maxStackSize = Math.max(1, material.getMaxStackSize());
            totalSlotsNeeded += (amount + maxStackSize - 1L) / maxStackSize;
        }

        return totalSlotsNeeded;
    }

    private Set<Material> collectTrackableInputMaterials(
            final UUID playerId,
            final ConfigurationSection packSection
    ) {
        Set<Material> trackableInputs = new HashSet<>();
        for (String key : packSection.getKeys(false)) {
            Material input = Material.matchMaterial(key);
            if (input == null
                    || plugin.isPackInputDisabled(input)
                    || plugin.isPackInputExcludedForRun(playerId, input)) {
                continue;
            }

            trackableInputs.add(input);
        }

        return trackableInputs;
    }

    private int countMaterial(final ItemStack[] contents, final Material material) {
        int amount = 0;

        for (ItemStack stack : contents) {
            if (stack == null || stack.getType() != material) {
                continue;
            }

            amount += stack.getAmount();
        }

        return amount;
    }

    private ItemStack[] cloneContents(final ItemStack[] original) {
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i] == null ? null : original[i].clone();
        }
        return copy;
    }

    private boolean storageContentsMatch(final ItemStack[] expected, final ItemStack[] actual) {
        if (expected.length != actual.length) {
            return false;
        }

        for (int i = 0; i < expected.length; i++) {
            if (!Objects.equals(expected[i], actual[i])) {
                return false;
            }
        }

        return true;
    }

    private boolean removeFromContents(final ItemStack[] contents, final Material material, int amount) {
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }

            int stackAmount = stack.getAmount();
            if (stackAmount <= amount) {
                amount -= stackAmount;
                contents[i] = null;
            } else {
                stack.setAmount(stackAmount - amount);
                amount = 0;
            }
        }

        return amount == 0;
    }

    private Map<Integer, ItemStack> addToContents(final ItemStack[] contents, final ItemStack toAdd) {
        Map<Integer, ItemStack> leftovers = new HashMap<>();

        int remaining = toAdd.getAmount();
        Material material = toAdd.getType();
        int maxStack = toAdd.getMaxStackSize();

        for (ItemStack stack : contents) {
            if (remaining <= 0) {
                break;
            }
            if (stack == null || stack.getType() != material) {
                continue;
            }
            if (stack.getAmount() >= maxStack) {
                continue;
            }

            int space = maxStack - stack.getAmount();
            int moved = Math.min(space, remaining);
            stack.setAmount(stack.getAmount() + moved);
            remaining -= moved;
        }

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            if (contents[i] != null) {
                continue;
            }

            int moved = Math.min(maxStack, remaining);
            contents[i] = new ItemStack(material, moved);
            remaining -= moved;
        }

        if (remaining > 0) {
            leftovers.put(0, new ItemStack(material, remaining));
        }

        return leftovers;
    }

    private void openExcludeMenu(final Player player, final int page) {
        List<Material> configuredInputs = getConfiguredInputMaterials();
        if (configuredInputs.isEmpty()) {
            player.sendMessage(Component.text("No pack inputs are currently configured.", NamedTextColor.YELLOW));
            return;
        }

        int totalPages = getExcludeMenuPageCount(configuredInputs.size());
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        excludeMenuSessions.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new ExcludeMenuSession(
                        plugin.getPackInputExcludedPersistentSnapshot(player.getUniqueId()),
                        plugin.getPackInputExcludedNextRunSnapshot(player.getUniqueId())
                )
        );

        ExcludeMenuHolder holder = new ExcludeMenuHolder(player.getUniqueId(), safePage);
        Inventory inventory = Bukkit.createInventory(
                holder,
                EXCLUDE_MENU_SIZE,
                Component.text("SmartPack Exclusions", NamedTextColor.DARK_GREEN)
        );
        holder.setInventory(inventory);
        renderExcludeMenu(inventory, player, holder, configuredInputs, safePage, totalPages);
        player.openInventory(inventory);
    }

    private void handleExcludeMenuClick(final InventoryClickEvent event, final ExcludeMenuHolder holder) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        List<Material> configuredInputs = getConfiguredInputMaterials();
        int totalPages = getExcludeMenuPageCount(configuredInputs.size());

        if (rawSlot < EXCLUDE_MENU_CONTENT_SIZE) {
            int materialIndex = (holder.page() * EXCLUDE_MENU_CONTENT_SIZE) + rawSlot;
            if (materialIndex >= configuredInputs.size()) {
                return;
            }

            Material material = configuredInputs.get(materialIndex);
            if (event.getClick().isRightClick()) {
                boolean excluded = plugin.togglePackInputExcluded(player.getUniqueId(), material);
                plugin.clearPackInputExcludedNextRun(player.getUniqueId(), material);

                refreshExcludeMenuItem(event, rawSlot, player, material, holder.page(), totalPages);

                if (excluded) {
                    player.sendMessage(
                            Component.text(getItemName(material), NamedTextColor.RED)
                                    .append(Component.text(" is now persistently excluded from packing.", NamedTextColor.GRAY))
                    );
                } else {
                    player.sendMessage(
                            Component.text(getItemName(material), NamedTextColor.GREEN)
                                    .append(Component.text(" is no longer persistently excluded.", NamedTextColor.GRAY))
                    );
                }
                return;
            }

            if (!event.getClick().isLeftClick()) {
                return;
            }

            if (plugin.isPackInputExcluded(player.getUniqueId(), material)) {
                player.sendMessage(
                        Component.text(getItemName(material), NamedTextColor.RED)
                                .append(Component.text(" is already persistently excluded. Right-click it to re-include.", NamedTextColor.GRAY))
                );
                return;
            }

            boolean excluded = plugin.togglePackInputExcludedNextRun(player.getUniqueId(), material);
            refreshExcludeMenuItem(event, rawSlot, player, material, holder.page(), totalPages);

            if (excluded) {
                player.sendMessage(
                        Component.text(getItemName(material), NamedTextColor.YELLOW)
                                .append(Component.text(" will be skipped on the next pack run only.", NamedTextColor.GRAY))
                );
            } else {
                player.sendMessage(
                        Component.text(getItemName(material), NamedTextColor.GREEN)
                                .append(Component.text(" will no longer be skipped on the next pack run.", NamedTextColor.GRAY))
                );
            }
            return;
        }

        if (rawSlot == EXCLUDE_MENU_CANCEL_SLOT) {
            ExcludeMenuSession session = excludeMenuSessions.get(player.getUniqueId());
            if (session != null) {
                boolean restored = plugin.replacePackInputExcludedPersistent(
                        player.getUniqueId(),
                        session.initialPersistentExclusions()
                );
                plugin.replacePackInputExcludedNextRun(
                        player.getUniqueId(),
                        session.initialNextRunExclusions()
                );

                if (restored) {
                    player.sendMessage(Component.text("Exclusion changes canceled.", NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text(
                            "Could not fully restore exclusion changes. Check the server log.",
                            NamedTextColor.RED
                    ));
                }
            }

            excludeMenuSessions.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }

        if (rawSlot == EXCLUDE_MENU_APPLY_SLOT) {
            ExcludeMenuSession session = excludeMenuSessions.remove(player.getUniqueId());
            if (session != null) {
                persistPersistentExclusionsIfChanged(player, session);
            }
            sendExclusionChangesAppliedMessage(player);
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }

        if (rawSlot == EXCLUDE_MENU_PREVIOUS_SLOT && holder.page() > 0) {
            renderCurrentExcludeMenu(event.getView().getTopInventory(), player, holder, holder.page() - 1, totalPages);
            return;
        }

        if (rawSlot == EXCLUDE_MENU_NEXT_SLOT && holder.page() + 1 < totalPages) {
            renderCurrentExcludeMenu(event.getView().getTopInventory(), player, holder, holder.page() + 1, totalPages);
        }
    }

    private void fillExcludeMenuControls(final Inventory inventory, final int page, final int totalPages) {
        for (int slot = EXCLUDE_MENU_CONTENT_SIZE; slot < EXCLUDE_MENU_SIZE; slot++) {
            inventory.setItem(slot, createMenuFillerItem());
        }

        inventory.setItem(EXCLUDE_MENU_INFO_SLOT, createExcludeMenuInfoItem(page, totalPages));

        if (page > 0) {
            inventory.setItem(
                    EXCLUDE_MENU_PREVIOUS_SLOT,
                    createMenuControlItem(Material.ARROW, "Previous Page", NamedTextColor.YELLOW)
            );
        }

        if (page + 1 < totalPages) {
            inventory.setItem(
                    EXCLUDE_MENU_NEXT_SLOT,
                    createMenuControlItem(Material.ARROW, "Next Page", NamedTextColor.YELLOW)
            );
        }

        inventory.setItem(
                EXCLUDE_MENU_APPLY_SLOT,
                createMenuControlItem(
                        Material.LIME_DYE,
                        "Apply Changes",
                        NamedTextColor.GREEN,
                        List.of(
                                Component.text("Keep the current exclusions and close this menu.", NamedTextColor.GRAY)
                                        .decoration(TextDecoration.ITALIC, false)
                        )
                )
        );

        inventory.setItem(
                EXCLUDE_MENU_CANCEL_SLOT,
                createMenuControlItem(
                        Material.BARRIER,
                        "Cancel Changes",
                        NamedTextColor.RED,
                        List.of(
                                Component.text("Undo all edits from this menu and close it.", NamedTextColor.GRAY)
                                        .decoration(TextDecoration.ITALIC, false)
                        )
                )
        );
    }

    private ItemStack createExcludeMenuItem(final Player player, final Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        boolean persistentExcluded = plugin.isPackInputExcluded(player.getUniqueId(), material);
        boolean nextRunExcluded = plugin.isPackInputExcludedNextRun(player.getUniqueId(), material);
        Component statusPrefix = persistentExcluded
                ? Component.text("X ", NamedTextColor.RED)
                : nextRunExcluded
                ? Component.text("! ", NamedTextColor.YELLOW)
                : Component.text("OK ", NamedTextColor.GREEN);
        Component materialName = Component.text(
                getItemName(material),
                persistentExcluded ? NamedTextColor.GRAY : nextRunExcluded ? NamedTextColor.YELLOW : NamedTextColor.WHITE
        );

        meta.setEnchantmentGlintOverride(persistentExcluded || nextRunExcluded);
        meta.displayName(statusPrefix.append(materialName).decoration(TextDecoration.ITALIC, false));
        if (persistentExcluded) {
            meta.lore(List.of(
                    Component.text("Persistent exclusion is active.", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("This slot glows to show an active exclusion.", NamedTextColor.DARK_RED)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Right-click to include this item again.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Left-click is unavailable while persistent exclusion is active.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        } else if (nextRunExcluded) {
            meta.lore(List.of(
                    Component.text("This item will be skipped on the next pack run only.", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("The slot glow marks a temporary exclusion.", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Left-click to remove the one-time skip.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Right-click to make the exclusion persistent.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        } else {
            meta.lore(List.of(
                    Component.text("No exclusion is active.", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Left-click to skip it on the next run only.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Right-click to exclude it persistently.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createExcludeMenuInfoItem(final int page, final int totalPages) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(
                Component.text("Exclusion Help", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
        );
        meta.lore(List.of(
                Component.text("Left-click: skip on the next pack run only.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: toggle a persistent exclusion.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Glowing slots have an active exclusion.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Red X = persistent, yellow ! = next run only.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Closing the menu or using green applies; red X cancels.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Use the red X button to cancel all edits from this menu.", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Page " + (page + 1) + " of " + totalPages, NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMenuControlItem(
            final Material material,
            final String name,
            final NamedTextColor color
    ) {
        return createMenuControlItem(material, name, color, Collections.emptyList());
    }

    private ItemStack createMenuControlItem(
            final Material material,
            final String name,
            final NamedTextColor color,
            final List<Component> lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMenuFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(" ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private List<Material> getConfiguredInputMaterials() {
        ConfigurationSection packSection = plugin.getConfig().getConfigurationSection("pack");
        if (packSection == null) {
            return Collections.emptyList();
        }

        List<Material> configuredInputs = new ArrayList<>();
        for (String key : packSection.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isItem()) {
                continue;
            }

            configuredInputs.add(material);
        }

        configuredInputs.sort(Comparator.comparing(this::getItemName));
        return configuredInputs;
    }

    private int getExcludeMenuPageCount(final int materialCount) {
        return Math.max(1, (int) Math.ceil((double) materialCount / EXCLUDE_MENU_CONTENT_SIZE));
    }

    private void renderCurrentExcludeMenu(
            final Inventory inventory,
            final Player player,
            final ExcludeMenuHolder holder,
            final int page,
            final int totalPages
    ) {
        List<Material> configuredInputs = getConfiguredInputMaterials();
        int safePage = Math.max(0, Math.min(page, Math.max(0, totalPages - 1)));
        excludeMenuSessions.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new ExcludeMenuSession(
                        plugin.getPackInputExcludedPersistentSnapshot(player.getUniqueId()),
                        plugin.getPackInputExcludedNextRunSnapshot(player.getUniqueId())
                )
        );
        renderExcludeMenu(inventory, player, holder, configuredInputs, safePage, getExcludeMenuPageCount(configuredInputs.size()));
    }

    private void renderExcludeMenu(
            final Inventory inventory,
            final Player player,
            final ExcludeMenuHolder holder,
            final List<Material> configuredInputs,
            final int page,
            final int totalPages
    ) {
        inventory.clear();
        holder.setPage(page);

        int startIndex = page * EXCLUDE_MENU_CONTENT_SIZE;
        for (int slot = 0; slot < EXCLUDE_MENU_CONTENT_SIZE; slot++) {
            int materialIndex = startIndex + slot;
            if (materialIndex >= configuredInputs.size()) {
                break;
            }

            inventory.setItem(slot, createExcludeMenuItem(player, configuredInputs.get(materialIndex)));
        }

        fillExcludeMenuControls(inventory, page, totalPages);
    }

    private void refreshExcludeMenuItem(
            final InventoryClickEvent event,
            final int rawSlot,
            final Player player,
            final Material material,
            final int page,
            final int totalPages
    ) {
        event.getView().getTopInventory().setItem(rawSlot, createExcludeMenuItem(player, material));
        event.getView().getTopInventory().setItem(
                EXCLUDE_MENU_INFO_SLOT,
                createExcludeMenuInfoItem(page, totalPages)
        );
    }

    private String getMessage(final String path, final String fallback) {
        String value = plugin.getConfig().getString(path);
        return sanitizeMessage(value == null ? fallback : value);
    }

    private String sanitizeMessage(final String message) {
        return message
                .replace("\u00C3\u201A\u00C2\u00A7", "\u00A7")
                .replace("\u00C2\u00A7", "\u00A7");
    }

    private String normalizeDisplayArrows(final String message) {
        return message.replace(" -> ", " → ");
    }

    private String getItemName(final Material material) {
        Component rendered = GlobalTranslator.render(Component.translatable(material), Locale.US);
        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);

        if (plain == null || plain.isBlank()) {
            return prettifyMaterialName(material);
        }

        return plain;
    }

    private String prettifyMaterialName(final Material material) {
        String raw = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.toString();
    }

    private String formatItemAmount(final int amount, final Material material) {
        return amount + " " + getItemName(material);
    }

    private String formatMaterialTotals(final Map<Material, Integer> materialTotals) {
        List<Map.Entry<Material, Integer>> entries = new ArrayList<>(materialTotals.entrySet());
        entries.removeIf(entry -> entry.getValue() <= 0);

        if (entries.isEmpty()) {
            return "nothing";
        }

        entries.sort(Comparator
                .<Map.Entry<Material, Integer>>comparingInt(entry -> getPackDepth(entry.getKey()))
                .reversed()
                .thenComparing(entry -> getItemName(entry.getKey())));

        StringJoiner joiner = new StringJoiner(" + ");
        for (Map.Entry<Material, Integer> entry : entries) {
            joiner.add(formatItemAmount(entry.getValue(), entry.getKey()));
        }

        return joiner.toString();
    }

    private int getPackDepth(final Material material) {
        ConfigurationSection packSection = plugin.getConfig().getConfigurationSection("pack");
        if (packSection == null) {
            return 0;
        }

        return getPackDepth(material, packSection, new HashSet<>());
    }

    private int getPackDepth(
            final Material material,
            final ConfigurationSection packSection,
            final Set<Material> visiting
    ) {
        if (!visiting.add(material)) {
            return 0;
        }

        int depth = 0;
        for (String key : packSection.getKeys(false)) {
            ConfigurationSection rule = packSection.getConfigurationSection(key);
            if (rule == null) {
                continue;
            }

            Material input = Material.matchMaterial(key);
            String outputName = rule.getString("output");
            Material output = outputName == null ? null : Material.matchMaterial(outputName);
            if (input == null || output != material) {
                continue;
            }

            depth = Math.max(depth, getPackDepth(input, packSection, visiting) + 1);
        }

        visiting.remove(material);
        return depth;
    }

    private String formatPackResult(
            final int producedAmount,
            final Material producedMaterial,
            final int leftoverAmount,
            final Material leftoverMaterial
    ) {
        String result = formatItemAmount(producedAmount, producedMaterial);

        if (leftoverAmount > 0) {
            result += " + " + formatItemAmount(leftoverAmount, leftoverMaterial);
        }

        return result;
    }

    private record PackRequest(boolean automatic, Inventory targetInventory, boolean includeNearbyPickups) {

        private static PackRequest manual() {
            return new PackRequest(false, null, true);
        }

        private static PackRequest auto() {
            return new PackRequest(true, null, true);
        }

        private static PackRequest chest(final Inventory inventory) {
            return new PackRequest(false, inventory, false);
        }

        private boolean chestTarget() {
            return targetInventory != null;
        }

        private Inventory targetInventory(final Player player) {
            if (targetInventory == null) {
                return player.getInventory();
            }

            return targetInventory;
        }
    }

    private enum AutoPackTrigger {
        PICKUP("pickup", true),
        JOIN("join", false),
        CRAFTING_TABLE_PLACE("crafting_table_place", true),
        CRAFTING_TABLE_NEARBY("crafting_table_nearby", true);

        private final String configKey;
        private final boolean defaultEnabled;

        AutoPackTrigger(final String configKey, final boolean defaultEnabled) {
            this.configKey = configKey;
            this.defaultEnabled = defaultEnabled;
        }

        private String configKey() {
            return configKey;
        }

        private boolean defaultEnabled() {
            return defaultEnabled;
        }
    }

    private record AttemptResult(
            int produced,
            int inputConsumed,
            int leftoverInput,
            int additionalSlotsNeeded,
            boolean inventoryChanged
    ) {
    }

    private record PassResult(
            int produced,
            boolean hadValidAttempt,
            boolean madeAnyChange,
            boolean blockedByCraftingRequirement,
            boolean usedSmallRecipeBypass,
            boolean inventoryChangedDuringPass
    ) {
    }

    private record InventoryFailure(
            int availableInput,
            Material inputMaterial,
            int producedAmount,
            Material producedMaterial,
            int leftoverInput,
            int additionalSlotsNeeded
    ) {
    }

    private record SkippedMaterial(int amount, Material material) {
    }

    private record FailureSummary(int totalAdditionalSlotsNeeded, Map<Material, InventoryFailure> failures) {
    }

    private record PackResult(
            int totalProduced,
            boolean hadValidAttempt,
            boolean blockedByCraftingRequirement,
            boolean usedSmallRecipeBypass,
            boolean inventoryChangedDuringRun,
            int totalAdditionalSlotsNeeded,
            Map<Material, InventoryFailure> inventoryFailures,
            List<SkippedMaterial> skippedMaterials
    ) {
    }

    private record OriginSummary(
            int initialInputAmount,
            Map<Material, Integer> finalMaterialTotals
    ) {
        private int totalFinalItems() {
            int total = 0;
            for (int amount : finalMaterialTotals.values()) {
                total += amount;
            }
            return total;
        }
    }

    private record OutputAllocation(Material originMaterial, long remainder, int consumedInput) {
    }

    private record PackRule(Material input, Material output, int ratioIn, int ratioOut) {
    }

    private record CraftingRequirementState(
            boolean valid,
            String failureMessage,
            CraftingTableMode mode,
            int range,
            boolean hasInventoryTable,
            boolean hasNearbyTable,
            boolean bypassForSmallRecipes,
            int bypassMaxRatioIn
    ) {
    }

    private record ExcludeMenuSession(
            Set<Material> initialPersistentExclusions,
            Set<Material> initialNextRunExclusions
    ) {
    }

    private static final class PackExecution {

        private boolean hadValidAttempt;
        private boolean blockedByCraftingRequirement;
        private boolean usedSmallRecipeBypass;
        private int settleTicksRemaining;
        private final Map<Material, Integer> initialInputTotals = new LinkedHashMap<>();
        private final Map<Material, LinkedHashMap<Material, Integer>> trackedMaterialOrigins = new EnumMap<>(Material.class);
        private final Set<Material> convertedOrigins = new HashSet<>();

        private void merge(final PackResult result) {
            hadValidAttempt |= result.hadValidAttempt();
            blockedByCraftingRequirement |= result.blockedByCraftingRequirement();
            usedSmallRecipeBypass |= result.usedSmallRecipeBypass();
        }

        private boolean hasSuccessfulConversions() {
            return !convertedOrigins.isEmpty();
        }

        private void syncTrackedInventory(
                final Map<Material, Integer> liveCounts,
                final Set<Material> trackableInputMaterials
        ) {
            for (Map.Entry<Material, Integer> entry : liveCounts.entrySet()) {
                Material material = entry.getKey();
                int liveAmount = entry.getValue();
                int trackedAmount = getTrackedAmount(material);

                if (liveAmount < trackedAmount) {
                    removeTrackedAmount(material, trackedAmount - liveAmount);
                    continue;
                }

                if (liveAmount > trackedAmount && trackableInputMaterials.contains(material)) {
                    trackNewInput(material, liveAmount - trackedAmount);
                }
            }

            for (Material material : new ArrayList<>(trackedMaterialOrigins.keySet())) {
                if (liveCounts.containsKey(material)) {
                    continue;
                }

                removeTrackedAmount(material, getTrackedAmount(material));
            }
        }

        private void recordSuccessfulConversion(
                final Material input,
                final Material output,
                final int ratioIn,
                final int ratioOut,
                final int consumedInput,
                final int producedOutput
        ) {
            Map<Material, Integer> consumedByOrigin = consumeTrackedAmounts(input, consumedInput);
            if (consumedByOrigin.isEmpty()) {
                return;
            }

            convertedOrigins.addAll(consumedByOrigin.keySet());

            Map<Material, Integer> producedByOrigin = allocateProducedAmounts(
                    consumedByOrigin,
                    ratioIn,
                    ratioOut,
                    producedOutput
            );
            for (Map.Entry<Material, Integer> entry : producedByOrigin.entrySet()) {
                if (entry.getValue() <= 0) {
                    continue;
                }

                addTrackedAmount(output, entry.getKey(), entry.getValue());
            }
        }

        private Map<Material, OriginSummary> buildOriginSummaries() {
            Map<Material, OriginSummary> summaries = new LinkedHashMap<>();

            for (Map.Entry<Material, Integer> entry : initialInputTotals.entrySet()) {
                Material origin = entry.getKey();
                if (!convertedOrigins.contains(origin)) {
                    continue;
                }

                Map<Material, Integer> finalTotals = new EnumMap<>(Material.class);
                for (Map.Entry<Material, LinkedHashMap<Material, Integer>> materialEntry : trackedMaterialOrigins.entrySet()) {
                    int amount = materialEntry.getValue().getOrDefault(origin, 0);
                    if (amount > 0) {
                        finalTotals.put(materialEntry.getKey(), amount);
                    }
                }

                if (!finalTotals.isEmpty()) {
                    summaries.put(origin, new OriginSummary(entry.getValue(), finalTotals));
                }
            }

            return summaries;
        }

        private Map<Material, Integer> consumeTrackedAmounts(final Material material, final int amount) {
            if (amount <= 0) {
                return Collections.emptyMap();
            }

            int trackedAmount = getTrackedAmount(material);
            if (trackedAmount < amount) {
                trackNewInput(material, amount - trackedAmount);
            }

            LinkedHashMap<Material, Integer> origins = trackedMaterialOrigins.get(material);
            if (origins == null || origins.isEmpty()) {
                return Collections.emptyMap();
            }

            int remaining = amount;
            Map<Material, Integer> consumedByOrigin = new LinkedHashMap<>();
            List<Material> emptiedOrigins = new ArrayList<>();

            for (Map.Entry<Material, Integer> entry : origins.entrySet()) {
                if (remaining <= 0) {
                    break;
                }

                int taken = Math.min(entry.getValue(), remaining);
                if (taken <= 0) {
                    continue;
                }

                consumedByOrigin.put(entry.getKey(), taken);

                int updatedAmount = entry.getValue() - taken;
                if (updatedAmount > 0) {
                    entry.setValue(updatedAmount);
                } else {
                    emptiedOrigins.add(entry.getKey());
                }

                remaining -= taken;
            }

            for (Material emptiedOrigin : emptiedOrigins) {
                origins.remove(emptiedOrigin);
            }

            if (origins.isEmpty()) {
                trackedMaterialOrigins.remove(material);
            }

            return consumedByOrigin;
        }

        private Map<Material, Integer> allocateProducedAmounts(
                final Map<Material, Integer> consumedByOrigin,
                final int ratioIn,
                final int ratioOut,
                final int producedOutput
        ) {
            if (consumedByOrigin.isEmpty() || producedOutput <= 0 || ratioIn <= 0 || ratioOut <= 0) {
                return Collections.emptyMap();
            }

            int totalConsumed = 0;
            for (int amount : consumedByOrigin.values()) {
                totalConsumed += amount;
            }
            if (totalConsumed <= 0) {
                return Collections.emptyMap();
            }

            Map<Material, Integer> producedByOrigin = new LinkedHashMap<>();
            List<OutputAllocation> allocations = new ArrayList<>();
            int assignedOutput = 0;

            for (Map.Entry<Material, Integer> entry : consumedByOrigin.entrySet()) {
                long scaledOutput = (long) entry.getValue() * producedOutput;
                int baseOutput = (int) (scaledOutput / totalConsumed);
                long remainder = scaledOutput % totalConsumed;

                producedByOrigin.put(entry.getKey(), baseOutput);
                assignedOutput += baseOutput;
                allocations.add(new OutputAllocation(entry.getKey(), remainder, entry.getValue()));
            }

            allocations.sort(
                    Comparator.comparingLong(OutputAllocation::remainder)
                            .reversed()
                            .thenComparing(Comparator.comparingInt(OutputAllocation::consumedInput).reversed())
            );

            int remainingOutput = producedOutput - assignedOutput;
            while (remainingOutput > 0 && !allocations.isEmpty()) {
                for (OutputAllocation allocation : allocations) {
                    producedByOrigin.merge(allocation.originMaterial(), 1, Integer::sum);
                    remainingOutput--;
                    if (remainingOutput == 0) {
                        break;
                    }
                }
            }

            return producedByOrigin;
        }

        private void trackNewInput(final Material material, final int amount) {
            if (amount <= 0) {
                return;
            }

            addTrackedAmount(material, material, amount);
            initialInputTotals.merge(material, amount, Integer::sum);
        }

        private void addTrackedAmount(final Material material, final Material origin, final int amount) {
            if (amount <= 0) {
                return;
            }

            trackedMaterialOrigins
                    .computeIfAbsent(material, ignored -> new LinkedHashMap<>())
                    .merge(origin, amount, Integer::sum);
        }

        private int getTrackedAmount(final Material material) {
            Map<Material, Integer> origins = trackedMaterialOrigins.get(material);
            if (origins == null) {
                return 0;
            }

            int total = 0;
            for (int amount : origins.values()) {
                total += amount;
            }
            return total;
        }

        private void removeTrackedAmount(final Material material, final int amount) {
            if (amount <= 0) {
                return;
            }

            LinkedHashMap<Material, Integer> origins = trackedMaterialOrigins.get(material);
            if (origins == null || origins.isEmpty()) {
                return;
            }

            int remaining = drainOriginAmount(origins, material, amount);
            if (remaining > 0) {
                for (Material origin : new ArrayList<>(origins.keySet())) {
                    if (origin == material) {
                        continue;
                    }

                    remaining = drainOriginAmount(origins, origin, remaining);
                    if (remaining <= 0) {
                        break;
                    }
                }
            }

            if (origins.isEmpty()) {
                trackedMaterialOrigins.remove(material);
            }
        }

        private int drainOriginAmount(
                final LinkedHashMap<Material, Integer> origins,
                final Material origin,
                final int amount
        ) {
            int existing = origins.getOrDefault(origin, 0);
            if (existing <= 0 || amount <= 0) {
                return amount;
            }

            int removed = Math.min(existing, amount);
            int updatedAmount = existing - removed;
            if (updatedAmount > 0) {
                origins.put(origin, updatedAmount);
            } else {
                origins.remove(origin);
            }

            return amount - removed;
        }

        private void resetSettleTicks() {
            settleTicksRemaining = PACK_SETTLE_TICKS;
        }

        private boolean shouldWaitForInventoryToSettle() {
            return settleTicksRemaining > 0;
        }

        private void consumeSettleTick() {
            settleTicksRemaining--;
        }
    }

    private static final class ExcludeMenuHolder implements InventoryHolder {

        private final UUID playerId;
        private int page;
        private Inventory inventory;

        private ExcludeMenuHolder(final UUID playerId, final int page) {
            this.playerId = playerId;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private int page() {
            return page;
        }

        private UUID playerId() {
            return playerId;
        }

        private void setPage(final int page) {
            this.page = page;
        }

        private void setInventory(final Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
