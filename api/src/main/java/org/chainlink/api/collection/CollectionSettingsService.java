package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.id.ID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.infrastructure.errorhandling.AppFailureException;
import org.chainlink.infrastructure.errorhandling.AppFailureMessage;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class CollectionSettingsService {

    private final CollectionAccessRepo collectionAccessRepo;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    @NonNull
    public CollectionSettingsJson getSettings(@NonNull ID<Collection> collectionId) {
        var userId = currentUserService.currentUser().getId();
        var access = collectionAccessRepo.findByUserAndCollection(userId, collectionId);
        return parse(access.getSettings());
    }

    @Transactional
    @NonNull
    public CollectionSettingsJson updateSettings(
        @NonNull ID<Collection> collectionId,
        @NonNull CollectionSettingsJson patch
    ) {
        var userId = currentUserService.currentUser().getId();
        var access = collectionAccessRepo.findByUserAndCollection(userId, collectionId);
        var current = parse(access.getSettings());
        var merged = new CollectionSettingsJson(
            patch.getLayout() != null ? patch.getLayout() : current.getLayout()
        );
        access.setSettings(serialize(merged));
        return merged;
    }

    @NonNull
    private CollectionSettingsJson parse(@org.jspecify.annotations.Nullable String json) {
        if (json == null || json.isBlank()) {
            return new CollectionSettingsJson(null);
        }
        try {
            return objectMapper.readValue(json, CollectionSettingsJson.class);
        } catch (JsonProcessingException e) {
            return new CollectionSettingsJson(null);
        }
    }

    @NonNull
    private String serialize(@NonNull CollectionSettingsJson value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AppFailureException(AppFailureMessage.internalError("could not serialize collection settings"));
        }
    }
}
