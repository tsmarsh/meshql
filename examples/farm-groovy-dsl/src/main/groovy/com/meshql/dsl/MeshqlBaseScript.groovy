package com.meshql.dsl

/**
 * Base script class for MeshQL DSL scripts.
 * <p>
 * Provides the top-level {@code meshql { ... }} function.
 * Used with {@code CompilerConfiguration.setScriptBaseClass}.
 */
abstract class MeshqlBaseScript extends Script {

    MeshqlDsl meshql(@DelegatesTo(MeshqlDsl) Closure cl) {
        MeshqlDsl dsl = new MeshqlDsl()
        cl.delegate = dsl
        cl.resolveStrategy = Closure.DELEGATE_FIRST
        cl.call()
        return dsl
    }
}
