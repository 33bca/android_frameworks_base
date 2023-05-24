/*
 * Copyright (C) 2022 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.util;

import android.content.Context;
import android.os.HapticPlayer;
import android.os.RichTapVibrationEffect;
import android.util.Slog;

import com.android.internal.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class RichTapVibrationUtils {

    private static final String TAG = "RichTapVibrationUtils";

    private HapticPlayer mHapticPlayer;
    private Boolean mSupportRichTap;

    public RichTapVibrationUtils() {
        mHapticPlayer = new HapticPlayer();
        mSupportRichTap = mHapticPlayer.isAvailable();
    }

    public boolean playVerityVibrate(String heFile) {
        if (!mSupportRichTap) {
            return false;
        }
        File file = new File(heFile);
        if (!file.exists()) {
            return false;
        }
        StringBuilder sb = new StringBuilder();
        try {
            String line;
            BufferedReader br = new BufferedReader(new FileReader(file));
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            br.close();
        }
        catch (IOException e) {
            return false;
        }
        mHapticPlayer.applyPatternHeWithString(sb.toString(), 1, 0, 255, 0);
        return true;
    }

    public boolean isSupported() {
        return RichTapVibrationEffect.isSupported();
    }
}
