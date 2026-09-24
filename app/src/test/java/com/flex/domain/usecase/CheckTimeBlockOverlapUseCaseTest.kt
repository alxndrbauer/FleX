package com.flex.domain.usecase

import com.flex.domain.model.TimeBlock
import com.flex.domain.model.WorkLocation
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalTime

class CheckTimeBlockOverlapUseCaseTest {

    private lateinit var useCase: CheckTimeBlockOverlapUseCase

    @BeforeEach
    fun setUp() {
        useCase = CheckTimeBlockOverlapUseCase()
    }

    private fun createBlock(
        id: Long = 1,
        startTime: LocalTime,
        endTime: LocalTime? = null,
        isDuration: Boolean = false
    ): TimeBlock {
        return TimeBlock(
            id = id,
            workDayId = 1,
            startTime = startTime,
            endTime = endTime,
            isDuration = isDuration,
            location = WorkLocation.OFFICE
        )
    }

    // ---------------------------------------------------------------------------
    // Empty & Single Block Cases
    // ---------------------------------------------------------------------------
    @Nested
    @DisplayName("Empty and single block tests")
    inner class EmptyAndSingleBlockTests {

        @Test
        fun `empty list returns false and empty overlaps`() {
            val blocks = emptyList<TimeBlock>()

            val hasOverlap = useCase(blocks)
            val overlaps = useCase.findOverlaps(blocks)

            assertThat(hasOverlap).isFalse()
            assertThat(overlaps).isEmpty()
        }

        @Test
        fun `single completed block returns false`() {
            val blocks = listOf(
                createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            )

            val hasOverlap = useCase(blocks)
            val overlaps = useCase.findOverlaps(blocks)

            assertThat(hasOverlap).isFalse()
            assertThat(overlaps).isEmpty()
        }

        @Test
        fun `single running block returns false`() {
            val blocks = listOf(
                createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = null)
            )

            val hasOverlap = useCase(blocks, now = LocalTime.of(12, 0))
            val overlaps = useCase.findOverlaps(blocks, now = LocalTime.of(12, 0))

            assertThat(hasOverlap).isFalse()
            assertThat(overlaps).isEmpty()
        }
    }

    // ---------------------------------------------------------------------------
    // Back-to-back & Disjoint Blocks (NO overlap)
    // ---------------------------------------------------------------------------
    @Nested
    @DisplayName("Non-overlapping blocks tests")
    inner class NonOverlappingTests {

        @Test
        fun `back-to-back blocks return false`() {
            // 08:00 to 12:00 and 12:00 to 16:00
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(12, 0), endTime = LocalTime.of(16, 0))

            assertThat(useCase(listOf(b1, b2))).isFalse()
            assertThat(useCase.findOverlaps(listOf(b1, b2))).isEmpty()
        }

        @Test
        fun `back-to-back blocks in reversed order return false`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(12, 0), endTime = LocalTime.of(16, 0))

            assertThat(useCase(listOf(b2, b1))).isFalse()
            assertThat(useCase.findOverlaps(listOf(b2, b1))).isEmpty()
        }

        @Test
        fun `multiple consecutive back-to-back blocks return false`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(12, 0))
            val b3 = createBlock(id = 3, startTime = LocalTime.of(12, 0), endTime = LocalTime.of(14, 0))

            assertThat(useCase(listOf(b1, b2, b3))).isFalse()
            assertThat(useCase.findOverlaps(listOf(b1, b2, b3))).isEmpty()
        }

        @Test
        fun `disjoint blocks with gaps return false`() {
            // 08:00 to 12:00 and 13:00 to 17:00
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0))

            assertThat(useCase(listOf(b1, b2))).isFalse()
            assertThat(useCase.findOverlaps(listOf(b1, b2))).isEmpty()
        }

        @Test
        fun `multiple disjoint blocks with gaps return false`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(13, 0))
            val b3 = createBlock(id = 3, startTime = LocalTime.of(14, 0), endTime = LocalTime.of(16, 0))

            assertThat(useCase(listOf(b1, b2, b3))).isFalse()
            assertThat(useCase.findOverlaps(listOf(b1, b2, b3))).isEmpty()
        }
    }

    // ---------------------------------------------------------------------------
    // Overlapping Blocks (Partially, Enclosed, Identical)
    // ---------------------------------------------------------------------------
    @Nested
    @DisplayName("Overlapping blocks tests")
    inner class OverlappingTests {

        @Test
        fun `partially overlapping blocks return true`() {
            // 08:00 to 12:00 and 11:30 to 15:00
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(11, 30), endTime = LocalTime.of(15, 0))

            assertThat(useCase(listOf(b1, b2))).isTrue()
            val overlaps = useCase.findOverlaps(listOf(b1, b2))
            assertThat(overlaps).hasSize(1)
            assertThat(overlaps[0].first).isEqualTo(b1)
            assertThat(overlaps[0].second).isEqualTo(b2)
        }

        @Test
        fun `partially overlapping blocks in reversed list order return true`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(11, 30), endTime = LocalTime.of(15, 0))

            assertThat(useCase(listOf(b2, b1))).isTrue()
            val overlaps = useCase.findOverlaps(listOf(b2, b1))
            assertThat(overlaps).hasSize(1)
            assertThat(overlaps[0].first).isEqualTo(b2)
            assertThat(overlaps[0].second).isEqualTo(b1)
        }

        @Test
        fun `blocks overlapping by one minute return true`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(11, 59), endTime = LocalTime.of(14, 0))

            assertThat(useCase(listOf(b1, b2))).isTrue()
        }

        @Test
        fun `fully enclosed blocks return true`() {
            // 08:00 to 16:00 and 10:00 to 12:00
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(12, 0))

            assertThat(useCase(listOf(b1, b2))).isTrue()
            val overlaps = useCase.findOverlaps(listOf(b1, b2))
            assertThat(overlaps).hasSize(1)
            assertThat(overlaps[0].first).isEqualTo(b1)
            assertThat(overlaps[0].second).isEqualTo(b2)
        }

        @Test
        fun `enclosed block with same start time returns true`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))

            assertThat(useCase(listOf(b1, b2))).isTrue()
        }

        @Test
        fun `enclosed block with same end time returns true`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(12, 0), endTime = LocalTime.of(16, 0))

            assertThat(useCase(listOf(b1, b2))).isTrue()
        }

        @Test
        fun `identical blocks return true`() {
            // 08:00 to 12:00 and 08:00 to 12:00
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))

            assertThat(useCase(listOf(b1, b2))).isTrue()
            val overlaps = useCase.findOverlaps(listOf(b1, b2))
            assertThat(overlaps).hasSize(1)
            assertThat(overlaps[0].first).isEqualTo(b1)
            assertThat(overlaps[0].second).isEqualTo(b2)
        }
    }

    // ---------------------------------------------------------------------------
    // Duration Blocks (isDuration = true)
    // ---------------------------------------------------------------------------
    @Nested
    @DisplayName("Duration blocks tests")
    inner class DurationBlocksTests {

        @Test
        fun `single duration block returns false`() {
            val b1 = createBlock(
                id = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                isDuration = true
            )

            assertThat(useCase(listOf(b1))).isFalse()
            assertThat(useCase.findOverlaps(listOf(b1))).isEmpty()
        }

        @Test
        fun `two duration blocks with identical times return false`() {
            val b1 = createBlock(
                id = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                isDuration = true
            )
            val b2 = createBlock(
                id = 2,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                isDuration = true
            )

            assertThat(useCase(listOf(b1, b2))).isFalse()
            assertThat(useCase.findOverlaps(listOf(b1, b2))).isEmpty()
        }

        @Test
        fun `normal block and duration block with overlapping times return false`() {
            val normal = createBlock(
                id = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(12, 0),
                isDuration = false
            )
            val duration = createBlock(
                id = 2,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(14, 0),
                isDuration = true
            )

            assertThat(useCase(listOf(normal, duration))).isFalse()
            assertThat(useCase.findOverlaps(listOf(normal, duration))).isEmpty()
        }

        @Test
        fun `duration block overlapping with disjoint normal blocks returns false`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(14, 0), endTime = LocalTime.of(16, 0))
            val duration = createBlock(
                id = 3,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(15, 0),
                isDuration = true
            )

            assertThat(useCase(listOf(b1, b2, duration))).isFalse()
            assertThat(useCase.findOverlaps(listOf(b1, b2, duration))).isEmpty()
        }
    }

    // ---------------------------------------------------------------------------
    // Running Blocks (endTime == null evaluated against `now`)
    // ---------------------------------------------------------------------------
    @Nested
    @DisplayName("Running blocks tests")
    inner class RunningBlocksTests {

        @Test
        fun `running block starting at 08-00 with now 12-00 overlaps with 11-00 to 14-00`() {
            // Block 1 starts at 08:00 and is running (now = 12:00), block 2 is 11:00 to 14:00 -> overlaps (true)
            val now = LocalTime.of(12, 0)
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = null)
            val b2 = createBlock(id = 2, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(14, 0))

            assertThat(useCase(listOf(b1, b2), now = now)).isTrue()
            val overlaps = useCase.findOverlaps(listOf(b1, b2), now = now)
            assertThat(overlaps).hasSize(1)
            // Original TimeBlock instances should be preserved
            assertThat(overlaps[0].first).isEqualTo(b1)
            assertThat(overlaps[0].first.endTime).isNull()
            assertThat(overlaps[0].second).isEqualTo(b2)
        }

        @Test
        fun `running block starting at 08-00 with now 10-00 does not overlap with 14-00 to 16-00`() {
            // Block 1 starts at 08:00 and is running (now = 10:00), block 2 is 14:00 to 16:00 -> no overlap (false)
            val now = LocalTime.of(10, 0)
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = null)
            val b2 = createBlock(id = 2, startTime = LocalTime.of(14, 0), endTime = LocalTime.of(16, 0))

            assertThat(useCase(listOf(b1, b2), now = now)).isFalse()
            assertThat(useCase.findOverlaps(listOf(b1, b2), now = now)).isEmpty()
        }

        @Test
        fun `running block starting at 08-00 with now 12-00 is back-to-back with 12-00 to 16-00 and returns false`() {
            val now = LocalTime.of(12, 0)
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = null)
            val b2 = createBlock(id = 2, startTime = LocalTime.of(12, 0), endTime = LocalTime.of(16, 0))

            assertThat(useCase(listOf(b1, b2), now = now)).isFalse()
            assertThat(useCase.findOverlaps(listOf(b1, b2), now = now)).isEmpty()
        }

        @Test
        fun `two running blocks active at the same time return true`() {
            val now = LocalTime.of(10, 0)
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = null)
            val b2 = createBlock(id = 2, startTime = LocalTime.of(9, 0), endTime = null)

            assertThat(useCase(listOf(b1, b2), now = now)).isTrue()
            val overlaps = useCase.findOverlaps(listOf(b1, b2), now = now)
            assertThat(overlaps).hasSize(1)
            assertThat(overlaps[0].first).isEqualTo(b1)
            assertThat(overlaps[0].second).isEqualTo(b2)
        }

        @Test
        fun `running block starting at or after now has non-positive duration and returns false`() {
            val now = LocalTime.of(8, 0)
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = null)
            val b2 = createBlock(id = 2, startTime = LocalTime.of(7, 0), endTime = LocalTime.of(9, 0))

            // At now = 08:00, b1 has 0 elapsed time, so effective interval is empty
            assertThat(useCase(listOf(b1, b2), now = now)).isFalse()
        }
    }

    // ---------------------------------------------------------------------------
    // findOverlaps Tests
    // ---------------------------------------------------------------------------
    @Nested
    @DisplayName("findOverlaps detailed tests")
    inner class FindOverlapsTests {

        @Test
        fun `findOverlaps returns exact OverlappingBlockPair entries for multiple overlaps`() {
            // b1: 08:00 - 11:00
            // b2: 10:00 - 13:00 (overlaps b1 and b3)
            // b3: 12:00 - 15:00 (overlaps b2, but NOT b1)
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(11, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(13, 0))
            val b3 = createBlock(id = 3, startTime = LocalTime.of(12, 0), endTime = LocalTime.of(15, 0))

            val overlaps = useCase.findOverlaps(listOf(b1, b2, b3))

            assertThat(overlaps).hasSize(2)
            assertThat(overlaps).containsExactly(
                OverlappingBlockPair(b1, b2),
                OverlappingBlockPair(b2, b3)
            ).inOrder()
        }

        @Test
        fun `findOverlaps returns all pairwise overlaps when 3 blocks mutually overlap`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(13, 0))
            val b3 = createBlock(id = 3, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(14, 0))

            val overlaps = useCase.findOverlaps(listOf(b1, b2, b3))

            assertThat(overlaps).hasSize(3)
            assertThat(overlaps).containsExactly(
                OverlappingBlockPair(b1, b2),
                OverlappingBlockPair(b1, b3),
                OverlappingBlockPair(b2, b3)
            ).inOrder()
        }
    }

    // ---------------------------------------------------------------------------
    // findOverlap Tests
    // ---------------------------------------------------------------------------
    @Nested
    @DisplayName("findOverlap method tests")
    inner class FindOverlapTests {

        @Test
        fun `findOverlap returns null when existing blocks list is empty`() {
            val result = useCase.findOverlap(
                start = LocalTime.of(8, 0),
                end = LocalTime.of(12, 0),
                existingBlocks = emptyList()
            )

            assertThat(result).isNull()
        }

        @Test
        fun `findOverlap returns null when there is no overlap`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(13, 0), endTime = LocalTime.of(17, 0))

            val result = useCase.findOverlap(
                start = LocalTime.of(12, 0),
                end = LocalTime.of(13, 0),
                existingBlocks = listOf(b1, b2)
            )

            assertThat(result).isNull()
        }

        @Test
        fun `findOverlap returns conflicting block when partially overlapping`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(14, 0), endTime = LocalTime.of(18, 0))

            val result = useCase.findOverlap(
                start = LocalTime.of(11, 0),
                end = LocalTime.of(13, 0),
                existingBlocks = listOf(b1, b2)
            )

            assertThat(result).isEqualTo(b1)
        }

        @Test
        fun `findOverlap returns conflicting block when fully enclosed`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(16, 0))

            val result = useCase.findOverlap(
                start = LocalTime.of(10, 0),
                end = LocalTime.of(12, 0),
                existingBlocks = listOf(b1)
            )

            assertThat(result).isEqualTo(b1)
        }

        @Test
        fun `findOverlap respects excludeBlockId and ignores specified block`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))

            // Editing block 1 with overlapping time: should be excluded
            val result = useCase.findOverlap(
                start = LocalTime.of(8, 0),
                end = LocalTime.of(11, 30),
                existingBlocks = listOf(b1),
                excludeBlockId = 1L
            )

            assertThat(result).isNull()
        }

        @Test
        fun `findOverlap with excludeBlockId returns other conflicting block`() {
            val b1 = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))
            val b2 = createBlock(id = 2, startTime = LocalTime.of(11, 0), endTime = LocalTime.of(15, 0))

            // Exclude b1, but new block also overlaps b2
            val result = useCase.findOverlap(
                start = LocalTime.of(10, 0),
                end = LocalTime.of(12, 0),
                existingBlocks = listOf(b1, b2),
                excludeBlockId = 1L
            )

            assertThat(result).isEqualTo(b2)
        }

        @Test
        fun `findOverlap ignores duration blocks`() {
            val durationBlock = createBlock(
                id = 1,
                startTime = LocalTime.of(8, 0),
                endTime = LocalTime.of(16, 0),
                isDuration = true
            )

            val result = useCase.findOverlap(
                start = LocalTime.of(10, 0),
                end = LocalTime.of(12, 0),
                existingBlocks = listOf(durationBlock)
            )

            assertThat(result).isNull()
        }

        @Test
        fun `findOverlap evaluates existing running block against now`() {
            val now = LocalTime.of(12, 0)
            val runningBlock = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = null)

            // Overlaps with running block (effective end = 12:00)
            val resultOverlap = useCase.findOverlap(
                start = LocalTime.of(11, 0),
                end = LocalTime.of(14, 0),
                existingBlocks = listOf(runningBlock),
                now = now
            )
            assertThat(resultOverlap).isEqualTo(runningBlock)
            // Preserves original runningBlock attributes
            assertThat(resultOverlap?.endTime).isNull()

            // Does not overlap (after now)
            val resultNoOverlap = useCase.findOverlap(
                start = LocalTime.of(13, 0),
                end = LocalTime.of(15, 0),
                existingBlocks = listOf(runningBlock),
                now = now
            )
            assertThat(resultNoOverlap).isNull()
        }

        @Test
        fun `findOverlap evaluates new running block (end == null) against now`() {
            val now = LocalTime.of(12, 0)
            val existing = createBlock(id = 1, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(11, 0))

            // New running block starts at 08:00, end = null, now = 12:00 -> effective [08:00, 12:00)
            val result = useCase.findOverlap(
                start = LocalTime.of(8, 0),
                end = null,
                existingBlocks = listOf(existing),
                now = now
            )

            assertThat(result).isEqualTo(existing)
        }

        @Test
        fun `findOverlap returns null when start is greater than or equal to end`() {
            val existing = createBlock(id = 1, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(12, 0))

            // Start == End
            val zeroDuration = useCase.findOverlap(
                start = LocalTime.of(10, 0),
                end = LocalTime.of(10, 0),
                existingBlocks = listOf(existing)
            )
            assertThat(zeroDuration).isNull()

            // Start > End
            val invertedTimes = useCase.findOverlap(
                start = LocalTime.of(14, 0),
                end = LocalTime.of(10, 0),
                existingBlocks = listOf(existing)
            )
            assertThat(invertedTimes).isNull()

            // Start >= now when end is null
            val now = LocalTime.of(10, 0)
            val startAfterNow = useCase.findOverlap(
                start = LocalTime.of(11, 0),
                end = null,
                existingBlocks = listOf(existing),
                now = now
            )
            assertThat(startAfterNow).isNull()
        }
    }
}
