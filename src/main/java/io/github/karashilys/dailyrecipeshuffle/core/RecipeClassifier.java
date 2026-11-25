package io.github.karashilys.dailyrecipeshuffle.core;

/**
 * レシピをプール・固定・対象外に分類する(仕様書4章)。
 */
public final class RecipeClassifier {

    /** 作業台を作るレシピのキー。作業台を入手できない状況を避けるため固定する。 */
    public static final String CRAFTING_TABLE_KEY = "minecraft:crafting_table";

    private RecipeClassifier() {}

    /**
     * レシピを分類する。
     *
     * @param key レシピのキー(例: {@code minecraft:stick})
     * @param kind レシピの種別
     * @return 分類結果
     */
    public static RecipeCategory classify(String key, RecipeKind kind) {
        if (key.equals(CRAFTING_TABLE_KEY)) {
            return RecipeCategory.FIXED;
        }
        return switch (kind) {
            case SHAPED, SHAPELESS, FURNACE, BLASTING, SMOKING, CAMPFIRE, STONECUTTING, SMITHING_TRANSFORM ->
                    RecipeCategory.POOL;
            case SMITHING_TRIM, COMPLEX -> RecipeCategory.FIXED;
            case BREWING -> RecipeCategory.EXCLUDED;
            case UNKNOWN -> RecipeCategory.UNCLASSIFIED;
        };
    }
}
