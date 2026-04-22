package crosis47.minecraft.condense;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import crosis47.minecraft.condense.commands.CondenseCommand;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CondensePlugin extends JavaPlugin {

    private static final int CURRENT_CONFIG_VERSION = 1;

    private final Set<Material> disabledCondenseInputs = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfigIfNeeded();
        validateConfig();

        PluginCommand condenseCommand = Objects.requireNonNull(
                getCommand("condense"),
                "Command 'condense' is missing from plugin.yml"
        );
        condenseCommand.setExecutor(new CondenseCommand(this));

        getLogger().info("Condense Reforged enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Condense Reforged disabled.");
    }

    public boolean isCondenseInputDisabled(final Material material) {
        return disabledCondenseInputs.contains(material);
    }

    public Set<Material> getDisabledCondenseInputs() {
        return Collections.unmodifiableSet(disabledCondenseInputs);
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
        validateCraftingTableMode();
        validateCondenseRecipes();
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
        return forwardExists && reverseExists;
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

        for (ItemStack ingredient : recipe.getIngredientMap().values()) {
            if (ingredient == null) {
                continue;
            }

            if (ingredient.getType() != source || ingredient.getAmount() != 1) {
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
        List<ItemStack> ingredients = recipe.getIngredientList();
        if (ingredients.size() != expectedCount) {
            return false;
        }

        for (ItemStack ingredient : ingredients) {
            if (ingredient == null) {
                return false;
            }

            if (ingredient.getType() != source || ingredient.getAmount() != 1) {
                return false;
            }
        }

        return true;
    }
}