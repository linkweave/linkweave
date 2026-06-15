package org.chainlink.api.shared.archunit;

import com.querydsl.core.types.dsl.EntityPathBase;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.dependencies.Slice;
import jakarta.persistence.Entity;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.infrastructure.stereotypes.JaxDTO;
import org.chainlink.infrastructure.stereotypes.JaxMapper;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.chainlink.infrastructure.stereotypes.NoTransactionService;
import org.chainlink.infrastructure.stereotypes.Repository;
import org.chainlink.infrastructure.stereotypes.Service;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.or;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.is;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.codeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;
import static org.chainlink.api.shared.archunit.ArchConst.APP_CLASSES;
import static org.chainlink.api.shared.archunit.ArchConst.APP_PACKAGE;
import static org.chainlink.api.shared.archunit.ArchConst.STARTER_PACKAGE;
import static org.chainlink.api.shared.archunit.predicates.ClassPredicates.interfaceWithSuffix;

@DisplayNameGeneration(ReplaceUnderscores.class)
class LayeringTest {
    public static final DescribedPredicate<JavaClass> SERVICE =
        is(interfaceWithSuffix("Service")).or(is(metaAnnotatedWith(Service.class)).or(is(metaAnnotatedWith(
            NoTransactionService.class))));
    public static final DescribedPredicate<JavaClass> UTIL =
        is(JavaClass.Predicates.simpleNameEndingWith("Util").or(is(interfaceWithSuffix("Util"))));

    public static final DescribedPredicate<JavaClass> NO_TRANSACTION_SERVICE =
        is(metaAnnotatedWith(NoTransactionService.class));
    public static final DescribedPredicate<JavaClass> REPOSITORY =
        is(interfaceWithSuffix("Repo")).or(is(metaAnnotatedWith(Repository.class)));
    public static final DescribedPredicate<JavaClass> RESOURCE = metaAnnotatedWith(JaxResource.class).forSubtype();
    public static final DescribedPredicate<JavaClass> RESOURCE_MAPPER = metaAnnotatedWith(JaxMapper.class).forSubtype();

    public static final DescribedPredicate<JavaClass> JSON_DTO = metaAnnotatedWith(JaxDTO.class).forSubtype();
    public static final DescribedPredicate<JavaClass> ENTITY = annotatedWith(Entity.class).forSubtype();

    private static final DescribedPredicate<JavaClass> QUERYDSL_Q_CLASSES =
        simpleNameStartingWith("Q").and(assignableTo(EntityPathBase.class));

    public static final DescribedPredicate<JavaClass> SHARED = resideInAPackage("org.chainlink.api.shared..");

    private static final DescribedPredicate<JavaClass> DATABASE_LAYER =
        resideInAPackage("org.chainlink.infrastructure.db..")
            .or(resideInAPackage("org.chainlink.api.shared.abstractentity.."))
            .or(LayeringTest.REPOSITORY)
            .or(ENTITY)
            .or(QUERYDSL_Q_CLASSES);


    public static final DescribedPredicate<JavaClass> SHARED_PRINTER =
        resideInAPackage("org.chainlink.api.shared.printer..");

    public static final DescribedPredicate<JavaClass> CSV_COLUMNS =
        resideInAPackage("org.chainlink.api.features.spss.columns..");

    public static final DescribedPredicate<JavaClass> DOC_MERGER =
        resideInAPackage("org.chainlink.api.features.briefvorlagen.briefgenerierung.docmerger..");

    public static final DescribedPredicate<JavaClass> STARTER_SHARED = resideInAPackage(STARTER_PACKAGE + "..");
    public static final DescribedPredicate<JavaClass> STARTER_INFRASTRUCTURE =
        resideInAPackage("org.chainlink.infrastructure..");


    private static final Architectures.LayeredArchitecture LAYERS = layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("Resource")
        .definedBy(or(RESOURCE))
        .layer("ResourceMapper")
        .definedBy(RESOURCE_MAPPER)
        .layer("Service")
        .definedBy(SERVICE)
        .layer("Database")
        .definedBy(DATABASE_LAYER)
        .layer("Entity")
        .definedBy(ENTITY)
        .layer("Shared")
        .definedBy(SHARED)
        .layer("Infrastructure")
        .definedBy(STARTER_SHARED.or(STARTER_INFRASTRUCTURE));

    @Test
    void app_package_exists() {
        assertThat(APP_CLASSES.stream().anyMatch(c -> c.getPackageName().startsWith(APP_PACKAGE + ".")))
            .as("app package %s.* contains classes", APP_PACKAGE)
            .isTrue();
    }

