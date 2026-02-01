package org.chainlink.api.shared;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import lombok.experimental.UtilityClass;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

@UtilityClass
public final class Util {
    /**
     * Regex explained:
     * - (\\s): Any Whitespace, tab, newline, linebreak, Unicode whitespace character
     * - (-): a hyphen character
     * - (+): One or more (eg. "Hans - Peter", the split is " - " and not " ", "-", " ")
     * - (\\uFEFF): Zero-width space (Byte order mark), as it is not ecluded by \\s
     * - (Pattern.UNICODE_CHARACTER_CLASS): Ensures that the regex works with Unicode whitespace characters
     */
    private static final Pattern PATTERN_WHITESPACES_HYPHENS = Pattern.compile("[\\s-\\uFEFF]+", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Hilfsmethode fuer toString(): Wenn beim Debugging eine JPA-Referenz schon detached ist,
     * kann nicht mehr auf den Wert zugegriffen werden und es kommt eine Exception.
     * Diese Methode faengt die Exception ab und gibt einen fixen Text zurueck.
     * <pre>
     * {@code
     * 	public String toString() {
     * 		return MoreObjects.toStringHelper(this)
     * 			.add("id", getId())
     * 			.add("kontaktperson", getSilent(() -> kontaktperson))
     * 			.add("kind", getSilent(() -> kind))
     * 			.toString();
     *    }
     * }
     * </pre>
     */
    public static <T> String getSilent(Supplier<T> supplier) {
        try {
            return String.valueOf(supplier.get());
        } catch (RuntimeException ignored) {
            return "<unknown>";
        }
    }

    public static URL parseURL(String url) {
        try {
            return URI.create(url)
                .toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IllegalArgumentException("Not a valid URL: " + url, e);
        }
    }
    public static URL parseURI(URI uri) {
        try {
            return uri.toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
            throw new IllegalArgumentException("Not a valid URL: " + uri.toString(), e);
        }
    }

    public static URI parseURI(String uri) {
        return URI.create(uri);
    }

    /**
     * Find an enum entry by the given predicate.
     */
    public static <E extends Enum<?>> Optional<E> findEnumBy(Class<E> enumClass, Predicate<E> predicate) {
        return Arrays.stream(requireNonNull(enumClass.getEnumConstants()))
            .filter(predicate)
            .findFirst();
    }

    /**
     * See {@link #findEnumBy(Class, Predicate)} but throws {@link IllegalArgumentException} if no entry was found.
     */
    public static <E extends Enum<?>> E getEnum(Class<E> enumClass, Predicate<E> predicate) {
        return findEnumBy(enumClass, predicate)
            .orElseThrow(() -> new IllegalArgumentException(
                "unable to find enum " + enumClass.getName() + " for predicate"));
    }

    /**
     * Return the first Non-Null parameter or {@link Optional#empty()}.
     */
    @SuppressWarnings("varargs")
    @SafeVarargs
    public static <T> Optional<@Nullable T> coalesce(T... values) {
        return Arrays.stream(values)
            .filter(Objects::nonNull)
            .findFirst();
    }

    /**
     * Nicer replacement for the ternary operator.
     * <p>
     * Please note: eager evalation! For a lazy version, see {@link #ifNull(Object, Supplier)}.
     * </p>
     * <p>
     * Example:
     * <pre>
     * {@code
     * // Old style:
     * Object foo = (bar != null ? bar : new OtherObject())
     *
     * // New style:
     * Object foo = ifNull(bar, new OtherObject());
     * }
     * </pre>
     */
    public static <T> T ifNull(@Nullable T value, T valueIfNull) {
        return value != null
            ? value
            : valueIfNull;
    }

    /**
     * Nicer replacement for the ternary operator.
     * <p>
     * Example:
     * <pre>
     * {@code
     * // Old style:
     * Object foo = (bar != null ? bar : new OtherObject())
     *
     * // New style:
     * Object foo = ifNull(bar, () -> new OtherObject());
     * }
     * </pre>
     */
    public static <T> T ifNull(@Nullable T value, Supplier<T> valueIfNull) {
        return value != null
            ? value
            : valueIfNull.get();
    }

    public static <T, R> Optional<R> map(@Nullable T value, Function<T, @Nullable R> resolver) {
        if (value == null || resolver == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(resolver.apply(value));
    }

    /**
     * For use in functional contexts: {@code intList.stream().map(Util.mapNull(0))...}.
     */
    public static <T> Function<@Nullable T, T> mapNull(T valueIfNull) {
        return (T t) -> ifNull(t, valueIfNull);
    }

    /**
     * For use in functional contexts: {@code intList.stream().map(Util.mapNull(() -> calcFallback())))...}.
     */
    public static <T> Function<@Nullable T, T> mapNull(Supplier<T> valueIfNull) {
        return (T t) -> ifNull(t, valueIfNull);
    }

    public static <T, R> @Nullable R mapValue(@Nullable T value, Function<T, @Nullable R> resolver) {
        if (value == null || resolver == null) {
            return null;
        }
        return resolver.apply(value);
    }

    /**
     * Return the forst Non-Null/Non-Blank String or {@link Optional#empty()}.
     */
    public static Optional<String> coalesceString(@Nullable String... values) {
        return Arrays.stream(values)
            .map(StringUtils::trimToNull)
            .filter(Objects::nonNull)
            .findFirst();
    }

    /**
     * Convenience: get the current ContextClassLoader.
     */
    public static ClassLoader contextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    /**
     * Convenience: the URL to the resource in the classpath, throws if nonexistent.
     *
     * @throws NullPointerException If the resource does not exist
     */
    public static URL resourceURL(String resourcePath) {
        return requireNonNull(contextClassLoader().getResource(resourcePath), "Resource not found: " + resourcePath);
    }

    /**
     * Convenience: read a resource from the classpath, throws if nonexistent.
     *
     * @throws NullPointerException If the resource does not exist
     */
    public static byte[] readResource(String resourcePath) {
        try (InputStream is = contextClassLoader().getResourceAsStream(resourcePath)) {
            requireNonNull(is, "Resource not found: " + resourcePath);
            return IOUtils.toByteArray(is);
        } catch (IOException e) {
            //
            throw new IllegalStateException("Cannot read resource: " + resourcePath, e);
        }
    }

    @SafeVarargs
    public static <@Nullable T> boolean isIn(@Nullable T needle, T... haystack) {
        for (T t : haystack) {
            if (Objects.equals(needle, t)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public List<String> tokenizeStringKeepingQuotedParts(@NonNull String input, char delimiter, char quote) {
        Validate.isTrue(delimiter != quote);
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;
        for (char c : input.toCharArray()) {
            if (c == delimiter && !inQuotes) {
                tokens.add(token.toString());
                token.setLength(0);
            } else {
                if (c == quote) {
                    inQuotes = !inQuotes;
                    continue; // do not add the quote to the token
                }
                token.append(c);
            }
        }
        tokens.add(token.toString()); // add the last token
        return tokens;
    }

    @NonNull
    public List<String> tokenizeStringByWhiteSpacesOrHyphens(@NonNull String input) {
        if (input.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(PATTERN_WHITESPACES_HYPHENS.split(input))
            .filter(token -> !token.isEmpty())
            .toList();
    }
}
