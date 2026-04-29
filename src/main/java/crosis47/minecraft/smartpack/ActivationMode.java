package crosis47.minecraft.smartpack;

public enum ActivationMode {
    COMMAND,
    SMART_PACKER_ITEM;

    public static ActivationMode fromString(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            String normalized = value.trim().toUpperCase();
            return ActivationMode.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
