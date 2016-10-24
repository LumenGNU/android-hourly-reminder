package com.github.axet.hourlyreminder.activities;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.basics.Alarm;
import com.github.axet.hourlyreminder.services.FireAlarmService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class AlarmActivity extends AppCompatActivity {
    public static final String TAG = AlarmActivity.class.getSimpleName();

    // alarm activity action. close it.
    public static final String CLOSE_ACTIVITY = AlarmActivity.class.getCanonicalName() + ".CLOSE_ACTIVITY";

    Handler handler = new Handler();
    Runnable updateClock;
    long time;

    public static void showAlarmActivity(Context context, long time, Sound.Silenced silenced) {
        Intent intent = new Intent(context, AlarmActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("time", time);
        intent.putExtra("silenced", silenced);
        context.startActivity(intent);
    }

    public static void closeAlarmActivity(Context context) {
        Intent intent = new Intent(context, AlarmActivity.class);
        intent.setAction(CLOSE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        setTheme(HourlyApplication.getTheme(this, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar));

        layoutInit();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        Intent intent = getIntent();

        String action = intent.getAction();
        if (action != null && action.equals(CLOSE_ACTIVITY)) {
            finish();
            return;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // bug? it haven't been called
        getResources().updateConfiguration(newConfig, null);

        layoutInit();
    }

    void layoutInit() {
        setContentView(R.layout.activity_alarm);

        Intent intent = getIntent();

        time = intent.getLongExtra("time", 0);

        View alarm = findViewById(R.id.alarm);

        List<Alarm> list = HourlyApplication.loadAlarms(this);
        for (Alarm a : list) {
            if (a.time == time) { // conflicts are ok here
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(a.time);
                cal.set(Calendar.HOUR_OF_DAY, a.getHour());
                cal.set(Calendar.MINUTE, a.getMin());
                updateTime(alarm, cal.getTimeInMillis());
            }
        }

        updateClock();

        View dismiss = findViewById(R.id.alarm_activity_button);
        dismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        View snooze = findViewById(R.id.alarm_snooze_button);
        snooze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FireAlarmService.snooze(AlarmActivity.this, time);
                finish();
            }
        });

        Sound.Silenced silenced = (Sound.Silenced) intent.getSerializableExtra("silenced");
        TextView sil = (TextView) findViewById(R.id.alarm_silenced);
        sil.setVisibility(View.GONE);
        if (silenced != null && silenced != Sound.Silenced.NONE) {
            sil.setVisibility(View.VISIBLE);
            switch (silenced) {
                case VIBRATE:
                    sil.setText(R.string.SoundSilencedVibrate);
                    break;
                case CALL:
                    sil.setText(R.string.SoundSilencedCall);
                    break;
                case MUSIC:
                    sil.setText(R.string.SoundSilencedMusic);
                    break;
                case SETTINGS:
                    sil.setText(R.string.SoundSilencedSettings);
                    break;
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // handing startActivity with Intent.FLAG_ACTIVITY_NEW_TASK
        String action = intent.getAction();
        if (action != null && action.equals(CLOSE_ACTIVITY))
            finish();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    void updateClock() {
        View time = findViewById(R.id.time);
        updateTime(time, System.currentTimeMillis());

        if (updateClock == null) {
            handler.removeCallbacks(updateClock);
        }
        updateClock = new Runnable() {
            @Override
            public void run() {
                updateClock();
            }
        };
        handler.postDelayed(updateClock, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (updateClock != null) {
            handler.removeCallbacks(updateClock);
            updateClock = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateClock();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        FireAlarmService.dismissActiveAlarm(this);

        if (updateClock != null) {
            handler.removeCallbacks(updateClock);
            updateClock = null;
        }
    }

    void updateTime(View view, long t) {
        TextView time = (TextView) view.findViewById(R.id.alarm_time);
        TextView am = (TextView) view.findViewById(R.id.alarm_am);
        TextView pm = (TextView) view.findViewById(R.id.alarm_pm);

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(t);
        int hour = cal.get(Calendar.HOUR_OF_DAY);

        am.setText(HourlyApplication.getHourString(this, hour));
        pm.setText(HourlyApplication.getHourString(this, hour));

        time.setText(Alarm.format2412(this, t));

        if (DateFormat.is24HourFormat(this)) {
            am.setVisibility(View.GONE);
            pm.setVisibility(View.GONE);
        } else {
            am.setVisibility(hour >= 12 ? View.GONE : View.VISIBLE);
            pm.setVisibility(hour >= 12 ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(false);
    }

}
