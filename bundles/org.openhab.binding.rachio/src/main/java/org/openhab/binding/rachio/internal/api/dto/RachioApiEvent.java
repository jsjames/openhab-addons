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
import java.util.Map;

import org.openhab.binding.rachio.internal.api.RachioId;

/**
 * {@link RachioApiEvent} maps the API result into a Java object (using GSon).
 *
 * @author Markus Michels - Initial contribution
 */
public record RachioApiEvent( //
        String externalId, //
        String routingId, //
        String connectId, //
        String correlationId, //
        String scheduleId, //
        String deviceId, //
        String zoneId, //
        RachioId.Event id, //

        String timeZone, //
        String timestamp, //
        String timeForSummary, //
        String startTime, //
        String endTime, //

        Date eventDate, //
        Date createDate, //
        Date lastUpdateDate, //
        int sequence, //
        String status, //

        String type, //
        String subType, //
        String eventType, //
        String category, //
        String topic, //
        String action, //
        String summary, //
        String description, //
        String title, //
        String pushTitle, //

        String icon, //
        String iconUrl, //

        Integer zoneNumber, //
        String zoneName, //
        Integer zoneCurrent, //
        String zoneRunState, //
        Integer duration, //
        Integer durationInMinutes, //
        Integer flowVolume, //
        RachioZoneStatus zoneRunStatus, //

        String scheduleName, //
        String scheduleType, //

        String deviceName, //
        String pin, //

        Map<String, String> eventParms, //
        Map<String, RachioEventProperty> deltaProperties) {
    public static record RachioZoneStatus( //
            Integer duration, //
            String scheduleType, //
            Integer zoneNumber, //
            String executionType, //
            String state, //
            String startTime, //
            String endTime) {
    }

    public static record RachioEventProperty( //
            String propertyName, //
            String oldValue, //
            String newValue) {
    }
}
