package com.explorermap.structure;

import com.explorermap.api.StructureStyle;
import com.explorermap.api.StructureVisibility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Each test uses a namespace of its own: registrations are global and the tests share one JVM.
class StructureApiTest {

    @Test
    void anUnregisteredModdedStructureIsWorkedOutFromItsPathAsBefore() {
        assertEquals("crypt", StructureIds.normalize("apitest_none", "crypt"));
        assertEquals("explorermap:pin", StructureIds.icon("apitest_none", "crypt"));
        assertEquals(0xFFFFFFFF, StructureIds.color("apitest_none", "crypt"));
        assertEquals(StructureVisibility.SURFACE, StructureIds.visibility("apitest_none", "crypt"));
        // A modded structure that happens to share a vanilla path keeps getting the vanilla look.
        assertEquals("explorermap:village", StructureIds.icon("apitest_none", "village_big"));
    }

    @Test
    void anExactRegistrationGivesTheStructureItsStyle() {
        StructureStyles.register("apitest_exact", "crypt", StructureStyle.builder()
                .type("crypt").icon("apitest_exact:crypt").color(0x88AA55).visibility(StructureVisibility.ANYWHERE).build());

        assertEquals("explorermap.structure.crypt", StructureIds.nameKey("apitest_exact", "crypt"));
        assertEquals("apitest_exact:crypt", StructureIds.icon("apitest_exact", "crypt"));
        assertEquals(0xFF88AA55, StructureIds.color("apitest_exact", "crypt"));
        assertEquals(StructureVisibility.ANYWHERE, StructureIds.visibility("apitest_exact", "crypt"));
        // Only that structure, and only in that namespace.
        assertEquals("explorermap:pin", StructureIds.icon("apitest_exact", "crypt_small"));
        assertEquals("explorermap:pin", StructureIds.icon("apitest_other", "crypt"));
    }

    @Test
    void aTypeAloneBorrowsTheVanillaLookOfThatType() {
        StructureStyles.register("apitest_borrow", "hamlet", StructureStyle.builder().type("village").build());

        assertEquals("village", StructureIds.normalize("apitest_borrow", "hamlet"));
        assertEquals("explorermap:village", StructureIds.icon("apitest_borrow", "hamlet"));
        assertEquals(0xFFFFFF55, StructureIds.color("apitest_borrow", "hamlet"));
        assertEquals("explorermap.structure.village", StructureIds.nameKey("apitest_borrow", "hamlet"));
    }

    @Test
    void aPrefixCoversAFamilyAndTheMostSpecificRegistrationWins() {
        StructureStyles.registerPrefix("apitest_prefix", "ruin_", StructureStyle.of("apitest_prefix:ruin", 0x112233));
        StructureStyles.registerPrefix("apitest_prefix", "ruin_big_", StructureStyle.of("apitest_prefix:big", 0x445566));
        StructureStyles.register("apitest_prefix", "ruin_big_one", StructureStyle.of("apitest_prefix:one", 0x778899));

        assertEquals("apitest_prefix:ruin", StructureIds.icon("apitest_prefix", "ruin_small"));
        assertEquals("apitest_prefix:big", StructureIds.icon("apitest_prefix", "ruin_big_two"));
        assertEquals("apitest_prefix:one", StructureIds.icon("apitest_prefix", "ruin_big_one"));
        assertEquals("explorermap:pin", StructureIds.icon("apitest_prefix", "other"));
        assertEquals("explorermap:pin", StructureIds.icon("apitest_prefix_2", "ruin_small"));
    }

    @Test
    void hiddenStructuresAreHidden() {
        StructureStyles.register("apitest_hide", "well", StructureStyle.hidden());
        assertEquals(StructureVisibility.HIDDEN, StructureIds.visibility("apitest_hide", "well"));
        assertEquals(StructureVisibility.SURFACE, StructureIds.visibility("apitest_hide", "temple"));
    }

    @Test
    void aStoredNameAlwaysMapsBackToTheTypeItWasMadeFrom() {
        // A path longer than the name allows used to be cut in the stored name but not when checking for duplicates,
        // so the same structure got a new waypoint every time it was scanned.
        String longPath = "a_structure_with_a_really_quite_long_id_indeed";
        String type = StructureIds.normalize("apitest_long", longPath);
        assertTrue(type.length() <= StructureStyle.MAX_TYPE_LENGTH);
        assertEquals(type, StructureIds.typeOfStoredName(StructureIds.nameKey("apitest_long", longPath)));
        assertEquals(type, StructureIds.typeOfStoredName(StructureIds.nameKey("apitest_long", longPath) + " (3)"));
    }

