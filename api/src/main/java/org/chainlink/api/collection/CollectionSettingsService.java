package org.chainlink.api.collection;

import ch.dvbern.dvbstarter.types.id.ID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.shared.user.CurrentUserService;
import org.chainlink.infrastructure.errorhandling.AppFailureException;
import org.chainlink.infrastructure.errorhandling.AppFailureMessage;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Service
@RequiredArgsConstructor
@Slf4j
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
        @NonNull CollectionSettingsJson patchFromClient
    ) {
        var userId = currentUserService.currentUser().getId();
        var access = collectionAccessRepo.findByUserAndCollection(userId, collectionId);
        var current = parse(access.getSettings());
        var updatedCollection = new CollectionSettingsJson(
            patchFromClient.getLayout() != null ? patchFromClient.getLayout() : current.getLayout(),
            patchFromClient.getSortField() != null ? patchFromClient.getSortField() : current.getSortField(),
            patchFromClient.getSortDirection() != null ? patchFromClient.getSortDirection() : current.getSortDirection()
        );
        access.setSettings(serialize(updatedCollection));
        return updatedCollection;
    }

    @Transactional
    public void resetSortPreference(@NonNull ID<Collection> collectionId) {
        var userId = currentUserService.currentUser().getId();
        var collectionAccess = collectionAccessRepo.findByUserAndCollection(userId, collectionId);
        var current = parse(collectionAccess.getSettings());
        var reset = new CollectionSettingsJson(current.getLayout(), null, null);
        collectionAccess.setSettings(serialize(reset));
    }

    @NonNull
    private CollectionSettingsJson parse(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return new CollectionSettingsJson(null, null, null);
        }
        try {
            return objectMapper.readValue(json, CollectionSettingsJson.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Could not parse to CollectionSettingJson, using empty default");
            return new CollectionSettingsJson(null, null, null);
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
