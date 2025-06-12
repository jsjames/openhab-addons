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
import static org.openhab.binding.rachio.utils.RachioUtils.isUpdateRequired;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
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
import org.openhab.binding.rachio.internal.api.RachioApi;
import org.openhab.binding.rachio.internal.api.RachioApiException;
import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.internal.api.dto.RachioApiCurrentSchedule;
import org.openhab.binding.rachio.internal.api.dto.RachioApiDevice;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEvent;
import org.openhab.binding.rachio.internal.api.dto.RachioApiScheduleRule;
import org.openhab.binding.rachio.internal.api.dto.RachioApiWebhook;
import org.openhab.binding.rachio.internal.api.dto.RachioApiZone;
import org.openhab.binding.rachio.internal.api.dto.RachioApiZoneRun;
import org.openhab.binding.rachio.internal.configuration.RachioControllerConfiguration;
import org.openhab.binding.rachio.internal.discovery.RachioDiscoveryService;
import org.openhab.binding.rachio.utils.ClientRateLimitManager.RateLimitThrottleException;
import org.openhab.binding.rachio.utils.RachioUtils;
import org.openhab.core.cache.ExpiringCache;
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
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonSyntaxException;

/**
 * The {@link RachioControllerHandler} is responsible for handling commands, which are
 * sent to one of the device channels.
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class RachioControllerHandler
        extends AbstractRachioBridgeHandler<RachioCloudConnectorHandler, RachioId.Device, RachioId.Zone> {
    private final Logger logger = LoggerFactory.getLogger(RachioControllerHandler.class);
    private RachioControllerConfiguration config;

    private static final int WAIT_ZONE_DURATION_SEC = 10;

    RachioApiDevice apiDevice = RachioApiDevice.EMPTY;
    RachioId.Webhook webhookId = RachioId.Webhook.EMPTY;

    public enum DeviceState {
        STOP,
        STOP_PENDING,
        RUN,
        RUN_PENDING,
        PAUSE,
        PAUSE_PENDING,
        UNDEF;

        public static DeviceState fromStatus(String state) {
            return switch (state) {
                case "PROCESSING" -> RUN;
                case "PAUSED" -> PAUSE;
                case null -> STOP;
                default -> UNDEF;
            };
        }
    };

    // Device state information
    private DeviceState deviceState = DeviceState.UNDEF;
    private RachioId.Zone zoneRunningId = RachioId.Zone.EMPTY;
    private int zoneRunningDuration = 0;

    ExpiringCache<RachioApiCurrentSchedule> currentScheduleCache = new ExpiringCache<>(Duration.ofSeconds(30),
            this::getCurrentSchedule);
    // RachioApiCurrentSchedule currentSchedule = RachioApiCurrentSchedule.EMPTY;
    List<RachioApiZoneRun> zonesRun = Collections.emptyList();

    private boolean pollingMode;

    public RachioControllerHandler(final Bridge thing, final RachioId.Device deviceId, final RachioApi api,
            final RachioCloudConnectorHandler cloudConnectorHandler) {
        super(thing, deviceId, api, cloudConnectorHandler);
        config = getConfigAs(RachioControllerConfiguration.class);
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

        this.pollingMode = getCloudConnectorHandler().isPollingMode();

        RachioApiDevice rachioApiDevice = getBridgeHandler().getDeviceById(id);
        if (rachioApiDevice == null) {
            logger.error("rachioApiDevice is null");
            return;
        }
        this.apiDevice = rachioApiDevice;

        updateProperties();
        postChannelData(rachioApiDevice, true, null);
        updateZoneStructure(rachioApiDevice.zones());

        if (!pollingMode) {
            try {
                webhookId = getBridgeHandler().registerWebhook(id);
            } catch (RachioApiException | InterruptedException | TimeoutException | ExecutionException
                    | RateLimitThrottleException e) {
                logger.error("Unable to create webhook");
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "@text/controller-offline.webhook-error");
                return;
            }
        }

        if ("ONLINE".equals(rachioApiDevice.status())) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "@text/controller-offline");
        }
    }

    @Override
    public void goOffline(ThingStatusDetail thingStatusDetail, @Nullable String description) {
        updateStatus(ThingStatus.OFFLINE, thingStatusDetail, description);

        try {
            if (!webhookId.equals(RachioId.Webhook.EMPTY)) {
                api.deleteWebhook(webhookId);
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException
                | RateLimitThrottleException e) {
            logger.error("Unable to delete webhook");
        } finally {
            webhookId = RachioId.Webhook.EMPTY;
        }
    }

    public void dispose() {
        try {
            if (webhookId != RachioId.Webhook.EMPTY) {
                api.deleteWebhook(webhookId);
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException
                | RateLimitThrottleException e) {
            logger.debug("Unhandled exception when removing webhook: {}", e.getLocalizedMessage());
        } finally {
            webhookId = RachioId.Webhook.EMPTY;
        }
    }

    public void registerWebhook2(String externalId) throws JsonSyntaxException, RachioApiException,
            InterruptedException, TimeoutException, ExecutionException, RateLimitThrottleException {
        List<RachioApiWebhook> webhooks = api.getListWebhook(id);

        String callbackUrl = RachioUtils.enocdeUri(getBridgeHandler().getCallbackUrl());

        logger.debug("Registered webhooks for device '{}': {}", id, webhooks.toString());
        for (RachioApiWebhook webhook : webhooks) {
            logger.debug("Webhook: id='{}', url='{}', externalId='{}'", webhook.id(), webhook.url(),
                    webhook.externalId());
            if (webhook.url().equals(callbackUrl.toString())) {
                logger.debug("The callback url '{}' is already registered -> delete", webhook.url());
                api.deleteWebhook(webhook.id());
            }
        }

        RachioApiWebhook rachioApiWebhook = api.createWebhook2(id, callbackUrl, externalId);
        webhookId = rachioApiWebhook.id();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channel = channelUID.getId();
        logger.debug("{}: Handle Command {} for channel {}", apiDevice.name(), command, channel);

        try {
            if (command == RefreshType.REFRESH) {
                RachioApiDevice localRachioApiDevice = getBridgeHandler().getDeviceById(id);
                if (localRachioApiDevice == null) {
                    return;
                }
                postChannelData(localRachioApiDevice, true, channel);
                apiDevice = localRachioApiDevice;

                return;
            }

            switch (channel) {
                case CHANNEL_DEVICE_ACTIVE:
                    logger.debug("Enable device {} - {}", apiDevice.name(), command);
                    api.enableDevice(apiDevice.id(), OnOffType.ON.equals(command));
                    break;
                case CHANNEL_DEVICE_RUN_TIME:
                    if (command instanceof DecimalType) {
                        int runtime = ((DecimalType) command).intValue();
                        logger.debug("Default Runtime for zones set to {} sec", runtime);
                        // rachioDevice.setRunTime(runtime);
                    }
                    break;
                case CHANNEL_DEVICE_RUN_ZONES:
                    if (command instanceof StringType zoneString) {
                        logger.debug("Run multiple zones: '{}' ('' = ALL)", command.toString());
                        zonesRun = parseZoneRunString(zoneString.toString());
                        if (zonesRun == null || zonesRun.isEmpty()) {
                            logger.warn("Invalid zone string '{}'", zoneString.toString());
                            return;
                        }
                    }
                    break;
                case CHANNEL_DEVICE_RUN:
                case CHANNEL_DEVICE_PLAYER:
                    handleRunPlayerCommand(command);
                    break;
                case CHANNEL_DEVICE_RAIN_DELAY:
                case CHANNEL_DEVICE_RAIN_DELAY_UNTIL:
                    handleRainDelayCommand(command);
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

    public void handleRunPlayerCommand(Command command) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        if (command == OnOffType.ON || command == PlayPauseType.PLAY) {
            if (deviceState == DeviceState.PAUSE || deviceState == DeviceState.PAUSE_PENDING) {
                logger.info("RESUME watering for device '{}'", apiDevice.name());
                api.pauseZoneRun(id, 0, false);
                deviceState = DeviceState.RUN_PENDING;
                waitForState(DeviceState.RUN, null);
                updateZoneRunning();
            } else if (deviceState != DeviceState.RUN && deviceState != DeviceState.RUN_PENDING) {
                api.putZoneStartMultiple(zonesRun);
                deviceState = DeviceState.RUN_PENDING;
                waitForState(DeviceState.RUN, null);
                updateZoneRunning();
            } else {
                logger.debug("Cannot start watering schedule since controller is already running");
            }
        } else if (command == OnOffType.OFF) {
            logger.info("STOP watering for device '{}'", apiDevice.name());
            api.putStopWatering(id);
            deviceState = DeviceState.STOP_PENDING;
            waitForState(DeviceState.STOP, null);
        } else if (command == PlayPauseType.PAUSE) {
            logger.info("PAUSE watering for device '{}'", apiDevice.name());
            api.pauseZoneRun(id, 3600, true); // pause for max time allowed of 1hr
            deviceState = DeviceState.PAUSE_PENDING;
        } else if (command == NextPreviousType.NEXT && deviceState == DeviceState.RUN) {
            logger.info("NEXT zone");
            api.putScheduleSkipForward(id);
        }
    }

    public void handleRainDelayCommand(Command command) throws RachioApiException, InterruptedException,
            TimeoutException, ExecutionException, RateLimitThrottleException {
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
            api.putDeviceRainDelay(apiDevice.id(), delayDurationSec);
        } else {
            logger.info("Rain delay duration must be between 1 and 7 days");
        }
    }

    public int getDefaultRunTime() {
        return config.defaultRunTime;
    }

    public RachioId.@Nullable Zone getZoneIdByNumber(int zoneNumber) {
        for (RachioApiZone zone : apiDevice.zones().values()) {
            if (zone.zoneNumber() == zoneNumber) {
                return zone.id();
            }
        }
        return null;
    }

    public RachioApiZoneRun parseZoneToken(String zoneToken) {
        if (zoneToken.contains(":")) {
            String[] parts = zoneToken.split(":");
            int zone = Integer.parseInt(parts[0]);
            QuantityType<?> qtDuration = new QuantityType<>(parts[1].trim());
            if (qtDuration.getUnit() == Units.ONE) {
                qtDuration = new QuantityType<>(qtDuration.intValue(), Units.MINUTE);
            }
            return new RachioApiZoneRun(getZoneIdByNumber(zone), qtDuration.toUnit(Units.SECOND).intValue(), 0);
        } else {
            int zone = Integer.parseInt(zoneToken);
            return new RachioApiZoneRun(getZoneIdByNumber(zone), getDefaultRunTime(), 0);
        }
    }

    @SuppressWarnings("null") // will always return a non-null List<RachioApiZoneRun>
    public List<RachioApiZoneRun> parseZoneRunString(String zoneString) {
        return Arrays.stream(zoneString.split(",")) //
                .map(String::trim) //
                .filter(entry -> !entry.isEmpty()) //
                .map(this::parseZoneToken) //
                .filter(Objects::nonNull) //
                .collect(Collectors.toList());
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
            newRachioApiDevice = apiDevice;
            forceUpdate = true;
        }

        if (forceUpdate) {
            newRachioApiDevice = getBridgeHandler().getDeviceById(id);
        }

        if (isUpdateRequired(CHANNEL_DEVICE_NAME, forceUpdate, updateChannel, apiDevice.name(),
                newRachioApiDevice.name())) {
            updateState(CHANNEL_DEVICE_NAME, new StringType(newRachioApiDevice.name()));
        }
        if (isUpdateRequired(CHANNEL_DEVICE_ACTIVE, forceUpdate, updateChannel, apiDevice.on(),
                newRachioApiDevice.on())) {
            updateState(CHANNEL_DEVICE_ACTIVE, OnOffType.from(newRachioApiDevice.on()));
        }
        // updateChannel(RachioBindingConstants.CHANNEL_DEVICE_RUN_ZONES, new StringType(d.getRunZones()));
        // updateChannel(RachioBindingConstants.CHANNEL_DEVICE_RUN_TIME,
        // new DecimalType(new BigDecimal(d.getRunTime()).toString()));
        if (isUpdateRequired(CHANNEL_DEVICE_RAIN_DELAY, forceUpdate, updateChannel, apiDevice.rainDelayExpirationDate(),
                newRachioApiDevice.rainDelayExpirationDate())
                || CHANNEL_DEVICE_RAIN_DELAY_UNTIL.equals(updateChannel)) {

            Duration rainDelay = Duration.between(Instant.now(),
                    newRachioApiDevice.rainDelayExpirationDate().toInstant());
            updateState(CHANNEL_DEVICE_RAIN_DELAY, new QuantityType<>(rainDelay.toMinutes(), Units.MINUTE));
            updateState(CHANNEL_DEVICE_RAIN_DELAY_UNTIL,
                    new DateTimeType(newRachioApiDevice.rainDelayExpirationDate().toInstant()));
        }
        if (isUpdateRequired(CHANNEL_DEVICE_RAIN_SENSOR_TRIPPED, forceUpdate, updateChannel,
                apiDevice.rainSensorTripped(), newRachioApiDevice.rainSensorTripped())) {
            updateState(CHANNEL_DEVICE_RAIN_SENSOR_TRIPPED, OnOffType.from(newRachioApiDevice.rainSensorTripped()));
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

    public void updateZoneStructure(Map<RachioId.Zone, RachioApiZone> zones) {
        RachioDiscoveryService localDiscoveryService = getBridgeHandler().getDiscoveryService();
        RachioUtils.reconcileCollections(zones.keySet(), childHandlers.keySet(),
                id -> localDiscoveryService.notifyDiscoveryZone(getThing().getUID(),
                        requireNonNullElse(getThing().getLabel(), ""), requireNonNull(zones.get(id))),
                id -> requireNonNull(childHandlers.get(id)).goOffline(ThingStatusDetail.GONE, "@text-zone-remove"),
                id -> ((RachioZoneHandler) requireNonNull(childHandlers.get(id)))
                        .onStructureUpdate(Objects.requireNonNull(zones.get(id))));
    }

    public void onStructureUpdate(RachioApiDevice rachioApiDevice) {
        if (getThing().getStatus() == ThingStatus.OFFLINE
                && getThing().getStatusInfo().getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR) {
            goOnline();
            return;
        }

        if (!rachioApiDevice.id().equals(id)) {
            logger.error("Controller ID does not match configuration.");
            return;
        }

        ThingStatus controllerStatus = rachioApiDevice.status().equals("ONLINE") ? ThingStatus.ONLINE
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

        updateZoneStructure(rachioApiDevice.zones());

        this.apiDevice = rachioApiDevice;
    }

    public void onPollingUpdate() {
        currentScheduleCache.getValue();
        updateZoneRunning();
    }

    public boolean waitForState(DeviceState desiredState, RachioId.@Nullable Zone zoneIdStarted) {
        try {
            int attempts = pollingMode ? 10 : 1;
            for (int i = 0; i < attempts; i++) {
                if (pollingMode) {
                    currentScheduleCache.refreshValue();
                } else {
                    synchronized (this) {
                        logger.debug("Waiting for device state to change to {} (current: {})", desiredState,
                                deviceState);
                        deviceState.wait(WAIT_ZONE_DURATION_SEC * 1000L);
                    }
                }
                if (deviceState == desiredState && (zoneIdStarted == null || zoneRunningId.equals(zoneIdStarted))) {
                    return true;
                }
                if (pollingMode) {
                    Thread.sleep(WAIT_ZONE_DURATION_SEC / 2 * 1000L);
                }
            }
        } catch (InterruptedException e) {
            logger.trace("Interrupted while waiting for device state change: {}", e.getMessage());
        }
        return false;
    }

    public synchronized void updateZoneRunning() { // uses class member currentSchedule
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
                        new DecimalType(requireNonNull(getZoneById(zoneRunningId).zoneNumber())));
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NAME, new StringType(getZoneById(zoneRunningId).name()));
                break;
            case PAUSE:
                updateState(CHANNEL_DEVICE_RUN, OnOffType.ON);
                updateState(CHANNEL_DEVICE_PLAYER, PlayPauseType.PAUSE);
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NUMBER,
                        new DecimalType(requireNonNull(getZoneById(zoneRunningId).zoneNumber())));
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NAME, new StringType(getZoneById(zoneRunningId).name()));
                break;
            default: // UNDEF
                updateState(CHANNEL_DEVICE_RUN, UnDefType.UNDEF);
                updateState(CHANNEL_DEVICE_PLAYER, UnDefType.UNDEF);
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NUMBER, UnDefType.UNDEF);
                updateState(CHANNEL_DEVICE_RUNNING_ZONE_NAME, UnDefType.UNDEF);
                logger.debug("UNKNOWN device status {}", deviceState.name());
                break;
        }

        // update zone status for all zone things
        childHandlers.values().stream() //
                .filter(RachioZoneHandler.class::isInstance) //
                .map(RachioZoneHandler.class::cast) //
                .forEach(rachioZoneHandler -> rachioZoneHandler
                        .updateZoneRunning(rachioZoneHandler.getId().equals(zoneRunningId), zoneRunningDuration));
    }

    public boolean webhookEvent(RachioApiEvent event) {
        switch (event.type()) {
            case RachioApiEvent.SCHEDULE_STARTED_EVENT:
            case RachioApiEvent.SCHEDULE_COMPLETED_EVENT:
            case RachioApiEvent.SCHEDULE_STOPPED_EVENT:
                if (event.payload() instanceof RachioApiEvent.PayloadSchedule payloadSchedule) {
                    RachioApiScheduleRule scheduleRule = apiDevice.scheduleRules().get(payloadSchedule.scheduleId());

                    logger.info("{}: Status {} for schedule {} (start={}, end={}, duration={}min)", apiDevice.name(),
                            event.type(), scheduleRule.name(), payloadSchedule.startTime(), payloadSchedule.endTime(),
                            payloadSchedule.durationSeconds());

                    updateState(CHANNEL_SCHED_NAME, new StringType(scheduleRule.name()));
                    // updateState(CHANNEL_SCHED_INFO, new StringType(event.summary()));
                    updateState(CHANNEL_SCHED_START, new DateTimeType(payloadSchedule.startTime().toInstant()));
                    updateState(CHANNEL_SCHED_END, new DateTimeType(payloadSchedule.endTime().toInstant()));
                }
                break;
            case RachioApiEvent.DEVICE_ZONE_RUN_STARTED_EVENT:
            case RachioApiEvent.DEVICE_ZONE_RUN_COMPLETED_EVENT:
            case RachioApiEvent.DEVICE_ZONE_RUN_STOPPED_EVENT:
            case RachioApiEvent.DEVICE_ZONE_RUN_PAUSED_EVENT:
                break;
            /*
             * case "DEVICE_STATUS":
             * switch (event.subType()) {
             * case "COLD_REBOOT":
             * // logger.info("{}: Device {} was restarted, ip={}/{}, gw={}, dns={}/{}, wifi rssi={}.",
             * // rachoApiDevice.name,
             * // d.name, d.network.ip, d.network.nm, d.network.gw, d.network.dns1, d.network.dns2,
             * // d.network.rssi);
             * // if (event.network != null) {
             * // rachioDevice.setNetwork(event.network);
             * // }
             * break;
             * case "ONLINE":
             * updateStatus(ThingStatus.ONLINE);
             * break;
             * case "OFFLINE":
             * updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, "Controller is offline");
             * break;
             * case "SLEEP_MODE_ON":
             * case "SLEEP_MODE_OFF":
             * // TODO rachioApiDevice.paused = event.subType.equals("SLEEP_MODE_ON") ? true : false;
             * // updateState(CHANNEL_DEVICE_PAUSED, OnOffType.from(rachioApiDevice.paused));
             * break;
             * 
             * // case "RAIN_SENSOR_DETECTION_ON":
             * // case "RAIN_SENSOR_DETECTION_OFF":
             * // rachioApiDevice.rainSensorTripped() = event.subType().equals("RAIN_SENSOR_DETECTION_ON") ? true
             * // : false;
             * // updateState(CHANNEL_DEVICE_RAIN_SENSOR_TRIPPED,
             * // OnOffType.from(rachioApiDevice.rainSensorTripped()));
             * // break;
             * 
             * case "RAIN_DELAY_ON":
             * case "RAIN_DELAY_OFF":
             * // TODO rachioApiDevice.rai
             * // updateState(CHANNEL_DEVICE_RAIN_DELAY, OnOffType.from());
             * break;
             * default:
             * logger.info("Unknown sub-event for DEVICE_STATUS: {}", event);
             * }
             */
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
        properties.put(PROPERTY_NAME, apiDevice.name());
        properties.put(PROPERTY_MODEL, apiDevice.model());
        properties.put(Thing.PROPERTY_SERIAL_NUMBER, apiDevice.serialNumber());
        properties.put(Thing.PROPERTY_MAC_ADDRESS, apiDevice.macAddress());
        properties.put(PROPERTY_DEV_LOCATION,
                String.format("%.04f,%.04f", apiDevice.latitude(), apiDevice.longitude()));
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

        RachioCloudConnectorHandler bridgeHandler = (RachioCloudConnectorHandler) bridge.getHandler();
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
    public RachioApiCurrentSchedule getCurrentSchedule() {
        try {
            RachioApiCurrentSchedule currentSchedule = api.getDeviceCurrentSchedule(id);
            synchronized (this) {
                if (currentSchedule != RachioApiCurrentSchedule.EMPTY) {
                    deviceState = DeviceState.fromStatus(currentSchedule.status());
                    zoneRunningId = currentSchedule.zoneId();
                    zoneRunningDuration = currentSchedule.duration();
                } else {
                    deviceState = DeviceState.STOP;
                    zoneRunningId = RachioId.Zone.EMPTY;
                    zoneRunningDuration = 0;
                }
            }
            return currentSchedule;
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException
                | RateLimitThrottleException e) {
            logger.error("Unable to retrieve current schedule for device '{}': {}", id, e.getMessage());
            return RachioApiCurrentSchedule.EMPTY;
        }
    }

    RachioApiZone getZoneById(RachioId.Zone zoneId) {
        RachioApiZone zone = apiDevice.zones().get(zoneId);
        if (zone == null) {
            logger.warn("Zone with ID '{}' not found in device '{}'", zoneId.idString(), apiDevice.name());
            return RachioApiZone.EMPTY;
        }
        return zone;
    }
}
