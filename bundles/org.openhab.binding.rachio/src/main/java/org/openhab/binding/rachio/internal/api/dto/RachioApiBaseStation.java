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

/**
 * {@link RachioApiBaseStation}
 *
 * @author Jeff James - Initial contribution
 */
public class RachioApiBaseStation implements Id<RachioId.BaseStation> {
    public RachioId.BaseStation id;
    public String serialNumber;
    public String macAddress;
    public ReportedState reportedState;
    public Instant created;
    public Instant updated;
    public boolean shared;

    public static class ReportedState {
        public boolean connected;
        public String bleHubFirmwareVersion;
        public String wifiBridgeFirmwareVersion;
    }

    public RachioId.BaseStation getId() {
        return id;
    }

    public static final RachioApiBaseStation EMPTY = new RachioApiBaseStation();
}
