package org.linkweave.api.shared;

import java.net.URI;
import java.net.URL;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UtilTest {

    @Nested
    class GetSilent {

        @Test
        void shouldReturnValueAsString() {
            String result = Util.getSilent(() -> 42);

            assertThat(result).isEqualTo("42");
        }

        @Test
        void shouldReturnUnknownOnException() {
            String result = Util.getSilent(() -> {
                throw new RuntimeException("boom");
            });

            assertThat(result).isEqualTo("<unknown>");
        }

        @Test
        void shouldHandleNullValue() {
            String result = Util.getSilent(() -> null);

            assertThat(result).isEqualTo("null");
        }
    }

    @Nested
    class ParseURL {

        @Test
        void shouldParseValidUrl() {
            URL url = Util.parseURL("https://example.com/path");

            assertThat(url).hasToString("https://example.com/path");
        }

        @Test
        void shouldThrowOnInvalidUrl() {
            assertThatThrownBy(() -> Util.parseURL("not a url"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not a valid URL");
        }
    }

    @Nested
    class ParseURIFromURI {

        @Test
        void shouldConvertValidUri() {
            URI uri = URI.create("https://example.com");
            URL url = Util.parseURI(uri);

            assertThat(url).hasToString("https://example.com");
        }

        @Test
        void shouldThrowOnMalformedUri() {
            // file:/// is valid URI but toURL() may succeed; use a deliberately bad scheme
            URI badUri = URI.create("foo:bar:baz");
            assertThatThrownBy(() -> Util.parseURI(badUri))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ParseURIFromString {

        @Test
        void shouldCreateUriFromString() {
            URI uri = Util.parseURI("https://example.com");

            assertThat(uri.getHost()).isEqualTo("example.com");
        }
    }

    @Nested
    class FindEnumBy {

        @Test
        void shouldFindMatchingEnum() {
            Optional<TestEnum> result = Util.findEnumBy(TestEnum.class, e -> e.value == 2);

            assertThat(result).contains(TestEnum.BAR);
        }

        @Test
        void shouldReturnEmptyWhenNoMatch() {
            Optional<TestEnum> result = Util.findEnumBy(TestEnum.class, e -> e.value == 99);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetEnum {

        @Test
        void shouldReturnMatchingEnum() {
            TestEnum result = Util.getEnum(TestEnum.class, e -> e.value == 3);

            assertThat(result).isEqualTo(TestEnum.BAZ);
        }

        @Test
        void shouldThrowWhenNoMatch() {
            assertThatThrownBy(() -> Util.getEnum(TestEnum.class, e -> e.value == 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unable to find enum");
        }
    }

    @Nested
    class Coalesce {

        @Test
        void shouldReturnFirstNonNull() {
            Optional<String> result = Util.coalesce(null, "second", "third");

            assertThat(result).contains("second");
        }

        @Test
        void shouldReturnEmptyWhenAllNull() {
            Optional<String> result = Util.coalesce(null, null);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnFirstValue() {
            Optional<String> result = Util.coalesce("first", "second");

            assertThat(result).contains("first");
        }
    }

    @Nested
    class IfNull {

        @Test
        void shouldReturnValueWhenNotNull() {
            String result = Util.ifNull("value", "default");

            assertThat(result).isEqualTo("value");
        }

        @Test
        void shouldReturnDefaultWhenNull() {
            String result = Util.ifNull(null, "default");

            assertThat(result).isEqualTo("default");
        }

        @Test
        void shouldReturnValueWhenNotNullLazy() {
            String result = Util.ifNull("value", () -> "default");

            assertThat(result).isEqualTo("value");
        }

        @Test
        void shouldCallSupplierWhenNull() {
            String result = Util.ifNull(null, () -> "computed");

            assertThat(result).isEqualTo("computed");
        }
    }

    @Nested
    class Map {

        @Test
        void shouldMapNonNullValue() {
            Optional<Integer> result = Util.map("5", Integer::parseInt);

            assertThat(result).contains(5);
        }

        @Test
        void shouldReturnEmptyForNullInput() {
            Optional<Integer> result = Util.<String, Integer>map(null, Integer::parseInt);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyForNullResolver() {
            Function<String, Integer> nullResolver = null;
            Optional<Integer> result = Util.map("5", nullResolver);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class MapNull {

        @Test
        void shouldReplaceNullWithValue() {
            Function<String, String> replacer = Util.mapNull("fallback");

            assertThat(replacer.apply("actual")).isEqualTo("actual");
            assertThat(replacer.apply(null)).isEqualTo("fallback");
        }

        @Test
        void shouldReplaceNullWithSupplier() {
            Function<String, String> replacer = Util.mapNull(() -> "supplied");

            assertThat(replacer.apply("actual")).isEqualTo("actual");
            assertThat(replacer.apply(null)).isEqualTo("supplied");
        }
    }

    @Nested
    class MapValue {

        @Test
        void shouldMapNonNullValue() {
            Integer result = Util.mapValue("42", Integer::parseInt);

            assertThat(result).isEqualTo(42);
        }

        @Test
        void shouldReturnNullForNullInput() {
            Integer result = Util.<String, Integer>mapValue(null, Integer::parseInt);

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullForNullResolver() {
            Function<String, Integer> nullResolver = null;
            Integer result = Util.mapValue("42", nullResolver);

            assertThat(result).isNull();
        }
    }

    @Nested
    class CoalesceString {

        @Test
        void shouldReturnFirstNonBlank() {
            Optional<String> result = Util.coalesceString(null, "  ", "hello", "world");

            assertThat(result).contains("hello");
        }

        @Test
        void shouldReturnEmptyWhenAllBlank() {
            Optional<String> result = Util.coalesceString(null, "", "   ");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class IsIn {

        @Test
        void shouldReturnTrueWhenNeedleFound() {
            boolean result = Util.isIn("b", "a", "b", "c");

            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseWhenNeedleNotFound() {
            boolean result = Util.isIn("x", "a", "b", "c");

            assertThat(result).isFalse();
        }

        @Test
        void shouldHandleNullNeedle() {
            boolean result = Util.isIn(null, "a", null, "c");

            assertThat(result).isTrue();
        }
    }

    @Nested
    class TokenizeStringKeepingQuotedParts {

        @Test
        void shouldSplitByDelimiter() {
            var tokens = Util.tokenizeStringKeepingQuotedParts("a,b,c", ',', '"');

            assertThat(tokens).containsExactly("a", "b", "c");
        }

        @Test
        void shouldKeepQuotedPartsIntact() {
            var tokens = Util.tokenizeStringKeepingQuotedParts("a,\"b,c\",d", ',', '"');

            assertThat(tokens).containsExactly("a", "b,c", "d");
        }

        @Test
        void shouldHandleEmptyInput() {
            var tokens = Util.tokenizeStringKeepingQuotedParts("", ',', '"');

            assertThat(tokens).containsExactly("");
        }
    }

    @Nested
    class TokenizeStringByWhiteSpacesOrHyphens {

        @Test
        void shouldSplitBySpaces() {
            var tokens = Util.tokenizeStringByWhiteSpacesOrHyphens("hello world");

            assertThat(tokens).containsExactly("hello", "world");
        }

        @Test
        void shouldSplitByHyphens() {
            var tokens = Util.tokenizeStringByWhiteSpacesOrHyphens("Hans-Peter");

            assertThat(tokens).containsExactly("Hans", "Peter");
        }

        @Test
        void shouldReturnEmptyListForEmptyInput() {
            var tokens = Util.tokenizeStringByWhiteSpacesOrHyphens("");

            assertThat(tokens).isEmpty();
        }
    }

    @Nested
    class ResourceAndClassLoader {

        @Test
        void shouldReturnContextClassLoader() {
            ClassLoader cl = Util.contextClassLoader();

            assertThat(cl).isNotNull();
        }

        @Test
        void shouldReadExistingResource() {
            // application.properties always exists
            byte[] content = Util.readResource("application.properties");

            assertThat(content).isNotEmpty();
        }

        @Test
        void shouldGetResourceUrl() {
            URL url = Util.resourceURL("application.properties");

            assertThat(url).isNotNull();
        }
    }

    private enum TestEnum {
        FOO(1), BAR(2), BAZ(3);

        final int value;

        TestEnum(int value) {
            this.value = value;
        }
    }
}
