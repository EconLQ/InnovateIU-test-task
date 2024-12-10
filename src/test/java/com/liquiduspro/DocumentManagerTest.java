package com.liquiduspro;

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

public class DocumentManagerTest {
    private static final String AUTHOR_ID = UUID.randomUUID().toString();
    private static final String AUTHOR_NAME = "John Doe";
    private final DocumentManager documentManager;

    public DocumentManagerTest() {
        documentManager = new DocumentManager();
    }

    @Test
    public void save_WithNullDocument_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> documentManager.save(null));
    }

    @Test
    public void save_WithNewDocument_GeneratesId() {
        DocumentManager.Document document = createDocument(null, "Test Title", "Test Content");
        DocumentManager.Document saved = documentManager.save(document);

        assertNotNull(saved.getId());
    }

    @Test
    public void save_WithExistingDocument_UpdatesDocument() {
        // First save
        DocumentManager.Document document = createDocument(null, "Original Title", "Original Content");
        DocumentManager.Document saved = documentManager.save(document);
        String originalId = saved.getId();

        // Update and save again
        saved.setTitle("Updated Title");
        DocumentManager.Document updated = documentManager.save(saved);

        assertEquals(originalId, updated.getId());
        assertEquals("Updated Title", updated.getTitle());
    }

    @Test
    public void findById_WithExistingDocument_ReturnsDocument() {
        DocumentManager.Document document = createDocument(null, "Test Title", "Test Content");
        DocumentManager.Document saved = documentManager.save(document);

        Optional<DocumentManager.Document> found = documentManager.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(saved.getTitle(), found.get().getTitle());
    }

    @Test
    public void findById_WithNonExistingDocument_ReturnsEmpty() {
        Optional<DocumentManager.Document> found = documentManager.findById("non-existing-id");
        assertTrue(found.isEmpty());
    }

    @Test
    public void search_WithNullRequest_ReturnsAllDocuments() {
        DocumentManager.Document doc1 = createDocument(null, "Title 1", "Content 1");
        DocumentManager.Document doc2 = createDocument(null, "Title 2", "Content 2");
        documentManager.save(doc1);
        documentManager.save(doc2);

        List<DocumentManager.Document> results = documentManager.search(null);
        assertEquals(2, results.size());
    }

    @Test
    public void search_WithTitlePrefix_ReturnsMatchingDocuments() {
        DocumentManager.Document doc1 = createDocument(null, "ABC Title", "Content 1");
        DocumentManager.Document doc2 = createDocument(null, "XYZ Title", "Content 2");
        documentManager.save(doc1);
        documentManager.save(doc2);

        DocumentManager.SearchRequest request = DocumentManager.SearchRequest.builder()
                .titlePrefixes(List.of("ABC"))
                .build();

        List<DocumentManager.Document> results = documentManager.search(request);
        assertEquals(1, results.size());
        assertEquals("ABC Title", results.get(0).getTitle());
    }

    @Test
    public void search_WithContent_ReturnsMatchingDocuments() {
        DocumentManager.Document doc1 = createDocument(null, "Title 1", "ABC Content");
        DocumentManager.Document doc2 = createDocument(null, "Title 2", "XYZ Content");
        documentManager.save(doc1);
        documentManager.save(doc2);

        DocumentManager.SearchRequest request = DocumentManager.SearchRequest.builder()
                .containsContents(List.of("ABC"))
                .build();

        List<DocumentManager.Document> results = documentManager.search(request);
        assertEquals(1, results.size());
        assertEquals("ABC Content", results.get(0).getContent());
    }

    @Test
    public void search_WithAuthorId_ReturnsMatchingDocuments() {
        DocumentManager.Document doc1 = createDocument(null, "Title 1", "Content 1");
        DocumentManager.Document doc2 = createDocument(null, "Title 2", "Content 2");
        doc2.getAuthor().setId("different-author");

        documentManager.save(doc1);
        documentManager.save(doc2);

        DocumentManager.SearchRequest request = DocumentManager.SearchRequest.builder()
                .authorIds(Collections.singletonList(AUTHOR_ID))
                .build();

        List<DocumentManager.Document> results = documentManager.search(request);
        assertEquals(1, results.size());
        assertEquals(AUTHOR_ID, results.get(0).getAuthor().getId());
    }

    @Test
    public void search_WithTimeRange_ReturnsMatchingDocuments() {
        Instant now = Instant.now();
        Instant hourAgo = now.minusSeconds(3600);
        Instant twoHoursAgo = now.minusSeconds(7200);

        DocumentManager.Document doc1 = createDocument(null, "Title 1", "Content 1");
        doc1.setCreated(hourAgo);
        DocumentManager.Document doc2 = createDocument(null, "Title 2", "Content 2");
        doc2.setCreated(twoHoursAgo);

        documentManager.save(doc1);
        documentManager.save(doc2);

        DocumentManager.SearchRequest request = DocumentManager.SearchRequest.builder()
                .createdFrom(hourAgo.minusSeconds(60))
                .createdTo(now)
                .build();

        List<DocumentManager.Document> results = documentManager.search(request);
        assertEquals(1, results.size());
        assertEquals("Title 1", results.get(0).getTitle());
    }

    @Test
    public void search_WithMultipleCriteria_ReturnsMatchingDocuments() {
        DocumentManager.Document doc1 = createDocument(null, "ABC Title", "ABC Content");
        DocumentManager.Document doc2 = createDocument(null, "ABC Title", "XYZ Content");
        DocumentManager.Document doc3 = createDocument(null, "XYZ Title", "ABC Content");

        documentManager.save(doc1);
        documentManager.save(doc2);
        documentManager.save(doc3);

        DocumentManager.SearchRequest request = DocumentManager.SearchRequest.builder()
                .titlePrefixes(List.of("ABC"))
                .containsContents(List.of("ABC"))
                .authorIds(Collections.singletonList(AUTHOR_ID))
                .build();

        List<DocumentManager.Document> results = documentManager.search(request);
        assertEquals(1, results.size());
        assertEquals("ABC Title", results.get(0).getTitle());
        assertEquals("ABC Content", results.get(0).getContent());
    }

    @Test
    public void verifyLRUCache_WithMoreThanCacheSize() {
        // Create and save more documents than cache size
        for (int i = 0; i < DocumentManager.CACHE_SIZE + 10; i++) {
            DocumentManager.Document doc = createDocument(null, "Title " + i, "Content " + i);
            documentManager.save(doc);
        }

        // Access first document to put it in cache
        String firstDocId = documentManager.search(null).get(0).getId();
        documentManager.findById(firstDocId);

        // Access it again to verify it's still in cache (should be fast)
        long startTime = System.nanoTime();
        documentManager.findById(firstDocId);
        long endTime = System.nanoTime();

        // This access should be very fast since it's in cache
        assertTrue((endTime - startTime) < 1000000); // less than 1ms
    }

    private DocumentManager.Document createDocument(String id, String title, String content) {
        return DocumentManager.Document.builder()
                .id(id)
                .title(title)
                .content(content)
                .author(createAuthor())
                .created(Instant.now())
                .build();
    }

    private DocumentManager.Author createAuthor() {
        return DocumentManager.Author.builder()
                .id(AUTHOR_ID)
                .name(AUTHOR_NAME)
                .build();
    }
}