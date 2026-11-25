package io.github.karashilys.dailyrecipeshuffle.testserver;

import io.github.karashilys.dailyrecipeshuffle.core.GameDay;
import io.github.karashilys.dailyrecipeshuffle.core.RecipeCategory;
import io.github.karashilys.dailyrecipeshuffle.core.RecipeClassifier;
import io.github.karashilys.dailyrecipeshuffle.core.ShuffleTable;
import io.github.karashilys.dailyrecipeshuffle.paper.BukkitRecipes;
import io.github.karashilys.dailyrecipeshuffle.paper.RecipeShuffler;
import io.github.karashilys.dailyrecipeshuffle.paper.RecipeShuffler.Assignment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemCraftResult;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

/**
 * 実サーバー上での検証(仕様書10.2, 10.3)。
 */
final class ShuffleChecks {

    private final RecipeShuffler shuffler;
    private final TestReport report;
    private final World world;
    private final long seed;
    private final long day;

    /**
     * @param shuffler 検証対象
     * @param report 結果の記録先
     */
    ShuffleChecks(RecipeShuffler shuffler, TestReport report) {
        this.shuffler = shuffler;
        this.report = report;
        this.world = Bukkit.getWorlds().getFirst();
        this.seed = world.getSeed();
        this.day = GameDay.of(world.getFullTime());
    }

    /** すべての検証を順に行う。 */
    void all() {
        report.note("seed=" + seed + " day=" + day);
        if (shuffler.assignments().isEmpty()) {
            report.record("前提: 対応表が適用済み", List.of("まだ適用されていない。起動直後なら数秒待ってから再実行する"), "");
            return;
        }
        classification();
        table();
        registry();
        crafting();
        restoreAndReapply();
        reloadAndReapply();
    }

