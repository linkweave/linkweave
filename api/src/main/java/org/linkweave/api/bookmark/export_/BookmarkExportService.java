package org.linkweave.api.bookmark.export_;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.bookmark.folder.Folder;
import org.linkweave.api.bookmark.folder.FolderRepo;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Service
@RequiredArgsConstructor
public class BookmarkExportService {

    private static final String HEADER = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <!--This is an automatically generated file.
             It will be read and overwritten.
             Do Not Edit! -->
        <META HTTP-equiv="Content-Type" content="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE>
        <H1>Bookmarks</H1>
        """;

    private final BookmarkRepo bookmarkRepo;
    private final CollectionRepo collectionRepo;
    private final FolderRepo folderRepo;

    @NonNull
    public String exportBookmarks(@NonNull ID<Collection> collectionId) {
        collectionRepo.referenceById(collectionId);

        List<Folder> allFolders = folderRepo.findByCollection(collectionId);
        List<Bookmark> allBookmarks = bookmarkRepo.findByCollection(collectionId);

        Map<Folder, List<Folder>> childrenByParent = buildFolderTree(allFolders);
        Map<Folder, List<Bookmark>> bookmarksByFolder = allBookmarks.stream()
            .filter(b -> b.getFolder() != null)
            .collect(Collectors.groupingBy(Bookmark::getFolder, LinkedHashMap::new, Collectors.toList()));

        List<Folder> rootFolders = allFolders.stream()
            .filter(f -> f.getParent() == null)
            .sorted(Comparator.comparing(Folder::getName))
            .toList();
        List<Bookmark> rootBookmarks = allBookmarks.stream()
            .filter(b -> b.getFolder() == null)
            .sorted(Comparator.comparing(Bookmark::getTitle))
            .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(HEADER);
        sb.append("<DL><p>\n");

        for (Bookmark bookmark : rootBookmarks) {
            appendBookmark(sb, bookmark);
        }

        for (Folder folder : rootFolders) {
            appendFolder(sb, folder, childrenByParent, bookmarksByFolder);
        }

        sb.append("</DL><p>\n");

        return sb.toString();
    }

    private Map<Folder, List<Folder>> buildFolderTree(@NonNull List<Folder> allFolders) {
        Map<Folder, List<Folder>> childrenByParent = new HashMap<>();
        for (Folder folder : allFolders) {
            Folder parent = folder.getParent();
            if (parent != null) {
                childrenByParent.computeIfAbsent(parent, k -> new java.util.ArrayList<>()).add(folder);
            }
        }
        return childrenByParent;
    }

    private void appendFolder(
        @NonNull StringBuilder sb,
        @NonNull Folder folder,
        @NonNull Map<Folder, List<Folder>> childrenByParent,
        @NonNull Map<Folder, List<Bookmark>> bookmarksByFolder
    ) {
        long addDate = toEpochSeconds(folder.getTimestampErstellt());
        sb.append("    <DT><H3 ADD_DATE=\"").append(addDate).append("\">");
        sb.append(escapeHtml(folder.getName())).append("</H3>\n");
        sb.append("    <DL><p>\n");

        List<Bookmark> folderBookmarks = bookmarksByFolder.getOrDefault(folder, List.of()).stream()
            .sorted(Comparator.comparing(Bookmark::getTitle))
            .toList();
        for (Bookmark bookmark : folderBookmarks) {
            sb.append("        ");
            appendBookmark(sb, bookmark);
        }

        List<Folder> childFolders = childrenByParent.getOrDefault(folder, List.of()).stream()
            .sorted(Comparator.comparing(Folder::getName))
            .toList();
        for (Folder child : childFolders) {
            sb.append("        ");
            appendFolder(sb, child, childrenByParent, bookmarksByFolder);
        }

        sb.append("    </DL><p>\n");
    }

    private void appendBookmark(@NonNull StringBuilder sb, @NonNull Bookmark bookmark) {
        long addDate = toEpochSeconds(bookmark.getTimestampErstellt());
        sb.append("<DT><A HREF=\"").append(escapeHtml(bookmark.getUrl().toString())).append("\"");
        sb.append(" ADD_DATE=\"").append(addDate).append("\"");
        sb.append(">").append(escapeHtml(bookmark.getTitle())).append("</A>\n");
        if (StringUtils.isNotBlank(bookmark.getDescription())) {
            sb.append("<DD>").append(escapeHtml(bookmark.getDescription())).append("\n");
        }
    }
    private long toEpochSeconds(@NonNull OffsetDateTime offsetDateTime) {
        return offsetDateTime.toInstant().getEpochSecond();
    }

    @NonNull
    private String escapeHtml(@Nullable String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
