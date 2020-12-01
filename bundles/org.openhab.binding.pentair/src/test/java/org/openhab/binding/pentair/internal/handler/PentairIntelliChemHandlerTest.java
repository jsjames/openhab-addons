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

package org.openhab.binding.pentair.internal.handler;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.openhab.binding.pentair.internal.PentairBindingConstants.*;
import static org.openhab.binding.pentair.internal.TestUtilities.parsehex;

import java.util.Collections;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;

/**
 * PentairIntelliChemHandlerTest
 *
 * @author Jeff James - Initial contribution
 *
 */
public class PentairIntelliChemHandlerTest {

    //@formatter:off
    public static byte[][] packets = {
            parsehex("A50010901229030202A302D002C60000000000000000000000000006070000C8003F005A3C00580006A5201E01000000"),
            parsehex("A5100F10122902E302AF02EE02BC000000020000002A0004005C060518019000000096140051000065203C0100000000")
    };
    //@formatter:on

    private PentairIntelliChemHandler pich;

    @Mock
    private Bridge bridge;

    @Mock
    private ThingHandlerCallback callback;

    @Mock
    private Thing thing;

    @Mock
    private PentairIPBridgeHandler pibh;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterAll
    public static void tearDownAfterClass() throws Exception {
    }

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(bridge.getStatus()).thenReturn(ThingStatus.ONLINE);
        when(thing.getConfiguration()).thenReturn(new Configuration(Collections.singletonMap("id", 144)));
        when(thing.getUID()).thenReturn(new ThingUID("1:2:3"));

        pibh = new PentairIPBridgeHandler(bridge);

        pich = new PentairIntelliChemHandler(thing) {
            @Override
            public PentairBaseBridgeHandler getBridgeHandler() {
                return pibh;
            }
        };

        pich.setCallback(callback);
    }

    @AfterEach
    public void tearDown() throws Exception {
        pich.dispose();
    }

    @Test
    public void test() {
        pich.initialize();

        PentairPacket p = new PentairPacket(packets[0], packets[0].length);

        verify(callback).statusUpdated(eq(thing), argThat(arg -> arg.getStatus().equals(ThingStatus.UNKNOWN)));

        pich.processPacketFrom(p);

        verify(callback).statusUpdated(eq(thing), argThat(arg -> arg.getStatus().equals(ThingStatus.ONLINE)));

        ChannelUID cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLICHEM_PHREADING);
        verify(callback).stateUpdated(cuid, new DecimalType(7.7));

        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLICHEM_WATERFLOWALARM);
        verify(callback).stateUpdated(cuid, OnOffType.ON);

        // TODO: process 2nd packet
    }
}
