package com.flex.testing

/**
 * Lightweight, zero-overhead BDD DSL for Android Instrumented Tests (Compose & Hilt).
 * Enables Given / When / Then / And living documentation without cucumber glue-code.
 */
inline fun bddScenario(name: String, block: BddScenarioScope.() -> Unit) {
    BddScenarioScope(name).apply(block)
}

class BddScenarioScope(val scenarioName: String) {
    inline fun Given(description: String, block: () -> Unit) = block()
    inline fun When(description: String, block: () -> Unit) = block()
    inline fun Then(description: String, block: () -> Unit) = block()
    inline fun And(description: String, block: () -> Unit) = block()
}
