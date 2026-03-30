package org.chainlink.api.collection;

import lombok.RequiredArgsConstructor;
import org.chainlink.api.shared.user.User;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class CollectionService {

    private static final String DEFAULT_COLLECTION_NAME = "My Bookmarks";

    private final CollectionRepo collectionRepo;
    private final CollectionAccessRepo collectionAccessRepo;

    public void autoProvisionForUser(@NonNull User user) {
        if (collectionAccessRepo.existsByUser(user.getId())) {
            return;
        }

        Collection collection = new Collection(DEFAULT_COLLECTION_NAME, user);
        collectionRepo.persist(collection);

        CollectionAccess access = new CollectionAccess(
            collection,
            user,
            CollectionRole.OWNER,
            true
        );
        collectionAccessRepo.persist(access);
    }
}
