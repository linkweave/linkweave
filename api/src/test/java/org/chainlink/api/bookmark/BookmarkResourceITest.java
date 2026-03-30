package org.chainlink.api.bookmark;

import ch.dvbern.dvbstarter.types.emailaddress.EmailAddress;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.chainlink.api.benutzer.UserRepo;
import org.chainlink.api.bookmark.folder.Folder;
import org.chainlink.api.bookmark.folder.FolderRepo;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.collection.CollectionAccess;
import org.chainlink.api.collection.CollectionAccessRepo;
import org.chainlink.api.collection.CollectionRepo;
import org.chainlink.api.collection.CollectionRole;
import org.chainlink.api.shared.user.User;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

@QuarkusTest
class BookmarkResourceITest {

    @Inject
    CollectionRepo collectionRepo;

    @Inject
    CollectionAccessRepo collectionAccessRepo;

    @Inject
    UserRepo userRepo;

    @Inject
    FolderRepo folderRepo;

    @Inject
    TagRepo tagRepo;

    private Collection createTestCollection() {
        User user = userRepo.findByEmail(EmailAddress.fromString("test@example.com")).orElseThrow();
        Collection collection = new Collection();
        collection.name = "Test Collection";
        collection.owner = user;
        collectionRepo.persist(collection);

        CollectionAccess access = new CollectionAccess();
        access.collection = collection;
        access.user = user;
        access.role = CollectionRole.OWNER;
        access.isDefault = true;
        collectionAccessRepo.persist(access);

        return collection;
    }

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        String body = """
            {"collectionId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","title":"Test","url":"https://example.com"}
            """;
        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateBookmarkWithoutFolder_whenFolderIdIsNull() {
        Collection collection = createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","title":"Unfiled Bookmark","url":"https://example.org","folderId":null}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            .then()
            .statusCode(200)
            .body("data.folderId", nullValue())
            .body("data.title", equalTo("Unfiled Bookmark"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateBookmarkWithFolder_whenFolderIdProvided() {
        Collection collection = createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Folder folder = new Folder();
        folder.collection = collection;
        folder.name = "Test Folder";
        folderRepo.persist(folder);
        String folderId = folder.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","folderId":"%s","title":"Filed Bookmark","url":"https://example.org"}
            """.formatted(collectionId, folderId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            .then()
            .statusCode(200)
            .body("data.folderId", notNullValue())
            .body("data.title", equalTo("Filed Bookmark"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateBookmarkWithTags_whenTagIdsProvided() {
        Collection collection = createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        Tag tag = new Tag();
        tag.collection = collection;
        tag.name = "Test Tag";
        tag.color = "#FF0000";
        tagRepo.persist(tag);
        String tagId = tag.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","title":"Tagged Bookmark","url":"https://example.org","tagIds":["%s"]}
            """.formatted(collectionId, tagId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            .then()
            .statusCode(200)
            .body("data.tagIds", hasSize(1))
            .body("data.title", equalTo("Tagged Bookmark"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldCreateBookmarkWithDescription_whenDescriptionProvided() {
        Collection collection = createTestCollection();
        String collectionId = collection.getId().getUUID().toString();

        String body = """
            {"collectionId":"%s","title":"Bookmark with Description","url":"https://example.org","description":"This is my description"}
            """.formatted(collectionId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            .then()
            .statusCode(200)
            .body("data.description", equalTo("This is my description"));
    }

    @Test
    @TestSecurity(
        user = "test@example.com",
        roles = {"BOOKMARK_WRITE"}
    )
    void shouldReturn403_whenUserHasNoCollectionAccess() {
        String nonExistentId = java.util.UUID.randomUUID().toString();
        String body = """
            {"collectionId":"%s","title":"My Bookmark","url":"https://example.com"}
            """.formatted(nonExistentId);

        RestAssured.given()
            .contentType(ContentType.JSON)
            .body(body)
            .post("/bookmarks")
            .then()
            .statusCode(403);
    }
}
