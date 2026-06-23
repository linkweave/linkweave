package org.linkweave.api.shared.archunit;

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

    /**
     * Converts a Java class name (CamelCase) to the Hibernate default physical table name
     * (snake_case): {@code AutoTagRule} → {@code auto_tag_rule}. Matches the convention
     * used by {@code PhysicalNamingStrategyStandardImpl} which LinkWeave runs with.
     */
    public static String tableNameOf(String simpleClassName) {
        return simpleClassName.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "_").toLowerCase();
    }

    /**
     * Like {@link #tableNameOf} but as a regex fragment that matches either the snake_case
     * form ({@code auto_tag_rule}) or the all-lowercase-concatenated form
     * ({@code autotagrule}) — LinkWeave's constraint names mix both conventions across
     * migrations.
     */
    public static String tableNamePatternOf(String simpleClassName) {
        return simpleClassName.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "_?").toLowerCase();
    }

}
