package mind.beta.util.render.theme;

public enum ClientTheme {
    MIDNIGHT("Midnight", 0xFF448BFF),
    OCEAN("Ocean", 0xFF55CBEA),
    ROSE("Rose", 0xFFEA88BE),
    EMERALD("Emerald", 0xFF6DD7AD),
    IVORY("Ivory", 0xFF597DE0),
    AMETHYST("Amethyst", 0xFFAF86F6),
    EMBER("Ember", 0xFFFF9567),
    SOLAR("Solar", 0xFFF3C65C),
    CYBER("Cyber", 0xFFA3F36A),
    CRIMSON("Crimson", 0xFFFA667C),
    AURORA("Aurora", 0xFF4CE1C7),
    NEBULA("Nebula", 0xFF8F70FF),
    CORAL("Coral", 0xFFFF7C8F),
    LAGOON("Lagoon", 0xFF36B5C7),
    BLOSSOM("Blossom", 0xFFFFB1D0),
    VOLCANIC("Volcanic", 0xFFFF6449),
    FOREST("Forest", 0xFF64C17B),
    GLACIER("Glacier", 0xFF98DFFD),
    PLUM("Plum", 0xFFC77DD8),
    SANDSTONE("Sandstone", 0xFFDDB77D),
    NOVA("Nova", 0xFFFFD266),
    GRAPHITE("Graphite", 0xFFAAB8CD),
    ELECTRIC("Electric", 0xFF5B79FF),
    DUSK("Dusk", 0xFFB68BE2);

    private final String displayName;
    private final int accent;

    ClientTheme(String displayName, int accent) {
        this.displayName = displayName;
        this.accent = accent;
    }

    public String displayName() {
        return displayName;
    }

    public int accent() {
        return accent;
    }

    public static ClientTheme fromName(String name) {
        for (ClientTheme theme : values()) {
            if (theme.name().equalsIgnoreCase(name) || theme.displayName.equalsIgnoreCase(name)) {
                return theme;
            }
        }
        return MIDNIGHT;
    }
}
