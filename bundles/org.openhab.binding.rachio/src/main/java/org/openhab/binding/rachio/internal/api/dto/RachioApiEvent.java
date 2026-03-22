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

import java.lang.reflect.Type;
import java.util.Date;
import java.util.Map;

import org.openhab.binding.rachio.internal.api.RachioId;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

/**
 * {@link RachioApiEvent} DTO for Rachio API events.
 *
 * @author Jeff James - Initial contribution
 */
public record RachioApiEvent( //
        @SerializedName("eventId") RachioId.Event id, //
        @SerializedName("eventType") String type, //
        String externalId, //
        RachioId.Id resourceId, //
        Date timestamp, //
        Payload payload //
) {

    // Event type constants
    public static final String DEVICE_ZONE_RUN_STARTED_EVENT = "DEVICE_ZONE_RUN_STARTED_EVENT";
    public static final String DEVICE_ZONE_RUN_COMPLETED_EVENT = "DEVICE_ZONE_RUN_COMPLETED_EVENT";
    public static final String DEVICE_ZONE_RUN_STOPPED_EVENT = "DEVICE_ZONE_RUN_STOPPED_EVENT";
    public static final String DEVICE_ZONE_RUN_PAUSED_EVENT = "DEVICE_ZONE_RUN_PAUSED_EVENT";
    public static final String SCHEDULE_STARTED_EVENT = "SCHEDULE_STARTED_EVENT";
    public static final String SCHEDULE_COMPLETED_EVENT = "SCHEDULE_COMPLETED_EVENT";
    public static final String SCHEDULE_STOPPED_EVENT = "SCHEDULE_STOPPED_EVENT";
    public static final String CLIMATE_SKIP_NOTIFICATION_EVENT = "CLIMATE_SKIP_NOTIFICATION_EVENT";
    public static final String FREEZE_SKIP_NOTIFICATION_EVENT = "FREEZE_SKIP_NOTIFICATION_EVENT";
    public static final String WIND_SKIP_NOTIFICATION_EVENT = "WIND_SKIP_NOTIFICATION_EVENT";
    public static final String RAIN_SKIP_NOTIFICATION_EVENT = "RAIN_SKIP_NOTIFICATION_EVENT";
    public static final String NO_SKIP_NOTIFICATION_EVENT = "NO_SKIP_NOTIFICATION_EVENT";
    public static final String PROGRAM_RAIN_SKIP_CREATED_EVENT = "PROGRAM_RAIN_SKIP_CREATED_EVENT";
    public static final String PROGRAM_RAIN_SKIP_CANCELLED_EVENT = "PROGRAM_RAIN_SKIP_CANCELLED_EVENT";
    public static final String VALVE_RUN_START_EVENT = "VALVE_RUN_START_EVENT";
    public static final String VALVE_RUN_END_EVENT = "VALVE_RUN_END_EVENT";
    public interface Payload {
        // Marker interface for payloads
    }

    public record PayloadDeviceZoneRun( //
            long durationSeconds, // the duration in seconds this zone has been watering
            Date startTime, // the timestamp this zone will begin watering again
            Date endTime, // the timestamp the zone will complete watering after it resumes
            String runType, // MANUAL or SCHEDULED depending on whether this zone started due to a manual command or a
                            // schedule
            int zoneNumber, // the number of the zone which has started
            double flowVolumeG // the current volume reading in gallons from a flow meter if one is configured and
                               // connected to the controller
    ) implements Payload {
    }

    public record PayloadSchedule(long durationSeconds, // the total time the schedule ran
            Date startTime, // the timestamp this schedule run completed
            Date endTime, // the timestamp when this schedule completed
            String runType, // MANUAL or SCHEDULED depending on whether this schedule run started due to a manual quick
                            // run
            RachioId.Schedule scheduleId // the ID of the schedule
    ) implements Payload {
    }

    public record PayloadClimateSkipNotification( //
            RachioId.Schedule scheduleId, // the ID of the schedule
            Date startTime // the timestamp when run will be scipped
    ) implements Payload {
    }

    public record PayloadFreezeSkipNotification( //
            RachioId.Schedule scheduleId, // the ID of the schedule
            Date startTime, // the timestamp when run will be skipped
            int tempC, // the temperature in celsius that triggered the skip
            int thresholdC // the configured threshold for skips in celsius
    ) implements Payload {
    }

    public record PayloadWindSkipNotificaiton( //
            RachioId.Schedule scheduleId, // the ID of the schedule
            Date startTime, // the timestamp when run will be skipped
            int windKph, // the wind kilometers per hour
            int thresholdKph // the configured threshold for skips in km per hour
    ) implements Payload {
    }

    public record PayloadRainSkipNotification( //
            RachioId.Schedule scheduleId, // the ID of the schedule
            Date startTime, // the timestamp when run will be skipped
            int observedMm, // the observed precipitation in millimeters
            int predictedMm, // the forecasted precipitation in millimeters
            int thresholdMm // the configured threshold for skips in millimeters
    ) implements Payload {
    }

    public record PayloadNoSkipNotification( //
            RachioId.Schedule scheduleId, // the ID of the schedule
            Date startTime // the timestamp when run which will not be skipped
    ) implements Payload {
    }

    public record PayloadProgramRainSkip( //
            Date startTime // the timestamp when run will be skipped
    ) implements Payload {
    }

    public record PayloadValveRun(long durationSeconds, // the total time the valve was open
            String endReason, // the reason the valve stopped running, either COMPLETED or STOPPED
            boolean flowDetected, // true if the valve had flow detected, false otherwise
            String runType, // the type of run that started this valve, either BUTTON_PRESS, QUICK_RUN, or PROGRAM
            RachioId.Program programId // the ID of the program that started this valve, if applicable
    ) implements Payload {
    }

    public static final RachioApiEvent EMPTY = new RachioApiEvent(null, null, null, null, null, null);

    public static class GsonAdapter implements JsonDeserializer<RachioApiEvent> {
        private static final Map<String, Class<? extends Payload>> PAYLOAD_TYPE_MAP = Map.ofEntries(
                Map.entry("DEVICE_ZONE_RUN_STARTED_EVENT", PayloadDeviceZoneRun.class),
                Map.entry("DEVICE_ZONE_RUN_COMPLETED_EVENT", PayloadDeviceZoneRun.class),
                Map.entry("DEVICE_ZONE_RUN_STOPPED_EVENT", PayloadDeviceZoneRun.class),
                Map.entry("DEVICE_ZONE_RUN_PAUSED_EVENT", PayloadDeviceZoneRun.class),
                Map.entry("SCHEDULE_STARTED_EVENT", PayloadSchedule.class),
                Map.entry("SCHEDULE_COMPLETED_EVENT", PayloadSchedule.class),
                Map.entry("SCHEDULE_STOPPED_EVENT", PayloadSchedule.class),
                Map.entry("CLIMATE_SKIP_NOTIFICATION_EVENT", PayloadClimateSkipNotification.class),
                Map.entry("FREEZE_SKIP_NOTIFICATION_EVENT", PayloadFreezeSkipNotification.class),
                Map.entry("WIND_SKIP_NOTIFICATION_EVENT", PayloadWindSkipNotificaiton.class),
                Map.entry("RAIN_SKIP_NOTIFICATION_EVENT", PayloadRainSkipNotification.class),
                Map.entry("NO_SKIP_NOTIFICATION_EVENT", PayloadNoSkipNotification.class),
                Map.entry("PROGRAM_RAIN_SKIP_CREATED_EVENT", PayloadProgramRainSkip.class),
                Map.entry("PROGRAM_RAIN_SKIP_CANCELLED_EVENT", PayloadProgramRainSkip.class),
                Map.entry("VALVE_RUN_START_EVENT", PayloadValveRun.class),
                Map.entry("VALVE_RUN_END_EVENT", PayloadValveRun.class));

        @Override
        public RachioApiEvent deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            // deserialze the common fields
            RachioId.Event eventId = context.deserialize(jsonObject.get("id"), Date.class);
            String eventType = context.deserialize(jsonObject.get("type"), String.class);
            String externalId = context.deserialize(jsonObject.get("externalId"), String.class);
            Date timestamp = context.deserialize(jsonObject.get("timestamp"), Date.class);

            // deserialize the resource ID into the specific ID type
            String resourceType = jsonObject.get("resourceType").getAsString();
            String idString = jsonObject.get("eventId").getAsString();
            RachioId.Id rachioId = RachioId.create(resourceType, idString);

            // deserialize the payload based on the event type
            if (jsonObject.has("payload")) {
                JsonElement payloadElement = jsonObject.get("payload");
                if (payloadElement.isJsonObject()) {
                    JsonObject payloadObject = payloadElement.getAsJsonObject();

                    Class<? extends Payload> payloadClass = PAYLOAD_TYPE_MAP.get(eventType);
                    if (payloadClass == null) {
                        return new RachioApiEvent(eventId, eventType, externalId, rachioId, timestamp, null);
                    }
                    Payload payload = context.deserialize(payloadObject, payloadClass);
                    return new RachioApiEvent(eventId, eventType, externalId, rachioId, timestamp, payload);
                }
            }

            return RachioApiEvent.EMPTY;
        }
    }
}
