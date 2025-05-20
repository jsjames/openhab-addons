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

import static java.util.Objects.*;
import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;
import static org.openhab.binding.rachio.internal.RachioUtils.isUpdateRequired;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.RachioBindingConstants;
import org.openhab.binding.rachio.internal.RachioUtils;
import org.openhab.binding.rachio.internal.api.RachioApi;
import org.openhab.binding.rachio.internal.api.RachioApiException;
import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.internal.api.dto.RachioApiCurrentSchedule;
import org.openhab.binding.rachio.internal.api.dto.RachioApiDevice;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEvent;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEventType;
import org.openhab.binding.rachio.internal.api.dto.RachioApiWebhook;
import org.openhab.binding.rachio.internal.api.dto.RachioApiZone;
import org.openhab.binding.rachio.internal.configuration.RachioControllerConfiguration;
import org.openhab.binding.rachio.internal.discovery.RachioDiscoveryService;
import org.openhab.binding.rachio.utils.ClientRateLimitManager.RateLimitThrottleException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RachioControllerHandler} is responsible for handling commands, which are
 * sent to one of the device channels.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class RachioControllerHandler extends AbstractRachioBridgeHandler<RachioBridgeHandler, RachioId.Device> {
    private final Logger logger = LoggerFactory.getLogger(RachioControllerHandler.class);
    private RachioControllerConfiguration config;

    RachioApiDevice rachioApiDevice = RachioApiDevice.EMPTY;
    RachioId.Webhook rachioWebhookId = RachioId.Webhook.EMPTY;

    private enum DeviceState {
        STOP,
        RUN,
        PAUSE,
        UNDEF;
    };

    DeviceState deviceState = DeviceState.UNDEF;

    private Map<RachioId.Zone, RachioZoneHandler> zoneHandlers = new HashMap<>();

    public RachioControllerHandler(final Bridge thing, final RachioId.Device deviceId, final RachioApi api) {
        super(thing, deviceId, api);
        config = getConfigAs(RachioControllerConfiguration.class);
    }

    @Override
    public void initialize() {
        logger.debug("Controller handler initialize");
        if (id.toString().isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/controller-configuration-issue");
            return;
        }
        updateStatus(ThingStatus.UNKNOWN);
        // only goOnline if bridge is ONLINE, otherwise wait for bridgeStatusChange
        if (requireNonNull(getBridge()).getStatus() == ThingStatus.ONLINE) {
            scheduler.execute(this::goOnline);
        }
    }

    public void goOnline() {
        if (getThing().getStatus() == ThingStatus.ONLINE || !checkBridgeStatus()) {
            return;
        }

        RachioApiDevice rachioApiDevice = getBridgeHandler().getDeviceById(id);
        if (rachioApiDevice == null) {
            logger.error("rachioApiDevice is null");
            return;
        }
        this.rachioApiDevice = rachioApiDevice;

        RachioDiscoveryService discoveryService = getBridgeHandler().getDiscoveryService();

        updateProperties();
        postChannelData(rachioApiDevice, true, null);

        String callbackUrl = getBridgeHandler().getCallbackUrl();
        if (!callbackUrl.isEmpty()) {
            try {
                registerWebhook(api.getExternalId());
            } catch (Throwable e) {
                logger.error("Unable to create webhook");
            }
        }

        if ("ONLINE".equals(rachioApiDevice.status)) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "@text/controller-offline");
        }

        if (rachioApiDevice.zones == null) {
            logger.error("Controller device does not have any zones");
        } else {
            for (RachioApiZone rachioApiZone : rachioApiDevice.zones.values()) {
                discoveryService.notifyDiscoveryZone(getThing().getUID(), requireNonNullElse(getThing().getLabel(), ""),
                        rachioApiZone);
            }
        }
    }

    public void goOffline(ThingStatusDetail thingStatusDetail, @Nullable String description) {
        updateStatus(ThingStatus.OFFLINE, thingStatusDetail, description);

        try {
            if (rachioWebhookId != RachioId.Webhook.EMPTY) {
                api.deleteWebhook(rachioWebhookId);
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException
                | RateLimitThrottleException e) {
            logger.error("Unable to delete webhook");
        } finally {
            rachioWebhookId = RachioId.Webhook.EMPTY;
        }
    }

    public void dispose() {
        try {
            if (rachioWebhookId != RachioId.Webhook.EMPTY) {
                api.deleteWebhook(rachioWebhookId);
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException
                | RateLimitThrottleException e) {
            logger.debug("Unhandled exception when removing webhook: {}", e.getLocalizedMessage());
        } finally {
            rachioWebhookId = RachioId.Webhook.EMPTY;
        }
    }

    /**
     * {@link registerWebhook} will check to see if existing webhook exists and deletes.
     * Then, will create new webhook for this device.
     * 
     * @param externalId
     */
    public void registerWebhook(String externalId)
            throws InterruptedException, TimeoutException, ExecutionException, Throwable {
        List<RachioApiWebhook> webhooks = api.getListWebhook(id);

        URI uri = new URI(getBridgeHandler().getCallbackUrl());

        logger.debug("Registered webhooks for device '{}': {}", id, webhooks.toString());
        for (RachioApiWebhook webhook : webhooks) {
            logger.debug("Webhook: id='{}', url='{}', externalId='{}'", webhook.id, webhook.url, webhook.externalId);
            if (webhook.url.equals(uri.toString())) {
                logger.debug("The callback url '{}' is already registered -> delete", webhook.url);
                api.deleteWebhook(webhook.id);
            }
        }

        // TODO final List<String> defaultEventTypes = new ArrayList<>(List.of("WHE_DEVICE_STATUS", "WHE_RAIN_DELAY",
        // "WEATHER_INTELLIGENCE", "WHE_WATER_BUDGET", "WHE_ZONEDELTA", "WHE_SCHEDULE_STATUS", "WHE_ZONE_STATUS",
        // "WHE_RAIN_SENSOR_DETECTION", "WHE_DELTA"));√

        List<RachioApiEventType> eventTypeList = api.getListWebhookEventTypes(id);
        List<String> eventTypes = eventTypeList.stream().map(et -> et.eventType).collect(Collectors.toList());

        RachioApiWebhook rachioApiWebhook = api.createWebhook(id, uri.toString(), externalId, eventTypes);
        rachioWebhookId = rachioApiWebhook.id;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channel = channelUID.getId();
        logger.debug("{}: Handle Command {} for channel {}", rachioApiDevice.name, command, channel);

        try {
            if (command == RefreshType.REFRESH) {
                RachioApiDevice localRachioApiDevice = getBridgeHandler().getDeviceById(id);
                if (localRachioApiDevice == null) {
                    return;
                }
                postChannelData(localRachioApiDevice, true, channel);
                rachioApiDevice = localRachioApiDevice;

                return;
            }

            switch (channel) {
                case CHANNEL_DEVICE_ACTIVE:
                    logger.debug("Enable device {} - {}", rachioApiDevice.name, command);
                    api.enableDevice(rachioApiDevice.id, OnOffType.ON.equals(command));
                    break;
                case CHANNEL_DEVICE_RUN_TIME:
                    if (command instanceof DecimalType) {
                        int runtime = ((DecimalType) command).intValue();
                        logger.debug("Default Runtime for zones set to {} sec", runtime);
                        // rachioDevice.setRunTime(runtime);
                    }
                    break;
                case CHANNEL_DEVICE_RUN_ZONES:
                    if (command instanceof StringType) {
                        logger.debug("Run multiple zones: '{}' ('' = ALL)", command.toString());
                        // rachioDevice.setRunZones(command.toString());
                    }
                    break;
                case CHANNEL_DEVICE_RUN:
                case CHANNEL_DEVICE_PLAYER:
                    if (command == OnOffType.ON || command == PlayPauseType.PLAY) {
                        deviceState = DeviceState.RUN;
                        // TODO logger.debug("START watering zones '{}' ('' = ALL)", rachioApiDevice.getRunZones());
                        // TODO api.runMultipleZones();
                        // handler.runMultipleZones(rachioDevice.getAllRunZonesJson(handler.getDefaultRuntime()));
                    } else if (command == OnOffType.OFF) {
                        logger.info("STOP watering for device '{}'", rachioApiDevice.name);
                        api.putStopWatering(id);
                        deviceState = DeviceState.STOP;
                    } else if (command == PlayPauseType.PAUSE) {
                        logger.info("PAUSE watering for device '{}'", rachioApiDevice.name);
                        api.pauseZoneRun(id, 3600, true); // pause for max time allowed of 1hr
                        deviceState = DeviceState.PAUSE;
                    } else if (command == NextPreviousType.NEXT && deviceState == DeviceState.RUN) {
                        logger.info("NEXT zone");
                        api.putScheduleSkipForward(id);
                    }
                    break;
                case CHANNEL_DEVICE_RAIN_DELAY:
                case CHANNEL_DEVICE_RAIN_DELAY_UNTIL:
                    long delayDurationSec = 0;

                    if (command instanceof QuantityType<?> qtDuration) {
                        QuantityType<?> qtDurationSeconds = qtDuration.toUnit(Units.SECOND);
                        if (qtDurationSeconds != null) {
                            delayDurationSec = qtDurationSeconds.longValue();
                        }
                    } else if (command instanceof DateTimeType dttCommand) {
                        delayDurationSec = Duration.between(Instant.now(), dttCommand.getInstant()).toSeconds();
                    } else if (command instanceof DecimalType dtDuration) {
                        delayDurationSec = (long) (dtDuration.floatValue() * 24 * 3600);
                    }

                    if (delayDurationSec > 0 && delayDurationSec < 7 * 24 * 3600) {
                        logger.info("Start rain delay cycle for {} sec", delayDurationSec);
                        api.putDeviceRainDelay(rachioApiDevice.id, delayDurationSec);
                    } else {
                        logger.info("Rain delay duration must be between 1 and 7 days");
                    }
                    break;
                default:
                    logger.debug("Command {} for {} ignored", command, channelUID.getAsString());
                    break;
            }
        } catch (RachioApiException e) {
            logger.warn("RachioApiException: {}", e.toString());
        } catch (InterruptedException | TimeoutException | ExecutionException | RateLimitThrottleException e) {
            logger.warn("Exception during API call: {}", e.toString());
        }
    }

    public int getDefaultRunTime() {
        return config.defaultRunTime;
    }

    /**
     * {@link postChannelData} method is a general purpose function to update the channels for the device.
     * 
     * @param newRachioApiDevice is new structure to compare what has changed with the last stored structure. If null,
     *            will use existing stored structure.
     * @param forceUpdate will update the channels without comparing to see if an update is needed
     * @param updateChannel will only update this particular channel
     */
    private void postChannelData(@Nullable RachioApiDevice newRachioApiDevice, boolean forceUpdate,
            @Nullable String updateChannel) {
        if (newRachioApiDevice == null) {
            newRachioApiDevice = rachioApiDevice;
            forceUpdate = true;
        }

        if (forceUpdate) {
            newRachioApiDevice = getBridgeHandler().getDeviceById(id);
        }

        if (isUpdateRequired(CHANNEL_DEVICE_NAME, forceUpdate, updateChannel, rachioApiDevice.name,
                newRachioApiDevice.name)) {
            updateState(CHANNEL_DEVICE_NAME, new StringType(newRachioApiDevice.name));
        }
        if (isUpdateRequired(CHANNEL_DEVICE_ACTIVE, forceUpdate, updateChannel, rachioApiDevice.on,
                newRachioApiDevice.on)) {
            updateState(CHANNEL_DEVICE_ACTIVE, OnOffType.from(newRachioApiDevice.on));
        }
        // updateChannel(RachioBindingConstants.CHANNEL_DEVICE_RUN_ZONES, new StringType(d.getRunZones()));
        // updateChannel(RachioBindingConstants.CHANNEL_DEVICE_RUN_TIME,
        // new DecimalType(new BigDecimal(d.getRunTime()).toString()));
        if (isUpdateRequired(CHANNEL_DEVICE_RAIN_DELAY, forceUpdate, updateChannel,
                rachioApiDevice.rainDelayExpirationDate, newRachioApiDevice.rainDelayExpirationDate)
                || CHANNEL_DEVICE_RAIN_DELAY_UNTIL.equals(updateChannel)) {

            Duration rainDelay = Duration.between(Instant.now(),
                    newRachioApiDevice.rainDelayExpirationDate.toInstant());
            updateState(CHANNEL_DEVICE_RAIN_DELAY, new QuantityType<>(rainDelay.toMinutes(), Units.MINUTE));
            updateState(CHANNEL_DEVICE_RAIN_DELAY_UNTIL,
                    new DateTimeType(newRachioApiDevice.rainDelayExpirationDate.toInstant()));
        }
        if (isUpdateRequired(CHANNEL_DEVICE_RAIN_SENSOR_TRIPPED, forceUpdate, updateChannel,
                rachioApiDevice.rainSensorTripped, newRachioApiDevice.rainSensorTripped)) {
            updateState(CHANNEL_DEVICE_RAIN_SENSOR_TRIPPED, OnOffType.from(newRachioApiDevice.rainSensorTripped));
        }
        // updateChannel(RachioBindingConstants.CHANNEL_LAST_EVENT, new StringType(d.getEvent()));
        // DateTimeType ts = d.getEventTime();
        // updateChannel(RachioBindingConstants.CHANNEL_LAST_EVENTS, ts != null ? ts : UnDefType.UNDEF);

        if (forceUpdate || CHANNEL_DEVICE_RUN.equals(updateChannel)) {
            if (deviceState == DeviceState.UNDEF) {
                updateState(CHANNEL_DEVICE_RUN, UnDefType.UNDEF);
            } else {
                updateState(CHANNEL_DEVICE_RUN,
                        OnOffType.from(deviceState == DeviceState.RUN || deviceState == DeviceState.PAUSE));
            }
        }
        if (forceUpdate || CHANNEL_DEVICE_PLAYER.equals(updateChannel)) {
            if (deviceState == DeviceState.UNDEF) {
                updateState(CHANNEL_DEVICE_RUN, UnDefType.UNDEF);
            } else {
                updateState(CHANNEL_DEVICE_PLAYER,
                        (deviceState != DeviceState.RUN) ? PlayPauseType.PAUSE : PlayPauseType.PLAY);
            }
        }
    }

    public void onStatusRefresh(RachioApiDevice rachioApiDevice) {
        if (getThing().getStatus() == ThingStatus.OFFLINE
                && getThing().getStatusInfo().getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR) {
            goOnline();
            return;
        }

        if (!rachioApiDevice.getId().equals(id)) {
            logger.error("Controller ID does not match configuration.");
            return;
        }

        ThingStatus controllerStatus = rachioApiDevice.status.equals("ONLINE") ? ThingStatus.ONLINE
                : ThingStatus.OFFLINE;
        ThingStatus thingStatus = getThing().getStatus();
        if (thingStatus == ThingStatus.OFFLINE & controllerStatus == ThingStatus.ONLINE) {
            goOnline();
            return;
        } else if (thingStatus == ThingStatus.ONLINE && controllerStatus == ThingStatus.OFFLINE) {
            goOffline(ThingStatusDetail.NONE, "@text/controller-offline");
            // still proceed to update channels / zones
        }

        postChannelData(rachioApiDevice, false, null);

        RachioDiscoveryService localDiscoveryService = getBridgeHandler().getDiscoveryService();
        RachioUtils.compareCollections(rachioApiDevice.zones.keySet(), zoneHandlers.keySet(),
                id -> localDiscoveryService.notifyDiscoveryZone(getThing().getUID(),
                        requireNonNullElse(getThing().getLabel(), ""), requireNonNull(rachioApiDevice.zones.get(id))),
                id -> requireNonNull(zoneHandlers.get(id)).goOffline(ThingStatusDetail.GONE, "@text-zone-remove"),
                id -> requireNonNull(zoneHandlers.get(id))
                        .onStatusRefresh(Objects.requireNonNull(rachioApiDevice.zones.get(id))));

        this.rachioApiDevice = rachioApiDevice;
        updateZoneRunning();
    }

    public void updateZoneRunning() {
        RachioApiCurrentSchedule rachioApiCurrentSchedule = RachioApiCurrentSchedule.EMPTY;
        try {
            rachioApiCurrentSchedule = api.getDeviceCurrentSchedule(id);
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException
                | RateLimitThrottleException e) {
            logger.error("Unable to retrieve current schedule");
        }

        /*
         * TODO
         * deviceState = switch (rachioApiCurrentSchedule.status) {
         * case null -> DeviceState.STOP;
         * case "PAUSED" -> DeviceState.PAUSE;
         * case "PROCESSING" -> DeviceState.RUN;
         * default -> DeviceState.UNDEF;
         * };
         */

        deviceState = (rachioApiCurrentSchedule.status == null) ? DeviceState.STOP
                : (rachioApiCurrentSchedule.status.equals("PAUSED")) ? DeviceState.PAUSE
                        : (rachioApiCurrentSchedule.status.equals("PROCESSING")) ? DeviceState.RUN : DeviceState.UNDEF;

        RachioApiZone rachioApiZone = rachioApiDevice.zones.get(rachioApiCurrentSchedule.zoneId);

        if (deviceState == DeviceState.UNDEF) {
            logger.info("UNKNOWN Device Status {}", rachioApiCurrentSchedule.status);
        }

        switch (deviceState) {
            case STOP:
                updateState(CHANNEL_DEVICE_RUN, OnOffType.OFF);
                updateState(CHANNEL_DEVICE_PLAYER, UnDefType.UNDEF);
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NUMBER, UnDefType.UNDEF);
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NAME, UnDefType.UNDEF);
                break;
            case RUN:
                updateState(CHANNEL_DEVICE_RUN, OnOffType.ON);
                updateState(CHANNEL_DEVICE_PLAYER, PlayPauseType.PLAY);
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NUMBER,
                        new DecimalType(requireNonNull(rachioApiZone.zoneNumber)));
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NAME, new StringType(requireNonNull(rachioApiZone.name)));
                break;
            case PAUSE:
                updateState(CHANNEL_DEVICE_RUN, OnOffType.ON);
                updateState(CHANNEL_DEVICE_PLAYER, PlayPauseType.PAUSE);
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NUMBER,
                        new DecimalType(requireNonNull(rachioApiZone.zoneNumber)));
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NAME, new StringType(requireNonNull(rachioApiZone.name)));
                break;
            default:
                updateState(CHANNEL_DEVICE_RUN, UnDefType.UNDEF);
                updateState(CHANNEL_DEVICE_PLAYER, UnDefType.UNDEF);
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NUMBER, UnDefType.UNDEF);
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NAME, UnDefType.UNDEF);
                logger.debug("UNKNOWN current schedule Status {}", rachioApiCurrentSchedule.status);
                break;
        }

        updateState(CHANNEL_DEVICE_RUN, OnOffType.from(deviceState != DeviceState.STOP));

        for (RachioZoneHandler rachioZoneHandler : zoneHandlers.values()) {
            rachioZoneHandler.updateZoneRunning(rachioZoneHandler.getId().equals(rachioApiCurrentSchedule.zoneId),
                    rachioApiCurrentSchedule.duration);
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        super.bridgeStatusChanged(bridgeStatusInfo);

        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            goOnline();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof RachioZoneHandler rachioZoneHandler) {
            zoneHandlers.put(rachioZoneHandler.getId(), rachioZoneHandler);
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof RachioZoneHandler rachioZoneHandler) {
            zoneHandlers.remove(rachioZoneHandler.getId());
        }
    }

    public boolean webhookEvent(RachioApiEvent event) {
        // TODO boolean update = true;

        switch (event.type) {
            case "ZONE_STATUS":
                // RachioZoneStatus runStatus = event.zoneRunStatus;
                // if (runStatus != null) {
                // zone = d.getZoneByNumber(runStatus.zoneNumber);
                // }
                // zone handler - webhookEvent
                break;
            case "ZONE_DELTA":
                // zone = d.getZoneById(event.zoneId);
                // zone handler - webhookEvent
                break;
            case "DEVICE_STATUS":
                switch (event.subType) {
                    case "COLD_REBOOT":
                        // logger.info("{}: Device {} was restarted, ip={}/{}, gw={}, dns={}/{}, wifi rssi={}.",
                        // rachoApiDevice.name,
                        // d.name, d.network.ip, d.network.nm, d.network.gw, d.network.dns1, d.network.dns2,
                        // d.network.rssi);
                        // if (event.network != null) {
                        // rachioDevice.setNetwork(event.network);
                        // }
                        break;
                    case "ONLINE":
                        updateStatus(ThingStatus.ONLINE);
                        break;
                    case "OFFLINE":
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "Controller is offline");
                        break;
                    case "SLEEP_MODE_ON":
                    case "SLEEP_MODE_OFF":
                        // TODO rachioApiDevice.paused = event.subType.equals("SLEEP_MODE_ON") ? true : false;
                        // updateState(CHANNEL_DEVICE_PAUSED, OnOffType.from(rachioApiDevice.paused));
                        break;
                    case "RAIN_SENSOR_DETECTION_ON":
                    case "RAIN_SENSOR_DETECTION_OFF":
                        rachioApiDevice.rainSensorTripped = event.subType.equals("RAIN_SENSOR_DETECTION_ON") ? true
                                : false;
                        updateState(CHANNEL_DEVICE_RAIN_SENSOR_TRIPPED,
                                OnOffType.from(rachioApiDevice.rainSensorTripped));
                        break;
                    case "RAIN_DELAY_ON":
                    case "RAIN_DELAY_OFF":
                        // TODO rachioApiDevice.rai
                        // updateState(CHANNEL_DEVICE_RAIN_DELAY, OnOffType.from());
                        break;
                    default:
                        logger.info("Unknown sub-event for DEVICE_STATUS: {}", event);
                }
            case "SCHEDULE_STATUS":
                logger.info("{}: Status {} for schedule {}: {} (start={}, end={}, duration={}min)",
                        rachioApiDevice.name, event.subType, event.scheduleName, event.summary, event.startTime,
                        event.endTime, event.durationInMinutes);

                updateState(CHANNEL_SCHED_NAME, new StringType(event.scheduleName));
                updateState(CHANNEL_SCHED_INFO, new StringType(event.summary));
                updateState(CHANNEL_SCHED_START, new DateTimeType(event.startTime));
                updateState(CHANNEL_SCHED_END, new DateTimeType(event.endTime));

                break;
            default:
                logger.info("Unknown event: {}", event);
                break;
        }

        /*
         * if (update) {
         * postChannelData();
         * updateChannel(CHANNEL_LAST_UPDATE, getTimestamp());
         * return true;
         * }
         * logger.debug("{}: Unhandled event {}.{} for device {} ({}): {}", rachoApiDevice.name, event.type,
         * event.subType, d.name,
         * d.id, event.summary);
         * return false;
         * } catch (RuntimeException e) {
         * logger.debug("{}: Unable to process event {}.{} - {}", rachoApiDevice.name, event.type, event.subType,
         * event.summary,
         * e);
         * return false;
         * }
         */
        return true;
    }

    private void updateProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put(Thing.PROPERTY_VENDOR, RachioBindingConstants.BINDING_VENDOR);
        properties.put(PROPERTY_NAME, rachioApiDevice.name);
        properties.put(PROPERTY_MODEL, rachioApiDevice.model);
        properties.put(Thing.PROPERTY_SERIAL_NUMBER, rachioApiDevice.serialNumber);
        properties.put(Thing.PROPERTY_MAC_ADDRESS, rachioApiDevice.macAddress);
        properties.put(PROPERTY_DEV_LOCATION,
                String.format("%.04f,%.04f", rachioApiDevice.latitude, rachioApiDevice.longitude));
        /*
         * TODO
         * RachioCloudNetworkSettings nw = network;
         * if (nw != null) {
         * properties.put(PROPERTY_IP_ADDRESS, nw.ip);
         * properties.put(PROPERTY_IP_MASK, nw.ip);
         * properties.put(PROPERTY_IP_GW, nw.gw);
         * properties.put(PROPERTY_IP_DNS1, nw.dns1);
         * properties.put(PROPERTY_IP_DNS2, nw.dns2);
         * properties.put(PROPERTY_WIFI_RSSI, nw.rssi);
         * }
         */

        updateProperties(properties);
    }

    public boolean checkBridgeStatus() {
        Bridge bridge = this.getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.configuration-error.bridge-missing");
            return false;
        }

        RachioBridgeHandler bridgeHandler = (RachioBridgeHandler) bridge.getHandler();
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

    @Nullable
    RachioApiZone getZoneById(RachioId.Zone zoneId) {
        RachioApiDevice localRachioApiDevice = getBridgeHandler().getDeviceById(id);
        if (localRachioApiDevice == null) {
            logger.error("Invalid state - getDeviceById did not return device");
            return null;
        }
        rachioApiDevice = localRachioApiDevice;

        return rachioApiDevice.zones.get(zoneId);
    }
}
