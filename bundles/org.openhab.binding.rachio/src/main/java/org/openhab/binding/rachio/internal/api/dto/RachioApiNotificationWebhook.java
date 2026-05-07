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

import java.util.Date;
import java.util.List;

import org.openhab.binding.rachio.internal.api.RachioId;

import com.google.gson.annotations.SerializedName;

/**
 * {@link RachioApiWebhook} store some relevant information from API events.
 *
 * @author Jeff James - Initial contribution
 */
public record RachioApiNotificationWebhook( //
        RachioId.NotificationWebhook id, //
        Date createDate, //
        @SerializedName(value = "external_id", alternate = "externalId") String externalId, //
        String url, //
        @SerializedName(value = "event_types", alternate = "eventTypes") List<RachioApiNotificationWebhookEventType> eventTypes) {

    public static final RachioApiNotificationWebhook EMPTY = new RachioApiNotificationWebhook(null, null, null, null,
            List.of());
}
