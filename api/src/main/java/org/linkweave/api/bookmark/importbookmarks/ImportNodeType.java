package org.linkweave.api.bookmark.importbookmarks;

/**
 * Kind of node in the import manifest tree (UC-096): a {@link #FOLDER} carries
 * children, a {@link #BOOKMARK} carries a URL.
 */
public enum ImportNodeType {
    FOLDER,
    BOOKMARK
}
