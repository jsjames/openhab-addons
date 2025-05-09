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
import static org.openhab.binding.rachio.internal.RachioUtils.*;

import java.time.Duration;
import java.time.Instant;
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
import org.openhab.binding.rachio.internal.api.dto.RachioApiEvent;
import org.openhab.binding.rachio.internal.api.dto.RachioApiValve;
import org.openhab.binding.rachio.utils.ClientRateLimitManager.RateLimitThrottleException;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tech.units.indriya.unit.Units;

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
        if (id.toString().isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "@text/valve-configuration-issue");
        }
        updateStatus(ThingStatus.UNKNOWN);
        // only goOnline if bridge is ONLINE, otherwise wait for bridgeStatusChange
        if (Objects.requireNonNull(getBridge()).getStatus() == ThingStatus.ONLINE) {
            scheduler.execute(this::goOnline);
        }
    }

    public void goOnline() {
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
                        logger.debug("Starting Valve {} for {} min", rachioApiValve.name, runTime);
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
                        logger.debug("Valve {} will start for {} min", rachioApiValve.name, runTime);
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

        if (!rachioApiValve.getId().equals(id)) {
            logger.error("Valve ID does not match configuration");
            return;
        }

        ThingStatus valveStatus = rachioApiValve.state.reportedState.connected ? ThingStatus.ONLINE
                : ThingStatus.OFFLINE;
        ThingStatus thingStatus = getThing().getStatus();
        if (thingStatus == ThingStatus.OFFLINE & valveStatus == ThingStatus.ONLINE) {
            goOnline();
            return;
        } else if (thingStatus == ThingStatus.ONLINE && valveStatus == ThingStatus.OFFLINE) {
            goOffline(ThingStatusDetail.NONE, "@text/valve-offline");
            // still proceed to update channels
        }

        postChannelData(rachioApiValve, false, null);
    }

    private long getDefaultRunTime() {
        return rachioApiValve.state.reportedState.defaultRuntimeSeconds;
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
            newRachioApiValve = Objects.requireNonNull(rachioApiValve);
            forceUpdate = true;
        }
        if (isUpdateRequired(CHANNEL_VALVE_NAME, forceUpdate, updateChannel, rachioApiValve.name,
                Objects.requireNonNullElse(newRachioApiValve.name, ""))) {
            updateState(CHANNEL_VALVE_NAME, new StringType(newRachioApiValve.name));
        }
        if (isUpdateRequired(CHANNEL_VALVE_IMAGEURL, forceUpdate, updateChannel,
                (rachioApiValve.photo != null) ? rachioApiValve.photo.id : null, newRachioApiValve.photo.id)
                || CHANNEL_VALVE_IMAGE.equals(updateChannel)) {
            String imageUrl = RachioApi.API_PHOTO_BASE + newRachioApiValve.photo.id;
            updateState(CHANNEL_VALVE_IMAGEURL, new StringType(imageUrl));
            if (isLinked(CHANNEL_VALVE_IMAGE)) {
                RawType imageRawType = api.getImageFromURL(imageUrl);
                if (imageRawType != null) {
                    updateState(CHANNEL_VALVE_IMAGE, imageRawType);
                }
            }
        }
        final RachioApiValve localNewRachioApiValve = newRachioApiValve; // require final for reference in lambda
                                                                         // function
        RachioApiValve.ReportedState currentReportedState = safeGet(() -> rachioApiValve.state.reportedState);
        RachioApiValve.ReportedState newReportedState = safeGet(() -> localNewRachioApiValve.state.reportedState);
        if (isUpdateRequired(CHANNEL_VALVE_LOW_BATTERY, forceUpdate, updateChannel,
                safeGet(() -> currentReportedState.batteryStatus), safeGet(() -> newReportedState.batteryStatus))) {
            updateState(CHANNEL_VALVE_LOW_BATTERY,
                    OnOffType.from(!"GOOD".equals(newRachioApiValve.state.reportedState.batteryStatus)));
        }
        if (isUpdateRequired(CHANNEL_VALVE_DEFAULT_RUN_TIME, forceUpdate, updateChannel,
                (currentReportedState != null) ? currentReportedState.defaultRuntimeSeconds : null,
                newRachioApiValve.state.reportedState.defaultRuntimeSeconds)) {
            updateState(CHANNEL_VALVE_DEFAULT_RUN_TIME,
                    new QuantityType<>(newRachioApiValve.state.reportedState.defaultRuntimeSeconds, Units.SECOND));
        }
        RachioApiValve.LastWateringAction lastWateringAction = safeGet(() -> newReportedState.lastWateringAction);
        if (lastWateringAction == null) {
            updateValveRunning(Instant.MIN, 0);
        } else {
            updateValveRunning(lastWateringAction.start, lastWateringAction.durationSeconds);
        }

        // TODO updateChannel(CHANNEL_LAST_EVENT, new StringType(z.getEvent()));
        // DateTimeType ts = z.getEventTime();
        // updateChannel(RachioBindingConstants.CHANNEL_LAST_EVENTTS, ts != null ? ts : UnDefType.UNDEF);

        rachioApiValve = newRachioApiValve;
    }

    private void updateProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, RachioBindingConstants.BINDING_VENDOR);
        properties.put(PROPERTY_NAME, rachioApiValve.name);
        properties.put(Thing.PROPERTY_SERIAL_NUMBER,
                rachioApiValve.connectionId.substring(rachioApiValve.connectionId.indexOf("-") + 1));
        properties.put(PROPERTY_VALVE_COLOR, rachioApiValve.color);
        properties.put(PROPERTY_VALVE_FW, rachioApiValve.state.reportedState.firmwareVersion);
        updateProperties(properties);
    }
}
