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

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.rachio.internal.RachioUtils;
import org.openhab.binding.rachio.internal.api.RachioApi;
import org.openhab.binding.rachio.internal.api.RachioApiException;
import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.internal.api.RachioWebhookServlet;
import org.openhab.binding.rachio.internal.api.dto.RachioApiBaseStation;
import org.openhab.binding.rachio.internal.api.dto.RachioApiDevice;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEvent;
import org.openhab.binding.rachio.internal.api.dto.RachioApiPerson;
import org.openhab.binding.rachio.internal.configuration.RachioBridgeConfiguration;
import org.openhab.binding.rachio.internal.discovery.RachioDiscoveryService;
import org.openhab.binding.rachio.utils.ClientRateLimitManager.RateLimitThrottleException;
import org.openhab.core.cache.ExpiringCache;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link RachioBridgeHandler} is responsible for implementing the cloud api access.
 * The concept of a Bridge is used. In general multiple bridges are supported using different API keys.
 * Devices are linked to the bridge. All devices and zones go offline if the cloud api access fails.
 *
 * @author Markus Michels - initial contribution
 * @author Jeff James - initial contribution
 */
@NonNullByDefault
public class RachioBridgeHandler extends BaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(RachioBridgeHandler.class);
    private final RachioApi rachioApi;
    private @Nullable RachioDiscoveryService discoveryService;
    private RachioBridgeConfiguration config;
    private RachioId.Person personId = RachioId.Person.EMPTY;
    private ExpiringCache<RachioApiPerson> rachioApiPersonCache = new ExpiringCache<>(Duration.ofSeconds(30),
            this::getApiPerson);
    private ExpiringCache<Map<RachioId.BaseStation, RachioApiBaseStation>> rachioApiBaseStationsCache = new ExpiringCache<>(
            Duration.ofSeconds(120), this::getApiBaseStations);
    private Map<RachioId.Device, RachioControllerHandler> controllerHandlers = new HashMap<>();
    private Map<RachioId.BaseStation, RachioBaseStationHandler> baseStationHandlers = new HashMap<>();

    RachioWebhookServlet rachioWebhookServlet;

    @Nullable
    private ScheduledFuture<?> pollingJob;
    private boolean jobPending = false;

    public RachioBridgeHandler(final Bridge bridge, HttpClient httpClient, HttpService httpService) {
        super(bridge);
        rachioApi = new RachioApi(httpClient);
        rachioWebhookServlet = new RachioWebhookServlet(httpService, this);
        config = getConfigAs(RachioBridgeConfiguration.class);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(this::goOnline);
    }

    public void goOnline() {
        String errorMessage = "";

        try {
            logger.debug("RachioCloud: Connecting to Rachio Cloud");
            rachioApi.initialize(config.apikey);
            personId = rachioApi.getPersonId();

            updateProperties();
            updateStatus(ThingStatus.ONLINE);

            // Call after bridge is online
            refreshDeviceStatus();
        } catch (RachioApiException | RateLimitThrottleException e) {
            errorMessage = e.toString();
            /*
             * TODO
             * if (e.getApiResult().isResponseRateLimit()) {
             * logger.warn("RachioCloud: Account is blocked due to rate limit, wait 24h and retry");
             * }
             */
        } catch (RuntimeException | InterruptedException | TimeoutException | ExecutionException e) {
            errorMessage = requireNonNullElse(e.getLocalizedMessage(), "Unknown exception occurred");
        } finally {
            if (!errorMessage.isEmpty()) {
                logger.debug("RachioCloud: {}", errorMessage);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, errorMessage);
            }
        }
    }

    public void goOffline(ThingStatusDetail thingStatusDetail, @Nullable String description) {
        updateStatus(ThingStatus.OFFLINE, thingStatusDetail, description);
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(RachioDiscoveryService.class);
    }

    public boolean registerDiscoveryListener(RachioDiscoveryService listener) {
        if (discoveryService == null) {
            discoveryService = listener;
            return true;
        }

        return false;
    }

    public boolean unregisterDiscoveryListener() {
        if (discoveryService != null) {
            discoveryService = null;
            return true;
        }

        return false;
    }

    @Override
    public void handleCommand(final ChannelUID channelUID, final Command command) {
        logger.debug("RachioCloud: Command {} for {} ignored", command, channelUID.getAsString());
    }

    @Nullable
    protected RachioApiPerson getApiPerson() {
        try {
            return rachioApi.getPerson(personId);
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException e) {
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR, "@text/rachio-api-communication-error");
            logger.error("Exception during getPerson: {}", e.getLocalizedMessage());
            return null;
        } catch (RateLimitThrottleException e) {
            return null;
        }
    }

    @Nullable
    protected Map<RachioId.BaseStation, RachioApiBaseStation> getApiBaseStations() {
        try {
            return rachioApi.getBaseStations(personId);
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException e) {
            goOffline(ThingStatusDetail.COMMUNICATION_ERROR, "@text/rachio-api-communication-error");
            logger.error("Exception during getBaseStations: {}", e.getLocalizedMessage());
            return null;
        } catch (RateLimitThrottleException e) {
            return null;
        }
    }

    /**
     * Update device status (poll Rachio Cloud)
     * in addition webhooks are used to get events (if callbackUrl is configured)
     */
    public void refreshDeviceStatus() {
        String errorMessage = "";
        logger.trace("RachioCloud: refreshDeviceStatus");

        if (getThing().getStatus() == ThingStatus.OFFLINE
                && getThing().getStatusInfo().getStatusDetail() == ThingStatusDetail.COMMUNICATION_ERROR) {
            logger.debug("Try to goOnline");
            // TODO goOnline();
        }

        if (getThing().getStatus() != ThingStatus.ONLINE && getThing().getStatus() != ThingStatus.UNKNOWN) {
            return;
        }

        RachioDiscoveryService localDiscoveryService = requireNonNull(discoveryService);

        try {
            synchronized (this) {
                if (jobPending) {
                    logger.debug("RachioCloud: Already checking");
                    return;
                }
                jobPending = true;
            }

            RachioApiPerson localRachioApiPerson = rachioApi.getPerson(personId);
            RachioUtils.compareCollections(localRachioApiPerson.devices.keySet(), controllerHandlers.keySet(),
                    id -> localDiscoveryService.notifyDiscoveryController(getThing().getUID(),
                            requireNonNull(localRachioApiPerson.devices.get(id))),
                    id -> requireNonNull(controllerHandlers.get(id)).goOffline(ThingStatusDetail.GONE,
                            "@text/controller-removed"),
                    id -> requireNonNull(controllerHandlers.get(id))
                            .onStatusRefresh(requireNonNull(localRachioApiPerson.devices.get(id))));
            rachioApiPersonCache.putValue(localRachioApiPerson);

            Map<RachioId.BaseStation, RachioApiBaseStation> rachioApiBaseStations = rachioApi.getBaseStations(personId);
            RachioUtils.compareCollections(rachioApiBaseStations.keySet(), baseStationHandlers.keySet(),
                    id -> localDiscoveryService.notifyDiscoveryBaseStation(getThing().getUID(),
                            requireNonNull(rachioApiBaseStations.get(id))),
                    id -> requireNonNull(baseStationHandlers.get(id)).goOffline(ThingStatusDetail.GONE,
                            "@text/basestation-removed"),
                    id -> requireNonNull(baseStationHandlers.get(id))
                            .onStatusRefresh(requireNonNull(rachioApiBaseStations.get(id))));
            rachioApiBaseStationsCache.putValue(rachioApiBaseStations);
        } catch (RachioApiException e) {
            errorMessage = e.toString();
        } catch (InterruptedException | TimeoutException | ExecutionException | RateLimitThrottleException e) {
            errorMessage = requireNonNull(e.getLocalizedMessage(), "Exception during API");
        } finally {
            if (!errorMessage.isEmpty()) {
                logger.debug("RachioBridge: {}", errorMessage);
            }
            jobPending = false;
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof RachioControllerHandler rachioControllerHandler) {
            controllerHandlers.put(rachioControllerHandler.getId(), rachioControllerHandler);
        }

        if (childHandler instanceof RachioBaseStationHandler rachioBaseStationHandler) {
            baseStationHandlers.put(rachioBaseStationHandler.getId(), rachioBaseStationHandler);
        }
        updateListenerManagement();
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof RachioControllerHandler rachioControllerHandler) {
            controllerHandlers.remove(rachioControllerHandler.getId());
        }

        if (childHandler instanceof RachioBaseStationHandler rachioBaseStationHandler) {
            baseStationHandlers.remove(rachioBaseStationHandler.getId());
        }
        updateListenerManagement();
    }

    /**
     * Retrieve the callback URL for Rachio Cloud Eevents
     *
     * @return callbackUrl
     */
    public String getCallbackUrl() {
        return getConfigAs(RachioBridgeConfiguration.class).callbackUrl;
    }

    /**
     * Retrieve the clearAllCallbacks flag from thing config
     *
     * @return true=clear all callbacks, false=clear only the current one (avoid multiple instances)
     */
    public Boolean getClearAllCallbacks() {
        return getConfigAs(RachioBridgeConfiguration.class).clearAllCallbacks;
    }

    public RachioDiscoveryService getDiscoveryService() {
        return requireNonNull(discoveryService, "Invalid state - discoveryService not set.");
    }

    @Nullable
    public Map<RachioId.Device, RachioApiDevice> getDevices() {
        RachioApiPerson rachioApiPerson = rachioApiPersonCache.getValue();
        if (rachioApiPerson == null) {
            return null;
        }

        return rachioApiPerson.devices;
    }

    @Nullable
    RachioApiDevice getDeviceById(RachioId.Device id) {
        Map<RachioId.Device, RachioApiDevice> deviceMap = getDevices();
        if (deviceMap == null) {
            return null;
        }
        return deviceMap.get(id);
    }

    @Nullable
    public Map<RachioId.BaseStation, RachioApiBaseStation> getBaseStations() {
        Map<RachioId.BaseStation, RachioApiBaseStation> rachioApiBaseStations = rachioApiBaseStationsCache.getValue();
        if (rachioApiBaseStations == null) {
            return null;
        }

        return rachioApiBaseStations;
    }

    @Nullable
    RachioApiBaseStation getBaseStationByID(RachioId.BaseStation id) {
        Map<RachioId.BaseStation, RachioApiBaseStation> rachioBaseStations = rachioApiBaseStationsCache.getValue();
        if (rachioBaseStations == null) {
            return null;
        }

        return rachioBaseStations.get(id);
    }

    /**
     * Handle inbound Webhook event (dispatch to device handler)
     *
     * @param event
     * @return
     */
    public boolean webhookEvent(RachioApiEvent event) {
        // TODO
        if (controllerHandlers.get(event.id) instanceof RachioControllerHandler rachioControllerHandler) {
            return rachioControllerHandler.webhookEvent(event);
        } else {
            logger.debug("RachioCloud: Event {}.{} for unknown device {}: {}", event.category, event.type,
                    event.deviceId, event.summary);
        }
        return false;
    }

    /**
     * Start or stop a background polling job to look for bed status updates based on whether or not there are any
     * listeners to notify.
     */
    private synchronized void updateListenerManagement() {
        ScheduledFuture<?> job = pollingJob;
        if ((!controllerHandlers.isEmpty() || !baseStationHandlers.isEmpty()) && (job == null || job.isCancelled())) {
            pollingJob = scheduler.scheduleWithFixedDelay(pollingRunnable, config.pollingInterval,
                    config.pollingInterval, TimeUnit.SECONDS);
        } else if (controllerHandlers.isEmpty() && baseStationHandlers.isEmpty() && job != null && !job.isCancelled()) {
            job.cancel(true);
            pollingJob = null;
        }
    }

    /**
     * Update the given properties with attributes of the given bed. If no properties are given, a new map will be
     * created.
     *
     * @param bed the source of data
     * @param properties the properties to update (this may be <code>null</code>)
     * @return the given map (or a new map if no map was given) with updated/set properties from the supplied bed
     */
    private void updateProperties() {
        RachioApiPerson rachioApiPerson = rachioApiPersonCache.getValue();
        if (rachioApiPerson == null) {
            return;
        }

        Map<String, String> properties = new HashMap<>();
        properties.put(PROPERTY_PERSON_USER, rachioApiPerson.username);
        properties.put(PROPERTY_PERSON_NAME, rachioApiPerson.fullName);
        properties.put(PROPERTY_PERSON_EMAIL, rachioApiPerson.email);
        updateProperties(properties);
    }

    private Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                refreshDeviceStatus();
            } catch (Exception e) {
                logger.error("Unhandled exception: {}", e.toString());
            }
        }
    };

    @Override
    public synchronized void dispose() {
        logger.debug("RachioCloud: Disposing handler");

        rachioWebhookServlet.dispose();

        // TODO getApi().deleteAllWebhooks();

        ScheduledFuture<?> job = pollingJob;
        if (job != null && !job.isCancelled()) {
            job.cancel(true);
            pollingJob = null;
        }
    }

    public RachioApi getApi() {
        return rachioApi;
    }
}
