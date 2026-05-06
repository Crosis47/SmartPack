package crosis47.minecraft.smartpack;

import crosis47.minecraft.smartpack.commands.PackCommand;
import crosis47.minecraft.smartpack.storage.PlayerExclusionStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimpleBarChart;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

public final class SmartPack extends JavaPlugin {

    private static final int CURRENT_CONFIG_VERSION = 16;
    private static final int BSTATS_PLUGIN_ID = 31132;
    private static final String DEFAULT_SMART_PACKER_NAME = "Smart Packer";

    private final Set<Material> disabledPackInputs = new HashSet<>();
    private final Map<UUID, Set<Material>> excludedPackInputsByPlayer = new HashMap<>();
    private final Map<UUID, Set<Material>> nextRunExcludedPackInputsByPlayer = new HashMap<>();
    private final Map<UUID, Boolean> autoPackEnabledByPlayer = new HashMap<>();

    private NamespacedKey smartPackerRecipeKey;
    private NamespacedKey smartPackerItemKey;
    private PlayerExclusionStore playerExclusionStore;

    @Override
    public void onEnable() {
        smartPackerRecipeKey = new NamespacedKey(this, "smart_packer");
        smartPackerItemKey = new NamespacedKey(this, "smart_packer_item");

        saveDefaultConfig();
        updateConfigIfNeeded();
        validateConfig();
        try {
            playerExclusionStore = new PlayerExclusionStore(
                    this,
                    new File(getDataFolder(), "player-exclusions.db")
            );
            playerExclusionStore.initialize();
            reloadPersistentExclusionsFromStore();
            reloadPersistentAutoPackPreferencesFromStore();
        } catch (Exception ex) {
            getLogger().severe("Failed to initialize SQLite player exclusion storage: " + ex.getMessage());
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PluginCommand packCommand = Objects.requireNonNull(
                getCommand("pack"),
                "Command 'pack' is missing from plugin.yml"
        );

        PackCommand commandHandler = new PackCommand(this);
        packCommand.setExecutor(commandHandler);
        packCommand.setTabCompleter(commandHandler);
        getServer().getPluginManager().registerEvents(commandHandler, this);

        refreshSmartPackerRecipe();
        cleanupSmartPackerItemsForOnlinePlayers();
        startMetrics();

        getLogger().info("SmartPack enabled.");
    }

    @Override
    public void onDisable() {
        unregisterSmartPackerRecipe();
        if (playerExclusionStore != null) {
            playerExclusionStore.shutdown();
        }
        getLogger().info("SmartPack disabled.");
    }

    private void startMetrics() {
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        addCustomMetricsCharts(metrics);
    }

    private void addCustomMetricsCharts(final Metrics metrics) {
        metrics.addCustomChart(new SimplePie("activation_mode", () -> getActivationMode().name()));
        metrics.addCustomChart(new SimplePie("crafting_table_requirement", this::getCraftingTableRequirementMetric));
        metrics.addCustomChart(new SimplePie("auto_pack_state", this::getAutoPackStateMetric));
        metrics.addCustomChart(new SimplePie("auto_pack_default_preference", () -> booleanMetric(
                getConfig().getBoolean("auto_pack.default_enabled", false)
        )));
        metrics.addCustomChart(new SimplePie("chest_pack_mode", this::getChestPackModeMetric));
        metrics.addCustomChart(new SimplePie("smart_packer_cooldown_mode", () -> booleanMetric(
                isSmartPackerCooldownModeEnabled()
        )));
        metrics.addCustomChart(new SingleLineChart("configured_pack_recipes", this::countConfiguredPackRecipes));
        metrics.addCustomChart(new SingleLineChart("disabled_pack_recipes", disabledPackInputs::size));
        metrics.addCustomChart(new SimpleBarChart("auto_pack_triggers", this::getAutoPackTriggerMetric));
    }

    private String getCraftingTableRequirementMetric() {
        String mode = getConfig().getString("requirements.crafting_table_mode", "DISABLED");
        if (mode == null || mode.isBlank()) {
            return "DISABLED";
        }

        return switch (mode.trim().toUpperCase(Locale.ROOT)) {
            case "DISABLED", "INVENTORY_ONLY", "NEARBY_ONLY", "INVENTORY_OR_NEARBY" -> mode.trim().toUpperCase(Locale.ROOT);
            default -> "INVALID";
        };
    }

    private String getAutoPackStateMetric() {
        if (!getConfig().getBoolean("auto_pack.enabled", false)) {
            return "DISABLED";
        }

        if (isSmartPackerCooldownModeEnabled()) {
            return "DISABLED_BY_SMART_PACKER_COOLDOWN";
        }

        return isSmartPackerItemModeEnabled() ? "ENABLED_SMART_PACKER_ITEM" : "ENABLED_COMMAND";
    }

    private String getChestPackModeMetric() {
        if (!getConfig().getBoolean("chest_pack.enabled", true)) {
            return "DISABLED";
        }

        boolean commandEnabled = getConfig().getBoolean("chest_pack.command", true);
        boolean itemEnabled = getConfig().getBoolean("chest_pack.smart_packer_item", true);

        if (commandEnabled && itemEnabled) {
            return "COMMAND_AND_SMART_PACKER_ITEM";
        }
        if (commandEnabled) {
            return "COMMAND_ONLY";
        }
        if (itemEnabled) {
            return "SMART_PACKER_ITEM_ONLY";
        }

        return "NO_TRIGGERS_ENABLED";
    }

    private int countConfiguredPackRecipes() {
        ConfigurationSection packSection = getConfig().getConfigurationSection("pack");
        return packSection == null ? 0 : packSection.getKeys(false).size();
    }

    private Map<String, Integer> getAutoPackTriggerMetric() {
        Map<String, Integer> triggers = new LinkedHashMap<>();

        if (!getConfig().getBoolean("auto_pack.enabled", false)) {
            triggers.put("auto_pack_disabled", 1);
            return triggers;
        }

        putEnabledTrigger(triggers, "pickup", getConfig().getBoolean("auto_pack.triggers.pickup", true));
        putEnabledTrigger(triggers, "join", getConfig().getBoolean("auto_pack.triggers.join", false));
        putEnabledTrigger(triggers, "crafting_table_place", getConfig().getBoolean("auto_pack.triggers.crafting_table_place", true));
        putEnabledTrigger(triggers, "crafting_table_nearby", getConfig().getBoolean("auto_pack.triggers.crafting_table_nearby", true));

        if (triggers.isEmpty()) {
            triggers.put("no_triggers_enabled", 1);
        }

        return triggers;
    }

    private void putEnabledTrigger(final Map<String, Integer> triggers, final String trigger, final boolean enabled) {
        if (enabled) {
            triggers.put(trigger, 1);
        }
    }

    private String booleanMetric(final boolean value) {
        return value ? "ENABLED" : "DISABLED";
    }

    public void reloadPluginConfig() {
        reloadConfig();
        updateConfigIfNeeded();
        validateConfig();
        reloadPersistentExclusionsFromStore();
        reloadPersistentAutoPackPreferencesFromStore();
        refreshSmartPackerRecipe();
        cleanupSmartPackerItemsForOnlinePlayers();
    }

    public boolean isPackInputDisabled(final Material material) {
        return disabledPackInputs.contains(material);
    }

    public Set<Material> getDisabledPackInputs() {
        return Collections.unmodifiableSet(disabledPackInputs);
    }

    public boolean isPackInputExcluded(final UUID playerId, final Material material) {
        if (playerId == null || material == null) {
            return false;
        }

        Set<Material> excludedInputs = excludedPackInputsByPlayer.get(playerId);
        return excludedInputs != null && excludedInputs.contains(material);
    }

    public boolean isPackInputExcludedNextRun(final UUID playerId, final Material material) {
        if (playerId == null || material == null) {
            return false;
        }

        Set<Material> excludedInputs = nextRunExcludedPackInputsByPlayer.get(playerId);
        return excludedInputs != null && excludedInputs.contains(material);
    }

    public boolean isPackInputExcludedForRun(final UUID playerId, final Material material) {
        return isPackInputExcluded(playerId, material)
                || isPackInputExcludedNextRun(playerId, material);
    }

    public Set<Material> getPackInputExcludedPersistentSnapshot(final UUID playerId) {
        return copyMaterialSet(excludedPackInputsByPlayer.get(playerId));
    }

    public Set<Material> getPackInputExcludedNextRunSnapshot(final UUID playerId) {
        return copyMaterialSet(nextRunExcludedPackInputsByPlayer.get(playerId));
    }

    public boolean togglePackInputExcluded(final UUID playerId, final Material material) {
        return setPackInputExcluded(playerId, material, !isPackInputExcluded(playerId, material));
    }

    public boolean setPackInputExcluded(final UUID playerId, final Material material, final boolean excluded) {
        if (playerId == null || material == null) {
            return false;
        }

        Set<Material> excludedInputs = excludedPackInputsByPlayer.computeIfAbsent(
                playerId,
                ignored -> EnumSet.noneOf(Material.class)
        );

        if (excluded) {
            excludedInputs.add(material);
        } else {
            excludedInputs.remove(material);
        }

        if (excludedInputs.isEmpty()) {
            excludedPackInputsByPlayer.remove(playerId);
        }

        return excluded;
    }

    public void savePackInputExcludedPersistentAsync(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        if (playerExclusionStore == null) {
            getLogger().warning("Persistent exclusion store is not available.");
            return;
        }

        playerExclusionStore.replacePersistentExclusionsAsync(
                playerId,
                getPackInputExcludedPersistentSnapshot(playerId)
        );
    }

    public boolean isAutoPackEnabledForPlayer(final UUID playerId) {
        if (playerId == null) {
            return false;
        }

        return autoPackEnabledByPlayer.getOrDefault(
                playerId,
                getConfig().getBoolean("auto_pack.default_enabled", true)
        );
    }

    public boolean toggleAutoPackEnabledForPlayer(final UUID playerId) {
        return setAutoPackEnabledForPlayer(playerId, !isAutoPackEnabledForPlayer(playerId));
    }

    public boolean setAutoPackEnabledForPlayer(final UUID playerId, final boolean enabled) {
        if (playerId == null) {
            return false;
        }

        autoPackEnabledByPlayer.put(playerId, enabled);

        if (playerExclusionStore == null) {
            getLogger().warning("Persistent exclusion store is not available.");
            return enabled;
        }

        playerExclusionStore.setAutoPackEnabledAsync(playerId, enabled);
        return enabled;
    }

    public boolean togglePackInputExcludedNextRun(final UUID playerId, final Material material) {
        return setPackInputExcludedNextRun(playerId, material, !isPackInputExcludedNextRun(playerId, material));
    }

    public boolean setPackInputExcludedNextRun(final UUID playerId, final Material material, final boolean excluded) {
        if (playerId == null || material == null) {
            return false;
        }

        Set<Material> excludedInputs = nextRunExcludedPackInputsByPlayer.computeIfAbsent(
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
            nextRunExcludedPackInputsByPlayer.remove(playerId);
        }

        return excluded;
    }

    public void clearPackInputExcludedNextRun(final UUID playerId, final Material material) {
        if (playerId == null || material == null) {
            return;
        }

        Set<Material> excludedInputs = nextRunExcludedPackInputsByPlayer.get(playerId);
        if (excludedInputs == null) {
            return;
        }

        excludedInputs.remove(material);
        if (excludedInputs.isEmpty()) {
            nextRunExcludedPackInputsByPlayer.remove(playerId);
        }
    }

    public void clearAllPackInputsExcludedNextRun(final UUID playerId) {
        if (playerId == null) {
            return;
        }

        nextRunExcludedPackInputsByPlayer.remove(playerId);
    }

    public boolean replacePackInputExcludedPersistent(
            final UUID playerId,
            final Set<Material> materials
    ) {
        if (playerId == null) {
            return false;
        }

        Set<Material> sanitized = sanitizeMaterialSet(materials);

        if (sanitized.isEmpty()) {
            excludedPackInputsByPlayer.remove(playerId);
        } else {
            excludedPackInputsByPlayer.put(playerId, sanitized);
        }

        return true;
    }

    public void replacePackInputExcludedNextRun(
            final UUID playerId,
            final Set<Material> materials
    ) {
        if (playerId == null) {
            return;
        }

        Set<Material> sanitized = sanitizeMaterialSet(materials);
        if (sanitized.isEmpty()) {
            nextRunExcludedPackInputsByPlayer.remove(playerId);
        } else {
            nextRunExcludedPackInputsByPlayer.put(playerId, sanitized);
        }
    }

    private void reloadPersistentExclusionsFromStore() {
        excludedPackInputsByPlayer.clear();

        if (playerExclusionStore == null) {
            return;
        }

        try {
            playerExclusionStore.flushPendingWrites();
            excludedPackInputsByPlayer.putAll(playerExclusionStore.loadPersistentExclusions());
        } catch (Exception ex) {
            getLogger().severe("Failed to load persistent SQLite exclusions: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void reloadPersistentAutoPackPreferencesFromStore() {
        autoPackEnabledByPlayer.clear();

        if (playerExclusionStore == null) {
            return;
        }

        try {
            playerExclusionStore.flushPendingWrites();
            autoPackEnabledByPlayer.putAll(playerExclusionStore.loadAutoPackPreferences());
        } catch (Exception ex) {
            getLogger().severe("Failed to load persistent auto-pack preferences: " + ex.getMessage());
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

    public boolean isSmartPackerItemModeEnabled() {
        return getActivationMode() == ActivationMode.SMART_PACKER_ITEM;
    }

    public boolean isSmartPackerCommandAllowed() {
        return getConfig().getBoolean("activation.smart_packer_item.allow_command_with_item", false);
    }

    public boolean isSmartPackerCooldownModeEnabled() {
        return isSmartPackerItemModeEnabled()
                && getConfig().getBoolean("activation.smart_packer_item.cooldown.enabled", false);
    }

    public int getSmartPackerCooldownSeconds() {
        return Math.max(0, getConfig().getInt("activation.smart_packer_item.cooldown.seconds", 10));
    }

    public long getSmartPackerCooldownTicks() {
        return getSmartPackerCooldownSeconds() * 20L;
    }

    public ItemStack createSmartPackerItem() {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        updateSmartPackerItemLore(item, 0L);
        return item;
    }

    public boolean updateSmartPackerItemLore(final ItemStack item, final long remainingCooldownTicks) {
        if (item == null || item.getType() != Material.CRAFTING_TABLE) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        meta.displayName(Component.text(DEFAULT_SMART_PACKER_NAME, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-click to pack now.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Open a chest to pack that chest instead.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        if (isSmartPackerCooldownModeEnabled()) {
            lore.add(buildSmartPackerCooldownLore(remainingCooldownTicks));
        } else {
            lore.add(Component.text("Auto mode: shift-right-click to enable.", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Auto mode packs after item pickups.", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(smartPackerItemKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return true;
    }

    private Component buildSmartPackerCooldownLore(final long remainingCooldownTicks) {
        long seconds = Math.max(0L, (remainingCooldownTicks + 19L) / 20L);
        String cooldownText = seconds <= 0L ? "Ready" : formatCooldownSeconds(seconds);
        NamedTextColor cooldownColor = seconds <= 0L ? NamedTextColor.GREEN : NamedTextColor.YELLOW;

        return Component.text("Cooldown: ", NamedTextColor.GRAY)
                .append(Component.text(cooldownText, cooldownColor))
                .decoration(TextDecoration.ITALIC, false);
    }

    private String formatCooldownSeconds(final long seconds) {
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

    public boolean isSmartPackerItem(final ItemStack item) {
        if (item == null || item.getType() != Material.CRAFTING_TABLE || !item.hasItemMeta()) {
            return false;
        }

        Byte marker = item.getItemMeta()
                .getPersistentDataContainer()
                .get(smartPackerItemKey, PersistentDataType.BYTE);

        return marker != null && marker == (byte) 1;
    }

    public boolean hasSmartPackerItem(final ItemStack[] contents) {
        if (contents == null) {
            return false;
        }

        for (ItemStack item : contents) {
            if (isSmartPackerItem(item)) {
                return true;
            }
        }

        return false;
    }

    public int cleanupSmartPackerItems(final Player player) {
        if (player == null || isSmartPackerItemModeEnabled()) {
            return 0;
        }

        ItemStack[] contents = player.getInventory().getContents();
        int removed = removeSmartPackerItems(contents);

        if (removed > 0) {
            player.getInventory().setContents(contents);
        }

        ItemStack cursorItem = player.getItemOnCursor();
        if (isSmartPackerItem(cursorItem)) {
            removed += cursorItem.getAmount();
            player.setItemOnCursor(null);
        }

        if (removed > 0) {
            player.updateInventory();
        }

        return removed;
    }

    public void cleanupSmartPackerItemsForOnlinePlayers() {
        if (isSmartPackerItemModeEnabled()) {
            return;
        }

        int totalRemoved = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            totalRemoved += cleanupSmartPackerItems(player);
        }

        if (totalRemoved > 0) {
            getLogger().info("Removed " + totalRemoved
                    + " leftover Smart Packer item(s) because COMMAND mode is active.");
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

    private void validateConfig() {
        disabledPackInputs.clear();
        validateActivationMode();
        validateCraftingTableMode();
        validatePackRecipes();
    }

    private void validateActivationMode() {
        String mode = getConfig().getString("activation.mode", "COMMAND");
        if (mode == null || mode.isBlank()) {
            getLogger().warning("Config warning: activation.mode is missing or blank. Defaulting to COMMAND.");
            return;
        }

        if (ActivationMode.fromString(mode) == null) {
            getLogger().warning("Config warning: invalid activation.mode value '" + mode
                    + "'. Valid values are COMMAND and SMART_PACKER_ITEM.");
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

    private void validatePackRecipes() {
        ConfigurationSection packSection = getConfig().getConfigurationSection("pack");
        if (packSection == null) {
            getLogger().warning("Config warning: missing 'pack' section in config.yml");
            return;
        }

        boolean warnIfNotReversible = getConfig().getBoolean("validation.warn_if_not_reversible", true);
        boolean disableNonReversible = getConfig().getBoolean("validation.disable_non_reversible_recipes", false);

        for (String inputKey : packSection.getKeys(false)) {
            ConfigurationSection rule = packSection.getConfigurationSection(inputKey);

            if (rule == null) {
                getLogger().warning("Config warning: pack entry '" + inputKey + "' is not a valid section.");
                continue;
            }

            Material input = Material.matchMaterial(inputKey);
            if (input == null) {
                getLogger().warning("Config warning: invalid pack input material '" + inputKey + "'");
            }

            String outputName = rule.getString("output");
            Material output = outputName == null ? null : Material.matchMaterial(outputName);
            if (outputName == null || output == null) {
                getLogger().warning("Config warning: invalid pack output material for '" + inputKey + "': '" + outputName + "'");
            }

            int ratioIn = rule.getInt("ratio_in");
            int ratioOut = rule.getInt("ratio_out");

            if (ratioIn <= 0) {
                getLogger().warning("Config warning: pack entry '" + inputKey + "' has invalid ratio_in=" + ratioIn + ". It must be greater than 0.");
            }

            if (ratioOut <= 0) {
                getLogger().warning("Config warning: pack entry '" + inputKey + "' has invalid ratio_out=" + ratioOut + ". It must be greater than 0.");
            }

            if (input != null && !input.isItem()) {
                getLogger().warning("Config warning: pack input material '" + inputKey + "' is not an item material.");
            }

            if (output != null && !output.isItem()) {
                getLogger().warning("Config warning: pack output material '" + outputName + "' is not an item material.");
            }

            if (input == null || output == null || ratioIn <= 0 || ratioOut <= 0 || !input.isItem() || !output.isItem()) {
                continue;
            }

            boolean reversible = isRecipeReversible(input, output, ratioIn, ratioOut);

            if (!reversible && warnIfNotReversible) {
                getLogger().warning("Config warning: pack recipe '" + input + " -> " + output
                        + "' does not appear reversible in the currently loaded server recipes.");
            }

            if (!reversible && disableNonReversible) {
                disabledPackInputs.add(input);
                getLogger().warning("Config warning: disabling non-reversible pack recipe for input '" + input + "'.");
            }
        }

        if (!disabledPackInputs.isEmpty()) {
            getLogger().warning("Disabled " + disabledPackInputs.size()
                    + " non-reversible pack recipe(s): " + disabledPackInputs);
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

    private int removeSmartPackerItems(final ItemStack[] contents) {
        if (contents == null) {
            return 0;
        }

        int removed = 0;

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isSmartPackerItem(item)) {
                continue;
            }

            removed += item.getAmount();
            contents[i] = null;
        }

        return removed;
    }

    private void refreshSmartPackerRecipe() {
        unregisterSmartPackerRecipe();

        if (!isSmartPackerItemModeEnabled()) {
            return;
        }

        ShapedRecipe recipe = createSmartPackerRecipeFromConfig();
        if (recipe == null) {
            getLogger().warning("Smart Packer item mode is enabled, but the configured recipe is invalid. "
                    + "The Smart Packer recipe was not registered.");
            return;
        }

        Bukkit.addRecipe(recipe);
        getLogger().info("Registered Smart Packer crafting recipe.");
    }

    private void unregisterSmartPackerRecipe() {
        Bukkit.removeRecipe(smartPackerRecipeKey);
    }

    private ShapedRecipe createSmartPackerRecipeFromConfig() {
        ConfigurationSection recipeSection = getConfig().getConfigurationSection("activation.smart_packer_item.recipe");
        if (recipeSection == null) {
            getLogger().warning("Config warning: missing activation.smart_packer_item.recipe section.");
            return null;
        }

        List<String> shapeList = recipeSection.getStringList("shape");
        if (shapeList.isEmpty() || shapeList.size() > 3) {
            getLogger().warning("Config warning: activation.smart_packer_item.recipe.shape must contain 1 to 3 rows.");
            return null;
        }

        String[] shape = new String[shapeList.size()];
        Integer expectedWidth = null;
        Set<Character> usedKeys = new HashSet<>();

        for (int i = 0; i < shapeList.size(); i++) {
            String row = shapeList.get(i);
            if (row == null || row.isEmpty() || row.length() > 3) {
                getLogger().warning("Config warning: invalid Smart Packer recipe row '" + row
                        + "'. Each row must be 1 to 3 characters long.");
                return null;
            }

            if (expectedWidth == null) {
                expectedWidth = row.length();
            } else if (row.length() != expectedWidth) {
                getLogger().warning("Config warning: all Smart Packer recipe rows must have the same width.");
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
            getLogger().warning("Config warning: missing activation.smart_packer_item.recipe.ingredients section.");
            return null;
        }

        ShapedRecipe recipe;
        try {
            recipe = new ShapedRecipe(smartPackerRecipeKey, createSmartPackerItem());
            recipe.shape(shape);
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Config warning: failed to build Smart Packer recipe shape: " + ex.getMessage());
            return null;
        }

        for (char key : usedKeys) {
            String materialName = ingredientsSection.getString(String.valueOf(key));
            Material ingredient = materialName == null ? null : Material.matchMaterial(materialName);

            if (ingredient == null || !ingredient.isItem()) {
                getLogger().warning("Config warning: invalid Smart Packer recipe ingredient '" + key
                        + "' = '" + materialName + "'.");
                return null;
            }

            recipe.setIngredient(key, ingredient);
        }

        return recipe;
    }

}
