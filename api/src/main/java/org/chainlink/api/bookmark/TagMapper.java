package org.chainlink.api.bookmark;

import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.json.TagJson;
import org.chainlink.api.bookmark.json.TagSaveJson;
import org.chainlink.infrastructure.json.EntityInfoJson;
import org.chainlink.infrastructure.stereotypes.JaxMapper;
import org.jspecify.annotations.NonNull;

@JaxMapper
@RequiredArgsConstructor
public class TagMapper {

    @NonNull
    public static TagJson toJson(@NonNull Tag tag) {
        return new TagJson(
            tag.getId(),
            EntityInfoJson.fromEntity(tag),
            new TagSaveJson(
                tag.getCollection().getId(),
                tag.getName(),
                tag.getColor()
            )
        );
    }
}
