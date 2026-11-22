package ru.playtimegate.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор и форматирование игрового времени.
 */
public final class TimeUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)([dhms])");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("\\d+");

    private TimeUtil() {
    }

    /**
     * Разбирает строку вида "24h", "1d12h30m", "90m", "3600s".
     * Число без единицы измерения трактуется как минуты.
     *
     * @param input           строка из конфига
     * @param fallbackSeconds значение, если строку разобрать не удалось
     * @return количество секунд
     */
    public static long parseDuration(String input, long fallbackSeconds) {
        if (input == null) {
            return fallbackSeconds;
        }

        String value = input.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
        if (value.isEmpty()) {
            return fallbackSeconds;
        }

        if (PLAIN_NUMBER.matcher(value).matches()) {
            try {
                return Long.parseLong(value) * 60L;
            } catch (NumberFormatException e) {
                return fallbackSeconds;
            }
        }

        Matcher matcher = PART.matcher(value);
        long total = 0L;
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return fallbackSeconds;
            }
            switch (matcher.group(2)) {
                case "d" -> total += amount * 86_400L;
                case "h" -> total += amount * 3_600L;
                case "m" -> total += amount * 60L;
                case "s" -> total += amount;
                default -> {
                }
            }
        }

        return matched ? total : fallbackSeconds;
    }

    /**
     * Форматирует секунды в человекочитаемый вид: "2д 3ч 15м", "5ч 40м", "12м".
     */
    public static String format(long seconds) {
        long value = Math.max(0L, seconds);
        long days = value / 86_400L;
        long hours = (value % 86_400L) / 3_600L;
        long minutes = (value % 3_600L) / 60L;

        if (days > 0) {
            return days + "д " + hours + "ч " + minutes + "м";
        }
        if (hours > 0) {
            return hours + "ч " + minutes + "м";
        }
        if (minutes > 0) {
            return minutes + "м";
        }
        return value + "с";
    }
}
