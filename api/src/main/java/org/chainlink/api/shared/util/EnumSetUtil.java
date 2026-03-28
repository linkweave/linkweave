package org.chainlink.api.shared.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class EnumSetUtil {

    public static final String DELIMITER = ",";

    private EnumSetUtil() {
    }


    @NonNull
    public static <E extends Enum<E>> EnumSet<E> toEnumSet(@Nullable Collection<E> list, Class<E> enumClass) {
        if (list == null || list.isEmpty()) {
            return EnumSet.noneOf(enumClass);
        }
        return EnumSet.copyOf(list);
    }

    @NonNull
    public static <E extends Enum<E>> List<E> toList(@Nullable EnumSet<E> enumSet) {
        if (enumSet == null || enumSet.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(enumSet);
    }

    @Nullable
    public static <E extends Enum<E>> String toString(@Nullable Collection<E> enumList) {
        if (enumList == null || enumList.isEmpty()) {
            return null;
        }
        return enumList.stream()
            .sorted()
            .map(Enum::name)
            .collect(Collectors.joining(DELIMITER));
    }

    @NonNull
    public static <E extends Enum<E>> EnumSet<E> fromString(@NonNull Class<E> enumClass, @Nullable String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return EnumSet.noneOf(enumClass);
        }
        return Arrays.stream(dbData.split(DELIMITER))
            .map(String::trim)
            .map(s -> Enum.valueOf(enumClass, s))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(enumClass)));
    }
}
