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
package org.openhab.binding.rachio.internal.configuration;

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link RachioBridgeConfiguration} contains the binding configuration and default values. The field names
 * represent the configuration names, do not rename them if you don't intend to break the configuration interface.
 *
 * @author Markus Michels - Initial contribution
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class RachioBridgeConfiguration {
    public String apikey = "";
    public int pollingInterval = DEFAULT_POLLING_INTERVAL_SEC;
    public int defaultRuntime = DEFAULT_ZONE_RUNTIME_SEC;
    public String callbackUrl = "";
    public Boolean clearAllCallbacks = false;
}
