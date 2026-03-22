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
import java.util.List;

import org.openhab.binding.rachio.internal.api.RachioId;

/**
 * The {@link RachioApiScheduleRule}
 *
 * @author Jeff James - Initial contribution
 */
public record RachioApiScheduleRule( //
        RachioId.Schedule id, //
        List<RachioCloudScheduleRuleZone> zones, //
        List<String> scheduleJobTypes, //
        String summary, //
        boolean rainDelay, //
        boolean waterBudget, //
        String cycleSoakStatus, //
        Date startDate, //
        String name, //
        boolean enabled, //
        int totalDuration, //
        float weatherInteligenceSensitivity, //
        float seasonalAdjustement, //
        int cycles, //
        boolean cycleSoak) {
    public record RachioCloudScheduleRuleZone( //
            RachioId.Zone zoneId, //
            int zoneNumber, //
            int duration, //
            int sortOrder) {
    }
}
