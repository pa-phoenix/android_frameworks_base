/*
 * Copyright (C) 2016 The Android Open Source Project
 *
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

package com.android.internal.location.gnssmetrics;

import android.location.GnssStatus;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.connectivity.GpsBatteryStats;
import android.server.location.ServerLocationProtoEnums;
import android.text.format.DateUtils;
import android.util.Base64;
import android.util.Log;
import android.util.StatsLog;
import android.util.TimeUtils;

import com.android.internal.app.IBatteryStats;
import com.android.internal.location.nano.GnssLogsProto.GnssLog;
import com.android.internal.location.nano.GnssLogsProto.PowerMetrics;

import java.util.Arrays;

/**
 * GnssMetrics: Is used for logging GNSS metrics
 *
 * @hide
 */
public class GnssMetrics {

    private static final String TAG = GnssMetrics.class.getSimpleName();

    /* Constant which indicates GPS signal quality is as yet unknown */
    private static final int GPS_SIGNAL_QUALITY_UNKNOWN =
            ServerLocationProtoEnums.GPS_SIGNAL_QUALITY_UNKNOWN; // -1

    /* Constant which indicates GPS signal quality is poor */
    private static final int GPS_SIGNAL_QUALITY_POOR =
            ServerLocationProtoEnums.GPS_SIGNAL_QUALITY_POOR; // 0

    /* Constant which indicates GPS signal quality is good */
    private static final int GPS_SIGNAL_QUALITY_GOOD =
            ServerLocationProtoEnums.GPS_SIGNAL_QUALITY_GOOD; // 1

    /* Number of GPS signal quality levels */
    public static final int NUM_GPS_SIGNAL_QUALITY_LEVELS = GPS_SIGNAL_QUALITY_GOOD + 1;

    /** Default time between location fixes (in millisecs) */
    private static final int DEFAULT_TIME_BETWEEN_FIXES_MILLISECS = 1000;

    /* The time since boot when logging started */
    private String mLogStartInElapsedRealTime;

    /* GNSS power metrics */
    private GnssPowerMetrics mGnssPowerMetrics;

    /* A boolean array indicating whether the constellation types have been used in fix. */
    private boolean[] mConstellationTypes;
    /** Location failure statistics */
    private Statistics mLocationFailureStatistics;
    /** Time to first fix statistics */
    private Statistics mTimeToFirstFixSecStatistics;
    /** Position accuracy statistics */
    private Statistics mPositionAccuracyMeterStatistics;
    /** Top 4 average CN0 statistics */
    private Statistics mTopFourAverageCn0Statistics;

    public GnssMetrics(IBatteryStats stats) {
        mGnssPowerMetrics = new GnssPowerMetrics(stats);
        mLocationFailureStatistics = new Statistics();
        mTimeToFirstFixSecStatistics = new Statistics();
        mPositionAccuracyMeterStatistics = new Statistics();
        mTopFourAverageCn0Statistics = new Statistics();
        reset();
    }

    /**
     * Logs the status of a location report received from the HAL
     */
    public void logReceivedLocationStatus(boolean isSuccessful) {
        if (!isSuccessful) {
            mLocationFailureStatistics.addItem(1.0);
            return;
        }
        mLocationFailureStatistics.addItem(0.0);
    }

    /**
     * Logs missed reports
     */
    public void logMissedReports(int desiredTimeBetweenFixesMilliSeconds,
            int actualTimeBetweenFixesMilliSeconds) {
        int numReportMissed = (actualTimeBetweenFixesMilliSeconds / Math.max(
                DEFAULT_TIME_BETWEEN_FIXES_MILLISECS, desiredTimeBetweenFixesMilliSeconds)) - 1;
        if (numReportMissed > 0) {
            for (int i = 0; i < numReportMissed; i++) {
                mLocationFailureStatistics.addItem(1.0);
            }
        }
    }

    /**
     * Logs time to first fix
     */
    public void logTimeToFirstFixMilliSecs(int timeToFirstFixMilliSeconds) {
        mTimeToFirstFixSecStatistics.addItem((double) (timeToFirstFixMilliSeconds / 1000));
    }

    /**
     * Logs position accuracy
     */
    public void logPositionAccuracyMeters(float positionAccuracyMeters) {
        mPositionAccuracyMeterStatistics.addItem((double) positionAccuracyMeters);
    }