    @Test
    void starter_package_exists() {
        assertThat(APP_CLASSES.stream().anyMatch(c -> c.getPackageName().startsWith(STARTER_PACKAGE + ".")))
            .as("starter package %s.* contains classes", STARTER_PACKAGE)
            .isTrue();
    }

    @Test
    void test_layer_boundaries() {
        LAYERS
            .whereLayer("Resource")
            .mayNotBeAccessedByAnyLayer()
            .check(APP_CLASSES);

        LAYERS
            .whereLayer("ResourceMapper")
            .mayOnlyBeAccessedByLayers("Resource")
            .check(APP_CLASSES);
    }

    @Test
    void test_layer_boundaries_entities_codeUnits() {
        final ArchRule rule = codeUnits().that()
            .areDeclaredInClassesThat(are(DATABASE_LAYER))
            .should()
            .onlyBeCalled()
            .byClassesThat(are(DATABASE_LAYER).or(SERVICE)
                .or(RESOURCE_MAPPER)
                .or(STARTER_INFRASTRUCTURE)
                .or(STARTER_SHARED)
                .or(SHARED_PRINTER)
                .or(UTIL)
                .or(CSV_COLUMNS)
                .or(DOC_MERGER)
            )
            .because("Entities should not be used in Resource Layer");
        rule.allowEmptyShould(true).check(APP_CLASSES);
    }

    @Test
    void test_layer_boundaries_entities_constructors() {
        final ArchRule rule = constructors().that()
            .areDeclaredInClassesThat(are(DATABASE_LAYER))
            .should()
            .onlyBeCalled()
            .byClassesThat(are(DATABASE_LAYER).or(SERVICE)
                .or(RESOURCE_MAPPER)
                .or(STARTER_INFRASTRUCTURE)
                .or(STARTER_SHARED)
                .or(DOC_MERGER))
            .because("Entities should not be used in Resource Layer");
        rule.allowEmptyShould(true).check(APP_CLASSES);
    }

    @Test
    void test_layer_boundaries_entities_methods() {
        final ArchRule rule = methods().that()
            .areDeclaredInClassesThat(are(DATABASE_LAYER))
            .should()
            .onlyBeCalled()
            .byClassesThat(are(DATABASE_LAYER).or(SERVICE).or(UTIL)
                .or(RESOURCE_MAPPER)
                .or(STARTER_INFRASTRUCTURE)
                .or(STARTER_SHARED)
                .or(SHARED_PRINTER)
                .or(CSV_COLUMNS)
                .or(DOC_MERGER)
            )
            .because("Entities should not be used in Resource Layer");
        rule.allowEmptyShould(true).check(APP_CLASSES);
    }

    @Test
    void no_cycles_between_features() {
        var rule = slices()
            .matching(APP_PACKAGE + ".features.(*)..")
            .should()
            .beFreeOfCycles()
            .because("Cycles between feature decrease maintainability. Introduce a new shared feature");
        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void no_cross_feature_repo_access() {
        // FIXME: sinnlos: repos duerfen nicht Repos von anderen Features brauchen... aber alles Andere schon?
        //  Ausserdem: das ein Repo ein Anderes einbaut ist schon seeeeehr selten.
        //  Umbauen auf: Features duerfen nur Service-Interfaces von anderen Features brauchen.
        var rule = slices()
            .matching(ArchConst.APP_PACKAGE + ".features.(*).repo")
            .that(areNotInTheCommonPackage())
            .should()
            .notDependOnEachOther()
            .because("Use a service to access repos outside of the feature package");
        rule.allowEmptyShould(true).check(ArchConst.APP_CLASSES);
    }

    @Test
    void authorizer_service_should_only_be_called_from_resource() {
        var rule = methods().that()
            .areDeclaredIn(AuthorizationService.class)
            .should()
            .onlyBeCalled()
            .byClassesThat(are(RESOURCE).or(are(assignableTo(AuthorizationService.class))))
            .because("AuthorizationService should only be called from Resource layer (and may call itself)");
        rule.allowEmptyShould(true).check(APP_CLASSES);
    }

    private static @NonNull DescribedPredicate<Slice> areNotInTheCommonPackage() {
        return new DescribedPredicate<>("features") {
            @Override
            public boolean test(Slice javaClasses) {
                return !javaClasses.getNamePart(1).equals("common");
            }
        };
    }



}
