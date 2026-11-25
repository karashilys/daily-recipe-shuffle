package io.github.karashilys.dailyrecipeshuffle.testserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 検証結果を集める。
 */
final class TestReport {

    /** 1件の検証で詳細に書き出す項目の上限。 */
    private static final int DETAIL_LIMIT = 50;

    private final List<String> lines = new ArrayList<>();
    private int checks;
    private int failures;

    /**
     * 検証1件の結果を記録する。
     *
     * @param name 検証名
     * @param problems 問題の一覧。空なら合格
     * @param info 合格・不合格にかかわらず出す補足
     */
    void record(String name, List<String> problems, String info) {
        checks++;
        boolean ok = problems.isEmpty();
        if (!ok) {
            failures++;
        }
        lines.add((ok ? "[PASS] " : "[FAIL] ") + name + (info.isEmpty() ? "" : " — " + info)
                + (ok ? "" : " (" + problems.size() + "件)"));
        problems.stream().limit(DETAIL_LIMIT).forEach(p -> lines.add("    " + p));
        if (problems.size() > DETAIL_LIMIT) {
            lines.add("    ...ほか " + (problems.size() - DETAIL_LIMIT) + " 件");
        }
    }

    /**
     * 合否に関係しない補足の行を追加する。
     *
     * @param line 行
     */
    void note(String line) {
        lines.add("    " + line);
    }

    /** @return 1行の要約 */
    String summary() {
        return (failures == 0 ? "PASS" : "FAIL") + ": " + checks + " 件中 " + failures + " 件失敗";
    }

    /** @return 全行 */
    List<String> lines() {
        return lines;
    }

    /**
     * ファイルに書き出す。
     *
     * @param out 出力先
     * @throws IOException 書き込み失敗時
     */
    void write(Path out) throws IOException {
        Files.createDirectories(out.getParent());
        List<String> all = new ArrayList<>(lines);
        all.add(summary());
        Files.write(out, all);
    }
}
