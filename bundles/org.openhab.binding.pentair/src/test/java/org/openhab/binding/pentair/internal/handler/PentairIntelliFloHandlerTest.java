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
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PentailIntelliFlowHandlerTest }
 *
 * @author Jeff James - Initial contribution
 */
public class PentairIntelliFloHandlerTest {
    private final Logger logger = LoggerFactory.getLogger(PentairIntelliFloHandlerTest.class);

    //@formatter:off
    public static byte[][] packets = {
            parsehex("A5 00 22 60 07 0F 0A 02 02 00 E7 06 D6 00 00 00 00 00 01 02 03"),
            parsehex("A5 00 22 60 07 0F 0A 00 00 01 F9 07 D5 00 00 00 00 09 21 0A 3A"),          // SVRS alarm
            parsehex("a5 00 10 60 07 0f 0a 02 02 00 5a 02 ee 00 00 00 00 00 01 15 1f"),
            parsehex("A5 00 10 60 07 0F 04 00 00 00 00 00 00 00 00 00 00 00 00 14 1E")
    };
    //@formatter:on

    private PentairIntelliFloHandler handler;

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

        handler = new PentairIntelliFloHandler(thing) {
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
        ChannelUID cuid;
        PentairPacket p;

        handler.initialize();

        p = new PentairPacket(packets[0], packets[0].length);
        handler.processPacketFrom(p);
        verify(callback, times(1)).statusUpdated(eq(thing), argThat(arg -> arg.getStatus().equals(ThingStatus.ONLINE)));
        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLIFLO_RUN);
        verify(callback, times(1)).stateUpdated(cuid, OnOffType.ON);
        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLIFLO_POWER);
        verify(callback, times(1)).stateUpdated(cuid, new QuantityType<>(231, Units.WATT));
        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLIFLO_RPM);
        verify(callback, times(1)).stateUpdated(cuid, new DecimalType(1750));

        Mockito.reset(callback);

        p = new PentairPacket(packets[1], packets[1].length);
        handler.processPacketFrom(p);
        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLIFLO_RUN);
        verify(callback, times(1)).stateUpdated(cuid, OnOffType.ON);
        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLIFLO_POWER);
        verify(callback, times(1)).stateUpdated(cuid, new QuantityType<>(505, Units.WATT));
        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLIFLO_RPM);
        verify(callback, times(1)).stateUpdated(cuid, new DecimalType(2005));

        Mockito.reset(callback);

        p = new PentairPacket(packets[2], packets[2].length);
        handler.processPacketFrom(p);

        Mockito.reset(callback);

        p = new PentairPacket(packets[3], packets[3].length);
        handler.processPacketFrom(p);
        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLIFLO_RUN);
        verify(callback, times(1)).stateUpdated(cuid, OnOffType.OFF);
        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLIFLO_POWER);
        verify(callback, times(1)).stateUpdated(cuid, new QuantityType<>(0, Units.WATT));
        cuid = new ChannelUID(new ThingUID("1:2:3"), INTELLIFLO_RPM);
        verify(callback, times(1)).stateUpdated(cuid, new DecimalType(0));
    }
}
