package org.linkweave.infrastructure.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard: every JSON String must be trimmed and stripped of unprintable
 * control characters. This behaviour is registered globally by
 * {@link LinkweaveJacksonCustomizer}; the test goes through the real customizer so
 * that silently dropping the {@link StringNormalizerModule} registration fails here.
 */
class StringNormalizerModuleTest {

    private record Box(String value) {
    }

    private static ObjectMapper customizedMapper() {
        ObjectMapper mapper = new ObjectMapper();
        new LinkweaveJacksonCustomizer().customize(mapper);
        return mapper;
    }

    private static String roundTripValue(String rawJsonStringContent) throws Exception {
        return customizedMapper()
            .readValue("{\"value\":\"" + rawJsonStringContent + "\"}", Box.class)
            .value();
    }

    @Test
    void shouldTrimLeadingAndTrailingWhitespaceOnDeserialize() throws Exception {
        assertThat(roundTripValue("  hello world  ")).isEqualTo("hello world");
    }

    @Test
    void shouldRemoveUnprintableControlCharactersOnDeserialize() throws Exception {
        // JSON escapes for NUL (0x00) and BEL (0x07); both are unprintable and must be stripped.
        assertThat(roundTripValue("ab\\u0000c\\u0007d")).isEqualTo("abcd");
    }

    @Test
    void shouldKeepPrintableWhitespaceTabAndNewline() throws Exception {
        // Tab, LF and CR are printable whitespace and must survive in the interior
        // (only leading/trailing whitespace is trimmed).
        assertThat(roundTripValue("a\\tb\\nc")).isEqualTo("a\tb\nc");
    }

    @Test
    void shouldRemoveUnprintableControlCharactersOnSerialize() throws Exception {
        // ARRANGE
        // NUL (0x00) and BEL (0x07) embedded between the printable characters.
        String value = "ab" + (char) 0x00 + "c" + (char) 0x07 + "d";
        // ACT
        String json = customizedMapper().writeValueAsString(new Box(value));
        // ASSERT
        assertThat(json).isEqualTo("{\"value\":\"abcd\"}");
    }

    @Test
    void shouldLeaveCleanStringsUntouched() throws Exception {
        assertThat(roundTripValue("already clean")).isEqualTo("already clean");
    }
}
