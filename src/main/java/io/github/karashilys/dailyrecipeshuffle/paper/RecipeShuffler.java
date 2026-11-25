package io.github.karashilys.dailyrecipeshuffle.paper;

import io.github.karashilys.dailyrecipeshuffle.core.RecipeCategory;
import io.github.karashilys.dailyrecipeshuffle.core.RecipeClassifier;
import io.github.karashilys.dailyrecipeshuffle.core.ShuffleTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final Logger log;
    /** プールのレシピの、差し替え前の元のレシピ。 */
    private Map<NamespacedKey, Recipe> originals = Map.of();
    /** 差し替えで各レシピに割り当てた完成品。 */
    private Map<NamespacedKey, ItemStack> applied = Map.of();

    /** @param log ロガー */
    public RecipeShuffler(Logger log) {
        this.log = log;
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
        Map<NamespacedKey, ItemStack> newApplied = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : table.entrySet()) {
            NamespacedKey key = keyByString.get(e.getKey());
            Recipe base = pool.get(key);
            ItemStack result = pool.get(keyByString.get(e.getValue())).getResult();
            try {
                rebuilt.add(BukkitRecipes.rebuild(base, result));
                newApplied.put(key, result);
            } catch (RuntimeException ex) {
                log.log(Level.WARNING, "レシピを差し替えられないため、元のまま登録します: " + key, ex);
                rebuilt.add(base);
            }
        }

        replace(pool.keySet(), rebuilt);
        originals = pool;
        applied = newApplied;
        log.info(String.format("day %d の対応表を適用しました (プール %d 件, %.1fms)",
                day, pool.size(), (System.nanoTime() - t0) / 1e6));
    }

    /** 差し替えたレシピを元に戻す。 */
    public void restore() {
        if (applied.isEmpty()) {
            return;
        }
        List<NamespacedKey> present = applied.keySet().stream().filter(k -> Bukkit.getRecipe(k) != null).toList();
        replace(present, new ArrayList<>(originals.values()));
        applied = Map.of();
    }

    /**
     * レシピを一括で削除してから一括で登録する。1件ずつのAPIは呼ぶたびに全プレイヤーへ再送するため使わない。
     *
     * @param remove 削除するキー
     * @param add 登録するレシピ
     */
    private void replace(Iterable<NamespacedKey> remove, List<Recipe> add) {
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
                    ItemStack ours = applied.get(key);
                    boolean stillApplied = ours != null && ours.equals(r.getResult()) && originals.containsKey(key);
                    pool.put(key, stillApplied ? originals.get(key) : r);
                }
                case UNCLASSIFIED -> unclassified.add(key + " (" + r.getClass().getName() + ")");
                case FIXED, EXCLUDED -> {
                }
            }
        }
        if (!unclassified.isEmpty()) {
            log.severe("未分類のレシピが " + unclassified.size() + " 件あります。入れ替えずにそのまま残します: " + unclassified);
        }
        return pool;
    }
}
