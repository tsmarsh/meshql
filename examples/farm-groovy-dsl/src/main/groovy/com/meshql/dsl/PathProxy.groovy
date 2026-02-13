package com.meshql.dsl

/**
 * Builds dotted path strings via Groovy's propertyMissing.
 * <p>
 * {@code payload.coop_id} becomes {@code "payload.coop_id"}.
 * Used as map values in query definitions: {@code [id: id]} becomes
 * {@code ["id": "{{id}}"]}} after Handlebars wrapping.
 */
class PathProxy {
    final String path

    PathProxy(String path) {
        this.path = path
    }

    def propertyMissing(String name) {
        new PathProxy("${path}.${name}")
    }

    /** Returns the Handlebars-wrapped value: {@code "{{name}}"} */
    String toHandlebars() {
        "{{${path}}}"
    }

    @Override
    String toString() {
        path
    }
}
