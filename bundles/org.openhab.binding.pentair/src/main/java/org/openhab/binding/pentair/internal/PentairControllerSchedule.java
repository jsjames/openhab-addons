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

import static org.openhab.binding.pentair.internal.PentairBindingConstants.CONTROLLER_SCHEDULE;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Class for the pentair controller schedules.
 *
 * @author Jeff James - initial contribution
 *
 */
@NonNullByDefault
public class PentairControllerSchedule {
    public static final int ID = 0;
    public static final int CIRCUIT = 1;
    public static final int STARTH = 2;
    public static final int STARTM = 3;
    public static final int ENDH = 4;
    public static final int ENDM = 5;
    public static final int DAYS = 6;

    private static final String regexSchedule = "^(NONE|NORMAL|EGGTIMER|ONCEONLY),(\\\\d+),(\\\\d+):(\\\\d+),(\\\\d+):(\\\\d+),([SMTWRFY]+)";
    private static final Pattern ptnSchedule = Pattern.compile(regexSchedule);

    private boolean dirty;

    public enum ScheduleType {
        NONE("None"),
        NORMAL("Normal"),
        EGGTIMER("Egg Timer"),
        ONCEONLY("Once Only"),
        UNKNOWN("Unknown");

        private String name;

        private ScheduleType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public int id;
    public int circuit;
    public ScheduleType type = ScheduleType.UNKNOWN;

    public int start;
    public int end;

    public int days;

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean d) {
        dirty = d;
    }

    public void parsePacket(PentairPacket p) {
        id = p.getByte(ID);
        circuit = p.getByte(CIRCUIT);
        days = p.getByte(DAYS);

        if (p.getByte(STARTH) == 25) {
            type = ScheduleType.EGGTIMER;
            start = 0;
            end = p.getByte(ENDH) * 60 + p.getByte(ENDM);
        } else if (p.getByte(ENDH) == 26) {
            type = ScheduleType.ONCEONLY;
            start = p.getByte(STARTH) * 60 + p.getByte(STARTM);
            end = 0;
        } else if (circuit == 0) {
            type = ScheduleType.NONE;
            start = 0;
            end = 0;
        } else {
            type = ScheduleType.NORMAL;
            start = p.getByte(STARTH) * 60 + p.getByte(STARTM);
            end = p.getByte(ENDH) * 60 + p.getByte(ENDM);
        }
    }

    public @Nullable String getScheduleTypeStr() {
        String str = type.name();

        return str;
    }

    public boolean setScheduleCircuit(int c) {
        if (circuit == c) {
            return true;
        }

        if (c > 18 || c <= 0) {
            return false;
        }

        circuit = c;
        dirty = true;

        return true;
    }

    public boolean setScheduleStart(int min) {
        if (min == start) {
            return true;
        }

        if (min > 1440 || min < 0) {
            return false;
        }

        start = min;
        dirty = true;

        return true;
    }

    public boolean setScheduleEnd(int min) {
        if (min == end) {
            return true;
        }

        if (min > 1440 || min < 0) {
            return false;
        }

        end = min;
        dirty = true;

        return true;
    }

    public boolean setScheduleType(ScheduleType type) {
        if (this.type == type) {
            return true;
        }

        this.type = type;
        dirty = true;

        return true;
    }

    public boolean setScheduleType(String typestring) {
        ScheduleType scheduleType;

        try {
            scheduleType = ScheduleType.valueOf(typestring);
        } catch (IllegalArgumentException e) {
            return false;
        }

        return setScheduleType(scheduleType);
    }

    public boolean setDays(String d) {
        final String dow = "SMTWRFY";

        days = 0;
        for (int i = 0; i <= 6; i++) {
            if (d.indexOf(dow.charAt(i)) >= 0) {
                days |= 1 << i;
            }
        }

        dirty = true;

        return true;
    }

    public @Nullable PentairPacket getWritePacket(int controllerid, int preamble) {
        byte[] packet = { (byte) 0xA5, (byte) preamble, (byte) controllerid, (byte) 0x00 /* source */, (byte) 0x91,
                (byte) 7, (byte) id, (byte) circuit, (byte) (start / 60), (byte) (start % 60), (byte) (end / 60),
                (byte) (end % 60), (byte) days };
        PentairPacket p = new PentairPacket(packet);

        switch (type) {
            case NONE:
                p.setByte(STARTH, (byte) 0);
                p.setByte(STARTM, (byte) 0);
                p.setByte(ENDH, (byte) 0);
                p.setByte(ENDM, (byte) 0);
                p.setByte(CIRCUIT, (byte) 0);
                p.setByte(DAYS, (byte) 0);
                break;

            case NORMAL:
                break;

            case ONCEONLY:
                p.setByte(ENDH, (byte) 26);
                p.setByte(ENDM, (byte) 0);
                break;
            case EGGTIMER:
                p.setByte(STARTH, (byte) 25);
                p.setByte(STARTM, (byte) 0);
                p.setByte(DAYS, (byte) 0);
                break;
            case UNKNOWN:
                return null;
        }

        return p;
    }

    public String getDays() {
        final String dow = "SMTWRFY";
        String str = "";

        for (int i = 0; i <= 6; i++) {
            if ((((days >> i) & 0x01)) == 0x01) {
                str += dow.charAt(i);
            }
        }

        return str;
    }

    @Override
    public String toString() {
        String str = String.format("%s,%d,%02d:%02d,%02d:%02d,%s", getScheduleTypeStr(), circuit, start / 60,
                start % 60, end / 60, end % 60, getDays());

        return str;
    }

    public boolean fromString(String str) {
        String schedulestr = str.toUpperCase();
        Matcher m = ptnSchedule.matcher(schedulestr);

        if (!m.find()) {
            return false;
        }

        if (!setScheduleCircuit(Integer.parseUnsignedInt(m.group(2)))) {
            return false;
        }

        int min = Integer.parseUnsignedInt(m.group(3)) * 60 + Integer.parseUnsignedInt(m.group(4));
        if (!setScheduleStart(min)) {
            return false;
        }

        min = Integer.parseUnsignedInt(m.group(5)) * 60 + Integer.parseUnsignedInt(m.group(6));
        if (!setScheduleEnd(min)) {
            return false;
        }

        if (!setDays(m.group(7))) {
            return false;
        }

        ScheduleType t;
        try {
            t = ScheduleType.valueOf(m.group(1));
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (!setScheduleType(t)) {
            return false;
        }

        dirty = true;

        return true;
    }

    public String getGroupID() {
        String groupID = CONTROLLER_SCHEDULE + Integer.toString(id);

        return groupID;
    }
}
