/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar

import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import androidx.test.filters.SmallTest
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.systemui.SysuiTestCase
import com.android.systemui.dump.DumpManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.Mockito.`when` as whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper
class StatusBarStateControllerImplTest : SysuiTestCase() {

    @Mock lateinit var interactionJankMonitor: InteractionJankMonitor

    private lateinit var controller: StatusBarStateControllerImpl
    private lateinit var uiEventLogger: UiEventLoggerFake

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        whenever(interactionJankMonitor.begin(any(), anyInt())).thenReturn(true)
        whenever(interactionJankMonitor.end(anyInt())).thenReturn(true)

        uiEventLogger = UiEventLoggerFake()
        controller = StatusBarStateControllerImpl(
            uiEventLogger,
            mock(DumpManager::class.java),
            interactionJankMonitor
        )
    }

    @Test
    fun testChangeState_logged() {
        TestableLooper.get(this).runWithLooper {
            controller.state = StatusBarState.KEYGUARD
            controller.state = StatusBarState.SHADE
            controller.state = StatusBarState.SHADE_LOCKED
        }

        val logs = uiEventLogger.logs
        assertEquals(3, logs.size)
        val ids = logs.map(UiEventLoggerFake.FakeUiEvent::eventId)
        assertEquals(StatusBarStateEvent.STATUS_BAR_STATE_KEYGUARD.id, ids[0])
        assertEquals(StatusBarStateEvent.STATUS_BAR_STATE_SHADE.id, ids[1])
        assertEquals(StatusBarStateEvent.STATUS_BAR_STATE_SHADE_LOCKED.id, ids[2])
    }
}
