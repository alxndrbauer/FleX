package com.flex.specification.fixture

import com.flex.domain.model.DayType
import com.flex.domain.model.Settings
import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkDay
import com.flex.domain.model.WorkLocation
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Typisierte Testdatenbasis für den realen Monat September 2026.
 * Daten entstammen dem 1:1 FleX-Export (22 Arbeitstage, 156:12h Soll, 158:16h Ist, +2:04h Saldo).
 */
object September2026ExportFixture {

    val YEAR_MONTH: YearMonth = YearMonth.of(2026, 9)
    const val DAILY_TARGET_MINUTES: Int = 426 // 7:06 h
    const val EXPECTED_MONTHLY_TARGET_MINUTES: Long = 9372L // 156:12 h (22 * 426)
    const val EXPECTED_NET_MINUTES: Long = 9496L // 158:16 h
    const val EXPECTED_DIFF_MINUTES: Long = 124L // +2:04 h
    const val EXPECTED_OFFICE_DAYS: Int = 8
    const val EXPECTED_HOME_OFFICE_DAYS: Int = 14

    fun createDefaultSettings(): Settings = Settings(
        dailyWorkMinutes = DAILY_TARGET_MINUTES,
        monthlyWorkMinutes = EXPECTED_MONTHLY_TARGET_MINUTES.toInt(),
        officeQuotaPercent = 40,
        officeQuotaMinDays = 8,
        initialFlextimeMinutes = 0,
        initialOvertimeMinutes = 0
    )

