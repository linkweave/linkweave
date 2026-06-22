package org.linkweave.api.shared.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HostPatternSetTest {

    @Test
    void shouldMatchNothingWhenEmpty() {
        HostPatternSet list = HostPatternSet.parse(null);
        assertThat(list.isEmpty()).isTrue();
        assertThat(list.matches("example.com")).isFalse();
        assertThat(HostPatternSet.parse("  ").isEmpty()).isTrue();
    }

    @Test
    void shouldMatchExactHostCaseInsensitively() {
        HostPatternSet list = HostPatternSet.parse("intranet.local");
        assertThat(list.matches("intranet.local")).isTrue();
        assertThat(list.matches("INTRANET.LOCAL")).isTrue();
        assertThat(list.matches("other.local")).isFalse();
        assertThat(list.matches("sub.intranet.local")).isFalse();
    }

    @Test
    void shouldMatchWildcardSubdomainsAndApex() {
        HostPatternSet list = HostPatternSet.parse("*.mycompany.domain");
        assertThat(list.matches("wiki.mycompany.domain")).isTrue();
        assertThat(list.matches("a.b.mycompany.domain")).isTrue();
        assertThat(list.matches("mycompany.domain")).isTrue(); // bare apex
        assertThat(list.matches("notmycompany.domain")).isFalse();
        assertThat(list.matches("mycompany.domain.evil.com")).isFalse();
    }

    @Test
    void shouldParseCommaAndNewlineSeparatedAndNormalize() {
        HostPatternSet list = HostPatternSet.parse("  Foo.Example , bar.example\n*.baz.example\nfoo.example");
        assertThat(list.patterns()).containsExactly("foo.example", "bar.example", "*.baz.example");
        assertThat(list.matches("foo.example")).isTrue();
        assertThat(list.matches("x.baz.example")).isTrue();
    }

    @Test
    void shouldNotMatchBlankHost() {
        HostPatternSet list = HostPatternSet.parse("example.com");
        assertThat(list.matches(null)).isFalse();
        assertThat(list.matches("")).isFalse();
        assertThat(list.matches("  ")).isFalse();
    }
}
