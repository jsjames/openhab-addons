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
package org.openhab.binding.rachio.internal._old;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEvent;

import com.google.gson.Gson;

/**
 * {@link RachioEventStringDTO} store some relevant information from API events.
 *
 * @author Markus Michels - Initial contribution
 */
public class RachioEventStringDTO {
    @Nullable
    private GenericEvent gEvent;
    @Nullable
    private ZoneEvent zEvent;
    private Gson gson = new Gson();

    private class GenericEvent {
        private final String timetstamp;
        private final String summary;
        private final String topic;
        private final String type;
        private final String subType;

        public GenericEvent(RachioApiEvent event) {
            timetstamp = event.timestamp;
            summary = event.summary;
            topic = event.topic;
            type = event.type;
            subType = event.subType;
        }
    }

    private class ZoneEvent {
        private final String timestamp;
        private final String summary;
        private final String type;
        private final String subType;

        private final String zoneName;
        private final int zoneNumber;
        private final String zoneRunState;
        private final String scheduleType;
        private final String startTime;
        private final String endTime;
        private final int duration;

        public ZoneEvent(RachioApiEvent event) {
            timestamp = event.timestamp;
            summary = event.summary;
            type = event.type;
            subType = event.subType;

            zoneName = event.zoneName;
            zoneNumber = event.zoneNumber;
            zoneRunState = event.zoneRunState;
            scheduleType = event.zoneRunStatus.scheduleType;
            startTime = event.zoneRunStatus.startTime;
            endTime = event.zoneRunStatus.endTime;
            duration = event.duration;
        }
    }

    public RachioEventStringDTO(RachioApiEvent event) {
        if (event.type.equals("ZONE_STATUS")) {
            zEvent = new ZoneEvent(event);
        } else {
            gEvent = new GenericEvent(event);
        }
    }

    @Nullable
    public String toJson() {
        return zEvent != null ? gson.toJson(zEvent) : gson.toJson(gEvent);
    }
}
