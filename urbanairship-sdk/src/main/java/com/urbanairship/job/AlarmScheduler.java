/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import com.urbanairship.AirshipService;
import com.urbanairship.Logger;

/**
 * Alarm based job scheduler. Only supports {@link JobInfo#getInitialDelay()}.
 */
class AlarmScheduler implements Scheduler {

    /**
     * The default starting back off time for retries in milliseconds
     */
    private static final long DEFAULT_STARTING_BACK_OFF_TIME_MS = 10000; // 10 seconds.

    /**
     * The default max back off time for retries in milliseconds.
     */
    private static final long DEFAULT_MAX_BACK_OFF_TIME_MS = 5120000; // About 85 mins.

    /**
     * Extra to track back off in seconds.
     */
    private static final String EXTRA_BACKOFF_DELAY = "EXTRA_BACKOFF_DELAY";

    @Override
    public void cancel(@NonNull Context context, @NonNull String tag) {
        Intent intent = new Intent(context, AirshipService.class)
                .setAction(AirshipService.ACTION_RUN_JOB)
                .addCategory(tag);

        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) context
                    .getSystemService(Context.ALARM_SERVICE);

            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    @Override
    public void schedule(@NonNull Context context, @NonNull JobInfo jobInfo) throws SchedulerException {
        long delay = jobInfo.getInitialDelay();
        if (delay <= 0) {
            delay = DEFAULT_STARTING_BACK_OFF_TIME_MS;
        }
        scheduleIntent(context, jobInfo, delay);
    }

    @Override
    public boolean requiresScheduling(@NonNull Context context, @NonNull JobInfo jobInfo) {
        return jobInfo.getInitialDelay() > 0;
    }

    @Override
    public void reschedule(@NonNull Context context, @NonNull JobInfo jobInfo) throws SchedulerException {
        long backOff = jobInfo.getSchedulerExtras().getLong(EXTRA_BACKOFF_DELAY, 0);
        if (backOff <= 0) {
            backOff = DEFAULT_STARTING_BACK_OFF_TIME_MS;
        } else {
            backOff = Math.min(backOff * 2, DEFAULT_MAX_BACK_OFF_TIME_MS);
        }

        jobInfo.getSchedulerExtras().putLong(EXTRA_BACKOFF_DELAY, backOff);
        scheduleIntent(context, jobInfo, backOff);
    }

    /**
     * Helper method to schedule alarms.
     *
     * @param context The application context.
     * @param jobInfo The jobInfo to schedule.
     * @param delay The alarm delay in milliseconds.
     *
     * @throws SchedulerException if the schedule fails.
     */
    private void scheduleIntent(@NonNull Context context, @NonNull JobInfo jobInfo, long delay) throws SchedulerException {
        Intent intent = AirshipService.createIntent(context, jobInfo)
                                      .addCategory(jobInfo.getTag());

        // Schedule the intent
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            Logger.verbose("AlarmScheduler - Scheduling jobInfo: " + jobInfo + " with delay: " + delay);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, pendingIntent);
        } catch (RuntimeException e) {

            // Some Samsung will throw a security exception if more than 500 alarms are being used. This usually
            // happens if the app or another library scheduling alarms with FLAG_CANCEL_CURRENT instead of
            // FLAG_UPDATE_CURRENT

            Logger.error("AlarmScheduler - Failed to schedule intent " + intent.getAction(), e);
            throw new SchedulerException("AlarmScheduler - Failed to schedule intent " + intent.getAction(), e);
        }
    }
}