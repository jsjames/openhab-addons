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

import java.util.List;

import org.eclipse.jdt.annotation.NonNull;
import org.openhab.binding.rachio.internal.api.RachioId;

import com.google.gson.annotations.SerializedName;

/**
 * {@link RachioApiWebhook} store some relevant information from API events.
 *
 * @author Jeff James - Initial contribution
 */
public record RachioApiWebhook( //
        RachioId.Webhook id, //
        @SerializedName("external_id") String externalId, //
        @SerializedName("resource_id") RachioApiWebhookResourceID resourceId, //
        String url, //
        @SerializedName("event_types") List<@NonNull String> eventTypes) {

    public static record RachioApiWebhookResourceID( //
            @SerializedName("valve_id") RachioId.Valve valveId, //
            @SerializedName("irrigation_controller_id") RachioId.Device irrigationControllerId, //
            @SerializedName("program_id") RachioId.Program programId) {
    }

    public static final RachioApiWebhook EMPTY = new RachioApiWebhook(null, null, null, null, List.of());
}
