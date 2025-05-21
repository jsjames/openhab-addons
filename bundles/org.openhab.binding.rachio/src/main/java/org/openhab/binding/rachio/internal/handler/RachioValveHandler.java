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

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;
import static org.openhab.binding.rachio.internal.RachioUtils.isUpdateRequired;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.RachioBindingConstants;
import org.openhab.binding.rachio.internal.RachioUtils;
import org.openhab.binding.rachio.internal.api.RachioApi;
import org.openhab.binding.rachio.internal.api.RachioApiException;
import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEvent;
import org.openhab.binding.rachio.internal.api.dto.RachioApiValve;
import org.openhab.binding.rachio.utils.ClientRateLimitManager.RateLimitThrottleException;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RachioValveHandler} is responsible for handling commands, which are
 * sent to one of the Valve channels.
 *
 * @author Jeff James - Initial Contribution
 */

@NonNullByDefault
public class RachioValveHandler extends AbstractRachioThingHandler<RachioBaseStationHandler, RachioId.Valve> {
    private final Logger logger = LoggerFactory.getLogger(RachioValveHandler.class);

    private RachioApiValve rachioApiValve = RachioApiValve.EMPTY;

    private boolean running;
    private long runningDuration;
    private Instant runningStart = Instant.MIN;

    public RachioValveHandler(Thing thing, final RachioId.Valve valveId, final RachioApi api) {
        super(thing, valveId, api);
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

        RachioApiValve rachioApiValve = getBridgeHandler().getValveById(id);
        if (rachioApiValve == null) {
            logger.error("RachioApiValve is null");
            return;
        }
        this.rachioApiValve = rachioApiValve;

        updateProperties();
        postChannelData(rachioApiValve, true, null);

        updateStatus(ThingStatus.ONLINE);
    }

    public void goOffline(ThingStatusDetail thingStatusDetail, @Nullable String description) {
        updateStatus(ThingStatus.OFFLINE, thingStatusDetail, description);
    }

    public void updateValveRunning(Instant start, long durationSeconds) {
        if (start == Instant.MIN) {
            runningDuration = 0;
            running = false;
            runningStart = Instant.MIN;
        } else {
            runningDuration = Math.max(0, durationSeconds - Duration.between(start, Instant.now()).toSeconds());
            running = runningDuration > 0;
            runningStart = start;
        }
        updateState(CHANNEL_VALVE_RUN, OnOffType.from(running));
        updateState(CHANNEL_VALVE_RUN_TIME, new QuantityType<>(runningDuration, Units.SECOND));
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channel = channelUID.getId();
        logger.debug("Handle command {} for {}", command.toString(), channelUID.getAsString());

        if (command == RefreshType.REFRESH) {
            RachioApiValve localRachioApiValve = getBridgeHandler().getValveById(id);
            if (localRachioApiValve == null) {
                return;
            }
            postChannelData(localRachioApiValve, true, channel);
            rachioApiValve = localRachioApiValve;

            return;
        }

        try {
            long runTime;
            switch (channel) {
                case CHANNEL_VALVE_RUN:
                    if (command == OnOffType.ON) {
                        runTime = getDefaultRunTime();
                        logger.debug("Starting Valve {} for {} min", rachioApiValve.name(), runTime);
                        api.putValveStartWatering(id, runTime);
                        updateValveRunning(Instant.now(), runTime);
                    } else {
                        api.putValveStopWatering(id);
                        updateValveRunning(Instant.MIN, 0);
                    }
                    break;
                case CHANNEL_VALVE_RUN_TIME:
                    runTime = RachioUtils.getRuntimeFromCommand(command);
                    if (runTime > 0) {
                        api.putValveStartWatering(id, runTime);
                        updateValveRunning(Instant.now(), runTime);
                        logger.debug("Valve {} will start for {} min", rachioApiValve.name(), runTime);
                    }
                    break;
                case CHANNEL_VALVE_DEFAULT_RUN_TIME:
                    runTime = RachioUtils.getRuntimeFromCommand(command);
                    if (runTime > 0) {
                        api.putValveDefaultRunTime(id, runTime);
                    }
                    break;
            }
        } catch (RachioApiException | InterruptedException | TimeoutException | ExecutionException
                | RateLimitThrottleException e) {
            logger.error("Unable to process api command ({}) on channel {}", command.toString(), channel);
            return;
        }
    }

    public void onStatusRefresh(RachioApiValve rachioApiValve) {
        if (getThing().getStatus() == ThingStatus.OFFLINE
                && getThing().getStatusInfo().getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR) {
            goOnline();
            return;
        }

        if (!rachioApiValve.id().equals(id)) {
            logger.error("Valve ID does not match configuration");
            return;
        }

        ThingStatus valveStatus = (rachioApiValve.state() != null && rachioApiValve.state().reportedState() != null
                && rachioApiValve.state().reportedState().connected()) ? ThingStatus.ONLINE : ThingStatus.OFFLINE;
        ThingStatus thingStatus = getThing().getStatus();
        if (thingStatus == ThingStatus.OFFLINE && valveStatus == ThingStatus.ONLINE) {
            goOnline();
            return;
        } else if (thingStatus == ThingStatus.ONLINE && valveStatus == ThingStatus.OFFLINE) {
            goOffline(ThingStatusDetail.NONE, "@text/valve-offline");
            // still proceed to update channels
        }

        postChannelData(rachioApiValve, false, null);
    }

