package io.github.karashilys.dailyrecipeshuffle.core;

/**
 * レシピの種別。Bukkitのレシピクラスに依存しない形で表す。
 */
public enum RecipeKind {
    /** 作業台の有形レシピ */
    SHAPED,
    /** 作業台の無形レシピ */
    SHAPELESS,
    /** かまど */
    FURNACE,
    /** 溶鉱炉 */
    BLASTING,
    /** 燻製器 */
    SMOKING,
    /** 焚き火 */
    CAMPFIRE,
    /** 石切台 */
    STONECUTTING,
    /** 鍛冶台のネザライト強化など、完成品が固定の鍛冶台レシピ */
    SMITHING_TRANSFORM,
    /** 鍛冶台の装飾(トリム) */
    SMITHING_TRIM,
    /** 完成品が材料で決まる特殊レシピ(染色、花火、旗、本のコピー、地図など) */
    COMPLEX,
    /** 醸造 */
    BREWING,
    /** 上記のどれにも当てはまらない */
    UNKNOWN
}
