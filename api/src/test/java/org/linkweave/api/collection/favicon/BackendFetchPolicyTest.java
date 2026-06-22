package org.linkweave.api.collection.favicon;

import static org.assertj.core.api.Assertions.assertThat;

import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.api.shared.net.HostPatternSet;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class BackendFetchPolicyTest {

    private static BackendFetchPolicy policyWithDenylist(String denylist) {
        ConfigService config = new ConfigService() {
            @Override
            @NonNull
            public HostPatternSet getBackendFetchDenylist() {
                return HostPatternSet.parse(denylist);
            }
        };
        return new BackendFetchPolicy(config);
    }

    @Test
    void shouldBlockHostOnOperatorDenylist() {
        BackendFetchPolicy policy = policyWithDenylist("*.blocked.example");

        assertThat(policy.blocks("api.blocked.example", null)).isTrue();
        assertThat(policy.blocks("other.example", null)).isFalse();
    }

    @Test
    void shouldBlockHostOnCollectionBrowserAllowlist() {
        BackendFetchPolicy policy = policyWithDenylist(null);

        assertThat(policy.blocks("intranet.local", "intranet.local")).isTrue();
        assertThat(policy.blocks("wiki.corp.internal", "*.corp.internal")).isTrue();
        assertThat(policy.blocks("intranet.local", "other.local")).isFalse();
    }

    @Test
    void shouldNotBlockWhenNeitherListMatches() {
        BackendFetchPolicy policy = policyWithDenylist("denied.example");

        assertThat(policy.blocks("example.com", "*.corp.internal")).isFalse();
        assertThat(policy.blocks("example.com", null)).isFalse();
    }
}
