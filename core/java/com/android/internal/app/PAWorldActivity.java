/*
 * Copyright (C) 2010 The Android Open Source Project
 * This code has been modified. Portions copyright (C) 2013, ParanoidAndroid Project.
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
import android.graphics.Typeface;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.method.AllCapsTransformationMethod;
import android.text.method.TransformationMethod;
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
import android.widget.Toast;

public class PAWorldActivity extends Activity {
    FrameLayout mContent;

    final static int SOLID_BGCOLOR = 0xFF000000;
    final static int TEXT_COLOR = 0xFFFFFFFF;

    private static final String AOSPA_BUILD_VARIANT_PROP = "ro.aospa.build.variant";
    private static final String AOSPA_VERSION_MAJOR_PROP = "ro.aospa.version.major";
    private static final String AOSPA_VERSION_MINOR_PROP = "ro.aospa.version.minor";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);

        mContent = new FrameLayout(this);
        mContent.setBackgroundColor(SOLID_BGCOLOR);

        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;

        final ImageView bean = new ImageView(this);
        bean.setImageResource(com.android.internal.R.drawable.pa_bean);
        bean.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        bean.setMaxWidth((int)(metrics.widthPixels * 0.75));

        final ImageView world = new ImageView(this);
        world.setImageResource(com.android.internal.R.drawable.pa_world);
        world.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        world.setVisibility(View.INVISIBLE);

        final int p = (int)(4 * metrics.density);

        final TextView tv = new TextView(this);
        if (light != null) tv.setTypeface(light);
        tv.setTextSize(20);
        tv.setPadding(p, p, p, p);
        tv.setTextColor(TEXT_COLOR);
        tv.setGravity(Gravity.CENTER);
        tv.setTransformationMethod(new AllCapsTransformationMethod(this));
        tv.setText("Paranoid Android " + getVersion());
        tv.setVisibility(View.INVISIBLE);

        mContent.addView(bean, lp);
        mContent.addView(world, lp);

        final FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(lp);
        lp2.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp2.bottomMargin = p;

        mContent.addView(tv, lp2);

        mContent.setOnClickListener(new View.OnClickListener() {
            int clicks;
            @Override
            public void onClick(View v) {
                clicks++;
                if (clicks >= 6) {
                    mContent.performLongClick();
                    return;
                }
                bean.animate().cancel();
                final float offset = (int)bean.getRotation() % 360;
                bean.animate()
                    .rotationBy((Math.random() > 0.5f ? 360 : -360) - offset)
                    .setInterpolator(new DecelerateInterpolator())
                    .setDuration(700).start();
            }
        });

        mContent.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (world.getVisibility() != View.VISIBLE) {
                    bean.animate().alpha(0f).scaleY(0.5f).scaleX(0.5f)
                            .rotationBy(360)
                            .setInterpolator(new AccelerateInterpolator())
                            .setDuration(1000)
                            .start();
                    world.setAlpha(0f);
                    world.setVisibility(View.VISIBLE);
                    world.setScaleX(0.5f);
                    world.setScaleY(0.5f);
                    world.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(1000).setStartDelay(500)
                        .setInterpolator(new AnticipateOvershootInterpolator())
                        .start();
                    tv.setAlpha(0f);
                    tv.setVisibility(View.VISIBLE);
                    tv.animate().alpha(1f).setDuration(1000).setStartDelay(1000).start();
                    return true;
                }
                return false;
            }
        });

        world.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                try {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setClassName("com.android.settings", "com.android.settings.BeanBag")
                          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getBaseContext().startActivityAsUser(intent,
                            new UserHandle(UserHandle.USER_CURRENT));
                } catch (ActivityNotFoundException ex) {
                    android.util.Log.e("PAWorldActivity", "Couldn't catch a break.");
                }
                finish();
                return true;
            }
        });

        setContentView(mContent);
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
}
