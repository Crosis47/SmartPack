package crosis47.minecraft.condense;

public enum ActivationMode {
    COMMAND,
    CONDENSER_ITEM;

    public static ActivationMode fromString(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return ActivationMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
