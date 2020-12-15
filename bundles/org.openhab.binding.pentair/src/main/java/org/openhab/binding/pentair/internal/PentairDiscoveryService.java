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

import static org.openhab.binding.pentair.internal.PentairBindingConstants.*;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pentair.internal.handler.PentairBaseBridgeHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PentairDiscoveryService} handles discovery of devices as they are identified by the bridge handler.
 * Requests from the framework to startScan() are ignored, since no active scanning is possible. (Leveraged from
 * AlarmDecoder)
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class PentairDiscoveryService extends AbstractDiscoveryService implements ThingHandlerService {

    private final Logger logger = LoggerFactory.getLogger(PentairDiscoveryService.class);

    private @Nullable PentairBaseBridgeHandler bridgeHandler;

    public PentairDiscoveryService() throws IllegalArgumentException {
        super(DISCOVERABLE_DEVICE_TYPE_UIDS, 0, false);
    }

    @Override
    public void activate() {
        super.activate(null);
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    protected void startScan() {
        // Ignore start scan requests
    }

    public void notifyDiscoveredController(int id) {
        Objects.requireNonNull(bridgeHandler, "Discovery with null bridgehandler.");
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        ThingUID uid = new ThingUID(CONTROLLER_THING_TYPE, bridgeUID, CONTROLLER);

        DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID).withProperty(PARAMETER_ID, id)
                .withLabel("Controller").build();
        thingDiscovered(result);
        logger.debug("Discovered Controller {}", uid);
    }

    public void notifyDiscoverdIntelliflo(int id) {
        int pumpid = (id & 0x04) + 1;

        Objects.requireNonNull(bridgeHandler, "Discovery with null bridgehandler.");
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        ThingUID uid = new ThingUID(INTELLIFLO_THING_TYPE, bridgeUID, "pump" + pumpid);

        DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID).withProperty(PARAMETER_ID, id)
                .withLabel("Pump").build();
        thingDiscovered(result);
        logger.debug("Discovered Pump {}", uid);
    }

    public void notifyDiscoveredIntellichlor(int id) {
        Objects.requireNonNull(bridgeHandler, "Discovery with null bridgehandler.");
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        ThingUID uid = new ThingUID(INTELLICHLOR_THING_TYPE, bridgeUID, INTELLICHLOR);

        DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID).withProperty(PARAMETER_ID, id)
                .withLabel("Intellichlor").withRepresentationProperty(CONTROLLER_PROPERTYID).build();
        thingDiscovered(result);
        logger.debug("Discovered Intellichlor {}", uid);
    }

    public void notifyDiscoveryIntellichem(int id) {
        Objects.requireNonNull(bridgeHandler, "Discovery with null bridgehandler.");
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        ThingUID uid = new ThingUID(INTELLICHEM_THING_TYPE, bridgeUID, INTELLICHEM);

        DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID).withProperty(PARAMETER_ID, id)
                .withLabel("IntelliChem").withRepresentationProperty(CONTROLLER_PROPERTYID).build();
        thingDiscovered(result);
        logger.debug("Discovered Intellichem {}", uid);
    }

    @Override
    public void setThingHandler(ThingHandler handler) {
        if (handler instanceof PentairBaseBridgeHandler) {
            this.bridgeHandler = (PentairBaseBridgeHandler) handler;
            this.bridgeHandler.setDiscoveryService(this);
        } else {
            this.bridgeHandler = null;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return this.bridgeHandler;
    }
}
