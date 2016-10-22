package com.github.axet.hourlyreminder.fragments;

import android.Manifest;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.design.widget.FloatingActionButton;
import android.support.v13.app.FragmentCompat;
import android.support.v14.preference.PreferenceFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.Preference;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.support.v7.widget.ContentFrameLayout;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.SeekBarPreference;
import com.github.axet.androidlibrary.widgets.SeekBarPreferenceDialogFragment;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.widgets.BeepDialogFragment;
import com.github.axet.hourlyreminder.widgets.HoursDialogFragment;

public class SettingsFragment extends PreferenceFragment implements PreferenceFragment.OnPreferenceDisplayDialogCallback, SharedPreferences.OnSharedPreferenceChangeListener {
    Sound sound;
    Handler handler = new Handler();

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
    }

    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public boolean onPreferenceDisplayDialog(PreferenceFragment preferenceFragment, Preference preference) {
        if (preference instanceof SeekBarPreference) {
            AudioManager am = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamVolume(AudioManager.STREAM_ALARM), AudioManager.FLAG_SHOW_UI);

            SeekBarPreferenceDialogFragment f = SeekBarPreferenceDialogFragment.newInstance(preference.getKey());
            ((DialogFragment) f).setTargetFragment(this, 0);
            ((DialogFragment) f).show(this.getFragmentManager(), "android.support.v14.preference.PreferenceFragment.DIALOG");
            return true;
        }

        if (preference.getKey().equals(HourlyApplication.PREFERENCE_HOURS)) {
            HoursDialogFragment f = HoursDialogFragment.newInstance(preference.getKey());
            ((DialogFragment) f).setTargetFragment(this, 0);
            ((DialogFragment) f).show(this.getFragmentManager(), "android.support.v14.preference.PreferenceFragment.DIALOG");
            return true;
        }

        if (preference.getKey().equals(HourlyApplication.PREFERENCE_BEEP_CUSTOM)) {
            BeepDialogFragment f = BeepDialogFragment.newInstance(preference.getKey());
            ((DialogFragment) f).setTargetFragment(this, 0);
            ((DialogFragment) f).show(this.getFragmentManager(), "android.support.v14.preference.PreferenceFragment.DIALOG");
            return true;
        }

        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_settings);
        setHasOptionsMenu(true);

        sound = new Sound(getActivity());

        // 23 SDK requires to be Alarm to be percice on time
        if (Build.VERSION.SDK_INT < 23) {
            getPreferenceScreen().removePreference(findPreference(HourlyApplication.PREFERENCE_ALARM));
        } else {
            // it is only for 23 api phones and up. since only alarms can trigs often then 15 mins.

            findPreference(HourlyApplication.PREFERENCE_ALARM).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
                    int min = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_REPEAT, "60"));
                    if (min < 15) {
                        boolean b = (Boolean) o;
                        if (!b) {
                            Toast.makeText(getActivity(), R.string.Reminders15, Toast.LENGTH_SHORT).show();
                            SharedPreferences.Editor edit = shared.edit();
                            edit.putString(HourlyApplication.PREFERENCE_REPEAT, "15");
                            edit.commit();
                        }
                    }
                    return true;
                }
            });
        }

        RemindersFragment.bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_VOLUME));

        RemindersFragment.bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_INCREASE_VOLUME));

        if (DateFormat.is24HourFormat(getActivity())) {
            getPreferenceScreen().removePreference(findPreference(HourlyApplication.PREFERENCE_SPEAK_AMPM));
        }

        RemindersFragment.bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_THEME));
        RemindersFragment.bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_WEEKSTART));

        RemindersFragment.bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_SNOOZE_DELAY));
        RemindersFragment.bindPreferenceSummaryToValue(findPreference(HourlyApplication.PREFERENCE_SNOOZE_AFTER));

        findPreference(HourlyApplication.PREFERENCE_CALLSILENCE).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                if (!permitted(PERMISSIONS)) {
                    permitted(PERMISSIONS, 1);
                    return false;
                }
                return true;
            }
        });

        Vibrator v = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        Preference vp = findPreference(HourlyApplication.PREFERENCE_VIBRATE);

        if (!v.hasVibrator()) {
            getPreferenceScreen().removePreference(vp);
        } else {
            vp.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    if (!permitted(PERMISSIONS_V)) {
                        permitted(PERMISSIONS_V, 2);
                        return false;
                    }
                    annonce((boolean) o);
                    return true;
                }
            });
        }

        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.registerOnSharedPreferenceChangeListener(this);
    }

    void setPhone() {
        SwitchPreferenceCompat s = (SwitchPreferenceCompat) findPreference(HourlyApplication.PREFERENCE_CALLSILENCE);
        s.setChecked(true);
    }

    void setVibr() {
        SwitchPreferenceCompat s = (SwitchPreferenceCompat) findPreference(HourlyApplication.PREFERENCE_VIBRATE);
        s.setChecked(true);
        annonce(true);
    }

    void annonce(boolean v) {
        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean b = shared.getBoolean(HourlyApplication.PREFERENCE_BEEP, false);
        boolean s = shared.getBoolean(HourlyApplication.PREFERENCE_SPEAK, false);
        if (!b && !s) {
            RemindersFragment.annonce(getActivity(), v);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1:
                if (permitted(PERMISSIONS))
                    setPhone();
                else
                    Toast.makeText(getActivity(), R.string.NotPermitted, Toast.LENGTH_SHORT).show();
                break;
            case 2:
                if (permitted(PERMISSIONS_V))
                    setVibr();
                else
                    Toast.makeText(getActivity(), R.string.NotPermitted, Toast.LENGTH_SHORT).show();
                break;
        }
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_PHONE_STATE};

    public static final String[] PERMISSIONS_V = new String[]{Manifest.permission.VIBRATE};

    boolean permitted(String[] ss) {
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(getActivity(), s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    boolean permitted(String[] p, int c) {
        for (String s : p) {
            if (ContextCompat.checkSelfPermission(getActivity(), s) != PackageManager.PERMISSION_GRANTED) {
                FragmentCompat.requestPermissions(this, p, c);
                return false;
            }
        }
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);

        {
            final Context context = inflater.getContext();
            ViewGroup layout = (ViewGroup) view.findViewById(R.id.list_container);
            RecyclerView v = getListView();

            int fab_margin = (int) getResources().getDimension(R.dimen.fab_margin);
            int fab_size = ThemeUtils.dp2px(getActivity(), 61);
            int pad = 0;
            int top = 0;
            if (Build.VERSION.SDK_INT <= 16) { // so, it bugged only on 16
                pad = ThemeUtils.dp2px(context, 10);
                top = (int) getResources().getDimension(R.dimen.appbar_padding_top);
            }

            v.setClipToPadding(false);
            v.setPadding(pad, top, pad, pad + fab_size + fab_margin);

            FloatingActionButton fab = new FloatingActionButton(context);
            fab.setImageResource(R.drawable.play);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ContentFrameLayout.LayoutParams.WRAP_CONTENT, ContentFrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            lp.setMargins(fab_margin, fab_margin, fab_margin, fab_margin);
            fab.setLayoutParams(lp);
            layout.addView(fab);

            // fix nexus 9 tabled bug, when fab showed offscreen
            handler.post(new Runnable() {
                @Override
                public void run() {
                    view.requestLayout();
                }
            });

            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    sound.soundReminder(System.currentTimeMillis());
                }
            });
        }

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (sound != null) {
            sound.close();
            sound = null;
        }

        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
        shared.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals(HourlyApplication.PREFERENCE_ALARM)) {
            ((SwitchPreferenceCompat) findPreference(HourlyApplication.PREFERENCE_ALARM)).setChecked(sharedPreferences.getBoolean(HourlyApplication.PREFERENCE_ALARM, false));
        }
    }
}
