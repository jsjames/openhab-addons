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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.api.RachioApi;
import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEvent;
import org.openhab.binding.rachio.internal.utils.RachioUtils;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;

/**
 * {@link AbstractRachioBridgeHandler}
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public abstract class AbstractRachioBridgeHandler<BH extends BaseBridgeHandler, ID extends RachioId.Id, CH_ID extends RachioId.Id>
        extends BaseBridgeHandler {
    protected final RachioApi api;
    protected final ID id;
    protected final RachioCloudConnectorHandler cloudConnectorHandler;
    protected Map<CH_ID, AbstractRachioThingHandler<?, ?>> childHandlers = new HashMap<>();

    public AbstractRachioBridgeHandler(final Bridge thing, ID id, final RachioApi api,
            RachioCloudConnectorHandler cloudConnectorHandler) {
        super(thing);
        this.api = api;
        this.id = id;
        this.cloudConnectorHandler = cloudConnectorHandler;
    }

    public void initialize() {
        if (id.idString().isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "@text/configuration-issue");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);
    }

    public abstract void goOnline();

    public void goOffline(ThingStatusDetail thingStatusDetail, @Nullable String description) {
        updateStatus(ThingStatus.OFFLINE, thingStatusDetail, description);
    }

    public abstract boolean webhookEvent(RachioApiEvent event);

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof AbstractRachioThingHandler<?, ?> rachioChildHandler) {
            @SuppressWarnings("unchecked")
            CH_ID childId = (CH_ID) rachioChildHandler.getId();
            childHandlers.put(childId, rachioChildHandler);
        }
    }

    public RachioCloudConnectorHandler getCloudConnectorHandler() {
        return cloudConnectorHandler;
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof AbstractRachioThingHandler<?, ?> rachioChildHandler) {
            @SuppressWarnings("unchecked")
            CH_ID childId = (CH_ID) rachioChildHandler.getId();
            childHandlers.remove(childId);
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            goOnline();
        } else if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
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

        BH bridgeHandler = getBridgeHandler();
        if (bridgeHandler.getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return false;
        }

        return true;
    }

    public String getExternalId() {
        return "OH_" + RachioUtils.getMD5Hash(getThing().getUID().getAsString());
    }

    @SuppressWarnings("unchecked")
    public BH getBridgeHandler() {
        Bridge bridge = Objects.requireNonNull(getBridge(), "Invalid state, no bridge");
        return (BH) bridge.getHandler();
    }

    public ID getId() {
        return id;
    }
}
