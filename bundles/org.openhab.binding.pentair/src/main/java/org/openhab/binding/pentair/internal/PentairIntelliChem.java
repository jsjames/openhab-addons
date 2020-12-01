/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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

import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.pentair.internal.handler.PentairControllerHandler;
import org.openhab.binding.pentair.internal.handler.PentairIntelliChlorHandler;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for the pentair controller schedules.
 *
 * @author Jeff James - initial contribution
 *
 */
@NonNullByDefault
public class PentairIntelliChem {
    private final Logger logger = LoggerFactory.getLogger(PentairIntelliChem.class);

    public static final int PHREADINGHI = 0;
    public static final int PHREADINGLO = 1;
    public static final int ORPREADINGHI = 2;
    public static final int ORPREADINGLO = 3;
    public static final int PHSETPOINTHI = 4;
    public static final int PHSETPOINTLO = 5;
    public static final int ORPSETPOINTHI = 6;
    public static final int ORPSETPOINTLO = 7;
    public static final int TANK1 = 20;
    public static final int TANK2 = 21;
    public static final int CALCIUMHARDNESSHI = 23;
    public static final int CALCIUMHARDNESSLO = 24;
    public static final int CYAREADING = 27;
    public static final int TOTALALKALINITYREADING = 28;
    public static final int WATERFLOW = 30;
    public static final int MODE1 = 34;
    public static final int MODE2 = 35;

    public double phReading;
    public int orpReading;
    public double phSetPoint;
    public int orpSetPoint; // Oxidation Reduction Potential
    public int tank1;
    public int tank2;
    public int calciumHardness;
    public int cyaReading; // Cyanuric Acid
    public int totalAlkalinity;
    public boolean waterFlowAlarm;
    public int mode1;
    public int mode2;
    public double saturationIndex;

    public double calcCalciumHardnessFactor() {
        double calciumHardnessFactor = 0;

        if (calciumHardness <= 25) {
            calciumHardnessFactor = 1.0;
        } else if (calciumHardness <= 50) {
            calciumHardnessFactor = 1.3;
        } else if (calciumHardness <= 75) {
            calciumHardnessFactor = 1.5;
        } else if (calciumHardness <= 100) {
            calciumHardnessFactor = 1.6;
        } else if (calciumHardness <= 125) {
            calciumHardnessFactor = 1.7;
        } else if (calciumHardness <= 150) {
            calciumHardnessFactor = 1.8;
        } else if (calciumHardness <= 200) {
            calciumHardnessFactor = 1.9;
        } else if (calciumHardness <= 250) {
            calciumHardnessFactor = 2.0;
        } else if (calciumHardness <= 300) {
            calciumHardnessFactor = 2.1;
        } else if (calciumHardness <= 400) {
            calciumHardnessFactor = 2.2;
        } else if (calciumHardness <= 800) {
            calciumHardnessFactor = 2.5;
        }

        return calciumHardnessFactor;
    }

    public double calcTemperatureFactor(QuantityType<Temperature> t) {
        double temperatureFactor = 0;
        int temperature = t.intValue();

        if (t.getUnit() == SIUnits.CELSIUS) {
            if (temperature <= 0) {
                temperatureFactor = 0.0;
            } else if (temperature <= 2.8) {
                temperatureFactor = 0.1;
            } else if (temperature <= 7.8) {
                temperatureFactor = 0.2;
            } else if (temperature <= 11.7) {
                temperatureFactor = 0.3;
            } else if (temperature <= 15.6) {
                temperatureFactor = 0.4;
            } else if (temperature <= 18.9) {
                temperatureFactor = 0.5;
            } else if (temperature <= 24.4) {
                temperatureFactor = 0.6;
            } else if (temperature <= 28.9) {
                temperatureFactor = 0.7;
            } else if (temperature <= 34.4) {
                temperatureFactor = 0.8;
            } else if (temperature <= 40.6) {
                temperatureFactor = 0.9;
            }
        } else { // Fahrenheit
            if (temperature <= 32) {
                temperatureFactor = 0.0;
            } else if (temperature <= 37) {
                temperatureFactor = 0.1;
            } else if (temperature <= 46) {
                temperatureFactor = 0.2;
            } else if (temperature <= 53) {
                temperatureFactor = 0.3;
            } else if (temperature <= 60) {
                temperatureFactor = 0.4;
            } else if (temperature <= 66) {
                temperatureFactor = 0.5;
            } else if (temperature <= 76) {
                temperatureFactor = 0.6;
            } else if (temperature <= 84) {
                temperatureFactor = 0.7;
            } else if (temperature <= 94) {
                temperatureFactor = 0.8;
            } else if (temperature <= 105) {
                temperatureFactor = 0.9;
            }
        }

        return temperatureFactor;
    }

