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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.RachioBindingConstants;
import org.openhab.binding.rachio.internal.RachioUtils;
import org.openhab.binding.rachio.internal.api.RachioApi;
import org.openhab.binding.rachio.internal.api.RachioApiException;
import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.internal.api.dto.RachioApiBaseStation;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEvent;
import org.openhab.binding.rachio.internal.api.dto.RachioApiValve;
import org.openhab.binding.rachio.internal.discovery.RachioDiscoveryService;
import org.openhab.binding.rachio.utils.ClientRateLimitManager.RateLimitThrottleException;
import org.openhab.core.cache.ExpiringCache;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RachioBaseStationHandler}
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class RachioBaseStationHandler
        extends AbstractRachioBridgeHandler<RachioCloudConnectorHandler, RachioId.BaseStation, RachioId.Valve> {
    private final Logger logger = LoggerFactory.getLogger(RachioBaseStationHandler.class);

    RachioApiBaseStation rachioApiBaseStation = RachioApiBaseStation.EMPTY;

    private Map<RachioId.Valve, RachioValveHandler> valveHandlers = new HashMap<>();
    private ExpiringCache<Map<RachioId.Valve, RachioApiValve>> rachioApiValvesCache = new ExpiringCache<>(
            Duration.ofSeconds(30), this::getApiValves);

    public RachioBaseStationHandler(final Bridge thing, final RachioId.BaseStation baseStationId, final RachioApi api,
            RachioCloudConnectorHandler cloudConnectorHandler) {
        super(thing, baseStationId, api, cloudConnectorHandler);
    }

    @Override
    public void initialize() {
        super.initialize();
        scheduler.execute(this::goOnline);
    }

    public synchronized void goOnline() {
        if (getThing().getStatus() == ThingStatus.ONLINE || !checkBridgeStatus()) {
            return;
        }

        RachioApiBaseStation rachioApiBaseStation = getBridgeHandler().getBaseStationByID(id);
        if (rachioApiBaseStation == null) {
            logger.error("rachioApiBaseStation is null");
            return;
        }
        this.rachioApiBaseStation = rachioApiBaseStation;

        updateProperties();
        refreshValves();

        if (rachioApiBaseStation.reportedState().connected() == true) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "@text/base-station-offline");
        }
    }

    @Nullable
    protected Map<RachioId.Valve, RachioApiValve> getApiValves() {
        try {
            return api.getValves(id);
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException
                | RateLimitThrottleException e) {
            logger.error("Exception: {}", e.getLocalizedMessage());
        }
        return null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Base Station has no channels
        logger.debug("RachioBaseStation: Command {} for {} ignored", command, channelUID.getAsString());
    }

    @SuppressWarnings("null")
    public void refreshValves() {
        Map<RachioId.Valve, RachioApiValve> rachioApiValves = rachioApiValvesCache.getValue();
        RachioDiscoveryService discoveryService = getBridgeHandler().getDiscoveryService();

        if (rachioApiValves == null) {
            logger.error("Base station device does not have any valves");
            return;
        }

        RachioUtils.reconcileCollections(rachioApiValves.keySet(), valveHandlers.keySet(),
                id -> discoveryService.notifyDiscoveryValve(getThing().getUID(),
                        Objects.requireNonNullElse(getThing().getLabel(), ""),
                        Objects.requireNonNull(rachioApiValves.get(id))),
                id -> valveHandlers.get(id).goOffline(ThingStatusDetail.GONE, "@test/valve-removed"),
                id -> Objects.requireNonNull(valveHandlers.get(id))
                        .onStatusRefresh(Objects.requireNonNull(rachioApiValves.get(id))));

        // TODO: Update valve status
    }

    public void onStructureUpdate(RachioApiBaseStation rachioApiBaseStation) {
        if (getThing().getStatus() == ThingStatus.OFFLINE
                && getThing().getStatusInfo().getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR) {
            goOnline();
            return;
        }

        if (!rachioApiBaseStation.id().equals(id)) {
            logger.error("Controller ID does not match configuration.");
            return;
        }

        ThingStatus baseStationStatus = rachioApiBaseStation.reportedState().connected() ? ThingStatus.ONLINE
                : ThingStatus.OFFLINE;
        ThingStatus thingStatus = getThing().getStatus();
        if (thingStatus == ThingStatus.OFFLINE & baseStationStatus == ThingStatus.ONLINE) {
            goOnline();
            return;
        } else if (thingStatus == ThingStatus.ONLINE && baseStationStatus == ThingStatus.OFFLINE) {
            goOffline(ThingStatusDetail.NONE, "@text/controller-offline");
            // still proceed to update channels
        }

        this.rachioApiBaseStation = rachioApiBaseStation;
    }

    public void onPollingUpdate() {
        refreshValves();
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof RachioValveHandler rachioValveHandler) {
            valveHandlers.put(rachioValveHandler.getId(), rachioValveHandler);
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof RachioValveHandler rachioValveHandler) {
            valveHandlers.remove(rachioValveHandler.getId());
        }
    }

    private void updateProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, RachioBindingConstants.BINDING_VENDOR);
        properties.put(Thing.PROPERTY_SERIAL_NUMBER, rachioApiBaseStation.serialNumber());
        properties.put(Thing.PROPERTY_MAC_ADDRESS, rachioApiBaseStation.macAddress());

        updateProperties(properties);
    }

    public boolean webhookEvent(RachioApiEvent event) {
        switch (event.type()) {
            case RachioApiEvent.PROGRAM_RAIN_SKIP_CREATED_EVENT:
            case RachioApiEvent.PROGRAM_RAIN_SKIP_CANCELLED_EVENT:
                if (event.payload() instanceof RachioApiEvent.PayloadProgramRainSkip payloadProgramRainSkip) {
                    logger.info("EVENT: {}", event);
                }
                break;
            case RachioApiEvent.VALVE_RUN_START_EVENT:
            case RachioApiEvent.VALVE_RUN_END_EVENT:
                if (event.payload() instanceof RachioApiEvent.PayloadValveRun payloadValueRun) {
                    logger.info("EVENT: {}", event);
                }
                break;
        }
        return false;
    }

    @Nullable
    RachioApiValve getValveById(RachioId.Valve valveId) {
        Map<RachioId.Valve, RachioApiValve> rachioApiValves = rachioApiValvesCache.getValue();
        return (rachioApiValves != null) ? rachioApiValves.get(valveId) : null;
    }
}
