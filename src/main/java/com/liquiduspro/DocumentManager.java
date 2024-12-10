package com.liquiduspro;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.*;

/**
 * For implement this task focus on clear code, and make this solution as simple readable as possible
 * Don't worry about performance, concurrency, etc
 * You can use in Memory collection for sore data
 * <p>
 * Please, don't change class name, and signature for methods save, search, findById
 * Implementations should be in a single class
 * This class could be auto tested
 */
public class DocumentManager {
    public static final int CACHE_SIZE = 100;   // cache size for documents (can be adjusted as needed)
    private final Map<String, Document> documents; // in-memory storage for documents
    private final Map<String, Document> cache; // LRU cache for documents

    public DocumentManager() {
        documents = new HashMap<>();
        /*
        LRU cache implementation is based on LinkedHashMap (non-synchronized and not thread-safe)
        Allows to preserve order and has time complexity of O(1) for get and remove methods.
        After a consequent put operation, the least recently used entry will be evicted
        */
        cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Document> eldest) {
                return size() > CACHE_SIZE;
            }
        };
    }

    /**
     * Implementation of this method should upsert the document to your storage
     * And generate unique id if it does not exist, don't change [created] field
     *
     * @param document - document content and author data
     * @return saved document
     */
    public Document save(Document document) {
        if (document == null) {
            throw new IllegalArgumentException("Received Document is null");
        }

        if (document.getId() == null) {
            document.setId(UUID.randomUUID().toString());
        }
        // save to cache
        cache.put(document.getId(), document);
        // save to storage
        documents.put(document.getId(), document);
        return document;
    }

    /**
     * Implementation this method should find documents which match with request
     *
     * @param request - search request, each field could be null
     * @return list matched documents
     */
    public List<Document> search(SearchRequest request) {
        // null check for request
        if (request == null) {
            return new ArrayList<>(documents.values());
        }

        List<Document> matchedDocuments = documents.values().stream()
                .filter(document -> request.getTitlePrefixes() == null || request.getTitlePrefixes().stream().anyMatch(document.getTitle()::startsWith))    // filter by title prefixes
                .filter(document -> request.getContainsContents() == null || request.getContainsContents().stream().anyMatch(document.getContent()::contains)) // filter by content contains
                .filter(document -> request.getAuthorIds() == null || request.getAuthorIds().stream().anyMatch(document.getAuthor().getId()::equals)) // filter by author id
                .filter(document -> request.getCreatedFrom() == null || document.getCreated().isAfter(request.getCreatedFrom())) // filter by created from
                .filter(document -> request.getCreatedTo() == null || document.getCreated().isBefore(request.getCreatedTo())) // filter by created to
                .toList();

        // update cache for all the documents
        matchedDocuments.forEach(document -> cache.put(document.getId(), document));
        return matchedDocuments;
    }

    /**
     * Implementation this method should find document by id
     *
     * @param id - document id
     * @return optional document
     */
    public Optional<Document> findById(String id) {
        // check if document is in cache
        Document cachedDocument = cache.get(id);
        if (cachedDocument != null) {
            return Optional.of(cachedDocument);

        }
        // if document is not in cache, then check in storage
        Document storageDocument = documents.get(id);
        if (storageDocument != null) {
            // update cache
            cache.put(id, storageDocument);
            return Optional.of(storageDocument);
        }

        return Optional.empty();
    }

    @Data
    @Builder
    public static class SearchRequest {
        private List<String> titlePrefixes;
        private List<String> containsContents;
        private List<String> authorIds;
        private Instant createdFrom;
        private Instant createdTo;
    }

    @Data
    @Builder
    public static class Document {
        private String id;
        private String title;
        private String content;
        private Author author;
        private Instant created;
    }

    @Data
    @Builder
    public static class Author {
        private String id;
        private String name;
    }
}