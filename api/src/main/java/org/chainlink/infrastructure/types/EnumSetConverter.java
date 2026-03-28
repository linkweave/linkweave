package org.chainlink.infrastructure.types;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Converter(autoApply = true)
public abstract class EnumSetConverter<E extends Enum<E>> implements AttributeConverter<EnumSet<E>, String> {

    public static final String DELIMITER = ",";
    private final Class<E> enumClass;

    protected EnumSetConverter(@NonNull Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    @Nullable
    public String convertToDatabaseColumn(@Nullable EnumSet<E> enumList) {
        if (enumList == null || enumList.isEmpty()) {
            return null;
        }
        return enumList.stream()
            .map(Enum::name)
            .collect(Collectors.joining(DELIMITER));
    }

    @Override
    @NonNull
    public EnumSet<E> convertToEntityAttribute(@Nullable String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return EnumSet.noneOf(enumClass);
        }
        return Arrays.stream(dbData.split(DELIMITER))
            .map(String::trim)
            .map(s -> Enum.valueOf(enumClass, s))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(enumClass)));
    }
}
