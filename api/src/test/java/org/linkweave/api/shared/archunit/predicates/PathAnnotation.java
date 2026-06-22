package org.linkweave.api.shared.archunit.predicates;

import java.util.regex.Pattern;

import com.tngtech.archunit.base.DescribedPredicate;
import jakarta.ws.rs.Path;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.checkerframework.checker.nullness.qual.NonNull;

@UtilityClass
public class PathAnnotation {

    public static DescribedPredicate<Path> pathWithOptionalKebabCaseSegments() {
        Pattern urlWithOptionalKebabCaseSegments =
            Pattern.compile("^/?([a-z0-9])+(?:-[a-z0-9]+)*(/([a-z0-9])+(?:-[a-z0-9]+)*)*$");

        return new PathRegexPredicate(
            urlWithOptionalKebabCaseSegments,
            "URL with optional kebab-case segments"
        );
    }

    public static DescribedPredicate<Path> pathWithSafeChars() {
        Pattern urlsafeChars = Pattern.compile("[a-zA-Z0-9:._\\-/{}]*");

        return new PathRegexPredicate(urlsafeChars, "Characters allowed in URLs");
    }

    private static class PathRegexPredicate extends DescribedPredicate<Path> {

        private final Pattern pattern;

        public PathRegexPredicate(Pattern pattern, String regexInfo) {
            super("value matches " + pattern + " (" + regexInfo + ')');
            this.pattern = pattern;
        }

        @Override
        public boolean test(Path path) {
            // Wir ersetzen alle Path-Parameter durch x, damit wir nur den Pfad prüfen (inkl. korrekte Anzahl /)
            String value = replaceParams(path.value());
            return pattern.matcher(value).matches();
        }

        @NonNull
        private String replaceParams(@NonNull String value) {
            // Param-Namen werden nicht vorgeschrieben. Nur die Verschachtelung ({}) muss stimmen
            int index = value.indexOf('{');
            while (index >= 0) {
                int endIndex = value.indexOf('}');
                String param = value.substring(index, endIndex + 1);
                validateParam(param);
                value = Strings.CS.replace(value, param, "x");
                index = value.indexOf('{');
            }
            return value;
        }

        private void validateParam(@NonNull String param) {
            // Es darf nur je 1 { und } geben
            if (StringUtils.countMatches(param, "{") != 1 || StringUtils.countMatches(param, "}") != 1) {
                throw new IllegalArgumentException("Invalid path parameter: " + param);
            }
        }
    }
}
