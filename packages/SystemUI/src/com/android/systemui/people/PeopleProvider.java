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

package com.android.systemui.people;

import android.app.people.IPeopleManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;
import android.widget.RemoteViews;

import com.android.systemui.Dependency;
import com.android.systemui.shared.system.PeopleProviderUtils;
import com.android.systemui.statusbar.notification.NotificationEntryManager;

/** API that returns a People Tile preview. */
public class PeopleProvider extends ContentProvider {

    LauncherApps mLauncherApps;
    IPeopleManager mPeopleManager;
    NotificationEntryManager mNotificationEntryManager;

    private static final String TAG = "PeopleProvider";
    private static final boolean DEBUG = PeopleSpaceUtils.DEBUG;
    private static final String EMPTY_STRING = "";

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!doesCallerHavePermission()) {
            String callingPackage = getCallingPackage();
            Log.w(TAG, "API not accessible to calling package: " + callingPackage);
            throw new SecurityException("API not accessible to calling package: " + callingPackage);
        }
        if (!PeopleProviderUtils.GET_PEOPLE_TILE_PREVIEW_METHOD.equals(method)) {
            Log.w(TAG, "Invalid method");
            throw new IllegalArgumentException("Invalid method");
        }

        if (extras == null) {
            Log.w(TAG, "Extras can't be null");
            throw new IllegalArgumentException("Extras can't be null");
        }

        String shortcutId = extras.getString(
                PeopleProviderUtils.EXTRAS_KEY_SHORTCUT_ID, EMPTY_STRING);
        String packageName = extras.getString(
                PeopleProviderUtils.EXTRAS_KEY_PACKAGE_NAME, EMPTY_STRING);
        UserHandle userHandle = extras.getParcelable(
                PeopleProviderUtils.EXTRAS_KEY_USER_HANDLE);
        if (shortcutId.isEmpty()) {
            Log.w(TAG, "Invalid shortcut id");
            throw new IllegalArgumentException("Invalid shortcut id");
        }

        if (packageName.isEmpty()) {
            Log.w(TAG, "Invalid package name");
            throw new IllegalArgumentException("Invalid package name");
        }
        if (userHandle == null) {
            Log.w(TAG, "Null user handle");
            throw new IllegalArgumentException("Null user handle");
        }

        // If services are not set as mocks in tests, fetch them now.
        mPeopleManager = mPeopleManager != null ? mPeopleManager
                : IPeopleManager.Stub.asInterface(
                        ServiceManager.getService(Context.PEOPLE_SERVICE));
        mLauncherApps = mLauncherApps != null ? mLauncherApps
                : getContext().getSystemService(LauncherApps.class);
        mNotificationEntryManager = mNotificationEntryManager != null ? mNotificationEntryManager
                : Dependency.get(NotificationEntryManager.class);

        RemoteViews view = PeopleSpaceUtils.getPreview(getContext(), mPeopleManager, mLauncherApps,
                mNotificationEntryManager, shortcutId, userHandle, packageName);
        if (view == null) {
            if (DEBUG) Log.d(TAG, "No preview available for shortcutId: " + shortcutId);
            return null;
        }
        final Bundle bundle = new Bundle();
        bundle.putParcelable(PeopleProviderUtils.RESPONSE_KEY_REMOTE_VIEWS, view);
        return bundle;
    }

    private boolean doesCallerHavePermission() {
        return getContext().checkPermission(
                PeopleProviderUtils.GET_PEOPLE_TILE_PREVIEW_PERMISSION,
                Binder.getCallingPid(), Binder.getCallingUid())
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        throw new IllegalArgumentException("Invalid method");
    }

    @Override
    public String getType(Uri uri) {
        throw new IllegalArgumentException("Invalid method");
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        throw new IllegalArgumentException("Invalid method");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new IllegalArgumentException("Invalid method");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new IllegalArgumentException("Invalid method");
    }
}

