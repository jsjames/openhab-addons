/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
import java.util.List;
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
import org.openhab.binding.rachio.internal.api.dto.RachioApiNotificationWebhook;
import org.openhab.binding.rachio.internal.api.dto.RachioApiNotificationWebhookEventType;
import org.openhab.binding.rachio.internal.api.dto.RachioApiWebhook;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager.RateLimitThrottleException;
import org.openhab.binding.rachio.internal.utils.RachioUtils;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.slf4j.Logger;

/**
 * {@link AbstractRachioBridgeHandler}
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public abstract class AbstractRachioBridgeHandler<BH extends BaseBridgeHandler, ID extends RachioId.Id, CH_ID extends RachioId.Id>
        extends BaseBridgeHandler {
    protected final Logger logger;
    protected final RachioApi api;
    protected final ID id;
    protected final RachioCloudConnectorHandler cloudConnectorHandler;
    protected Map<CH_ID, AbstractRachioThingHandler<?, ?>> childHandlers = new HashMap<>();
    @Nullable
    protected RachioId webhookId = null;

    public AbstractRachioBridgeHandler(final Bridge thing, ID id, final RachioApi api,
            RachioCloudConnectorHandler cloudConnectorHandler, Logger logger) {
        super(thing);
        this.api = api;
        this.id = id;
        this.cloudConnectorHandler = cloudConnectorHandler;
        this.logger = logger;
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

    public void dispose() {
        unregisterWebhooks();
        /*
         * try {
         * if (webhookId != RachioId.Webhook.EMPTY) {
         * api.deleteWebhook(webhookId);
         * }
         * } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException
         * | RateLimitThrottleException e) {
         * logger.debug("Unhandled exception when removing webhook: {}", e.getLocalizedMessage());
         * } finally {
         * webhookId = RachioId.Webhook.EMPTY;
         * }
         */
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

    public void registerWebhook() {

        try {
            unregisterWebhooks();

            final String webhookVersion = cloudConnectorHandler.getWebhookVersion();

            if (webhookVersion.equals("None") || webhookVersion.isEmpty()
                    || cloudConnectorHandler.getWebhookCallbackUrl().isEmpty()) {
                logger.debug("Webhook version is set to None or empty, skipping webhook registration");
                return;
            }

            if (webhookVersion.equals("Legacy")) {
                if (!(this instanceof RachioControllerHandler)) {
                    logger.warn("Legacy webhooks are not supported by this handler, skipping webhook registration");
                    return;
                }

                List<RachioApiNotificationWebhookEventType> webhooks = api.getNotificationWebhookEventTypes();

                logger.debug("Available legacy webhook event types: {}", webhooks);

                // logger.debug("Registered legacy webhook with id {}", webhookId.idString());
            } else {
                // api.createV2Webhook(getExternalId(), cloudConnectorHandler.getWebhookCallbackUrl(), webhookVersion,
                // getWebhookEventTypes());
                // logger.debug("Registered webhook with id {}", webhookId.idString());
            }
        } catch (InterruptedException | ExecutionException | RachioApiException | TimeoutException
                | RateLimitThrottleException e) {
            logger.error("Exception: {}", e.getLocalizedMessage());
        }

        /*
         * List<String> eventTypes = api.getNotificationWebhookEventTypes();
         * RachioApiWebhook rachioApiWebhook = api.postNotificationWebhook(id, callbackUrl, externalId,
         * eventTypes);
         * 
         * return rachioApiWebhook.id();
         */
    }

    public void unregisterWebhooks() {
        unregisterLegacyWebhook();
        unregisterV2Webhooks();
        webhookId = null;
    }

    public void unregisterLegacyWebhook() {
        try {
            List<RachioApiNotificationWebhook> webhooks = api.getNotificationDeviceWebhook((RachioId.Device) id);

            for (RachioApiNotificationWebhook webhook : webhooks) {
                logger.debug("Webhook: id='{}', url='{}', externalId='{}'", webhook.id(), webhook.url(),
                        webhook.externalId());
                if (webhook.url().equals(cloudConnectorHandler.getWebhookCallbackUrl())
                        && webhook.externalId().equals(getExternalId())) {
                    logger.debug("The callback url '{}' is already registered -> delete", webhook.url());
                    api.deleteNotificationWebhook(webhook.id());
                }
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException
                | RateLimitThrottleException e) {
            logger.error("Exception: {}", e.getLocalizedMessage());
        }
    }

    public void unregisterV2Webhooks() {
        try {
            List<RachioApiWebhook> webhooks = api.getListWebhook(id);

            for (RachioApiWebhook webhook : webhooks) {
                logger.debug("Webhook: id='{}', url='{}', externalId='{}'", webhook.id(), webhook.url(),
                        webhook.externalId());
                if (webhook.url().equals(cloudConnectorHandler.getWebhookCallbackUrl())
                        && webhook.externalId().equals(getExternalId())) {
                    logger.debug("The callback url '{}' is already registered -> delete", webhook.url());
                    api.deleteWebhook((RachioId.Webhook) webhook.id());
                }
            }
        } catch (InterruptedException | TimeoutException | ExecutionException | RachioApiException
                | RateLimitThrottleException e) {
            logger.error("Exception: {}", e.getLocalizedMessage());
        }
    }
}