    /**
     * Logs CN0 when at least 4 SVs are available
     */
    public void logCn0(float[] cn0s, int numSv) {
        if (numSv == 0 || cn0s == null || cn0s.length == 0 || cn0s.length < numSv) {
            if (numSv == 0) {
                mGnssPowerMetrics.reportSignalQuality(null, 0);
            }
            return;
        }
        float[] cn0Array = Arrays.copyOf(cn0s, numSv);
        Arrays.sort(cn0Array);
        mGnssPowerMetrics.reportSignalQuality(cn0Array, numSv);
        if (numSv < 4) {
            return;
        }
        if (cn0Array[numSv - 4] > 0.0) {
            double top4AvgCn0 = 0.0;
            for (int i = numSv - 4; i < numSv; i++) {
                top4AvgCn0 += (double) cn0Array[i];
            }
            top4AvgCn0 /= 4;
            mTopFourAverageCn0Statistics.addItem(top4AvgCn0);
        }
    }

    /**
     * Logs that a constellation type has been observed.
     */
    public void logConstellationType(int constellationType) {
        if (constellationType >= mConstellationTypes.length) {
            Log.e(TAG, "Constellation type " + constellationType + " is not valid.");
            return;
        }
        mConstellationTypes[constellationType] = true;
    }

    /**
     * Dumps GNSS metrics as a proto string
     */
    public String dumpGnssMetricsAsProtoString() {
        GnssLog msg = new GnssLog();
        if (mLocationFailureStatistics.getCount() > 0) {
            msg.numLocationReportProcessed = mLocationFailureStatistics.getCount();
            msg.percentageLocationFailure = (int) (100.0 * mLocationFailureStatistics.getMean());
        }
        if (mTimeToFirstFixSecStatistics.getCount() > 0) {
            msg.numTimeToFirstFixProcessed = mTimeToFirstFixSecStatistics.getCount();
            msg.meanTimeToFirstFixSecs = (int) mTimeToFirstFixSecStatistics.getMean();
            msg.standardDeviationTimeToFirstFixSecs =
                    (int) mTimeToFirstFixSecStatistics.getStandardDeviation();
        }
        if (mPositionAccuracyMeterStatistics.getCount() > 0) {
            msg.numPositionAccuracyProcessed = mPositionAccuracyMeterStatistics.getCount();
            msg.meanPositionAccuracyMeters = (int) mPositionAccuracyMeterStatistics.getMean();
            msg.standardDeviationPositionAccuracyMeters =
                    (int) mPositionAccuracyMeterStatistics.getStandardDeviation();
        }
        if (mTopFourAverageCn0Statistics.getCount() > 0) {
            msg.numTopFourAverageCn0Processed = mTopFourAverageCn0Statistics.getCount();
            msg.meanTopFourAverageCn0DbHz = mTopFourAverageCn0Statistics.getMean();
            msg.standardDeviationTopFourAverageCn0DbHz =
                    mTopFourAverageCn0Statistics.getStandardDeviation();
        }
        msg.powerMetrics = mGnssPowerMetrics.buildProto();
        msg.hardwareRevision = SystemProperties.get("ro.boot.revision", "");
        String s = Base64.encodeToString(GnssLog.toByteArray(msg), Base64.DEFAULT);
        reset();
        return s;
    }

