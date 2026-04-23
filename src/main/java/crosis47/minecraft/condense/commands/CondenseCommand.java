package crosis47.minecraft.condense.commands;

import crosis47.minecraft.condense.CondensePlugin;
import crosis47.minecraft.condense.requirements.CraftingTableMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CondenseCommand implements TabExecutor {

    private final CondensePlugin plugin;

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
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("condense.reload")) {
                sender.sendMessage(getMessage(
                        "message.error.no_permission",
                        "§cYou do not have permission to use this command."
                ));
                return true;
            }

            plugin.reloadPluginConfig();
            sender.sendMessage(getMessage(
                    "message.reload",
                    "§aCondense Reforged config reloaded."
            ));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        RequirementCheckResult requirementResult = checkCraftingTableRequirement(player);
        if (!requirementResult.allowed()) {
            player.sendMessage(requirementResult.message());
            return true;
        }

        CondenseResult result = condense(player);

        if (result.totalProduced() == 0) {
            if (result.totalAdditionalSlotsNeeded() > 0) {
                String summary = getMessage(
                        "message.error.inventory_full_summary",
                        "§6Inventory full summary: [slots] additional slot(s) would have been needed in total."
                ).replace("[slots]", String.valueOf(result.totalAdditionalSlotsNeeded()));

                player.sendMessage(summary);
                return true;
            }

            if (!result.hadValidAttempt()) {
                String message = getMessage(
                        "message.error.nothing_to_condense",
                        "§eYou do not have any valid materials to condense."
                );
                player.sendMessage(message);
            }
            return true;
        }

        String message = getMessage(
                "message.condense.resume",
                "§a[number] output items were created."
        ).replace("[number]", String.valueOf(result.totalProduced()));

        player.sendMessage(message);

        if (result.totalAdditionalSlotsNeeded() > 0) {
            String summary = getMessage(
                    "message.error.inventory_full_summary",
                    "§6Inventory full summary: [slots] additional slot(s) would have been needed in total."
            ).replace("[slots]", String.valueOf(result.totalAdditionalSlotsNeeded()));

            player.sendMessage(summary);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1 && sender.hasPermission("condense.reload")) {
            String input = args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(input)) {
                return Collections.singletonList("reload");
            }
        }

        return Collections.emptyList();
    }

    private RequirementCheckResult checkCraftingTableRequirement(final Player player) {
        String rawMode = plugin.getConfig().getString("requirements.crafting_table_mode", "DISABLED");
        int range = Math.max(0, plugin.getConfig().getInt("requirements.crafting_table_range", 5));

        CraftingTableMode mode = CraftingTableMode.fromString(rawMode);
        if (mode == null) {
            plugin.getLogger().warning("Invalid requirements.crafting_table_mode value: " + rawMode);

            String message = getMessage(
                    "message.error.invalid_crafting_table_mode",
                    "§4This plugin is misconfigured: invalid crafting table mode '[mode]'. Please contact an admin."
            ).replace("[mode]", String.valueOf(rawMode));

            return RequirementCheckResult.failure(message);
        }

        boolean hasInventoryTable = hasCraftingTableInInventory(player.getInventory());
        boolean hasNearbyTable = isNearCraftingTable(player, range);

        return switch (mode) {
            case DISABLED -> RequirementCheckResult.success();

            case INVENTORY_ONLY -> {
                if (hasInventoryTable) {
                    yield RequirementCheckResult.success();
                }

                String message = getMessage(
                        "message.error.crafting_table_required_inventory",
                        "§4You must have a crafting table in your inventory to use this command."
                );
                yield RequirementCheckResult.failure(message);
            }

            case NEARBY_ONLY -> {
                if (hasNearbyTable) {
                    yield RequirementCheckResult.success();
                }

                String message = getMessage(
                        "message.error.crafting_table_required_nearby",
                        "§4You must be within [range] blocks of a crafting table to use this command."
                ).replace("[range]", String.valueOf(range));

                yield RequirementCheckResult.failure(message);
            }

            case INVENTORY_OR_NEARBY -> {
                if (hasInventoryTable || hasNearbyTable) {
                    yield RequirementCheckResult.success();
                }

                String message = getMessage(
                        "message.error.crafting_table_required_inventory_or_nearby",
                        "§4You need a crafting table to use this command. Put one in your inventory or stand within [range] blocks of one."
                ).replace("[range]", String.valueOf(range));

                yield RequirementCheckResult.failure(message);
            }
        };
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

    private CondenseResult condense(final Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();

        Map<Material, Integer> itemCounts = new EnumMap<>(Material.class);
        for (ItemStack item : storage) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            itemCounts.merge(item.getType(), item.getAmount(), Integer::sum);
        }

        ConfigurationSection condenseSection = plugin.getConfig().getConfigurationSection("condense");
        if (condenseSection == null) {
            plugin.getLogger().warning("Missing 'condense' section in config.yml");
            return new CondenseResult(0, false, 0);
        }

        int totalProduced = 0;
        boolean hadValidAttempt = false;
        int totalAdditionalSlotsNeeded = 0;

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

            if (plugin.isCondenseInputDisabled(input)) {
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
                        "§4Attention: the conversion ratio of [item1] is invalid."
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

            AttemptResult attempt = tryCondense(player, inventory, input, available, output, ratioIn, ratioOut);
            totalAdditionalSlotsNeeded += attempt.additionalSlotsNeeded();

            if (attempt.produced() > 0) {
                int crafts = available / ratioIn;
                int consumed = crafts * ratioIn;
                itemCounts.put(input, available - consumed);
                itemCounts.merge(output, attempt.produced(), Integer::sum);
                totalProduced += attempt.produced();
            }
        }

        return new CondenseResult(totalProduced, hadValidAttempt, totalAdditionalSlotsNeeded);
    }

    private AttemptResult tryCondense(
            final Player player,
            final PlayerInventory inventory,
            final Material input,
            final int availableInput,
            final Material output,
            final int ratioIn,
            final int ratioOut
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

            if (plugin.getConfig().getBoolean("display.list")) {
                String fullInput = formatItemAmount(availableInput, input);
                String result = formatCondenseResult(toProduce, output, leftoverInput, input);

                String message = getMessage(
                        "message.error.inventory_full",
                        "§4Inventory full: [item1] → [item2]"
                ).replace("[item1]", fullInput)
                 .replace("[item2]", result);

                player.sendMessage(message);
            }
            return new AttemptResult(0, additionalSlotsNeeded);
        }

        inventory.setStorageContents(simulated);

        if (plugin.getConfig().getBoolean("display.list")) {
            String item1 = formatItemAmount(availableInput, input);
            String item2 = formatCondenseResult(toProduce, output, leftoverInput, input);

            String message = getMessage(
                    "message.condense.item",
                    "§a[item1] → [item2]"
            ).replace("[item1]", item1)
             .replace("[item2]", item2);

            player.sendMessage(message);
        }

        return new AttemptResult(toProduce, 0);
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

    private String getMessage(final String path, final String fallback) {
        String value = plugin.getConfig().getString(path);
        return value == null ? fallback : value;
    }

    private String getItemName(final Material material) {
        return new ItemStack(material).getI18NDisplayName();
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

    private record RequirementCheckResult(boolean allowed, String message) {
        private static RequirementCheckResult success() {
            return new RequirementCheckResult(true, "");
        }

        private static RequirementCheckResult failure(final String message) {
            return new RequirementCheckResult(false, message);
        }
    }

    private record AttemptResult(int produced, int additionalSlotsNeeded) {
    }

    private record CondenseResult(int totalProduced, boolean hadValidAttempt, int totalAdditionalSlotsNeeded) {
    }
}