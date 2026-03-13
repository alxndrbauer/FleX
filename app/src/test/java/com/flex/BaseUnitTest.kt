package com.flex

import org.junit.Before
import org.mockito.MockitoAnnotations

/**
 * Base class for unit tests providing common setup and utilities.
 */
open class BaseUnitTest {
    @Before
    open fun setUp() {
        MockitoAnnotations.openMocks(this)
    }
}