    /**
     * Dumps GNSS Metrics as text
     *
     * @return GNSS Metrics
     */
    public String dumpGnssMetricsAsText() {
        StringBuilder s = new StringBuilder();
        s.append("GNSS_KPI_START").append('\n');
        s.append("  KPI logging start time: ").append(mLogStartInElapsedRealTime).append("\n");
        s.append("  KPI logging end time: ");
        TimeUtils.formatDuration(SystemClock.elapsedRealtimeNanos() / 1000000L, s);
        s.append("\n");
        s.append("  Number of location reports: ").append(
                mLocationFailureStatistics.getCount()).append("\n");
        if (mLocationFailureStatistics.getCount() > 0) {
            s.append("  Percentage location failure: ").append(
                    100.0 * mLocationFailureStatistics.getMean()).append("\n");
        }
        s.append("  Number of TTFF reports: ").append(
                mTimeToFirstFixSecStatistics.getCount()).append("\n");
        if (mTimeToFirstFixSecStatistics.getCount() > 0) {
            s.append("  TTFF mean (sec): ").append(mTimeToFirstFixSecStatistics.getMean()).append(
                    "\n");
            s.append("  TTFF standard deviation (sec): ").append(
                    mTimeToFirstFixSecStatistics.getStandardDeviation()).append("\n");
        }
        s.append("  Number of position accuracy reports: ").append(
                mPositionAccuracyMeterStatistics.getCount()).append("\n");
        if (mPositionAccuracyMeterStatistics.getCount() > 0) {
            s.append("  Position accuracy mean (m): ").append(
                    mPositionAccuracyMeterStatistics.getMean()).append("\n");
            s.append("  Position accuracy standard deviation (m): ").append(
                    mPositionAccuracyMeterStatistics.getStandardDeviation()).append("\n");
        }
        s.append("  Number of CN0 reports: ").append(
                mTopFourAverageCn0Statistics.getCount()).append("\n");
        if (mTopFourAverageCn0Statistics.getCount() > 0) {
            s.append("  Top 4 Avg CN0 mean (dB-Hz): ").append(
                    mTopFourAverageCn0Statistics.getMean()).append("\n");
            s.append("  Top 4 Avg CN0 standard deviation (dB-Hz): ").append(
                    mTopFourAverageCn0Statistics.getStandardDeviation()).append("\n");
        }
        s.append("  Used-in-fix constellation types: ");
        for (int i = 0; i < mConstellationTypes.length; i++) {
            if (mConstellationTypes[i]) {
                s.append(GnssStatus.constellationTypeToString(i)).append(" ");
            }
        }
        s.append("\n");
        s.append("GNSS_KPI_END").append("\n");
        GpsBatteryStats stats = mGnssPowerMetrics.getGpsBatteryStats();
        if (stats != null) {
            s.append("Power Metrics").append("\n");
            s.append("  Time on battery (min): ").append(
                    stats.getLoggingDurationMs() / ((double) DateUtils.MINUTE_IN_MILLIS)).append(
                    "\n");
            long[] t = stats.getTimeInGpsSignalQualityLevel();
            if (t != null && t.length == NUM_GPS_SIGNAL_QUALITY_LEVELS) {
                s.append("  Amount of time (while on battery) Top 4 Avg CN0 > "
                        + GnssPowerMetrics.POOR_TOP_FOUR_AVG_CN0_THRESHOLD_DB_HZ
                        + " dB-Hz (min): ").append(
                        t[1] / ((double) DateUtils.MINUTE_IN_MILLIS)).append("\n");
                s.append("  Amount of time (while on battery) Top 4 Avg CN0 <= "
                        + GnssPowerMetrics.POOR_TOP_FOUR_AVG_CN0_THRESHOLD_DB_HZ
                        + " dB-Hz (min): ").append(
                        t[0] / ((double) DateUtils.MINUTE_IN_MILLIS)).append("\n");
            }
            s.append("  Energy consumed while on battery (mAh): ").append(
                    stats.getEnergyConsumedMaMs() / ((double) DateUtils.HOUR_IN_MILLIS)).append(
                    "\n");
        }
        s.append("Hardware Version: ").append(SystemProperties.get("ro.boot.revision", "")).append(
                "\n");
        return s.toString();
    }

    private void reset() {
        StringBuilder s = new StringBuilder();
        TimeUtils.formatDuration(SystemClock.elapsedRealtimeNanos() / 1000000L, s);
        mLogStartInElapsedRealTime = s.toString();
        mLocationFailureStatistics.reset();
        mTimeToFirstFixSecStatistics.reset();
        mPositionAccuracyMeterStatistics.reset();
        mTopFourAverageCn0Statistics.reset();
        resetConstellationTypes();
    }

    /** Resets {@link #mConstellationTypes} as an all-false boolean array. */
    public void resetConstellationTypes() {
        mConstellationTypes = new boolean[GnssStatus.CONSTELLATION_COUNT];
    }

    /** Class for storing statistics */
    private class Statistics {

        private int mCount;
        private double mSum;
        private double mSumSquare;

        /** Resets statistics */
        public void reset() {
            mCount = 0;
            mSum = 0.0;
            mSumSquare = 0.0;
        }

        /** Adds an item */
        public void addItem(double item) {
            mCount++;
            mSum += item;
            mSumSquare += item * item;
        }

        /** Returns number of items added */
        public int getCount() {
            return mCount;
        }

