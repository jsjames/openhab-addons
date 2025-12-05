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
package org.openhab.binding.rachio.internal.api;

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.text.MessageFormat;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link RachioApiException} implements an extension to the standard Exception class. This allows to keep also the
 * result of the last API call (e.g. including the http status code in the message).
 *
 * @author Markus Michels - Initial contribution
 */

@NonNullByDefault
public class RachioApiException extends Exception {
    private static final long serialVersionUID = 1L;
    @Nullable
    private Throwable e = null;
    private boolean isConfigurationError;

    public RachioApiException(String message, boolean isConfigurationError) {
        super(message);
        this.isConfigurationError = isConfigurationError;
    }

    public boolean isConfigurationError() {
        return isConfigurationError;
    }

    @Override
    public String toString() {
        String message = super.getMessage();
        Throwable ex = e;
        if (ex != null) {
            if (ex.getClass() == UnknownHostException.class) {
                String[] string = message.split(": "); // java.net.UnknownHostException: api.rach.io
                message = MessageFormat.format("Unable to connect to {0} (unknown host / internet connection down)",
                        string[1]);
            } else if (ex.getClass() == MalformedURLException.class) {
                message = MessageFormat.format("Invalid URL: {0}", message);
            } else {
                message = MessageFormat.format("{0} ({1}", ex.toString(), ex.getMessage());
            }
        } else {
            message = MessageFormat.format("{0} ({1})", super.getClass().toString(), super.getMessage());
        }

        return message == null ? "" : message;
    }
}
