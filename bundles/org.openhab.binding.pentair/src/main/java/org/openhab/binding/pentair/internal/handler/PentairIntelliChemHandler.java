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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.pentair.internal.PentairIntelliChem;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.openhab.binding.pentair.internal.config.PentairIntelliChemHandlerConfig;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PentairIntelliChemHandler} is responsible for implementation of the IntelliChemp. This will
 * parse of status packets to set the stat for various channels.
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class PentairIntelliChemHandler extends PentairBaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(PentairIntelliChemHandler.class);

    protected PentairIntelliChem pic = new PentairIntelliChem();

    public PentairIntelliChemHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void readConfiguration() {
        PentairIntelliChemHandlerConfig config = getConfigAs(PentairIntelliChemHandlerConfig.class);

        this.id = config.id;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handleCommand, {}, {}", channelUID, command);

        // all fields are read only
        if (!(command instanceof RefreshType)) {
            return;
        }

        // The IntelliChem routinely updates the state, so just refresh to last state
        switch (channelUID.getId()) {
            case INTELLICHEM_PHREADING:
                updateChannel(INTELLICHEM_PHREADING, pic.phReading);
                break;
            case INTELLICHEM_ORPREADING:
                updateChannel(INTELLICHEM_ORPREADING, pic.orpReading);
                break;
            case INTELLICHEM_PHSETPOINT:
                updateChannel(INTELLICHEM_PHSETPOINT, pic.phSetPoint);
                break;
            case INTELLICHEM_ORPSETPOINT:
                updateChannel(INTELLICHEM_ORPSETPOINT, pic.orpSetPoint);
                break;
            case INTELLICHEM_TANK1:
                updateChannel(INTELLICHEM_TANK1, pic.tank1);
                break;
            case INTELLICHEM_TANK2:
                updateChannel(INTELLICHEM_TANK2, pic.tank2);
                break;
            case INTELLICHEM_CALCIUMHARDNESS:
                updateChannel(INTELLICHEM_CALCIUMHARDNESS, pic.calciumHardness);
                break;
            case INTELLICHEM_CYAREADING:
                updateChannel(INTELLICHEM_CYAREADING, pic.cyaReading);
                break;
            case INTELLICHEM_TOTALALKALINITY:
                updateChannel(INTELLICHEM_TOTALALKALINITY, pic.totalAlkalinity);
                break;
            case INTELLICHEM_WATERFLOWALARM:
                updateChannel(INTELLICHEM_WATERFLOWALARM, pic.waterFlowAlarm);
                break;
            case INTELLICHEM_MODE1:
                updateChannel(INTELLICHEM_MODE1, pic.mode1);
                break;
            case INTELLICHEM_MODE2:
                updateChannel(INTELLICHEM_MODE2, pic.mode2);
                break;
            case INTELLICHEM_SATURATIONINDEX:
                updateChannel(INTELLICHEM_SATURATIONINDEX, pic.saturationIndex);
                break;
        }
    }

    @Override
    public void processPacketFrom(PentairPacket p) {
        if (waitStatusForOnline) {
            finishOnline();
        }

        switch (p.getAction()) {
            case 0x12: // A5 10 09 10 E3 02 AF 02 EE 02 BC 00 00 00 02 00 00 00 2A 00 04 00 5C 06 05 18 01 90 00 00 00
                       // 96
                       // 14 00 51 00 00 65 20 3C 01 00 00 00

                pic.parsePacket(p);
                logger.debug("Intellichem status: {}: ", pic.toString());

                updateChannel(INTELLICHEM_PHREADING, pic.phReading);
                updateChannel(INTELLICHEM_ORPREADING, pic.orpReading);
                updateChannel(INTELLICHEM_PHSETPOINT, pic.phSetPoint);
                updateChannel(INTELLICHEM_ORPSETPOINT, pic.orpSetPoint);
                updateChannel(INTELLICHEM_TANK1, pic.tank1);
                updateChannel(INTELLICHEM_TANK2, pic.tank2);
                updateChannel(INTELLICHEM_CALCIUMHARDNESS, pic.calciumHardness);
                updateChannel(INTELLICHEM_CYAREADING, pic.cyaReading);
                updateChannel(INTELLICHEM_TOTALALKALINITY, pic.totalAlkalinity);
                updateChannel(INTELLICHEM_WATERFLOWALARM, pic.waterFlowAlarm);
                updateChannel(INTELLICHEM_MODE1, pic.mode1);
                updateChannel(INTELLICHEM_MODE2, pic.mode2);
                updateChannel(INTELLICHEM_SATURATIONINDEX, pic.saturationIndex);

                break;

            default:
                logger.debug("Unhandled Intellichem packet: {}", p.toString());
                break;
        }
    }
}
