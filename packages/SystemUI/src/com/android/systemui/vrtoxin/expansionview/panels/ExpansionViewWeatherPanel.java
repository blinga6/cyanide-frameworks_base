/*
 * Copyright (C) 2015 DarkKat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.vrtoxin.expansionview.panels;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.PorterDuff.Mode;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import com.android.internal.util.vrtoxin.WeatherServiceController;
import com.android.internal.util.vrtoxin.WeatherServiceController.DayForecast;
import com.android.internal.util.vrtoxin.WeatherServiceControllerImpl;
import com.android.internal.util.vrtoxin.ExpansionViewColorHelper;
import com.android.internal.util.vrtoxin.WeatherHelper;

import java.util.ArrayList;
import java.util.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class ExpansionViewWeatherPanel extends FrameLayout implements
        WeatherServiceController.Callback {

    private final Context mContext;
    private PhoneStatusBar mStatusBar;
    private WeatherServiceController mWeatherController;

    private LinearLayout mWeatherBar;
    private TextView mNoWeather;

    private boolean mWeatherAvailable = false;
    private boolean mListening = false;
    private int mExpansionViewWeatherTextSize;
    private boolean mExpansionViewVibrate = false;

    protected Vibrator mVibrator;

    public ExpansionViewWeatherPanel(Context context) {
        this(context, null);
    }

    public ExpansionViewWeatherPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public void setUp(PhoneStatusBar statusBar, WeatherServiceController weather) {
        mStatusBar = statusBar;
        mWeatherController = weather;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mNoWeather = (TextView) findViewById(R.id.expansion_view_weather_no_weather);
        mWeatherBar = 
                (LinearLayout) findViewById(R.id.expansion_view_weather);
        mWeatherBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                startForecastActivity();
            }
        });
        mWeatherBar.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mExpansionViewVibrate) {
                    vibrate(20);
                }
                startWeatherServiceActivity();
                return true;
            }
        });
    }

    public void setListening(boolean listening) {
        if (mWeatherController == null) {
            return;
        }
        if (listening && !mListening) {
            mListening = true;
            mWeatherController.addCallback(this);
        }
        if (mListening) {
            mWeatherController.addCallback(this);
        }
    }

    public void updateClickTargets(boolean clickable) {
    }

    @Override
    public void onWeatherChanged(WeatherServiceController.WeatherInfo info) {
        if (info.temp != null && info.condition != null) {
            mWeatherAvailable = true;
        } else {
            mWeatherAvailable = false;
        }
        updateWeather(info);
        if (mWeatherAvailable) {
            createItems(info);
        }
    }

    private void updateWeather(WeatherServiceController.WeatherInfo info) {
        if (!mWeatherAvailable) {
            mNoWeather.setVisibility(View.VISIBLE);
        } else {
            mNoWeather.setVisibility(View.GONE);
        }
    }

    private void createItems(WeatherServiceController.WeatherInfo info) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        TimeZone myTimezone = TimeZone.getDefault();
        Calendar calendar = new GregorianCalendar(myTimezone);
        final int iconColor =  ExpansionViewColorHelper.getWeatherIconColor(mContext);
        final int textColorPrimary = ExpansionViewColorHelper.getWeatherTextColor(mContext);
        final int textColorSecondary = (179 << 24) | (textColorPrimary & 0x00ffffff);
        mWeatherBar.removeAllViews();
        mNoWeather.setTextColor(textColorPrimary);
        mNoWeather.setTextSize(mExpansionViewWeatherTextSize);

        boolean isToday = false;
        if (WeatherHelper.showCurrent(mContext)) {
            View currentItem = inflater.inflate(R.layout.expansion_view_weather_current_item, null);

            TextView updateTime = (TextView) currentItem.findViewById(R.id.weather_update_time);
            updateTime.setText(getUpdateTime(info));
            updateTime.setTextColor(textColorPrimary);
            updateTime.setTextSize(mExpansionViewWeatherTextSize);
            calendar.roll(Calendar.DAY_OF_WEEK, true);

            ImageView currentImage = (ImageView) currentItem.findViewById(R.id.weather_image);
            Drawable icon = WeatherHelper.getCurrentConditionDrawable(mContext, info)
                    .getConstantState().newDrawable();
            currentImage.setImageDrawable(icon);
            if (WeatherHelper.getIconType(mContext) == 0) {
                currentImage.setColorFilter(iconColor, Mode.MULTIPLY);
            }
            TextView temp = (TextView) currentItem.findViewById(R.id.weather_temp);
            temp.setText(info.temp);
            temp.setTextColor(textColorPrimary);
            temp.setTextSize(mExpansionViewWeatherTextSize);

            mWeatherBar.addView(currentItem,
                  new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            isToday = true;
        }

        ArrayList<DayForecast> forecasts = (ArrayList) info.forecasts;

        for (DayForecast d : forecasts) {
            if (!isToday) {
                View forecastItem = inflater.inflate(R.layout.expansion_view_weather_forecast_item, null);

                TextView day = (TextView) forecastItem.findViewById(R.id.forecast_day);
                day.setText(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()));
                day.setTextColor(textColorPrimary);
                day.setTextSize(mExpansionViewWeatherTextSize);
                calendar.roll(Calendar.DAY_OF_WEEK, true);

                ImageView image = (ImageView) forecastItem.findViewById(R.id.weather_image);
                Drawable icon = WeatherHelper.getForcastConditionDrawable(mContext, d)
                        .getConstantState().newDrawable();
                image.setImageDrawable(icon);
                if (WeatherHelper.getIconType(mContext) == 0) {
                    image.setColorFilter(iconColor, Mode.MULTIPLY);
                }
                TextView temps = (TextView) forecastItem.findViewById(R.id.forecast_temps);
                temps.setText(isToday ? info.temp : d.low + " | " + d.high);
                temps.setTextColor(isToday ? textColorPrimary : textColorSecondary);
                temps.setTextSize(mExpansionViewWeatherTextSize);

                mWeatherBar.addView(forecastItem,
                      new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            } else {
                isToday = false;
            }
        }
        setExpansionViewWeatherTextSize();
    }

    public void updateItems() {
        if (mWeatherAvailable && mWeatherController != null) {
            createItems(mWeatherController.getWeatherInfo());
        }
    }

    public void setRippleColor() {
        RippleDrawable background =
                (RippleDrawable) mContext.getDrawable(R.drawable.ripple_drawable_rectangle).mutate();
        final int color = ExpansionViewColorHelper.getNormalRippleColor(mContext);
        background.setColor(ColorStateList.valueOf(color));
        mWeatherBar.setBackground(background);
    }

    private void startForecastActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(WeatherServiceControllerImpl.COMPONENT_WEATHER_FORECAST);
        mStatusBar.startActivity(intent, true /* dismissShade */);
    }

    private void startWeatherServiceActivity() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("net.cyanide.weather",
            "net.cyanide.weather.SettingsActivity");
        mStatusBar.startActivity(intent, true /* dismissShade */);
    }

    public void setExpansionViewWeatherTextSize() {
        mExpansionViewWeatherTextSize = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.EXPANSION_VIEW_WEATHER_TEXT_SIZE, 14);
    }

    public void vibrateOnClick(boolean vibrate) {
        mExpansionViewVibrate = vibrate;
    }

    public void vibrate(int duration) {
        if (mVibrator != null) {
            if (mVibrator.hasVibrator()) { mVibrator.vibrate(duration); }
        }
    }

    private String getUpdateTime(WeatherServiceController.WeatherInfo info) {
        if (info.timeStamp != null) {
            Date lastUpdate = new Date(info.timeStamp);
            StringBuilder sb = new StringBuilder();
            sb.append(DateFormat.getTimeFormat(mContext).format(lastUpdate));
            return sb.toString();
        } else {
            String empty = "";
            return empty;
        }
    }
}
