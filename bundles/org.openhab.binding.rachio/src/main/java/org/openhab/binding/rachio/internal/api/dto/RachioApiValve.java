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

import java.time.Instant;

import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.utils.Id;

import com.google.gson.annotations.SerializedName;

/**
 * {@link RachioApiValve}
 *
 * @author Jeff James - Initial contribution
 */
public class RachioApiValve implements Id<RachioId.Valve> {
    public RachioId.Valve id;
    public String name;
    public String connectionId;
    public Photo photo;
    public State state;
    public String color;
    public boolean detectFlow;
    public Instant created;
    public Instant updated;

    public static class Photo {
        public String id;
        @SerializedName("default")
        public boolean defaultPhoto;
    }

    public static class State {
        public ReportedState reportedState;
        public DesiredState desiredState;
        public boolean matches;
    }

    public static class ReportedState {
        public boolean connected;
        public long defaultRuntimeSeconds;
        public LastWateringAction lastWateringAction;
        public Instant lastSeen;
        public String batteryStatus;
        public String firmwareVersion;
        public String calendarHash;
    }

    public static class DesiredState {
        public long deafultRuntimeSeconds;
        public String calendarHash;
    }

    public static class LastWateringAction {
        public Instant start;
        public long durationSeconds;
        public String reason;
    }

    @Override
    public RachioId.Valve getId() {
        return id;
    }

    public static final RachioApiValve EMPTY = new RachioApiValve();
}
