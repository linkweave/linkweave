package org.linkweave.api.autotag.llm;

import java.util.List;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.linkweave.api.autotag.LlmDisabledTestProfile;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Feature-flag-off path (FR-096): with {@code linkweave.autotag.llm.enabled=false}
 * the service must short-circuit to an empty result and never touch the model —
 * the system falls back to rule-based suggestions.
 */
@QuarkusTest
@TestProfile(LlmDisabledTestProfile.class)
@TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
class BookmarkAutoTagLlmDisabledITest {

    @Inject
    BookmarkAutoTagLlmService service;

    @Inject
    FixtureService fixtureService;

    private final FakeLlmTaggingClient fake = new FakeLlmTaggingClient();

    @BeforeEach
    void installFake() {
        fake.reset();
        QuarkusMock.installMockForType(fake, LlmTaggingClient.class);
    }

    @Test
    void shouldReturnEmptyAndNotCallModelWhenFeatureDisabled() {
        Collection collection = fixtureService.createTestCollection();
        fixtureService.persistTag(b -> b.withCollection(collection).withName("rust"));
        fake.namesToReturn = List.of("rust");

        List<Tag> result = service.suggestTags(
            collection.getId(), "Async Rust", "https://example.com/rust", null);

        Assertions.assertThat(result).isEmpty();
        Assertions.assertThat(fake.suggestCalled)
            .as("disabled feature must not call the model")
            .isFalse();
    }

    @Test
    void shouldNotWarmUpWhenFeatureDisabled() {
        service.warmUp();

        Assertions.assertThat(fake.warmUpCalled).isFalse();
    }
}
