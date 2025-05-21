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

/**
 * {@link RachioApiBaseStation}
 *
 * @author Jeff James - Initial contribution
 */
public record RachioApiBaseStation( //
        RachioId.BaseStation id, //
        String serialNumber, //
        String macAddress, //
        ReportedState reportedState, //
        Instant created, //
        Instant updated, //
        boolean shared) {

    public static record ReportedState( //
            boolean connected, //
            String bleHubFirmwareVersion, //
            String wifiBridgeFirmwareVersion) {
    }

    public static final RachioApiBaseStation EMPTY = new RachioApiBaseStation(null, null, null, null, null, null,
            false);
}
