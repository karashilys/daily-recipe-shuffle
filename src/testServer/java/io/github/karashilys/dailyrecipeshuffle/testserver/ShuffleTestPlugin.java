package io.github.karashilys.dailyrecipeshuffle.testserver;

import io.github.karashilys.dailyrecipeshuffle.RecipeShufflePlugin;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 【テスト専用】DailyRecipeShuffle の分類と差し替え結果を実サーバーで検証するプラグイン。
 * 開発用サーバーにだけ読み込み、リリースには含めない。
 *
 * <p>実行方法は2つある。
 * <ul>
 *   <li>{@code /drstest}: 全検証を行い、結果を {@code plugins/DailyRecipeShuffleTest/report.txt} に書き出す。</li>
 *   <li>システムプロパティ {@code drs.test.autorun=true}: 対応表が適用されるのを待って全検証を行い、
 *       結果を書き出してからサーバーを停止する。Gradleの {@code serverTest} タスクが使う。</li>
 * </ul>
 * 検証の途中でレシピを元に戻して適用し直し、データパックを再読み込みする。
 */
public final class ShuffleTestPlugin extends JavaPlugin {

    /** 自動実行モードを有効にするシステムプロパティ。 */
    private static final String AUTORUN_PROPERTY = "drs.test.autorun";
    /** 自動実行モードで、対応表の適用を待つ上限(tick)。 */
    private static final long AUTORUN_TIMEOUT_TICKS = 20L * 120;

    /** プラグイン有効化時に検証コマンドを登録し、自動実行モードなら待機を始める。 */
    @Override
    public void onEnable() {
        registerCommand("drstest", "DailyRecipeShuffle の実サーバー検証(テスト専用)", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack source, String[] args) {
                source.getSender().sendMessage(run(new TestReport()));
            }

            @Override
            public String permission() {
                return "dailyrecipeshuffle.test";
            }
        });
        if (Boolean.getBoolean(AUTORUN_PROPERTY)) {
            startAutorun();
        }
    }

    /** 対応表が適用されるまで1秒ごとに確認し、適用されたら検証してサーバーを停止する。 */
    private void startAutorun() {
        getLogger().info("自動実行モード: 対応表の適用を待ってから検証し、サーバーを停止します");
        long[] waited = {0};
        BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            waited[0] += 20;
            boolean applied = !target().shuffler().assignments().isEmpty();
            if (!applied && waited[0] < AUTORUN_TIMEOUT_TICKS) {
                return;
            }
            task[0].cancel();
            TestReport report = new TestReport();
            if (!applied) {
                report.record("前提: 対応表が適用済み", List.of(AUTORUN_TIMEOUT_TICKS / 20 + "秒待っても適用されない"), "");
                finish(report);
            } else {
                run(report);
            }
            Bukkit.shutdown();
        }, 20L, 20L);
    }

    /** @return 検証対象のプラグイン */
    private static RecipeShufflePlugin target() {
        return JavaPlugin.getPlugin(RecipeShufflePlugin.class);
    }

    /**
     * 全検証を行い、結果を書き出す。
     *
     * @param report 結果の記録先
     * @return 結果の要約
     */
    private String run(TestReport report) {
        long t0 = System.nanoTime();
        try {
            new ShuffleChecks(target().shuffler(), report).all();
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "検証中に例外", e);
            report.record("検証の実行", List.of(e.toString()), "");
        }
        String summary = finish(report);
        return String.format("%s (%.1f秒)", summary, (System.nanoTime() - t0) / 1e9);
    }

    /**
     * 結果をファイルとログに書き出す。ファイルの最終行は要約で、PASS または FAIL で始まる。
     *
     * @param report 結果
     * @return 要約と出力先
     */
    private String finish(TestReport report) {
        Path out = getDataPath().resolve("report.txt");
        try {
            report.write(out);
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "結果を書き出せない", e);
        }
        report.lines().forEach(getLogger()::info);
        String summary = report.summary() + " " + out;
        getLogger().info(summary);
        return summary;
    }
}
