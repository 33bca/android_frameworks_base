/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.gallery.preference

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.tooling.preview.Preview
import com.android.settingslib.spa.framework.common.SettingsEntry
import com.android.settingslib.spa.framework.common.SettingsEntryBuilder
import com.android.settingslib.spa.framework.common.SettingsPage
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.compose.navigator
import com.android.settingslib.spa.framework.compose.stateOf
import com.android.settingslib.spa.framework.theme.SettingsTheme
import com.android.settingslib.spa.widget.preference.MainSwitchPreference
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.scaffold.RegularScaffold

private const val TITLE = "Sample MainSwitchPreference"

object MainSwitchPreferencePageProvider : SettingsPageProvider {
    override val name = "MainSwitchPreference"

    override fun buildEntry(arguments: Bundle?): List<SettingsEntry> {
        val owner = SettingsPage.create(name)
        val entryList = mutableListOf<SettingsEntry>()
        entryList.add(
            SettingsEntryBuilder.create( "MainSwitchPreference", owner)
                .setIsAllowSearch(true)
                .setUiLayoutFn {
                    SampleMainSwitchPreference()
                }.build()
        )
        entryList.add(
            SettingsEntryBuilder.create( "MainSwitchPreference not changeable", owner)
                .setIsAllowSearch(true)
                .setUiLayoutFn {
                    SampleNotChangeableMainSwitchPreference()
                }.build()
        )

        return entryList
    }

    fun buildInjectEntry(): SettingsEntryBuilder {
        return SettingsEntryBuilder.createInject(owner = SettingsPage.create(name))
            .setIsAllowSearch(true)
            .setUiLayoutFn {
                Preference(object : PreferenceModel {
                    override val title = TITLE
                    override val onClick = navigator(name)
                })
            }
    }

    @Composable
    override fun Page(arguments: Bundle?) {
        RegularScaffold(title = TITLE) {
            for (entry in buildEntry(arguments)) {
                entry.UiLayout()
            }
        }
    }
}

@Composable
private fun SampleMainSwitchPreference() {
    val checked = rememberSaveable { mutableStateOf(false) }
    MainSwitchPreference(remember {
        object : SwitchPreferenceModel {
            override val title = "MainSwitchPreference"
            override val checked = checked
            override val onCheckedChange = { newChecked: Boolean -> checked.value = newChecked }
        }
    })
}

@Composable
private fun SampleNotChangeableMainSwitchPreference() {
    val checked = rememberSaveable { mutableStateOf(true) }
    MainSwitchPreference(remember {
        object : SwitchPreferenceModel {
            override val title = "Not changeable"
            override val changeable = stateOf(false)
            override val checked = checked
            override val onCheckedChange = { newChecked: Boolean -> checked.value = newChecked }
        }
    })
}

@Preview(showBackground = true)
@Composable
private fun MainSwitchPreferencePagePreview() {
    SettingsTheme {
        MainSwitchPreferencePageProvider.Page(null)
    }
}
