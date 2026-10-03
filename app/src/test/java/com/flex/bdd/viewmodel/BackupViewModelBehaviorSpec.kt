package com.flex.bdd.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.flex.data.backup.BackupPreferences
import com.flex.data.backup.BackupRepository
import com.flex.data.backup.BackupWorker
import com.flex.ui.backup.BackupViewModel
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import io.kotest.assertions.nondeterministic.eventually
import kotlin.time.Duration.Companion.seconds
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelBehaviorSpec : BehaviorSpec({

    val testDispatcher = UnconfinedTestDispatcher()

    beforeSpec {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(Uri::class)
        mockkStatic(DocumentFile::class)
        mockkObject(WorkManager.Companion)
    }

    afterSpec {
        Dispatchers.resetMain()
        unmockkAll()
    }

    fun setupMocks(): Triple<Context, BackupRepository, BackupPreferences> {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver

        val backupRepository = mockk<BackupRepository>(relaxed = true)
        val backupPreferences = mockk<BackupPreferences>(relaxed = true)

        val workManager = mockk<WorkManager>(relaxed = true)
        every { WorkManager.getInstance(any()) } returns workManager

        return Triple(context, backupRepository, backupPreferences)
    }

    Given("ein BackupViewModel mit bestehenden Backup-Einstellungen") {
        val (context, backupRepository, backupPreferences) = setupMocks()

        val mockUri = mockk<Uri>(relaxed = true)
        every { mockUri.lastPathSegment } returns "Backups"
        every { Uri.parse("content://backups") } returns mockUri

        val mockFile1 = mockk<DocumentFile>(relaxed = true)
        every { mockFile1.name } returns "flex_backup_20261001_020000.json"
        val mockFile2 = mockk<DocumentFile>(relaxed = true)
        every { mockFile2.name } returns "flex_backup_20261002_020000.json"

        val mockDocDir = mockk<DocumentFile>(relaxed = true)
        every { mockDocDir.name } returns "Backups"
        every { mockDocDir.listFiles() } returns arrayOf(mockFile1, mockFile2)
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockDocDir

        every { backupPreferences.isAutoBackupEnabled } returns false
        every { backupPreferences.autoBackupDirectoryUri } returns "content://backups"
        every { backupPreferences.autoBackupHour } returns 3
        every { backupPreferences.autoBackupMinute } returns 30
        every { backupPreferences.maxLocalBackups } returns 5
        every { backupPreferences.lastBackupTimestamp } returns 1774000000000L

        val viewModel = BackupViewModel(context, backupRepository, backupPreferences)

        When("der initiale State geladen wird") {
            val state = viewModel.uiState.value

            Then("ist der Auto-Backup Status korrekt geladen") {
                state.isAutoBackupEnabled.shouldBeFalse()
            }

            Then("sind die Backup-Uhrzeit und Verzeichnis korrekt gesetzt") {
                state.autoBackupHour shouldBe 3
                state.autoBackupMinute shouldBe 30
                state.autoBackupDirectoryName shouldBe "Backups"
            }

            Then("ist der Zeitstempel des letzten Backups formatiert vorhanden") {
                state.lastBackupTime.shouldNotBeNull()
            }

            Then("wird die Anzahl der vorhandenen lokalen Backups ermittelt") {
                state.localBackupCount shouldBe 2
            }

            Then("ist die maximale Backup-Anzahl geladen") {
                state.maxLocalBackups shouldBe 5
            }
        }
    }

    Given("die Steuerung von Auto-Backup") {
        val (context, backupRepository, backupPreferences) = setupMocks()
        val workManager = WorkManager.getInstance(context)

        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns mockUri
        val mockDocDir = mockk<DocumentFile>(relaxed = true)
        every { mockDocDir.listFiles() } returns emptyArray()
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockDocDir

        When("Auto-Backup aktiviert wird, aber noch kein Verzeichnis gewählt wurde") {
            every { backupPreferences.autoBackupDirectoryUri } returns null
            every { backupPreferences.isAutoBackupEnabled } returns false

            val viewModel = BackupViewModel(context, backupRepository, backupPreferences)
            viewModel.toggleAutoBackup(true)

            Then("wird eine Hinweismeldung gesetzt und Auto-Backup nicht aktiviert") {
                viewModel.uiState.value.message shouldBe "Bitte zuerst ein Verzeichnis wählen"
                viewModel.uiState.value.isAutoBackupEnabled.shouldBeFalse()
                verify(exactly = 0) { workManager.enqueueUniquePeriodicWork(any(), any(), any()) }
            }
        }

        When("Auto-Backup mit vorhandenem Verzeichnis aktiviert wird") {
            every { backupPreferences.autoBackupDirectoryUri } returns "content://valid/path"
            every { backupPreferences.isAutoBackupEnabled } returns false

            val viewModel = BackupViewModel(context, backupRepository, backupPreferences)
            viewModel.toggleAutoBackup(true)

            Then("wird Auto-Backup in Preferences und State auf true gesetzt") {
                verify { backupPreferences.isAutoBackupEnabled = true }
                viewModel.uiState.value.isAutoBackupEnabled.shouldBeTrue()
            }

            Then("wird ein periodischer Backup-Job im WorkManager eingeplant") {
                verify {
                    workManager.enqueueUniquePeriodicWork(
                        BackupWorker.WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        any()
                    )
                }
            }
        }

        When("Auto-Backup deaktiviert wird") {
            every { backupPreferences.autoBackupDirectoryUri } returns "content://valid/path"
            every { backupPreferences.isAutoBackupEnabled } returns true

            val viewModel = BackupViewModel(context, backupRepository, backupPreferences)
            viewModel.toggleAutoBackup(false)

            Then("wird Auto-Backup in Preferences und State auf false gesetzt") {
                verify { backupPreferences.isAutoBackupEnabled = false }
                viewModel.uiState.value.isAutoBackupEnabled.shouldBeFalse()
            }

            Then("wird der Backup-Job im WorkManager abgebrochen") {
                verify { workManager.cancelUniqueWork(BackupWorker.WORK_NAME) }
            }
        }
    }

    Given("die Änderung der Backup-Uhrzeit") {
        val (context, backupRepository, backupPreferences) = setupMocks()
        val workManager = WorkManager.getInstance(context)

        every { backupPreferences.autoBackupDirectoryUri } returns "content://valid/path"
        every { backupPreferences.isAutoBackupEnabled } returns true

        val viewModel = BackupViewModel(context, backupRepository, backupPreferences)

        When("setBackupTime mit neuer Stunde und Minute aufgerufen wird") {
            viewModel.setBackupTime(hour = 4, minute = 15)

            Then("werden die neuen Werte in BackupPreferences gespeichert") {
                verify { backupPreferences.autoBackupHour = 4 }
                verify { backupPreferences.autoBackupMinute = 15 }
            }

            Then("aktualisiert der State die Backup-Uhrzeit") {
                viewModel.uiState.value.autoBackupHour shouldBe 4
                viewModel.uiState.value.autoBackupMinute shouldBe 15
                viewModel.uiState.value.showTimePicker.shouldBeFalse()
            }

            Then("wird der WorkManager Job mit CANCEL_AND_REENQUEUE neu geplant") {
                verify {
                    workManager.enqueueUniquePeriodicWork(
                        BackupWorker.WORK_NAME,
                        ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                        any()
                    )
                }
            }
        }
    }

    Given("die manuelle Ausführung eines lokalen Backups") {
        val (context, backupRepository, backupPreferences) = setupMocks()

        val mockUri = mockk<Uri>(relaxed = true)
        every { Uri.parse(any()) } returns mockUri

        val mockCreatedFile = mockk<DocumentFile>(relaxed = true)
        val fileUri = mockk<Uri>(relaxed = true)
        every { mockCreatedFile.uri } returns fileUri

        val existingBackup1 = mockk<DocumentFile>(relaxed = true)
        every { existingBackup1.name } returns "flex_backup_20261001_010000.json"
        every { existingBackup1.lastModified() } returns 1000L

        val existingBackup2 = mockk<DocumentFile>(relaxed = true)
        every { existingBackup2.name } returns "flex_backup_20261002_010000.json"
        every { existingBackup2.lastModified() } returns 2000L

        val mockDocDir = mockk<DocumentFile>(relaxed = true)
        every { mockDocDir.createFile("application/json", any()) } returns mockCreatedFile
        every { mockDocDir.listFiles() } returns arrayOf(existingBackup1, existingBackup2)
        every { DocumentFile.fromTreeUri(any(), any()) } returns mockDocDir

        val outputStream = ByteArrayOutputStream()
        every { context.contentResolver.openOutputStream(fileUri) } returns outputStream

        every { backupPreferences.autoBackupDirectoryUri } returns "content://backups/dir"
        every { backupPreferences.maxLocalBackups } returns 5
        coEvery { backupRepository.createBackupJson() } returns "{\"backup\":\"payload\"}"

        val viewModel = BackupViewModel(context, backupRepository, backupPreferences)

        When("runAutoBackupNow() ausgeführt wird") {
            viewModel.runAutoBackupNow()

            Then("wird das Backup-JSON vom Repository erzeugt") {
                coVerify { backupRepository.createBackupJson() }
            }

            Then("wird die Datei über den ContentResolver geschrieben") {
                outputStream.toString(Charsets.UTF_8.name()) shouldBe "{\"backup\":\"payload\"}"
            }

            Then("wird der Zeitstempel des letzten Backups aktualisiert") {
                verify { backupPreferences.lastBackupTimestamp = any() }
            }

            Then("wird der Erfolgsstatus im UI-State gemeldet") {
                eventually(5.seconds) {
                    viewModel.uiState.value.isLoading.shouldBeFalse()
                    viewModel.uiState.value.message shouldBe "Backup erfolgreich erstellt"
                }
            }
        }

        When("beim Erstellen des Backups ein Fehler auftritt") {
            coEvery { backupRepository.createBackupJson() } throws RuntimeException("Datenbank gesperrt")

            viewModel.runAutoBackupNow()

            Then("wird die Fehlermeldung im UI-State angezeigt") {
                eventually(5.seconds) {
                    viewModel.uiState.value.isLoading.shouldBeFalse()
                    viewModel.uiState.value.message shouldBe "Backup fehlgeschlagen: Datenbank gesperrt"
                }
            }
        }
    }

    Given("die Konfiguration der maximalen lokalen Backups") {
        val (context, backupRepository, backupPreferences) = setupMocks()

        val viewModel = BackupViewModel(context, backupRepository, backupPreferences)

        When("setMaxLocalBackups auf 10 gesetzt wird") {
            viewModel.setMaxLocalBackups(10)

            Then("wird der Wert in den Preferences persistiert") {
                verify { backupPreferences.maxLocalBackups = 10 }
            }

            Then("enthält der UI-State die aktualisierte maximale Anzahl") {
                viewModel.uiState.value.maxLocalBackups shouldBe 10
            }
        }
    }
})
