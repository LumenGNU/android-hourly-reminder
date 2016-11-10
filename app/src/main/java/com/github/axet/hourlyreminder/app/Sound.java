package com.github.axet.hourlyreminder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.basics.Alarm;
import com.github.axet.hourlyreminder.basics.ReminderSet;
import com.github.axet.hourlyreminder.basics.WeekSet;
import com.github.axet.hourlyreminder.dialogs.BeepPrefDialogFragment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Sound extends TTS {
    public static final String TAG = Sound.class.getSimpleName();

    ToneGenerator tone;
    MediaPlayer player;
    AudioTrack track;
    Runnable increaseVolume;
    Runnable loop; // loop preventer

    public static class Playlist {
        public List<String> before = new ArrayList<>();
        public boolean beep;
        public boolean speech;
        public List<String> after = new ArrayList<>();

        public Playlist() {
        }

        public Playlist(ReminderSet rs) {
            merge(rs);
        }

        public void merge(ReminderSet rs) {
            this.beep |= rs.beep;
            this.speech |= rs.speech;
            if (rs.ringtone) {
                if (rs.beep || rs.speech) {
                    if (!before.contains(rs.ringtoneValue)) // do not add after, if same sound already played "before"
                        add(after, rs.ringtoneValue);
                } else {
                    after.remove(rs.ringtoneValue); // do not add after, if same sound already played "before"
                    add(before, rs.ringtoneValue);
                }
            }
        }

        void add(List<String> l, String s) {
            if (l.contains(s))
                return;
            l.add(s);
        }
    }

    public Sound(Context context) {
        super(context);
    }

    public void close() {
        super.close();

        playerClose();

        if (tone != null) {
            tone.release();
            tone = null;
        }
        if (track != null) {
            track.release();
            track = null;
        }
    }

    // https://gist.github.com/slightfoot/6330866
    public static AudioTrack generateTone(double freqHz, int durationMs) {
        int sampleRate = 44100;
        int count = sampleRate * durationMs / 1000;
        int end = count;
        int stereo = count * 2;
        short[] samples = new short[stereo];
        for (int i = 0; i < stereo; i += 2) {
            int si = i / 2;
            double sx = 2 * Math.PI * si / (sampleRate / freqHz);
            short sample = (short) (Math.sin(sx) * 0x7FFF);
            samples[i + 0] = sample;
            samples[i + 1] = sample;
        }
        // old phones bug.
        //
        // http://stackoverflow.com/questions/27602492
        //
        // with MODE_STATIC setNotificationMarkerPosition not called
        AudioTrack track = new AudioTrack(SOUND_STREAM, sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
                stereo * (Short.SIZE / 8), AudioTrack.MODE_STREAM);
        track.write(samples, 0, stereo);
        if (track.setNotificationMarkerPosition(end) != AudioTrack.SUCCESS)
            throw new RuntimeException("unable to set marker");
        return track;
    }

    public Silenced silencedReminder(Playlist rr) {
        Silenced ss = silenced();

        if (ss != Silenced.NONE)
            return ss;

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        boolean v = shared.getBoolean(HourlyApplication.PREFERENCE_VIBRATE, false);
        boolean c = !rr.after.isEmpty() || !rr.before.isEmpty();
        boolean s = rr.speech;
        boolean b = rr.beep;

        if (!v && !c && !s && !b)
            return Silenced.SETTINGS;

        if (v && !c && !s && !b)
            return Silenced.VIBRATE;

        return Silenced.NONE;
    }

    public Silenced silencedAlarm(WeekSet a) {
        Silenced ss = silenced();

        if (ss != Silenced.NONE)
            return ss;

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        boolean v = shared.getBoolean(HourlyApplication.PREFERENCE_VIBRATE, false);
        boolean c = a.ringtone;
        boolean s = a.speech;
        boolean b = a.beep;

        if (!v && !c && !s && !b)
            return Silenced.SETTINGS;

        if (v && !c && !s && !b)
            return Silenced.VIBRATE;

        return Silenced.NONE;
    }

    public Silenced silenced() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        if (shared.getBoolean(HourlyApplication.PREFERENCE_CALLSILENCE, false)) {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK) {
                return Silenced.CALL;
            }
        }

        if (shared.getBoolean(HourlyApplication.PREFERENCE_MUSICSILENCE, false)) {
            AudioManager tm = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (tm.isMusicActive()) {
                return Silenced.MUSIC;
            }
        }

        return Silenced.NONE;
    }

    public Silenced playList(final Playlist rr, final long time, final Runnable done) {
        playerClose();

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        Silenced s = silencedReminder(rr);

        // do we have slince alarm?
        if (s != Silenced.NONE) {
            if (s == Silenced.VIBRATE)
                vibrate();
            silencedToast(s, time);
            return s;
        }

        if (shared.getBoolean(HourlyApplication.PREFERENCE_VIBRATE, false)) {
            vibrate();
        }

        final Runnable after = new Runnable() {
            @Override
            public void run() {
                if (!rr.after.isEmpty()) {
                    playCustom(rr.after, done);
                } else {
                    if (done != null) {
                        done.run();
                    }
                }
            }
        };

        final Runnable speech = new Runnable() {
            @Override
            public void run() {
                if (rr.speech) {
                    playSpeech(time, after);
                } else {
                    after.run();
                }
            }
        };

        final Runnable beep = new Runnable() {
            @Override
            public void run() {
                if (rr.beep) {
                    playBeep(speech);
                } else {
                    speech.run();
                }
            }
        };

        final Runnable before = new Runnable() {
            @Override
            public void run() {
                if (!rr.before.isEmpty()) {
                    playCustom(rr.before, beep);
                } else {
                    beep.run();
                }
            }
        };

        before.run();
        return s;
    }

    public Silenced playReminder(final ReminderSet rs, final long time, final Runnable done) {
        return playList(new Playlist(rs), time, done);
    }

    public void playCustom(List<String> uu, final Runnable done) {
        playCustom(uu, 0, done);
    }

    public void playCustom(final List<String> uu, final int index, final Runnable done) {
        if (index >= uu.size()) {
            if (done != null)
                done.run();
            return;
        }

        playCustom(uu.get(index), new Runnable() {
            @Override
            public void run() {
                playCustom(uu, index + 1, done);
            }
        });
    }

    public void playCustom(String uri, final Runnable done) {
        if (!uri.isEmpty()) {
            playerCl();

            Sound.this.done.add(done);

            player = playOnce(Uri.parse(uri), new Runnable() {
                @Override
                public void run() {
                    if (done != null && Sound.this.done.contains(done))
                        done.run();
                }
            });
        } else {
            if (done != null)
                done.run();
        }
    }

    public void playBeep(final Runnable done) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String b = shared.getString(HourlyApplication.PREFERENCE_BEEP_CUSTOM, "1800:100");

        BeepPrefDialogFragment.BeepConfig beep = new BeepPrefDialogFragment.BeepConfig();
        beep.load(b);

        playBeep(generateTone(beep.value_f, beep.value_l), done);
    }

    public void playBeep(AudioTrack t, final Runnable done) {
        if (track != null)
            track.release();

        track = t;

        if (Build.VERSION.SDK_INT < 21) {
            track.setStereoVolume(getVolume(), getVolume());
        } else {
            track.setVolume(getVolume());
        }

        Sound.this.done.add(done);

        track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack t) {
                // prevent strange android bug, with second beep when connecting android to external usb audio source.
                // seems like this beep pushed to external audio source from sound cache.
                if (track != null) {
                    track.release();
                    track = null;
                }
                if (done != null && Sound.this.done.contains(done))
                    done.run();
            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {
            }
        });

        track.play();
    }

    // called from alarm
    public void playRingtone(Uri uri) {
        playerClose();

        player = create(uri);
        if (player == null) {
            player = create(Alarm.DEFAULT_ALARM);
        }
        if (player == null) {
            if (tone != null) {
                tone.release();
            }
            tone = new ToneGenerator(SOUND_STREAM, 100);
            tone.startTone(ToneGenerator.TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL);
            return;
        }
        player.setLooping(true);

        startPlayer(player);
    }

    public void startPlayer(final MediaPlayer player) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        final int inc = Integer.parseInt(shared.getString(HourlyApplication.PREFERENCE_INCREASE_VOLUME, "0")) * 1000;

        if (inc == 0) {
            player.setVolume(getVolume(), getVolume());
            player.start();

            return;
        }

        final float startVolume;

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        float systemVolume = am.getStreamMaxVolume(SOUND_STREAM) / (float) am.getStreamVolume(SOUND_STREAM);
        float alarmVolume = getVolume();

        // if user trying to reduce alarms volume, then use it as start volume. else start from silence
        if (systemVolume > alarmVolume)
            startVolume = alarmVolume;
        else
            startVolume = 0;

        if (increaseVolume != null)
            handler.removeCallbacks(increaseVolume);

        increaseVolume = new Runnable() {
            int step = 0;
            int steps = 50;
            int delay = 100;
            // we start from startVolume, rest - how much we should increase
            float rest = 0;

            {
                steps = (inc / delay);
                rest = 1f - startVolume;
            }

            @Override
            public void run() {
                if (player == null)
                    return;

                float log1 = (float) (Math.log(steps - step) / Math.log(steps));
                // volume 0..1
                float vol = 1 - log1;

                // actual volume
                float restvol = startVolume + rest * vol;

                try {
                    player.setVolume(restvol, restvol);
                } catch (IllegalStateException e) {
                    // ignore. player probably already closed
                    return;
                }

                step++;

                if (step >= steps) {
                    // should be clear anyway
                    handler.removeCallbacks(increaseVolume);
                    increaseVolume = null;
                    Log.d(TAG, "increaseVolume done");
                    return;
                }

                handler.postDelayed(increaseVolume, delay);
            }
        };

        increaseVolume.run();
        player.start();
    }

    public void timeToast(long time) {
        String text = context.getResources().getString(R.string.ToastTime, Alarm.format2412ap(context, time));
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    public void silencedToast(Silenced s, long time) {
        String text = "";
        switch (s) {
            case VIBRATE:
                text += context.getString(R.string.SoundSilencedVibrate);
                break;
            case CALL:
                text += context.getString(R.string.SoundSilencedCall);
                break;
            case MUSIC:
                text += context.getString(R.string.SoundSilencedMusic);
                break;
            case SETTINGS:
                text += context.getString(R.string.SoundSilencedSettings);
                break;
        }
        text += "\n";
        text += context.getResources().getString(R.string.ToastTime, Alarm.format2412ap(context, time));

        Toast t = Toast.makeText(context, text.trim(), Toast.LENGTH_SHORT);
        TextView v = (TextView) t.getView().findViewById(android.R.id.message);
        if (v != null)
            v.setGravity(Gravity.CENTER);
        t.show();
    }

    MediaPlayer create(Uri uri) {
        if (Build.VERSION.SDK_INT >= 21) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(SOUND_CHANNEL)
                    .setContentType(SOUND_TYPE)
                    .build();

            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            int audioSessionId = am.generateAudioSessionId();

            try {
                MediaPlayer mp = new MediaPlayer();
                final AudioAttributes aa = audioAttributes != null ? audioAttributes : new AudioAttributes.Builder().build();
                mp.setAudioAttributes(aa);
                mp.setAudioSessionId(audioSessionId);
                mp.setAudioStreamType(SOUND_STREAM);
                mp.setDataSource(context, uri);
                mp.prepare();
                return mp;
            } catch (IOException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            } catch (SecurityException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            }
            return null;
        } else {
            try {
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(context, uri);
                mp.setAudioStreamType(SOUND_STREAM);
                mp.prepare();
                return mp;
            } catch (IOException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            } catch (IllegalArgumentException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            } catch (SecurityException ex) {
                Log.d(TAG, "create failed:", ex);
                // fall through
            }
            return null;
        }
    }

    // called from reminder or test sound button
    public MediaPlayer playOnce(Uri uri, final Runnable done) {
        MediaPlayer player = create(uri);
        if (player == null) {
            player = create(ReminderSet.DEFAULT_NOTIFICATION);
        }
        if (player == null) {
            Toast.makeText(context, context.getString(R.string.NoDefaultRingtone), Toast.LENGTH_SHORT).show();
            if (done != null)
                done.run();
            return null;
        }

        // https://code.google.com/p/android/issues/detail?id=1314
        player.setLooping(false);

        final MediaPlayer p = player;
        loop = new Runnable() {
            int last = 0;

            @Override
            public void run() {
                int pos = p.getCurrentPosition();
                if (pos < last) {
                    if (done != null && Sound.this.done.contains(done))
                        done.run();
                    return;
                }
                last = pos;
                handler.postDelayed(loop, 200);
            }
        };
        loop.run();

        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                           @Override
                                           public void onCompletion(MediaPlayer mp) {
                                               if (done != null)
                                                   done.run();
                                           }
                                       }
        );

        startPlayer(player);

        return player;
    }

    public void vibrate() {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(400);
    }

    public void vibrateStart() {
        long[] pattern = {0, 1000, 300};
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(pattern, 0);
    }

    public void vibrateStop() {
        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        v.cancel();
    }

    void playerCl() {
        if (increaseVolume != null) {
            handler.removeCallbacks(increaseVolume);
            increaseVolume = null;
        }

        if (loop != null) {
            handler.removeCallbacks(loop);
            loop = null;
        }

        if (player != null) {
            player.release();
            player = null;
        }
    }

    public void playerClose() {
        playerCl();
        done.clear();
    }

    public Silenced playAlarm(final Alarm a) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);

        Silenced s = silencedAlarm(a);

        final long time = System.currentTimeMillis(); // show/speak current time

        // do not show toast, sice we will fire Alarm Activity
        // silencedToast(s, time);

        if (s == Silenced.VIBRATE) {
            vibrateStart();
            return s;
        }

        if (s != Silenced.NONE)
            return s;

        if (shared.getBoolean(HourlyApplication.PREFERENCE_VIBRATE, false)) {
            vibrateStart();
        }

        if (a.beep) {
            playBeep(new Runnable() {
                         @Override
                         public void run() {
                             if (a.speech) {
                                 playSpeech(time, new Runnable() {
                                     @Override
                                     public void run() {
                                         if (a.ringtone) {
                                             playRingtone(Uri.parse(a.ringtoneValue));
                                         }
                                     }
                                 });
                             } else if (a.ringtone) {
                                 playRingtone(Uri.parse(a.ringtoneValue));
                             }
                         }
                     }
            );
        } else if (a.speech) {
            playSpeech(time, new Runnable() {
                @Override
                public void run() {
                    playRingtone(Uri.parse(a.ringtoneValue));
                }
            });
        } else if (a.ringtone) {
            playRingtone(Uri.parse(a.ringtoneValue));
        }

        return s;
    }
}