    fun createSeptember2026WorkDays(): List<WorkDay> = listOf(
        // 01.09.2026: Di, Büro, 09:10–17:01 (Rundung: 09:10–17:05 = 7:55, -30m = 7:25)
        workDay(
            date = LocalDate.of(2026, 9, 1),
            location = WorkLocation.OFFICE,
            blocks = listOf(
                block(LocalTime.of(9, 10), LocalTime.of(17, 1), WorkLocation.OFFICE)
            )
        ),
        // 02.09.2026: Mi, Büro, 08:40–17:52 (Rundung: 08:40–17:55 = 9:15, -30m = 8:45)
        workDay(
            date = LocalDate.of(2026, 9, 2),
            location = WorkLocation.OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 40), LocalTime.of(17, 52), WorkLocation.OFFICE)
            )
        ),
        // 03.09.2026: Do, HO, 08:51–16:38 (2 Blöcke, 2:10h Pause, Brutto 5:40, Netto 5:40)
        workDay(
            date = LocalDate.of(2026, 9, 3),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 51), LocalTime.of(12, 0), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(14, 10), LocalTime.of(16, 38), WorkLocation.HOME_OFFICE)
            )
        ),
        // 04.09.2026: Fr, HO, 08:29–15:41 (2 Blöcke, 1:20h Pause, Brutto 6:00, Netto 6:00)
        workDay(
            date = LocalDate.of(2026, 9, 4),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 29), LocalTime.of(12, 0), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 20), LocalTime.of(15, 41), WorkLocation.HOME_OFFICE)
            )
        ),
        // 07.09.2026: Mo, Büro, 08:41–17:54 (Rundung: 08:40–17:55 = 9:15, -30m = 8:45)
        workDay(
            date = LocalDate.of(2026, 9, 7),
            location = WorkLocation.OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 41), LocalTime.of(17, 54), WorkLocation.OFFICE)
            )
        ),
        // 08.09.2026: Di, Büro, 09:13–16:58 (Rundung: 09:10–17:00 = 7:50, -30m = 7:20)
        workDay(
            date = LocalDate.of(2026, 9, 8),
            location = WorkLocation.OFFICE,
            blocks = listOf(
                block(LocalTime.of(9, 13), LocalTime.of(16, 58), WorkLocation.OFFICE)
            )
        ),
        // 09.09.2026: Mi, HO, 09:45–16:09 (2 Blöcke, 1:23h Pause, Brutto 5:02, Netto 5:02)
        workDay(
            date = LocalDate.of(2026, 9, 9),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(9, 45), LocalTime.of(12, 0), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 23), LocalTime.of(16, 9), WorkLocation.HOME_OFFICE)
            )
        ),
        // 10.09.2026: Do, HO, 09:48–17:14 (2 Blöcke, 1:14h Pause, Brutto 6:16, Netto 6:16)
        workDay(
            date = LocalDate.of(2026, 9, 10),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(9, 48), LocalTime.of(12, 30), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 44), LocalTime.of(17, 14), WorkLocation.HOME_OFFICE)
            )
        ),
        // 11.09.2026: Fr, HO, 09:40–15:09 (2 Blöcke, 1:02h Pause, Brutto 4:28, Netto 4:28)
        workDay(
            date = LocalDate.of(2026, 9, 11),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(9, 40), LocalTime.of(12, 0), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 2), LocalTime.of(15, 9), WorkLocation.HOME_OFFICE)
            )
        ),
        // 14.09.2026: Mo, Büro, 08:44–16:33 (Rundung: 08:40–16:35 = 7:55, -30m = 7:25)
        workDay(
            date = LocalDate.of(2026, 9, 14),
            location = WorkLocation.OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 44), LocalTime.of(16, 33), WorkLocation.OFFICE)
            )
        ),
        // 15.09.2026: Di, Büro, 08:45–16:41 (Rundung: 08:45–16:45 = 8:00, -30m = 7:30)
        workDay(
            date = LocalDate.of(2026, 9, 15),
            location = WorkLocation.OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 45), LocalTime.of(16, 41), WorkLocation.OFFICE)
            )
        ),
        // 16.09.2026: Mi, HO, 06:00–17:33 (2 Blöcke, 2:24h Pause, Brutto 9:11, Netto 9:11)
        workDay(
            date = LocalDate.of(2026, 9, 16),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(6, 0), LocalTime.of(12, 0), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(14, 24), LocalTime.of(17, 33), WorkLocation.HOME_OFFICE)
            )
        ),
        // 17.09.2026: Do, HO, 08:45–17:31 (2 Blöcke, 1:06h Pause, Brutto 7:44, Netto 7:44)
        workDay(
            date = LocalDate.of(2026, 9, 17),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 45), LocalTime.of(12, 30), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 36), LocalTime.of(17, 31), WorkLocation.HOME_OFFICE)
            )
        ),
        // 18.09.2026: Fr, HO, 08:57–15:38 (2 Blöcke, 1:57h Pause, Brutto 4:48, Netto 4:48)
        workDay(
            date = LocalDate.of(2026, 9, 18),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 57), LocalTime.of(11, 30), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 27), LocalTime.of(15, 38), WorkLocation.HOME_OFFICE)
            )
        ),
        // 21.09.2026: Mo, HO, 08:02–16:51 (2 Blöcke, 1:01h Pause, Brutto 7:54, Netto 7:54)
        workDay(
            date = LocalDate.of(2026, 9, 21),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 2), LocalTime.of(12, 0), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 1), LocalTime.of(16, 51), WorkLocation.HOME_OFFICE)
            )
        ),
        // 22.09.2026: Di, Büro, 09:03–16:52 (Rundung: 09:00–16:55 = 7:55, -30m = 7:25)
        workDay(
            date = LocalDate.of(2026, 9, 22),
            location = WorkLocation.OFFICE,
            blocks = listOf(
                block(LocalTime.of(9, 3), LocalTime.of(16, 52), WorkLocation.OFFICE)
            )
        ),
        // 23.09.2026: Mi, HO, 08:29–17:30 (2 Blöcke, 0:46h Pause, Brutto 8:19, Netto 8:19)
        workDay(
            date = LocalDate.of(2026, 9, 23),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 29), LocalTime.of(12, 30), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 16), LocalTime.of(17, 30), WorkLocation.HOME_OFFICE)
            )
        ),
        // 24.09.2026: Do, HO, 08:56–17:55 (2 Blöcke, 1:23h Pause, Brutto 7:37, Netto 7:37)
        workDay(
            date = LocalDate.of(2026, 9, 24),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 56), LocalTime.of(12, 30), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 53), LocalTime.of(17, 55), WorkLocation.HOME_OFFICE)
            )
        ),
        // 25.09.2026: Fr, HO, 08:31–17:58 (2 Blöcke, 0:43h Pause, Brutto 8:47, Netto 8:47)
        workDay(
            date = LocalDate.of(2026, 9, 25),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 31), LocalTime.of(12, 30), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 13), LocalTime.of(17, 58), WorkLocation.HOME_OFFICE)
            )
        ),
        // 28.09.2026: Mo, HO, 09:08–16:58 (2 Blöcke, 1:01h Pause, Brutto 6:54, Netto 6:54)
        workDay(
            date = LocalDate.of(2026, 9, 28),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(9, 8), LocalTime.of(12, 30), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 31), LocalTime.of(16, 58), WorkLocation.HOME_OFFICE)
            )
        ),
        // 29.09.2026: Di, HO, 08:36–17:33 (2 Blöcke, 0:43h Pause, Brutto 8:17, Netto 8:17)
        workDay(
            date = LocalDate.of(2026, 9, 29),
            location = WorkLocation.HOME_OFFICE,
            blocks = listOf(
                block(LocalTime.of(8, 36), LocalTime.of(12, 30), WorkLocation.HOME_OFFICE),
                block(LocalTime.of(13, 13), LocalTime.of(17, 33), WorkLocation.HOME_OFFICE)
            )
        ),
        // 30.09.2026: Mi, Misch-Tag Büro & HO, 09:02–16:31 (2 Blöcke: Büro 09:02–15:00, HO 15:51–16:31, 0:51h Pause, Brutto 6:44, Netto 6:44)
        workDay(
            date = LocalDate.of(2026, 9, 30),
            location = WorkLocation.OFFICE,
            blocks = listOf(
                block(LocalTime.of(9, 2), LocalTime.of(15, 0), WorkLocation.OFFICE),
                block(LocalTime.of(15, 51), LocalTime.of(16, 31), WorkLocation.HOME_OFFICE)
            )
        )
    )

    fun rawCsvContent(): String = """
Datum;Tag;Typ;Ort;Start;Ende;Brutto;Pause;Netto;Soll;Differenz;Notiz
01.09.2026;Dienstag;Arbeit;Büro;09:10;17:01;7:55;0:30;7:25;7:06;+0:19;
02.09.2026;Mittwoch;Arbeit;Büro;08:40;17:52;9:15;0:30;8:45;7:06;+1:39;
03.09.2026;Donnerstag;Arbeit;HO;08:51;16:38;5:40;2:10;5:40;7:06;1:26;
04.09.2026;Freitag;Arbeit;HO;08:29;15:41;6:00;1:20;6:00;7:06;1:06;
05.09.2026;Samstag;-;-;-;-;-;-;-;-;-;
06.09.2026;Sonntag;-;-;-;-;-;-;-;-;-;
07.09.2026;Montag;Arbeit;Büro;08:41;17:54;9:15;0:30;8:45;7:06;+1:39;
08.09.2026;Dienstag;Arbeit;Büro;09:13;16:58;7:50;0:30;7:20;7:06;+0:14;
09.09.2026;Mittwoch;Arbeit;HO;09:45;16:09;5:02;1:23;5:02;7:06;2:04;
10.09.2026;Donnerstag;Arbeit;HO;09:48;17:14;6:16;1:14;6:16;7:06;0:50;
11.09.2026;Freitag;Arbeit;HO;09:40;15:09;4:28;1:02;4:28;7:06;2:38;
12.09.2026;Samstag;-;-;-;-;-;-;-;-;-;
13.09.2026;Sonntag;-;-;-;-;-;-;-;-;-;
14.09.2026;Montag;Arbeit;Büro;08:44;16:33;7:55;0:30;7:25;7:06;+0:19;
15.09.2026;Dienstag;Arbeit;Büro;08:45;16:41;8:00;0:30;7:30;7:06;+0:24;
16.09.2026;Mittwoch;Arbeit;HO;06:00;17:33;9:11;2:24;9:11;7:06;+2:05;
17.09.2026;Donnerstag;Arbeit;HO;08:45;17:31;7:44;1:06;7:44;7:06;+0:38;
18.09.2026;Freitag;Arbeit;HO;08:57;15:38;4:48;1:57;4:48;7:06;2:18;
19.09.2026;Samstag;-;-;-;-;-;-;-;-;-;
20.09.2026;Sonntag;-;-;-;-;-;-;-;-;-;
21.09.2026;Montag;Arbeit;HO;08:02;16:51;7:54;1:01;7:54;7:06;+0:48;
22.09.2026;Dienstag;Arbeit;Büro;09:03;16:52;7:55;0:30;7:25;7:06;+0:19;
23.09.2026;Mittwoch;Arbeit;HO;08:29;17:30;8:19;0:46;8:19;7:06;+1:13;
24.09.2026;Donnerstag;Arbeit;HO;08:56;17:55;7:37;1:23;7:37;7:06;+0:31;
25.09.2026;Freitag;Arbeit;HO;08:31;17:58;8:47;0:43;8:47;7:06;+1:41;
26.09.2026;Samstag;-;-;-;-;-;-;-;-;-;
27.09.2026;Sonntag;-;-;-;-;-;-;-;-;-;
28.09.2026;Montag;Arbeit;HO;09:08;16:58;6:54;1:01;6:54;7:06;0:12;
29.09.2026;Dienstag;Arbeit;HO;08:36;17:33;8:17;0:43;8:17;7:06;+1:11;
30.09.2026;Mittwoch;Arbeit;Büro;09:02;15:00;5:58;-;5:58;-;-;
30.09.2026;Mittwoch;Arbeit;HO;15:51;16:31;0:40;-;0:40;-;-;
30.09.2026;Mittwoch;Gesamt;-;09:02;16:31;6:44;0:51;6:44;7:06;0:22;
;;;;;;;Gesamt;;158:16;156:12;+2:04;
""".trimIndent()

    private fun workDay(
        date: LocalDate,
        location: WorkLocation,
        blocks: List<TimeBlock>,
        dayType: DayType = DayType.WORK
    ): WorkDay = WorkDay(
        date = date,
        location = location,
        dayType = dayType,
        isPlanned = false,
        timeBlocks = blocks
    )

    private fun block(
        start: LocalTime,
        end: LocalTime,
        location: WorkLocation
    ): TimeBlock = TimeBlock(
        startTime = start,
        endTime = end,
        location = location,
        isDuration = false
    )
}
