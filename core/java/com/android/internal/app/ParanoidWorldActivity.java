/*
 * Copyright (C) 2023 Paranoid Android
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

package com.android.internal.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.Typeface;
import android.provider.Settings;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.mText.method.AllCapsTransformationMethod;
import android.mText.method.TransformationMethod;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ParanoidWorldActivity extends Activity {
    private FrameLayout mContent;
    private ImageView mBackground;
    private ImageView mWorld;
    private TextView mText;

    private DisplayMetrics mMetrics;

    private FrameLayout.LayoutParams mLp;
    private FrameLayout.LayoutParams mLp2;
    private FrameLayout.LayoutParams mLp3;

    private Sensor mAccelerometerSensor;
    private SensorManager mSensorManager;

    private static final int BG_COLOR = 0xFF000000;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private static final String AOSPA_BUILD_VARIANT_PROP = "ro.aospa.build.variant";
    private static final String AOSPA_VERSION_MAJOR_PROP = "ro.aospa.version.major";
    private static final String AOSPA_VERSION_MINOR_PROP = "ro.aospa.version.minor";

    private static final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometerSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        mMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        mContent = new FrameLayout(this);
        mContent.setBackgroundColor(BG_COLOR);

        mLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        mLp.gravity = Gravity.CENTER;

        mLp2 = new FrameLayout.LayoutParams(mLp);
        mLp2.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;

        mLp3 = new FrameLayout.LayoutParams(mLp2);
        mLp3.bottomMargin = (int)(4 * mMetrics.density);

        mBackground = new ImageView(this);
        mBackground.setImageResource(com.android.internal.R.drawable.paranoid_bg);
        mBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mContent.addView(mBackground, mLp2);

        mWorld = new ImageView(this);
        mWorld.setImageResource(com.android.internal.R.drawable.paranoid_world);
        mWorld.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        mWorld.setScaleX(0.5f);
        mWorld.setScaleY(0.5f);
        mWorld.setAlpha(0f);
        mWorld.setVisibility(View.INVISIBLE);
        mContent.addView(mWorld, mLp);

        mText = new TextView(this);
        if (light != null) mText.setTypeface(light);
        mText.setTextSize(20);
        mText.setPadding((int)(4 * mMetrics.density),
                      (int)(4 * mMetrics.density),
                      (int)(4 * mMetrics.density),
                      (int)(4 * mMetrics.density));
        mText.setTextColor(TEXT_COLOR);
        mText.setGravity(Gravity.CENTER);
        mText.setTransformationMethod(new AllCapsTransformationMethod(this));
        mText.setText("Paranoid Android " + getVersion());
        mText.setAlpha(0f);
        mText.setVisibility(View.INVISIBLE);
        mContent.addView(mText, mLp3);

        mContent.setOnClickListener(new View.OnClickListener() {
            int clicks;
            @Override
            public void onClick(View v) {
                clicks++;
                if (clicks >= 8) {
                    mContent.performLongClick();
                    return;
                }
            }
        });

        mContent.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mWorld.getVisibility() != View.VISIBLE) {
                    mBackground.animate().alpha(0.4f)
                        .setDuration(900).setStartDelay(500)
                        .setInterpolator(new AccelerateInterpolator())
                        .start();
                    mWorld.setVisibility(View.VISIBLE);
                    mWorld.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(1000).setStartDelay(500)
                        .setInterpolator(new AnticipateOvershootInterpolator())
                        .start();
                    mText.setVisibility(View.VISIBLE);
                    mText.animate().alpha(1f).setDuration(1000).setStartDelay(1000).start();
                    return true;
                }
                return false;
            }
        });

        mWorld.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("com.android.settings", "com.android.settings.BeanBag")
                          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getBaseContext().startActivityAsUser(intent,
                            new UserHandle(UserHandle.USER_CURRENT));
                } catch (ActivityNotFoundException ex) {
                    android.util.Log.e("ParanoidWorldActivity", "Couldn't catch a break.");
                }
                finish();
                return true;
            }
        });

        setContentView(mContent);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSensorManager.registerListener(mSensorEventListener,
            mAccelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    private String getVersion() {
        String aospaVersionMajor = SystemProperties.get(AOSPA_VERSION_MAJOR_PROP, "Unknown");
        String aospaVersionMinor = SystemProperties.get(AOSPA_VERSION_MINOR_PROP, "Unknown");
        String aospaBuildVariant = SystemProperties.get(AOSPA_BUILD_VARIANT_PROP, "Unknown");

        if (aospaBuildVariant.equals("Release")) {
            return aospaVersionMajor + " " + aospaVersionMinor;
        } else if (aospaBuildVariant.equals("Unofficial")) {
           return aospaVersionMajor + " " + aospaBuildVariant;
        } else {
           return aospaVersionMajor + " " + aospaBuildVariant + " " + aospaVersionMinor;
        }
    }

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
	    @Override
	    public void onSensorChanged(SensorEvent event) {
            if (mBackground == null) return;
            float x = event.values[0];
            int widthBg = mBackground.getMeasuredWidth();
            int widthScreen = mMetrics.widthPixels;
            mBackground.setTranslationX(x * 10);
            android.util.Log.e("ParanoidWorldActivity", "x: " + x);
            android.util.Log.e("ParanoidWorldActivity", "widthBg: " + widthBg);
            android.util.Log.e("ParanoidWorldActivity", "widthScreen: " + widthScreen);
	    }

	    @Override
	    public void onAccuracyChanged(Sensor sensor, int accuracy) {
	    }
    };
}
