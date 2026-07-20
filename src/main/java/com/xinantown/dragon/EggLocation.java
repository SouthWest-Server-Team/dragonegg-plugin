package com.xinantown.dragon;

import java.util.Map;

public sealed interface EggLocation
        permits BlockLocation, ItemEntityLocation, PlayerInventoryLocation,
                ContainerLocation, ItemFrameLocation {

    boolean verify();

    void remove();

    String describe();

    boolean matches(EggLocation other);

    /** Returns the type discriminator string used for serialization. */
    String typeName();

    /** Append type-specific detail lines to the given StringBuilder. */
    void appendDetailsTo(StringBuilder sb);

    Map<String, Object> serialize();

    static EggLocation deserialize(Map<String, Object> map) {
        String type = (String) map.get("type");
        return switch (type) {
            case "BLOCK" -> new BlockLocation(
                    (String) map.get("world"),
                    (int) map.get("x"),
                    (int) map.get("y"),
                    (int) map.get("z"));
            case "ITEM_ENTITY" -> new ItemEntityLocation(
                    (String) map.get("world"),
                    (int) map.getOrDefault("x", 0),
                    (int) map.getOrDefault("y", 0),
                    (int) map.getOrDefault("z", 0),
                    java.util.UUID.fromString((String) map.get("entity-uuid")));
            case "PLAYER_INVENTORY" -> new PlayerInventoryLocation(
                    java.util.UUID.fromString((String) map.get("player-uuid")));
            case "CONTAINER" -> new ContainerLocation(
                    (String) map.get("world"),
                    (int) map.get("x"),
                    (int) map.get("y"),
                    (int) map.get("z"),
                    (String) map.getOrDefault("container-type", "容器"));
            case "ITEM_FRAME" -> new ItemFrameLocation(
                    (String) map.get("world"),
                    (int) map.getOrDefault("x", 0),
                    (int) map.getOrDefault("y", 0),
                    (int) map.getOrDefault("z", 0),
                    java.util.UUID.fromString((String) map.get("entity-uuid")));
            default -> throw new IllegalArgumentException("Unknown EggLocation type: " + type);
        };
    }
}
