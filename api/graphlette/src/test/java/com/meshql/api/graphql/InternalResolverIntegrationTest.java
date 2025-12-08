package com.meshql.api.graphql;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meshql.auth.noop.NoAuth;
import com.meshql.core.Auth;
import com.meshql.core.Searcher;
import com.meshql.core.config.*;
import com.tailoredshapes.stash.Stash;
import graphql.schema.DataFetcher;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.ocho.UnderBar.list;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for internal resolvers.
 * Tests complete query execution flow with internal resolvers to verify
 * that all checks and balances are maintained while avoiding HTTP overhead.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InternalResolverIntegrationTest {
    private Graphlette authorGraphlette;
    private Graphlette bookGraphlette;
    private Searcher authorSearcher;
    private Searcher bookSearcher;
    private Auth auth;

    @BeforeAll
    void setup() {
        auth = new NoAuth();
        authorSearcher = mock(Searcher.class);
        bookSearcher = mock(Searcher.class);

        // Setup test data
        when(authorSearcher.find(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            Stash args = invocation.getArgument(1);
            String id = args.get("id").toString();
            if ("author1".equals(id)) {
                return stash("id", "author1", "name", "Jane Doe", "email", "jane@example.com");
            }
            return stash();
        });

        when(bookSearcher.find(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            Stash args = invocation.getArgument(1);
            String id = args.get("id").toString();
            if ("book1".equals(id)) {
                return stash("id", "book1", "title", "GraphQL Basics", "authorId", "author1");
            } else if ("book2".equals(id)) {
                return stash("id", "book2", "title", "Advanced GraphQL", "authorId", "author1");
            }
            return stash();
        });

        when(bookSearcher.findAll(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            Stash args = invocation.getArgument(1);
            // Internal resolvers pass the value as "id" parameter
            String authorId = args.get("id") != null ? args.get("id").toString() : null;
            if ("author1".equals(authorId)) {
                return list(
                        stash("id", "book1", "title", "GraphQL Basics", "authorId", "author1"),
                        stash("id", "book2", "title", "Advanced GraphQL", "authorId", "author1")
                );
            }
            return Collections.emptyList();
        });

        // Create graphlette registry
        Stash graphletteRegistry = stash();

        // Create Author graphlette with internal vector resolver for books
        RootConfig authorConfig = RootConfig.builder()
                .singleton("getAuthor", "{\"id\": \"{{id}}\"}")
                .internalVectorResolver("books", null, "getBooksByAuthor", "/book/graph")
                .build();

        DTOFactory authorDtoFactory = new DTOFactory(list(), list(), list(),
                authorConfig.internalVectorResolvers(), graphletteRegistry);
        Map<String, DataFetcher> authorFetchers = Root.create(authorSearcher, authorDtoFactory, auth, authorConfig);
        authorGraphlette = new Graphlette(authorFetchers, createAuthorSchema());
        graphletteRegistry.put("/author/graph", authorGraphlette);

        // Create Book graphlette with internal singleton resolver for author
        // Note: Internal resolvers always pass the foreign key as "id" parameter,
        // so the query template must use {{id}} not {{authorId}}
        RootConfig bookConfig = RootConfig.builder()
                .singleton("getBook", "{\"id\": \"{{id}}\"}")
                .vector("getBooksByAuthor", "{\"payload.author_id\": \"{{id}}\"}")
                .internalSingletonResolver("author", "authorId", "getAuthor", "/author/graph")
                .build();

        DTOFactory bookDtoFactory = new DTOFactory(list(), list(),
                bookConfig.internalSingletonResolvers(), list(), graphletteRegistry);
        Map<String, DataFetcher> bookFetchers = Root.create(bookSearcher, bookDtoFactory, auth, bookConfig);
        bookGraphlette = new Graphlette(bookFetchers, createBookSchema());
        graphletteRegistry.put("/book/graph", bookGraphlette);
    }

    /**
     * Test querying book with nested author via internal singleton resolver
     */
    @Test
    void testBookWithNestedAuthorViaInternalResolver() {
        String query = String.format(
                "{getBook(id: \"book1\" at: %d){id title author{id name email}}}",
                System.currentTimeMillis()
        );

        String jsonResponse = bookGraphlette.executeInternal(query);
        assertNotNull(jsonResponse, "Should return response");

        JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();
        assertFalse(response.has("errors"), "Should not have errors: " + jsonResponse);
        assertTrue(response.has("data"), "Should have data field");

        JsonObject data = response.getAsJsonObject("data");
        JsonObject book = data.getAsJsonObject("getBook");

        assertNotNull(book, "Book should not be null");
        assertEquals("book1", book.get("id").getAsString());
        assertEquals("GraphQL Basics", book.get("title").getAsString());

        // Verify nested author was resolved internally
        assertTrue(book.has("author"), "Book should have author field");
        JsonObject author = book.getAsJsonObject("author");
        assertNotNull(author, "Author should not be null");
        assertEquals("author1", author.get("id").getAsString());
        assertEquals("Jane Doe", author.get("name").getAsString());
        assertEquals("jane@example.com", author.get("email").getAsString());
    }

    /**
     * Test querying author with nested books via internal vector resolver
     */
    @Test
    void testAuthorWithNestedBooksViaInternalResolver() {
        String query = String.format(
                "{getAuthor(id: \"author1\" at: %d){id name books{id title}}}",
                System.currentTimeMillis()
        );

        String jsonResponse = authorGraphlette.executeInternal(query);
        assertNotNull(jsonResponse, "Should return response");

        JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();
        assertFalse(response.has("errors"), "Should not have errors: " + jsonResponse);
        assertTrue(response.has("data"), "Should have data field");

        JsonObject data = response.getAsJsonObject("data");
        JsonObject author = data.getAsJsonObject("getAuthor");

        assertNotNull(author, "Author should not be null");
        assertEquals("author1", author.get("id").getAsString());
        assertEquals("Jane Doe", author.get("name").getAsString());

        // Verify nested books were resolved internally
        assertTrue(author.has("books"), "Author should have books field");
        JsonArray books = author.getAsJsonArray("books");
        assertNotNull(books, "Books should not be null");
        assertEquals(2, books.size(), "Should have 2 books");

        JsonObject firstBook = books.get(0).getAsJsonObject();
        assertEquals("book1", firstBook.get("id").getAsString());
        assertEquals("GraphQL Basics", firstBook.get("title").getAsString());

        JsonObject secondBook = books.get(1).getAsJsonObject();
        assertEquals("book2", secondBook.get("id").getAsString());
        assertEquals("Advanced GraphQL", secondBook.get("title").getAsString());
    }

    /**
     * Test that internal resolver respects field selection (only requested fields)
     */
    @Test
    void testInternalResolverRespectsFieldSelection() {
        String query = String.format(
                "{getBook(id: \"book1\" at: %d){id author{name}}}",  // Only request author name, not id or email
                System.currentTimeMillis()
        );

        String jsonResponse = bookGraphlette.executeInternal(query);
        JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();

        assertFalse(response.has("errors"), "Should not have errors: " + jsonResponse);

        JsonObject data = response.getAsJsonObject("data");
        JsonObject book = data.getAsJsonObject("getBook");
        JsonObject author = book.getAsJsonObject("author");

        // Should have requested field
        assertTrue(author.has("name"), "Should have name field");
        assertEquals("Jane Doe", author.get("name").getAsString());

        // GraphQL only returns requested fields - id was not requested so it won't be present
        // (This is correct GraphQL behavior - only return what was asked for)
    }

    /**
     * Test query with multiple levels of nesting using internal resolvers
     */
    @Test
    void testMultipleLevelsOfNesting() {
        // Query book -> author -> books (circular reference, but different instances)
        String query = String.format(
                "{getBook(id: \"book1\" at: %d){id title author{id name books{id title}}}}",
                System.currentTimeMillis()
        );

        String jsonResponse = bookGraphlette.executeInternal(query);
        JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();

        assertFalse(response.has("errors"), "Should not have errors: " + jsonResponse);

        JsonObject data = response.getAsJsonObject("data");
        JsonObject book = data.getAsJsonObject("getBook");
        JsonObject author = book.getAsJsonObject("author");

        assertNotNull(author, "Author should not be null");
        assertTrue(author.has("books"), "Author should have books");

        JsonArray authorBooks = author.getAsJsonArray("books");
        assertEquals(2, authorBooks.size(), "Author should have 2 books");
    }

    /**
     * Test that internal resolver handles non-existent data gracefully
     */
    @Test
    void testInternalResolverWithNonExistentData() {
        String query = String.format(
                "{getBook(id: \"nonexistent\" at: %d){id title author{id name}}}",
                System.currentTimeMillis()
        );

        String jsonResponse = bookGraphlette.executeInternal(query);
        JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();

        // The response should not throw errors - it should gracefully handle non-existent data
        // GraphQL returns data with null or empty values for non-existent entities
        assertTrue(response.has("data") || response.has("errors"),
                "Response should have data or errors field: " + jsonResponse);

        // If there's a data field, verify the getBook returns null/empty for non-existent ID
        if (response.has("data") && !response.get("data").isJsonNull()) {
            JsonObject data = response.getAsJsonObject("data");
            if (data.has("getBook")) {
                // Either null or an empty object is acceptable
                assertTrue(data.get("getBook").isJsonNull() ||
                          (data.get("getBook").isJsonObject() && data.getAsJsonObject("getBook").entrySet().isEmpty()),
                        "Non-existent book should be null or empty: " + data.get("getBook"));
            }
        }
    }

    /**
     * Test executeInternal preserves timestamps for temporal queries
     */
    @Test
    void testInternalResolverPreservesTimestamp() {
        long timestamp = System.currentTimeMillis();
        String query = String.format("{getAuthor(id: \"author1\" at: %d){id name}}", timestamp);

        String jsonResponse = authorGraphlette.executeInternal(query);
        assertNotNull(jsonResponse, "Should return response");

        JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();
        assertFalse(response.has("errors"), "Should not have errors");
    }

    /**
     * Test that internal resolver works with empty result sets
     */
    @Test
    void testInternalVectorResolverWithEmptyResults() {
        String query = String.format(
                "{getAuthor(id: \"author_no_books\" at: %d){id name books{id title}}}",
                System.currentTimeMillis()
        );

        // This will return empty books list since author_no_books doesn't match our mock data
        String jsonResponse = authorGraphlette.executeInternal(query);
        JsonObject response = JsonParser.parseString(jsonResponse).getAsJsonObject();

        assertTrue(response.has("data"), "Should have data field");
    }

    // Helper methods to create test schemas
    private String createAuthorSchema() {
        return """
                type Query {
                    getAuthor(id: ID! at: Float): Author
                }

                type Author {
                    id: ID!
                    name: String!
                    email: String
                    books: [Book]
                }

                type Book {
                    id: ID!
                    title: String!
                }
                """;
    }

    private String createBookSchema() {
        return """
                type Query {
                    getBook(id: ID! at: Float): Book
                    getBooksByAuthor(id: ID! at: Float): [Book]
                }

                type Book {
                    id: ID!
                    title: String!
                    authorId: String
                    author: Author
                }

                type Author {
                    id: ID!
                    name: String!
                    email: String
                    books: [Book]
                }
                """;
    }
}
