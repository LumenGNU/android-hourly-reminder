package com.github.axet.hourlyreminder.fragments;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.axet.androidlibrary.animations.ExpandItemAnimator;
import com.github.axet.androidlibrary.animations.MarginAnimation;
import com.github.axet.androidlibrary.widgets.OpenChoicer;
import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.alarms.Week;
import com.github.axet.hourlyreminder.alarms.WeekSet;
import com.github.axet.hourlyreminder.animations.AlarmAnimation;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Sound;
import com.github.axet.hourlyreminder.app.SoundConfig;
import com.github.axet.hourlyreminder.app.Storage;

import java.io.File;

public abstract class WeekSetFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final int RESULT_RINGTONE = 0;
    public static final int RESULT_FILE = 1;

    RecyclerView list;

    long selected = -1; // id
    boolean boxAnimate;
    Handler handler;
    boolean preview; // preview ringtone
    View alarmRingtonePlay;
    Sound sound;
    Storage storage;
    OpenChoicer choicer;
    ExpandItemAnimator animator;
    Adapter adapter;

    int startweek = 0;

    public static boolean checkboxAnimate(boolean checkbox, View view) {
        boolean animate;
        Animation a = view.getAnimation();
        if (a != null && !a.hasEnded())
            return true;
        if (checkbox)
            animate = view.getVisibility() != View.VISIBLE;
        else
            animate = view.getVisibility() == View.VISIBLE;
        return animate;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public View alarmRingtonePlay;
        public CheckBox weekdays;
        public LinearLayout weekdaysValues;
        public CheckBox alarmRingtone;
        public View alarmRingtoneLayout;
        public ImageView expand;

        public ViewHolder(View v) {
            super(v);
            alarmRingtonePlay = v.findViewById(R.id.alarm_ringtone_play);
            weekdays = (CheckBox) v.findViewById(R.id.alarm_week_days);
            weekdaysValues = (LinearLayout) v.findViewById(R.id.alarm_week);
            alarmRingtone = (CheckBox) v.findViewById(R.id.alarm_ringtone);
            alarmRingtoneLayout = v.findViewById(R.id.alarm_ringtone_layout);
            expand = (ImageView) v.findViewById(R.id.alarm_expand);
        }
    }

    public class Adapter extends RecyclerView.Adapter<ViewHolder> {
        @Override
        public int getItemCount() {
            return 0;
        }

        public Object getItem(int position) {
            return null;
        }

        @Override
        public long getItemId(int position) {
            return -1;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        public int getPosition(long id) {
            return -1;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View convertView = inflater.inflate(R.layout.alarm, parent, false);
            return new ViewHolder(convertView);
        }

        @Override
        public void onBindViewHolder(ViewHolder h, int position) {
            final WeekSet a = (WeekSet) getItem(position);

            h.alarmRingtonePlay.clearAnimation();

            animator.onBindViewHolder(h, position);

            if (selected == a.id) {
                fillDetailed(h.itemView, a, boxAnimate);
                h.expand.setImageResource(R.drawable.arrow_up);
            } else {
                fillCompact(h.itemView, a, boxAnimate);
                h.expand.setImageResource(R.drawable.arrow_down);
            }
        }
    }

    public WeekSetFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler();

        animator = new ExpandItemAnimator() {
            @Override
            public Animation apply(RecyclerView.ViewHolder holder, boolean animate) {
                ViewHolder h = (ViewHolder) holder;
                final WeekSet a = (WeekSet) adapter.getItem(h.getAdapterPosition());
                if (selected == a.id) {
                    Animation n = AlarmAnimation.apply(list, h.itemView, true, animate);

                    MarginAnimation.apply(h.weekdaysValues, h.weekdays.isChecked(), n == null && checkboxAnimate(h.weekdays.isChecked(), h.weekdaysValues));
                    MarginAnimation.apply(h.alarmRingtoneLayout, h.alarmRingtone.isChecked(), n == null && checkboxAnimate(h.alarmRingtone.isChecked(), h.alarmRingtoneLayout));

                    return n;
                } else {
                    return AlarmAnimation.apply(list, h.itemView, false, animate);
                }
            }

            @Override
            public void onScrollStateChanged(int state) {
                super.onScrollStateChanged(state);
                boxAnimate = true;
            }
        };

        sound = new Sound(getActivity());

        storage = new Storage(getActivity());

        updateStartWeek();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.registerOnSharedPreferenceChangeListener(this);

        if (savedInstanceState != null)
            selected = savedInstanceState.getLong("selected");
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("selected", selected);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        boxAnimate = true;
        if (key.equals(HourlyApplication.PREFERENCE_WEEKSTART))
            updateStartWeek();
    }

    public void updateStartWeek() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String s = prefs.getString(HourlyApplication.PREFERENCE_WEEKSTART, "");
        for (int i = 0; i < Week.DAYS.length; i++) {
            if (s.equals(getString(Week.DAYS[i]))) {
                startweek = i;
                break;
            }
        }
    }

    public abstract Uri fallbackUri(Uri uri);

    void select(long id) {
        if (preview) { // stop sound preview when detailed view closed.
            sound.playerClose();
            preview = false;
        }
        if (selected != id && selected != -1) {
            int pos = adapter.getPosition(selected);
            animator.notifyItemChanged(pos);
            adapter.notifyItemChanged(pos);
        }
        selected = id;
        if (id != -1) {
            int pos = adapter.getPosition(id);
            animator.notifyItemChanged(pos);
            adapter.notifyItemChanged(pos);
        }
    }

    void save(WeekSet a) {
    }

    public void remove(WeekSet a) {
        boxAnimate = false;
    }

    public void setWeek(WeekSet a, int week, boolean c) {
        a.setWeek(week, c);
    }

    public void previewCancel() {
        if (alarmRingtonePlay != null) {
            alarmRingtonePlay.clearAnimation();
            alarmRingtonePlay = null;
        }
        sound.vibrateStop();
        sound.playerClose();
        preview = false;
    }

    public Sound.Silenced playPreview(WeekSet a) {
        return SoundConfig.Silenced.NONE;
    }

    public void fillDetailed(final View view, final WeekSet w, final boolean animate) {
        final SwitchCompat enable = (SwitchCompat) view.findViewById(R.id.alarm_enable);
        enable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setEnable(w, enable.isChecked());
                save(w);
            }
        });
        enable.setChecked(w.getEnable());
        if (!animate)
            enable.jumpDrawablesToCurrentState();

        final CheckBox weekdays = (CheckBox) view.findViewById(R.id.alarm_week_days);
        LinearLayout weekdaysValues = (LinearLayout) view.findViewById(R.id.alarm_week);

        for (int i = 0; i < weekdaysValues.getChildCount(); i++) {
            final View c = weekdaysValues.getChildAt(i);
            if (c instanceof CheckBox) {
                final CheckBox child = (CheckBox) c;
                child.setText(getString(Week.DAYS[startweek]).substring(0, 1));
                final int week = Week.EVERYDAY[startweek];

                child.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setWeek(w, week, child.isChecked());
                        save(w);
                    }
                });
                child.setChecked(w.isWeek(week));
                startweek++;
                if (startweek >= Week.DAYS.length)
                    startweek = 0;
            }
        }
        weekdays.setChecked(w.weekdaysCheck);
        weekdays.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                w.weekdaysCheck = weekdays.isChecked();
                if (w.weekdaysCheck && w.noDays())
                    w.setEveryday();
                save(w);
            }
        });

        TextView weektext = (TextView) view.findViewById(R.id.alarm_week_text);
        weektext.setText("(" + w.formatDays() + ")");

        final CheckBox ringtone = (CheckBox) view.findViewById(R.id.alarm_ringtone);
        ringtone.setChecked(w.ringtone);
        if (ringtone.isChecked()) {
            TextView ringtoneValue = (TextView) view.findViewById(R.id.alarm_ringtone_value);
            String title = storage.getTitle(w.ringtoneValue);
            if (title == null)
                title = storage.getTitle(fallbackUri(null));
            if (title == null)
                title = "Built-in Tone Alarm"; // fall back, when here is no ringtones installed
            ringtoneValue.setText(title);
        }

        final CheckBox beep = (CheckBox) view.findViewById(R.id.alarm_beep);
        beep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                w.beep = beep.isChecked();
                save(w);
            }
        });
        beep.setChecked(w.beep);
        final CheckBox speech = (CheckBox) view.findViewById(R.id.alarm_speech);
        speech.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                w.speech = speech.isChecked();
                save(w);
            }
        });
        speech.setChecked(w.speech);

        final View alarmRingtonePlay = view.findViewById(R.id.alarm_ringtone_play);

        if (preview)
            previewCancel();

        ringtone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                w.ringtone = ringtone.isChecked();
                save(w);
                int pos = adapter.getPosition(w.id);
                animator.notifyItemChanged(pos);
                adapter.notifyItemChanged(pos);
            }
        });
        alarmRingtonePlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (preview) {
                    previewCancel();
                    return;
                }

                Sound.Silenced s = playPreview(w);

                if (s == Sound.Silenced.VIBRATE) { // we can stop vibrate by clicking on image
                    WeekSetFragment.this.preview = true;
                    WeekSetFragment.this.alarmRingtonePlay = alarmRingtonePlay;
                    return;
                }
                if (s != Sound.Silenced.NONE) { // if not vibrating exit
                    return;
                }

                WeekSetFragment.this.preview = true;
                WeekSetFragment.this.alarmRingtonePlay = alarmRingtonePlay;

                Animation a = AnimationUtils.loadAnimation(getActivity(), R.anim.shake);
                alarmRingtonePlay.startAnimation(a);
            }
        });

        final View trash = view.findViewById(R.id.alarm_trash);
        trash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == DialogInterface.BUTTON_POSITIVE) {
                            remove(w);
                            selected = -1;
                            adapter.notifyItemRemoved(adapter.getPosition(w.id));
                        }
                    }
                };
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(R.string.are_you_sure).setPositiveButton(R.string.Yes, dialogClickListener)
                        .setNegativeButton(R.string.No, dialogClickListener).show();
            }
        });

        View rename = view.findViewById(R.id.alarm_rename);
        rename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final OpenFileDialog.EditTextDialog d = new OpenFileDialog.EditTextDialog(getContext());
                d.setTitle(R.string.filedialog_rename);
                if (w.name != null)
                    d.setText(w.name);
                d.setNeutralButton(R.string.default_tts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        w.name = "";
                        save(w);
                    }
                });
                d.setPositiveButton(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        w.name = d.getText();
                        save(w);
                    }
                });
                d.show();
            }
        });

        View ringtoneBrowse = view.findViewById(R.id.alarm_ringtone_browse);
        ringtoneBrowse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                choicer = new OpenChoicer(OpenFileDialog.DIALOG_TYPE.FILE_DIALOG, true) {
                    @Override
                    public void onResult(Uri uri, boolean tmp) {
                        if (tmp) {
                            File f = storage.storeRingtone(uri);
                            uri = Uri.fromFile(f);
                        }
                        SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
                        shared.edit().putString(HourlyApplication.PREFERENCE_LAST_PATH, uri.toString()).commit();
                        w.ringtoneValue = uri;
                        save(w);
                    }

                    @Override
                    public void onDismiss() {
                        choicer = null;
                    }
                };
                choicer.setPermissionsDialog(WeekSetFragment.this, Storage.PERMISSIONS_RO, RESULT_FILE);
                choicer.setStorageAccessFramework(WeekSetFragment.this, RESULT_FILE);

                Uri path = w.ringtoneValue;

                Uri fdef;
                String def = Uri.fromFile(new File(Environment.getExternalStorageDirectory().getPath())).toString();
                SharedPreferences shared = android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences(getActivity());
                String last = shared.getString(HourlyApplication.PREFERENCE_LAST_PATH, def);
                if (last.startsWith(ContentResolver.SCHEME_FILE)) {
                    fdef = Uri.parse(last);
                } else if (last.startsWith(ContentResolver.SCHEME_CONTENT)) {
                    fdef = Uri.parse(last);
                } else {
                    File f = new File(last);
                    fdef = Uri.fromFile(f);
                }

                String a = path.getAuthority();
                String s = path.getScheme();
                if (s.equals(ContentResolver.SCHEME_FILE)) {
                    File sound = new File(path.getPath());
                    while (!sound.exists()) {
                        sound = sound.getParentFile();
                        if (sound == null) {
                            path = fdef;
                        } else {
                            path = Uri.fromFile(sound);
                        }
                    }
                } else if (s.equals(ContentResolver.SCHEME_CONTENT) && !a.startsWith(Storage.SAF)) { // uri points to ringtone, use default
                    path = fdef;
                }

                choicer.show(path);
            }
        });

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boxAnimate = true;
                select(-1);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case RESULT_RINGTONE:
            case RESULT_FILE:
                if (choicer != null) // called twice? or mainactivity were recreated
                    choicer.onRequestPermissionsResult(permissions, grantResults);
                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RESULT_RINGTONE:
            case RESULT_FILE:
                if (choicer != null) // called twice? or mainactivity were recreated
                    choicer.onActivityResult(resultCode, data);
                break;
        }
    }

    public void setEnable(WeekSet a, boolean e) {
        a.setEnable(e);
    }

    public void fillCompact(final View view, final WeekSet a, boolean animate) {
        TextView time = (TextView) view.findViewById(R.id.alarm_time);
        time.setClickable(false);

        final SwitchCompat enable = (SwitchCompat) view.findViewById(R.id.alarm_enable);
        enable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setEnable(a, enable.isChecked());
                save(a);
            }
        });
        enable.setChecked(a.getEnable());
        if (!animate)
            enable.jumpDrawablesToCurrentState();

        TextView days = (TextView) view.findViewById(R.id.alarm_compact_first);
        days.setText(a.name == null || a.name.isEmpty() ? a.formatDays() : a.name);

        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boxAnimate = true;
                select(a.id);
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.unregisterOnSharedPreferenceChangeListener(this);

        if (sound != null) {
            sound.close();
            sound = null;
        }
    }
}
