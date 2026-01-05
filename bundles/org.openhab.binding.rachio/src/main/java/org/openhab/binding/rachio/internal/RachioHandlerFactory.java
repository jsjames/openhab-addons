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
package org.openhab.binding.rachio.internal;

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;

import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.internal.handler.RachioBaseStationHandler;
import org.openhab.binding.rachio.internal.handler.RachioCloudConnectorHandler;
import org.openhab.binding.rachio.internal.handler.RachioControllerHandler;
import org.openhab.binding.rachio.internal.handler.RachioValveHandler;
import org.openhab.binding.rachio.internal.handler.RachioZoneHandler;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link RachioHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding." + BINDING_ID, service = { ThingHandlerFactory.class,
        RachioHandlerFactory.class }, immediate = true)
public class RachioHandlerFactory extends BaseThingHandlerFactory {
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Objects.requireNonNull(Set.of(THING_TYPE_CLOUD,
            THING_TYPE_CONTROLLER, THING_TYPE_ZONE, THING_TYPE_BASE_STATION, THING_TYPE_VALVE));

    private final Logger logger = Objects.requireNonNull(LoggerFactory.getLogger(RachioHandlerFactory.class));
    private final HttpClientFactory httpClientFactory;
    private final HttpService httpService;

    @Nullable
    private RachioCloudConnectorHandler cloudConnectorHandler = null;

    @Activate
    public RachioHandlerFactory(final @Reference HttpClientFactory httpClientFactory,
            final @Reference HttpService httpService) {
        this.httpClientFactory = httpClientFactory;
        this.httpService = httpService;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        try {
            ThingTypeUID thingTypeUID = thing.getThingTypeUID();
            logger.trace("RachioHandlerFactory: Create thing handler for type {}", thingTypeUID.toString());
            if (THING_TYPE_CLOUD.equals(thingTypeUID)) {
                return createBridgeHandler((Bridge) thing, httpClientFactory.getCommonHttpClient());
            }

            RachioCloudConnectorHandler lRachioBridgeHandler = cloudConnectorHandler;
            if (lRachioBridgeHandler == null) {
                logger.debug("RachioHandlerFactory: Unable to create thing handler - no bridge handler found.");
                return null;
            }

            String id = thing.getConfiguration().get(PARAM_ID).toString();
            if (id.isEmpty()) {
                logger.debug("RachioHandlerFactory: Unable to create thing handler - no id configured.");
                return null;
            }

            if (THING_TYPE_CONTROLLER.equals(thingTypeUID)) {
                return new RachioControllerHandler((Bridge) thing, new RachioId.Device(id),
                        lRachioBridgeHandler.getApi(), Objects.requireNonNull(cloudConnectorHandler), httpService);
            } else if (THING_TYPE_ZONE.equals(thingTypeUID)) {
                return new RachioZoneHandler(thing, new RachioId.Zone(id), lRachioBridgeHandler.getApi());
            } else if (THING_TYPE_BASE_STATION.equals(thingTypeUID)) {
                return new RachioBaseStationHandler((Bridge) thing, new RachioId.BaseStation(id),
                        lRachioBridgeHandler.getApi(), Objects.requireNonNull(cloudConnectorHandler));
            } else if (THING_TYPE_VALVE.equals(thingTypeUID)) {
                return new RachioValveHandler(thing, new RachioId.Valve(id), lRachioBridgeHandler.getApi());
            } else {
                logger.debug("RachioHandlerFactory: Unable to create thing handler - {}", thing.getUID());
            }
        } catch (RuntimeException e) {
            logger.debug("RachioHandlerFactory: Exception while creating Rachio Thing handler: {}", e.toString());
        }
        return null;
    }

    @Nullable
    private RachioCloudConnectorHandler createBridgeHandler(Bridge bridgeThing, HttpClient httpClient) {
        if (cloudConnectorHandler != null) {
            logger.debug("RachioHandlerFactory: Duplicate bridge already exists.");
            return null;
        }

        cloudConnectorHandler = new RachioCloudConnectorHandler(bridgeThing, httpClient);
        return cloudConnectorHandler;
    }

    @Override
    protected void removeHandler(ThingHandler thingHandler) {
        if (cloudConnectorHandler != null && thingHandler.equals(cloudConnectorHandler)) {
            cloudConnectorHandler = null;
        }
    }
}
