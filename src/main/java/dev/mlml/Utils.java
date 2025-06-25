package dev.mlml;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.data.DataObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    /**
     * Checks if the given string is a valid Discord snowflake.
     *
     * @param input the string to check
     * @return true if the string is a valid snowflake, false otherwise
     */
    public static boolean stringIsSnowflake(String input) {
        return input.matches("^\\d{17,19}$");
    }

    /**
     * Checks if the given string is a valid Discord user mention.
     *
     * @param input the string to check
     * @return true if the string is a valid user mention, false otherwise
     */
    public static boolean stringIsUserMention(String input) {
        return input.matches("^<@!?\\d{17,19}>$");
    }

    /**
     * Checks if the given string is a valid Discord channel mention.
     *
     * @param input the string to check
     * @return true if the string is a valid channel mention, false otherwise
     */
    public static boolean stringIsChannelMention(String input) {
        return input.matches("^<#\\d{17,19}>$");
    }

    /**
     * Converts a time duration in milliseconds to a human-readable string.
     *
     * @param milliseconds the time duration in milliseconds
     * @return a human-readable string representing the time duration
     */
    public static String timePrettyPrint(long milliseconds) {
        if (milliseconds < 1000) {
            return "1 second";
        }

        long hours = milliseconds / 3600000;
        milliseconds %= 3600000;

        long minutes = milliseconds / 60000;
        milliseconds %= 60000;

        long seconds = milliseconds / 1000;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(" hour");
            if (hours > 1) {
                sb.append("s");
            }
            sb.append(" ");
        }
        if (minutes > 0) {
            sb.append(minutes).append(" minute");
            if (minutes > 1) {
                sb.append("s");
            }
            sb.append(" ");
        }
        if (seconds > 0) {
            sb.append(seconds).append(" second");
            if (seconds > 1) {
                sb.append("s");
            }
            sb.append(" ");
        }

        return sb.toString().trim();
    }

    /**
     * Sends a GET request to the specified URL and returns the response as a DataObject.
     *
     * @param urlString the URL to send the GET request to
     * @return the response as a DataObject, or null if the request failed
     */
    public static DataObject sendGetRequest(String urlString) {
        DataObject result = null;
        try {
            URL url = new URL(urlString);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");

            connection.setRequestProperty("Content-Type", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                result = DataObject.fromJson(in);
                in.close();
            } else {
                logger.debug("Failed to send GET request");
            }
        } catch (Exception e) {
            logger.debug(e.getMessage());
        }
        return result;
    }

    /**
     * Sends a POST request to the specified URL with the given body and returns the response as a DataObject.
     *
     * @param urlString the URL to send the POST request to
     * @param body      the body of the POST request
     * @return the response as a DataObject, or null if the request failed
     */
    public static DataObject sendPostRequest(String urlString, String body) {
        DataObject result = null;
        try {
            URL url = new URL(urlString);

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");

            connection.setRequestProperty("Content-Type", "application/json");

            connection.setDoOutput(true);
            connection.getOutputStream().write(body.getBytes());

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                result = DataObject.fromJson(in);
                in.close();
            } else {
                logger.debug("Failed to send POST request");
            }
        } catch (Exception e) {
            logger.debug(e.getMessage());
        }
        return result;
    }

    /**
     * Parses a duration string in the format "XdYhZm" (e.g., "2d5h30m") into a Duration object.
     * Days, hours, and minutes are optional, but the duration cannot be zero.
     *
     * @param input the duration string to parse
     * @return a Duration object representing the parsed duration
     * @throws IllegalArgumentException if the input format is invalid or the duration is zero
     */
    public static Duration parseDuration(String input) throws IllegalArgumentException {
        Pattern pattern = Pattern.compile("(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");
        Matcher matcher = pattern.matcher(input.toLowerCase());

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration format");
        }

        long days = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
        long hours = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0;
        long minutes = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;
        long seconds = matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0;

        if (days == 0 && hours == 0 && minutes == 0 && seconds == 0) {
            throw new IllegalArgumentException("Duration cannot be zero");
        }

        return Duration.ofDays(days).plusHours(hours).plusMinutes(minutes).plusSeconds(seconds);
    }
}