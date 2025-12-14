package com.msi.videoplayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SubtitleUtils {

    // Structure to hold one subtitle line
    public static class SubtitleItem {
        long startTime;
        long endTime;
        String text;

        public SubtitleItem(long startTime, long endTime, String text) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.text = text;
        }
    }

    // Parse .srt file
    public static List<SubtitleItem> parseSrt(String path) {
        List<SubtitleItem> subtitleList = new ArrayList<>();
        File file = new File(path);

        if (!file.exists()) return subtitleList;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            long startTime = -1;
            long endTime = -1;
            StringBuilder textBuilder = new StringBuilder();

            // Regex for time format: 00:00:20,000 --> 00:00:24,400
            Pattern timePattern = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s-->\\s(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})");

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip index numbers (1, 2, 3...)
                if (line.isEmpty() || isNumeric(line)) {
                    // Push previous subtitle if exists
                    if (startTime != -1 && textBuilder.length() > 0) {
                        subtitleList.add(new SubtitleItem(startTime, endTime, textBuilder.toString().trim()));
                        textBuilder.setLength(0); // Reset
                        startTime = -1;
                    }
                    continue;
                }

                Matcher matcher = timePattern.matcher(line);
                if (matcher.find()) {
                    // It's a time line
                    startTime = parseTime(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
                    endTime = parseTime(matcher.group(5), matcher.group(6), matcher.group(7), matcher.group(8));
                } else {
                    // It's text content
                    if (startTime != -1) {
                        textBuilder.append(line).append("\n");
                    }
                }
            }
            // Add last item
            if (startTime != -1 && textBuilder.length() > 0) {
                subtitleList.add(new SubtitleItem(startTime, endTime, textBuilder.toString().trim()));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return subtitleList;
    }

    // Helper: Convert time parts to milliseconds
    private static long parseTime(String h, String m, String s, String ms) {
        return (Long.parseLong(h) * 3600000) +
               (Long.parseLong(m) * 60000) +
               (Long.parseLong(s) * 1000) +
               Long.parseLong(ms);
    }

    private static boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}