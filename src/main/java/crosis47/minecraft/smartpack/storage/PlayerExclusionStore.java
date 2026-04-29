package crosis47.minecraft.smartpack.storage;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class PlayerExclusionStore {

    private static final String LEGACY_MIGRATION_KEY = "legacy_yaml_migrated";

    private final JavaPlugin plugin;
    private final File databaseFile;
    private final File legacyYamlFile;
    private final ExecutorService writeExecutor;

    public PlayerExclusionStore(
            final JavaPlugin plugin,
            final File databaseFile,
            final File legacyYamlFile
    ) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
        this.legacyYamlFile = legacyYamlFile;
        this.writeExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable runnable) {
                Thread thread = new Thread(runnable, plugin.getName() + "-sqlite-writer");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public synchronized void initialize() throws SQLException {
        ensureDataDirectoryExists();

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("SQLite JDBC driver is not available.", ex);
        }

        try (Connection connection = openConnection()) {
            createSchema(connection);
            migrateLegacyYamlIfNeeded(connection);
        }
    }

    public synchronized Map<UUID, Set<Material>> loadPersistentExclusions() throws SQLException {
        Map<UUID, Set<Material>> exclusionsByPlayer = new HashMap<>();

        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT player_uuid, material FROM player_excluded_inputs ORDER BY player_uuid, material"
                );
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                String rawPlayerId = resultSet.getString("player_uuid");
                String rawMaterial = resultSet.getString("material");

                UUID playerId;
                try {
                    playerId = UUID.fromString(rawPlayerId);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Skipping invalid player UUID in player-exclusions.db: " + rawPlayerId);
                    continue;
                }

                Material material = Material.matchMaterial(rawMaterial);
                if (material == null || !material.isItem()) {
                    plugin.getLogger().warning("Skipping invalid excluded pack material in player-exclusions.db: "
                            + rawMaterial);
                    continue;
                }

                exclusionsByPlayer.computeIfAbsent(playerId, ignored -> EnumSet.noneOf(Material.class))
                        .add(material);
            }
        }

        return exclusionsByPlayer;
    }

    public synchronized Map<UUID, Boolean> loadAutoCondensePreferences() throws SQLException {
        Map<UUID, Boolean> preferencesByPlayer = new HashMap<>();

        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT player_uuid, enabled FROM player_auto_condense_preferences ORDER BY player_uuid"
                );
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                String rawPlayerId = resultSet.getString("player_uuid");

                UUID playerId;
                try {
                    playerId = UUID.fromString(rawPlayerId);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Skipping invalid player UUID in auto-pack preferences: "
                            + rawPlayerId);
                    continue;
                }

                preferencesByPlayer.put(playerId, resultSet.getInt("enabled") != 0);
            }
        }

        return preferencesByPlayer;
    }

    public synchronized void setPersistentExclusion(
            final UUID playerId,
            final Material material,
            final boolean excluded
    ) throws SQLException {
        try (Connection connection = openConnection()) {
            if (excluded) {
                insertPersistentExclusion(connection, playerId, material);
            } else {
                deletePersistentExclusion(connection, playerId, material);
            }
        }
    }

    public synchronized void replacePersistentExclusions(
            final UUID playerId,
            final Set<Material> materials
    ) throws SQLException {
        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                try (
                        PreparedStatement deleteStatement = connection.prepareStatement(
                                "DELETE FROM player_excluded_inputs WHERE player_uuid = ?"
                        )
                ) {
                    deleteStatement.setString(1, playerId.toString());
                    deleteStatement.executeUpdate();
                }

                for (Material material : materials) {
                    insertPersistentExclusion(connection, playerId, material);
                }

                connection.commit();
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    public void replacePersistentExclusionsAsync(
            final UUID playerId,
            final Set<Material> materials
    ) {
        Set<Material> snapshot = materials == null || materials.isEmpty()
                ? EnumSet.noneOf(Material.class)
                : EnumSet.copyOf(materials);

        writeExecutor.execute(() -> {
            try {
                replacePersistentExclusions(playerId, snapshot);
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to persist SQLite exclusions asynchronously for player "
                        + playerId + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    public synchronized void setAutoCondenseEnabled(
            final UUID playerId,
            final boolean enabled
    ) throws SQLException {
        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR REPLACE INTO player_auto_condense_preferences "
                                + "(player_uuid, enabled) VALUES (?, ?)"
                )
        ) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, enabled ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public void setAutoCondenseEnabledAsync(
            final UUID playerId,
            final boolean enabled
    ) {
        writeExecutor.execute(() -> {
            try {
                setAutoCondenseEnabled(playerId, enabled);
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to persist auto-pack preference asynchronously for player "
                        + playerId + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        });
    }

    public void flushPendingWrites() throws SQLException {
        try {
            Future<?> barrier = writeExecutor.submit(() -> {
                // Barrier task so callers can wait for earlier writes to finish.
            });
            barrier.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for pending SQLite writes to finish.", ex);
        } catch (ExecutionException ex) {
            throw new SQLException("Failed while waiting for pending SQLite writes to finish.", ex.getCause());
        }
    }

    public void shutdown() {
        writeExecutor.shutdown();
        try {
            if (!writeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            writeExecutor.shutdownNow();
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private void ensureDataDirectoryExists() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create plugin data folder for SQLite exclusions.");
        }
    }

    private void createSchema(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_excluded_inputs (
                        player_uuid TEXT NOT NULL,
                        material TEXT NOT NULL,
                        PRIMARY KEY (player_uuid, material)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS plugin_metadata (
                        metadata_key TEXT NOT NULL PRIMARY KEY,
                        metadata_value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_auto_condense_preferences (
                        player_uuid TEXT NOT NULL PRIMARY KEY,
                        enabled INTEGER NOT NULL CHECK (enabled IN (0, 1))
                    )
                    """);
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_player_excluded_inputs_player_uuid
                    ON player_excluded_inputs (player_uuid)
                    """);
        }
    }

    private void migrateLegacyYamlIfNeeded(final Connection connection) throws SQLException {
        if (isLegacyMigrationRecorded(connection)) {
            return;
        }

        if (!legacyYamlFile.exists()) {
            setLegacyMigrationRecorded(connection);
            return;
        }

        int migratedCount = 0;
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);

        try {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(legacyYamlFile);
            ConfigurationSection playersSection = configuration.getConfigurationSection("players");
            if (playersSection != null) {
                for (String playerKey : playersSection.getKeys(false)) {
                    UUID playerId;
                    try {
                        playerId = UUID.fromString(playerKey);
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Skipping invalid player UUID in legacy player-exclusions.yml: "
                                + playerKey);
                        continue;
                    }

                    List<String> excludedNames = playersSection.getStringList(playerKey + ".excluded_inputs");
                    for (String excludedName : excludedNames) {
                        Material material = Material.matchMaterial(excludedName);
                        if (material == null || !material.isItem()) {
                            plugin.getLogger().warning("Skipping invalid excluded pack material '"
                                    + excludedName + "' for player " + playerId + " during SQLite migration.");
                            continue;
                        }

                        insertPersistentExclusion(connection, playerId, material);
                        migratedCount++;
                    }
                }
            }

            setLegacyMigrationRecorded(connection);
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            throw new SQLException("Failed to migrate legacy player exclusions to SQLite.", ex);
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }

        if (migratedCount > 0) {
            plugin.getLogger().info("Migrated " + migratedCount
                    + " player exclusion record(s) from player-exclusions.yml to player-exclusions.db.");
        } else {
            plugin.getLogger().info("Legacy player-exclusions.yml was present but did not contain any valid exclusions.");
        }

        backupLegacyYamlFile();
    }

    private boolean isLegacyMigrationRecorded(final Connection connection) throws SQLException {
        try (
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT metadata_value FROM plugin_metadata WHERE metadata_key = ?"
                )
        ) {
            statement.setString(1, LEGACY_MIGRATION_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && "1".equals(resultSet.getString("metadata_value"));
            }
        }
    }

    private void setLegacyMigrationRecorded(final Connection connection) throws SQLException {
        try (
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR REPLACE INTO plugin_metadata (metadata_key, metadata_value) VALUES (?, ?)"
                )
        ) {
            statement.setString(1, LEGACY_MIGRATION_KEY);
            statement.setString(2, "1");
            statement.executeUpdate();
        }
    }

    private void insertPersistentExclusion(
            final Connection connection,
            final UUID playerId,
            final Material material
    ) throws SQLException {
        try (
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR IGNORE INTO player_excluded_inputs (player_uuid, material) VALUES (?, ?)"
                )
        ) {
            statement.setString(1, playerId.toString());
            statement.setString(2, material.name());
            statement.executeUpdate();
        }
    }

    private void deletePersistentExclusion(
            final Connection connection,
            final UUID playerId,
            final Material material
    ) throws SQLException {
        try (
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM player_excluded_inputs WHERE player_uuid = ? AND material = ?"
                )
        ) {
            statement.setString(1, playerId.toString());
            statement.setString(2, material.name());
            statement.executeUpdate();
        }
    }

    private void backupLegacyYamlFile() {
        File backupFile = new File(legacyYamlFile.getParentFile(), legacyYamlFile.getName() + ".bak");
        try {
            Files.move(
                    legacyYamlFile.toPath(),
                    backupFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );
            plugin.getLogger().info("Backed up legacy player exclusions file to " + backupFile.getName() + ".");
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to back up legacy player-exclusions.yml after SQLite migration: "
                    + ex.getMessage());
        }
    }
}
