package com.explorermap.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureIdsTest {

    @Test
    void biomeVariantsFoldIntoOneType() {
        for (String village : new String[] {"village", "village_plains", "village_desert", "village_savanna",
                "village_snowy", "village_taiga"}) {
            assertEquals("village", StructureIds.normalize(village), village);
        }
        assertEquals("mineshaft", StructureIds.normalize("mineshaft_mesa"));
        assertEquals("woodland_mansion", StructureIds.normalize("mansion"));
    }

    @Test
    void otherIdsAreLeftAlone() {
        assertEquals("desert_pyramid", StructureIds.normalize("desert_pyramid"));
        assertEquals("villager_hut", StructureIds.normalize("villager_hut"));
    }

    @Test
    void vanillaIdsGetTheirIconAndName() {
        assertEquals("explorermap:village", StructureIds.icon("village_taiga"));
        assertEquals("explorermap:temple", StructureIds.icon("jungle_pyramid"));
        assertEquals("explorermap:dungeon", StructureIds.icon("mineshaft_mesa"));
        assertEquals("explorermap:dungeon", StructureIds.icon("mansion"));
        assertEquals("explorermap:village", StructureIds.icon("igloo"));
        assertEquals("explorermap:pin", StructureIds.icon("some_modded_structure"));
        assertEquals("explorermap.structure.village", StructureIds.nameKey("village_savanna"));
    }

    @Test
    void storedNamesMapBackToTheirType() {
        assertEquals("village", StructureIds.typeOfStoredName("explorermap.structure.village"));
        assertEquals("village", StructureIds.typeOfStoredName("explorermap.structure.village (3)"));
        // Written by older versions.
        assertEquals("village", StructureIds.typeOfStoredName("Village Plains"));
        assertEquals("desert_pyramid", StructureIds.typeOfStoredName("Desert Pyramid (2)"));
    }

    @Test
    void moreVariantsFoldIntoOneType() {
        for (String portal : new String[] {"ruined_portal", "ruined_portal_desert", "ruined_portal_jungle",
                "ruined_portal_mountain", "ruined_portal_nether", "ruined_portal_ocean", "ruined_portal_swamp"}) {
            assertEquals("ruined_portal", StructureIds.normalize(portal), portal);
        }
        assertEquals("shipwreck", StructureIds.normalize("shipwreck_beached"));
        assertEquals("ocean_ruin", StructureIds.normalize("ocean_ruin_cold"));
        assertEquals("ocean_ruin", StructureIds.normalize("ocean_ruin_warm"));
        assertEquals("explorermap.structure.ruined_portal", StructureIds.nameKey("ruined_portal_nether"));
    }

    @Test
    void everyKnownStructureHasItsOwnLook() {
        assertEquals("explorermap:monument", StructureIds.icon("monument"));
        assertEquals("explorermap:outpost", StructureIds.icon("pillager_outpost"));
        assertEquals("explorermap:portal", StructureIds.icon("ruined_portal_desert"));
        assertEquals("explorermap:ship", StructureIds.icon("shipwreck_beached"));
        assertEquals("explorermap:fortress", StructureIds.icon("fortress"));
        assertEquals("explorermap:fortress", StructureIds.icon("bastion_remnant"));
        assertEquals("explorermap:fortress", StructureIds.icon("end_city"));
        assertEquals("explorermap:treasure", StructureIds.icon("buried_treasure"));
        assertEquals("explorermap:vault", StructureIds.icon("trial_chambers"));
        assertEquals("explorermap:temple", StructureIds.icon("ocean_ruin_warm"));
        assertEquals("explorermap:village", StructureIds.icon("swamp_hut"));
    }

    @Test
    void storedNamesOfFoldedVariantsMapBackToTheirType() {
        assertEquals("ocean_ruin", StructureIds.typeOfStoredName("explorermap.structure.ocean_ruin_cold"));
        assertEquals("shipwreck", StructureIds.typeOfStoredName("explorermap.structure.shipwreck_beached (2)"));
    }

    @Test
    void mapDecorationsGiveTheStructureTheyMark() {
        assertEquals("woodland_mansion", StructureIds.fromMapDecoration("mansion"));
        assertEquals("monument", StructureIds.fromMapDecoration("monument"));
        assertEquals("trial_chambers", StructureIds.fromMapDecoration("trial_chambers"));
        assertEquals("buried_treasure", StructureIds.fromMapDecoration("red_x"));
        assertEquals("jungle_pyramid", StructureIds.fromMapDecoration("jungle_temple"));
        assertEquals("swamp_hut", StructureIds.fromMapDecoration("swamp_hut"));
        for (String village : new String[] {"desert_village", "plains_village", "savanna_village",
                "snowy_village", "taiga_village"}) {
            assertEquals("village", StructureIds.fromMapDecoration(village), village);
        }
        assertEquals("map_target", StructureIds.fromMapDecoration("target_x"));
        // Unknown ones keep their path, so they still get a readable name.
        assertEquals("modded_marker", StructureIds.fromMapDecoration("modded_marker"));
    }
}
