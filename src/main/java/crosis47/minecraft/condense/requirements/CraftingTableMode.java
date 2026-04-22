package crosis47.minecraft.condense.requirements;

public enum CraftingTableMode {
    DISABLED,
    INVENTORY_ONLY,
    NEARBY_ONLY,
    INVENTORY_OR_NEARBY;

    public static CraftingTableMode fromString(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return CraftingTableMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}