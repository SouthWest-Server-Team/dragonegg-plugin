package com.xinantown.dragon;

/**
 * Represents the type of location where the dragon egg currently resides.
 */
public enum EggLocationType {
    /** Egg is placed as a block in the world */
    BLOCK,
    /** Egg is a dropped item entity in the world */
    ITEM_ENTITY,
    /** Egg is in a player's inventory */
    PLAYER_INVENTORY,
    /** Egg is inside a container (chest, hopper, barrel, etc.) */
    CONTAINER,
    /** Egg is displayed in an item frame */
    ITEM_FRAME,
    /** No dragon egg exists */
    NONE
}
