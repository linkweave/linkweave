package org.chainlink.api.bookmark;

import java.util.List;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepo tagRepo;

    @NonNull
    public Tag createTag(@NonNull String name) {
        var tag = new Tag();
        tag.name = name;
        tagRepo.persist(tag);
        return tag;
    }

    @NonNull
    public Tag getTag(@NonNull ID<Tag> id) {
        return tagRepo.getById(id);
    }

    @NonNull
    public Tag getTagByName(@NonNull String name) {
        return tagRepo.getByName(name);
    }

    public List<Tag> getAllTags() {
        return tagRepo.findAll();
    }

    @NonNull
    public Tag updateTag(@NonNull ID<Tag> id, @NonNull String name) {
        var tag = tagRepo.getById(id);
        tag.name = name;
        tagRepo.persist(tag);
        return tag;
    }

    public void removeTag(@NonNull ID<Tag> id) {
        tagRepo.remove(id);
    }
}
