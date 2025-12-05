/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.binding.rachio.internal.utils;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * {@link ClientRateLimitManager } maintains a running average of requests and provides methods to check whether a
 * request should be throttled based on the priority.
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class ClientRateLimitManager {
    private final int numBuckets;
    private final long bucketSizeMillis;
    private int rateLimitCap;
    private int rateRemaining;
    private Instant rateResetTime = Instant.MAX;

    private long total;

    private int[] buckets;
    private long bucket0EndMillis = 0;

    public enum PRIORITY {
        VERY_LOW,
        LOW,
        MED,
        HI;
    }

    public ClientRateLimitManager(int numBuckets, Duration bucketSize) {
        this.numBuckets = numBuckets;
        this.bucketSizeMillis = bucketSize.toMillis();

        // bucket[0] is the "live" bucket for the most current period
        buckets = new int[numBuckets + 1];
    }

    public void updateRateLimit(int rateLimitCap, int rateRemaining, Instant rateResetTime) {
        this.rateLimitCap = rateLimitCap;
        this.rateRemaining = rateRemaining;
        this.rateResetTime = rateResetTime;

        logRequest();
    }

    public void logRequest() {
        long now = System.currentTimeMillis();

        if (bucket0EndMillis == 0) {
            bucket0EndMillis = now + bucketSizeMillis;
        }

        if (now >= bucket0EndMillis) {
            int shiftBuckets = (int) ((now - bucket0EndMillis) / bucketSizeMillis) + 1;
            shift(shiftBuckets);
            bucket0EndMillis = bucket0EndMillis + bucketSizeMillis * shiftBuckets;
        }

        buckets[0]++;
    }

    public boolean shouldThrottle(PRIORITY priority) {
        try {
            tryThrottle(priority);
        } catch (RateLimitThrottleException e) {
            return true;
        }
        return false;
    }

    public void tryThrottle(PRIORITY priority) throws RateLimitThrottleException {
        if (rateResetTime == Instant.MAX) {
            return;
        }

        double currentRate = total / ((numBuckets) * bucketSizeMillis); // requests / Milliseconds * 1000 * 60
        double budgetRate = rateRemaining / (rateResetTime.toEpochMilli() - System.currentTimeMillis());

        boolean throttle = switch (priority) {
            case HI -> (currentRate > budgetRate * 1.1);
            case MED -> (currentRate > budgetRate * .9);
            case LOW -> (currentRate > budgetRate * .8);
            default -> true;
        };
        if (throttle) {
            throw new RateLimitThrottleException(priority, budgetRate, currentRate);
        }
    }

    private void shift(int n) {
        if (n <= 0) {
            return;
        }

        // When shifting/totaling, reset "live" bucket[0] to 0 and don't include in total
        total = 0;
        for (int i = numBuckets - 1; i > 0; i--) {
            buckets[i] = ((i - n) >= 0) ? buckets[i - n] : 0;
            total += buckets[i];
        }
        buckets[0] = 0;
    }

    public static class RateLimitThrottleException extends Exception {
        private static final long serialVersionUID = 1L;

        public PRIORITY priority;
        public double budgetRate;
        public double currentRate;

        public RateLimitThrottleException(PRIORITY priority, double budgetRate, double currentRate) {
            super();

            this.priority = priority;
            this.budgetRate = budgetRate;
            this.currentRate = currentRate;
        }

        @Override
        public String toString() {
            final String message = "Throttling REST API call with priority %s (budgeted rate: %.1f vs. running average rate: %.1f";
            return String.format(message, priority.toString(), budgetRate, currentRate);
        }
    }
}
