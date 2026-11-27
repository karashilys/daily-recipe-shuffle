package io.github.karashilys.dailyrecipeshuffle.paper;

import io.github.karashilys.dailyrecipeshuffle.core.RecipeCategory;
import io.github.karashilys.dailyrecipeshuffle.core.RecipeClassifier;
import io.github.karashilys.dailyrecipeshuffle.core.ShuffleTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * サーバー上のレシピを分類し、対応表に従って完成品を差し替える(仕様書6章)。
 */
public final class RecipeShuffler {

    /**
     * 1件のレシピへの割り当て。
     *
     * @param key 配置側のレシピのキー
     * @param original 配置側の元のレシピ
     * @param sourceKey 完成品セットの出どころのレシピのキー
     * @param result 割り当てた完成品。差し替えに失敗して元のまま登録した場合は null
     */
    public record Assignment(NamespacedKey key, Recipe original, NamespacedKey sourceKey, ItemStack result) {}

    private final Logger log;
    /** 現在の割り当て(キーの文字列順)。 */
    private Map<NamespacedKey, Assignment> assignments = Map.of();
    /** 直前に警告した未分類レシピの一覧。同じ内容を日数ごとに繰り返し出さないために保持する。 */
    private Set<String> reportedUnclassified = Set.of();

    /** @param log ロガー */
    public RecipeShuffler(Logger log) {
        this.log = log;
    }

    /** @return 現在の割り当て(読み取り専用、キーの文字列順)。未適用なら空 */
    public Map<NamespacedKey, Assignment> assignments() {
        return Collections.unmodifiableMap(assignments);
    }

    /**
     * 全レシピを分類し直し、指定の日数の対応表を適用する。
     *
     * @param seed ワールドシード
     * @param day 日数
     */
    public void apply(long seed, long day) {
        long t0 = System.nanoTime();
        Map<NamespacedKey, Recipe> pool = scanPool();

        Map<String, String> resultIds = new HashMap<>();
        Map<String, NamespacedKey> keyByString = new HashMap<>();
        for (Map.Entry<NamespacedKey, Recipe> e : pool.entrySet()) {
            resultIds.put(e.getKey().toString(), BukkitRecipes.resultId(e.getValue().getResult()));
            keyByString.put(e.getKey().toString(), e.getKey());
        }
        Map<String, String> table = ShuffleTable.compute(resultIds, seed, day);

        List<Recipe> rebuilt = new ArrayList<>(pool.size());
        Map<NamespacedKey, Assignment> next = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : table.entrySet()) {
            NamespacedKey key = keyByString.get(e.getKey());
            NamespacedKey sourceKey = keyByString.get(e.getValue());
            Recipe base = pool.get(key);
            ItemStack result = pool.get(sourceKey).getResult();
            try {
                rebuilt.add(BukkitRecipes.rebuild(base, result));
                next.put(key, new Assignment(key, base, sourceKey, result));
            } catch (RuntimeException ex) {
                log.log(Level.WARNING, "レシピを差し替えられないため、元のまま登録します: " + key, ex);
                rebuilt.add(base);
                next.put(key, new Assignment(key, base, sourceKey, null));
            }
        }

        replace(pool.keySet(), rebuilt);
        assignments = next;
        log.info(String.format("day %d の対応表を適用しました (プール %d 件, %.1fms)",
                day, pool.size(), (System.nanoTime() - t0) / 1e6));
    }

    /** 差し替えたレシピを元に戻す。 */
    public void restore() {
        if (assignments.isEmpty()) {
            return;
        }
        // 外部で削除されたレシピまで復活させないよう、現在も存在するものだけを戻す
        List<NamespacedKey> present = assignments.keySet().stream().filter(k -> Bukkit.getRecipe(k) != null).toList();
        Set<NamespacedKey> presentSet = new HashSet<>(present);
        replace(present, assignments.values().stream()
                .filter(a -> presentSet.contains(a.key()))
                .map(Assignment::original)
                .toList());
        assignments = Map.of();
    }

    /**
     * レシピを一括で削除してから一括で登録する。1件ずつのAPIは呼ぶたびに全プレイヤーへ再送するため使わない。
     * 削除・登録の際にサーバーが全プレイヤーの実績をディスクから読み直すため、先にプレイヤーを保存する。
     *
     * @param remove 削除するキー
     * @param add 登録するレシピ
     */
    private void replace(Iterable<NamespacedKey> remove, List<Recipe> add) {
        // 一括APIは全プレイヤーの実績をディスクから読み直す。未保存の進捗が消えるため、先に保存する
        Bukkit.savePlayers();
        Bukkit.removeRecipes(remove);
        try {
            Bukkit.addRecipes(add);
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "レシピの一括登録に失敗しました。登録できなかったレシピを1件ずつ登録し直します", ex);
            for (Recipe r : add) {
                if (r instanceof Keyed k && Bukkit.getRecipe(k.getKey()) == null) {
                    Bukkit.addRecipe(r);
                }
            }
        }
    }

    /**
     * 全レシピを分類し、プールのレシピを元の状態で返す。
     * 差し替え済みのレシピは、保持している元のレシピに置き換える。
     * 再読み込みなどで差し替えが失われたレシピは、サーバー上のものを元のレシピとして扱う。
     *
     * @return プールのレシピ(キー → 元のレシピ)
     */
    private Map<NamespacedKey, Recipe> scanPool() {
        Map<NamespacedKey, Recipe> pool = new HashMap<>();
        List<String> unclassified = new ArrayList<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (!(r instanceof Keyed keyed)) {
                unclassified.add(r.getClass().getName());
                continue;
            }
            NamespacedKey key = keyed.getKey();
            RecipeCategory category = RecipeClassifier.classify(key.toString(), BukkitRecipes.kindOf(r));
            switch (category) {
                case POOL -> {
                    Assignment a = assignments.get(key);
                    boolean stillApplied = a != null && a.result() != null && a.result().equals(r.getResult());
                    pool.put(key, stillApplied ? a.original() : r);
                }
                case UNCLASSIFIED -> unclassified.add(key + " (" + r.getClass().getName() + ")");
                case FIXED, EXCLUDED -> {
                }
            }
        }
        Set<String> current = new HashSet<>(unclassified);
        boolean changed = !current.equals(reportedUnclassified);
        reportedUnclassified = current;
        if (!unclassified.isEmpty() && changed) {
            log.severe("未分類のレシピが " + unclassified.size() + " 件あります。入れ替えずにそのまま残します: " + unclassified);
        }
        return pool;
    }
}
