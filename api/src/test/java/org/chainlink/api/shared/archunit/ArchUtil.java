package org.chainlink.api.shared.archunit;

import java.util.Collection;
import java.util.List;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArchUtil {

    /**
     * Usual usecase is for lists/optionals, to make it generic results in much more work.
     *
     * @param type get actual type of
     * @return in case its #{@link JavaParameterizedType} the first type parameter is returned, the input instead.
     */
    public static JavaType getActualType(JavaType type) {
        return type instanceof JavaParameterizedType
            ? ((JavaParameterizedType) type).getActualTypeArguments().getFirst()
            : type;
    }

    public static List<JavaAnnotation<JavaParameter>> parameterAnnotations(JavaMethod method) {
        return method.getParameters().stream()
            .map(JavaParameter::getAnnotations)
            .flatMap(Collection::stream)
            .toList();
    }

}
