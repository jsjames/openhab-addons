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

import org.openhab.binding.rachio.internal.api.RachioId;

/**
 * {@link RachioApiCurrentSchedule}
 *
 * @author Jeff James - Initial contribution
 */
public class RachioApiCurrentSchedule {
    public RachioId.Device deviceId;
    public RachioId.Schedule scheduleId;
    public String type;
    public String status;
    public Date startDate;
    public int duration;
    public RachioId.Zone zoneId;
    public Date zoneStartDate;
    public int zoneDuration;
    public int cycleCount;
    public int totalCyclingCount;
    public boolean cycling;
    public int durationNoCycle;

    static final public RachioApiCurrentSchedule EMPTY = new RachioApiCurrentSchedule();
}
