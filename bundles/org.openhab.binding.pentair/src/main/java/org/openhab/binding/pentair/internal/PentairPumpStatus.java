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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pentair pump status packet specialation of a PentairPacket. Includes public variables for many of the reverse
 * engineered packet content.
 *
 * @author Jeff James - initial contribution
 *
 */
@NonNullByDefault
public class PentairPumpStatus { // 15 byte packet format
    private final Logger logger = LoggerFactory.getLogger(PentairPumpStatus.class);

    protected static final int RUN = 0;
    protected static final int MODE = 1; // Mode in pump status. Means something else in pump
                                         // write/response?
    protected static final int DRIVESTATE = 2; // ?? Drivestate in pump status. Means something else in
                                               // pump write/respoonse
    protected static final int WATTSH = 3;
    protected static final int WATTSL = 4;
    protected static final int RPMH = 5;
    protected static final int RPML = 6;
    protected static final int GPM = 7;
    protected static final int PPC = 8; // not sure what this is? always 0
    protected static final int B09 = 9;
    protected static final int STATUS1 = 11;
    protected static final int STATUS2 = 12;
    protected static final int HOUR = 13;
    protected static final int MIN = 14;

    /** pump is running */
    public boolean run;

    /** pump mode (1-4) */
    public int mode;

    /** pump drivestate - not sure what this specifically represents. */
    public int drivestate;
    /** pump power - in KW */
    public int power;
    /** pump rpm */
    public int rpm;
    /** pump gpm */
    public int gpm;
    /** byte in packet indicating an error condition */
    public int error;
    /** byte in packet indicated status */
    public int status1;
    public int status2;
    /** current timer for pump */
    public int timer;
    /** hour or packet (based on Intelliflo time setting) */
    public int hour;
    /** minute of packet (based on Intelliflo time setting) */
    public int min;

    public void parsePacket(PentairPacket p) {
        if (p.getLength() != 15) {
            logger.debug("Pump status packet not 15 bytes long");
            return;
        }

        run = (p.getByte(RUN) == (byte) 0x0A);
        mode = p.getByte(MODE);
        drivestate = p.getByte(DRIVESTATE);
        power = ((p.getByte(WATTSH) & 0xFF) * 256) + (p.getByte(WATTSL) & 0xFF);
        rpm = ((p.getByte(RPMH) & 0xFF) * 256) + (p.getByte(RPML) & 0xFF);
        gpm = p.getByte(GPM) & 0xFF;

        status1 = p.getByte(STATUS1);
        status2 = p.getByte(STATUS2);
        hour = p.getByte(HOUR);
        min = p.getByte(MIN);
    }

    @Override
    public String toString() {
        String str = String.format("%02d:%02d run:%b mode:%d power:%d rpm:%d gpm:%d status11:0x%h status12:0x%h", hour,
                min, run, mode, power, rpm, gpm, status1, status2);

        return str;
    }
}
