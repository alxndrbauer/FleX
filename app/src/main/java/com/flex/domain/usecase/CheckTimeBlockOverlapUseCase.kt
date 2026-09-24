package com.flex.domain.usecase

import com.flex.domain.model.TimeBlock
import java.time.LocalTime
import javax.inject.Inject

data class OverlappingBlockPair(
    val first: TimeBlock,
    val second: TimeBlock
)

class CheckTimeBlockOverlapUseCase @Inject constructor() {

    operator fun invoke(blocks: List<TimeBlock>, now: LocalTime = LocalTime.now()): Boolean {
        return findOverlaps(blocks, now).isNotEmpty()
    }

    fun findOverlaps(blocks: List<TimeBlock>, now: LocalTime = LocalTime.now()): List<OverlappingBlockPair> {
        val regularBlocks = blocks
            .filter { !it.isDuration }
            .filter { (it.endTime ?: now) > it.startTime }

        val overlaps = mutableListOf<OverlappingBlockPair>()
        for (i in 0 until regularBlocks.size) {
            for (j in i + 1 until regularBlocks.size) {
                val b1 = regularBlocks[i]
                val b2 = regularBlocks[j]
                val s1 = b1.startTime
                val e1 = b1.endTime ?: now
                val s2 = b2.startTime
                val e2 = b2.endTime ?: now

                if (s1 < e2 && s2 < e1) {
                    overlaps.add(OverlappingBlockPair(b1, b2))
                }
            }
        }
        return overlaps
    }

    fun findOverlap(
        start: LocalTime,
        end: LocalTime?,
        existingBlocks: List<TimeBlock>,
        excludeBlockId: Long? = null,
        now: LocalTime = LocalTime.now()
    ): TimeBlock? {
        val effectiveEnd = end ?: now
        if (start >= effectiveEnd) return null

        val candidates = existingBlocks
            .filter { !it.isDuration && (excludeBlockId == null || it.id != excludeBlockId) }
            .filter { (it.endTime ?: now) > it.startTime }

        return candidates.firstOrNull { existing ->
            val existingEnd = existing.endTime ?: now
            start < existingEnd && existing.startTime < effectiveEnd
        }
    }
}
