package com.meshql.dsl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PathProxy Handlebars variable and dotted path building.
 */
class PathProxyTest {

    @Test
    void simplePathToString() {
        PathProxy proxy = new PathProxy("id");
        assertEquals("id", proxy.toString());
    }

    @Test
    void simplePathToHandlebars() {
        PathProxy proxy = new PathProxy("id");
        assertEquals("{{id}}", proxy.toHandlebars());
    }

    @Test
    void namedPathToHandlebars() {
        PathProxy proxy = new PathProxy("name");
        assertEquals("{{name}}", proxy.toHandlebars());
    }

    @Test
    void dottedPathViaPropertyAccess() {
        PathProxy proxy = new PathProxy("payload");
        PathProxy dotted = (PathProxy) proxy.propertyMissing("coop_id");
        assertEquals("payload.coop_id", dotted.toString());
        assertEquals("{{payload.coop_id}}", dotted.toHandlebars());
    }

    @Test
    void deeplyNestedPath() {
        PathProxy proxy = new PathProxy("a");
        PathProxy b = (PathProxy) proxy.propertyMissing("b");
        PathProxy c = (PathProxy) b.propertyMissing("c");
        assertEquals("a.b.c", c.toString());
        assertEquals("{{a.b.c}}", c.toHandlebars());
    }
}