        /** Returns mean */
        public double getMean() {
            return mSum / mCount;
        }

        /** Returns standard deviation */
        public double getStandardDeviation() {
            double m = mSum / mCount;
            m = m * m;
            double v = mSumSquare / mCount;
            if (v > m) {
                return Math.sqrt(v - m);
            }
            return 0;
        }
    }

    /* Class for handling GNSS power related metrics */
    private class GnssPowerMetrics {

        /* Threshold for Top Four Average CN0 below which GNSS signal quality is declared poor */
        public static final double POOR_TOP_FOUR_AVG_CN0_THRESHOLD_DB_HZ = 20.0;

        /* Minimum change in Top Four Average CN0 needed to trigger a report */
        private static final double REPORTING_THRESHOLD_DB_HZ = 1.0;

        /* BatteryStats API */
        private final IBatteryStats mBatteryStats;

        /* Last reported Top Four Average CN0 */
        private double mLastAverageCn0;

        /* Last reported signal quality bin (based on Top Four Average CN0) */
        private int mLastSignalLevel;

        private GnssPowerMetrics(IBatteryStats stats) {
            mBatteryStats = stats;
            // Used to initialize the variable to a very small value (unachievable in practice)
          // so that
            // the first CNO report will trigger an update to BatteryStats
            mLastAverageCn0 = -100.0;
            mLastSignalLevel = GPS_SIGNAL_QUALITY_UNKNOWN;
        }

        /**
         * Builds power metrics proto buf. This is included in the gnss proto buf.
         *
         * @return PowerMetrics
         */
        public PowerMetrics buildProto() {
            PowerMetrics p = new PowerMetrics();
            GpsBatteryStats stats = mGnssPowerMetrics.getGpsBatteryStats();
            if (stats != null) {
                p.loggingDurationMs = stats.getLoggingDurationMs();
                p.energyConsumedMah =
                        stats.getEnergyConsumedMaMs() / ((double) DateUtils.HOUR_IN_MILLIS);
                long[] t = stats.getTimeInGpsSignalQualityLevel();
                p.timeInSignalQualityLevelMs = new long[t.length];
                for (int i = 0; i < t.length; i++) {
                    p.timeInSignalQualityLevelMs[i] = t[i];
                }
            }
            return p;
        }

        /**
         * Returns the GPS power stats
         *
         * @return GpsBatteryStats
         */
        public GpsBatteryStats getGpsBatteryStats() {
            try {
                return mBatteryStats.getGpsBatteryStats();
            } catch (Exception e) {
                Log.w(TAG, "Exception", e);
                return null;
            }
        }

        /**
         * Reports signal quality to BatteryStats. Signal quality is based on Top four average CN0.
         * If
         * the number of SVs seen is less than 4, then signal quality is the average CN0.
         * Changes are reported only if the average CN0 changes by more than
         * REPORTING_THRESHOLD_DB_HZ.
         */
        public void reportSignalQuality(float[] ascendingCN0Array, int numSv) {
            double avgCn0 = 0.0;
            if (numSv > 0) {
                for (int i = Math.max(0, numSv - 4); i < numSv; i++) {
                    avgCn0 += (double) ascendingCN0Array[i];
                }
                avgCn0 /= Math.min(numSv, 4);
            }
            if (Math.abs(avgCn0 - mLastAverageCn0) < REPORTING_THRESHOLD_DB_HZ) {
                return;
            }
            int signalLevel = getSignalLevel(avgCn0);
            if (signalLevel != mLastSignalLevel) {
                StatsLog.write(StatsLog.GPS_SIGNAL_QUALITY_CHANGED, signalLevel);
                mLastSignalLevel = signalLevel;
            }
            try {
                mBatteryStats.noteGpsSignalQuality(signalLevel);
                mLastAverageCn0 = avgCn0;
            } catch (Exception e) {
                Log.w(TAG, "Exception", e);
            }
        }

        /**
         * Obtains signal level based on CN0
         */
        private int getSignalLevel(double cn0) {
            if (cn0 > POOR_TOP_FOUR_AVG_CN0_THRESHOLD_DB_HZ) {
                return GnssMetrics.GPS_SIGNAL_QUALITY_GOOD;
            }
            return GnssMetrics.GPS_SIGNAL_QUALITY_POOR;
        }
    }
}
