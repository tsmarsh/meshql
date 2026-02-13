package com.meshql.dsl;

import com.meshql.core.Config;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilerConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Java entry point for the Groovy DSL.
 * <p>
 * Usage from Java:
 * <pre>
 * Config config = MeshQL.configure(new File("farm.groovy"));
 * Config config = MeshQL.configureFromClasspath("farm.groovy");
 * </pre>
 */
public class MeshQL {

    private static GroovyShell createShell() {
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.setScriptBaseClass(MeshqlBaseScript.class.getName());
        return new GroovyShell(MeshQL.class.getClassLoader(), cc);
    }

    /**
     * Parse a Groovy DSL file and return the MeshQL Config.
     */
    public static Config configure(File scriptFile) throws IOException {
        Script script = createShell().parse(scriptFile);
        return evaluateScript(script);
    }

    /**
     * Parse a Groovy DSL string and return the MeshQL Config.
     */
    public static Config configureFromString(String scriptText) {
        Script script = createShell().parse(scriptText);
        return evaluateScript(script);
    }

    /**
     * Parse a Groovy DSL from a classpath resource and return the MeshQL Config.
     */
    public static Config configureFromClasspath(String resourcePath) throws IOException {
        InputStream is = MeshQL.class.getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("Resource not found on classpath: " + resourcePath);
        }
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            Script script = createShell().parse(reader);
            return evaluateScript(script);
        }
    }

    /**
     * Build a Config using the DSL closure directly from Java.
     */
    public static Config configure(@DelegatesTo(MeshqlDsl.class) Closure<?> closure) {
        MeshqlDsl dsl = new MeshqlDsl();
        closure.setDelegate(dsl);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return dsl.toConfig();
    }

    private static Config evaluateScript(Script script) {
        Object result = script.run();
        if (result instanceof MeshqlDsl dsl) {
            return dsl.toConfig();
        }
        throw new IllegalStateException(
                "Groovy DSL script must return a MeshqlDsl instance (use meshql { ... } at top level). " +
                "Got: " + (result == null ? "null" : result.getClass().getName()));
    }
}
