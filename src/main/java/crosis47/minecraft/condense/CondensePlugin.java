package crosis47.minecraft.condense;

import crosis47.minecraft.condense.commands.CondenseCommand;
import crosis47.minecraft.condense.storage.PlayerExclusionStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

public final class CondensePlugin extends JavaPlugin {

    private static final int CURRENT_CONFIG_VERSION = 3;
    private static final String DEFAULT_CONDENSER_NAME = "Condenser";

    private final Set<Material> disabledCondenseInputs = new HashSet<>();
    private final Map<UUID, Set<Material>> excludedCondenseInputsByPlayer = new HashMap<>();
    private final Map<UUID, Set<Material>> nextRunExcludedCondenseInputsByPlayer = new HashMap<>();

    private NamespacedKey condenserRecipeKey;
    private NamespacedKey condenserItemKey;
    private PlayerExclusionStore playerExclusionStore;

    @Override
    public void onEnable() {
        condenserRecipeKey = new NamespacedKey(this, "condenser");
        condenserItemKey = new NamespacedKey(this, "condenser_item");

        saveDefaultConfig();
        updateConfigIfNeeded();
        validateConfig();
        try {
            playerExclusionStore = new PlayerExclusionStore(
                    this,
                    new File(getDataFolder(), "player-exclusions.db"),
                    new File(getDataFolder(), "player-exclusions.yml")
            );
            playerExclusionStore.initialize();
            reloadPersistentExclusionsFromStore();
        } catch (Exception ex) {
            getLogger().severe("Failed to initialize SQLite player exclusion storage: " + ex.getMessage());
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PluginCommand condenseCommand = Objects.requireNonNull(
                getCommand("condense"),
                "Command 'condense' is missing from plugin.yml"
        );

        CondenseCommand commandHandler = new CondenseCommand(this);
        condenseCommand.setExecutor(commandHandler);
        condenseCommand.setTabCompleter(commandHandler);
        getServer().getPluginManager().registerEvents(commandHandler, this);

        refreshCondenserRecipe();
        cleanupCondenserItemsForOnlinePlayers();

        getLogger().info("Condense Reforged enabled.");
    }

    @Override
    public void onDisable() {
        unregisterCondenserRecipe();
        getLogger().info("Condense Reforged disabled.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        updateConfigIfNeeded();
        validateConfig();
        reloadPersistentExclusionsFromStore();
        refreshCondenserRecipe();
        cleanupCondenserItemsForOnlinePlayers();
    }

    public boolean isCondenseInputDisabled(final Material material) {
        return disabledCondenseInputs.contains(material);
    }

    public Set<Material> getDisabledCondenseInputs() {
        return Collections.unmodifiableSet(disabledCondenseInputs);
    }

    public boolean isCondenseInputExcluded(final UUID playerId, final Material material) {
        if (playerId == null || material == null) {
            return false;
        }

        Set<Material> excludedInputs = excludedCondenseInputsByPlayer.get(playerId);
        return excludedInputs != null && excludedInputs.contains(material);
    }

    public boolean isCondenseInputExcludedNextRun(final UUID playerId, final Material material) {
        if (playerId == null || material == null) {
            return false;
        }

        Set<Material> excludedInputs = nextRunExcludedCondenseInputsByPlayer.get(playerId);
        return excludedInputs != null && excludedInputs.contains(material);
    }

    public boolean isCondenseInputExcludedForRun(final UUID playerId, final Material material) {
        return isCondenseInputExcluded(playerId, material)
                || isCondenseInputExcludedNextRun(playerId, material);
    }

    public Set<Material> getCondenseInputExcludedPersistentSnapshot(final UUID playerId) {
        return copyMaterialSet(excludedCondenseInputsByPlayer.get(playerId));
    }

    public Set<Material> getCondenseInputExcludedNextRunSnapshot(final UUID playerId) {
        return copyMaterialSet(nextRunExcludedCondenseInputsByPlayer.get(playerId));
    }

    public boolean toggleCondenseInputExcluded(final UUID playerId, final Material material) {
        return setCondenseInputExcluded(playerId, material, !isCondenseInputExcluded(playerId, material));
    }

    public boolean setCondenseInputExcluded(final UUID playerId, final Material material, final boolean excluded) {
        if (playerId == null || material == null) {
            return false;
        }

        if (playerExclusionStore == null) {
            getLogger().warning("Persistent exclusion store is not available.");
            return isCondenseInputExcluded(playerId, material);
        }

        try {
            playerExclusionStore.setPersistentExclusion(playerId, material, excluded);
        } catch (Exception ex) {
            getLogger().severe("Failed to update persistent SQLite exclusion for player " + playerId
                    + " and material " + material + ": " + ex.getMessage());
            ex.printStackTrace();
            return !excluded;
        }

        Set<Material> excludedInputs = excludedCondenseInputsByPlayer.computeIfAbsent(
                playerId,
                ignored -> EnumSet.noneOf(Material.class)
        );

        if (excluded) {
            excludedInputs.add(material);
        } else {
            excludedInputs.remove(material);
        }

        if (excludedInputs.isEmpty()) {
            excludedCondenseInputsByPlayer.remove(playerId);
        }

        return excluded;
    }

    public boolean toggleCondenseInputExcludedNextRun(final UUID playerId, final Material material) {
        return setCondenseInputExcludedNextRun(playerId, material, !isCondenseInputExcludedNextRun(playerId, material));
    }

    public boolean setCondenseInputExcludedNextRun(final UUID playerId, final Material material, final boolean excluded) {
        if (playerId == null || material == null) {
            return false;
        }

        Set<Material> excludedInputs = nextRunExcludedCondenseInputsByPlayer.computeIfAbsent(
                playerId,
                ignored -> EnumSet.noneOf(Material.class)
        );

        if (excludedInputs.contains(material)) {
            if (!excluded) {
                excludedInputs.remove(material);
            }
        } else if (excluded) {
            excludedInputs.add(material);
        }

        if (excludedInputs.isEmpty()) {
            nextRunExcludedCondenseInputsByPlayer.remove(playerId);
        }

        return excluded;
    }

    public void clearCondenseInputExcludedNextRun(final UUID playerId, final Material material) {
        if (playerId == null || material == null) {
            return;
        }

        Set<Material> excludedInputs = nextRunExcludedCondenseInputsByPlayer.get(playerId);
        if (excludedInputs == null) {
            return;
        }

        excludedInputs.remove(material);
        if (excludedInputs.isEmpty()) {
            nextRunExcludedCondenseInputsByPlayer.remove(playerId);
        }
    }

    public void clearAllCondenseInputsExcludedNextRun(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        nextRunExcludedCondenseInputsByPlayer.remove(playerId);
    }

    public boolean replaceCondenseInputExcludedPersistent(
            final UUID playerId,
            final Set<Material> materials
    ) {
        if (playerId == null) {
            return false;
        }

        Set<Material> sanitized = sanitizeMaterialSet(materials);
        if (playerExclusionStore == null) {
            getLogger().warning("Persistent exclusion store is not available.");
            return false;
        }

        try {
            playerExclusionStore.replacePersistentExclusions(playerId, sanitized);
        } catch (Exception ex) {
            getLogger().severe("Failed to replace persistent SQLite exclusions for player " + playerId
                    + ": " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }

        if (sanitized.isEmpty()) {
            excludedCondenseInputsByPlayer.remove(playerId);
        } else {
            excludedCondenseInputsByPlayer.put(playerId, sanitized);
        }

        return true;
    }

    public void replaceCondenseInputExcludedNextRun(
            final UUID playerId,
            final Set<Material> materials
    ) {
        if (playerId == null) {
            return;
        }

        Set<Material> sanitized = sanitizeMaterialSet(materials);
        if (sanitized.isEmpty()) {
            nextRunExcludedCondenseInputsByPlayer.remove(playerId);
        } else {
            nextRunExcludedCondenseInputsByPlayer.put(playerId, sanitized);
        }
    }

    private void reloadPersistentExclusionsFromStore() {
        excludedCondenseInputsByPlayer.clear();

        if (playerExclusionStore == null) {
            return;
        }

        try {
            excludedCondenseInputsByPlayer.putAll(playerExclusionStore.loadPersistentExclusions());
        } catch (Exception ex) {
            getLogger().severe("Failed to load persistent SQLite exclusions: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private Set<Material> copyMaterialSet(final Set<Material> materials) {
        if (materials == null || materials.isEmpty()) {
            return EnumSet.noneOf(Material.class);
        }

        return EnumSet.copyOf(materials);
    }

    private Set<Material> sanitizeMaterialSet(final Set<Material> materials) {
        Set<Material> sanitized = EnumSet.noneOf(Material.class);
        if (materials == null) {
            return sanitized;
        }

        for (Material material : materials) {
            if (material != null && material.isItem()) {
                sanitized.add(material);
            }
        }

        return sanitized;
    }

    public ActivationMode getActivationMode() {
        String rawMode = getConfig().getString("activation.mode", "COMMAND");
        ActivationMode mode = ActivationMode.fromString(rawMode);
        if (mode == null) {
            return ActivationMode.COMMAND;
        }

        return mode;
    }

    public boolean isCondenserItemModeEnabled() {
        return getActivationMode() == ActivationMode.CONDENSER_ITEM;
    }

    public boolean isCondenserCommandAllowed() {
        return getConfig().getBoolean("activation.condenser_item.allow_command_with_item", false);
    }

    public ItemStack createCondenserItem() {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(DEFAULT_CONDENSER_NAME));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(condenserItemKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isCondenserItem(final ItemStack item) {
        if (item == null || item.getType() != Material.CRAFTING_TABLE || !item.hasItemMeta()) {
            return false;
        }

        Byte marker = item.getItemMeta()
                .getPersistentDataContainer()
                .get(condenserItemKey, PersistentDataType.BYTE);

        return marker != null && marker == (byte) 1;
    }

    public boolean hasCondenserItem(final ItemStack[] contents) {
        if (contents == null) {
            return false;
        }

        for (ItemStack item : contents) {
            if (isCondenserItem(item)) {
                return true;
            }
        }

        return false;
    }

    public int cleanupCondenserItems(final Player player) {
        if (player == null || isCondenserItemModeEnabled()) {
            return 0;
        }

        ItemStack[] contents = player.getInventory().getContents();
        int removed = removeCondenserItems(contents);

        if (removed > 0) {
            player.getInventory().setContents(contents);
        }

        ItemStack cursorItem = player.getItemOnCursor();
        if (isCondenserItem(cursorItem)) {
            removed += cursorItem.getAmount();
            player.setItemOnCursor(null);
        }

        if (removed > 0) {
            player.updateInventory();
        }

        return removed;
    }

    public void cleanupCondenserItemsForOnlinePlayers() {
        if (isCondenserItemModeEnabled()) {
            return;
        }

        int totalRemoved = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            totalRemoved += cleanupCondenserItems(player);
        }

        if (totalRemoved > 0) {
            getLogger().info("Removed " + totalRemoved
                    + " leftover Condenser item(s) because COMMAND mode is active.");
        }
    }

    private void updateConfigIfNeeded() {
        FileConfiguration config = getConfig();
        int existingVersion = config.getInt("config-version", 0);

        try (InputStream input = getResource("config.yml")) {
            if (input == null) {
                getLogger().warning("Could not load bundled config.yml from plugin jar.");
                return;
            }

            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8)
            );

            config.setDefaults(defaultConfig);
            config.options().copyDefaults(true);

            if (existingVersion < 1) {
                runConfigMigrationsFromPreV1(config);
            }

            config.set("config-version", CURRENT_CONFIG_VERSION);
            saveConfig();

            if (existingVersion < CURRENT_CONFIG_VERSION) {
                getLogger().info("Updated config.yml from version " + existingVersion
                        + " to version " + CURRENT_CONFIG_VERSION + ".");
            }
        } catch (Exception ex) {
            getLogger().severe("Failed to update config.yml: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void runConfigMigrationsFromPreV1(final FileConfiguration config) {
        if (!config.isSet("requirements.crafting_table_mode")) {
            boolean requireCraftingTable = config.getBoolean("requirements.require_crafting_table", false);
            boolean allowInventoryTable = config.getBoolean("requirements.allow_crafting_table_in_inventory", true);

            String mode;
            if (!requireCraftingTable) {
                mode = "DISABLED";
            } else if (allowInventoryTable) {
                mode = "INVENTORY_OR_NEARBY";
            } else {
                mode = "NEARBY_ONLY";
            }

            config.set("requirements.crafting_table_mode", mode);

            if (config.isSet("requirements.require_crafting_table")) {
                config.set("requirements.require_crafting_table", null);
            }
            if (config.isSet("requirements.allow_crafting_table_in_inventory")) {
                config.set("requirements.allow_crafting_table_in_inventory", null);
            }

            getLogger().info("Migrated legacy crafting table settings to requirements.crafting_table_mode.");
        }
    }

    private void validateConfig() {
        disabledCondenseInputs.clear();
        validateActivationMode();
        validateCraftingTableMode();
        validateCondenseRecipes();
    }

    private void validateActivationMode() {
        String mode = getConfig().getString("activation.mode", "COMMAND");
        if (mode == null || mode.isBlank()) {
            getLogger().warning("Config warning: activation.mode is missing or blank. Defaulting to COMMAND.");
            return;
        }

        if (ActivationMode.fromString(mode) == null) {
            getLogger().warning("Config warning: invalid activation.mode value '" + mode
                    + "'. Valid values are COMMAND and CONDENSER_ITEM.");
        }
    }

    private void validateCraftingTableMode() {
        String mode = getConfig().getString("requirements.crafting_table_mode", "DISABLED");
        if (mode == null || mode.isBlank()) {
            getLogger().warning("Config warning: requirements.crafting_table_mode is missing or blank. Default behavior may be used.");
            return;
        }

        switch (mode.trim().toUpperCase()) {
            case "DISABLED", "INVENTORY_ONLY", "NEARBY_ONLY", "INVENTORY_OR_NEARBY" -> {
                // valid
            }
            default -> getLogger().warning(
                    "Config warning: invalid requirements.crafting_table_mode value '" + mode
                            + "'. Valid values are DISABLED, INVENTORY_ONLY, NEARBY_ONLY, INVENTORY_OR_NEARBY."
            );
        }
    }

    private void validateCondenseRecipes() {
        ConfigurationSection condenseSection = getConfig().getConfigurationSection("condense");
        if (condenseSection == null) {
            getLogger().warning("Config warning: missing 'condense' section in config.yml");
            return;
        }

        boolean warnIfNotReversible = getConfig().getBoolean("validation.warn_if_not_reversible", true);
        boolean disableNonReversible = getConfig().getBoolean("validation.disable_non_reversible_recipes", false);

        for (String inputKey : condenseSection.getKeys(false)) {
            ConfigurationSection rule = condenseSection.getConfigurationSection(inputKey);

            if (rule == null) {
                getLogger().warning("Config warning: condense entry '" + inputKey + "' is not a valid section.");
                continue;
            }

            Material input = Material.matchMaterial(inputKey);
            if (input == null) {
                getLogger().warning("Config warning: invalid condense input material '" + inputKey + "'");
            }

            String outputName = rule.getString("output");
            Material output = outputName == null ? null : Material.matchMaterial(outputName);
            if (outputName == null || output == null) {
                getLogger().warning("Config warning: invalid condense output material for '" + inputKey + "': '" + outputName + "'");
            }

            int ratioIn = rule.getInt("ratio_in");
            int ratioOut = rule.getInt("ratio_out");

            if (ratioIn <= 0) {
                getLogger().warning("Config warning: condense entry '" + inputKey + "' has invalid ratio_in=" + ratioIn + ". It must be greater than 0.");
            }

            if (ratioOut <= 0) {
                getLogger().warning("Config warning: condense entry '" + inputKey + "' has invalid ratio_out=" + ratioOut + ". It must be greater than 0.");
            }

            if (input != null && !input.isItem()) {
                getLogger().warning("Config warning: condense input material '" + inputKey + "' is not an item material.");
            }

            if (output != null && !output.isItem()) {
                getLogger().warning("Config warning: condense output material '" + outputName + "' is not an item material.");
            }

            if (input == null || output == null || ratioIn <= 0 || ratioOut <= 0 || !input.isItem() || !output.isItem()) {
                continue;
            }

            boolean reversible = isRecipeReversible(input, output, ratioIn, ratioOut);

            if (!reversible && warnIfNotReversible) {
                getLogger().warning("Config warning: condense recipe '" + input + " -> " + output
                        + "' does not appear reversible in the currently loaded server recipes.");
            }

            if (!reversible && disableNonReversible) {
                disabledCondenseInputs.add(input);
                getLogger().warning("Config warning: disabling non-reversible condense recipe for input '" + input + "'.");
            }
        }

        if (!disabledCondenseInputs.isEmpty()) {
            getLogger().warning("Disabled " + disabledCondenseInputs.size()
                    + " non-reversible condense recipe(s): " + disabledCondenseInputs);
        }
    }

    private boolean isRecipeReversible(
            final Material input,
            final Material output,
            final int ratioIn,
            final int ratioOut
    ) {
        boolean forwardExists = hasSimpleConversionRecipe(input, output, ratioIn, ratioOut);
        boolean reverseExists = hasSimpleConversionRecipe(output, input, ratioOut, ratioIn);

        if (forwardExists && reverseExists) {
            return true;
        }

        // Exception: if the forward recipe exists and the configured output belongs
        // to a broader variant family (for example colored wool), allow the validator
        // to consider it acceptable even if the reverse recipe is not exact-material reversible.
        if (forwardExists && !reverseExists && materialHasVariants(output)) {
            return true;
        }

        return false;
    }

    private boolean materialHasVariants(final Material material) {
        String name = material.name();

        // Quick explicit support for common Minecraft variant families that are
        // often grouped by tags in datapacks.
        if (name.endsWith("_WOOL")
                || name.endsWith("_CARPET")
                || name.endsWith("_BED")
                || name.endsWith("_BANNER")
                || name.endsWith("_CONCRETE")
                || name.endsWith("_CONCRETE_POWDER")
                || name.endsWith("_STAINED_GLASS")
                || name.endsWith("_STAINED_GLASS_PANE")
                || name.endsWith("_TERRACOTTA")
                || name.endsWith("_SHULKER_BOX")
                || name.endsWith("_CANDLE")
                || name.endsWith("_DYE")) {
            return true;
        }

        // Generic fallback: if another material exists with the same suffix after
        // the first underscore, treat this as a variant family.
        int underscoreIndex = name.indexOf('_');
        if (underscoreIndex <= 0 || underscoreIndex >= name.length() - 1) {
            return false;
        }

        String suffix = name.substring(underscoreIndex);

        for (Material other : Material.values()) {
            if (other == material) {
                continue;
            }

            String otherName = other.name();
            if (otherName.endsWith(suffix)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasSimpleConversionRecipe(
            final Material source,
            final Material result,
            final int sourceCount,
            final int resultCount
    ) {
        List<Recipe> recipes = Bukkit.getRecipesFor(new ItemStack(result, resultCount));
        for (Recipe recipe : recipes) {
            if (matchesSimpleConversionRecipe(recipe, source, result, sourceCount, resultCount)) {
                return true;
            }
        }

        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (matchesSimpleConversionRecipe(recipe, source, result, sourceCount, resultCount)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesSimpleConversionRecipe(
            final Recipe recipe,
            final Material source,
            final Material result,
            final int sourceCount,
            final int resultCount
    ) {
        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
            return false;
        }

        ItemStack recipeResult = craftingRecipe.getResult();
        if (recipeResult.getType() != result || recipeResult.getAmount() != resultCount) {
            return false;
        }

        if (craftingRecipe instanceof ShapedRecipe shapedRecipe) {
            return matchesShapedSingleMaterial(shapedRecipe, source, sourceCount);
        }

        if (craftingRecipe instanceof ShapelessRecipe shapelessRecipe) {
            return matchesShapelessSingleMaterial(shapelessRecipe, source, sourceCount);
        }

        return false;
    }

    private boolean matchesShapedSingleMaterial(
            final ShapedRecipe recipe,
            final Material source,
            final int expectedCount
    ) {
        int count = 0;

        for (RecipeChoice choice : recipe.getChoiceMap().values()) {
            if (choice == null) {
                continue;
            }

            if (!matchesSingleMaterialChoice(choice, source)) {
                return false;
            }

            count++;
        }

        return count == expectedCount;
    }

    private boolean matchesShapelessSingleMaterial(
            final ShapelessRecipe recipe,
            final Material source,
            final int expectedCount
    ) {
        List<RecipeChoice> choices = recipe.getChoiceList();
        if (choices.size() != expectedCount) {
            return false;
        }

        for (RecipeChoice choice : choices) {
            if (choice == null || !matchesSingleMaterialChoice(choice, source)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesSingleMaterialChoice(final RecipeChoice choice, final Material source) {
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            return materialChoice.getChoices().size() == 1
                    && materialChoice.getChoices().contains(source);
        }

        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            return exactChoice.getChoices().size() == 1
                    && exactChoice.getChoices().getFirst().getType() == source
                    && exactChoice.getChoices().getFirst().getAmount() == 1;
        }

        return false;
    }

    private int removeCondenserItems(final ItemStack[] contents) {
        if (contents == null) {
            return 0;
        }

        int removed = 0;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isCondenserItem(item)) {
                continue;
            }

            removed += item.getAmount();
            contents[i] = null;
        }

        return removed;
    }

    private void refreshCondenserRecipe() {
        unregisterCondenserRecipe();

        if (!isCondenserItemModeEnabled()) {
            return;
        }

        ShapedRecipe recipe = createCondenserRecipeFromConfig();
        if (recipe == null) {
            getLogger().warning("Condenser item mode is enabled, but the configured recipe is invalid. "
                    + "The Condenser recipe was not registered.");
            return;
        }

        Bukkit.addRecipe(recipe);
        getLogger().info("Registered Condenser crafting recipe.");
    }

    private void unregisterCondenserRecipe() {
        Bukkit.removeRecipe(condenserRecipeKey);
    }

    private ShapedRecipe createCondenserRecipeFromConfig() {
        ConfigurationSection recipeSection = getConfig().getConfigurationSection("activation.condenser_item.recipe");
        if (recipeSection == null) {
            getLogger().warning("Config warning: missing activation.condenser_item.recipe section.");
            return null;
        }

        List<String> shapeList = recipeSection.getStringList("shape");
        if (shapeList.isEmpty() || shapeList.size() > 3) {
            getLogger().warning("Config warning: activation.condenser_item.recipe.shape must contain 1 to 3 rows.");
            return null;
        }

        String[] shape = new String[shapeList.size()];
        Integer expectedWidth = null;
        Set<Character> usedKeys = new HashSet<>();

        for (int i = 0; i < shapeList.size(); i++) {
            String row = shapeList.get(i);
            if (row == null || row.isEmpty() || row.length() > 3) {
                getLogger().warning("Config warning: invalid Condenser recipe row '" + row
                        + "'. Each row must be 1 to 3 characters long.");
                return null;
            }

            if (expectedWidth == null) {
                expectedWidth = row.length();
            } else if (row.length() != expectedWidth) {
                getLogger().warning("Config warning: all Condenser recipe rows must have the same width.");
                return null;
            }

            shape[i] = row;
            for (char character : row.toCharArray()) {
                if (character != ' ') {
                    usedKeys.add(character);
                }
            }
        }

        ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
        if (ingredientsSection == null) {
            getLogger().warning("Config warning: missing activation.condenser_item.recipe.ingredients section.");
            return null;
        }

        ShapedRecipe recipe;
        try {
            recipe = new ShapedRecipe(condenserRecipeKey, createCondenserItem());
            recipe.shape(shape);
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Config warning: failed to build Condenser recipe shape: " + ex.getMessage());
            return null;
        }

        for (char key : usedKeys) {
            String materialName = ingredientsSection.getString(String.valueOf(key));
            Material ingredient = materialName == null ? null : Material.matchMaterial(materialName);

            if (ingredient == null || !ingredient.isItem()) {
                getLogger().warning("Config warning: invalid Condenser recipe ingredient '" + key
                        + "' = '" + materialName + "'.");
                return null;
            }

            recipe.setIngredient(key, ingredient);
        }

        return recipe;
    }

}
