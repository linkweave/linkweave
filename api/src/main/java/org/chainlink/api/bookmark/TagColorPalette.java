package org.chainlink.api.bookmark;

import java.util.List;

import lombok.experimental.UtilityClass;

@UtilityClass
public class TagColorPalette {

    private static final List<String> COLORS = List.of(
        "#ef4444",
        "#f97316",
        "#eab308",
        "#22c55e",
        "#06b6d4",
        "#3b82f6",
        "#8b5cf6",
        "#ec4899",
        "#64748b",
        "#14b8a6"
    );

    public static String autoAssignColor(int existingTagCount) {
        return COLORS.get(existingTagCount % COLORS.size());
    }
}
