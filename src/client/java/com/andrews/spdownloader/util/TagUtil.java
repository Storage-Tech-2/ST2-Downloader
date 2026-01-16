package com.andrews.spdownloader.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.andrews.spdownloader.gui.theme.UITheme;

/**
 * Shared helper for consistent tag colors across widgets.
 */
public final class TagUtil {

    private TagUtil() {
    }

    public static int getTagColor(String tag) {
        if (tag == null) {
            return UITheme.Colors.BUTTON_BG;
        }
        String lower = tag.toLowerCase();
        if (lower.contains("untested")) {
            return 0xFF8C6E00;
        }
        if (lower.contains("broken")) {
            return 0xFF8B1A1A;
        }
        if (lower.contains("tested") || lower.contains("functional")) {
            return 0xFF1E7F1E;
        }
        if (lower.contains("recommend")) {
            return 0xFFB8860B;
        }
        return UITheme.Colors.BUTTON_BG;
    }

    public static List<String> orderTags(String[] tags) {
        List<String> tagList = List.of(tags);
        return orderTags(tagList);
    }

    public static List<String> orderTags(List<String> tags) {
        if (tags == null) return List.of();
        List<String> specials = List.of("untested", "broken", "tested & functional", "recommended");
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String s : specials) {
            for (String tag : tags) {
                if (tag == null) continue;
                if (tag.toLowerCase().equals(s) && seen.add(tag.toLowerCase())) {
                    ordered.add(tag);
                }
            }
        }
        for (String tag : tags) {
            if (tag == null) continue;
            String key = tag.toLowerCase();
            if (!seen.contains(key)) {
                ordered.add(tag);
                seen.add(key);
            }
        }
        return ordered;
    }
}


