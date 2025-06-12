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

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.rachio.internal.api.RachioId;

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
public record RachioApiWebhook( //
        RachioId.Webhook id, //
        @SerializedName(value = "external_id", alternate = "externalId") String externalId, //
        @SerializedName(value = "resource_id", alternate = "resourceId") RachioId.Id resourceId, //
        String url, //
        @SerializedName(value = "event_types", alternate = "eventTypes") List<@NonNull String> eventTypes) {

    public static class GsonAdapter implements JsonDeserializer<RachioApiWebhook>, JsonSerializer<RachioApiWebhook> {
        @Override
        public RachioApiWebhook deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            RachioId.Webhook id = context.deserialize(jsonObject.get("id"), RachioId.Webhook.class);
            String externalId = context.deserialize(jsonObject.get("externalId"), String.class);
            String url = context.deserialize(jsonObject.get("url"), String.class);
            List<String> eventTypes = context.deserialize(jsonObject.get("eventTypes"), List.class);

            JsonObject resourceIdObject = jsonObject.getAsJsonObject("resourceId");

            if (resourceIdObject == null) {
                throw new JsonParseException("Resource ID is missing or null");
            }

            // convert separate parameters of resourceId field to a RachioId.Id type
            RachioId.Id resourceId = null;
            if (resourceIdObject.has("valve_id")) {
                resourceId = context.deserialize(resourceIdObject.get("valve_id"), RachioId.Valve.class);
            } else if (resourceIdObject.has("irrigation_controller_id")) {
                resourceId = context.deserialize(resourceIdObject.get("irrigation_controller_id"),
                        RachioId.Device.class);
            } else if (resourceIdObject.has("program_id")) {
                resourceId = context.deserialize(resourceIdObject.get("program_id"), RachioId.Program.class);
            }

            return new RachioApiWebhook(id, externalId, resourceId, url, eventTypes);
        }

        public JsonElement serialize(RachioApiWebhook src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("id", context.serialize(src.id()));
            jsonObject.add("externalId", context.serialize(src.externalId()));
            jsonObject.add("url", context.serialize(src.url()));
            jsonObject.add("eventTypes", context.serialize(src.eventTypes()));

            // convert RachioId.Id type to separate parameters of resourceId field
            JsonObject resourceIdObject = new JsonObject();
            if (src.resourceId() instanceof RachioId.Valve valveId) {
                resourceIdObject.add("valve_id", context.serialize(valveId));
            } else if (src.resourceId() instanceof RachioId.Device deviceId) {
                resourceIdObject.add("irrigation_controller_id", context.serialize(deviceId));
            } else if (src.resourceId() instanceof RachioId.Program programId) {
                resourceIdObject.add("program_id", context.serialize(programId));
            } else if (src.resourceId() == null) {
                throw new JsonParseException("Resource ID is null");
            } else {
                throw new JsonParseException("Unknown resource ID type: " + src.resourceId().getClass().getName());
            }

            jsonObject.add("resourceId", context.serialize(resourceIdObject));
            return jsonObject;
        }
    }

    public static final RachioApiWebhook EMPTY = new RachioApiWebhook(null, null, null, null, List.of());
}
