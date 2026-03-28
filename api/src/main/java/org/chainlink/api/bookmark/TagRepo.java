package org.chainlink.api.bookmark;

import java.util.List;
import java.util.Optional;

import org.chainlink.infrastructure.db.BaseRepo;
import org.chainlink.infrastructure.errorhandling.AppFailureException;
import org.chainlink.infrastructure.errorhandling.AppFailureMessage;
import org.chainlink.infrastructure.stereotypes.Repository;

@Repository
public class TagRepo extends BaseRepo<Tag> {

    public List<Tag> findAll() {
        return db.findAll(QTag.tag);
    }

    public Optional<Tag> findByName(String name) {
        return db.selectFrom(QTag.tag)
            .where(QTag.tag.name.eq(name))
            .fetchOne();
    }

    public Tag getByName(String name) {
        return findByName(name).orElseThrow(() ->
            new AppFailureException(AppFailureMessage.entityNotFoundMsg(
                Tag.class, name)));
    }
}
