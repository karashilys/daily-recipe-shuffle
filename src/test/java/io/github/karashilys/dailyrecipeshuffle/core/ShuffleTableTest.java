package io.github.karashilys.dailyrecipeshuffle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * 対応表の性質(仕様書5.3)を検証する。
 */
class ShuffleTableTest {

    private static final long SEED = -4172144997902289642L;

    /**
     * 完成品がすべて異なるプールを作る。
     *
     * @param n 件数
     * @return キー → 完成品セットの識別文字列
     */
    private static Map<String, String> distinctPool(int n) {
        Map<String, String> pool = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            pool.put("minecraft:recipe_" + i, "minecraft:item_" + i + " x1");
        }
        return pool;
    }

    /**
     * バニラに近い、同じ完成品セットを持つレシピが多数あるプールを作る。
     *
     * @return キー → 完成品セットの識別文字列
     */
    private static Map<String, String> poolWithDuplicates() {
        Map<String, String> pool = new LinkedHashMap<>();
        Random r = new Random(1);
        for (int i = 0; i < 1700; i++) {
            // 完成品の種類を600に絞り、同じ完成品セットのレシピを多く作る
            pool.put("minecraft:recipe_" + i, "minecraft:item_" + r.nextInt(600) + " x" + (1 + r.nextInt(2)));
        }
        // 同じ完成品セットが10件ある染料のような集まり
        for (int i = 0; i < 10; i++) {
            pool.put("minecraft:dye_" + i, "minecraft:white_dye x1");
        }
        return pool;
    }

    @Test
    void 同じシードと日数なら同じ対応表になる() {
        Map<String, String> pool = poolWithDuplicates();
        for (long day = 0; day < 20; day++) {
            assertEquals(ShuffleTable.compute(pool, SEED, day), ShuffleTable.compute(pool, SEED, day));
        }
    }

    @Test
    void 日数が違えば対応表が変わる() {
        Map<String, String> pool = distinctPool(100);
        assertNotEquals(ShuffleTable.compute(pool, SEED, 0), ShuffleTable.compute(pool, SEED, 1));
        assertNotEquals(ShuffleTable.compute(pool, SEED, 0), ShuffleTable.compute(pool, SEED + 1, 0));
    }

    @Test
    void 全単射である() {
        Map<String, String> pool = poolWithDuplicates();
        for (long day = 0; day < 50; day++) {
            Map<String, String> table = ShuffleTable.compute(pool, SEED, day);
            assertEquals(pool.keySet(), table.keySet());
            assertEquals(pool.keySet(), new HashSet<>(table.values()));
        }
    }

    @Test
    void 不動点がない() {
        Map<String, String> pool = poolWithDuplicates();
        for (long day = 0; day < 200; day++) {
            Map<String, String> table = ShuffleTable.compute(pool, SEED, day);
            for (Map.Entry<String, String> e : table.entrySet()) {
                assertNotEquals(e.getKey(), e.getValue(), "day " + day);
                assertNotEquals(pool.get(e.getKey()), pool.get(e.getValue()),
                        "day " + day + ": " + e.getKey() + " に同じ完成品セットが割り当てられた");
            }
        }
    }

    @Test
    void 列挙順に依存しない() {
        Map<String, String> pool = poolWithDuplicates();
        List<Map.Entry<String, String>> entries = new ArrayList<>(pool.entrySet());
        Collections.shuffle(entries, new Random(42));
        Map<String, String> shuffled = new LinkedHashMap<>();
        entries.forEach(e -> shuffled.put(e.getKey(), e.getValue()));
        for (long day = 0; day < 10; day++) {
            assertEquals(ShuffleTable.compute(pool, SEED, day), ShuffleTable.compute(shuffled, SEED, day));
        }
    }

    @Test
    void 負の日数と小さなプールを扱える() {
        assertTrue(ShuffleTable.compute(Map.of(), SEED, 0).isEmpty());
        assertEquals(Map.of("a", "a"), ShuffleTable.compute(Map.of("a", "x"), SEED, 0));
        Map<String, String> two = ShuffleTable.compute(Map.of("a", "x", "b", "y"), SEED, -1);
        assertEquals(Map.of("a", "b", "b", "a"), two);
    }

    @Test
    void 実装を変えていないことを固定値で確認する() {
        // 乱数や並べ替えの実装が変わると、同じワールドでも配置が変わってしまうため固定値で検知する
        Map<String, String> table = ShuffleTable.compute(distinctPool(5), 12345L, 7L);
        assertEquals("{minecraft:recipe_0=minecraft:recipe_3, minecraft:recipe_1=minecraft:recipe_4, "
                + "minecraft:recipe_2=minecraft:recipe_0, minecraft:recipe_3=minecraft:recipe_1, "
                + "minecraft:recipe_4=minecraft:recipe_2}", table.toString());
    }
}
