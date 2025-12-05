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
package org.openhab.binding.rachio.internal.discovery;

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;

import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.rachio.internal.api.dto.RachioApiBaseStation;
import org.openhab.binding.rachio.internal.api.dto.RachioApiDevice;
import org.openhab.binding.rachio.internal.api.dto.RachioApiValve;
import org.openhab.binding.rachio.internal.api.dto.RachioApiZone;
import org.openhab.binding.rachio.internal.handler.RachioCloudConnectorHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;

/**
 * The {@code RachioDiscoveryService} class implements the methods to add found objects to the inbox. It does not
 * implement a unique discovery background polling mechanism, but instead relies on the regular polling calls for the
 * binding. If a user wants immediate update, the startScan method will initiate an immediate poll.
 *
 * @author Markus Michels - Initial contribution
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class RachioDiscoveryService extends AbstractThingHandlerDiscoveryService<RachioCloudConnectorHandler> {
    private static final Set<ThingTypeUID> DISCOVERABLE_THING_TYPE_UIDS = Objects
            .requireNonNull(Set.of(THING_TYPE_CONTROLLER, THING_TYPE_ZONE, THING_TYPE_BASE_STATION, THING_TYPE_VALVE));

    @Override
    public void initialize() {
        thingHandler.registerDiscoveryListener(this);
        super.initialize();
    }

    @Override
    public void dispose() {
        super.dispose();
        thingHandler.unregisterDiscoveryListener();
    }

    public RachioDiscoveryService() {
        super(RachioCloudConnectorHandler.class, DISCOVERABLE_THING_TYPE_UIDS, 0, false);
    }

    @Override
    protected synchronized void startScan() {
        removeOlderResults(getTimestampOfLastScan());
        if (thingHandler.getThing().getStatus() == ThingStatus.ONLINE) {
            thingHandler.refreshStructure();
        }
    }

    public void notifyDiscoveryController(ThingUID bridgeUID, RachioApiDevice device) {
        ThingUID uid = new ThingUID(THING_TYPE_CONTROLLER, bridgeUID, device.name().replace(" ", "-"));

        DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID) //
                .withProperty(PARAM_ID, device.id().idString()) //
                .withRepresentationProperty(PARAM_ID).withLabel(device.name()) //
                .build();

        thingDiscovered(result);
    }

    public void notifyDiscoveryZone(ThingUID bridgeUID, String bridgeLabel, RachioApiZone zone) {
        ThingUID uid = new ThingUID(THING_TYPE_ZONE, bridgeUID, String.format("zone-%02d", zone.zoneNumber()));
        final String zoneLabelFormat = "%s [%02d]: %s";

        DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID) //
                .withProperty(PARAM_ID, zone.id().idString()) //
                .withRepresentationProperty(PARAM_ID) //
                .withLabel(String.format(zoneLabelFormat, bridgeLabel, zone.zoneNumber(), zone.name())) //
                .build();

        thingDiscovered(result);
    }

    public void notifyDiscoveryBaseStation(ThingUID bridgeUID, RachioApiBaseStation baseStation) {
        String idString = baseStation.id().idString();
        String shortId = idString.substring(idString.length() - 6).toUpperCase();
        ThingUID uid = new ThingUID(THING_TYPE_BASE_STATION, bridgeUID, "base-station-" + shortId);

        DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID) //
                .withProperty(PARAM_ID, idString) //
                .withRepresentationProperty(PARAM_ID) //
                .withLabel("Base Station-" + shortId) //
                .build();

        thingDiscovered(result);
    }

    public void notifyDiscoveryValve(ThingUID bridgeUID, String bridgeLabel, RachioApiValve valve) {
        String id = valve.id().idString();
        String shortId = id.substring(id.length() - 6).toUpperCase();
        ThingUID uid = new ThingUID(THING_TYPE_VALVE, bridgeUID, "valve-" + shortId);
        final String valveLabelFormat = "%s: %s";

        DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(bridgeUID) //
                .withProperty(PARAM_ID, id) //
                .withRepresentationProperty(PARAM_ID) //
                .withLabel(String.format(valveLabelFormat, bridgeLabel, valve.name())) //
                .build();

        thingDiscovered(result);
    }
}
