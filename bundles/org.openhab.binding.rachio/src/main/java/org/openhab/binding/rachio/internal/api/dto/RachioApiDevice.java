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
import java.util.Map;

import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.utils.Id;

/**
 * The {@link RachioApiDevice}
 *
 * @author Jeff James - Initial contribution
 */
public class RachioApiDevice implements Id<RachioId.Device> {
    public RachioId.Device id;
    public Date createDate;
    public String name;
    public String status;
    public String model;
    public Map<RachioId.Zone, RachioApiZone> zones;
    public double latitude;
    public double longitude;
    public List<RachioApiScheduleRule> scheduleRules;
    public String serialNumber;
    public Date rainDelayExpirationDate;
    public Date rainDelayStartDate;
    public String macAddress;
    // webhooks
    public boolean on;
    public List<RachioApiScheduleRule> flexScheduleRules;
    public long utcOffset;
    public boolean rainSensorTripped;

    @Override
    public RachioId.Device getId() {
        return id;
    }

    public static final RachioApiDevice EMPTY = new RachioApiDevice();
}
