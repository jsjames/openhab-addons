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

import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.rachio.internal.api.RachioId;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;

/**
 * {@link RachioApiWebhook} store some relevant information from API events.
 *
 * @author Jeff James - Initial contribution
 */
public record RachioApiNotificationWebhook( //
        RachioId.NotificationWebhook id, //
        @SerializedName(value = "external_id", alternate = "externalId") String externalId, //
        String url, //
        @SerializedName(value = "event_types", alternate = "eventTypes") List<@NonNull String> eventTypes) {

    public static class GsonAdapter
            implements JsonDeserializer<RachioApiNotificationWebhook>, JsonSerializer<RachioApiNotificationWebhook> {
        @Override
        public RachioApiNotificationWebhook deserialize(JsonElement json, Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            RachioId.NotificationWebhook id = context.deserialize(jsonObject.get("id"),
                    RachioId.NotificationWebhook.class);
            String externalId = context.deserialize(jsonObject.get("externalId"), String.class);
            String url = context.deserialize(jsonObject.get("url"), String.class);

            JsonArray eventTypesArray = jsonObject.getAsJsonArray("eventTypes");
            List<?> eventTypes = context.deserialize(eventTypesArray, List.class);
            if (!(eventTypes.get(0) instanceof String)) {
                eventTypes = eventTypes.stream()
                        .map((e) -> ((JsonElement) e).getAsJsonObject().get("name").getAsString())
                        .collect(Collectors.toList());
            }

            @SuppressWarnings("unchecked")
            List<@NonNull String> eventTypesCasted = (List<@NonNull String>) eventTypes;
            return new RachioApiNotificationWebhook(id, externalId, url, eventTypesCasted);
        }

        public JsonElement serialize(RachioApiNotificationWebhook src, Type typeOfSrc,
                JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("id", context.serialize(src.id()));
            jsonObject.add("externalId", context.serialize(src.externalId()));
            jsonObject.add("url", context.serialize(src.url()));
            jsonObject.add("eventTypes", context.serialize(src.eventTypes()));
            return jsonObject;
        }
    }

    public static final RachioApiNotificationWebhook EMPTY = new RachioApiNotificationWebhook(null, null, null,
            List.of());
}
