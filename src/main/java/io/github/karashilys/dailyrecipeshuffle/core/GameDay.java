package io.github.karashilys.dailyrecipeshuffle.core;

/**
 * 経過tickからゲーム内の日数を求める(仕様書5.4)。
 */
public final class GameDay {

    /** ゲーム内の1日のtick数。 */
    public static final long TICKS_PER_DAY = 24000L;

    private GameDay() {}

    /**
     * 基準ワールドの時計の通算tickから日数を求める。
     *
     * @param fullTime 時計の通算tick
     * @return 日数(0始まり)
     */
    public static long of(long fullTime) {
        return Math.floorDiv(fullTime, TICKS_PER_DAY);
    }
}
