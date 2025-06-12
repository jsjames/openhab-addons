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

package org.openhab.binding.rachio.internal.api.dto;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.rachio.internal.api.RachioId;

/**
 * The {@link RachioApiDevice}
 *
 * @author Jeff James - Initial contribution
 */
public record RachioApiDevice( //
        RachioId.Device id, //
        Date createDate, //
        String name, //
        String status, //
        String model, //
        @NonNull Map<RachioId.Zone, RachioApiZone> zones, //
        double latitude, //
        double longitude, //
        @NonNull Map<RachioId.Schedule, RachioApiScheduleRule> scheduleRules, //
        String serialNumber, //
        Date rainDelayExpirationDate, //
        Date rainDelayStartDate, //
        String macAddress, //
        boolean on, //
        List<RachioApiScheduleRule> flexScheduleRules, //
        long utcOffset, //
        boolean rainSensorTripped) {
    public static final RachioApiDevice EMPTY = new RachioApiDevice(null, null, null, null, null,
            Collections.<RachioId.Zone, RachioApiZone> emptyMap(), 0.0, 0.0,
            Collections.<RachioId.Schedule, RachioApiScheduleRule> emptyMap(), null, null, null, null, false, null, 0L,
            false);
}
