package com.xinantown.dragon;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class EggLocationTest {

    // === BlockLocation ===

    @Test
    void blockLocation_serialize_returnsMapWithCorrectFields() {
        BlockLocation loc = new BlockLocation("world", 100, 64, 200);
        Map<String, Object> result = loc.serialize();

        assertEquals("BLOCK", result.get("type"));
        assertEquals("world", result.get("world"));
        assertEquals(100, result.get("x"));
        assertEquals(64, result.get("y"));
        assertEquals(200, result.get("z"));
    }

    @Test
    void blockLocation_describe_returnsChineseDescription() {
        BlockLocation loc = new BlockLocation("world", 100, 64, 200);
        assertEquals("龙蛋方块在 world (100,64,200)", loc.describe());
    }

    @Test
    void blockLocation_matches_sameLocation_returnsTrue() {
        BlockLocation a = new BlockLocation("world", 100, 64, 200);
        BlockLocation b = new BlockLocation("world", 100, 64, 200);
        assertTrue(a.matches(b));
    }

    @Test
    void blockLocation_matches_differentCoordinate_returnsFalse() {
        BlockLocation a = new BlockLocation("world", 100, 64, 200);
        BlockLocation b = new BlockLocation("world", 101, 64, 200);
        assertFalse(a.matches(b));
    }

    @Test
    void blockLocation_matches_containerAtSameCoord_returnsTrue() {
        BlockLocation a = new BlockLocation("world", 100, 64, 200);
        ContainerLocation b = new ContainerLocation("world", 100, 64, 200, "CHEST");
        assertTrue(a.matches(b));
    }

    @Test
    void blockLocation_matches_differentTypeDifferentCoord_returnsFalse() {
        BlockLocation a = new BlockLocation("world", 100, 64, 200);
        PlayerInventoryLocation b = new PlayerInventoryLocation(UUID.randomUUID());
        assertFalse(a.matches(b));
    }

    // === ItemEntityLocation ===

    @Test
    void itemEntityLocation_serialize_returnsMapWithCorrectFields() {
        UUID uuid = UUID.randomUUID();
        ItemEntityLocation loc = new ItemEntityLocation("world_nether", 10, 20, 30, uuid);
        Map<String, Object> result = loc.serialize();

        assertEquals("ITEM_ENTITY", result.get("type"));
        assertEquals("world_nether", result.get("world"));
        assertEquals(10, result.get("x"));
        assertEquals(20, result.get("y"));
        assertEquals(30, result.get("z"));
        assertEquals(uuid.toString(), result.get("entity-uuid"));
    }

    @Test
    void itemEntityLocation_describe_returnsChineseDescription() {
        UUID uuid = UUID.randomUUID();
        ItemEntityLocation loc = new ItemEntityLocation("world", 1, 2, 3, uuid);
        assertEquals("龙蛋掉落物在 world (1,2,3)", loc.describe());
    }

    @Test
    void itemEntityLocation_matches_sameUUID_returnsTrue() {
        UUID uuid = UUID.randomUUID();
        ItemEntityLocation a = new ItemEntityLocation("world", 10, 64, 10, uuid);
        ItemEntityLocation b = new ItemEntityLocation("other_world", 99, 99, 99, uuid);
        assertTrue(a.matches(b));
    }

    @Test
    void itemEntityLocation_matches_nearbyLocation_returnsTrue() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        ItemEntityLocation a = new ItemEntityLocation("world", 10, 64, 10, u1);
        ItemEntityLocation b = new ItemEntityLocation("world", 12, 65, 12, u2);
        assertTrue(a.matches(b)); // within 2 blocks
    }

    @Test
    void itemEntityLocation_matches_tooFar_returnsFalse() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        ItemEntityLocation a = new ItemEntityLocation("world", 10, 64, 10, u1);
        ItemEntityLocation b = new ItemEntityLocation("world", 20, 64, 10, u2);
        assertFalse(a.matches(b)); // > 2 blocks away on x
    }

    // === PlayerInventoryLocation ===

    @Test
    void playerInventoryLocation_serialize_returnsMapWithPlayerUUID() {
        UUID uuid = UUID.randomUUID();
        PlayerInventoryLocation loc = new PlayerInventoryLocation(uuid);
        Map<String, Object> result = loc.serialize();

        assertEquals("PLAYER_INVENTORY", result.get("type"));
        assertEquals(uuid.toString(), result.get("player-uuid"));
    }

    @Test
    void playerInventoryLocation_describe_formatsWithNamePlaceholder() {
        UUID uuid = UUID.randomUUID();
        PlayerInventoryLocation loc = new PlayerInventoryLocation(uuid);
        assertTrue(loc.describe().contains(uuid.toString()));
    }

    @Test
    void playerInventoryLocation_matches_samePlayerUUID_returnsTrue() {
        UUID uuid = UUID.randomUUID();
        PlayerInventoryLocation a = new PlayerInventoryLocation(uuid);
        PlayerInventoryLocation b = new PlayerInventoryLocation(uuid);
        assertTrue(a.matches(b));
    }

    @Test
    void playerInventoryLocation_matches_differentPlayerUUID_returnsFalse() {
        PlayerInventoryLocation a = new PlayerInventoryLocation(UUID.randomUUID());
        PlayerInventoryLocation b = new PlayerInventoryLocation(UUID.randomUUID());
        assertFalse(a.matches(b));
    }

    // === ContainerLocation ===

    @Test
    void containerLocation_serialize_returnsMapWithContainerType() {
        ContainerLocation loc = new ContainerLocation("world", 0, 64, 0, "CHEST");
        Map<String, Object> result = loc.serialize();

        assertEquals("CONTAINER", result.get("type"));
        assertEquals("world", result.get("world"));
        assertEquals(0, result.get("x"));
        assertEquals(64, result.get("y"));
        assertEquals(0, result.get("z"));
        assertEquals("CHEST", result.get("container-type"));
    }

    @Test
    void containerLocation_describe_returnsChineseDescription() {
        ContainerLocation loc = new ContainerLocation("world", 100, 64, 200, "箱子");
        assertEquals("龙蛋在 world (100,64,200) 的箱子 中", loc.describe());
    }

    @Test
    void containerLocation_matches_sameLocationAndBlockType_returnsTrue() {
        ContainerLocation a = new ContainerLocation("world", 1, 2, 3, "CHEST");
        BlockLocation b = new BlockLocation("world", 1, 2, 3);
        assertTrue(a.matches(b));
    }

    @Test
    void containerLocation_matches_sameCoordDifferentType_returnsFalse() {
        ContainerLocation a = new ContainerLocation("world", 1, 2, 3, "CHEST");
        PlayerInventoryLocation b = new PlayerInventoryLocation(UUID.randomUUID());
        assertFalse(a.matches(b));
    }

    // === ItemFrameLocation ===

    @Test
    void itemFrameLocation_serialize_returnsMapWithEntityUUID() {
        UUID uuid = UUID.randomUUID();
        ItemFrameLocation loc = new ItemFrameLocation("world", 5, 6, 7, uuid);
        Map<String, Object> result = loc.serialize();

        assertEquals("ITEM_FRAME", result.get("type"));
        assertEquals("world", result.get("world"));
        assertEquals(uuid.toString(), result.get("entity-uuid"));
    }

    @Test
    void itemFrameLocation_describe_returnsChineseDescription() {
        UUID uuid = UUID.randomUUID();
        ItemFrameLocation loc = new ItemFrameLocation("world", 5, 6, 7, uuid);
        assertEquals("龙蛋在 world (5,6,7) 的物品展示框中", loc.describe());
    }

    // === deserialize ===

    @Test
    void deserialize_block_returnsBlockLocation() {
        Map<String, Object> map = Map.of("type", "BLOCK", "world", "world", "x", 10, "y", 20, "z", 30);
        EggLocation loc = EggLocation.deserialize(map);
        assertInstanceOf(BlockLocation.class, loc);
        BlockLocation b = (BlockLocation) loc;
        assertEquals("world", b.worldName());
        assertEquals(10, b.x());
        assertEquals(20, b.y());
        assertEquals(30, b.z());
    }

    @Test
    void deserialize_itemEntity_returnsItemEntityLocation() {
        UUID uuid = UUID.randomUUID();
        Map<String, Object> map = Map.of(
                "type", "ITEM_ENTITY", "world", "nether",
                "x", 1, "y", 2, "z", 3, "entity-uuid", uuid.toString());
        EggLocation loc = EggLocation.deserialize(map);
        assertInstanceOf(ItemEntityLocation.class, loc);
        ItemEntityLocation ie = (ItemEntityLocation) loc;
        assertEquals("nether", ie.worldName());
        assertEquals(uuid, ie.entityUUID());
    }

    @Test
    void deserialize_playerInventory_returnsPlayerInventoryLocation() {
        UUID uuid = UUID.randomUUID();
        Map<String, Object> map = Map.of("type", "PLAYER_INVENTORY", "player-uuid", uuid.toString());
        EggLocation loc = EggLocation.deserialize(map);
        assertInstanceOf(PlayerInventoryLocation.class, loc);
        assertEquals(uuid, ((PlayerInventoryLocation) loc).playerUUID());
    }

    @Test
    void deserialize_container_returnsContainerLocation() {
        Map<String, Object> map = Map.of(
                "type", "CONTAINER", "world", "world",
                "x", 5, "y", 6, "z", 7, "container-type", "BARREL");
        EggLocation loc = EggLocation.deserialize(map);
        assertInstanceOf(ContainerLocation.class, loc);
        ContainerLocation c = (ContainerLocation) loc;
        assertEquals("BARREL", c.containerType());
        assertEquals(5, c.x());
    }

    @Test
    void deserialize_itemFrame_returnsItemFrameLocation() {
        UUID uuid = UUID.randomUUID();
        Map<String, Object> map = Map.of(
                "type", "ITEM_FRAME", "world", "end",
                "x", 0, "y", 60, "z", 0, "entity-uuid", uuid.toString());
        EggLocation loc = EggLocation.deserialize(map);
        assertInstanceOf(ItemFrameLocation.class, loc);
        assertEquals(uuid, ((ItemFrameLocation) loc).entityUUID());
    }
}
