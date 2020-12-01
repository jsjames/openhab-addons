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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.openhab.binding.pentair.internal.TestUtilities.parsehex;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * PentairIntelliChemTest
 *
 * @author Jeff James - Initial contribution
 *
 */
@NonNullByDefault
public class PentairIntelliChemTest {

    //@formatter:off
    public static byte[][] packets = {
            parsehex("A50010901229030202A302D002C60000000000000000000000000006070000C8003F005A3C00580006A5201E01000000"),
            parsehex("A5100F10122902E302AF02EE02BC000000020000002A0004005C060518019000000096140051000065203C0100000000")
    };
    //@formatter:on

    @Test
    public void test() {
        PentairIntelliChem pic = new PentairIntelliChem();
        PentairPacket p1 = new PentairPacket(packets[0], packets[0].length);

        pic.parsePacket(p1);

        assertThat(pic.phReading, equalTo(7.70));
        assertThat(pic.orpReading, equalTo(675));
        assertThat(pic.phSetPoint, equalTo(7.20));
        assertThat(pic.orpSetPoint, equalTo(710));
        assertThat(pic.tank1, equalTo(0));
        assertThat(pic.tank2, equalTo(6));
        // assertThat(pic.calciumhardness, equalTo(0));
        assertThat(pic.cyaReading, equalTo(63));
        assertThat(pic.totalAlkalinity, equalTo(0));
        assertThat(pic.waterFlowAlarm, equalTo(true));
        assertThat(pic.mode1, equalTo(0x06));
        assertThat(pic.mode2, equalTo(0xA5));

        assertThat(pic.calcCalciumHardnessFactor(), equalTo(1.0));

        PentairPacket p2 = new PentairPacket(packets[1], packets[1].length);
        pic.parsePacket(p2);

        assertThat(pic.phReading, equalTo(7.39));
        assertThat(pic.orpReading, equalTo(687));
        assertThat(pic.phSetPoint, equalTo(7.50));
        assertThat(pic.orpSetPoint, equalTo(700));
        assertThat(pic.tank1, equalTo(6));
        assertThat(pic.tank2, equalTo(5));
        // assertThat(pic.calciumhardness, equalTo(0));
        assertThat(pic.cyaReading, equalTo(0));
        assertThat(pic.totalAlkalinity, equalTo(150));
        assertThat(pic.waterFlowAlarm, equalTo(false));
        assertThat(pic.mode1, equalTo(0x65));
        assertThat(pic.mode2, equalTo(0x20));

        assertThat(pic.calcCalciumHardnessFactor(), equalTo(2.2));
    }
}
