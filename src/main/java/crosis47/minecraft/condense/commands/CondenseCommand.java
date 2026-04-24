package crosis47.minecraft.condense.commands;

import crosis47.minecraft.condense.CondensePlugin;
import crosis47.minecraft.condense.requirements.CraftingTableMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

public final class CondenseCommand implements TabExecutor, Listener {

    private static final int EXCLUDE_MENU_SIZE = 54;
    private static final int EXCLUDE_MENU_CONTENT_SIZE = 45;
    private static final int EXCLUDE_MENU_PREVIOUS_SLOT = 45;
    private static final int EXCLUDE_MENU_INFO_SLOT = 49;
    private static final int EXCLUDE_MENU_NEXT_SLOT = 50;
    private static final int EXCLUDE_MENU_APPLY_SLOT = 52;
    private static final int EXCLUDE_MENU_CANCEL_SLOT = 53;

    private final CondensePlugin plugin;
    private final Map<UUID, ExcludeMenuSession> excludeMenuSessions = new HashMap<>();

    public CondenseCommand(final CondensePlugin plugin) {
        this.plugin = plugin;
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
                if (!sender.hasPermission("condense.reload")) {
                    sender.sendMessage(getMessage(
                            "message.error.no_permission",
                            "You do not have permission to use this command."
                    ));
                    return true;
                }

                plugin.reloadPluginConfig();
                sender.sendMessage(getMessage(
                        "message.reload",
                        "Condense Reforged config reloaded."
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
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        if (plugin.isCondenserItemModeEnabled()) {
            if (!plugin.isCondenserCommandAllowed()) {
                player.sendMessage(getMessage(
                        "message.info.use_condenser_item",
                        "Use a Condenser from your inventory to condense materials."
                ));
                return true;
            }

            if (!plugin.hasCondenserItem(player.getInventory().getStorageContents())) {
                player.sendMessage(getMessage(
                        "message.error.condenser_item_required",
                        "You must have a Condenser in your inventory to use this command."
                ));
                return true;
            }
        }

        executeCondense(player);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ExcludeMenuHolder holder) {
            handleExcludeMenuClick(event, holder);
            return;
        }

        if (!plugin.isCondenserItemModeEnabled()) {
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

        if (!plugin.isCondenserItem(event.getCurrentItem())) {
            return;
        }

        event.setCancelled(true);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            if (!player.hasPermission("condense.use")) {
                player.sendMessage(getMessage(
                        "message.error.no_permission",
                        "You do not have permission to use this command."
                ));
                return;
            }

            executeCondense(player);
        });
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
            sendExclusionChangesAppliedMessage(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (!plugin.isCondenserItem(event.getItemInHand())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        plugin.cleanupCondenserItems(event.getPlayer());
    }

    public void executeCondense(final Player player) {
        UUID playerId = player.getUniqueId();

        try {
            CraftingRequirementState craftingState = getCraftingRequirementState(player);
            if (!craftingState.valid()) {
                player.sendMessage(craftingState.failureMessage());
                return;
            }

            CondenseResult result = condense(player, craftingState);

            if (result.totalProduced() == 0) {
                if (result.totalAdditionalSlotsNeeded() > 0) {
                    sendInventoryFullMessages(player, result.inventoryFailures());
                    sendInventoryFullSummary(player, result.totalAdditionalSlotsNeeded());
                    sendSkippedExcludedMaterialsMessage(player, result.skippedMaterials());
                    return;
                }

                if (result.blockedByCraftingRequirement()) {
                    player.sendMessage(buildCraftingRequirementFailureMessage(craftingState));
                    sendSkippedExcludedMaterialsMessage(player, result.skippedMaterials());
                    return;
                }

                if (!result.hadValidAttempt()) {
                    String message = getMessage(
                            "message.error.nothing_to_condense",
                            "You do not have any valid materials to condense."
                    );
                    player.sendMessage(message);
                }

                sendSkippedExcludedMaterialsMessage(player, result.skippedMaterials());
                return;
            }

            String message = getMessage(
                    "message.condense.resume",
                    "Converted [input] items into [output] output items."
            )
                    .replace("[input]", String.valueOf(result.totalInputConsumed()))
                    .replace("[output]", String.valueOf(result.totalProduced()));

            player.sendMessage(message);

            if (result.usedSmallRecipeBypass() && result.blockedByCraftingRequirement()) {
                String hint = getMessage(
                        "message.info.more_available_at_crafting_table",
                        "More materials can be condensed at a crafting table."
                );
                player.sendMessage(hint);
            }

            if (result.totalAdditionalSlotsNeeded() > 0) {
                sendInventoryFullMessages(player, result.inventoryFailures());
                sendInventoryFullSummary(player, result.totalAdditionalSlotsNeeded());
            }

            sendSkippedExcludedMaterialsMessage(player, result.skippedMaterials());
        } finally {
            plugin.clearAllCondenseInputsExcludedNextRun(playerId);
        }
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
            if (sender.hasPermission("condense.reload") && "reload".startsWith(input)) {
                completions.add("reload");
            }

            return completions;
        }

        return Collections.emptyList();
    }

    private CraftingRequirementState getCraftingRequirementState(final Player player) {
        if (plugin.isCondenserItemModeEnabled()) {
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
        int range = Math.max(0, plugin.getConfig().getInt("requirements.crafting_table_range", 5));

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

        boolean hasInventoryTable = hasCraftingTableInInventory(player.getInventory());
        boolean hasNearbyTable = isNearCraftingTable(player, range);

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
        Location location = player.getLocation();
        World world = player.getWorld();

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

    private CondenseResult condense(final Player player, final CraftingRequirementState craftingState) {
        PlayerInventory inventory = player.getInventory();
        ConfigurationSection condenseSection = plugin.getConfig().getConfigurationSection("condense");
        if (condenseSection == null) {
            plugin.getLogger().warning("Missing 'condense' section in config.yml");
            return new CondenseResult(0, 0, false, false, false, 0, Collections.emptyMap(), Collections.emptyList());
        }

        int totalProduced = 0;
        int totalInputConsumed = 0;
        boolean hadValidAttempt = false;
        boolean blockedByCraftingRequirement = false;
        boolean usedSmallRecipeBypass = false;

        while (true) {
            PassResult passResult = runCondensePass(player, inventory, condenseSection, craftingState);

            totalProduced += passResult.produced();
            totalInputConsumed += passResult.inputConsumed();
            hadValidAttempt |= passResult.hadValidAttempt();
            blockedByCraftingRequirement |= passResult.blockedByCraftingRequirement();
            usedSmallRecipeBypass |= passResult.usedSmallRecipeBypass();

            if (!passResult.madeAnyChange()) {
                break;
            }
        }

        FailureSummary finalFailureSummary = evaluateRemainingInventoryFailures(
                player.getUniqueId(),
                inventory,
                condenseSection,
                craftingState
        );
        List<SkippedMaterial> skippedMaterials = collectSkippedExcludedMaterialsForRun(
                player.getUniqueId(),
                inventory,
                condenseSection
        );

        return new CondenseResult(
                totalProduced,
                totalInputConsumed,
                hadValidAttempt,
                blockedByCraftingRequirement,
                usedSmallRecipeBypass,
                finalFailureSummary.totalAdditionalSlotsNeeded(),
                finalFailureSummary.failures(),
                skippedMaterials
        );
    }

    private PassResult runCondensePass(
            final Player player,
            final PlayerInventory inventory,
            final ConfigurationSection condenseSection,
            final CraftingRequirementState craftingState
    ) {
        ItemStack[] storage = inventory.getStorageContents();

        Map<Material, Integer> itemCounts = new EnumMap<>(Material.class);
        for (ItemStack item : storage) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            itemCounts.merge(item.getType(), item.getAmount(), Integer::sum);
        }

        int passProduced = 0;
        int inputConsumed = 0;
        boolean hadValidAttempt = false;
        boolean madeAnyChange = false;
        boolean blockedByCraftingRequirement = false;
        boolean usedSmallRecipeBypass = false;

        for (String key : condenseSection.getKeys(false)) {
            ConfigurationSection rule = condenseSection.getConfigurationSection(key);
            if (rule == null) {
                plugin.getLogger().warning("Invalid condense rule section: " + key);
                continue;
            }

            Material input = Material.matchMaterial(key);
            if (input == null) {
                plugin.getLogger().warning("Invalid input material in config: " + key);
                continue;
            }

            if (plugin.isCondenseInputDisabled(input)
                    || plugin.isCondenseInputExcludedForRun(player.getUniqueId(), input)) {
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

                player.sendMessage(message);

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

            AttemptResult attempt = tryCondense(
                    player,
                    inventory,
                    input,
                    available,
                    output,
                    ratioIn,
                    ratioOut,
                    true,
                    false
            );

            if (attempt.produced() > 0) {
                passProduced += attempt.produced();
                madeAnyChange = true;

                if (bypassed) {
                    usedSmallRecipeBypass = true;
                }

                int crafts = available / ratioIn;
                int consumed = crafts * ratioIn;
                inputConsumed += consumed;
                itemCounts.put(input, available - consumed);
                itemCounts.merge(output, attempt.produced(), Integer::sum);
            }
        }

        return new PassResult(
                passProduced,
                inputConsumed,
                hadValidAttempt,
                madeAnyChange,
                blockedByCraftingRequirement,
                usedSmallRecipeBypass
        );
    }

    private FailureSummary evaluateRemainingInventoryFailures(
            final UUID playerId,
            final PlayerInventory inventory,
            final ConfigurationSection condenseSection,
            final CraftingRequirementState craftingState
    ) {
        ItemStack[] storage = inventory.getStorageContents();

        Map<Material, Integer> itemCounts = new EnumMap<>(Material.class);
        for (ItemStack item : storage) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            itemCounts.merge(item.getType(), item.getAmount(), Integer::sum);
        }

        Map<Material, InventoryFailure> failures = new EnumMap<>(Material.class);
        int totalAdditionalSlotsNeeded = 0;

        for (String key : condenseSection.getKeys(false)) {
            ConfigurationSection rule = condenseSection.getConfigurationSection(key);
            if (rule == null) {
                continue;
            }

            Material input = Material.matchMaterial(key);
            if (input == null
                    || plugin.isCondenseInputDisabled(input)
                    || plugin.isCondenseInputExcludedForRun(playerId, input)) {
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

            AttemptResult attempt = tryCondense(
                    null,
                    inventory,
                    input,
                    available,
                    output,
                    ratioIn,
                    ratioOut,
                    false,
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
                totalAdditionalSlotsNeeded += attempt.additionalSlotsNeeded();
            }
        }

        return new FailureSummary(totalAdditionalSlotsNeeded, failures);
    }

    private List<SkippedMaterial> collectSkippedExcludedMaterialsForRun(
            final UUID playerId,
            final PlayerInventory inventory,
            final ConfigurationSection condenseSection
    ) {
        ItemStack[] storage = inventory.getStorageContents();

        Map<Material, Integer> itemCounts = new EnumMap<>(Material.class);
        for (ItemStack item : storage) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            itemCounts.merge(item.getType(), item.getAmount(), Integer::sum);
        }

        List<SkippedMaterial> skippedMaterials = new ArrayList<>();
        for (String key : condenseSection.getKeys(false)) {
            Material input = Material.matchMaterial(key);
            if (input == null
                    || plugin.isCondenseInputDisabled(input)
                    || !plugin.isCondenseInputExcludedForRun(playerId, input)) {
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

    private AttemptResult tryCondense(
            final Player player,
            final PlayerInventory inventory,
            final Material input,
            final int availableInput,
            final Material output,
            final int ratioIn,
            final int ratioOut,
            final boolean sendSuccessMessages,
            final boolean sendInventoryFullMessages
    ) {
        int crafts = availableInput / ratioIn;
        if (crafts <= 0) {
            return new AttemptResult(0, 0);
        }

        int toConsume = crafts * ratioIn;
        int toProduce = crafts * ratioOut;
        int leftoverInput = availableInput - toConsume;

        ItemStack[] simulated = cloneContents(inventory.getStorageContents());

        boolean removed = removeFromContents(simulated, input, toConsume);
        if (!removed) {
            plugin.getLogger().warning("Failed to remove expected input items for " + input);
            return new AttemptResult(0, 0);
        }

        Map<Integer, ItemStack> leftovers = addToContents(simulated, new ItemStack(output, toProduce));
        if (!leftovers.isEmpty()) {
            int additionalSlotsNeeded = calculateAdditionalSlotsNeeded(leftovers, output.getMaxStackSize());

            if (sendInventoryFullMessages && player != null && plugin.getConfig().getBoolean("display.list")) {
                String fullInput = formatItemAmount(availableInput, input);
                String result = formatCondenseResult(toProduce, output, leftoverInput, input);

                String message = getMessage(
                        "message.error.inventory_full",
                        "Inventory full: [item1] -> [item2]"
                ).replace("[item1]", fullInput)
                 .replace("[item2]", result);

                player.sendMessage(message);
            }
            return new AttemptResult(0, additionalSlotsNeeded);
        }

        inventory.setStorageContents(simulated);

        if (sendSuccessMessages && player != null && plugin.getConfig().getBoolean("display.list")) {
            String item1 = formatItemAmount(availableInput, input);
            String item2 = formatCondenseResult(toProduce, output, leftoverInput, input);

            String message = getMessage(
                    "message.condense.item",
                    "[item1] -> [item2]"
            ).replace("[item1]", item1)
             .replace("[item2]", item2);

            player.sendMessage(message);
        }

        return new AttemptResult(toProduce, 0);
    }

    private void sendInventoryFullMessages(final Player player, final Map<Material, InventoryFailure> failures) {
        if (!plugin.getConfig().getBoolean("display.list")) {
            return;
        }

        for (InventoryFailure failure : failures.values()) {
            String fullInput = formatItemAmount(failure.availableInput(), failure.inputMaterial());
            String result = formatCondenseResult(
                    failure.producedAmount(),
                    failure.producedMaterial(),
                    failure.leftoverInput(),
                    failure.inputMaterial()
            );

            String message = getMessage(
                    "message.error.inventory_full",
                    "Inventory full: [item1] -> [item2]"
            ).replace("[item1]", fullInput)
             .replace("[item2]", result);

            player.sendMessage(message);
        }
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

    private void sendExclusionChangesAppliedMessage(final Player player) {
        player.sendMessage(Component.text("Exclusion changes applied.", NamedTextColor.GREEN));
    }

    private int calculateAdditionalSlotsNeeded(final Map<Integer, ItemStack> leftovers, final int maxStackSize) {
        int totalRemainingItems = 0;

        for (ItemStack leftover : leftovers.values()) {
            if (leftover != null) {
                totalRemainingItems += leftover.getAmount();
            }
        }

        return (int) Math.ceil((double) totalRemainingItems / maxStackSize);
    }

    private ItemStack[] cloneContents(final ItemStack[] original) {
        ItemStack[] copy = new ItemStack[original.length];
        for (int i = 0; i < original.length; i++) {
            copy[i] = original[i] == null ? null : original[i].clone();
        }
        return copy;
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
            player.sendMessage(Component.text("No condense inputs are currently configured.", NamedTextColor.YELLOW));
            return;
        }

        int totalPages = getExcludeMenuPageCount(configuredInputs.size());
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        excludeMenuSessions.computeIfAbsent(
                player.getUniqueId(),
                ignored -> new ExcludeMenuSession(
                        plugin.getCondenseInputExcludedPersistentSnapshot(player.getUniqueId()),
                        plugin.getCondenseInputExcludedNextRunSnapshot(player.getUniqueId())
                )
        );

        ExcludeMenuHolder holder = new ExcludeMenuHolder(player.getUniqueId(), safePage);
        Inventory inventory = Bukkit.createInventory(
                holder,
                EXCLUDE_MENU_SIZE,
                Component.text("Condense Exclusions", NamedTextColor.DARK_GREEN)
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
                boolean excluded = plugin.toggleCondenseInputExcluded(player.getUniqueId(), material);
                plugin.clearCondenseInputExcludedNextRun(player.getUniqueId(), material);

                refreshExcludeMenuItem(event, rawSlot, player, material, holder.page(), totalPages);

                if (excluded) {
                    player.sendMessage(
                            Component.text(getItemName(material), NamedTextColor.RED)
                                    .append(Component.text(" is now persistently excluded from condensing.", NamedTextColor.GRAY))
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

            if (plugin.isCondenseInputExcluded(player.getUniqueId(), material)) {
                player.sendMessage(
                        Component.text(getItemName(material), NamedTextColor.RED)
                                .append(Component.text(" is already persistently excluded. Right-click it to re-include.", NamedTextColor.GRAY))
                );
                return;
            }

            boolean excluded = plugin.toggleCondenseInputExcludedNextRun(player.getUniqueId(), material);
            refreshExcludeMenuItem(event, rawSlot, player, material, holder.page(), totalPages);

            if (excluded) {
                player.sendMessage(
                        Component.text(getItemName(material), NamedTextColor.YELLOW)
                                .append(Component.text(" will be skipped on the next condense run only.", NamedTextColor.GRAY))
                );
            } else {
                player.sendMessage(
                        Component.text(getItemName(material), NamedTextColor.GREEN)
                                .append(Component.text(" will no longer be skipped on the next condense run.", NamedTextColor.GRAY))
                );
            }
            return;
        }

        if (rawSlot == EXCLUDE_MENU_CANCEL_SLOT) {
            ExcludeMenuSession session = excludeMenuSessions.get(player.getUniqueId());
            if (session != null) {
                boolean restored = plugin.replaceCondenseInputExcludedPersistent(
                        player.getUniqueId(),
                        session.initialPersistentExclusions()
                );
                plugin.replaceCondenseInputExcludedNextRun(
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
            excludeMenuSessions.remove(player.getUniqueId());
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

        boolean persistentExcluded = plugin.isCondenseInputExcluded(player.getUniqueId(), material);
        boolean nextRunExcluded = plugin.isCondenseInputExcludedNextRun(player.getUniqueId(), material);
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
                    Component.text("This item will be skipped on the next condense run only.", NamedTextColor.YELLOW)
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
                Component.text("Left-click: skip on the next condense run only.", NamedTextColor.GRAY)
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
        ConfigurationSection condenseSection = plugin.getConfig().getConfigurationSection("condense");
        if (condenseSection == null) {
            return Collections.emptyList();
        }

        List<Material> configuredInputs = new ArrayList<>();
        for (String key : condenseSection.getKeys(false)) {
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
                        plugin.getCondenseInputExcludedPersistentSnapshot(player.getUniqueId()),
                        plugin.getCondenseInputExcludedNextRunSnapshot(player.getUniqueId())
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
        return value == null ? fallback : value;
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

    private String formatCondenseResult(
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

    private record AttemptResult(int produced, int additionalSlotsNeeded) {
    }

    private record PassResult(
            int produced,
            int inputConsumed,
            boolean hadValidAttempt,
            boolean madeAnyChange,
            boolean blockedByCraftingRequirement,
            boolean usedSmallRecipeBypass
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

    private record CondenseResult(
            int totalProduced,
            int totalInputConsumed,
            boolean hadValidAttempt,
            boolean blockedByCraftingRequirement,
            boolean usedSmallRecipeBypass,
            int totalAdditionalSlotsNeeded,
            Map<Material, InventoryFailure> inventoryFailures,
            List<SkippedMaterial> skippedMaterials
    ) {
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
