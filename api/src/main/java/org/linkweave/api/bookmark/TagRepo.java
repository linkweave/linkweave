package org.linkweave.api.bookmark;

import java.util.List;
import java.util.Optional;

import ch.dvbern.dvbstarter.types.id.ID;
import org.linkweave.api.collection.Collection;
import org.linkweave.infrastructure.db.BaseRepo;
import org.linkweave.infrastructure.errorhandling.AppFailureException;
import org.linkweave.infrastructure.errorhandling.AppFailureMessage;
import org.linkweave.infrastructure.stereotypes.Repository;
import org.jspecify.annotations.NonNull;

@Repository
public class TagRepo extends BaseRepo<Tag> {

    public List<Tag> findAll() {
        return db.findAll(QTag.tag);
    }

    public List<Tag> findByCollection(ID<Collection> collectionId) {
        return db.selectFrom(QTag.tag)
            .where(QTag.tag.collection.id.eq(collectionId.getUUID()))
            .fetch();
    }

    public void deleteByCollection(@NonNull ID<Collection> collectionId) {
        var tags = db.selectFrom(QTag.tag)
            .where(QTag.tag.collection.id.eq(collectionId.getUUID()))
            .fetch();
        for (var tag : tags) {
            remove(tag.getId());
        }
    }

    public Optional<Tag> findByName(String name) {
        return db.selectFrom(QTag.tag)
            .where(QTag.tag.name.eq(name))
            .fetchOne();
    }

    public void flush() {
        db.flush();
    }

    public Tag getByName(String name) {
        return findByName(name).orElseThrow(() ->
            new AppFailureException(AppFailureMessage.entityNotFoundMsg(
                Tag.class, name)));
    }
}
