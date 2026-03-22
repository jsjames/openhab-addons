/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.rachio.internal.api.dto;

import java.util.Date;

import org.openhab.binding.rachio.internal.api.RachioId;

/**
 * {@link RachioApiCurrentSchedule}
 *
 * @author Jeff James - Initial contribution
 */
public record RachioApiCurrentSchedule( //
        RachioId.Device deviceId, //
        RachioId.Schedule scheduleId, //
        String type, //
        String status, //
        Date startDate, //
        int duration, //
        RachioId.Zone zoneId, //
        Date zoneStartDate, //
        int zoneDuration, //
        int cycleCount, //
        int totalCyclingCount, //
        boolean cycling, //
        int durationNoCycle) {
    public static final RachioApiCurrentSchedule EMPTY = new RachioApiCurrentSchedule(null, null, null, null, null, 0,
            null, null, 0, 0, 0, false, 0);
}
