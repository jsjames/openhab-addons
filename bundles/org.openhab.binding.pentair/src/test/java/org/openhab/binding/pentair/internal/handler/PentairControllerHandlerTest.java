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
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PentairControllerHandlerTest }
 *
 * @author Jeff James - Initial contribution
 */
public class PentairControllerHandlerTest {
    private final Logger logger = LoggerFactory.getLogger(PentairControllerHandlerTest.class);

    //@formatter:off
    public static byte[][] packets = {
            parsehex("A5 1E 0F 10 02 1D 09 20 21 00 00 00 00 00 00 20 0F 00 00 04 3F 3F 00 00 41 3C 00 00 07 00 00 6A B6 00 0D"),
            parsehex("A5 24 0f 10 02 1d 08 3b 00 01 00 00 00 00 00 20 00 00 00 04 4a 4a 00 00 44 00 00 00 04 00 00 7c e6 00 0d"),
            parsehex("a5 24 0f 10 02 1d 09 04 00 31 00 00 00 00 00 20 00 00 00 04 4a 4a 00 00 45 00 00 00 04 00 07 ce 60 00 0d"),
            parsehex("A5 1E 0F 10 02 1D 0A 0B 00 00 00 00 00 00 00 21 33 00 00 04 45 45 00 00 3F 3F 00 00 07 00 00 D9 89 00 0D")
    };
    //@formatter:on

    private PentairControllerHandler handler;

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
        when(thing.getConfiguration()).thenReturn(new Configuration(Collections.singletonMap("id", 0x10)));
        when(thing.getUID()).thenReturn(new ThingUID("1:2:3"));

        pibh = new PentairIPBridgeHandler(bridge);

        handler = new PentairControllerHandler(thing) {
            @Override
            public PentairBaseBridgeHandler getBridgeHandler() {
                return pibh;
            }
        };

        handler.setCallback(callback);
    }

    @AfterEach
    public void tearDown() throws Exception {
        handler.dispose();
    }

    @Test
    public void testPacketProcessing() {
        handler.initialize();

        verify(callback, times(1)).statusUpdated(eq(thing),
                argThat(arg -> arg.getStatus().equals(ThingStatus.UNKNOWN)));

        PentairPacket p = new PentairPacket(packets[0], packets[0].length);
        handler.processPacketFrom(p);
        verify(callback, times(1)).statusUpdated(eq(thing), argThat(arg -> arg.getStatus().equals(ThingStatus.ONLINE)));
        ChannelUID cuid = new ChannelUID(new ThingUID("1:2:3"), CONTROLLER_SPACIRCUIT + "#switch");
        verify(callback, times(1)).stateUpdated(cuid, OnOffType.ON);
        cuid = new ChannelUID(new ThingUID("1:2:3"), CONTROLLER_POOLCIRCUIT + "#switch");
        verify(callback, times(1)).stateUpdated(cuid, OnOffType.ON);
        cuid = new ChannelUID(new ThingUID("1:2:3"), CONTROLLER_STATUS + "#" + CONTROLLER_AIRTEMPERATURE);
        verify(callback, times(1)).stateUpdated(cuid, new QuantityType<>(65, ImperialUnits.FAHRENHEIT));

        Mockito.reset(callback);

        p = new PentairPacket(packets[1], packets[1].length);
        handler.processPacketFrom(p);

        Mockito.reset(callback);

        p = new PentairPacket(packets[2], packets[2].length);
        handler.processPacketFrom(p);
        cuid = new ChannelUID(new ThingUID("1:2:3"), CONTROLLER_POOLCIRCUIT + "#switch");
        verify(callback, times(1)).stateUpdated(cuid, OnOffType.OFF);
        cuid = new ChannelUID(new ThingUID("1:2:3"), CONTROLLER_FEATURE3 + "#switch");
        verify(callback, times(1)).stateUpdated(cuid, OnOffType.ON);
        cuid = new ChannelUID(new ThingUID("1:2:3"), CONTROLLER_FEATURE4 + "#switch");
        verify(callback, times(1)).stateUpdated(cuid, OnOffType.ON);
        cuid = new ChannelUID(new ThingUID("1:2:3"), CONTROLLER_STATUS + "#" + CONTROLLER_AIRTEMPERATURE);
        verify(callback, times(1)).stateUpdated(cuid, new QuantityType<>(69, ImperialUnits.FAHRENHEIT));

        p = new PentairPacket(packets[3], packets[3].length);
        handler.processPacketFrom(p);
    }
}
