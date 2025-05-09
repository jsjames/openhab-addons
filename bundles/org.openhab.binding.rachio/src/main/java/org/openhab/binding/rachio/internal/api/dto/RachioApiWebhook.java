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
import org.openhab.binding.rachio.utils.Id;

import com.google.gson.annotations.SerializedName;

/**
 * {@link RachioApiWebhook} store some relevant information from API events.
 *
 * @author Jeff James - Initial contribution
 */
public class RachioApiWebhook implements Id<RachioId.Webhook> {
    public RachioId.Webhook id;
    @SerializedName("external_id")
    public String externalId;
    @SerializedName("resource_id")
    public RachioApiWebhookResorceID resourceId;
    public String url;
    @SerializedName("event_types")
    public List<@NonNull String> eventTypes;

    public static class RachioApiWebhookResorceID {
        @SerializedName("valve_id")
        public RachioId.Valve valveId;
        @SerializedName("irrigation_controller_id")
        public RachioId.Device irrigationControllerId;
        @SerializedName("program_id")
        public RachioId.Program programId;
    }

    public RachioId.Webhook getId() {
        return id;
    }

    public static RachioApiWebhook EMPTY = new RachioApiWebhook();
}
