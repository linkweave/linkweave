package org.linkweave.api.shared.archunit;

import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ArchConst {

    public static final String APP_PACKAGE = "org.linkweave";

    // Excludes both Hibernate's "EntityName_.class" JPA static metamodel and the
    // jakarta.data "_EntityName.class" variant -- both are framework-generated.
    private static final ImportOption EXCLUDE_JPA_STATIC_METAMODEL =
        location -> {
            String path = location.asURI().getPath();
            return !path.matches(".*_\\.class")
                && !path.matches(".*/_[A-Z][A-Za-z0-9]*\\.class");
        };

    private static final Set<ImportOption> IMPORT_OPTIONS = Set.of(
        new ImportOption.DoNotIncludeTests(),
        new ImportOption.DoNotIncludeArchives(),
        EXCLUDE_JPA_STATIC_METAMODEL
    );
    private static final Set<ImportOption> IMPORT_OPTIONS_FOR_TESTS = Set.of(
        new ImportOption.OnlyIncludeTests()
    );

    public static final JavaClasses APP_CLASSES = new ClassFileImporter(IMPORT_OPTIONS)
        .importPackages(APP_PACKAGE);

    public static final JavaClasses TEST_CLASSES = new ClassFileImporter(IMPORT_OPTIONS_FOR_TESTS)
        .importPackages(APP_PACKAGE);

    @UtilityClass
    public static class Pattern {
        public static final java.util.regex.Pattern STARTS_WITH_LOWERCASE_FOLLOWED_BY_ALPHANUMERIC =
            java.util.regex.Pattern.compile("^[a-z]+[A-Za-z0-9]*");
    }
}
