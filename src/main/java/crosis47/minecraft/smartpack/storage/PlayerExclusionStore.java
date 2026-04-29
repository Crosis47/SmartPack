package crosis47.minecraft.smartpack.storage;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.HashMap;
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

    private static final String AUTO_PACK_PREFERENCES_TABLE = "player_auto_pack_preferences";

    private final JavaPlugin plugin;
    private final File databaseFile;
    private final ExecutorService writeExecutor;

    public PlayerExclusionStore(
            final JavaPlugin plugin,
            final File databaseFile
    ) {
        this.plugin = plugin;
        this.databaseFile = databaseFile;
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

    public synchronized Map<UUID, Boolean> loadAutoPackPreferences() throws SQLException {
        Map<UUID, Boolean> preferencesByPlayer = new HashMap<>();

        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT player_uuid, enabled FROM " + AUTO_PACK_PREFERENCES_TABLE + " ORDER BY player_uuid"
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

    public synchronized void setAutoPackEnabled(
            final UUID playerId,
            final boolean enabled
    ) throws SQLException {
        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT OR REPLACE INTO " + AUTO_PACK_PREFERENCES_TABLE + " "
                                + "(player_uuid, enabled) VALUES (?, ?)"
                )
        ) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, enabled ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public void setAutoPackEnabledAsync(
            final UUID playerId,
            final boolean enabled
    ) {
        writeExecutor.execute(() -> {
            try {
                setAutoPackEnabled(playerId, enabled);
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
                    CREATE TABLE IF NOT EXISTS %s (
                        player_uuid TEXT NOT NULL PRIMARY KEY,
                        enabled INTEGER NOT NULL CHECK (enabled IN (0, 1))
                    )
                    """.formatted(AUTO_PACK_PREFERENCES_TABLE));
            statement.execute("""
                    CREATE INDEX IF NOT EXISTS idx_player_excluded_inputs_player_uuid
                    ON player_excluded_inputs (player_uuid)
                    """);
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
}
