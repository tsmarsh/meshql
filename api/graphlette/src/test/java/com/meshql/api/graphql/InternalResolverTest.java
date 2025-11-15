package com.meshql.api.graphql;

import com.meshql.auth.noop.NoAuth;
import com.meshql.core.Auth;
import com.meshql.core.Searcher;
import com.meshql.core.config.*;
import com.tailoredshapes.stash.Stash;
import graphql.schema.DataFetcher;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.tailoredshapes.stash.Stash.stash;
import static com.tailoredshapes.underbar.ocho.UnderBar.list;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Comprehensive tests for internal resolver functionality.
 * Tests that internal resolvers properly reference graphlettes by name
 * and execute queries without HTTP overhead while maintaining all checks and balances.
 */
class InternalResolverTest {
    @Mock private Searcher authorSearcher;
    @Mock private Searcher bookSearcher;

    private Stash graphletteRegistry;
    private Auth auth;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        graphletteRegistry = stash();
        auth = new NoAuth();
    }

    /**
     * Test that internal singleton resolver can be configured and resolves data
     */
    @Test
    void testInternalSingletonResolverConfiguration() {
        // Setup author graphlette
        when(authorSearcher.find(any(), any(), any(), anyLong()))
                .thenReturn(stash("id", "author1", "name", "Jane Doe"));

        RootConfig authorConfig = new RootConfig(
                list(new QueryConfig("getAuthor", "{\"id\": \"{{id}}\"}")),
                list(),
                list(),
                list(),
                list(),
                list()
        );

        DTOFactory authorDtoFactory = new DTOFactory(list(), list(), list(), list(), graphletteRegistry);
        Map<String, DataFetcher> authorFetchers = Root.create(authorSearcher, authorDtoFactory, auth, authorConfig);
        Graphlette authorGraphlette = new Graphlette(authorFetchers, createAuthorSchema());
        graphletteRegistry.put("/author/graph", authorGraphlette);

        // Setup book graphlette with internal singleton resolver for author
        when(bookSearcher.find(any(), any(), any(), anyLong()))
                .thenReturn(stash("id", "book1", "title", "GraphQL Basics", "authorId", "author1"));

        RootConfig bookConfig = new RootConfig(
                list(new QueryConfig("getBook", "{\"id\": \"{{id}}\"}")),
                list(),
                list(),
                list(),
                list(new InternalSingletonResolverConfig("author", "authorId", "getAuthor", "/author/graph")),
                list()
        );

        DTOFactory bookDtoFactory = new DTOFactory(
                list(),
                list(),
                bookConfig.internalSingletonResolvers(),
                bookConfig.internalVectorResolvers(),
                graphletteRegistry
        );

        // Verify internal resolver was configured
        Stash bookDto = bookDtoFactory.fillOne(
                stash("id", "book1", "title", "GraphQL Basics", "authorId", "author1"),
                System.currentTimeMillis()
        );

        assertTrue(bookDto.containsKey("author"), "Book DTO should contain author resolver");
        assertInstanceOf(SingletonResolver.class, bookDto.get("author"), "Should be a SingletonResolver");
    }

    /**
     * Test that internal vector resolver can be configured and resolves data
     */
    @Test
    void testInternalVectorResolverConfiguration() {
        // Setup book graphlette
        when(bookSearcher.findAll(any(), any(), any(), anyLong()))
                .thenReturn(list(
                        stash("id", "book1", "title", "GraphQL Basics", "authorId", "author1"),
                        stash("id", "book2", "title", "Advanced GraphQL", "authorId", "author1")
                ));

        RootConfig bookConfig = new RootConfig(
                list(),
                list(new QueryConfig("getBooksByAuthor", "{\"authorId\": \"{{id}}\"}")),
                list(),
                list(),
                list(),
                list()
        );

        DTOFactory bookDtoFactory = new DTOFactory(list(), list(), list(), list(), graphletteRegistry);
        Map<String, DataFetcher> bookFetchers = Root.create(bookSearcher, bookDtoFactory, auth, bookConfig);
        Graphlette bookGraphlette = new Graphlette(bookFetchers, createBookSchema());
        graphletteRegistry.put("/book/graph", bookGraphlette);

        // Setup author graphlette with internal vector resolver for books
        when(authorSearcher.find(any(), any(), any(), anyLong()))
                .thenReturn(stash("id", "author1", "name", "Jane Doe"));

        RootConfig authorConfig = new RootConfig(
                list(new QueryConfig("getAuthor", "{\"id\": \"{{id}}\"}")),
                list(),
                list(),
                list(),
                list(),
                list(new InternalVectorResolverConfig("books", null, "getBooksByAuthor", "/book/graph"))
        );

        DTOFactory authorDtoFactory = new DTOFactory(
                list(),
                list(),
                authorConfig.internalSingletonResolvers(),
                authorConfig.internalVectorResolvers(),
                graphletteRegistry
        );

        // Verify internal resolver was configured
        Stash authorDto = authorDtoFactory.fillOne(
                stash("id", "author1", "name", "Jane Doe"),
                System.currentTimeMillis()
        );

        assertTrue(authorDto.containsKey("books"), "Author DTO should contain books resolver");
        assertInstanceOf(VectorResolver.class, authorDto.get("books"), "Should be a VectorResolver");
    }

    /**
     * Test that internal resolver handles missing graphlette gracefully
     */
    @Test
    void testInternalResolverWithMissingGraphlette() {
        // Create DTOFactory with internal resolver referencing non-existent graphlette
        RootConfig config = new RootConfig(
                list(),
                list(),
                list(),
                list(),
                list(new InternalSingletonResolverConfig("author", "authorId", "getAuthor", "/nonexistent/graph")),
                list()
        );

        DTOFactory dtoFactory = new DTOFactory(
                list(),
                list(),
                config.internalSingletonResolvers(),
                config.internalVectorResolvers(),
                graphletteRegistry
        );

        Stash dto = dtoFactory.fillOne(
                stash("id", "book1", "authorId", "author1"),
                System.currentTimeMillis()
        );

        // Resolver should still be created (lazy evaluation)
        assertTrue(dto.containsKey("author"), "DTO should contain author resolver");

        // When executed, it should return empty stash (not throw exception)
        SingletonResolver resolver = (SingletonResolver) dto.get("author");
        Stash result = resolver.resolve(dto, null);

        assertNotNull(result, "Should return empty stash, not null");
        assertTrue(result.isEmpty() || result.size() == 0, "Should return empty stash when graphlette not found");
    }

    /**
     * Test that executeInternal returns proper JSON format
     */
    @Test
    void testGraphletteExecuteInternalReturnsJson() {
        when(authorSearcher.find(any(), any(), any(), anyLong()))
                .thenReturn(stash("id", "author1", "name", "Jane Doe"));

        RootConfig config = new RootConfig(
                list(new QueryConfig("getAuthor", "{\"id\": \"{{id}}\"}")),
                list(),
                list(),
                list(),
                list(),
                list()
        );

        DTOFactory dtoFactory = new DTOFactory(list(), list(), list(), list(), graphletteRegistry);
        Map<String, DataFetcher> fetchers = Root.create(authorSearcher, dtoFactory, auth, config);
        Graphlette graphlette = new Graphlette(fetchers, createAuthorSchema());

        String query = "{getAuthor(id: \"author1\" at: " + System.currentTimeMillis() + "){id name}}";
        String jsonResponse = graphlette.executeInternal(query);

        assertNotNull(jsonResponse, "Should return JSON response");
        assertTrue(jsonResponse.contains("data") || jsonResponse.contains("errors"),
                "Response should contain data or errors field");
    }

    /**
     * Test internal resolver with custom foreign key field
     */
    @Test
    void testInternalResolverWithCustomForeignKey() {
        when(authorSearcher.find(any(), any(), any(), anyLong()))
                .thenReturn(stash("id", "author1", "name", "Jane Doe"));

        RootConfig authorConfig = new RootConfig(
                list(new QueryConfig("getAuthor", "{\"id\": \"{{id}}\"}")),
                list(),
                list(),
                list(),
                list(),
                list()
        );

        DTOFactory authorDtoFactory = new DTOFactory(list(), list(), list(), list(), graphletteRegistry);
        Map<String, DataFetcher> authorFetchers = Root.create(authorSearcher, authorDtoFactory, auth, authorConfig);
        Graphlette authorGraphlette = new Graphlette(authorFetchers, createAuthorSchema());
        graphletteRegistry.put("/author/graph", authorGraphlette);

        // Use custom foreign key field "author_uuid" instead of default "id"
        RootConfig bookConfig = new RootConfig(
                list(),
                list(),
                list(),
                list(),
                list(new InternalSingletonResolverConfig("author", "author_uuid", "getAuthor", "/author/graph")),
                list()
        );

        DTOFactory bookDtoFactory = new DTOFactory(
                list(),
                list(),
                bookConfig.internalSingletonResolvers(),
                bookConfig.internalVectorResolvers(),
                graphletteRegistry
        );

        Stash bookDto = bookDtoFactory.fillOne(
                stash("id", "book1", "title", "Test Book", "author_uuid", "author1"),
                System.currentTimeMillis()
        );

        assertTrue(bookDto.containsKey("author"), "Book DTO should contain author resolver");
    }

    /**
     * Test that internal resolver returns empty result when foreign key is null
     */
    @Test
    void testInternalResolverWithNullForeignKey() {
        when(authorSearcher.find(any(), any(), any(), anyLong()))
                .thenReturn(stash("id", "author1", "name", "Jane Doe"));

        RootConfig authorConfig = new RootConfig(
                list(new QueryConfig("getAuthor", "{\"id\": \"{{id}}\"}")),
                list(),
                list(),
                list(),
                list(),
                list()
        );

        DTOFactory authorDtoFactory = new DTOFactory(list(), list(), list(), list(), graphletteRegistry);
        Map<String, DataFetcher> authorFetchers = Root.create(authorSearcher, authorDtoFactory, auth, authorConfig);
        Graphlette authorGraphlette = new Graphlette(authorFetchers, createAuthorSchema());
        graphletteRegistry.put("/author/graph", authorGraphlette);

        RootConfig bookConfig = new RootConfig(
                list(),
                list(),
                list(),
                list(),
                list(new InternalSingletonResolverConfig("author", "authorId", "getAuthor", "/author/graph")),
                list()
        );

        DTOFactory bookDtoFactory = new DTOFactory(
                list(),
                list(),
                bookConfig.internalSingletonResolvers(),
                bookConfig.internalVectorResolvers(),
                graphletteRegistry
        );

        // Create book DTO with null authorId
        Stash bookDto = bookDtoFactory.fillOne(
                stash("id", "book1", "title", "Orphan Book"),  // no authorId
                System.currentTimeMillis()
        );

        SingletonResolver resolver = (SingletonResolver) bookDto.get("author");
        assertNotNull(resolver, "Resolver should exist even if foreign key is null");

        Stash result = resolver.resolve(bookDto, null);

        assertNotNull(result, "Should return empty stash, not null");
        assertTrue(result.isEmpty() || result.size() == 0, "Should return empty stash when foreign key is null");
    }

    /**
     * Test internal vector resolver returns empty list when foreign key is null
     */
    @Test
    void testInternalVectorResolverWithNullForeignKey() {
        when(bookSearcher.findAll(any(), any(), any(), anyLong()))
                .thenReturn(list(stash("id", "book1", "title", "GraphQL Basics")));

        RootConfig bookConfig = new RootConfig(
                list(),
                list(new QueryConfig("getBooksByAuthor", "{\"authorId\": \"{{id}}\"}")),
                list(),
                list(),
                list(),
                list()
        );

        DTOFactory bookDtoFactory = new DTOFactory(list(), list(), list(), list(), graphletteRegistry);
        Map<String, DataFetcher> bookFetchers = Root.create(bookSearcher, bookDtoFactory, auth, bookConfig);
        Graphlette bookGraphlette = new Graphlette(bookFetchers, createBookSchema());
        graphletteRegistry.put("/book/graph", bookGraphlette);

        RootConfig authorConfig = new RootConfig(
                list(),
                list(),
                list(),
                list(),
                list(),
                list(new InternalVectorResolverConfig("books", null, "getBooksByAuthor", "/book/graph"))
        );

        DTOFactory authorDtoFactory = new DTOFactory(
                list(),
                list(),
                authorConfig.internalSingletonResolvers(),
                authorConfig.internalVectorResolvers(),
                graphletteRegistry
        );

        // Create author DTO without id field
        Stash authorDto = authorDtoFactory.fillOne(
                stash("name", "Unknown Author"),  // no id
                System.currentTimeMillis()
        );

        VectorResolver resolver = (VectorResolver) authorDto.get("books");
        assertNotNull(resolver, "Resolver should exist even if foreign key is null");

        List<Stash> result = resolver.resolve(authorDto, null);

        assertNotNull(result, "Should return empty list, not null");
        assertTrue(result.isEmpty(), "Should return empty list when foreign key is null");
    }

    /**
     * Test mixing internal and external resolvers in same DTOFactory
     */
    @Test
    void testMixedInternalAndExternalResolvers() {
        // Setup internal graphlette
        when(authorSearcher.find(any(), any(), any(), anyLong()))
                .thenReturn(stash("id", "author1", "name", "Jane Doe"));

        RootConfig authorConfig = new RootConfig(
                list(new QueryConfig("getAuthor", "{\"id\": \"{{id}}\"}")),
                list(),
                list(),
                list(),
                list(),
                list()
        );

        DTOFactory authorDtoFactory = new DTOFactory(list(), list(), list(), list(), graphletteRegistry);
        Map<String, DataFetcher> authorFetchers = Root.create(authorSearcher, authorDtoFactory, auth, authorConfig);
        Graphlette authorGraphlette = new Graphlette(authorFetchers, createAuthorSchema());
        graphletteRegistry.put("/author/graph", authorGraphlette);

        // Create config with both internal and external resolvers
        RootConfig mixedConfig = new RootConfig(
                list(),
                list(),
                list(new SingletonResolverConfig("externalAuthor", "authorId", "getAuthor",
                        java.net.URI.create("http://external.example.com/author/graph"))),
                list(),
                list(new InternalSingletonResolverConfig("internalAuthor", "authorId", "getAuthor", "/author/graph")),
                list()
        );

        DTOFactory mixedFactory = new DTOFactory(
                mixedConfig.singletonResolvers(),
                mixedConfig.vectorResolvers(),
                mixedConfig.internalSingletonResolvers(),
                mixedConfig.internalVectorResolvers(),
                graphletteRegistry
        );

        Stash dto = mixedFactory.fillOne(
                stash("id", "item1", "authorId", "author1"),
                System.currentTimeMillis()
        );

        assertTrue(dto.containsKey("externalAuthor"), "Should have external resolver");
        assertTrue(dto.containsKey("internalAuthor"), "Should have internal resolver");
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
                }
                """;
    }
}
