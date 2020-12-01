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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pentair.internal.PentairDiscoveryService;
import org.openhab.binding.pentair.internal.PentairIntelliChlorPacket;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.openhab.binding.pentair.internal.PentairParser;
import org.openhab.binding.pentair.internal.PentairParser.CallbackPentairParser;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class for all common functions for different bridge implementations. Use as superclass for IPBridge and
 * SerialBridge implementations.
 *
 * - Implements parsing of packets on Pentair bus and dispositions to appropriate Thing
 * - Periodically sends query to any {@link PentairIntelliFloHandler} things
 * - Provides function to write packets
 *
 * @author Jeff James - Initial contribution
 *
 */
@NonNullByDefault
public abstract class PentairBaseBridgeHandler extends BaseBridgeHandler implements CallbackPentairParser {
    private final Logger logger = LoggerFactory.getLogger(PentairBaseBridgeHandler.class);

    /** input stream - subclass needs to assign in connect function */
    protected Optional<BufferedInputStream> reader = Optional.empty();
    /** output stream - subclass needs to assign in connect function */
    protected Optional<BufferedOutputStream> writer = Optional.empty();

    protected final PentairParser parser = new PentairParser();

    /** thread for parser - subclass needs to create/assign connect */
    private @Nullable Thread parserThread;

    /** job for monitoring IO */
    protected @Nullable ScheduledFuture<?> monitorIOJob;
    /** ID to use when sending commands on Pentair bus - subclass needs to assign based on configuration parameter */
    protected int id;
    /** array to keep track of IDs seen on the Pentair bus that are not configured yet */
    protected final Set<Integer> unregistered = new HashSet<Integer>();

    protected final Map<Integer, @Nullable PentairBaseThingHandler> equipment = new HashMap<>();

    protected ConnectState connectstate = ConnectState.INIT;

    private final ReentrantLock lock = new ReentrantLock();
    private Condition waitAck = lock.newCondition();
    private int ackResponse = -1;

    protected boolean discovery = false;

    protected @Nullable PentairDiscoveryService discoveryService;

    public void setDiscoveryService(PentairDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /**
     * Gets pentair bus id
     *
     * @return id
     */
    public int getId() {
        return id;
    }

    protected enum ConnectState {
        CONNECTING,
        DISCONNECTED,
        CONNECTED,
        INIT,
        CONFIGERROR
    };

    /**
     * Constructor
     *
     * @param bridge
     */
    PentairBaseBridgeHandler(Bridge bridge) {
        super(bridge);
        parser.setCallback(this);
        connectstate = ConnectState.INIT;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(PentairDiscoveryService.class);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            logger.debug("Bridge received refresh command");
        }
    }

    @Override
    public void initialize() {
        logger.debug("initializing Pentair Bridge handler.");

        internalConnect();
    }

    @Override
    public void dispose() {
        if (monitorIOJob != null) {
            monitorIOJob.cancel(true);
        }

        internalDisconnect();
    }

    /*
     * Custom function to call during initialization to notify the bridge. childHandlerInitialized is not called
     * until the child thing actually goes to the ONLINE status.
     */
    public void childHandlerInitializing(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof PentairBaseThingHandler) {
            equipment.put(((PentairBaseThingHandler) childHandler).id, (PentairBaseThingHandler) childHandler);
            unregistered.remove(((PentairBaseThingHandler) childHandler).id);
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        if (childHandler instanceof PentairBaseThingHandler) {
            equipment.remove(((PentairBaseThingHandler) childHandler).id);
        }
    }

    /**
     * Abstract method for creating connection. Must be implemented in subclass.
     * Return 0 if all goes well. Must call setInputStream and setOutputStream before exciting.
     *
     * @throws Exception
     */
    protected abstract int connect();

    protected abstract void disconnect();

    private void internalConnect() {
        if (connectstate != ConnectState.DISCONNECTED && connectstate != ConnectState.INIT) {
            logger.debug("_connect() without ConnectState == DISCONNECTED or INIT: {}", connectstate);
        }

        connectstate = ConnectState.CONNECTING;

        if (connect() != 0) {
            connectstate = ConnectState.CONFIGERROR;

            return;
        }

        // montiorIOJob will only start after a successful connection
        if (monitorIOJob == null) {
            monitorIOJob = scheduler.scheduleWithFixedDelay(() -> monitorIO(), 60, 30, TimeUnit.SECONDS);
        }

        parserThread = new Thread(parser, "OH-pentair-" + this.getThing().getUID() + "-parser");
        parserThread.setDaemon(true);
        parserThread.start();

        if (reader.isPresent() && writer.isPresent()) {
            updateStatus(ThingStatus.ONLINE);
            connectstate = ConnectState.CONNECTED;
        } else {
            // this should never occur
            logger.debug("Reader or Write did not get created during connect()");
        }
    }

    public void setInputStream(InputStream inputStream) {
        reader.ifPresent(close -> {
            try {
                close.close();
            } catch (IOException e) {
                logger.debug("setInputStream: Exception error while closing: {}", e.getMessage());
            }
        });

        reader = Optional.of(new BufferedInputStream(inputStream));
        parser.setInputStream(inputStream);
    }

    public void setOutputStream(OutputStream outputStream) {
        writer.ifPresent(close -> {
            try {
                close.close();
            } catch (IOException e) {
                logger.debug("setOutputStream: Exception error while closing: {}", e.getMessage());
            }
        });

        writer = Optional.of(new BufferedOutputStream(outputStream));
    }

