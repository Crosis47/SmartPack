package crosis47.minecraft.smartpack.compatibility;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Optional API integrations; neither Geyser nor Floodgate is required to run SmartPack. */
public final class BedrockPlayerDetector {
    private final JavaPlugin plugin;
    private final Set<String> warnedIntegrations = new HashSet<>();

    public BedrockPlayerDetector(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isBedrockPlayer(final UUID playerId) {
        return query("floodgate", "org.geysermc.floodgate.api.FloodgateApi",
                "getInstance", "isFloodgatePlayer", playerId)
                || query("Geyser-Spigot", "org.geysermc.geyser.api.GeyserApi",
                "api", "isBedrockPlayer", playerId);
    }

    private boolean query(
            final String pluginName,
            final String apiClassName,
            final String instanceMethod,
            final String playerMethod,
            final UUID playerId
    ) {
        Plugin integration = plugin.getServer().getPluginManager().getPlugin(pluginName);
        if (integration == null || !integration.isEnabled()) {
            return false;
        }

        try {
            // Use the integration's loader so optional APIs also work with Paper's plugin isolation.
            Class<?> apiClass = Class.forName(apiClassName, true, integration.getClass().getClassLoader());
            Object api = apiClass.getMethod(instanceMethod).invoke(null);
            return api != null && Boolean.TRUE.equals(apiClass.getMethod(playerMethod, UUID.class).invoke(api, playerId));
        } catch (ReflectiveOperationException | LinkageError ex) {
            if (warnedIntegrations.add(pluginName)) {
                plugin.getLogger().warning("Could not detect Bedrock players using " + pluginName
                        + "; check its API compatibility. " + ex.getClass().getSimpleName());
            }
            return false;
        }
    }
}
