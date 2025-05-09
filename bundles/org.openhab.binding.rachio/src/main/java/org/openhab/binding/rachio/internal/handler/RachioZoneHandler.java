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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.api.RachioApi;
import org.openhab.binding.rachio.internal.api.RachioApiException;
import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEvent;
import org.openhab.binding.rachio.internal.api.dto.RachioApiZone;
import org.openhab.binding.rachio.internal.configuration.RachioZoneConfiguration;
import org.openhab.binding.rachio.utils.ClientRateLimitManager.RateLimitThrottleException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.ImperialUnits;
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
 * The {@link RachioZoneHandler} is responsible for handling commands, which are
 * sent to one of the zone channels.
 *
 * @author Markus Michels - Initial contribution
 * @author Jeff James - Modernized / Re-factored
 */

@NonNullByDefault
public class RachioZoneHandler extends AbstractRachioThingHandler<RachioControllerHandler, RachioId.Zone> {
    private final Logger logger = LoggerFactory.getLogger(RachioZoneHandler.class);

    private RachioZoneConfiguration config;
    private RachioApiZone rachioApiZone = RachioApiZone.EMPTY;

    private boolean running;
    private int runningDuration;

    public RachioZoneHandler(final Thing thing, final RachioId.Zone zoneId, final RachioApi api) {
        super(thing, zoneId, api);
        config = getConfigAs(RachioZoneConfiguration.class);
    }

    @Override
    public void initialize() {
        logger.debug("ZoneHandler Initialize");
        if (id.toString().isEmpty()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "@text/zone-configuration-issue");
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

        RachioApiZone rachioApiZone = getBridgeHandler().getZoneById(id);
        if (rachioApiZone == null) {
            logger.debug("RachioApiZone is null");
            return;
        }
        this.rachioApiZone = rachioApiZone;

        updateProperties();
        postChannelData(null, true, null);

        updateStatus(ThingStatus.ONLINE);
    }

    public void goOffline(ThingStatusDetail thingStatusDetail, @Nullable String description) {
        updateStatus(ThingStatus.OFFLINE, thingStatusDetail, description);
    }

    public void updateZoneRunning(boolean updateZoneRun, int duration) {
        running = updateZoneRun;
        runningDuration = duration;
        updateState(CHANNEL_ZONE_RUN, OnOffType.from(updateZoneRun));
        updateState(CHANNEL_ZONE_RUN_TIME, new QuantityType<>(duration, Units.MINUTE));
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channel = channelUID.getId();
        logger.debug("Handle command {} for {}", command.toString(), channelUID.getAsString());

        if (command == RefreshType.REFRESH) {
            RachioApiZone localRachioApiZone = getBridgeHandler().getZoneById(id);
            if (localRachioApiZone == null) {
                return;
            }
            postChannelData(localRachioApiZone, true, channel);
            rachioApiZone = localRachioApiZone;

            return;
        }

        try {
            switch (channel) {
                case CHANNEL_ZONE_RUN:
                    if (command == OnOffType.ON) {
                        int runtime = getDefaultRunTime();
                        logger.debug("Starting zone {} for {} min", rachioApiZone.name, runtime);
                        api.putZoneStartWatering(id, runtime * 60);
                        getBridgeHandler().updateZoneRunning();
                    } else {
                        api.putStopWatering(getBridgeHandler().getId());
                    }
                    break;
                case CHANNEL_ZONE_ENABLED:
                    if (command == OnOffType.ON) {
                        api.putZoneEnable(id);
                    } else {
                        api.putZoneDisable(id);
                    }
                    break;
                case CHANNEL_ZONE_RUN_TIME:
                    int runtime = (command instanceof QuantityType qtCommand) ? qtCommand.intValue() : 0;
                    runtime = (command instanceof DecimalType dtCommand) ? dtCommand.intValue() : runtime;

                    if (runtime > 0) {
                        api.putZoneStartWatering(id, runtime * 60);
                        getBridgeHandler().updateZoneRunning();
                        logger.debug("Zone {} will start for {} min", rachioApiZone.name, runtime);
                    }
                    break;
            }
        } catch (RachioApiException | InterruptedException | TimeoutException | ExecutionException
                | RateLimitThrottleException e) {
            logger.error("Unable to process api command ({}) on channel {}", command.toString(), channel);
            return;
        }
    }

    public void onStatusRefresh(RachioApiZone rachioApiZone) {
        if (getThing().getStatus() == ThingStatus.OFFLINE
                && getThing().getStatusInfo().getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR) {
            goOnline();
            return;
        }

        if (!rachioApiZone.getId().equals(id)) {
            logger.error("Zone ID does not match configuration");
            return;
        }

        postChannelData(rachioApiZone, false, null);
    }

    private int getDefaultRunTime() {
        return (config.defaultRunTime > 0) ? config.defaultRunTime : getBridgeHandler().getDefaultRunTime();
    }

    public boolean webhookEvent(RachioApiEvent event) {
        try {
            switch (event.type) {
                case "ZONE_STATUS":
                    switch (event.subType) {
                        case "ZONE_STARTED":
                            logger.info("Zone {} STARTED watering ({}).", rachioApiZone.name, event.timestamp);
                            // TODO duration
                            updateZoneRunning(true, 0);
                            break;
                        case "ZONE_STOPPED":
                            logger.info(
                                    "Zoned {} STOPPED watering (timestamp={}, current={}, duration={}sec/{}min, flowVolume={}).",
                                    rachioApiZone.name, event.timestamp, event.zoneCurrent, event.duration,
                                    event.durationInMinutes, event.flowVolume);
                            // TODO duration
                            updateZoneRunning(false, 0);
                            break;
                        case "ZONE_COMPLETED":
                        case "ZONE_CYCLING":
                        case "ZONE_CYCLING_COMPLETED":
                            logger.info("Event for zone {}: {} (status={}, duration = {}sec)", event.zoneName,
                                    event.summary, event.zoneRunStatus.state, event.duration);
                            break;
                    }
                case "ZONE_DELTA":
                    logger.info("DELTA Event for zone {}: {}.{}", rachioApiZone.name, event.category, event.action);
                    break;
                default:
                    logger.debug("Unhandled event type {}.{} for zone {}", event.type, event.subType,
                            rachioApiZone.name);
            }
        } catch (RuntimeException e) {
            logger.debug("Unable to process event: {}", e.getLocalizedMessage());
        }

        return true;
    }

