package com.github.axet.hourlyreminder.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.hourlyreminder.R;
import com.github.axet.hourlyreminder.app.HourlyApplication;
import com.github.axet.hourlyreminder.fragments.AlarmsFragment;
import com.github.axet.hourlyreminder.fragments.RemindersFragment;
import com.github.axet.hourlyreminder.fragments.SettingsFragment;
import com.github.axet.hourlyreminder.services.AlarmService;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, DialogInterface.OnDismissListener {
    // MainActivity action
    public static final String SHOW_ALARMS_PAGE = MainActivity.class.getCanonicalName() + ".SHOW_ALARMS_PAGE";
    public static final String SHOW_SETTINGS_PAGE = MainActivity.class.getCanonicalName() + ".SHOW_SETTINGS_PAGE";

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    TimeSetReceiver reciver = new TimeSetReceiver();

    boolean is24Hours = false;
    boolean timeChanged = false;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(HourlyApplication.getTheme(this, R.style.AppThemeLight_NoActionBar, R.style.AppThemeDark_NoActionBar));
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
        ViewCompat.setBackground(appbar, new ColorDrawable(HourlyApplication.getActionbarColor(this)));

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
        TabLayout.Tab tab = tabLayout.getTabAt(2);
        SettingsTabView v = new SettingsTabView(this, tab, tabLayout.getTabTextColors());
        tab.setCustomView(v);
        v.updateLayout();

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                ((HourlyApplication) getApplicationContext()).soundReminder();
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });

        AlarmService.start(this);

        Intent intent = getIntent();
        String a = intent.getAction();
        if (a != null && a.equals(SHOW_ALARMS_PAGE)) {
            mViewPager.setCurrentItem(1);
        }
        if (a != null && a.equals(SHOW_SETTINGS_PAGE)) {
            mViewPager.setCurrentItem(2);
        }

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        shared.registerOnSharedPreferenceChangeListener(this);

        if (OptimizationPreferenceCompat.needWarning(this)) {
            OptimizationPreferenceCompat.showWarning(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main, menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar base clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
//            startActivity(new Intent(this, SettingsActivity.class));
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

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        shared.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(HourlyApplication.PREFERENCE_THEME)) {
            finish();
            startActivity(new Intent(this, MainActivity.class).setAction(SHOW_SETTINGS_PAGE));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
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
    protected void onResume() {
        super.onResume();
        if (timeChanged) {
            finish();
            startActivity(new Intent(MainActivity.this, MainActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
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
