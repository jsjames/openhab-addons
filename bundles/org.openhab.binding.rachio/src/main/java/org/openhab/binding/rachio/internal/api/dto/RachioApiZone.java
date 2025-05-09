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
import org.openhab.binding.rachio.utils.Id;

/**
 * {@link RachioApiZone} maps the API results to a Java object (using GSon).
 *
 * @author Markus Michels - Initial contribution
 */
public class RachioApiZone implements Id<RachioId.Zone> {
    public RachioId.Zone id;
    public String name;
    public int zoneNumber;
    public boolean enabled;
    public double availableWater;
    public double rootZoneDepth;
    public double managementAllowedDepletion;
    public double efficiency;
    public int yardAreaSquareFeet;
    public double irrigationAmount;
    public double depthOfWater;
    public int runtime;

    public String imageUrl;
    public Date lastWateredDate;
    public long lastWaterDuration;
    public boolean scheduleDataModified;
    public int fixedRuntime;
    public double saturatedDepthOfWater;
    public int maxRuntime;
    public int runtimeNoMultiplier;
    // public HashMap<String, int> wateringAdjustmentRuntimes[];

    public RachioApiZoneCustomNozzle customNozzle;
    public RachioApiZoneCustomSoil customSoil;
    public RachioApiZoneCustomSlope customSlope;
    public RachioApiZoneCustomCrop customCrop;
    public RachioApiZoneCustomShade customShade;

    public static class RachioApiZoneCustomNozzle {
        public String name;
        public Double inchesPerHour;
    }

    public static class RachioApiZoneCustomSoil {
        public String name;
        public int sortOrder;
    }

    public static class RachioApiZoneCustomSlope {
        public String name;
        public int sortOrder;
    }

    public static class RachioApiZoneCustomCrop {
        public String name;
        public Double coefficient;
    }

    public static class RachioApiZoneCustomShade {
        public String name;
    }

    public static final RachioApiZone EMPTY = new RachioApiZone();

    @Override
    public RachioId.Zone getId() {
        return id;
    }
}
