package com.nyamtils.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A HUD element whose on-screen position can be edited by dragging in the {@link HudEditorScreen}.
 * Adding a new movable HUD = add one implementation to {@link HudRegistry}.
 */
public interface EditableHud {

    String name();

    /** Whether this element is currently active (shown in-game and in the editor). */
    boolean isEnabled();

    int getX(int screenWidth);

    int getY(int screenHeight);

    /** Persists a new top-left position (absolute screen pixels). */
    void setPos(int x, int y);

    int width();

    int height();

    /** Draws a representative preview at (x, y) for the editor. */
    void renderPreview(GuiGraphicsExtractor gui, int x, int y);

    // ── Optional resize support (drag the bottom-right corner, or scroll, in the editor) ──

    /** Whether this element can be resized (corner-drag and scroll-wheel) in the editor. */
    default boolean resizable() { return false; }

    /**
     * Persists a new size. Width and height are independent — implementations that must stay
     * square (e.g. a spatial map) can just clamp height to width themselves. No-op unless
     * {@link #resizable()}.
     */
    default void setSize(int width, int height) {}

    default int minWidth() { return 40; }
    default int maxWidth() { return 320; }
    default int minHeight() { return 20; }
    default int maxHeight() { return 320; }

    /** Pixels added/removed per scroll-wheel notch while hovering this element in the editor. */
    default int scrollStep() { return 4; }
}
