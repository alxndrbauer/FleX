package com.flex

import org.junit.jupiter.api.BeforeEach
import org.mockito.MockitoAnnotations

/**
 * Base class for unit tests providing common setup and utilities.
 */
open class BaseUnitTest {
    @BeforeEach
    open fun setUp() {
        MockitoAnnotations.openMocks(this)
    }
}
