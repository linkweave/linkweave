package org.linkweave.api.bookmark;

import java.time.temporal.ChronoUnit;

import org.linkweave.api.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.json.AutoTagRuleJson;
import org.linkweave.api.bookmark.json.AutoTagRuleListJson;
import org.linkweave.api.bookmark.json.AutoTagRuleOrderJson;
import org.linkweave.api.bookmark.json.AutoTagRuleSaveJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.auth.AuthorizationService;
import io.smallrye.faulttolerance.api.RateLimit;
import org.linkweave.infrastructure.db.RetryOnSqliteBusy;
import org.linkweave.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@RetryOnSqliteBusy
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/auto-tag-rules")
public class AutoTagRuleResource {

    private final AutoTagRuleService autoTagRuleService;
    private final AuthorizationService authorizationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @NonNull
    public AutoTagRuleListJson list(@QueryParam("collectionId") @NotNull @NonNull ID<Collection> collectionId) {
        authorizationService.requireCollectionAccess(collectionId);
        return new AutoTagRuleListJson(
            autoTagRuleService.findByCollection(collectionId).stream()
                .map(AutoTagRuleMapper::toJson)
                .toList()
        );
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @NonNull
    public AutoTagRuleJson create(@NotNull @Valid @NonNull AutoTagRuleSaveJson json) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        AutoTagRule rule = autoTagRuleService.createRule(json);
        return AutoTagRuleMapper.toJson(rule);
    }

    @PUT
    @Path("/{ruleId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @NonNull
    public AutoTagRuleJson update(
        @PathParam("ruleId") @NotNull @NonNull ID<AutoTagRule> ruleId,
        @NotNull @Valid @NonNull AutoTagRuleSaveJson json
    ) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        AutoTagRule rule = autoTagRuleService.getRule(ruleId);
        authorizationService.requireAccessTo(rule);
        AutoTagRule updated = autoTagRuleService.updateRule(rule, json);
        return AutoTagRuleMapper.toJson(updated);
    }

    @DELETE
    @Path("/{ruleId}")
    @Authenticated
    public void delete(@PathParam("ruleId") @NotNull @NonNull ID<AutoTagRule> ruleId) {
        AutoTagRule rule = autoTagRuleService.getRule(ruleId);
        authorizationService.requireAccessTo(rule);
        autoTagRuleService.removeRule(ruleId);
    }

    @PUT
    @Path("/reorder")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    public void reorder(@NotNull @Valid @NonNull AutoTagRuleOrderJson json) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        autoTagRuleService.reorder(json.getCollectionId(), json.getOrderedIds());
    }
}
