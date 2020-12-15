/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.pentair.internal;

import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Pentair heat set point packet specialization of a PentairPacket. Includes public variables for many of the reverse
 * engineered
 * packet content.
 *
 * @author Jeff James - initial contribution
 *
 */
@NonNullByDefault
public class PentairHeatStatus {

    public enum HeatMode {
        NONE(0, "None"),
        HEATER(1, "Heater"),
        SOLARPREFERRED(2, "Solar Preferred"),
        SOLAR(3, "Solar");

        private int code;
        private String friendlyName;

        private HeatMode(int code, String friendlyName) {
            this.code = code;
            this.friendlyName = friendlyName;
        }

        public int getCode() {
            return code;
        }

        public String getFriendlyName() {
            return friendlyName;
        }

        public static @Nullable HeatMode valueOfCode(int code) {
            return Arrays.stream(values()).filter(value -> (value.getCode() == code)).findFirst().orElse(null);
        }
    }

    protected static final int POOLTEMP = 1;
    protected static final int AIRTEMP = 2;
    protected static final int POOLSETPOINT = 3;
    protected static final int SPASETPOINT = 4;
    protected static final int HEATMODE = 5;
    protected static final int SOLARTEMP = 8;

    /** pool temperature set point */
    public int poolSetPoint;
    /** pool heat mode - 0=Off, 1=Heater, 2=Solar Pref, 3=Solar */
    public @Nullable HeatMode poolHeatMode;
    /** spa temperature set point */
    public int spaSetPoint;
    /** spa heat mode - 0=Off, 1=Heater, 2=Solar Pref, 3=Solar */
    public @Nullable HeatMode spaHeatMode;

    /**
     * Constructure to create an empty status packet
     */
    public PentairHeatStatus() {
    }

    public PentairHeatStatus(PentairPacket p) {
        parsePacket(p);
    }

    public void parsePacket(PentairPacket p) {
        poolSetPoint = p.getByte(POOLSETPOINT);
        poolHeatMode = HeatMode.valueOfCode(p.getByte(HEATMODE) & 0x03);

        spaSetPoint = p.getByte(SPASETPOINT);
        spaHeatMode = HeatMode.valueOfCode((p.getByte(HEATMODE) >> 2) & 0x03);
    }

    @Override
    public String toString() {
        String str = String.format("poolSetPoint: %d, poolHeatMode: %s, spaSetPoint: %d, spaHeatMode: %s", poolSetPoint,
                poolHeatMode.name(), spaSetPoint, spaHeatMode.name());

        return str;
    }
}
