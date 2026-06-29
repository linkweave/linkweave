package org.linkweave.api.autotag.llm;

import java.util.List;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.linkweave.api.bookmark.Tag;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.testutil.fixture.FixtureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestSecurity(user = "test@example.com", roles = {"BOOKMARK_READ"})
class BookmarkAutoTagLlmServiceITest {

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
    void shouldReturnExistingTagsAndDropOutOfVocabularyNames() {
        Collection collection = fixtureService.createTestCollection();
        fixtureService.persistTag(b -> b.withCollection(collection).withName("rust"));
        fixtureService.persistTag(b -> b.withCollection(collection).withName("databases"));
        fixtureService.persistTag(b -> b.withCollection(collection).withName("career"));

        // Model returns two valid tags (reversed order) plus a hallucinated one.
        fake.namesToReturn = List.of("databases", "not-a-real-tag", "rust");

        List<Tag> result = service.suggestTags(
            collection.getId(), "Async Rust patterns", "https://example.com/rust", "blog post");

        Assertions.assertThat(result)
            .as("only existing tags, in the model's order, hallucinated name dropped")
            .extracting(Tag::getName)
            .containsExactly("databases", "rust");
        Assertions.assertThat(fake.lastVocabulary)
            .as("the full collection vocabulary is offered to the model")
            .containsExactlyInAnyOrder("rust", "databases", "career");
    }

    @Test
    void shouldReturnEmptyAndNotCallModelWhenCollectionHasNoTags() {
        Collection collection = fixtureService.createTestCollection();
        fake.namesToReturn = List.of("rust");

        List<Tag> result = service.suggestTags(
            collection.getId(), "Title", "https://example.com", null);

        Assertions.assertThat(result).isEmpty();
        Assertions.assertThat(fake.suggestCalled)
            .as("no vocabulary → no model call")
            .isFalse();
    }

    @Test
    void shouldWarmUpModel() {
        service.warmUp();

        Assertions.assertThat(fake.warmUpCalled).isTrue();
    }
}
