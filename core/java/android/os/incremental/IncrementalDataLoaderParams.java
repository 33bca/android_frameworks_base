/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.os.incremental;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.ParcelFileDescriptor;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class represents the parameters used to configure an Incremental Data Loader.
 * Hide for now.
 * @hide
 */
public class IncrementalDataLoaderParams {
    @NonNull private final IncrementalDataLoaderParamsParcel mData;

    public IncrementalDataLoaderParams(@NonNull String url, @NonNull String packageName,
            @Nullable Map<String, ParcelFileDescriptor> namedFds) {
        IncrementalDataLoaderParamsParcel data = new IncrementalDataLoaderParamsParcel();
        data.staticArgs = url;
        data.packageName = packageName;
        if (namedFds == null || namedFds.isEmpty()) {
            data.dynamicArgs = new NamedParcelFileDescriptor[0];
        } else {
            data.dynamicArgs = new NamedParcelFileDescriptor[namedFds.size()];
            int i = 0;
            for (Map.Entry<String, ParcelFileDescriptor> namedFd : namedFds.entrySet()) {
                data.dynamicArgs[i] = new NamedParcelFileDescriptor();
                data.dynamicArgs[i].name = namedFd.getKey();
                data.dynamicArgs[i].fd = namedFd.getValue();
                i += 1;
            }
        }
        mData = data;
    }

    public IncrementalDataLoaderParams(@NonNull IncrementalDataLoaderParamsParcel data) {
        mData = data;
    }

    /**
     * @return static server's URL
     */
    public final @NonNull String getStaticArgs() {
        return mData.staticArgs;
    }

    /**
     * @return data loader's package name
     */
    public final @NonNull String getPackageName() {
        return mData.packageName;
    }

    public final @NonNull IncrementalDataLoaderParamsParcel getData() {
        return mData;
    }

    /**
     * @return data loader's dynamic arguments such as file descriptors
     */
    public final @NonNull Map<String, ParcelFileDescriptor> getDynamicArgs() {
        return Arrays.stream(mData.dynamicArgs).collect(
            Collectors.toMap(p->p.name, p->p.fd));
    }
}
