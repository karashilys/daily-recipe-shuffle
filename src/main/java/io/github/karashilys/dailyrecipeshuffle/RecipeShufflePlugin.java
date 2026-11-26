package io.github.karashilys.dailyrecipeshuffle;

import io.github.karashilys.dailyrecipeshuffle.core.GameDay;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import io.github.karashilys.dailyrecipeshuffle.paper.RecipeShuffler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * レシピシャッフルプラグインのエントリポイント。
 * 1秒ごとに基準ワールドの日数を確認し、日数が変わっていればレシピの対応表を適用する。
 */
public final class RecipeShufflePlugin extends JavaPlugin implements Listener {

    /** 日数を確認する間隔(tick)。最初の確認も、起動完了からこの時間の後に行う。 */
    private static final long CHECK_INTERVAL_TICKS = 20L;

    private RecipeShuffler shuffler;
    /** 最後に適用した日数。未適用なら null。 */
    private Long lastDay;

    /** プラグイン有効化時に、日数の定期確認を開始する。 */
    @Override
    public void onEnable() {
        shuffler = new RecipeShuffler(getLogger());
        Bukkit.getPluginManager().registerEvents(this, this);
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
        shuffler.apply(base.getSeed(), day);
        boolean dayChanged = lastDay != null;
        lastDay = day;
        // 起動直後や再読み込み後の適用は日数の変化ではないので、通知しない
        if (dayChanged) {
            Bukkit.broadcast(Component.text("レシピがシャッフルされました", NamedTextColor.GOLD));
        }
    }

    /**
     * データパックの再読み込みでレシピが入れ替え前に戻るため、次の確認(最大1秒後)で適用し直す。
     *
     * @param event 再読み込み完了イベント
     */
    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        lastDay = null;
    }
}
