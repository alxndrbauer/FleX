package com.flex.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flex.R
import com.flex.ui.month.ExportFormat
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExportNotificationHelperTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var notificationManager: NotificationManager

    @Mock
    private lateinit var contentResolver: ContentResolver

    @Mock
    private lateinit var notificationManagerCompat: NotificationManagerCompat

    private var channelMockConstruction: MockedConstruction<NotificationChannel>? = null
    private var notifCompatMockedStatic: MockedStatic<NotificationManagerCompat>? = null
    private var pendingIntentMockedStatic: MockedStatic<PendingIntent>? = null

    private val mockNotification: Notification = mock()
    private val mockPendingIntent: PendingIntent = mock()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(context.getSystemService(NotificationManager::class.java)).thenReturn(notificationManager)
        whenever(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager)
        whenever(context.contentResolver).thenReturn(contentResolver)

        // Mock NotificationChannel constructor so its getters return passed values
        channelMockConstruction = Mockito.mockConstruction(NotificationChannel::class.java) { mockChannel, channelContext ->
            val id = channelContext.arguments()[0] as? String
            val name = channelContext.arguments()[1] as? CharSequence
            val importance = channelContext.arguments()[2] as? Int ?: 0
            whenever(mockChannel.id).thenReturn(id)
            whenever(mockChannel.name).thenReturn(name)
            whenever(mockChannel.importance).thenReturn(importance)
        }

        // Mock NotificationManagerCompat.from(context)
        notifCompatMockedStatic = Mockito.mockStatic(NotificationManagerCompat::class.java)
        notifCompatMockedStatic?.`when`<NotificationManagerCompat> {
            NotificationManagerCompat.from(any())
        }?.thenReturn(notificationManagerCompat)

        // Mock PendingIntent.getActivity
        pendingIntentMockedStatic = Mockito.mockStatic(PendingIntent::class.java)
        pendingIntentMockedStatic?.`when`<PendingIntent> {
            PendingIntent.getActivity(any(), any(), anyOrNull(), any())
        }?.thenReturn(mockPendingIntent)
    }

    @AfterEach
    fun tearDown() {
        channelMockConstruction?.close()
        notifCompatMockedStatic?.close()
        pendingIntentMockedStatic?.close()
    }

    @Nested
    @DisplayName("Notification Channel Registration")
    inner class ChannelRegistrationTests {

        @Test
        fun `notification channel is registered on NotificationManager with ID export_channel`() {
            val helper = ExportNotificationHelper(context)

            val captor = argumentCaptor<NotificationChannel>()
            verify(notificationManager).createNotificationChannel(captor.capture())

            val channel = captor.firstValue
            assertThat(channel.id).isEqualTo(ExportNotificationHelper.CHANNEL_ID)
            assertThat(channel.id).isEqualTo("export_channel")
            assertThat(channel.name).isEqualTo("Exporte & Downloads")
            assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
            verify(channel).description = "Benachrichtigungen bei abgeschlossenen Exporten"
        }

        @Test
        fun `init handles null NotificationManager gracefully`() {
            whenever(context.getSystemService(NotificationManager::class.java)).thenReturn(null)

            val helper = ExportNotificationHelper(context)

            assertThat(helper).isNotNull()
            verify(notificationManager, never()).createNotificationChannel(any())
        }
    }

    @Nested
    @DisplayName("MIME Type Verification and Explicit Intents")
    inner class MimeTypeTests {

        @Test
        fun `showExportNotification with ExportFormat PDF uses application-pdf mime type and explicit intents`() {
            val capturedIntentContexts = mutableListOf<MockedConstruction.Context>()
            val builderConstruction = Mockito.mockConstruction(
                NotificationCompat.Builder::class.java,
                Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ) { mock, _ ->
                whenever(mock.build()).thenReturn(mockNotification)
            }
            val intentConstruction = Mockito.mockConstruction(Intent::class.java) { _, context ->
                capturedIntentContexts.add(context)
            }

            try {
                val helper = ExportNotificationHelper(context)
                val uri = mock<Uri>()
                helper.showExportNotification(uri, ExportFormat.PDF)

                val intents = intentConstruction.constructed()
                assertThat(intents).hasSize(2)

                // First intent: viewIntent directed to NotificationActionActivity
                assertThat(capturedIntentContexts[0].arguments()[0]).isSameInstanceAs(context)
                assertThat(capturedIntentContexts[0].arguments()[1]).isEqualTo(NotificationActionActivity::class.java)
                val viewIntent = intents[0]
                verify(viewIntent).action = NotificationActionActivity.ACTION_VIEW_EXPORT
                verify(viewIntent).setDataAndType(uri, "application/pdf")
                verify(viewIntent).flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                // Second intent: shareIntent directed to NotificationActionActivity
                assertThat(capturedIntentContexts[1].arguments()[0]).isSameInstanceAs(context)
                assertThat(capturedIntentContexts[1].arguments()[1]).isEqualTo(NotificationActionActivity::class.java)
                val shareIntent = intents[1]
                verify(shareIntent).action = NotificationActionActivity.ACTION_SHARE_EXPORT
                verify(shareIntent).setDataAndType(uri, "application/pdf")
                verify(shareIntent).flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            } finally {
                intentConstruction.close()
                builderConstruction.close()
            }
        }

        @Test
        fun `showExportNotification with ExportFormat CSV uses text-csv mime type and explicit intents`() {
            val capturedIntentContexts = mutableListOf<MockedConstruction.Context>()
            val builderConstruction = Mockito.mockConstruction(
                NotificationCompat.Builder::class.java,
                Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ) { mock, _ ->
                whenever(mock.build()).thenReturn(mockNotification)
            }
            val intentConstruction = Mockito.mockConstruction(Intent::class.java) { _, context ->
                capturedIntentContexts.add(context)
            }

            try {
                val helper = ExportNotificationHelper(context)
                val uri = mock<Uri>()
                helper.showExportNotification(uri, ExportFormat.CSV)

                val intents = intentConstruction.constructed()
                assertThat(intents).hasSize(2)

                // First intent: viewIntent directed to NotificationActionActivity
                assertThat(capturedIntentContexts[0].arguments()[0]).isSameInstanceAs(context)
                assertThat(capturedIntentContexts[0].arguments()[1]).isEqualTo(NotificationActionActivity::class.java)
                val viewIntent = intents[0]
                verify(viewIntent).action = NotificationActionActivity.ACTION_VIEW_EXPORT
                verify(viewIntent).setDataAndType(uri, "text/csv")
                verify(viewIntent).flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                // Second intent: shareIntent directed to NotificationActionActivity
                assertThat(capturedIntentContexts[1].arguments()[0]).isSameInstanceAs(context)
                assertThat(capturedIntentContexts[1].arguments()[1]).isEqualTo(NotificationActionActivity::class.java)
                val shareIntent = intents[1]
                verify(shareIntent).action = NotificationActionActivity.ACTION_SHARE_EXPORT
                verify(shareIntent).setDataAndType(uri, "text/csv")
                verify(shareIntent).flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            } finally {
                intentConstruction.close()
                builderConstruction.close()
            }
        }
    }

    @Nested
    @DisplayName("getFileName Resolution and Fallbacks")
    inner class FileNameResolutionTests {

        @Test
        fun `getFileName correctly queries OpenableColumns DISPLAY_NAME and returns filename`() {
            val helper = ExportNotificationHelper(context)
            val uri = mock<Uri>()
            val cursor = mock<Cursor>()

            whenever(contentResolver.query(eq(uri), eq(arrayOf(OpenableColumns.DISPLAY_NAME)), isNull(), isNull(), isNull()))
                .thenReturn(cursor)
            whenever(cursor.moveToFirst()).thenReturn(true)
            whenever(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(0)
            whenever(cursor.getString(0)).thenReturn("Zeiterfassung_2026.pdf")

            val result = helper.getFileName(uri)

            assertThat(result).isEqualTo("Zeiterfassung_2026.pdf")
            verify(cursor).close()
        }

        @Test
        fun `getFileName returns null when cursor moveToFirst returns false`() {
            val helper = ExportNotificationHelper(context)
            val uri = mock<Uri>()
            val cursor = mock<Cursor>()

            whenever(contentResolver.query(eq(uri), eq(arrayOf(OpenableColumns.DISPLAY_NAME)), isNull(), isNull(), isNull()))
                .thenReturn(cursor)
            whenever(cursor.moveToFirst()).thenReturn(false)

            val result = helper.getFileName(uri)

            assertThat(result).isNull()
            verify(cursor).close()
        }

        @Test
        fun `getFileName returns null when DISPLAY_NAME column index is -1`() {
            val helper = ExportNotificationHelper(context)
            val uri = mock<Uri>()
            val cursor = mock<Cursor>()

            whenever(contentResolver.query(eq(uri), eq(arrayOf(OpenableColumns.DISPLAY_NAME)), isNull(), isNull(), isNull()))
                .thenReturn(cursor)
            whenever(cursor.moveToFirst()).thenReturn(true)
            whenever(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(-1)

            val result = helper.getFileName(uri)

            assertThat(result).isNull()
            verify(cursor).close()
        }

        @Test
        fun `getFileName returns null when contentResolver returns null`() {
            val helper = ExportNotificationHelper(context)
            val uri = mock<Uri>()

            whenever(contentResolver.query(eq(uri), eq(arrayOf(OpenableColumns.DISPLAY_NAME)), isNull(), isNull(), isNull()))
                .thenReturn(null)

            val result = helper.getFileName(uri)

            assertThat(result).isNull()
        }

        @Test
        fun `getFileName returns null when contentResolver query throws exception`() {
            val helper = ExportNotificationHelper(context)
            val uri = mock<Uri>()

            whenever(contentResolver.query(eq(uri), eq(arrayOf(OpenableColumns.DISPLAY_NAME)), isNull(), isNull(), isNull()))
                .thenThrow(SecurityException("Permission denied"))

            val result = helper.getFileName(uri)

            assertThat(result).isNull()
        }

        @Test
        fun `showExportNotification with custom filename sets correct title`() {
            val builderConstruction = Mockito.mockConstruction(
                NotificationCompat.Builder::class.java,
                Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ) { mock, _ ->
                whenever(mock.build()).thenReturn(mockNotification)
            }

            try {
                val helper = ExportNotificationHelper(context)
                val uri = mock<Uri>()
                val cursor = mock<Cursor>()

                whenever(contentResolver.query(eq(uri), eq(arrayOf(OpenableColumns.DISPLAY_NAME)), isNull(), isNull(), isNull()))
                    .thenReturn(cursor)
                whenever(cursor.moveToFirst()).thenReturn(true)
                whenever(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)).thenReturn(0)
                whenever(cursor.getString(0)).thenReturn("Mein_Export.csv")

                helper.showExportNotification(uri, ExportFormat.CSV)

                val builder = builderConstruction.constructed().first()
                verify(builder).setContentTitle("Mein_Export.csv gespeichert")
            } finally {
                builderConstruction.close()
            }
        }

        @Test
        fun `showExportNotification falls back to Export pdf for PDF when filename is null`() {
            val builderConstruction = Mockito.mockConstruction(
                NotificationCompat.Builder::class.java,
                Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ) { mock, _ ->
                whenever(mock.build()).thenReturn(mockNotification)
            }

            try {
                val helper = ExportNotificationHelper(context)
                val uri = mock<Uri>()

                whenever(contentResolver.query(eq(uri), any(), isNull(), isNull(), isNull()))
                    .thenReturn(null)

                helper.showExportNotification(uri, ExportFormat.PDF)

                val builder = builderConstruction.constructed().first()
                verify(builder).setContentTitle("Export.pdf gespeichert")
            } finally {
                builderConstruction.close()
            }
        }

        @Test
        fun `showExportNotification falls back to Export csv for CSV when filename is null`() {
            val builderConstruction = Mockito.mockConstruction(
                NotificationCompat.Builder::class.java,
                Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ) { mock, _ ->
                whenever(mock.build()).thenReturn(mockNotification)
            }

            try {
                val helper = ExportNotificationHelper(context)
                val uri = mock<Uri>()

                whenever(contentResolver.query(eq(uri), any(), isNull(), isNull(), isNull()))
                    .thenReturn(null)

                helper.showExportNotification(uri, ExportFormat.CSV)

                val builder = builderConstruction.constructed().first()
                verify(builder).setContentTitle("Export.csv gespeichert")
            } finally {
                builderConstruction.close()
            }
        }
    }

    @Nested
    @DisplayName("Notification Building and notify()")
    inner class NotificationBuildingTests {

        @Test
        fun `showExportNotification builds notification with correct title, actions, channel, and properties`() {
            var capturedBuilderContext: MockedConstruction.Context? = null
            val builderConstruction = Mockito.mockConstruction(
                NotificationCompat.Builder::class.java,
                Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ) { mock, context ->
                capturedBuilderContext = context
                whenever(mock.build()).thenReturn(mockNotification)
            }

            try {
                val helper = ExportNotificationHelper(context)
                val uri = mock<Uri>()

                whenever(contentResolver.query(eq(uri), any(), isNull(), isNull(), isNull()))
                    .thenReturn(null)

                helper.showExportNotification(uri, ExportFormat.PDF)

                assertThat(builderConstruction.constructed()).hasSize(1)
                val builder = builderConstruction.constructed().first()

                // Verify channel ID and context passed to builder
                assertThat(capturedBuilderContext?.arguments()?.get(0)).isSameInstanceAs(context)
                assertThat(capturedBuilderContext?.arguments()?.get(1)).isEqualTo("export_channel")

                // Verify notification attributes
                verify(builder).setSmallIcon(R.drawable.ic_notification)
                verify(builder).setContentTitle("Export.pdf gespeichert")
                verify(builder).setContentText("Tippen zum Öffnen")
                verify(builder).setContentIntent(mockPendingIntent)
                verify(builder).addAction(0, "Öffnen", mockPendingIntent)
                verify(builder).addAction(0, "Teilen", mockPendingIntent)
                verify(builder).setAutoCancel(true)
                verify(builder).setPriority(NotificationCompat.PRIORITY_DEFAULT)
                verify(builder).build()

                // Verify notify() called with generated notifId and built notification
                val notifIdCaptor = argumentCaptor<Int>()
                verify(notificationManagerCompat).notify(notifIdCaptor.capture(), eq(mockNotification))
                val notifId = notifIdCaptor.firstValue
                assertThat(notifId).isAtLeast(3000)
                assertThat(notifId).isLessThan(4000)
            } finally {
                builderConstruction.close()
            }
        }

        @Test
        fun `showExportNotification catches SecurityException when notify throws`() {
            val builderConstruction = Mockito.mockConstruction(
                NotificationCompat.Builder::class.java,
                Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ) { mock, _ ->
                whenever(mock.build()).thenReturn(mockNotification)
            }

            try {
                val helper = ExportNotificationHelper(context)
                val uri = mock<Uri>()

                whenever(notificationManagerCompat.notify(any(), any()))
                    .thenThrow(SecurityException("POST_NOTIFICATIONS permission not granted"))

                // Should not throw SecurityException
                helper.showExportNotification(uri, ExportFormat.PDF)

                verify(notificationManagerCompat).notify(any(), eq(mockNotification))
            } finally {
                builderConstruction.close()
            }
        }

        @Test
        fun `PendingIntent is created with correct request codes and immutable flags`() {
            val builderConstruction = Mockito.mockConstruction(
                NotificationCompat.Builder::class.java,
                Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF)
            ) { mock, _ ->
                whenever(mock.build()).thenReturn(mockNotification)
            }

            try {
                val helper = ExportNotificationHelper(context)
                val uri = mock<Uri>()

                helper.showExportNotification(uri, ExportFormat.PDF)

                val notifIdCaptor = argumentCaptor<Int>()
                verify(notificationManagerCompat).notify(notifIdCaptor.capture(), eq(mockNotification))
                val notifId = notifIdCaptor.firstValue

                // Verify PendingIntent.getActivity calls for viewIntent and shareIntent
                pendingIntentMockedStatic?.verify {
                    PendingIntent.getActivity(
                        eq(context),
                        eq(notifId),
                        anyOrNull(),
                        eq(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )
                }
                pendingIntentMockedStatic?.verify {
                    PendingIntent.getActivity(
                        eq(context),
                        eq(notifId + 10000),
                        anyOrNull(),
                        eq(PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    )
                }
            } finally {
                builderConstruction.close()
            }
        }
    }
}
