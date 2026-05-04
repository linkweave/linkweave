package org.chainlink.api.bookmark;

import org.chainlink.api.bookmark.json.AutoTagRuleJson;
import org.chainlink.api.bookmark.json.AutoTagRuleSaveJson;
import org.chainlink.infrastructure.json.EntityInfoJson;
import org.chainlink.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
public class AutoTagRuleMapper {
    private AutoTagRuleMapper() {
        /* This utility class should not be instantiated */
    }


    @NonNull
    public static AutoTagRuleJson toJson(@NonNull AutoTagRule rule) {
        return new AutoTagRuleJson(
            rule.getId(),
            EntityInfoJson.fromEntity(rule),
            new AutoTagRuleSaveJson(
                rule.getCollection().getId(),
                rule.getPattern(),
                rule.getTagNames(),
                rule.getDescription(),
                rule.isEnabled()
            ),
            rule.getSortOrder()
        );
    }
}