    /** 10.2 全レシピを分類し、未分類がなく、プールの内容が割り当てと一致することを確かめる。 */
    void classification() {
        Map<RecipeCategory, Integer> counts = new EnumMap<>(RecipeCategory.class);
        List<String> problems = new ArrayList<>();
        Set<NamespacedKey> poolKeys = new HashSet<>();
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (!(r instanceof Keyed k)) {
                problems.add("キーのないレシピ: " + r.getClass().getName());
                continue;
            }
            RecipeCategory c = RecipeClassifier.classify(k.getKey().toString(), BukkitRecipes.kindOf(r));
            counts.merge(c, 1, Integer::sum);
            if (c == RecipeCategory.UNCLASSIFIED) {
                problems.add("未分類: " + k.getKey() + " (" + r.getClass().getName() + ")");
            }
            if (c == RecipeCategory.POOL) {
                poolKeys.add(k.getKey());
            }
        }
        Set<NamespacedKey> assigned = shuffler.assignments().keySet();
        for (NamespacedKey k : poolKeys) {
            if (!assigned.contains(k)) {
                problems.add("プールにあるが割り当てがない: " + k);
            }
        }
        for (NamespacedKey k : assigned) {
            if (!poolKeys.contains(k)) {
                problems.add("割り当てがあるがプールにない(固定・対象外の混入など): " + k);
            }
        }
        report.record("分類", problems, counts.toString());
    }

    /** 割り当てが、元の完成品から計算し直した対応表と一致し、全単射・不動点なしであることを確かめる。 */
    void table() {
        Map<NamespacedKey, Assignment> as = shuffler.assignments();
        Map<String, String> ids = new HashMap<>();
        as.forEach((k, a) -> ids.put(k.toString(), BukkitRecipes.resultId(a.original().getResult())));
        Map<String, String> expected = ShuffleTable.compute(ids, seed, day);
        List<String> problems = new ArrayList<>();
        Set<NamespacedKey> sources = new HashSet<>();
        int sameResult = 0;
        for (Assignment a : as.values()) {
            sources.add(a.sourceKey());
            if (!a.sourceKey().toString().equals(expected.get(a.key().toString()))) {
                problems.add(a.key() + ": 出どころ " + a.sourceKey() + " / 期待 " + expected.get(a.key().toString()));
            }
            if (a.result() == null) {
                problems.add(a.key() + ": 差し替えに失敗して元のまま");
                continue;
            }
            Assignment src = as.get(a.sourceKey());
            if (src == null || !a.result().equals(src.original().getResult())) {
                problems.add(a.key() + ": 完成品が出どころの元の完成品と一致しない");
            }
            if (BukkitRecipes.resultId(a.result()).equals(BukkitRecipes.resultId(a.original().getResult()))) {
                sameResult++;
                problems.add(a.key() + ": 元と同じ完成品セット " + BukkitRecipes.resultId(a.result()));
            }
        }
        if (!sources.equals(as.keySet())) {
            problems.add("全単射でない: 出どころ " + sources.size() + " 種 / プール " + as.size() + " 件");
        }
        report.record("対応表", problems, "プール " + as.size() + " 件, 元と同じ完成品 " + sameResult + " 件");
    }

    /** サーバーに登録されたレシピが、割り当ての完成品を持ち、配置側の性質(材料・調理時間など)を保っていることを確かめる。 */
    void registry() {
        List<String> problems = new ArrayList<>();
        for (Assignment a : shuffler.assignments().values()) {
            Recipe cur = Bukkit.getRecipe(a.key());
            if (cur == null) {
                problems.add(a.key() + ": 登録されていない");
                continue;
            }
            ItemStack want = a.result() != null ? a.result() : a.original().getResult();
            if (!cur.getResult().equals(want)) {
                problems.add(a.key() + ": 完成品 " + cur.getResult() + " / 期待 " + want);
            }
            String diff = placementDiff(a.original(), cur);
            if (diff != null) {
                problems.add(a.key() + ": " + diff);
            }
        }
        report.record("登録内容(全種別)", problems, "");
    }

    /**
     * 配置側の性質の差分を調べる。
     *
     * @param o 元のレシピ
     * @param c 現在のレシピ
     * @return 差分の説明。差分がなければ null
     */
    private static String placementDiff(Recipe o, Recipe c) {
        if (o.getClass() != c.getClass()) {
            return "種別が変わった " + o.getClass().getSimpleName() + " -> " + c.getClass().getSimpleName();
        }
        return switch (o) {
            case ShapedRecipe s when c instanceof ShapedRecipe n ->
                    !Arrays.equals(s.getShape(), n.getShape()) ? "形が変わった"
                            : !nonNull(s.getChoiceMap()).equals(nonNull(n.getChoiceMap())) ? "材料が変わった" : null;
            case ShapelessRecipe s when c instanceof ShapelessRecipe n ->
                    !s.getChoiceList().equals(n.getChoiceList()) ? "材料が変わった" : null;
            case CookingRecipe<?> s when c instanceof CookingRecipe<?> n ->
                    !s.getInputChoice().equals(n.getInputChoice()) ? "材料が変わった"
                            : s.getCookingTime() != n.getCookingTime() ? "調理時間 " + s.getCookingTime() + " -> " + n.getCookingTime()
                            : s.getExperience() != n.getExperience() ? "経験値 " + s.getExperience() + " -> " + n.getExperience() : null;
            case StonecuttingRecipe s when c instanceof StonecuttingRecipe n ->
                    !s.getInputChoice().equals(n.getInputChoice()) ? "材料が変わった" : null;
            case SmithingTransformRecipe s when c instanceof SmithingTransformRecipe n ->
                    !Objects.equals(s.getTemplate(), n.getTemplate()) || !Objects.equals(s.getBase(), n.getBase())
                            || !Objects.equals(s.getAddition(), n.getAddition()) ? "材料が変わった"
                            : s.willCopyDataComponents() != n.willCopyDataComponents() ? "コンポーネントの引き継ぎが変わった" : null;
            default -> null;
        };
    }

    private static Map<Character, RecipeChoice> nonNull(Map<Character, RecipeChoice> m) {
        Map<Character, RecipeChoice> r = new HashMap<>();
        m.forEach((k, v) -> {
            if (v != null) {
                r.put(k, v);
            }
        });
        return r;
    }

    /**
     * 10.3 作業台の全レシピについて、配置を作業台に並べた状態を再現し、完成品が割り当てと一致することを確かめる。
     * あわせて、材料側の残り物(空バケツなど)が作業台に残ることを確かめる(仕様書5.1)。
     */
    void crafting() {
        List<String> problems = new ArrayList<>();
        List<String> ambiguous = new ArrayList<>();
        int crafted = 0;
        int leftoverChecks = 0;
        for (Assignment a : shuffler.assignments().values()) {
            if (!(a.original() instanceof ShapedRecipe) && !(a.original() instanceof ShapelessRecipe)) {
                continue;
            }
            ItemStack[] matrix = matrixOf(a.original());
            if (matrix == null) {
                problems.add(a.key() + ": 配置を再現できない");
                continue;
            }
            Recipe matched = Bukkit.getCraftingRecipe(matrix, world);
            if (matched == null) {
                problems.add(a.key() + ": 配置に一致するレシピがない");
                continue;
            }
            NamespacedKey mk = ((Keyed) matched).getKey();
            if (!mk.equals(a.key())) {
                // 同じ配置に一致するレシピが複数ある。バニラでも起こる重なりのため、失敗ではなく記録だけする
                ambiguous.add(a.key() + " の配置は " + mk + " に一致");
                continue;
            }
            ItemCraftResult r = Bukkit.craftItemResult(matrix.clone(), world);
            crafted++;
            ItemStack want = a.result() != null ? a.result() : a.original().getResult();
            if (!r.getResult().equals(want)) {
                problems.add(a.key() + ": クラフト結果 " + r.getResult() + " / 期待 " + want);
            }
            ItemStack[] after = r.getResultingMatrix();
            for (int i = 0; i < 9; i++) {
                ItemType remain = matrix[i].isEmpty() ? null : matrix[i].getType().asItemType().getCraftingRemainingItem();
                if (remain == null) {
                    continue;
                }
                leftoverChecks++;
                if (after[i] == null || after[i].getType().asItemType() != remain) {
                    problems.add(a.key() + ": 材料 " + matrix[i].getType() + " の残り物 " + remain.getKey()
                            + " が残らない (" + after[i] + ")");
                }
            }
        }
        report.record("作業台のクラフト", problems,
                "クラフト " + crafted + " 件, 残り物の確認 " + leftoverChecks + " 件, 他レシピと重なる配置 " + ambiguous.size() + " 件");
        ambiguous.stream().limit(20).forEach(s -> report.note("重なり: " + s));
    }

    /**
     * 作業台のレシピの配置を3×3の配列で再現する。
     *
     * @param r 有形または無形のレシピ
     * @return 配置。再現できない場合は null
     */
    private static ItemStack[] matrixOf(Recipe r) {
        ItemStack[] m = new ItemStack[9];
        Arrays.fill(m, ItemStack.empty());
        if (r instanceof ShapedRecipe s) {
            String[] shape = s.getShape();
            Map<Character, RecipeChoice> choices = s.getChoiceMap();
            for (int row = 0; row < shape.length; row++) {
                for (int col = 0; col < shape[row].length(); col++) {
                    RecipeChoice c = choices.get(shape[row].charAt(col));
                    if (c != null) {
                        ItemStack item = sample(c);
                        if (item == null) {
                            return null;
                        }
                        m[row * 3 + col] = item;
                    }
                }
            }
            return m;
        }
        if (r instanceof ShapelessRecipe s) {
            List<RecipeChoice> list = s.getChoiceList();
            if (list.size() > 9) {
                return null;
            }
            for (int i = 0; i < list.size(); i++) {
                ItemStack item = sample(list.get(i));
                if (item == null) {
                    return null;
                }
                m[i] = item;
            }
            return m;
        }
        return null;
    }

    /**
     * 材料の候補から1つ選んでアイテムにする。
     *
     * @param c 材料の候補
     * @return アイテム。選べない場合は null
     */
    @SuppressWarnings("deprecation")
    private static ItemStack sample(RecipeChoice c) {
        if (c instanceof RecipeChoice.MaterialChoice mc && !mc.getChoices().isEmpty()) {
            return new ItemStack(mc.getChoices().getFirst());
        }
        if (c instanceof RecipeChoice.ExactChoice ec && !ec.getChoices().isEmpty()) {
            return ec.getChoices().getFirst().clone();
        }
        try {
            ItemStack s = c.getItemStack();
            return s == null || s.isEmpty() ? null : s.asOne();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 元に戻すと全レシピが元の完成品に戻り、適用し直すと同じ割り当てになることを確かめる。 */
    void restoreAndReapply() {
        Map<NamespacedKey, Assignment> before = new LinkedHashMap<>(shuffler.assignments());
        List<String> problems = new ArrayList<>();
        shuffler.restore();
        for (Assignment a : before.values()) {
            Recipe cur = Bukkit.getRecipe(a.key());
            if (cur == null) {
                problems.add(a.key() + ": 元に戻した後に登録されていない");
            } else if (!cur.getResult().equals(a.original().getResult())) {
                problems.add(a.key() + ": 元に戻した後の完成品 " + cur.getResult() + " / 元 " + a.original().getResult());
            }
        }
        shuffler.apply(seed, day);
        compare(before, shuffler.assignments(), problems);
        report.record("元に戻す → 適用し直す", problems, "");
    }

    /** データパックを再読み込みしてから適用し直すと、再読み込み前と同じ割り当てになることを確かめる。 */
    void reloadAndReapply() {
        Map<NamespacedKey, Assignment> before = new LinkedHashMap<>(shuffler.assignments());
        List<String> problems = new ArrayList<>();
        Bukkit.reloadData();
        int reverted = 0;
        for (Assignment a : before.values()) {
            Recipe cur = Bukkit.getRecipe(a.key());
            if (cur != null && cur.getResult().equals(a.original().getResult())) {
                reverted++;
            }
        }
        shuffler.apply(seed, day);
        compare(before, shuffler.assignments(), problems);
        report.record("再読み込み → 適用し直す", problems, "再読み込みで元の完成品に戻ったレシピ " + reverted + " 件");
    }

    /**
     * 2つの割り当てが同じかを比べる。
     *
     * @param before 比較元
     * @param after 比較先
     * @param problems 差分の記録先
     */
    private static void compare(Map<NamespacedKey, Assignment> before, Map<NamespacedKey, Assignment> after, List<String> problems) {
        if (!before.keySet().equals(after.keySet())) {
            problems.add("プールの内容が変わった: " + before.size() + " 件 -> " + after.size() + " 件");
        }
        for (Assignment b : before.values()) {
            Assignment a = after.get(b.key());
            if (a != null && (!a.sourceKey().equals(b.sourceKey()) || !Objects.equals(a.result(), b.result()))) {
                problems.add(b.key() + ": 出どころ " + b.sourceKey() + " -> " + a.sourceKey());
            }
        }
    }
}
