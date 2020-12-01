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

import static org.openhab.binding.pentair.internal.PentairBindingConstants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pentair.internal.PentairIntelliChlorPacket;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PentairIntelliChlorHandler} is responsible for implementation of the Intellichlor Salt generator. It will
 * process
 * Intellichlor commands and set the appropriate channel states. There are currently no commands implemented for this
 * Thing to receive from the framework.
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class PentairIntelliChlorHandler extends PentairBaseThingHandler {
    private final Logger logger = LoggerFactory.getLogger(PentairIntelliChlorHandler.class);

    protected int version;
    protected String name = "";

    /** for a saltoutput packet, represents the salt output percent */
    public int saltOutput;
    /** for a salinity packet, is value of salinity. Must be multiplied by 50 to get the actual salinity value. */
    public int salinity;

    public boolean ok;
    public boolean lowFlow;
    public boolean lowSalt;
    public boolean veryLowSalt;
    public boolean highCurrent;
    public boolean cleanCell;
    public boolean lowVoltage;
    public boolean lowWaterTemp;
    public boolean commError;

    public static @Nullable PentairIntelliChlorHandler onlineChlorinator;

    public PentairIntelliChlorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void readConfiguration() {
        // IntelliChlor has no configuration parameters, however we need to set id to 0
        this.id = 0;
    }

    @Override
    public void goOnline() {
        if (onlineChlorinator != null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Another IntelliChlor is already configured.");
            return;
        } else {
            onlineChlorinator = this;
            super.goOnline();
        }
    }

    @Override
    public void goOffline(ThingStatusDetail detail) {
        super.goOffline(detail);
        onlineChlorinator = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            logger.trace("IntelliChlor received refresh command");

            switch (channelUID.getId()) {
                case INTELLICHLOR_SALTOUTPUT:
                    updateChannel(INTELLICHLOR_SALTOUTPUT, saltOutput);
                    break;
                case INTELLICHLOR_SALINITY:
                    updateChannel(INTELLICHLOR_SALINITY, salinity);
                    break;
                case INTELLICHLOR_OK:
                    updateChannel(INTELLICHLOR_OK, ok);
                    break;
                case INTELLICHLOR_LOWFLOW:
                    updateChannel(INTELLICHLOR_LOWFLOW, lowFlow);
                    break;
                case INTELLICHLOR_LOWSALT:
                    updateChannel(INTELLICHLOR_LOWSALT, lowSalt);
                    break;
                case INTELLICHLOR_VERYLOWSALT:
                    updateChannel(INTELLICHLOR_VERYLOWSALT, veryLowSalt);
                    break;
                case INTELLICHLOR_HIGHCURRENT:
                    updateChannel(INTELLICHLOR_HIGHCURRENT, highCurrent);
                    break;
                case INTELLICHLOR_CLEANCELL:
                    updateChannel(INTELLICHLOR_CLEANCELL, cleanCell);
                    break;
                case INTELLICHLOR_LOWVOLTAGE:
                    updateChannel(INTELLICHLOR_LOWVOLTAGE, lowVoltage);
                    break;
                case INTELLICHLOR_LOWWATERTEMP:
                    updateChannel(INTELLICHLOR_LOWWATERTEMP, lowWaterTemp);
                    break;
                case INTELLICHLOR_COMMERROR:
                    updateChannel(INTELLICHLOR_COMMERROR, commError);
                    break;
            }
        }
    }

    @Override
    public void processPacketFrom(PentairPacket p) {
        if (waitStatusForOnline) { // Only go online after first response from the Intellichlor
            finishOnline();
        }

        PentairIntelliChlorPacket pic = (PentairIntelliChlorPacket) p;

        switch (p.getAction()) {
            case 0x03:
                version = pic.getVersion();
                name = pic.getName();

                Map<String, String> editProperties = editProperties();
                editProperties.put(INTELLICHLOR_PROPERTYVERSION, Integer.toString(version));
                editProperties.put(INTELLICHLOR_PROPERTYMODEL, name);
                updateProperties(editProperties);

                logger.debug("Intellichlor version: {}, {}", version, name);
                break;

            case 0x11: // set salt output % command
                saltOutput = pic.getSaltOutput();
                updateChannel(INTELLICHLOR_SALTOUTPUT, saltOutput);
                logger.debug("Intellichlor set output % {}", saltOutput);
                break;
            case 0x12: // response to set salt output
                salinity = pic.getSalinity();

                ok = pic.getOk();
                lowFlow = pic.getLowFlow();
                lowSalt = pic.getLowSalt();
                veryLowSalt = pic.getVeryLowSalt();
                highCurrent = pic.getHighCurrent();
                cleanCell = pic.getCleanCell();
                lowVoltage = pic.getLowVoltage();
                lowWaterTemp = pic.getLowWaterTemp();

                updateChannel(INTELLICHLOR_SALINITY, salinity);
                updateChannel(INTELLICHLOR_OK, ok);
                updateChannel(INTELLICHLOR_LOWFLOW, lowFlow);
                updateChannel(INTELLICHLOR_LOWSALT, lowSalt);
                updateChannel(INTELLICHLOR_VERYLOWSALT, veryLowSalt);
                updateChannel(INTELLICHLOR_HIGHCURRENT, highCurrent);
                updateChannel(INTELLICHLOR_CLEANCELL, cleanCell);
                updateChannel(INTELLICHLOR_LOWVOLTAGE, lowVoltage);
                updateChannel(INTELLICHLOR_LOWWATERTEMP, lowWaterTemp);

                if (logger.isDebugEnabled()) {
                    String status = String.format(
                            "saltoutput = %d, salinity = %d, ok = %b, lowflow = %b, lowsalt = %b, verylowsalt = %b, highcurrent = %b, cleancell = %b, lowvoltage = %b, lowwatertemp = %b",
                            saltOutput, salinity, ok, lowFlow, lowSalt, veryLowSalt, highCurrent, cleanCell, lowVoltage,
                            lowWaterTemp);
                    logger.debug("IntelliChlor salinity/status: {}, {}", salinity, status);
                }
        }
    }
}
