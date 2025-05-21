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

import java.util.Date;

import org.openhab.binding.rachio.internal.api.RachioId;

/**
 * {@link RachioApiZone} maps the API results to a Java object (using GSon).
 *
 * @author Markus Michels - Initial contribution
 */
public record RachioApiZone( //
        RachioId.Zone id, //
        String name, //
        int zoneNumber, //
        boolean enabled, //
        double availableWater, //
        double rootZoneDepth, //
        double managementAllowedDepletion, //
        double efficiency, //
        int yardAreaSquareFeet, //
        double irrigationAmount, //
        double depthOfWater, //
        int runtime, //
        String imageUrl, //
        Date lastWateredDate, //
        long lastWaterDuration, //
        boolean scheduleDataModified, //
        int fixedRuntime, //
        double saturatedDepthOfWater, //
        int maxRuntime, //
        int runtimeNoMultiplier, //
        RachioApiZone.RachioApiZoneCustomNozzle customNozzle, //
        RachioApiZone.RachioApiZoneCustomSoil customSoil, //
        RachioApiZone.RachioApiZoneCustomSlope customSlope, //
        RachioApiZone.RachioApiZoneCustomCrop customCrop, //
        RachioApiZone.RachioApiZoneCustomShade customShade) {
    public static record RachioApiZoneCustomNozzle( //
            String name, //
            Double inchesPerHour) {
    }

    public static record RachioApiZoneCustomSoil( //
            String name, //
            int sortOrder) {
    }

    public static record RachioApiZoneCustomSlope( //
            String name, //
            int sortOrder) {
    }

    public static record RachioApiZoneCustomCrop( //
            String name, //
            Double coefficient) {
    }

    public static record RachioApiZoneCustomShade( //
            String name) {
    }

    public static final RachioApiZone EMPTY = new RachioApiZone(null, null, 0, false, 0.0, 0.0, 0.0, 0.0, 0, 0.0, 0.0,
            0, null, null, 0L, false, 0, 0.0, 0, 0, null, null, null, null, null);
}
