package com.flex.ui.components

const val TOOLTIP_FLEXTIME_TITLE = "Gleitzeit-Saldo"
val TOOLTIP_FLEXTIME = """
Saldo = Anfangssaldo + Summe der täglichen Differenzen.

• Arbeitstag (Mo–Fr): Netto − Tagessoll
• Wochenendarbeit: volle Nettozeit
• Gleittag: −Tagessoll
• Urlaub / Sonderurlaub: neutral (0)
""".trim()

const val TOOLTIP_OVERTIME_TITLE = "Überstunden-Saldo"
val TOOLTIP_OVERTIME = """
Überstunden entstehen nur durch Samstagsarbeit (Typ "Samstag Bonus"): 50 % der Nettozeit werden als Überstunden gutgeschrieben.

Dazu kommt der eingetragene Anfangssaldo aus den Einstellungen.
""".trim()

const val TOOLTIP_OFFICE_QUOTA_TITLE = "Büro-Quote"
val TOOLTIP_OFFICE_QUOTA = """
Quote gilt als erfüllt wenn:

Büro-Prozent ≥ Ziel  ODER  Büro-Tage ≥ Minimum

Die Büro-Stunden werden anteilig nach der Brutto-Zeit der Zeitblöcke je Standort verteilt. Neutrale Tage (Urlaub, Gleittag) werden vom Monatssoll abgezogen.
""".trim()

const val TOOLTIP_OFFICE_DAYS_TITLE = "Büro-Tage"
val TOOLTIP_OFFICE_DAYS = """
Ein Tag zählt als Büro-Tag, wenn die gesamte Brutto-Bürozeit ≥ der gesamten Brutto-Home-Office-Zeit an diesem Tag ist.
""".trim()

const val TOOLTIP_WORK_TIME_TITLE = "Arbeitszeit (netto)"
val TOOLTIP_WORK_TIME = """
Netto = Brutto − Pausen

Pausen: Lücken zwischen Zeitblöcken + gesetzlicher Aufschlag:
• Ab 6h Brutto: mind. 30 min Pause
• Ab 9h Netto: weitere 15 min

Maximum: 10h pro Tag. Start- und Endzeiten werden auf 5 min gerundet.
""".trim()

const val TOOLTIP_VACATION_TITLE = "Jahresurlaub"
val TOOLTIP_VACATION = """
Verbleibend = Jahresanspruch + Resturlaub Vorjahr − genommen − geplant
""".trim()

const val TOOLTIP_SPECIAL_VACATION_TITLE = "Sonderurlaub"
val TOOLTIP_SPECIAL_VACATION = """
Sonderurlaub verfällt am 31. Oktober des laufenden Jahres.

Verbleibend = Anspruch − genommen − geplant
""".trim()

const val TOOLTIP_PROGNOSIS_TITLE = "Prognose"
val TOOLTIP_PROGNOSIS = """
Hochrechnung der Büro-Quote und Arbeitszeit basierend auf allen bisherigen und geplanten Arbeitstagen des Monats.

Geleistete Zeit:
• Arbeitstage zählen mit tatsächlichen Stunden
• Urlaub, Kranktage und Gleittage zählen als voller Arbeitstag
• Gleittage werden zusätzlich von der Gleitzeit-Bilanz abgezogen
""".trim()

const val TOOLTIP_QUOTA_PREVIEW_TITLE = "Quoten-Vorschau"
val TOOLTIP_QUOTA_PREVIEW = """
Vorschau der Quote basierend auf der aktuellen Monatsplanung. Zeigt wie viele Büro-Stunden und -Tage bei Umsetzung der Planung erreicht werden.
""".trim()

const val TOOLTIP_FLEXTIME_PROGNOSIS_TITLE = "Gleitzeit-Prognose"
val TOOLTIP_FLEXTIME_PROGNOSIS = """
Prognostizierter Saldo, wenn alle geplanten Tage wie geplant gearbeitet werden.

Soll = Arbeitstage (Mo–Fr ohne Feiertage) × Tagessoll
""".trim()
