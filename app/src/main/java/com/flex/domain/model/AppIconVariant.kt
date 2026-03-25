package com.flex.domain.model

enum class AppIconVariant(
    val aliasSimpleName: String,
    val displayName: String,
    val appLabel: String
) {
    CLASSIC(
        aliasSimpleName = "MainActivityAliasClassic",
        displayName = "FleX",
        appLabel = "FleX"
    ),
    VREMA(
        aliasSimpleName = "MainActivityAliasVrema",
        displayName = "Vrema",
        appLabel = "Vrema"
    );

    fun fullComponentName(packageName: String) = "$packageName.$aliasSimpleName"
}