    private long getDefaultRunTime() {
        if (rachioApiValve.state() != null && rachioApiValve.state().reportedState() != null) {
            return rachioApiValve.state().reportedState().defaultRuntimeSeconds();
        }
        return 0;
    }

    public boolean webhookEvent(RachioApiEvent event) {
        return true;
    }

    public void postChannelData(@Nullable RachioApiValve newRachioApiValve, boolean forceUpdate,
            @Nullable String updateChannel) {
        if (rachioApiValve == RachioApiValve.EMPTY && newRachioApiValve == null) {
            logger.error("Invalid state - both new and old RachioApiValve are not specfied");
        } else if (rachioApiValve == RachioApiValve.EMPTY) {
            forceUpdate = true;
        } else if (newRachioApiValve == null) { // use stored state
            newRachioApiValve = rachioApiValve;
        }

        String oldName = rachioApiValve.name();
        String newName = (newRachioApiValve != null && newRachioApiValve.name() != null) ? newRachioApiValve.name()
                : "";
        if (isUpdateRequired(CHANNEL_VALVE_NAME, forceUpdate, updateChannel, oldName, newName)) {
            updateState(CHANNEL_VALVE_NAME, new StringType(newName));
        }

        String oldPhotoId = (rachioApiValve.photo() != null) ? rachioApiValve.photo().id() : null;
        String newPhotoId = (newRachioApiValve.photo() != null) ? newRachioApiValve.photo().id() : null;
        if (isUpdateRequired(CHANNEL_VALVE_IMAGEURL, forceUpdate, updateChannel, oldPhotoId, newPhotoId)
                || CHANNEL_VALVE_IMAGE.equals(updateChannel)) {
            String imageUrl = RachioApi.API_PHOTO_BASE + newPhotoId;
            updateState(CHANNEL_VALVE_IMAGEURL, new StringType(imageUrl));
            if (isLinked(CHANNEL_VALVE_IMAGE)) {
                RawType imageRawType = api.getImageFromURL(imageUrl);
                if (imageRawType != null) {
                    updateState(CHANNEL_VALVE_IMAGE, imageRawType);
                }
            }
        }

        RachioApiValve.ReportedState currentReportedState = (rachioApiValve.state() != null)
                ? rachioApiValve.state().reportedState()
                : null;
        RachioApiValve.ReportedState newReportedState = (newRachioApiValve.state() != null)
                ? newRachioApiValve.state().reportedState()
                : null;

        String oldBatteryStatus = (currentReportedState != null) ? currentReportedState.batteryStatus() : null;
        String newBatteryStatus = (newReportedState != null) ? newReportedState.batteryStatus() : null;
        if (isUpdateRequired(CHANNEL_VALVE_LOW_BATTERY, forceUpdate, updateChannel, oldBatteryStatus,
                newBatteryStatus)) {
            updateState(CHANNEL_VALVE_LOW_BATTERY, OnOffType.from(!"GOOD".equals(newBatteryStatus)));
        }

        Long oldDefaultRuntime = (currentReportedState != null) ? currentReportedState.defaultRuntimeSeconds() : null;
        Long newDefaultRuntime = (newReportedState != null) ? newReportedState.defaultRuntimeSeconds() : null;
        if (isUpdateRequired(CHANNEL_VALVE_DEFAULT_RUN_TIME, forceUpdate, updateChannel, oldDefaultRuntime,
                newDefaultRuntime)) {
            updateState(CHANNEL_VALVE_DEFAULT_RUN_TIME,
                    new QuantityType<>(newDefaultRuntime != null ? newDefaultRuntime : 0, Units.SECOND));
        }

        RachioApiValve.LastWateringAction lastWateringAction = (newReportedState != null)
                ? newReportedState.lastWateringAction()
                : null;
        if (lastWateringAction == null) {
            updateValveRunning(Instant.MIN, 0);
        } else {
            updateValveRunning(lastWateringAction.start(), lastWateringAction.durationSeconds());
        }
        rachioApiValve = newRachioApiValve;
    }

    private void updateProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, RachioBindingConstants.BINDING_VENDOR);
        properties.put(PROPERTY_NAME, rachioApiValve.name());
        String connectionId = rachioApiValve.connectionId();
        if (connectionId != null && connectionId.contains("-")) {
            properties.put(Thing.PROPERTY_SERIAL_NUMBER, connectionId.substring(connectionId.indexOf("-") + 1));
        }
        properties.put(PROPERTY_VALVE_COLOR, rachioApiValve.color());
        if (rachioApiValve.state() != null && rachioApiValve.state().reportedState() != null) {
            properties.put(PROPERTY_VALVE_FW, rachioApiValve.state().reportedState().firmwareVersion());
        }
        updateProperties(properties);
    }
}
