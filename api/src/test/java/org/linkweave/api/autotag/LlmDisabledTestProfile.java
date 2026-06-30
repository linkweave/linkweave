package org.linkweave.api.autotag;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * Turns the local-LLM auto-tagging feature flag off (FR-096) so the
 * fallback-to-rules path can be tested. The feature is on by default, so only
 * the disabled-path test needs this profile.
 */
public class LlmDisabledTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("linkweave.autotag.llm.enabled", "false");
    }
}
