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

import java.time.Instant;

import org.openhab.binding.rachio.internal.api.RachioId;

import com.google.gson.annotations.SerializedName;

/**
 * {@link RachioApiValve}
 *
 * @author Jeff James - Initial contribution
 */
public record RachioApiValve( //
        RachioId.Valve id, //
        String name, //
        String connectionId, //
        Photo photo, //
        State state, //
        String color, //
        boolean detectFlow, //
        Instant created, //
        Instant updated) {
    public static record Photo( //
            String id, //
            @SerializedName("default") boolean defaultPhoto) {
    }

    public static record State( //
            ReportedState reportedState, //
            DesiredState desiredState, //
            boolean matches //
    ) {
    }

    public static record ReportedState( //
            boolean connected, //
            long defaultRuntimeSeconds, //
            LastWateringAction lastWateringAction, //
            Instant lastSeen, //
            String batteryStatus, //
            String firmwareVersion, //
            String calendarHash) {
    }

    public static record DesiredState( //
            long deafultRuntimeSeconds, //
            String calendarHash) {
    }

    public static record LastWateringAction( //
            Instant start, //
            long durationSeconds, //
            String reason) {
    }

    public static final RachioApiValve EMPTY = new RachioApiValve(null, null, null, null, null, null, false, null,
            null);
}
