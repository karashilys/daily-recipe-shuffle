package io.github.karashilys.dailyrecipeshuffle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * レシピの分類(仕様書4章)を検証する。
 */
class RecipeClassifierTest {

    @Test
    void 作業台のレシピは固定() {
        assertEquals(RecipeCategory.FIXED, RecipeClassifier.classify("minecraft:crafting_table", RecipeKind.SHAPED));
    }

    @Test
    void 装飾と特殊レシピは固定() {
        assertEquals(RecipeCategory.FIXED, RecipeClassifier.classify("minecraft:coast_armor_trim_smithing_template_smithing_trim", RecipeKind.SMITHING_TRIM));
        assertEquals(RecipeCategory.FIXED, RecipeClassifier.classify("minecraft:firework_rocket", RecipeKind.COMPLEX));
    }

    @Test
    void 醸造は対象外() {
        assertEquals(RecipeCategory.EXCLUDED, RecipeClassifier.classify("minecraft:brewing/awkward", RecipeKind.BREWING));
    }

    @Test
    void 不明な種別は未分類() {
        assertEquals(RecipeCategory.UNCLASSIFIED, RecipeClassifier.classify("minecraft:x", RecipeKind.UNKNOWN));
    }

    @Test
    void プール対象の種別() {
        for (RecipeKind kind : new RecipeKind[] {RecipeKind.SHAPED, RecipeKind.SHAPELESS, RecipeKind.FURNACE,
                RecipeKind.BLASTING, RecipeKind.SMOKING, RecipeKind.CAMPFIRE, RecipeKind.STONECUTTING,
                RecipeKind.SMITHING_TRANSFORM}) {
            assertEquals(RecipeCategory.POOL, RecipeClassifier.classify("minecraft:stick", kind), kind.name());
        }
    }

    @Test
    void データパックや他プラグインのレシピも同じ規則で分類する() {
        assertEquals(RecipeCategory.POOL, RecipeClassifier.classify("mypack:dirt_to_diamond", RecipeKind.SHAPED));
        assertEquals(RecipeCategory.FIXED, RecipeClassifier.classify("mypack:special", RecipeKind.COMPLEX));
    }
}
