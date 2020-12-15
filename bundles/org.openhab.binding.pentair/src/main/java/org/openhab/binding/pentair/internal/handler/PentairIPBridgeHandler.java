/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.pentair.internal.handler;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pentair.internal.config.PentairIPBridgeConfig;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for the IPBridge. Implements the connect and disconnect abstract methods of {@link PentairBaseBridgeHandler}
 *
 * @author Jeff James - Initial contribution
 *
 */

@NonNullByDefault
public class PentairIPBridgeHandler extends PentairBaseBridgeHandler {
    private final Logger logger = LoggerFactory.getLogger(PentairIPBridgeHandler.class);

    public PentairIPBridgeConfig config = new PentairIPBridgeConfig();

    /** Socket object for connection */
    protected @Nullable Socket socket;

    public PentairIPBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    protected synchronized boolean connect() {
        config = getConfigAs(PentairIPBridgeConfig.class);

        this.id = config.id;
        this.discovery = config.discovery;

        try {
            Socket socket = new Socket(config.address, config.port);
            this.socket = socket;

            setInputStream(socket.getInputStream());
            setOutputStream(socket.getOutputStream());

            logger.debug("Pentair IPBridge connected to {}:{}", config.address, config.port);
        } catch (UnknownHostException e) {
            String msg = String.format("unknown host name: %s, %s", config.address, e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return false;
        } catch (IOException e) {
            String msg = String.format("cannot open connection to %s, %s", config.address, e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, msg);
            return false;
        }

        return true;
    }

    @Override
    protected synchronized void disconnect() {
        updateStatus(ThingStatus.OFFLINE);

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                logger.debug("error when closing socket ", e);
            }
            socket = null;
        }
    }
}
