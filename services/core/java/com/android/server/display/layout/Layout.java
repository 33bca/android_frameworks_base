/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.display.layout;

import static android.view.Display.DEFAULT_DISPLAY;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Slog;
import android.view.DisplayAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds a collection of {@link Display}s. A single instance of this class describes
 * how to organize one or more DisplayDevices into LogicalDisplays for a particular device
 * state. For example, there may be one instance of this class to describe display layout when
 * a foldable device is folded, and a second instance for when the device is unfolded.
 */
public class Layout {
    private static final String TAG = "Layout";
    private static int sNextNonDefaultDisplayId = DEFAULT_DISPLAY + 1;

    private final List<Display> mDisplays = new ArrayList<>(2);

    /**
     *  @return The default display ID, or a new unique one to use.
     */
    public static int assignDisplayIdLocked(boolean isDefault) {
        return isDefault ? DEFAULT_DISPLAY : sNextNonDefaultDisplayId++;
    }

    public static int assignDisplayIdLocked(boolean isDefault, DisplayAddress address) {
        boolean isDisplayBuiltIn = false;
        if (address instanceof DisplayAddress.Physical) {
          isDisplayBuiltIn =
                   (((DisplayAddress.Physical) address).getPort() < 0);
        }
        if (!isDefault && isDisplayBuiltIn) {
            return sNextNonDefaultDisplayId++;
        }

        return assignDisplayIdLocked(isDefault);
    }


    @Override
    public String toString() {
        return mDisplays.toString();
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof  Layout)) {
            return false;
        }

        Layout otherLayout = (Layout) obj;
        return this.mDisplays.equals(otherLayout.mDisplays);
    }

    @Override
    public int hashCode() {
        return mDisplays.hashCode();
    }

    /**
     * Creates a simple 1:1 LogicalDisplay mapping for the specified DisplayDevice.
     *
     * @param address Address of the device.
     * @param isDefault Indicates if the device is meant to be the default display.
     * @param isEnabled Indicates if this display is usable and can be switched on
     * @return The new layout.
     */
    public Display createDisplayLocked(
            @NonNull DisplayAddress address, boolean isDefault, boolean isEnabled,
            DisplayIdProducer idProducer) {
        if (contains(address)) {
            Slog.w(TAG, "Attempting to add second definition for display-device: " + address);
            return null;
        }

        // See if we're dealing with the "default" display
        if (isDefault && getById(DEFAULT_DISPLAY) != null) {
            Slog.w(TAG, "Ignoring attempt to add a second default display: " + address);
            return null;
        }

        // Assign a logical display ID and create the new display.
        // Note that the logical display ID is saved into the layout, so when switching between
        // different layouts, a logical display can be destroyed and later recreated with the
        // same logical display ID.
        final int logicalDisplayId = idProducer.getId(isDefault);
        final Display display = new Display(address, logicalDisplayId, isEnabled);

        mDisplays.add(display);
        return display;
    }

    /**
     * @param id The ID of the display to remove.
     */
    public void removeDisplayLocked(int id) {
        Display display = getById(id);
        if (display != null) {
            mDisplays.remove(display);
        }
    }

    /**
     * @param address The address to check.
     *
     * @return True if the specified address is used in this layout.
     */
    public boolean contains(@NonNull DisplayAddress address) {
        final int size = mDisplays.size();
        for (int i = 0; i < size; i++) {
            if (address.equals(mDisplays.get(i).getAddress())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param id The display ID to check.
     *
     * @return The display corresponding to the specified display ID.
     */
    @Nullable
    public Display getById(int id) {
        for (int i = 0; i < mDisplays.size(); i++) {
            Display display = mDisplays.get(i);
            if (id == display.getLogicalDisplayId()) {
                return display;
            }
        }
        return null;
    }

    /**
     * @param address The display address to check.
     *
     * @return The display corresponding to the specified address.
     */
    @Nullable
    public Display getByAddress(@NonNull DisplayAddress address) {
        for (int i = 0; i < mDisplays.size(); i++) {
            Display display = mDisplays.get(i);
            if (address.equals(display.getAddress())) {
                return display;
            }
        }
        return null;
    }

    /**
     * @param index The index of the display to return.
     *
     * @return the display at the specified index.
     */
    public Display getAt(int index) {
        return mDisplays.get(index);
    }

    /**
     * @return The number of displays defined for this layout.
     */
    public int size() {
        return mDisplays.size();
    }

    /**
     * Describes how a {@link LogicalDisplay} is built from {@link DisplayDevice}s.
     */
    public static class Display {
        public static final int POSITION_UNKNOWN = -1;
        public static final int POSITION_FRONT = 0;
        public static final int POSITION_REAR = 1;

        // Address of the display device to map to this display.
        private final DisplayAddress mAddress;

        // Logical Display ID to apply to this display.
        private final int mLogicalDisplayId;

        // Indicates if this display is usable and can be switched on
        private final boolean mIsEnabled;

        // The direction the display faces
        // {@link DeviceStateToLayoutMap.POSITION_FRONT} or
        // {@link DeviceStateToLayoutMap.POSITION_REAR}.
        // {@link DeviceStateToLayoutMap.POSITION_UNKNOWN} is unspecified.
        private int mPosition;

        Display(@NonNull DisplayAddress address, int logicalDisplayId, boolean isEnabled) {
            mAddress = address;
            mLogicalDisplayId = logicalDisplayId;
            mIsEnabled = isEnabled;
            mPosition = POSITION_UNKNOWN;
        }

        @Override
        public String toString() {
            return "{"
                    + "dispId: " + mLogicalDisplayId
                    + "(" + (mIsEnabled ? "ON" : "OFF") + ")"
                    + ", addr: " + mAddress
                    +  ((mPosition == POSITION_UNKNOWN) ? "" : ", position: " + mPosition)
                    + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Display)) {
                return false;
            }

            Display otherDisplay = (Display) obj;

            return otherDisplay.mIsEnabled == this.mIsEnabled
                    && otherDisplay.mPosition == this.mPosition
                    && otherDisplay.mLogicalDisplayId == this.mLogicalDisplayId
                    && this.mAddress.equals(otherDisplay.mAddress);
        }

        @Override
        public int hashCode() {
            int result = 1;
            result = 31 * result + Boolean.hashCode(mIsEnabled);
            result = 31 * result + mPosition;
            result = 31 * result + mLogicalDisplayId;
            result = 31 * result + mAddress.hashCode();
            return result;
        }

        public DisplayAddress getAddress() {
            return mAddress;
        }

        public int getLogicalDisplayId() {
            return mLogicalDisplayId;
        }

        public boolean isEnabled() {
            return mIsEnabled;
        }

        public void setPosition(int position) {
            mPosition = position;
        }
    }
}
