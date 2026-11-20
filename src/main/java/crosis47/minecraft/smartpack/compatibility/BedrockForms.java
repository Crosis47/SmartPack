package crosis47.minecraft.smartpack.compatibility;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Uses the Cumulus API supplied by the installed form provider. */
public final class BedrockForms {
    public record Button(String label, String texturePath) { }

    private final JavaPlugin plugin;
    private final Set<String> warnedProviders = new HashSet<>();

    public BedrockForms(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean sendSimpleForm(
            final UUID playerId, final String title, final String content, final List<Button> buttons,
            final IntConsumer onSelect, final Runnable onDismiss
    ) {
        return send(playerId, loader -> createSimpleForm(loader, title, content, buttons, onSelect, onDismiss));
    }

    private boolean send(final UUID playerId, final FormFactory factory) {
        return send("Geyser-Spigot", "org.geysermc.geyser.api.GeyserApi", "api", "isBedrockPlayer",
                playerId, factory)
                || send("floodgate", "org.geysermc.floodgate.api.FloodgateApi", "getInstance", "isFloodgatePlayer",
                playerId, factory);
    }

    private boolean send(
            final String provider, final String apiName, final String accessor, final String playerCheck,
            final UUID playerId, final FormFactory factory
    ) {
        Plugin integration = plugin.getServer().getPluginManager().getPlugin(provider);
        if (integration == null || !integration.isEnabled()) {
            return false;
        }

        try {
            ClassLoader loader = integration.getClass().getClassLoader();
            Class<?> apiType = loader.loadClass(apiName);
            Object api = apiType.getMethod(accessor).invoke(null);
            if (api == null || !Boolean.TRUE.equals(apiType.getMethod(playerCheck, UUID.class).invoke(api, playerId))) {
                return false;
            }
            Object form = factory.create(loader);
            Class<?> formType = loader.loadClass("org.geysermc.cumulus.form.Form");
            return Boolean.TRUE.equals(apiType.getMethod("sendForm", UUID.class, formType).invoke(api, playerId, form));
        } catch (ReflectiveOperationException | LinkageError ex) {
            if (warnedProviders.add(provider)) {
                plugin.getLogger().warning("Could not send a Bedrock form through " + provider + ": " + ex);
            }
            return false;
        }
    }

    static Object createSimpleForm(
            final ClassLoader loader, final String title, final String content, final List<Button> buttons,
            final IntConsumer onSelect, final Runnable onDismiss
    ) throws ReflectiveOperationException {
        // The form and its API must come from the same provider class loader.
        Class<?> formType = loader.loadClass("org.geysermc.cumulus.form.SimpleForm");
        Class<?> builderType = loader.loadClass("org.geysermc.cumulus.form.SimpleForm$Builder");
        Object builder = formType.getMethod("builder").invoke(null);
        builderType.getMethod("title", String.class).invoke(builder, title);
        builderType.getMethod("content", String.class).invoke(builder, content);
        Class<?> imageType = loader.loadClass("org.geysermc.cumulus.util.FormImage$Type");
        Object pathType = imageType.getField("PATH").get(null);
        Method addButton = builderType.getMethod("button", String.class, imageType, String.class);
        for (Button button : buttons) {
            addButton.invoke(builder, button.label(), pathType, button.texturePath());
        }

        Class<?> responseType = loader.loadClass("org.geysermc.cumulus.response.SimpleFormResponse");
        Method clickedButton = responseType.getMethod("clickedButtonId");
        Consumer<Object> handler = response -> {
            try {
                onSelect.accept((int) clickedButton.invoke(response));
            } catch (ReflectiveOperationException ex) {
                onDismiss.run();
            }
        };
        builderType.getMethod("validResultHandler", Consumer.class).invoke(builder, handler);
        builderType.getMethod("closedOrInvalidResultHandler", Runnable.class).invoke(builder, onDismiss);
        return builderType.getMethod("build").invoke(builder);
    }

    @FunctionalInterface
    private interface FormFactory {
        Object create(ClassLoader loader) throws ReflectiveOperationException;
    }
}
