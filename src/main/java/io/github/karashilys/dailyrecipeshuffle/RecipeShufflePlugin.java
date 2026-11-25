package io.github.karashilys.dailyrecipeshuffle;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * レシピシャッフルプラグインのエントリポイント。
 * 現時点では起動・停止をログに出力するだけの最小実装。
 */
public final class RecipeShufflePlugin extends JavaPlugin {

    /** プラグイン有効化時に呼ばれ、起動ログを出力する。 */
    @Override
    public void onEnable() {
        getLogger().info("Hello, world! DailyRecipeShuffle enabled.");
    }

    /** プラグイン無効化時に呼ばれ、停止ログを出力する。 */
    @Override
    public void onDisable() {
        getLogger().info("Goodbye, world! DailyRecipeShuffle disabled.");
    }
}
