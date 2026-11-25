package io.github.karashilys.dailyrecipeshuffle.core;

/**
 * レシピの分類結果。
 */
public enum RecipeCategory {
    /** 入れ替え対象 */
    POOL,
    /** 入れ替えず、バニラのまま動作する */
    FIXED,
    /** プラグインの対象外 */
    EXCLUDED,
    /** どの区分にも当てはまらない。Paperの更新などで新しい種別が増えた可能性がある */
    UNCLASSIFIED
}
