package org.chainlink.api.shared.archunit.predicates;

import java.lang.annotation.Annotation;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaField;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AnnotationPredicates {

    public static <A extends Annotation> DescribedPredicate<? super JavaAnnotation<?>> annotationThatHas(
        Class<A> annotationClazz,
        DescribedPredicate<A> condition
    ) {
        return DescribedPredicate.describe(
            "annotation '%s' has %s".formatted(annotationClazz.getSimpleName(), condition.getDescription()),
            input -> {
                if (!input.getRawType().isAssignableTo(annotationClazz)) {
                    return false;
                }
                var annotation = input.as(annotationClazz);
                return condition.test(annotation);
            }
        );
    }

    public static DescribedPredicate<? super JavaField> typeAnnotation(Class<? extends Annotation> annotationClass) {
        return DescribedPredicate.describe(
            "type annotation '%s'".formatted(annotationClass.getSimpleName()),
            field -> field.reflect().getAnnotatedType().isAnnotationPresent(annotationClass)
        );
    }
}
