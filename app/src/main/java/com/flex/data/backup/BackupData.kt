package com.flex.data.backup

import com.flex.data.local.entity.QuotaRuleEntity
import com.flex.data.local.entity.SettingsEntity
import com.flex.data.local.entity.TimeBlockEntity
import com.flex.data.local.entity.WorkDayEntity
import com.flex.data.local.entity.WorkTimeRuleEntity

data class BackupFile(
    val version: Int = 1,
    val createdAt: String,
    val data: BackupData
)

data class BackupData(
    val workDays: List<WorkDayEntity>,
    val timeBlocks: List<TimeBlockEntity> = emptyList(),
    val settings: SettingsEntity?,
    val quotaRules: List<QuotaRuleEntity> = emptyList(),
    val workTimeRules: List<WorkTimeRuleEntity> = emptyList()
)

enum class ImportMode {
    REPLACE,
    MERGE
}
