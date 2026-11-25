package io.github.karashilys.dailyrecipeshuffle.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 対応表(どのレシピに、どのレシピの完成品セットを割り当てるか)を計算する(仕様書5.3)。
 *
 * <p>対応表は次の性質を満たす。
 * <ul>
 *   <li>全単射: すべての完成品セットが、ちょうど1つのレシピに割り当てられる</li>
 *   <li>不動点なし: どのレシピも、元と同じ完成品セットにならない</li>
 *   <li>決定的: 同じシード・日数・プールからは、常に同じ対応表が得られる</li>
 *   <li>順序非依存: 入力の列挙順に左右されない</li>
 * </ul>
 * 乱数生成器はJDKの実装に依存しないよう、SplitMix64を自前で実装する。
 */
public final class ShuffleTable {

    private ShuffleTable() {}

    /**
     * 対応表を計算する。
     *
     * <p>完成品セットが同じレシピ同士(例: 別の配置から同じ物ができるレシピ)を入れ替えても、
     * プレイヤーから見て変化がないため、完成品セットの値が元と同じにならないようにする。
     * プールの過半数が同じ完成品セットを持つなど、値が同じにならない割り当てが存在しない場合は、
     * そのレシピだけ元と同じ完成品セットになることがある。
     *
     * @param resultIds レシピのキー → 完成品セットの識別文字列
     * @param seed ワールドシード
     * @param day 日数
     * @return レシピのキー(配置側) → 完成品セットの出どころのレシピのキー。キーの文字列順に並ぶ
     */
    public static Map<String, String> compute(Map<String, String> resultIds, long seed, long day) {
        List<String> keys = new ArrayList<>(resultIds.keySet());
        Collections.sort(keys);
        int n = keys.size();
        String[] res = new String[n];
        for (int i = 0; i < n; i++) {
            res[i] = resultIds.get(keys.get(i));
        }
        int[] p = derange(n, res, new SplitMix64(mix(seed, day)));
        Map<String, String> table = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            table.put(keys.get(i), keys.get(p[i]));
        }
        return table;
    }

    /**
     * 不動点のない置換を作る。Sattoloのアルゴリズムで単一巡回の置換を作り、
     * 完成品セットの値が元と同じになった位置を、他の位置との交換で解消する。
     *
     * @param n 要素数
     * @param res 各位置の完成品セットの識別文字列
     * @param rnd 乱数生成器
     * @return 置換。位置iには res[p[i]] が割り当てられる
     */
    static int[] derange(int n, String[] res, SplitMix64 rnd) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = i;
        }
        if (n < 2) {
            return p;
        }
        for (int i = n - 1; i > 0; i--) {
            swap(p, i, rnd.nextInt(i));
        }
        for (int i = 0; i < n; i++) {
            if (!Objects.equals(res[p[i]], res[i])) {
                continue;
            }
            int start = rnd.nextInt(n);
            for (int k = 0; k < n; k++) {
                int j = (start + k) % n;
                // 交換後に i と j の両方が元と異なる完成品セットになる場合だけ交換する
                if (!Objects.equals(res[p[j]], res[i]) && !Objects.equals(res[p[i]], res[j])) {
                    swap(p, i, j);
                    break;
                }
            }
        }
        return p;
    }

    /**
     * シードと日数から乱数生成器の初期値を作る。
     *
     * @param seed ワールドシード
     * @param day 日数
     * @return 初期値
     */
    static long mix(long seed, long day) {
        return SplitMix64.mix64(seed * 0x9E3779B97F4A7C15L + day);
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    /**
     * SplitMix64による乱数生成器。
     */
    static final class SplitMix64 {
        private long state;

        /** @param seed 初期値 */
        SplitMix64(long seed) {
            this.state = seed;
        }

        /** @return 次の64ビットの乱数 */
        long nextLong() {
            state += 0x9E3779B97F4A7C15L;
            return mix64(state);
        }

        /**
         * 0以上bound未満の整数を返す。
         *
         * @param bound 上限(含まない)。正の数
         * @return 乱数
         */
        int nextInt(int bound) {
            return (int) Long.remainderUnsigned(nextLong(), bound);
        }

        /**
         * SplitMix64の攪拌関数。
         *
         * @param z 入力
         * @return 攪拌した値
         */
        static long mix64(long z) {
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
    }
}
