/**
 * Copyright (C) 2014 The Android Open Source Project
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

package android.media.soundtrigger;

import static android.hardware.soundtrigger.SoundTrigger.STATUS_ERROR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.app.ActivityThread;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.ComponentName;
import android.content.Context;
import android.hardware.soundtrigger.ModelParams;
import android.hardware.soundtrigger.SoundTrigger;
import android.hardware.soundtrigger.SoundTrigger.GenericSoundModel;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseSoundModel;
import android.hardware.soundtrigger.SoundTrigger.ModelParamRange;
import android.hardware.soundtrigger.SoundTrigger.ModuleProperties;
import android.hardware.soundtrigger.SoundTrigger.RecognitionConfig;
import android.hardware.soundtrigger.SoundTrigger.SoundModel;
import android.media.permission.ClearCallingIdentityContext;
import android.media.permission.Identity;
import android.media.permission.SafeCloseable;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Slog;

import com.android.internal.app.ISoundTriggerService;
import com.android.internal.app.ISoundTriggerSession;
import com.android.internal.util.Preconditions;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * This class provides management of non-voice (general sound trigger) based sound recognition
 * models. Usage of this class is restricted to system or signature applications only. This allows
 * OEMs to write apps that can manage non-voice based sound trigger models.
 *
 * @hide
 */
@SystemApi
@SystemService(Context.SOUND_TRIGGER_SERVICE)
public final class SoundTriggerManager {
    private static final boolean DBG = false;
    private static final String TAG = "SoundTriggerManager";

    private final Context mContext;
    private final ISoundTriggerSession mSoundTriggerSession;
    private final IBinder mBinderToken = new Binder();

    // Stores a mapping from the sound model UUID to the SoundTriggerInstance created by
    // the createSoundTriggerDetector() call.
    private final HashMap<UUID, SoundTriggerDetector> mReceiverInstanceMap;

    /**
     * @hide
     */
    public SoundTriggerManager(Context context, ISoundTriggerService soundTriggerService) {
        if (DBG) {
            Slog.i(TAG, "SoundTriggerManager created.");
        }
        try {
            // This assumes that whoever is calling this ctor is the originator of the operations,
            // as opposed to a service acting on behalf of a separate identity.
            // Services acting on behalf of some other identity should not be using this class at
            // all, but rather directly connect to the server and attach with explicit credentials.
            Identity originatorIdentity = new Identity();
            originatorIdentity.packageName = ActivityThread.currentOpPackageName();

            try (SafeCloseable ignored = ClearCallingIdentityContext.create()) {
                List<ModuleProperties> modulePropertiesList = soundTriggerService
                        .listModuleProperties(originatorIdentity);
                if (!modulePropertiesList.isEmpty()) {
                    mSoundTriggerSession = soundTriggerService.attachAsOriginator(
                                                originatorIdentity,
                                                modulePropertiesList.get(0),
                                                mBinderToken);
                } else {
                    mSoundTriggerSession = null;
                }
            }
        } catch (RemoteException e) {
            throw e.rethrowAsRuntimeException();
        }
        mContext = context;
        mReceiverInstanceMap = new HashMap<UUID, SoundTriggerDetector>();
    }

