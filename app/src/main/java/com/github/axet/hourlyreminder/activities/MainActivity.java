package com.github.axet.hourlyreminder.activities;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.AppCompatImageView;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;

import com.github.axet.androidlibrary.widgets.AppCompatSettingsThemeActivity;
import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.app.Toast;
import com.github.axet.hourlyreminder.fragments.AlarmsFragment;
import com.github.axet.hourlyreminder.fragments.RemindersFragment;
import com.github.axet.hourlyreminder.fragments.SettingsFragment;
import com.github.axet.hourlyreminder.services.AlarmService;

import java.util.HashMap;

public class MainActivity extends AppCompatSettingsThemeActivity implements DialogInterface.OnDismissListener {

    public static final String SHOW_REMINDERS_PAGE = MainActivity.class.getCanonicalName() + ".SHOW_REMINDERS_PAGE";
    public static final String SHOW_ALARMS_PAGE = MainActivity.class.getCanonicalName() + ".SHOW_ALARMS_PAGE";
    public static final String SHOW_SETTINGS_PAGE = MainActivity.class.getCanonicalName() + ".SHOW_SETTINGS_PAGE";

    private SectionsPagerAdapter mSectionsPagerAdapter;

    private ViewPager mViewPager;

    TimeSetReceiver reciver = new TimeSetReceiver();

    boolean is24Hours = false;
    boolean timeChanged = false;

    public static void startActivity(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        context.startActivity(intent);
    }

    public static void startActivity(Context context, String page) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(page);
        context.startActivity(intent);
    }

    public class TimeSetReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TimeSetReceiver.class.getSimpleName(), "TimeSetReceiver " + intent.getAction());
            if (intent.getAction().equals(Intent.ACTION_TIME_CHANGED)) {
                timeChanged = is24Hours != DateFormat.is24HourFormat(MainActivity.this);
            }
        }
    }

    public static class SettingsTabView extends AppCompatImageView {
        Drawable d;

        public SettingsTabView(Context context, TabLayout.Tab tab, ColorStateList colors) {
            super(context);

            d = ContextCompat.getDrawable(context, R.drawable.ic_more_vert_24dp);

            setImageDrawable(d);

            setColorFilter(colors.getDefaultColor());
        }

        void updateLayout() {
            ViewParent p = getParent();
            if (p != null && p instanceof LinearLayout) { // TabView extends LinearLayout
                LinearLayout l = (LinearLayout) p;
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) l.getLayoutParams();
                if (lp != null) {
                    lp.weight = 0;
                    lp.width = LinearLayout.LayoutParams.WRAP_CONTENT;

                    int left = l.getMeasuredHeight() / 2 - d.getIntrinsicWidth() / 2;
                    int right = left;
                    left -= l.getPaddingLeft();
                    right -= l.getPaddingRight();
                    if (left < 0)
                        left = 0;
                    if (right < 0)
                        right = 0;
                    setPadding(left, 0, right, 0);
                }
            }
        }
    }

    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        HashMap<Integer, Fragment> map = new HashMap<>();

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new RemindersFragment();
                case 1:
                    return new AlarmsFragment();
                case 2:
                    return new SettingsFragment();
                default:
                    throw new RuntimeException("bad page");
            }
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Object o = super.instantiateItem(container, position);
            map.put(position, (Fragment) o);
            return o;
        }

        @Override
        public int getCount() {
            return 3;
        }

        public Fragment getFragment(int pos) {
            return map.get(pos);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getResources().getString(R.string.HourlyReminders);
                case 1:
                    return getResources().getString(R.string.CustomAlarms);
                case 2:
                    return "⋮";
            }
            return null;
        }
    }

    @Override
    public int getAppTheme() {
        return HourlyApplication.getTheme(this, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar);
    }

    @Override
    public String getAppThemeKey() {
        return HourlyApplication.PREFERENCE_THEME;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        is24Hours = DateFormat.is24HourFormat(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        registerReceiver(reciver, filter);

//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        AppBarLayout appbar = (AppBarLayout) findViewById(R.id.appbar);

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
        TabLayout.Tab tab = tabLayout.getTabAt(2);
        SettingsTabView v = new SettingsTabView(this, tab, tabLayout.getTabTextColors());
        tab.setCustomView(v);
        v.updateLayout();

        AlarmService.start(this);

        Intent intent = getIntent();
        onNewIntent(intent);

        if (OptimizationPreferenceCompat.needKillWarning(this, HourlyApplication.PREFERENCE_NEXT)) {
            AlertDialog.Builder builder = OptimizationPreferenceCompat.buildKilledWarning(this, true);
            builder.show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String a = intent.getAction();
        if (a == null)
            return;
        if (a.equals(SHOW_REMINDERS_PAGE)) {
            mViewPager.setCurrentItem(0);
        }
        if (a.equals(SHOW_ALARMS_PAGE)) {
            mViewPager.setCurrentItem(1);
        }
        if (a.equals(SHOW_SETTINGS_PAGE)) {
            mViewPager.setCurrentItem(2);
        }
        if (intent.getBooleanExtra(AlarmService.ALARMINFO, false))
            Toast.makeText(this, getString(R.string.open_from_notificationbar_warning, getString(R.string.pref_alarm_title)), com.github.axet.androidlibrary.widgets.Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (reciver != null) {
            unregisterReceiver(reciver);
            reciver = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (timeChanged) {
            restartActivity();
        }
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        int i = mViewPager.getCurrentItem();
        Fragment f = mSectionsPagerAdapter.getFragment(i);
        if (f instanceof DialogInterface.OnDismissListener) {
            ((DialogInterface.OnDismissListener) f).onDismiss(dialogInterface);
        }
    }
}
