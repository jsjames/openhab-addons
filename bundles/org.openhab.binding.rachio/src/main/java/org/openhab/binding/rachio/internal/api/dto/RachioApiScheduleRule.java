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

import java.util.Date;
import java.util.List;

import org.openhab.binding.rachio.internal.api.RachioId;

/**
 * The {@link RachioApiScheduleRule}
 *
 * @author Jeff James - Initial contribution
 */
class RachioApiScheduleRule {
    public RachioId.Schedule id;
    public List<RachioCloudScheduleRuleZone> zones;
    public List<String> scheduleJobTypes;
    public String summary;
    public boolean rainDelay;
    public boolean waterBudget;
    public String cycleSoakStatus;
    public Date startDate;
    public String name;
    public boolean enabled;
    public int totalDuration;
    public float weatherInteligenceSensitivity;
    public float seasonalAdjustement;
    public int cycles;
    public boolean cycleSoak;

    public static class RachioCloudScheduleRuleZone {
        public RachioId.Zone zoneId;
        public int zoneNumber;
        public int duration;
        public int sortOrder;
    }
}
