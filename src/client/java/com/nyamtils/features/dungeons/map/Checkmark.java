package com.nyamtils.features.dungeons.map;

/**
 * A room's clear state, decoded from the small marker Hypixel paints in the centre of each room
 * square. Ordered by progression so {@code state.weight} can be compared (e.g. "at least cleared").
 */
public enum Checkmark {
    FAILED(-1),     // red X
    UNOPENED(0),    // not on the map yet
    ADJACENT(1),    // visible but not entered
    OPENED(2),      // entered, no checkmark
    CLEARED(3),     // white tick
    COMPLETED(4);   // green tick

    public final int weight;

    Checkmark(int weight) { this.weight = weight; }
}
