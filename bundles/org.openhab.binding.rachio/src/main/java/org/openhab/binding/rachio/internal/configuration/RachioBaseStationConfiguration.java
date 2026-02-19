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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link RachioBaseStationConfiguration} contains the binding configuration and default values. The field names
 * represent the
 * configuration names, do not rename them if you don't intend to break the configuration interface.
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class RachioBaseStationConfiguration {
    public String id = "";
}
