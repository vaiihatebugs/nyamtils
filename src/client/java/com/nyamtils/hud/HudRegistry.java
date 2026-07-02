package com.nyamtils.hud;

import com.nyamtils.features.dungeons.map.DungeonMapHud;
import com.nyamtils.hud.elements.DungeonScoreEditable;
import com.nyamtils.hud.elements.SpotifyHud;

import java.util.ArrayList;
import java.util.List;

/**
 * Central registry of {@link EditableHud} elements that the {@link HudEditorScreen} can move.
 *
 * <p>Adding a new movable HUD = write one {@link EditableHud} implementation and add a single
 * {@code register(...)} line in {@link #init()}. Nothing else in the editor needs to change.
 */
public final class HudRegistry {

    private HudRegistry() {}

    private static final List<EditableHud> HUDS = new ArrayList<>();

    /** Registers every editable HUD. Called once during client init. */
    public static void init() {
        register(new DungeonScoreEditable());
        if (DungeonMapHud.getInstance() != null) register(DungeonMapHud.getInstance());
        if (SpotifyHud.getInstance() != null) register(SpotifyHud.getInstance());
    }

    public static void register(EditableHud hud) {
        HUDS.add(hud);
    }

    public static List<EditableHud> all() {
        return HUDS;
    }
}