    private void internalDisconnect() {
        if (parserThread != null) {
            try {
                parserThread.interrupt();
                parserThread.join(3000); // wait for thread to complete
            } catch (InterruptedException e) {
                // do nothing
            }
            parserThread = null;
        }

        reader.ifPresent(close -> {
            try {
                close.close();
            } catch (IOException e) {
                logger.debug("setInputStream: Exception error while closing: {}", e.getMessage());
            }
        });

        writer.ifPresent(close -> {
            try {
                close.close();
            } catch (IOException e) {
                logger.debug("setOutputStream: Exception error while closing: {}", e.getMessage());
            }
        });

        disconnect();

        connectstate = ConnectState.DISCONNECTED;
    }

    // method to poll to try and reconnect upon being disconnected. Note this should only be started on an initial
    private void monitorIO() {
        logger.debug("MonitorIO");
        // ConnectState.DISCONNECTED implies the connection had at one time been successfully connected and
        // therefore the
        // configuration is correct. This state can be reached when an exception happens where the connection has
        // been
        // interrupted.
        switch (connectstate) {
            case DISCONNECTED: // Try to reconnect
            case CONFIGERROR:
                internalConnect();
                break;
            case CONNECTED:
                // Check if parser thread has terminated and if it has reconnect. This will take down the interface and
                // restart the interface.
                Objects.requireNonNull(parserThread);
                if (!parserThread.isAlive()) {
                    internalDisconnect();
                    internalConnect();
                }
                break;
            case CONNECTING: // in the process of connecting or initing, do nothing
            case INIT:
                break;
        }
    }

    /**
     * Helper function to find a Thing assigned to this bridge with a specific pentair bus id.
     *
     * @param id Pentair bus id
     * @return Thing object. null if id is not found.
     */
    public @Nullable Thing findThing(int id) {
        List<Thing> things = getThing().getThings();

        for (Thing t : things) {
            PentairBaseThingHandler handler = (PentairBaseThingHandler) t.getHandler();

            if (handler != null && handler.getPentairID() == id) {
                return t;
            }
        }

        return null;
    }

    @Override
    public void onPentairPacket(PentairPacket p) {
        PentairBaseThingHandler thinghandler;

        thinghandler = equipment.get(p.getSource());

        if (thinghandler == null) {
            int source = p.getSource();
            int sourceType = (source >> 4);

            if (sourceType == 0x02) { // control panels are 0x2*, don't treat as an
                                      // unregistered device
                logger.debug("Command from control panel device ({}): {}", p.getSource(), p);
            } else if (!unregistered.contains(p.getSource())) { // if not yet seen, print out ONE message and discover
                if (sourceType == 0x01) { // controller
                    if (PentairControllerHandler.onlineController == null) { // only register one
                                                                             // controller
                        if (discovery && discoveryService != null) {
                            discoveryService.notifyDiscoveredController(source);
                        }
                    }
                } else if (sourceType == 0x06) {
                    if (discovery && discoveryService != null) {
                        discoveryService.notifyDiscoverdIntelliflo(source);
                    }
                } else if (sourceType == 0x09) {
                    if (discovery && discoveryService != null) {
                        discoveryService.notifyDiscoveryIntellichem(source);
                    }
                }

                logger.debug("First command from unregistered device ({}): {}", p.getSource(), p);
                unregistered.add(p.getSource());
            } else {
                logger.trace("Subsequent command from unregistered device ({}): {}", p.getSource(), p);
            }
        } else {
            logger.debug("Received pentair command: {}", p);

            thinghandler.processPacketFrom(p);
            ackResponse(p.getAction());
        }
    }

    @Override
    public void onIntelliChlorPacket(PentairIntelliChlorPacket p) {
        PentairBaseThingHandler thinghandler;

        thinghandler = equipment.get(0);

        if (thinghandler == null) {
            if (!unregistered.contains(0)) { // if not yet seen, print out log message
                if (discovery && discoveryService != null) {
                    discoveryService.notifyDiscoveredIntellichlor(0);
                }
                logger.debug(" First command from unregistered Intelliflow: {}", p);
                unregistered.add(0);
            } else {
                logger.trace("Subsequent command from unregistered Intelliflow: {}", p);
            }

            return;
        }

        thinghandler.processPacketFrom(p);
    }

    /**
     * Method to write a package on the Pentair bus. Will add preamble and checksum to bytes written
     *
     * @param p {@link PentairPacket} to write
     */
    public void writePacket(PentairPacket p) {
        writePacket(p, -1, 0);
    }

    public boolean writePacket(PentairPacket p, int response, int retries) {
        boolean bReturn = true;

        try {
            byte[] buf;
            int nRetries = retries;

            if (!writer.isPresent()) {
                logger.debug("writePacket: writer = null");
                return false;
            }
            p.setSource(id);

            buf = p.getFullWriteStream();

            lock.lock();
            ackResponse = response;

            do {
                logger.debug("Writing packet: {}", PentairPacket.toHexString(buf));

                writer.get().write(buf, 0, buf.length);
                writer.get().flush();

                if (response != -1) {
                    logger.debug("writePacket: wait for ack (response: {}, retries: {})", response, nRetries);
                    bReturn = waitAck.await(1000, TimeUnit.MILLISECONDS); // bReturn will be false if timeout
                    nRetries--;
                }
            } while (!bReturn && (nRetries >= 0));
        } catch (IOException e) {
            logger.debug("I/O error while writing stream: {}", e.getMessage());
            internalDisconnect();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (InterruptedException e) {
            logger.debug("writePacket: InterruptedException: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }

        if (!bReturn) {
            logger.debug("writePacket: timeout");
        }
        return bReturn;
    }

    /**
     * Method to acknowledge an ack or response packet has been sent
     *
     * @param cmdresponse is the command that was seen as a return. This is validate against that this was the response
     *            before signally a return.
     */
    public void ackResponse(int response) {
        if (response != ackResponse) {
            return;
        }

        lock.lock();
        waitAck.signalAll();
        lock.unlock();
    }
}
