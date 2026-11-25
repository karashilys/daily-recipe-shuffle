package io.github.karashilys.dailyrecipeshuffle.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * tickから日数への変換を検証する。
 */
class GameDayTest {

    @Test
    void tickから日数を求める() {
        assertEquals(0, GameDay.of(0));
        assertEquals(0, GameDay.of(23999));
        assertEquals(1, GameDay.of(24000));
        assertEquals(1, GameDay.of(30515));
        assertEquals(3, GameDay.of(73000));
    }
}
