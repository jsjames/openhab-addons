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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Constants used for the Controller class
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class PentairControllerConstants {
    public enum LightMode {
        OFF(0, "Off"),
        ON(1, "On"),
        COLORSYNC(128, "Color Sync"),
        COLORSWIM(144, "Color Swim"),
        COLORSET(160, "COLORSET"),
        PARTY(177, "PARTY"),
        ROMANCE(178, "ROMANCE"),
        CARIBBENA(179, "CARIBBEAN"),
        AMERICAN(180, "AMERICAN"),
        SUNSET(181, "SUNSET"),
        ROYAL(182, "ROYAL"),
        BLUE(193, "BLUE"),
        GREEN(194, "GREEN"),
        RED(195, "RED"),
        WHITE(96, "WHITE"),
        MAGENTA(197, "MAGENTA");

        private int number;
        private String name;

        private LightMode(int n, String name) {
            this.number = n;
            this.name = name;
        }

        public int getModeNumber() {
            return number;
        }

        public String getName() {
            return name;
        }

        public static @Nullable LightMode valueOfModeNumber(int value) {
            for (LightMode lightMode : values()) {
                if (lightMode.getModeNumber() == value) {
                    return lightMode;
                }
            }
            return null;
        }
    }
}
