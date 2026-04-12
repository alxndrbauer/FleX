package com.flex.domain.model

enum class FederalState(val code: String, val displayName: String) {
    BADEN_WUERTTEMBERG("BW", "Baden-Württemberg"),
    BAVARIA("BY", "Bayern"),
    BERLIN("BE", "Berlin"),
    BRANDENBURG("BB", "Brandenburg"),
    BREMEN("HB", "Bremen"),
    HAMBURG("HH", "Hamburg"),
    HESSE("HE", "Hessen"),
    MECKLENBURG_VORPOMMERN("MV", "Mecklenburg-Vorpommern"),
    LOWER_SAXONY("NI", "Niedersachsen"),
    NORTH_RHINE_WESTPHALIA("NW", "Nordrhein-Westfalen"),
    RHINELAND_PALATINATE("RP", "Rheinland-Pfalz"),
    SAARLAND("SL", "Saarland"),
    SAXONY("SN", "Sachsen"),
    SAXONY_ANHALT("ST", "Sachsen-Anhalt"),
    SCHLESWIG_HOLSTEIN("SH", "Schleswig-Holstein"),
    THURINGIA("TH", "Thüringen");

    val countyCode: String get() = "DE-$code"
}
