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

import org.openhab.binding.rachio.internal.api.RachioId;

/**
 * The {@link RachioApiNotificationWebhookEventType } implements the interface to the Rachio cloud service (using http).
 *
 */
public record RachioApiNotificationWebhookEventType( //
        RachioId.Webhook id, //
        String name, //
        String description, //
        String type) {
}
