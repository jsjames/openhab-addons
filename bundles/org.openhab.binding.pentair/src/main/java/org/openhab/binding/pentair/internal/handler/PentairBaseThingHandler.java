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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for all Pentair Things.
 *
 * @author Jeff James - Initial contribution
 *
 */
@NonNullByDefault
public abstract class PentairBaseThingHandler extends BaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(PentairBaseThingHandler.class);

    /** ID of Thing on Pentair bus */
    protected int id;
    protected boolean waitStatusForOnline = false;

    public PentairBaseThingHandler(Thing thing) {
        super(thing);
    }

    // this method will be overridden by
    public abstract void readConfiguration();

    @Override
    public void initialize() {
        readConfiguration();

        PentairBaseBridgeHandler bh = getBridgeHandler();

        if (bh == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Device cannot be created without a bridge.");
            return;
        }

        if (bh.equipment.get(id) != null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "A device with this ID has already been initialized.");
            return;
        }

        bh.childHandlerInitializing(this, this.getThing());

        waitStatusForOnline = false;
        goOnline();

        updateStatus(ThingStatus.UNKNOWN);
    }

    public void goOnline() {
        waitStatusForOnline = true;
    }

    public void finishOnline() {
        waitStatusForOnline = false;
        updateStatus(ThingStatus.ONLINE);
    }

    public void goOffline(ThingStatusDetail detail) {
        updateStatus(ThingStatus.OFFLINE, detail);
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            goOffline(ThingStatusDetail.BRIDGE_OFFLINE);
        } else if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            waitStatusForOnline = false;
            goOnline();
        }
    }

    /**
     * Gets Pentair bus ID of Thing
     *
     * @return
     */
    public int getPentairID() {
        return id;
    }

    public void writePacket(byte[] packet) {
        writePacket(packet, -1, 0);
    }

    public boolean writePacket(byte[] packet, int response, int retries) {
        PentairPacket p = new PentairPacket(packet);

        return writePacket(p, response, retries);
    }

    public boolean writePacket(PentairPacket p, int response, int retries) {
        Bridge bridge = this.getBridge();
        if (bridge == null) {
            return false;
        }

        PentairBaseBridgeHandler bbh = (PentairBaseBridgeHandler) bridge.getHandler();
        if (bbh == null) {
            return false;
        }

        return bbh.writePacket(p, response, retries);
    }

    public @Nullable PentairBaseBridgeHandler getBridgeHandler() {
        // make sure bridge exists and is online
        Bridge bridge = this.getBridge();
        if (bridge == null) {
            return null;
        }
        PentairBaseBridgeHandler bh = (PentairBaseBridgeHandler) bridge.getHandler();
        if (bh == null) {
            return null;
        }

        return bh;
    }

    /**
     * Helper function to update channel.
     */
    public void updateChannel(String channel, boolean value) {
        updateState(channel, (value) ? OnOffType.ON : OnOffType.OFF);
    }

    public void updateChannel(String channel, int value) {
        updateState(channel, new DecimalType(value));
    }

    public void updateChannel(String channel, double value) {
        updateState(channel, new DecimalType(value));
    }

    public void updateChannel(String channel, String value) {
        updateState(channel, new StringType(value));
    }

    public void updateChannelPower(String channel, int value) {
        updateState(channel, new QuantityType<>(value, Units.WATT));
    }

    /**
     * Abstract function to be implemented by Thing to parse a received packet
     *
     * @param p
     */
    public abstract void processPacketFrom(PentairPacket p);
}
