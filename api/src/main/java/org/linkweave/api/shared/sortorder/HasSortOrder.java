package org.linkweave.api.shared.sortorder;

/**
 * Contract for entities carrying a manual sort order (folders — UC-102,
 * bookmarks — UC-103). Satisfied by the entities' Lombok-generated accessors.
 */
public interface HasSortOrder {

    long getSortOrder();

    void setSortOrder(long sortOrder);
}
