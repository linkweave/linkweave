package org.linkweave.api.bookmark;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.linkweave.api.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.json.AutoTagRuleSaveJson;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.api.shared.abstractentity.AbstractEntity;
import org.linkweave.infrastructure.errorhandling.AppValidationException;
import org.linkweave.infrastructure.errorhandling.AppValidationMessage;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class AutoTagRuleService {

    private static final int MAX_TAG_NAMES = 8;
    private static final int SORT_ORDER_STEP = 10;

    private final AutoTagRuleRepo autoTagRuleRepo;
    private final CollectionRepo collectionRepo;

    @NonNull
    public List<AutoTagRule> findByCollection(@NonNull ID<Collection> collectionId) {
        return autoTagRuleRepo.findByCollectionOrderedBySortOrder(collectionId);
    }

    @NonNull
    public AutoTagRule getRule(@NonNull ID<AutoTagRule> id) {
        return autoTagRuleRepo.getById(id);
    }

    @NonNull
    public AutoTagRule createRule(@NonNull AutoTagRuleSaveJson json) {
        validatePattern(json.getPattern());
        String normalizedName = normalizeTagName(json.getTagNames());

        int nextSortOrder = autoTagRuleRepo.findMaxSortOrder(json.getCollectionId()) + SORT_ORDER_STEP;

        AutoTagRule rule = new AutoTagRule(
            collectionRepo.referenceById(json.getCollectionId()),
            json.getPattern(),
            normalizedName,
            json.getDescription(),
            json.isEnabled(),
            nextSortOrder
        );
        autoTagRuleRepo.persistAndFlush(rule);
        return rule;
    }

    @NonNull
    public AutoTagRule updateRule(@NonNull AutoTagRule rule, @NonNull AutoTagRuleSaveJson json) {
        validatePattern(json.getPattern());
        rule.setPattern(json.getPattern());
        rule.setTagNames(normalizeTagName(json.getTagNames()));
        rule.setDescription(json.getDescription());
        rule.setEnabled(json.isEnabled());
        autoTagRuleRepo.persistAndFlush(rule);
        return rule;
    }

    public void removeRule(@NonNull ID<AutoTagRule> id) {
        autoTagRuleRepo.remove(id);
    }

    public void reorder(@NonNull ID<Collection> collectionId, @NonNull List<ID<AutoTagRule>> orderedIds) {
        List<AutoTagRule> rules = autoTagRuleRepo.findByCollectionOrderedBySortOrder(collectionId);
        var byId = rules.stream().collect(Collectors.toMap(AbstractEntity::getId, r -> r));
        int order = SORT_ORDER_STEP;
        for (ID<AutoTagRule> id : orderedIds) {
            AutoTagRule r = byId.get(id);
            if (r != null) {
                r.setSortOrder(order);
                order += SORT_ORDER_STEP;
            }
        }
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        autoTagRuleRepo.deleteByCollection(collectionId);
    }

    private static void validatePattern(@NonNull String pattern) {
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            throw new AppValidationException(
                AppValidationMessage.genericMessage("AppValidation.autoTagRule.pattern.invalid"),
                e);
        }
    }

    @NonNull
    private static String normalizeTagName(@NonNull String raw) {
        Set<String> names = Arrays.stream(raw.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (names.isEmpty()) {
            throw new AppValidationException(
                AppValidationMessage.genericMessage("AppValidation.autoTagRule.tagNames.required"));
        }
        if (names.size() > MAX_TAG_NAMES) {
            throw new AppValidationException(
                AppValidationMessage.genericMessage("AppValidation.autoTagRule.tagNames.tooMany"));
        }
        return String.join(",", names);
    }
}