    /**
     * Updates the given sound trigger model.
     * @deprecated replace with {@link #loadSoundModel}
     * SoundTriggerService model database will be removed
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @Deprecated
    public void updateModel(Model model) {
        if (mSoundTriggerSession == null) {
            throw new IllegalStateException("No underlying SoundTriggerModule available");
        }
        try {
            mSoundTriggerSession.updateSoundModel(model.getGenericSoundModel());
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get {@link SoundTriggerManager.Model} which is registered with the passed UUID
     *
     * @param soundModelId UUID associated with a loaded model
     * @return {@link SoundTriggerManager.Model} associated with UUID soundModelId
     * @deprecated SoundTriggerService model database will be removed
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @Nullable
    @Deprecated
    public Model getModel(UUID soundModelId) {
        if (mSoundTriggerSession == null) {
            throw new IllegalStateException("No underlying SoundTriggerModule available");
        }
        try {
            GenericSoundModel model =
                    mSoundTriggerSession.getSoundModel(new ParcelUuid(soundModelId));
            if (model == null) {
                return null;
            }

            return new Model(model);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Deletes the sound model represented by the provided UUID.
     * @deprecated replace with {@link #unloadSoundModel}
     * SoundTriggerService model database will be removed
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @Deprecated
    public void deleteModel(UUID soundModelId) {
        if (mSoundTriggerSession == null) {
            throw new IllegalStateException("No underlying SoundTriggerModule available");
        }

        try {
            mSoundTriggerSession.deleteSoundModel(new ParcelUuid(soundModelId));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Creates an instance of {@link SoundTriggerDetector} which can be used to start/stop
     * recognition on the model and register for triggers from the model. Note that this call
     * invalidates any previously returned instances for the same sound model Uuid.
     *
     * @param soundModelId UUID of the sound model to create the receiver object for.
     * @param callback Instance of the {@link SoundTriggerDetector#Callback} object for the
     * callbacks for the given sound model.
     * @param handler The Handler to use for the callback operations. A null value will use the
     * current thread's Looper.
     * @return Instance of {@link SoundTriggerDetector} or null on error.
     * @deprecated Use {@link SoundTriggerManager} directly. SoundTriggerDetector does not
     * ensure callbacks are delivered, and its model state is prone to mismatch.
     * It will be removed in a subsequent release.
     */
    @Nullable
    @Deprecated
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    public SoundTriggerDetector createSoundTriggerDetector(UUID soundModelId,
            @NonNull SoundTriggerDetector.Callback callback, @Nullable Handler handler) {
        if (soundModelId == null || mSoundTriggerSession == null) {
            return null;
        }

        SoundTriggerDetector oldInstance = mReceiverInstanceMap.get(soundModelId);
        if (oldInstance != null) {
            // Shutdown old instance.
        }
        try {
            SoundTriggerDetector newInstance = new SoundTriggerDetector(mSoundTriggerSession,
                    mSoundTriggerSession.getSoundModel(new ParcelUuid(soundModelId)),
                    callback, handler);
            mReceiverInstanceMap.put(soundModelId, newInstance);
            return newInstance;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
   }

    /**
     * Class captures the data and fields that represent a non-keyphrase sound model. Use the
     * factory constructor {@link Model#create()} to create an instance.
     */
    // We use encapsulation to expose the SoundTrigger.GenericSoundModel as a SystemApi. This
    // prevents us from exposing SoundTrigger.GenericSoundModel as an Api.
    public static class Model {

        private SoundTrigger.GenericSoundModel mGenericSoundModel;

        /**
         * @hide
         */
        Model(SoundTrigger.GenericSoundModel soundTriggerModel) {
            mGenericSoundModel = soundTriggerModel;
        }

        /**
         * Factory constructor to a voice model to be used with {@link SoundTriggerManager}
         *
         * @param modelUuid Unique identifier associated with the model.
         * @param vendorUuid Unique identifier associated the calling vendor.
         * @param data Model's data.
         * @param version Version identifier for the model.
         * @return Voice model
         */
        @NonNull
        public static Model create(@NonNull UUID modelUuid, @NonNull UUID vendorUuid,
                @Nullable byte[] data, int version) {
            Objects.requireNonNull(modelUuid);
            Objects.requireNonNull(vendorUuid);
            return new Model(new SoundTrigger.GenericSoundModel(modelUuid, vendorUuid, data,
                    version));
        }

        /**
         * Factory constructor to a voice model to be used with {@link SoundTriggerManager}
         *
         * @param modelUuid Unique identifier associated with the model.
         * @param vendorUuid Unique identifier associated the calling vendor.
         * @param data Model's data.
         * @return Voice model
         */
        @NonNull
        public static Model create(@NonNull UUID modelUuid, @NonNull UUID vendorUuid,
                @Nullable byte[] data) {
            return create(modelUuid, vendorUuid, data, -1);
        }

        /**
         * Get the model's unique identifier
         *
         * @return UUID associated with the model
         */
        @NonNull
        public UUID getModelUuid() {
            return mGenericSoundModel.getUuid();
        }

        /**
         * Get the model's vendor identifier
         *
         * @return UUID associated with the vendor of the model
         */
        @NonNull
        public UUID getVendorUuid() {
            return mGenericSoundModel.getVendorUuid();
        }

        /**
         * Get the model's version
         *
         * @return Version associated with the model
         */
        public int getVersion() {
            return mGenericSoundModel.getVersion();
        }

        /**
         * Get the underlying model data
         *
         * @return Backing data of the model
         */
        @Nullable
        public byte[] getModelData() {
            return mGenericSoundModel.getData();
        }

        /**
         * @hide
         */
        SoundTrigger.GenericSoundModel getGenericSoundModel() {
            return mGenericSoundModel;
        }
    }


    /**
     * Default message type.
     * @hide
     */
    public static final int FLAG_MESSAGE_TYPE_UNKNOWN = -1;
    /**
     * Contents of EXTRA_MESSAGE_TYPE extra for a RecognitionEvent.
     * @hide
     */
    public static final int FLAG_MESSAGE_TYPE_RECOGNITION_EVENT = 0;
    /**
     * Contents of EXTRA_MESSAGE_TYPE extra for recognition error events.
     * @hide
     */
    public static final int FLAG_MESSAGE_TYPE_RECOGNITION_ERROR = 1;
    /**
     * Contents of EXTRA_MESSAGE_TYPE extra for a recognition paused events.
     * @hide
     */
    public static final int FLAG_MESSAGE_TYPE_RECOGNITION_PAUSED = 2;
    /**
     * Contents of EXTRA_MESSAGE_TYPE extra for recognition resumed events.
     * @hide
     */
    public static final int FLAG_MESSAGE_TYPE_RECOGNITION_RESUMED = 3;

    /**
     * Extra key in the intent for the type of the message.
     * @hide
     */
    public static final String EXTRA_MESSAGE_TYPE = "android.media.soundtrigger.MESSAGE_TYPE";
    /**
     * Extra key in the intent that holds the RecognitionEvent parcelable.
     * @hide
     */
    public static final String EXTRA_RECOGNITION_EVENT = "android.media.soundtrigger.RECOGNITION_EVENT";
    /**
     * Extra key in the intent that holds the status in an error message.
     * @hide
     */
    public static final String EXTRA_STATUS = "android.media.soundtrigger.STATUS";

    /**
     * Loads a given sound model into the sound trigger. Note the model will be unloaded if there is
     * an error/the system service is restarted.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @UnsupportedAppUsage
    public int loadSoundModel(SoundModel soundModel) {
        if (soundModel == null || mSoundTriggerSession == null) {
            return STATUS_ERROR;
        }

        try {
            switch (soundModel.getType()) {
                case SoundModel.TYPE_GENERIC_SOUND:
                    return mSoundTriggerSession.loadGenericSoundModel(
                            (GenericSoundModel) soundModel);
                case SoundModel.TYPE_KEYPHRASE:
                    return mSoundTriggerSession.loadKeyphraseSoundModel(
                            (KeyphraseSoundModel) soundModel);
                default:
                    Slog.e(TAG, "Unkown model type");
                    return STATUS_ERROR;
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Starts recognition for the given model id. All events from the model will be sent to the
     * service.
     *
     * <p>This only supports generic sound trigger events. For keyphrase events, please use
     * {@link android.service.voice.VoiceInteractionService}.
     *
     * @param soundModelId Id of the sound model
     * @param params Opaque data sent to each service call of the service as the {@code params}
     *               argument
     * @param detectionService The component name of the service that should receive the events.
     *                         Needs to subclass {@link SoundTriggerDetectionService}
     * @param config Configures the recognition
     *
     * @return {@link SoundTrigger#STATUS_OK} if the recognition could be started, error code
     *         otherwise
     *
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @UnsupportedAppUsage
    public int startRecognition(@NonNull UUID soundModelId, @Nullable Bundle params,
        @NonNull ComponentName detectionService, @NonNull RecognitionConfig config) {
        Preconditions.checkNotNull(soundModelId);
        Preconditions.checkNotNull(detectionService);
        Preconditions.checkNotNull(config);
        if (mSoundTriggerSession == null) {
            return STATUS_ERROR;
        }
        try {
            return mSoundTriggerSession.startRecognitionForService(new ParcelUuid(soundModelId),
                params, detectionService, config);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Stops the given model's recognition.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public int stopRecognition(UUID soundModelId) {
        if (soundModelId == null || mSoundTriggerSession == null) {
            return STATUS_ERROR;
        }
        try {
            return mSoundTriggerSession.stopRecognitionForService(new ParcelUuid(soundModelId));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Removes the given model from memory. Will also stop any pending recognitions.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    public int unloadSoundModel(UUID soundModelId) {
        if (soundModelId == null || mSoundTriggerSession == null) {
            return STATUS_ERROR;
        }
        try {
            return mSoundTriggerSession.unloadSoundModel(
                    new ParcelUuid(soundModelId));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns true if the given model has had detection started on it.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @UnsupportedAppUsage
    public boolean isRecognitionActive(UUID soundModelId) {
        if (soundModelId == null || mSoundTriggerSession == null) {
            return false;
        }
        try {
            return mSoundTriggerSession.isRecognitionActive(
                    new ParcelUuid(soundModelId));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the amount of time (in milliseconds) an operation of the
     * {@link ISoundTriggerDetectionService} is allowed to ask.
     *
     * @return The amount of time an sound trigger detection service operation is allowed to last
     */
    public int getDetectionServiceOperationsTimeout() {
        try {
            return Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.SOUND_TRIGGER_DETECTION_SERVICE_OP_TIMEOUT);
        } catch (Settings.SettingNotFoundException e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Asynchronously get state of the indicated model.  The model state is returned as
     * a recognition event in the callback that was registered in the startRecognition
     * method.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @UnsupportedAppUsage
    public int getModelState(UUID soundModelId) {
        if (soundModelId == null || mSoundTriggerSession == null) {
            return STATUS_ERROR;
        }
        try {
            return mSoundTriggerSession.getModelState(new ParcelUuid(soundModelId));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the hardware sound trigger module properties currently loaded.
     *
     * @return The properties currently loaded. Returns null if no supported hardware loaded.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @Nullable
    public ModuleProperties getModuleProperties() {
        if (mSoundTriggerSession == null) {
            return null;
        }
        try {
            return mSoundTriggerSession.getModuleProperties();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set a model specific {@link ModelParams} with the given value. This
     * parameter will keep its value for the duration the model is loaded regardless of starting and
     * stopping recognition. Once the model is unloaded, the value will be lost.
     * {@link SoundTriggerManager#queryParameter} should be checked first before calling this
     * method.
     *
     * @param soundModelId UUID of model to apply the parameter value to.
     * @param modelParam   {@link ModelParams}
     * @param value        Value to set
     * @return - {@link SoundTrigger#STATUS_OK} in case of success
     *         - {@link SoundTrigger#STATUS_NO_INIT} if the native service cannot be reached
     *         - {@link SoundTrigger#STATUS_BAD_VALUE} invalid input parameter
     *         - {@link SoundTrigger#STATUS_INVALID_OPERATION} if the call is out of sequence or
     *           if API is not supported by HAL
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    public int setParameter(@Nullable UUID soundModelId,
            @ModelParams int modelParam, int value) {
        if (mSoundTriggerSession == null) {
            return SoundTrigger.STATUS_INVALID_OPERATION;
        }

        try {
            return mSoundTriggerSession.setParameter(new ParcelUuid(soundModelId), modelParam,
                    value);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get a model specific {@link ModelParams}. This parameter will keep its value
     * for the duration the model is loaded regardless of starting and stopping recognition.
     * Once the model is unloaded, the value will be lost. If the value is not set, a default
     * value is returned. See {@link ModelParams} for parameter default values.
     * {@link SoundTriggerManager#queryParameter} should be checked first before
     * calling this method. Otherwise, an exception can be thrown.
     *
     * @param soundModelId UUID of model to get parameter
     * @param modelParam   {@link ModelParams}
     * @return value of parameter
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    public int getParameter(@NonNull UUID soundModelId,
            @ModelParams int modelParam) {
        if (mSoundTriggerSession == null) {
            throw new IllegalArgumentException("Sound model is not loaded: "
                            + soundModelId.toString());
        }
        try {
            return mSoundTriggerSession.getParameter(new ParcelUuid(soundModelId), modelParam);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Determine if parameter control is supported for the given model handle.
     * This method should be checked prior to calling {@link SoundTriggerManager#setParameter} or
     * {@link SoundTriggerManager#getParameter}.
     *
     * @param soundModelId handle of model to get parameter
     * @param modelParam {@link ModelParams}
     * @return supported range of parameter, null if not supported
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_SOUND_TRIGGER)
    @Nullable
    public ModelParamRange queryParameter(@Nullable UUID soundModelId,
            @ModelParams int modelParam) {
        if (mSoundTriggerSession == null) {
            return null;
        }
        try {
            return mSoundTriggerSession.queryParameter(new ParcelUuid(soundModelId), modelParam);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
