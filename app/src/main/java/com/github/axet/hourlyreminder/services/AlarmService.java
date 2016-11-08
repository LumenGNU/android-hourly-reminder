package com.github.axet.hourlyreminder.services;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.activities.MainActivity;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.basics.Alarm;
import com.github.axet.hourlyreminder.basics.Reminder;
import com.github.axet.hourlyreminder.basics.ReminderSet;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;

/**
 * System Alarm Manager notifes this service to create/stop alarms.
 * <p/>
 * All Alarm notifications clicks routed to this service.
 */
public class AlarmService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = AlarmService.class.getSimpleName();

    // upcoming noticiation alarm action. triggers notification upcoming.
    public static final String REGISTER = AlarmService.class.getCanonicalName() + ".REGISTER";
    // upcoming noticiation alarm action. triggers notification upcoming.
    public static final String NOTIFICATION = AlarmService.class.getCanonicalName() + ".NOTIFICATION";
    // cancel alarm
    public static final String CANCEL = HourlyApplication.class.getCanonicalName() + ".CANCEL";
    // alarm broadcast, triggs sound
    public static final String ALARM = HourlyApplication.class.getCanonicalName() + ".ALARM";
    // reminder broadcast triggs sound
    public static final String REMINDER = HourlyApplication.class.getCanonicalName() + ".REMINDER";
    // dismiss current alarm action
    public static final String DISMISS = HourlyApplication.class.getCanonicalName() + ".DISMISS";

    public static void start(Context context) {
        Intent intent = new Intent(context, AlarmService.class);
        intent.setAction(REGISTER);
        context.startService(intent);
    }

    Sound sound;
    List<Alarm> alarms;
    List<ReminderSet> reminders;

    public AlarmService() {
        super();
    }

    public static String formatTime(long time) {
        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return s.format(new Date(time));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        sound = new Sound(this);
        alarms = HourlyApplication.loadAlarms(this);
        reminders = HourlyApplication.loadReminders(this);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);

        if (sound != null) {
            sound.close();
            sound = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand " + action);
            if (action != null) {
                long time = intent.getLongExtra("time", 0);
                if (action.equals(NOTIFICATION)) {
                    showNotificationUpcoming(time);
                } else if (action.equals(CANCEL)) {
                    tomorrow(time);
                } else if (action.equals(DISMISS)) {
                    FireAlarmService.dismissActiveAlarm(this);
                } else if (action.equals(ALARM)) {
                    soundAlarm(time);
                } else if (action.equals(REMINDER)) {
                    soundAlarm(time);
                } else if (action.equals(REGISTER)) {
                    alarms = HourlyApplication.loadAlarms(this);
                    reminders = HourlyApplication.loadReminders(this);
                    registerNextAlarm();
                }
            }
        } else {
            Log.d(TAG, "onStartCommand restart");
            alarms = HourlyApplication.loadAlarms(this);
            registerNextAlarm();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    // create list for hour reminders. 'time' list for reminder (hronological order)
    public TreeSet<Long> generateReminders(Calendar cur) {
        TreeSet<Long> alarms = new TreeSet<>();

        for (ReminderSet rr : reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.enabled)
                        alarms.add(r.getTime());
                }
            }
        }

        return alarms;
    }

    // create list for alarms. 'time' list for alarms (hronological order)
    public TreeSet<Long> generateAlarms() {
        TreeSet<Long> alarms = new TreeSet<>();

        for (Alarm a : this.alarms) {
            if (a.enabled)
                alarms.add(a.getTime());
        }

        return alarms;
    }

    // cancel alarm 'time' by set it time for day+1 (same hour:min)
    public void tomorrow(long time) {
        for (Alarm a : alarms) {
            if (a.getTime() == time && a.enabled) {
                if (a.weekdaysCheck) {
                    // be safe for another timezone. if we moved we better call setNext().
                    // but here we have to jump over next alarm.
                    a.setTomorrow();
                } else {
                    a.setEnable(false);
                }
                HourlyApplication.toastAlarmSet(this, a);

                HourlyApplication.saveAlarms(this, alarms);
            }
        }

        for (ReminderSet rr : reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.getTime() == time && r.enabled) {
                        r.setTomorrow();
                    }
                }
            }
        }

        registerNextAlarm();
    }

    // register alarm event for next one.
    //
    // scan all alarms and hourly reminders and register net one
    //
    public void registerNextAlarm() {
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        TreeSet<Long> all = new TreeSet<>();
        TreeSet<Long> reminders;
        TreeSet<Long> alarms;

        Calendar cur = Calendar.getInstance();

        // check hourly reminders
        reminders = generateReminders(cur);
        all.addAll(reminders);

        // check alarms
        alarms = generateAlarms();
        all.addAll(alarms);

        Intent alarmIntent = new Intent(this, AlarmService.class).setAction(ALARM);
        Intent reminderIntent = new Intent(this, AlarmService.class).setAction(REMINDER);

        if (all.isEmpty()) {
            updateNotificationUpcomingAlarm(0);
        } else {
            long time = all.first();
            updateNotificationUpcomingAlarm(time);
        }

        if (reminders.isEmpty()) {
            PendingIntent pe = PendingIntent.getService(this, 0, reminderIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            alarm.cancel(pe);
        } else {
            long time = reminders.first();

            reminderIntent.putExtra("time", time);

            PendingIntent pe = PendingIntent.getService(this, 0, reminderIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

            Log.d(HourlyApplication.class.getSimpleName(), "Current: " + formatTime(cur.getTimeInMillis()) + "; SetReminder: " + formatTime(time));

            if (shared.getBoolean(HourlyApplication.PREFERENCE_ALARM, true)) {
                if (Build.VERSION.SDK_INT >= 21) {
                    alarm.setAlarmClock(new AlarmManager.AlarmClockInfo(time, pe), pe);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    alarm.setExact(AlarmManager.RTC_WAKEUP, time, pe);
                } else {
                    alarm.set(AlarmManager.RTC_WAKEUP, time, pe);
                }
            } else {
                if (Build.VERSION.SDK_INT >= 23) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pe);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    alarm.setExact(AlarmManager.RTC_WAKEUP, time, pe);
                } else {
                    alarm.set(AlarmManager.RTC_WAKEUP, time, pe);
                }
            }
        }

        if (alarms.isEmpty()) {
            PendingIntent pe = PendingIntent.getService(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);
            alarm.cancel(pe);
        } else {
            long time = alarms.first();

            alarmIntent.putExtra("time", time);

            PendingIntent pe = PendingIntent.getService(this, 0, alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

            Log.d(HourlyApplication.class.getSimpleName(), "Current: " + formatTime(cur.getTimeInMillis()) + "; SetAlarm: " + formatTime(time));

            if (Build.VERSION.SDK_INT >= 21) {
                alarm.setAlarmClock(new AlarmManager.AlarmClockInfo(time, pe), pe);
            } else if (Build.VERSION.SDK_INT >= 19) {
                alarm.setExact(AlarmManager.RTC_WAKEUP, time, pe);
            } else {
                alarm.set(AlarmManager.RTC_WAKEUP, time, pe);
            }
        }
    }

    // register notification_upcoming alarm event for 'time' - 15min.
    //
    // service will call showNotificationUpcoming(time)
    //
    void updateNotificationUpcomingAlarm(long time) {
        AlarmManager alarm = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

        PendingIntent pe = PendingIntent.getService(this, 0,
                new Intent(this, AlarmService.class).setAction(NOTIFICATION).putExtra("time", time),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        alarm.cancel(pe);

        if (time == 0) {
            showNotificationUpcoming(0);
        } else {
            Calendar cur = Calendar.getInstance();

            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(time);

            int repeat = getRepeat(time) * 60; // make seconds
            int sec = repeat / 4;
            cal.add(Calendar.SECOND, -sec);

            if (cur.after(cal)) {
                // we already 15 before alarm, show notification_upcoming
                showNotificationUpcoming(time);
            } else {
                showNotificationUpcoming(0);
                // time to wait before show notification_upcoming
                time = cal.getTimeInMillis();

                if (Build.VERSION.SDK_INT >= 23) {
                    alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pe);
                } else if (Build.VERSION.SDK_INT >= 19) {
                    alarm.setExact(AlarmManager.RTC_WAKEUP, time, pe);
                } else {
                    alarm.set(AlarmManager.RTC_WAKEUP, time, pe);
                }
            }
        }
    }

    boolean isAlarm(long time) {
        for (Alarm a : alarms) {
            if (a.getTime() == time && a.getEnable())
                return true;
        }
        return false;
    }

    boolean isReminder(long time) {
        for (ReminderSet rr : reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.getTime() == time && r.enabled) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    int getRepeat(long time) {
        TreeSet<Integer> rep = new TreeSet<>();
        rep.add(60); // default 60 minutes
        for (ReminderSet rr : reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.getTime() == time && r.enabled) {
                        rep.add(rr.repeat); // add 15 or 5 or 30
                    }
                }
            }
        }
        return rep.first(); // sorted smallest first, but 60 must
    }

    // show notification_upcoming. (about upcoming alarm)
    //
    // time - 0 cancel notifcation
    // time - upcoming alarm time, show text.
    public void showNotificationUpcoming(long time) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(HourlyApplication.PREFERENCE_NOTIFICATIONS, true))
            return;

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (time == 0) {
            notificationManager.cancel(HourlyApplication.NOTIFICATION_UPCOMING_ICON);
        } else {
            PendingIntent button = PendingIntent.getService(this, 0,
                    new Intent(this, AlarmService.class).setAction(CANCEL).putExtra("time", time),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            PendingIntent main = PendingIntent.getActivity(this, 0,
                    new Intent(this, MainActivity.class).setAction(MainActivity.SHOW_ALARMS_PAGE).putExtra("time", time),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            String subject = getString(R.string.UpcomingAlarm);
            if (isReminder(time))
                subject = getString(R.string.UpcomingChime);
            String text = Alarm.format2412ap(this, time);
            for (Alarm a : alarms) {
                if (a.getTime() == time) {
                    if (a.isSnoozed()) {
                        text += " (" + getString(R.string.snoozed) + ": " + a.format2412ap() + ")";
                    }
                }
            }

            RemoteViews view = new RemoteViews(getPackageName(), HourlyApplication.getTheme(getBaseContext(), R.layout.notification_alarm_light, R.layout.notification_alarm_dark));
            view.setOnClickPendingIntent(R.id.notification_button, button);
            view.setOnClickPendingIntent(R.id.notification_base, main);
            view.setTextViewText(R.id.notification_subject, subject);
            view.setTextViewText(R.id.notification_text, text);
            view.setTextViewText(R.id.notification_button, getString(R.string.Cancel));

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setContentTitle(subject)
                    .setContentText(text)
                    .setSmallIcon(R.drawable.ic_notifications_black_24dp)
                    .setContent(view);

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            notificationManager.notify(HourlyApplication.NOTIFICATION_UPCOMING_ICON, builder.build());
        }
    }

    // alarm come from service call (System Alarm Manager) for specified time
    //
    // we have to check what 'alarms' do we have at specified time (can be reminder + alarm)
    // and act propertly.
    public void soundAlarm(final long time) {
        // find hourly reminder + alarm = combine proper sound notification_upcoming (can be merge beep, speech, ringtone)
        //
        // then sound alarm or hourly reminder

        boolean alarmed = false;

        // here can be two alarms with same time
        for (Alarm a : alarms) {
            if (a.getTime() == time && a.enabled) {
                Log.d(TAG, "Sound Alarm " + Alarm.format24(a.getTime()));
                Alarm old = new Alarm(a);
                if (!a.weekdaysCheck) {
                    // disable alarm after it goes off for non rcuring alarms (!a.weekdays)
                    a.setEnable(false);
                } else {
                    // calling setNext is more safe. if this alarm have to fire today we will reset it
                    // to the same time. if it is already past today's time (as we expect) then it will
                    // be set for tomorrow.
                    //
                    // also safe if we moved to another timezone.
                    a.setNext();
                }
                HourlyApplication.saveAlarms(this, alarms);

                FireAlarmService.activateAlarm(this, old);
                registerNextAlarm();
                alarmed = true;
            }
        }

        for (ReminderSet rr : reminders) {
            if (rr.enabled) {
                for (Reminder r : rr.list) {
                    if (r.getTime() == time && r.enabled) {
                        if (!alarmed)
                            sound.soundReminder(rr, time);

                        // calling setNext is more safe. if this alarm have to fire today we will reset it
                        // to the same time. if it is already past today's time (as we expect) then it will
                        // be set for tomorrow.
                        //
                        // also safe if we moved to another timezone.
                        r.setNext();

                        registerNextAlarm();
                    }
                }
            }
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG, "onSharedPreferenceChanged " + key);

        // do not update on pref change for alarms, too slow. use direct call from AlarmFragment
//        if (key.startsWith(HourlyApplication.PREFERENCE_ALARMS_PREFIX)) {
//            alarms = HourlyApplication.loadAlarms(this);
//            registerNextAlarm();
//        }

        // reset reminders on special events
        if (key.equals(HourlyApplication.PREFERENCE_ALARM)) {
            registerNextAlarm();
        }
    }
}
