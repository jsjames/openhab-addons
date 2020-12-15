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

import static org.openhab.binding.pentair.internal.PentairBindingConstants.*;

import java.util.Calendar;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.pentair.internal.ExpiringCache;
import org.openhab.binding.pentair.internal.PentairControllerCircuit;
import org.openhab.binding.pentair.internal.PentairControllerConstants;
import org.openhab.binding.pentair.internal.PentairControllerConstants.LightMode;
import org.openhab.binding.pentair.internal.PentairControllerSchedule;
import org.openhab.binding.pentair.internal.PentairControllerStatus;
import org.openhab.binding.pentair.internal.PentairHeatStatus;
import org.openhab.binding.pentair.internal.PentairPacket;
import org.openhab.binding.pentair.internal.config.PentairControllerHandlerConfig;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link PentairControllerHandler} is responsible for implementation of the EasyTouch Controller. It will handle
 * commands sent to a thing and implements the different channels. It also parses of the packets seen on the
 * bus from the controller.
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class PentairControllerHandler extends PentairBaseThingHandler {
    protected static final int NUM_CIRCUITS = PentairControllerStatus.NUMCIRCUITS;
    protected static final int NUM_SCHEDULES = 9;
    private static final int CACHE_EXPIRY = (int) TimeUnit.SECONDS.toMillis(10);
    private static final int CACHE_EXPIRY_LONG = (int) TimeUnit.MINUTES.toMillis(10);

    protected static final String[] CIRCUIT_GROUPS = { CONTROLLER_SPACIRCUIT, CONTROLLER_AUX1CIRCUIT,
            CONTROLLER_AUX2CIRCUIT, CONTROLLER_AUX3CIRCUIT, CONTROLLER_AUX4CIRCUIT, CONTROLLER_POOLCIRCUIT,
            CONTROLLER_AUX5CIRCUIT, CONTROLLER_AUX6CIRCUIT, CONTROLLER_AUX7CIRCUIT, CONTROLLER_AUX8CIRCUIT,
            CONTROLLER_FEATURE1, CONTROLLER_FEATURE2, CONTROLLER_FEATURE3, CONTROLLER_FEATURE4, CONTROLLER_FEATURE5,
            CONTROLLER_FEATURE6, CONTROLLER_FEATURE7, CONTROLLER_FEATURE8 };

    private boolean serviceMode = false;
    private boolean uom = false;

    private boolean syncTime = true;

    private final Logger logger = LoggerFactory.getLogger(PentairControllerHandler.class);

    protected @Nullable ScheduledFuture<?> syncTimeJob;

    private int preambleByte = -1; // Byte to use after 0xA5 in communicating to controller. Not sure why this changes,
                                   // but it requires to be in sync and up-to-date
    private long lastScheduleTypeWrite;

    protected final ExpiringCache<PentairControllerStatus> controllerStatusCache = new ExpiringCache<>(CACHE_EXPIRY);
    protected final ExpiringCache<PentairHeatStatus> heatStatusCache = new ExpiringCache<>(CACHE_EXPIRY);

    private int majorrev, minorrev;

    protected final ExpiringCache<PentairControllerCircuit>[] circuitsCache = new ExpiringCache[NUM_CIRCUITS];
    protected final ExpiringCache<PentairControllerSchedule>[] schedulesCache = new ExpiringCache[NUM_SCHEDULES];

    protected @Nullable LightMode lightMode;

    public PentairControllerHandler(Thing thing) {
        super(thing);

        for (int i = 0; i < NUM_SCHEDULES; i++) {
            schedulesCache[i] = new ExpiringCache<>(CACHE_EXPIRY_LONG);
        }

        for (int i = 0; i < NUM_CIRCUITS; i++) {
            circuitsCache[i] = new ExpiringCache<>(CACHE_EXPIRY_LONG);
        }
    }

    @Override
    public void readConfiguration() {
        PentairControllerHandlerConfig config = getConfigAs(PentairControllerHandlerConfig.class);

        this.id = config.id;
        this.syncTime = config.synctime;
    }

    @Override
    public void goOnline() {
        // Only a single controller is supported on the Pentair bus so prevent multiple controller
        // things being created.
        PentairControllerHandler handler = Objects.requireNonNull(getBridgeHandler()).findController();

        if (handler != null && !handler.equals(this)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Another controller is already configured.");
        } else {
            super.goOnline();
        }
    }

    @Override
    public void finishOnline() {
        super.finishOnline();

        // setup syncTimeJob to run once a day, initial time to sync is 3 minutes after controller goes online. This is
        // to prevent collision with main thread queries on initial startup
        syncTimeJob = scheduler.scheduleWithFixedDelay(this::syncTime, 3, 24 * 60 * 60, TimeUnit.MINUTES);

        scheduler.execute(() -> readControllerSettings());
    }

    public void syncTime() {
        boolean synctime = ((boolean) getConfig().get(CONTROLLER_CONFIGSYNCTIME));
        if (synctime) {
            logger.debug("Synchronizing System Time with Pentair controller");
            Calendar now = Calendar.getInstance();
            sendClockSettings(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.DAY_OF_WEEK),
                    now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.MONTH) + 1, now.get(Calendar.YEAR) - 2000);
        }
    }

    public void readControllerSettings() {
        int i;

        requestSWVersion();
        requestHeatStatus();
        requestClockSettings();

        for (i = 1; i <= NUM_CIRCUITS; i++) {
            requestCircuitNameFunction(i);
        }

        for (i = 1; i <= NUM_SCHEDULES; i++) {
            requestSchedule(i);
        }

        requestLightGroups();
        requestValves();
    }

    @Override
    public void goOffline(ThingStatusDetail detail) {
        ScheduledFuture<?> syncTimeJob;

        super.goOffline(detail);

        syncTimeJob = this.syncTimeJob;
        if (syncTimeJob != null) {
            syncTimeJob.cancel(true);
        }
    }

    public @Nullable PentairControllerCircuit getCircuitByGroupID(String groupID) {
        for (ExpiringCache<PentairControllerCircuit> circuitItem : circuitsCache) {
            PentairControllerCircuit circuit = circuitItem.getLastKnownValue();
            if (circuit == null) {
                continue;
            }

            if (circuit.getGroupID().equals(groupID)) {
                return circuit;
            }
        }

        return null;
    }

    public int getScheduleNumber(String name) {
        int scheduleNum;

        scheduleNum = Integer.parseInt(name.substring(CONTROLLER_SCHEDULE.length()));

        if (scheduleNum < 1 || scheduleNum > NUM_SCHEDULES) {
            return 0;
        }

        return scheduleNum;
    }

    public Unit<Temperature> getUOM() {
        return (this.uom) ? SIUnits.CELSIUS : ImperialUnits.FAHRENHEIT;
    }

    // public QuantityType<Temperature> getWaterTemp() {

    public State getWaterTemp() {
        PentairControllerStatus status = this.controllerStatusCache.getLastKnownValue();

        if (status == null) {
            return UnDefType.UNDEF;
        }

        return new QuantityType<Temperature>(status.poolTemp, getUOM());
    }

    public @Nullable PentairControllerSchedule getScheduleByGroupID(String groupid) {
        int scheduleNumber = getScheduleNumber(groupid);
        if (scheduleNumber == 0) {
            return null;
        }

        PentairControllerSchedule schedule = schedulesCache[scheduleNumber - 1].getLastKnownValue();

        return schedule;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String groupId = channelUID.getGroupId();

        if (groupId == null) {
            return;
        }

        if (command instanceof RefreshType) {
            logger.debug("handleCommand (refresh): {}", channelUID.getId());

            switch (channelUID.getIdWithoutGroup()) {
                case CONTROLLER_CIRCUITSWITCH:
                case CONTROLLER_TEMPERATURE:
                case CONTROLLER_AIRTEMPERATURE:
                case CONTROLLER_SOLARTEMPERATURE:
                case CONTROLLER_UOM:
                case CONTROLLER_SERVICEMODE:
                case CONTROLLER_SOLARON:
                case CONTROLLER_HEATERON:
                case CONTROLLER_HEATERDELAY: {
                    PentairControllerStatus status = controllerStatusCache.getValue(() -> {
                        requestControllerStatus();
                    });
                    if (status != null) {
                        refreshControllerStatusChannels(channelUID, status);
                    }
                    break;
                }
                case CONTROLLER_LIGHTMODE: {
                    // Unable to find command update the lightMode from the controller, so setting to last set value
                    if (lightMode != null) {
                        updateState(channelUID, new StringType(lightMode.name()));
                    }
                    break;
                }
                case CONTROLLER_SETPOINT:
                case CONTROLLER_HEATMODE: {
                    PentairHeatStatus status = heatStatusCache.getValue(() -> {
                        requestHeatStatus();
                    });
                    if (status != null) {
                        refreshHeatStatusChannels(channelUID, status);
                    }
                    break;
                }
                case CONTROLLER_SCHEDULESTRING:
                case CONTROLLER_SCHEDULETYPE:
                case CONTROLLER_SCHEDULECIRCUIT:
                case CONTROLLER_SCHEDULEDAYS:
                case CONTROLLER_SCHEDULESTART:
                case CONTROLLER_SCHEDULEEND: {
                    int index = getScheduleNumber(groupId) - 1;

                    if (index < 0 || index >= NUM_SCHEDULES) {
                        return;
                    }
                    PentairControllerSchedule schedule = schedulesCache[index].getValue(() -> {
                        requestSchedule(index + 1);
                    });
                    if (schedule != null) {
                        refreshScheduleChannels(channelUID, schedule);
                    }
                    break;
                }
                case CONTROLLER_CIRCUITNAME:
                case CONTROLLER_CIRCUITFUNCTION: {
                    int circuitNumber = PentairControllerCircuit.getCircuitNumberByGroupID(groupId);

                    if (circuitNumber <= 0 || circuitNumber > NUM_CIRCUITS) {
                        return;
                    }
                    PentairControllerCircuit circuit = circuitsCache[circuitNumber - 1].getValue(() -> {
                        requestCircuitNameFunction(circuitNumber);
                    });
                    if (circuit != null) {
                        refreshCircuitChannels(channelUID, circuit);
                    }
                    break;
                }
            }

            return;
        }

        logger.debug("handleCommand: {}", channelUID.getId());

        switch (channelUID.getIdWithoutGroup()) {
            case CONTROLLER_CIRCUITSWITCH: {
                if (!(command instanceof OnOffType)) {
                    logger.trace("Command is not OnOffType");
                    break;
                }

                int circuitNum = PentairControllerCircuit.getCircuitNumberByGroupID(groupId);
                boolean state = command == OnOffType.ON;

                circuitSwitch(circuitNum, state);
                break;
            }
            case CONTROLLER_LIGHTMODE: {
                if (!(command instanceof StringType)) {
                    break;
                }
                String str = command.toString();
                LightMode lightMode;

                try {
                    lightMode = PentairControllerConstants.LightMode.valueOf(str);
                    setLightMode(lightMode);
                } catch (IllegalArgumentException e) {
                    logger.debug("Invalid light mode: {}", str);
                }
                break;
            }
            case CONTROLLER_SCHEDULESTRING: {
                if (!(command instanceof StringType)) {
                    break;
                }
                PentairControllerSchedule schedule = getScheduleByGroupID(groupId);

                if (schedule == null) {
                    break;
                }
                String str = command.toString();

                if (!schedule.fromString(str)) {
                    logger.debug("schedule invalid format: {}", str);
                }
                break;
            }
            case CONTROLLER_SCHEDULETYPE: {
                if (!(command instanceof StringType)) {
                    break;
                }
                PentairControllerSchedule schedule = getScheduleByGroupID(groupId);

                if (schedule == null) {
                    break;
                }
                String str = command.toString();
                // In order to prevent accidental programming of schedules by an inadvertent update, make sure the same
                // value is written twice to this field within 5s. Only then will the schedule update command be
                // sent to the controller.
                boolean bUpdate = (str.equals(schedule.getScheduleTypeStr())
                        && ((System.currentTimeMillis() - lastScheduleTypeWrite) < 5000) && schedule.isDirty());
                if (!schedule.setScheduleType(str)) {
                    return;
                }
                lastScheduleTypeWrite = System.currentTimeMillis();
                if (bUpdate) {
                    saveSchedule(schedule);
                    lastScheduleTypeWrite = 0;
                    refreshScheduleChannels(channelUID, schedule);
                }
                break;
            }
            case CONTROLLER_SCHEDULESTART: {
                if (!(command instanceof Number)) {
                    break;
                }

                PentairControllerSchedule schedule = getScheduleByGroupID(groupId);

                if (schedule == null) {
                    break;
                }
                int start = ((Number) command).intValue();
                schedule.setScheduleStart(start);
                break;
            }
            case CONTROLLER_SCHEDULEEND: {
                if (!(command instanceof Number)) {
                    break;
                }
                PentairControllerSchedule schedule = getScheduleByGroupID(groupId);
                if (schedule == null) {
                    break;
                }
                int end = ((Number) command).intValue();
                schedule.setScheduleEnd(end);
                break;
            }
            case CONTROLLER_SCHEDULECIRCUIT: {
                if (!(command instanceof Number)) {
                    break;
                }
                PentairControllerSchedule schedule = getScheduleByGroupID(groupId);
                if (schedule == null) {
                    break;
                }
                int circuit = ((Number) command).intValue();
                schedule.setScheduleCircuit(circuit);
                break;
            }
            case CONTROLLER_SCHEDULEDAYS: {
                if (!(command instanceof StringType)) {
                    break;
                }
                PentairControllerSchedule schedule = getScheduleByGroupID(groupId);
                if (schedule == null) {
                    break;
                }
                String days = command.toString();
                schedule.setDays(days);
                break;
            }
            case CONTROLLER_SETPOINT: {
                if (!(command instanceof QuantityType<?>)) {
                    break;
                }
                QuantityType<?> qt = (QuantityType<?>) command;
                switch (groupId) {
                    case CONTROLLER_SPAHEAT:
                        sendSetPoint(false, qt);
                        break;
                    case CONTROLLER_POOLHEAT:
                        sendSetPoint(true, qt);
                        break;
                }
                break;
            }
            case CONTROLLER_HEATERDELAY: {
                if (!(command instanceof OnOffType)) {
                    break;
                }
                if (command != OnOffType.OFF) { // Delay can only be cancelled
                    break;
                }
                cancelDelay();
            }
        }
    }

    /* Commands to send to Controller */

    /**
     * Method to turn on/off a circuit in response to a command from the framework
     *
     * @param circuit circuit number
     * @param state
     */
    public boolean circuitSwitch(int circuit, boolean state) {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x86,
                (byte) 0x02, (byte) circuit, (byte) ((state) ? 1 : 0) };

        logger.debug("circuit Switch: {}, {}", circuit, state);

        if (!writePacket(packet, 0x01, 1)) {
            logger.trace("circuitSwitch: Timeout");

            return false;
        }

        return true;
    }

    /**
     * Method to request controller status
     * Note the controller regularly sends out status, so this rarely needs to be called
     */
    public boolean requestStatus() { // A5 01 10 20 C2 01 00
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xC2,
                (byte) 0x01, (byte) 0x00 };

        logger.debug("Request controller status");
        if (!writePacket(packet, 0x02, 1)) {
            logger.trace("requestStatus timeout");

            return false;
        }

        return true;
    }

    /**
     * Method to request clock
     */
    public boolean requestClockSettings() { // A5 01 10 20 C5 01 00
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xC5,
                (byte) 0x01, (byte) 0x00 };

        logger.debug("Request clock settings");
        if (!writePacket(packet, 0x05, 1)) {
            logger.trace("requestClockSetting: Timeout");

            return false;
        }

        return true;
    }

    public void requestControllerStatus() { // A5 01 10 20 02 01 00
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x02,
                (byte) 0x01, (byte) 0x00 };

        logger.debug("Request controller status");

        if (!writePacket(packet, 0x02, 1)) {
            logger.trace("requestControllerStatus: Timeout");
        }
    }

    public void requestLightGroups() {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xE7,
                (byte) 0x01, (byte) 0x00 };

        logger.debug("request Light Groups");

        if (!writePacket(packet, 0x27, 1)) {
            logger.trace("requestLightGroups: Timeout");
        }
    }

    public void setLightMode(int mode) {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x60,
                (byte) 0x02, (byte) mode, (byte) 0x00 };

        logger.debug("setLightMode: {}", mode);

        if (!writePacket(packet, 0x01, 1)) {
            logger.trace("setLightMode: Timeout");
        }
    }

    public void setLightMode(PentairControllerConstants.LightMode lightMode) {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x60,
                (byte) 0x02, (byte) lightMode.getModeNumber(), (byte) 0x00 };

        logger.debug("setLightMode: {} ({})", lightMode.name(), lightMode.getModeNumber());

        if (!writePacket(packet, 0x01, 1)) {
            logger.trace("setLightMode: Timeout");
        }
    }

    public void requestValves() {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xDD,
                (byte) 0x01, (byte) 0x00 };

        logger.debug("requestValves");

        if (!writePacket(packet, 29, 1)) {
            logger.trace("requestValves: Timeout");
        }
    }

    public boolean requestCircuitNameFunction(int circuit) {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xCB,
                (byte) 0x01, (byte) circuit };

        logger.debug("requestCircuitNameFunction: {}", circuit);

        if (!writePacket(packet, 0x0B, 1)) {
            logger.trace("requestCircuitNameFunction: Timeout");

            return false;
        }
        return true;
    }

    public boolean requestSchedule(int num) {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xD1,
                (byte) 0x01, (byte) num };

        logger.debug("requestSchedule: {}", num);

        if (!writePacket(packet, 0x11, 1)) {
            logger.trace("requestSchedule: Timeout");

            return false;
        }

        return true;
    }

    /**
     * Method to update the schedule to the controller
     *
     * @param p
     */
    public boolean saveSchedule(PentairControllerSchedule schedule) {
        PentairPacket p;

        p = schedule.getWritePacket(id, preambleByte);
        if (p == null) {
            logger.debug("Schedule {} type is unknown.", id);
            return false;
        }

        logger.debug("saveSchedule: {}", p.toString());
        schedule.setDirty(false);

        if (!writePacket(p, 0x01, 1)) {
            logger.trace("saveSchedule: Timeout");

            return false;
        }

        return true;
    }

    public boolean requestSWVersion() {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xFD,
                (byte) 0x01, (byte) 0x00 };

        logger.debug("requestSWVersion");

        if (!writePacket(packet, 0xFC, 1)) {
            logger.trace("requestSWVersion: Timeout");
            return false;
        }

        String version = String.format("%d.%d", majorrev, minorrev);

        Map<String, String> editProperties = editProperties();
        editProperties.put(CONTROLLER_PROPERTYFWVERSION, version);
        updateProperties(editProperties);

        return true;
    }

    public boolean cancelDelay() {
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x83,
                (byte) 0x01, (byte) 0x00 };

        logger.debug("cancelDelay");

        if (!writePacket(packet, 1, 1)) {
            logger.trace("cancelDelay: Timeout");
            return false;
        }

        return true;
    }

    /**
     * Method to set clock
     *
     */
    public void sendClockSettings(int hour, int min, int dow, int day, int month, int year) { // A5 01 10 20 85 08 0D 2A
                                                                                              // 02 1D 04 11 00 00

        logger.debug("Send Clock Settings {}:{} {} {}/{}/{}", hour, min, dow, day, month, year);

        if (hour > 23) {
            throw new IllegalArgumentException("hour not in range [0..23]: " + hour);
        }
        if (min > 59) {
            throw new IllegalArgumentException("hour not in range [0..59]: " + min);
        }
        if (dow > 7 || dow < 1) {
            throw new IllegalArgumentException("hour not in range [1..7]: " + dow);
        }
        if (day > 31 || day < 1) {
            throw new IllegalArgumentException("hour not in range [1..31]: " + day);
        }
        if (month > 12 || month < 1) {
            throw new IllegalArgumentException("hour not in range [1..12]: " + month);
        }
        if (year > 99) {
            throw new IllegalArgumentException("hour not in range [0..99]: " + year);
        }

        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x85,
                (byte) 0x08, (byte) hour, (byte) min, (byte) dow, (byte) day, (byte) month, (byte) year, (byte) 0x00,
                (byte) 0x00 };

        writePacket(packet);
    }

    public void requestHeatStatus() { // A5 01 10 20 C8 01 00
        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0xC8,
                (byte) 0x01, (byte) 0 };

        logger.debug("request heat settings");

        if (!writePacket(packet, 0x08, 1)) {
            logger.trace("requestHeat: Timeout");
        }
    }

    /**
     * Method to set heat point for pool (true) of spa (false)
     *
     * @param Pool pool=true, spa=false
     * @param temp
     */
    public void sendSetPoint(boolean pool, QuantityType<?> temp) {
        // [16,34,136,4,POOL HEAT Temp,SPA HEAT Temp,Heat Mode,0,2,56]
        // [165, preambleByte, 16, 34, 136, 4, currentHeat.poolSetPoint, parseInt(req.params.temp), updateHeatMode, 0]
        int value;

        QuantityType<?> t;
        if (temp.getUnit() == SIUnits.CELSIUS) {
            value = Math.max(10, Math.min(temp.intValue(), 41));
            t = QuantityType.valueOf(value, SIUnits.CELSIUS);
        } else if (temp.getUnit() == ImperialUnits.FAHRENHEIT) {
            value = Math.max(50, Math.min(temp.intValue(), 105));
            t = QuantityType.valueOf(value, ImperialUnits.FAHRENHEIT);
        } else {
            logger.debug("sendSetPoint QuantityType not a temperature");
            return;
        }

        t = (getUOM() == SIUnits.CELSIUS) ? t.toUnit(SIUnits.CELSIUS) : t.toUnit(ImperialUnits.FAHRENHEIT);
        if (t == null) {
            return;
        }

        PentairHeatStatus heatStatus = heatStatusCache.getLastKnownValue();
        if (heatStatus == null) {
            return;
        }

        int spaset = (!pool) ? t.intValue() : heatStatus.spaSetPoint;
        int poolset = (pool) ? t.intValue() : heatStatus.poolSetPoint;
        Objects.requireNonNull(heatStatus.spaHeatMode);
        Objects.requireNonNull(heatStatus.poolHeatMode);
        int heatmode = (heatStatus.spaHeatMode.getCode() << 2) | heatStatus.poolHeatMode.getCode();

        byte[] packet = { (byte) 0xA5, (byte) preambleByte, (byte) id, (byte) 0x00 /* source */, (byte) 0x88,
                (byte) 0x04, (byte) poolset, (byte) spaset, (byte) heatmode, (byte) 0 };

        logger.debug("Set {} temperature: {}", (pool) ? "Pool" : "Spa", temp);

        if (!writePacket(packet, 0x01, 1)) {
            logger.trace("sendSetPoint: Timeout");
        }
    }

    @Override
    public void processPacketFrom(PentairPacket p) {
        switch (p.getAction()) {
            case 1: // Ack
                logger.debug("Ack command from device: {} - {}", p.getByte(0), p);
                break;
            case 2: // Controller Status
                if (p.getLength() != 29) {
                    logger.debug("Expected length of 29: {}", p);
                    return;
                }

                logger.trace("Controller Status: {}", p);

                preambleByte = p.getPreambleByte(); // Adjust what byte is used for preamble
                if (waitStatusForOnline) {
                    finishOnline();
                }

                PentairControllerStatus currentControllerStatus = controllerStatusCache.getLastKnownValue();
                PentairControllerStatus newControllerStatus = new PentairControllerStatus();
                newControllerStatus.parsePacket(p);

                // always update the cached value to reset the expire timer
                controllerStatusCache.putValue(newControllerStatus);

                // Refresh initially when currentControllerStatus is not set - or when status has changed
                if (currentControllerStatus == null || !newControllerStatus.equals(currentControllerStatus)) {
                    refreshControllerStatusChannels(null, newControllerStatus);
                }

                break;
            case 4: // Pump control panel on/off - handled in intelliflo controller
                // Controller sends packet often to keep control of the motor
                logger.debug("Pump control panel on/of {}: {}", p.getDest(), p.getByte(0));

                break;
            case 5: // Current Clock - A5 01 0F 10 05 08 0E 09 02 1D 04 11 00 00 - H M DOW D M YY YY ??
                int hour = p.getByte(0);
                int minute = p.getByte(1);
                int dow = p.getByte(2);
                int day = p.getByte(3);
                int month = p.getByte(4);
                int year = p.getByte(5);

                logger.debug("System Clock: {}:{} {} {}/{}/{}", hour, minute, dow, day, month, year);

                break;
            case 6: // Set run mode
                // No action - have not verified these commands, here for documentation purposes and future enhancement
                logger.debug("Set run mode {}: {}", p.getDest(), p.getByte(0));

                break;
            case 7: // Pump Status - handled in IntelliFlo handler
                // No action - have not verified these commands, here for documentation purposes and future enhancement
                logger.debug("Pump request status (unseen): {}", p);
                break;
            case 8: // Heat Status - A5 01 0F 10 08 0D 4B 4B 4D 55 5E 07 00 00 58 00 00 00 
                if (p.getLength() != 0x0D) {
                    logger.debug("Expected length of 13: {}", p);
                    return;
                }

                PentairHeatStatus heatStatus = new PentairHeatStatus(p);
                heatStatusCache.putValue(heatStatus);

                refreshHeatStatusChannels(null, heatStatus);

                logger.debug("Heat status: {}, {}, {}", p, heatStatus.poolSetPoint, heatStatus.spaSetPoint);
                break;
            case 10: // Custom Names
                logger.debug("Get Custom Names (unseen): {}", p);
                break;
            case 11: // Circuit Names
                int index;

                index = p.getByte(0);
                index--; // zero index
                if (index < 0 || index >= NUM_CIRCUITS) {
                    break;
                }
                PentairControllerCircuit circuit = new PentairControllerCircuit(index + 1, CIRCUIT_GROUPS[index]);
                circuit.setName(p.getByte(2));
                circuit.setFunction(p.getByte(1));
                refreshCircuitChannels(null, circuit);
                circuitsCache[index].putValue(circuit);
                logger.debug("Circuit Names - Circuit: {}, Function: {}, Name: {}", circuit.id,
                        circuit.circuitFunction.getFriendlyName(), circuit.circuitName.getFriendlyName());
                break;
            case 17: // schedule - A5 1E 0F 10 11 07 01 06 0B 00 0F 00 7F
                int id;

                id = p.getByte(PentairControllerSchedule.ID);
                if (id < 1 || id > NUM_SCHEDULES) {
                    break;
                }

                PentairControllerSchedule schedule = new PentairControllerSchedule();
                schedule.parsePacket(p);
                String groupID = schedule.getGroupID();
                logger.debug("Controller schedule group: {}", groupID);
                refreshScheduleChannels(null, schedule);
                schedulesCache[id - 1].putValue(schedule);
                logger.debug(
                        "Controller Schedule - ID: {}, Type: {}, Circuit: {}, Start Time: {}:{}, End Time: {}:{}, Days: {}",
                        schedule.id, schedule.type, schedule.circuit, schedule.start / 60, schedule.start % 60,
                        schedule.end / 60, schedule.end % 60, schedule.days);
                break;
            case 18: // IntelliChem
                logger.debug("IntelliChem status: {}", p);
                break;
            case 25: // Intellichlor status
                logger.debug("Intellichlor status: {}", p);
                break;
            case 27: // Pump config (Extended)
                logger.debug("Pump Config: {}", p);
                break;
            case 29: // Valves
                logger.debug("Values: {}", p);
                break;
            case 30: // High speed circuits
                logger.debug("High speed circuits: {}", p);
                break;
            case 32: // spa-side is4/is10 remote
            case 33: // spa-side quicktouch remotes
                logger.debug("Spa-side remotes: {}", p);
                break;
            case 34: // Solar/Heat Pump status
                logger.debug("Solar/Heat Pump status: {}", p);
                break;
            case 35: // Delay status
                logger.debug("Delay status: {}", p);
                break;
            case 39: // Light Groups/Positions
                logger.debug("Light Groups/Positions; {}", p);
                break;
            case 40: // Settings? heat mode
                logger.debug("Settings?: {}", p);
                break;
            case 96: // set intellebrite colors
                logger.debug("Set intellebrite colors: {}", p);
                break;
            case 134: // Set Curcuit On/Off
                logger.debug("Set Circuit Function On/Off (unseen): {}", p);
                break;
            case 210: // Get Intellichem status
                logger.debug("Get IntelliChem status: {}", p);
                break;
            case 252: // Status - A5 1E 0F 10 FC 11 00 02 0A 00 00 01 0A 00 00 00 00 00 00 00 00 00 00
                majorrev = p.getByte(1);
                minorrev = p.getByte(2);
                logger.debug("SW Version - {}:{}", majorrev, minorrev);
                break;
            default:
                logger.debug("Not Implemented {}: {}", p.getAction(), p);
                break;
        }
    }

    /**
     * Helper function to update channel.
     */
    public void updateChannel(@Nullable String group, String channel, boolean value) {
        Objects.nonNull(group);
        updateState(group + "#" + channel, (value) ? OnOffType.ON : OnOffType.OFF);
    }

    public void updateChannelTemp(@Nullable String group, String channel, int value) {
        Objects.nonNull(group);
        if (value != 999) {
            updateState(group + "#" + channel, new QuantityType<Temperature>(value, getUOM()));
        } else {
            updateState(group + "#" + channel, UnDefType.UNDEF);
        }
    }

    public void updateChannel(@Nullable String group, String channel, int value) {
        Objects.nonNull(group);
        updateState(group + "#" + channel, new DecimalType(value));
    }

    public void updateChannel(@Nullable String group, String channel, @Nullable String value) {
        Objects.nonNull(group);
        if (value == null) {
            updateState(group + "#" + channel, UnDefType.NULL);
        } else {
            updateState(group + "#" + channel, new StringType(value));
        }
    }

    public void refreshScheduleChannels(@Nullable ChannelUID fullChannelUID, PentairControllerSchedule schedule) {
        String groupID = "";
        String channelID = "";

        if (fullChannelUID != null) {
            groupID = Objects.requireNonNull(fullChannelUID.getGroupId());
            channelID = fullChannelUID.getIdWithoutGroup();

            if (!schedule.getGroupID().equals(groupID)) {
                // this should never happen
                logger.debug("refreshScheduleChannels: schedule group is not aligned with channel");
            }
        }
        if (fullChannelUID == null || channelID.equals(CONTROLLER_SCHEDULESTRING)) {
            updateChannel(schedule.getGroupID(), CONTROLLER_SCHEDULESTRING, schedule.toString());
        }
        if (fullChannelUID == null || channelID.equals(CONTROLLER_SCHEDULETYPE)) {
            logger.debug("groupID: {}, scheduleType: {}", schedule.getGroupID(), schedule.getScheduleTypeStr());
            updateChannel(schedule.getGroupID(), CONTROLLER_SCHEDULETYPE, schedule.getScheduleTypeStr());
        }
        if (fullChannelUID == null || channelID.equals(CONTROLLER_SCHEDULECIRCUIT)) {
            updateChannel(schedule.getGroupID(), CONTROLLER_SCHEDULECIRCUIT, schedule.circuit);
        }
        if (fullChannelUID == null || channelID.equals(CONTROLLER_SCHEDULESTART)) {
            updateChannel(schedule.getGroupID(), CONTROLLER_SCHEDULESTART, schedule.start);
        }
        if (fullChannelUID == null || channelID.equals(CONTROLLER_SCHEDULEEND)) {
            updateChannel(schedule.getGroupID(), CONTROLLER_SCHEDULEEND, schedule.end);
        }
        if (fullChannelUID == null || channelID.equals(CONTROLLER_SCHEDULEDAYS)) {
            updateChannel(schedule.getGroupID(), CONTROLLER_SCHEDULEDAYS, schedule.getDays());
        }
        logger.debug(
                "Controller Schedule - ID: {}, Type: {}, Circuit: {}, Start Time: {}:{}, End Time: {}:{}, Days: {}",
                schedule.id, schedule.type, schedule.circuit, schedule.start / 60, schedule.start % 60,
                schedule.end / 60, schedule.end % 60, schedule.getDays());
    }

    public void refreshControllerStatusChannels(@Nullable ChannelUID fullChannelUID,
            PentairControllerStatus controllerStatus) {
        String groupID = "";
        String channelID = "";

        boolean updateAll = fullChannelUID == null;

        if (!updateAll) {
            groupID = Objects.requireNonNull(fullChannelUID.getGroupId());
            channelID = fullChannelUID.getIdWithoutGroup();
        }

        // update circuit states
        if (channelID.equals(CONTROLLER_CIRCUITSWITCH)) {
            PentairControllerCircuit circuit;

            circuit = getCircuitByGroupID(groupID);
            if (circuit == null) {
                return;
            }

            int circuitNum = circuit.id - 1;
            updateChannel(groupID, CONTROLLER_CIRCUITSWITCH, controllerStatus.circuits[circuitNum]);
        } else if (updateAll) {
            for (int i = 0; i < NUM_CIRCUITS; i++) {
                String circuitGroupID;

                circuitGroupID = PentairControllerCircuit.getCircuitGroupIDByNumber(i + 1);

                if (circuitGroupID != null) {
                    updateChannel(circuitGroupID, CONTROLLER_CIRCUITSWITCH, controllerStatus.circuits[i]);
                }
            }
        }

        if (updateAll || channelID.equals(CONTROLLER_UOM)) {
            updateChannel(CONTROLLER_STATUS, CONTROLLER_UOM, (controllerStatus.uom) ? "CELCIUS" : "FAHRENHEIT");
            this.uom = controllerStatus.uom;
        }

        if (updateAll || channelID.equals(CONTROLLER_TEMPERATURE)) {
            updateChannelTemp(CONTROLLER_POOLHEAT, CONTROLLER_TEMPERATURE,
                    (controllerStatus.pool) ? controllerStatus.poolTemp : 999);
            updateChannelTemp(CONTROLLER_SPAHEAT, CONTROLLER_TEMPERATURE,
                    (controllerStatus.spa) ? controllerStatus.spaTemp : 999);
        }

        if (updateAll || channelID.equals(CONTROLLER_AIRTEMPERATURE)) {
            updateChannelTemp(CONTROLLER_STATUS, CONTROLLER_AIRTEMPERATURE, controllerStatus.airTemp);
        }

        if (updateAll || channelID.equals(CONTROLLER_SOLARTEMPERATURE)) {
            updateChannelTemp(CONTROLLER_STATUS, CONTROLLER_SOLARTEMPERATURE, controllerStatus.solarTemp);
        }

        if (updateAll || channelID.equals(CONTROLLER_SERVICEMODE)) {
            updateChannel(CONTROLLER_STATUS, CONTROLLER_SERVICEMODE, controllerStatus.serviceMode);
            serviceMode = controllerStatus.serviceMode;
        }

        if (updateAll || channelID.equals(CONTROLLER_HEATERON)) {
            updateChannel(CONTROLLER_STATUS, CONTROLLER_HEATERON, controllerStatus.heaterOn);
        }

        if (updateAll || channelID.equals(CONTROLLER_SOLARON)) {
            updateChannel(CONTROLLER_STATUS, CONTROLLER_SOLARON, controllerStatus.solarOn);
        }

        if (updateAll || channelID.equals(CONTROLLER_HEATERDELAY)) {
            updateChannel(CONTROLLER_STATUS, CONTROLLER_HEATERDELAY, controllerStatus.heaterDelay);
        }
    }

    public void refreshHeatStatusChannels(@Nullable ChannelUID fullChannelUID, PentairHeatStatus heatStatus) {
        String groupID = "";
        String channelID = "";
        boolean updateAll = fullChannelUID == null;

        if (!updateAll) {
            groupID = Objects.requireNonNull(fullChannelUID.getGroupId());
            channelID = fullChannelUID.getIdWithoutGroup();
        }

        if (updateAll || groupID.equals(CONTROLLER_POOLHEAT)) {
            if (updateAll || channelID.equals(CONTROLLER_SETPOINT)) {
                updateChannelTemp(CONTROLLER_POOLHEAT, CONTROLLER_SETPOINT, heatStatus.poolSetPoint);
            }

            if ((updateAll || channelID.equals(CONTROLLER_HEATMODE)) && heatStatus.poolHeatMode != null) {
                updateChannel(CONTROLLER_POOLHEAT, CONTROLLER_HEATMODE, heatStatus.poolHeatMode.name());
            }
        }

        if (updateAll || groupID.equals(CONTROLLER_SPAHEAT)) {
            if (updateAll || channelID.equals(CONTROLLER_SETPOINT)) {
                updateChannelTemp(CONTROLLER_SPAHEAT, CONTROLLER_SETPOINT, heatStatus.spaSetPoint);
            }

            if ((updateAll || channelID.equals(CONTROLLER_HEATMODE)) && heatStatus.spaHeatMode != null) {
                updateChannel(CONTROLLER_SPAHEAT, CONTROLLER_HEATMODE, heatStatus.spaHeatMode.name());
            }
        }
    }

    public void refreshCircuitChannels(@Nullable ChannelUID fullChannelUID, PentairControllerCircuit circuit) {
        String groupID = "";
        String channelID = "";
        boolean updateAll = fullChannelUID == null;

        if (!updateAll) {
            groupID = Objects.requireNonNull(fullChannelUID.getGroupId());
            channelID = fullChannelUID.getIdWithoutGroup();

            if (!circuit.getGroupID().equals(groupID)) {
                // this should never happen
                logger.debug("refreshCircuitChannels: circuit group is not aligned with channel");
            }
        }

        if (updateAll || channelID.equals(CONTROLLER_CIRCUITNAME)) {
            updateChannel(circuit.getGroupID(), CONTROLLER_CIRCUITNAME, circuit.circuitName.getFriendlyName());
        }

        if (updateAll || channelID.equals(CONTROLLER_CIRCUITFUNCTION)) {
            updateChannel(circuit.getGroupID(), CONTROLLER_CIRCUITFUNCTION, circuit.circuitFunction.getFriendlyName());
        }
    }

    public boolean getServiceMode() {
        return serviceMode;
    }
}
