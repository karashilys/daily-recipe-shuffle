package io.github.karashilys.dailyrecipeshuffle;

import io.github.karashilys.dailyrecipeshuffle.core.GameDay;
import io.github.karashilys.dailyrecipeshuffle.paper.RecipeShuffler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * レシピシャッフルプラグインのエントリポイント。
 * 1秒ごとに基準ワールドの日数を確認し、日数が変わっていればレシピの対応表を適用する。
 */
public final class RecipeShufflePlugin extends JavaPlugin {

    /** 日数を確認する間隔(tick)。最初の確認も、起動完了からこの時間の後に行う。 */
    private static final long CHECK_INTERVAL_TICKS = 20L;

    private RecipeShuffler shuffler;
    /** 最後に適用した日数。未適用なら null。 */
    private Long lastDay;

    /** プラグイン有効化時に、日数の定期確認を開始する。 */
    @Override
    public void onEnable() {
        shuffler = new RecipeShuffler(getLogger());
        // 他プラグインが起動時に追加するレシピも分類に含めるため、最初の確認は起動完了の後に行う(仕様書6.1)
        Bukkit.getScheduler().runTaskTimer(this, this::checkDay, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    /** @return レシピの差し替えを行うオブジェクト。有効化前は null */
    public RecipeShuffler shuffler() {
        return shuffler;
    }

    /** プラグイン無効化時に、差し替えたレシピを元に戻す。 */
    @Override
    public void onDisable() {
        if (shuffler != null) {
            shuffler.restore();
        }
    }

    /** 基準ワールドの日数を確認し、直前に適用した日数と異なれば対応表を適用する。 */
    private void checkDay() {
        World base = Bukkit.getWorlds().getFirst();
        long day = GameDay.of(base.getFullTime());
        if (lastDay != null && lastDay == day) {
            return;
        }
        lastDay = day;
        shuffler.apply(base.getSeed(), day);
    }
}
