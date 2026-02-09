package com.example.rate_limiter.util;

/**
 * Utility for formatting seconds into human-readable time durations.
 */
public class TimeFormatUtil {

    private TimeFormatUtil() {
        // Utility class - no instantiation
    }

    /**
     * Convert seconds to human-readable format.
     * Examples: "45 seconds", "5 minutes", "2 hours 30 minutes", "3 days 4 hours", "2 weeks 3 days"
     * 
     * @param seconds total seconds to format
     * @return human-readable duration string
     */
    public static String formatDuration(long seconds) {
        if (seconds < 0) {
            return "invalid";
        }

        if (seconds < 60) {
            return seconds + " second" + (seconds == 1 ? "" : "s");
        }

        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes < 60) {
            if (seconds > 0) {
                return minutes + " minute" + (minutes == 1 ? "" : "s") + " " + 
                       seconds + " second" + (seconds == 1 ? "" : "s");
            }
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }

        long hours = minutes / 60;
        minutes = minutes % 60;

        if (hours < 24) {
            if (minutes > 0) {
                return hours + " hour" + (hours == 1 ? "" : "s") + " " + 
                       minutes + " minute" + (minutes == 1 ? "" : "s");
            }
            return hours + " hour" + (hours == 1 ? "" : "s");
        }

        long days = hours / 24;
        hours = hours % 24;

        if (days < 7) {
            if (hours > 0) {
                return days + " day" + (days == 1 ? "" : "s") + " " + 
                       hours + " hour" + (hours == 1 ? "" : "s");
            }
            return days + " day" + (days == 1 ? "" : "s");
        }

        long weeks = days / 7;
        days = days % 7;

        if (days > 0) {
            return weeks + " week" + (weeks == 1 ? "" : "s") + " " + 
                   days + " day" + (days == 1 ? "" : "s");
        }
        return weeks + " week" + (weeks == 1 ? "" : "s");
    }
}