    public void postChannelData(@Nullable RachioApiZone newRachioApiZone, boolean forceUpdate,
            @Nullable String updateChannel) {
        if (newRachioApiZone == null) {
            newRachioApiZone = rachioApiZone;
            forceUpdate = true;
        }

        if (forceUpdate) {
            newRachioApiZone = getBridgeHandler().getZoneById(id);
        }

        if (isUpdateRequired(CHANNEL_ZONE_NAME, forceUpdate, updateChannel, rachioApiZone.name,
                newRachioApiZone.name)) {
            updateState(CHANNEL_ZONE_NAME, new StringType(newRachioApiZone.name));
        }
        if (isUpdateRequired(CHANNEL_ZONE_NUMBER, forceUpdate, updateChannel, rachioApiZone.zoneNumber,
                newRachioApiZone.zoneNumber)) {
            updateState(CHANNEL_ZONE_NUMBER, new DecimalType(newRachioApiZone.zoneNumber));
        }
        if (isUpdateRequired(CHANNEL_ZONE_ENABLED, forceUpdate, updateChannel, rachioApiZone.enabled,
                newRachioApiZone.enabled)) {
            updateState(CHANNEL_ZONE_ENABLED, OnOffType.from(newRachioApiZone.enabled));
        }
        if (forceUpdate || CHANNEL_ZONE_RUN.equals(updateChannel)) {
            updateState(CHANNEL_ZONE_RUN, OnOffType.from(running));
        }
        if (forceUpdate || CHANNEL_ZONE_RUN_TIME.equals(updateChannel)) {
            updateState(CHANNEL_ZONE_RUN_TIME, new QuantityType<>(runningDuration, Units.MINUTE));
        }
        if (isUpdateRequired(CHANNEL_ZONE_RUN_TOTAL, forceUpdate, updateChannel, rachioApiZone.runtime,
                newRachioApiZone.runtime)) {
            updateState(CHANNEL_ZONE_RUN_TOTAL, new DecimalType(newRachioApiZone.runtime));
        }
        if (isUpdateRequired(CHANNEL_ZONE_IMAGEURL, forceUpdate, updateChannel, rachioApiZone.imageUrl,
                newRachioApiZone.imageUrl) || CHANNEL_ZONE_IMAGE.equals(updateChannel)) {
            updateState(CHANNEL_ZONE_IMAGEURL, new StringType(newRachioApiZone.imageUrl));
            if (isLinked(CHANNEL_ZONE_IMAGE)) {
                RawType imageType = api.getImageFromURL(newRachioApiZone.imageUrl);
                if (imageType != null) {
                    updateState(CHANNEL_ZONE_IMAGE, imageType);
                }
            }
        }
        if (isUpdateRequired(CHANNEL_ZONE_LAST_WATERED_DATE, forceUpdate, updateChannel, rachioApiZone.lastWateredDate,
                newRachioApiZone.lastWateredDate)) {
            updateState(CHANNEL_ZONE_RUN_TOTAL, new DateTimeType(newRachioApiZone.lastWateredDate.toInstant()));
        }
        if (isUpdateRequired(CHANNEL_ZONE_AVAILABLE_WATER, forceUpdate, updateChannel, rachioApiZone.availableWater,
                newRachioApiZone.availableWater)) {
            updateState(CHANNEL_ZONE_AVAILABLE_WATER,
                    new QuantityType<>(newRachioApiZone.availableWater, ImperialUnits.INCH));
        }
        if (isUpdateRequired(CHANNEL_ZONE_DEPTH_OF_WATER, forceUpdate, updateChannel, rachioApiZone.depthOfWater,
                newRachioApiZone.depthOfWater)) {
            updateState(CHANNEL_ZONE_DEPTH_OF_WATER,
                    new QuantityType<>(newRachioApiZone.depthOfWater, ImperialUnits.INCH));
        }
        if (isUpdateRequired(CHANNEL_ZONE_DEPLETION_LEVEL, forceUpdate, updateChannel,
                rachioApiZone.managementAllowedDepletion, newRachioApiZone.managementAllowedDepletion)) {
            updateState(CHANNEL_ZONE_DEPLETION_LEVEL,
                    new QuantityType<>(newRachioApiZone.managementAllowedDepletion, ImperialUnits.INCH));
        }
        if (isUpdateRequired(CHANNEL_ZONE_SATURATION_DEPTH, forceUpdate, updateChannel,
                rachioApiZone.saturatedDepthOfWater, newRachioApiZone.saturatedDepthOfWater)) {
            updateState(CHANNEL_ZONE_SATURATION_DEPTH,
                    new QuantityType<>(newRachioApiZone.saturatedDepthOfWater, ImperialUnits.INCH));
        }

        // TODO updateChannel(CHANNEL_LAST_EVENT, new StringType(z.getEvent()));
        // DateTimeType ts = z.getEventTime();
        // updateChannel(RachioBindingConstants.CHANNEL_LAST_EVENTTS, ts != null ? ts : UnDefType.UNDEF);
    }

    private void updateProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(PROPERTY_NAME, rachioApiZone.name);
        updateProperties(properties);
    }
}
