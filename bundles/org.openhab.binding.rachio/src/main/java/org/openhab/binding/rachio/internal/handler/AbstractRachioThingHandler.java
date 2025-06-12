/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.rachio.internal.handler;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.api.RachioApi;
import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;

/**
 * {@link AbstractRachioThingHandler}
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public abstract class AbstractRachioThingHandler<BH extends AbstractRachioBridgeHandler<?, ?, ID>, ID extends RachioId.Id>
        extends BaseThingHandler {
    protected final ID id;
    protected final RachioApi api;

    public AbstractRachioThingHandler(final Thing thing, ID id, final RachioApi api) {
        super(thing);
        this.api = api;
        this.id = id;
    }

    public void initialize() {
        if (id.idString().isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "@text/configuration-issue");
            return;
        }
    }

    abstract public void goOnline();

    public void goOffline(ThingStatusDetail thingStatusDetail, @Nullable String description) {
        updateStatus(ThingStatus.OFFLINE, thingStatusDetail, description);
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            goOnline();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    public boolean checkBridgeStatus() {
        Bridge bridge = this.getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.configuration-error.bridge-missing");
            return false;
        }

        BH bridgeHandler = (BH) bridge.getHandler();
        if (bridgeHandler == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return false;
        }

        if (bridgeHandler.getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return false;
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    public BH getBridgeHandler() {
        Bridge bridge = Objects.requireNonNull(getBridge(), "Invalid state, no bridge");
        BridgeHandler handler = Objects.requireNonNull(bridge.getHandler(), "Invalid state, no bridge handler");

        return (BH) handler;
    }

    public ID getId() {
        return id;
    }
}