    public double calcCorrectedAlkalinity() {
        return totalAlkalinity - cyaReading / 3;
    }

    public double calcAlkalinityFactor() {
        double ppm = calcCorrectedAlkalinity();
        double alkalinityFactor = 0;

        if (ppm <= 25) {
            alkalinityFactor = 1.4;
        } else if (ppm <= 50) {
            alkalinityFactor = 1.7;
        } else if (ppm <= 75) {
            alkalinityFactor = 1.9;
        } else if (ppm <= 100) {
            alkalinityFactor = 2.0;
        } else if (ppm <= 125) {
            alkalinityFactor = 2.1;
        } else if (ppm <= 150) {
            alkalinityFactor = 2.2;
        } else if (ppm <= 200) {
            alkalinityFactor = 2.3;
        } else if (ppm <= 250) {
            alkalinityFactor = 2.4;
        } else if (ppm <= 300) {
            alkalinityFactor = 2.5;
        } else if (ppm <= 400) {
            alkalinityFactor = 2.6;
        } else if (ppm <= 800) {
            alkalinityFactor = 2.9;
        }

        return alkalinityFactor;
    }

    public double calcTotalDisovledSolidsFactor() {
        // 12.1 for non-salt; 12.2 for salt

        if (PentairIntelliChlorHandler.onlineChlorinator != null) {
            return 12.2;
        }

        return 12.1;
    }

    public double calcSaturationIndex() {
        QuantityType<Temperature> temperature;
        double alkalinityFactor;
        double temperatureFactor;
        double saturationIndex;

        PentairControllerHandler pch = PentairControllerHandler.onlineController;

        if (pch != null) {
            temperature = pch.getWaterTemp();
            temperatureFactor = calcTemperatureFactor(temperature);
        } else {
            temperatureFactor = .4;
        }

        alkalinityFactor = calcAlkalinityFactor();

        saturationIndex = this.phReading + calcCalciumHardnessFactor() + alkalinityFactor + temperatureFactor
                - calcTotalDisovledSolidsFactor();

        return saturationIndex;
    }

    public void parsePacket(PentairPacket p) {
        if (p.getLength() != 41) {
            logger.debug("Intellichem packet not 41 bytes long");
            return;
        }

        phReading = (((p.getByte(PHREADINGHI) & 0xFF) * 256) + (p.getByte(PHREADINGLO) & 0xFF)) / 100.0;
        orpReading = ((p.getByte(ORPREADINGHI) & 0xFF) * 256) + (p.getByte(ORPREADINGLO) & 0xFF);
        phSetPoint = (((p.getByte(PHSETPOINTHI) & 0xFF) * 256) + (p.getByte(PHSETPOINTLO) & 0xFF)) / 100.0;
        orpSetPoint = ((p.getByte(ORPSETPOINTHI) & 0xFF) * 256) + (p.getByte(ORPSETPOINTLO) & 0xFF);
        tank1 = p.getByte(TANK1);
        tank2 = p.getByte(TANK2);
        calciumHardness = ((p.getByte(CALCIUMHARDNESSHI) & 0xFF) * 256) + (p.getByte(CALCIUMHARDNESSLO) & 0xFF);
        cyaReading = p.getByte(CYAREADING);
        totalAlkalinity = (p.getByte(TOTALALKALINITYREADING) & 0xFF);
        waterFlowAlarm = p.getByte(WATERFLOW) != 0x00;
        mode1 = p.getByte(MODE1);
        mode2 = p.getByte(MODE2);

        saturationIndex = calcSaturationIndex();
    }

    @Override
    public String toString() {
        String str = String.format(
                "PH: %.2f, OPR: %d, PH set point: %.2f, ORP set point: %d, tank1: %d, tank2: %d, calcium hardness: %d, cyareading: %d, total alkalinity: %d, water flow alarm: %b, mode1: %h, mode2: %h, saturationindex: %f.1",
                phReading, orpReading, phSetPoint, orpSetPoint, tank1, tank2, calciumHardness, cyaReading,
                totalAlkalinity, waterFlowAlarm, mode1, mode2, saturationIndex);

        return str;
    }
}
