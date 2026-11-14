package org.example.smartglasses.hud;

/**
 * Screen anchor points for 2D HUD widgets in virtual 1920x1080 space.
 */
public enum Anchor {
    TOP_LEFT(0.0f, 0.0f),
    TOP_CENTER(0.5f, 0.0f),
    TOP_RIGHT(1.0f, 0.0f),
    CENTER_LEFT(0.0f, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1.0f, 0.5f),
    BOTTOM_LEFT(0.0f, 1.0f),
    BOTTOM_CENTER(0.5f, 1.0f),
    BOTTOM_RIGHT(1.0f, 1.0f);

    public final float factorX;
    public final float factorY;

    Anchor(float factorX, float factorY) {
        this.factorX = factorX;
        this.factorY = factorY;
    }

    public static Anchor fromName(String name) {
        if (name == null) return TOP_LEFT;
        try {
            return Anchor.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TOP_LEFT;
        }
    }

    public static Anchor fromOrdinal(int ordinal) {
        Anchor[] values = values();
        if (ordinal < 0 || ordinal >= values.length) return TOP_LEFT;
        return values[ordinal];
    }
}
