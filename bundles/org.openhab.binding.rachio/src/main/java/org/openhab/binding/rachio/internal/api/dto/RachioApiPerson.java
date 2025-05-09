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

package org.openhab.binding.rachio.internal.api.dto;

import java.util.Map;

import org.openhab.binding.rachio.internal.api.RachioId;
import org.openhab.binding.rachio.utils.Id;

/**
 * The {@link RachioApiPerson} implements the interface to the Rachio cloud service (using http).
 *
 * @author Markus Michels - Initial contribution
 */
public class RachioApiPerson implements Id<RachioId.Person> {
    public RachioId.Person id;
    public String username;
    public String fullName;
    public String email;
    public Map<RachioId.Device, RachioApiDevice> devices;
    public boolean enabled;

    public static final RachioApiPerson EMPTY = new RachioApiPerson();

    public RachioId.Person getId() {
        return id;
    }
}
