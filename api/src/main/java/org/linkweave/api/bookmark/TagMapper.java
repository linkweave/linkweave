package org.linkweave.api.bookmark;

import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.json.TagJson;
import org.linkweave.api.bookmark.json.TagSaveJson;
import org.linkweave.infrastructure.json.EntityInfoJson;
import org.linkweave.infrastructure.stereotypes.JaxMapper;
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
