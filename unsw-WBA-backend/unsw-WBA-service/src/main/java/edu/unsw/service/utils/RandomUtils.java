package edu.unsw.service.utils;

import java.util.Random;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 27/7/2026 16:23
 * @description Random number utils
 */
public class RandomUtils {
    private RandomUtils() {

    }

    private static final Random RANDOM = new Random();

    public static String getRandom(int min, int max, int size) {
        String randomString = String.valueOf(RANDOM.nextInt(max - min + 1) + min);
        return randomString.substring(0, Math.min(size, randomString.length()));
    }
}
