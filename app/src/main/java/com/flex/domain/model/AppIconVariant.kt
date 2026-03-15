package com.flex.domain.model

enum class AppIconVariant(
    val aliasSimpleName: String,
    val displayName: String,
    val appLabel: String,
    val backgroundColor: Long
) {
    CLASSIC(
        aliasSimpleName = "MainActivityAliasClassic",
        displayName = "Klassisch",
        appLabel = "FleX",
        backgroundColor = 0xFF3DDC84
    ),
    OCEAN(
        aliasSimpleName = "MainActivityAliasOcean",
        displayName = "Ozean",
        appLabel = "FleX",
        backgroundColor = 0xFF1565C0
    ),
    NIGHT(
        aliasSimpleName = "MainActivityAliasNight",
        displayName = "Nacht",
        appLabel = "FleX",
        backgroundColor = 0xFF212121
    ),
    WARM(
        aliasSimpleName = "MainActivityAliasWarm",
        displayName = "Warm",
        appLabel = "FleX",
        backgroundColor = 0xFFE65100
    ),
    PROFESSIONAL(
        aliasSimpleName = "MainActivityAliasProfessional",
        displayName = "Professionell",
        appLabel = "FlexTime",
        backgroundColor = 0xFF1A237E
    ),
    DISCREET(
        aliasSimpleName = "MainActivityAliasDiscreet",
        displayName = "Diskret",
        appLabel = "Zeiterfassung",
        backgroundColor = 0xFF455A64
    ),
    VREMA(
        aliasSimpleName = "MainActivityAliasVrema",
        displayName = "Vrema",
        appLabel = "Vrema",
        backgroundColor = 0xFF3DDC84
    );

    fun fullComponentName(packageName: String) = "$packageName.$aliasSimpleName"
}