    @Test
    void aRegisteredTypeIsNotFoldedLikeAVanillaOne() {
        // "village_" would be folded into "village" for a vanilla id; a type a mod chose on purpose stands.
        StructureStyles.register("apitest_type", "ruins", StructureStyle.builder().type("village_ruins").build());
        assertEquals("village_ruins", StructureIds.normalize("apitest_type", "ruins"));
        assertEquals("village_ruins", StructureIds.typeOfStoredName(StructureIds.nameKey("apitest_type", "ruins")));
    }

    @Test
    void mapDecorationsMarkTheirStructure() {
        assertEquals(new StructureRef("minecraft", "buried_treasure"),
                StructureIds.structureForMapDecoration("minecraft", "red_x"));
        assertEquals(new StructureRef("minecraft", "village"),
                StructureIds.structureForMapDecoration("minecraft", "taiga_village"));

        StructureStyles.registerMapDecoration("apitest_deco", "crypt_marker", new StructureRef("apitest_deco", "crypt"));
        assertEquals(new StructureRef("apitest_deco", "crypt"),
                StructureIds.structureForMapDecoration("apitest_deco", "crypt_marker"));

        // Unregistered, it stands for itself, which still gets a readable name.
        assertEquals(new StructureRef("apitest_deco", "other_marker"),
                StructureIds.structureForMapDecoration("apitest_deco", "other_marker"));
        // A mod's decoration is not mistaken for a vanilla one of the same path.
        assertEquals(new StructureRef("apitest_deco", "mansion"),
                StructureIds.structureForMapDecoration("apitest_deco", "mansion"));
    }

    @Test
    void aStyleChecksItsPartsAndMakesTheColorOpaque() {
        assertThrows(IllegalArgumentException.class, () -> StructureStyle.builder().type("Not Valid").build());
        assertThrows(IllegalArgumentException.class,
                () -> StructureStyle.builder().type("x".repeat(StructureStyle.MAX_TYPE_LENGTH + 1)).build());
        assertThrows(IllegalArgumentException.class, () -> StructureStyle.builder().icon(" ").build());
        assertEquals(0xFF123456, (int) StructureStyle.builder().color(0x00123456).build().color());
        assertNull(StructureStyle.builder().build().color());
        assertFalse(StructureStyle.hidden().visibility() == StructureVisibility.SURFACE);
    }

    @Test
    void whatADataPackSaysTakesPrecedenceOverWhatAModRegistered() {
        StructureStyles.register("apitest_tags", "well", StructureStyle.hidden());

        // Registered as hidden, and nothing from a data pack: hidden.
        assertEquals(StructureVisibility.HIDDEN, StructureIds.visibility("apitest_tags", "well", StructureOverrides.NONE));
        // A data pack that shows it underground wins over the mod; one that hides it wins over everything.
        assertEquals(StructureVisibility.ANYWHERE,
                StructureIds.visibility("apitest_tags", "well", new StructureOverrides(false, true, null)));
        assertEquals(StructureVisibility.HIDDEN,
                StructureIds.visibility("apitest_tags", "well", new StructureOverrides(true, true, null)));
        // An ordinary structure is as it always was.
        assertEquals(StructureVisibility.SURFACE,
                StructureIds.visibility("apitest_tags", "temple", StructureOverrides.NONE));
    }

    @Test
    void aTypeFromADataPackReplacesTheWholeLook() {
        StructureStyles.register("apitest_look", "crypt", StructureStyle.of("apitest_look:crypt", 0x88AA55));

        StructureIds.Look own = StructureIds.look("apitest_look", "crypt", null);
        assertEquals("apitest_look:crypt", own.icon());
        assertEquals(0xFF88AA55, own.color());
        assertEquals("explorermap.structure.crypt", own.nameKey());

        StructureIds.Look village = StructureIds.look("apitest_look", "crypt", "village");
        assertEquals("village", village.type());
        assertEquals("explorermap.structure.village", village.nameKey());
        assertEquals("explorermap:village", village.icon());
        assertEquals(0xFFFFFF55, village.color());

        // Variants fold as they do for vanilla ids.
        assertEquals("ruined_portal", StructureIds.look("apitest_look", "x", "ruined_portal_nether").type());
    }

    @Test
    void everyTypeATagCanNameHasALookOfItsOwn() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String type : StructureIds.STYLED_TYPES) {
            assertTrue(seen.add(type), "listed twice: " + type);
            // The tag names the type, and the type is what the structure ends up being: no folding on the way.
            assertEquals(type, StructureIds.normalize(type));
            assertFalse(StructureIds.icon(type).equals("explorermap:pin"), "no icon of its own: " + type);
            assertFalse(StructureIds.color(type) == 0xFFFFFFFF, "no color of its own: " + type);
        }
    }
}
