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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link RachioBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class RachioBindingConstants {
    public static final String BINDING_ID = "rachio";
    public static final String BINDING_VENDOR = "Rachio";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_CLOUD = new ThingTypeUID(BINDING_ID, "cloud");
    public static final ThingTypeUID THING_TYPE_CONTROLLER = new ThingTypeUID(BINDING_ID, "controller");
    public static final ThingTypeUID THING_TYPE_ZONE = new ThingTypeUID(BINDING_ID, "zone");
    public static final ThingTypeUID THING_TYPE_BASE_STATION = new ThingTypeUID(BINDING_ID, "base-station");
    public static final ThingTypeUID THING_TYPE_VALVE = new ThingTypeUID(BINDING_ID, "valve");

    // Config opntions (e.g. rachio.cfg)
    public static final String PARAM_APIKEY = "apikey";
    public static final String PARAM_POLLING_INTERVAL = "pollingInterval";
    public static final String PARAM_DEF_RUNTIME = "defaultRuntime";
    public static final String PARAM_CALLBACK_URL = "callbackUrl";
    public static final String PARAM_CLEAR_CALLBACK = "clearAllCallbacks";

    public static final String PARAM_ID = "id";

    // List of non-standard Properties
    public static final String PROPERTY_IP_ADDRESS = "ipAddress";
    public static final String PROPERTY_IP_MASK = "ipMask";
    public static final String PROPERTY_IP_GW = "ipGateway";
    public static final String PROPERTY_IP_DNS1 = "ipDNS1";
    public static final String PROPERTY_IP_DNS2 = "ipDNS2";
    public static final String PROPERTY_WIFI_RSSI = "wifiSignal";
    public static final String PROPERTY_APIKEY = "apikey";
    public static final String PROPERTY_NAME = "name";
    public static final String PROPERTY_MODEL = "model";
    public static final String PROPERTY_EXT_ID = "externalId";
    public static final String PROPERTY_DEV_ID = "deviceId";
    public static final String PROPERTY_DEV_LOCATION = "location";
    public static final String PROPERTY_ZONE_ID = "zoneId";
    public static final String PROPERTY_PERSON_ID = "personId";
    public static final String PROPERTY_PERSON_USER = "accountUserName";
    public static final String PROPERTY_PERSON_NAME = "accountFullName";
    public static final String PROPERTY_PERSON_EMAIL = "accountEMail";
    public static final String PROPERTY_VALVE_COLOR = "color";
    public static final String PROPERTY_VALVE_FW = "firmwareVersion";

    // List of all Device Channel ids
    public static final String CHANNEL_DEVICE_NAME = "name";
    public static final String CHANNEL_DEVICE_ACTIVE = "active";
    public static final String CHANNEL_DEVICE_RUNNING_ZONE_NUMBER = "runningZoneNumber";
    public static final String CHANNEL_DEVICE_RUNNING_ZONE_NAME = "runningZoneName";
    public static final String CHANNEL_DEVICE_RUN = "run";
    public static final String CHANNEL_DEVICE_PLAYER = "player";
    public static final String CHANNEL_DEVICE_RUN_ZONES = "runZones";
    public static final String CHANNEL_DEVICE_RUN_TIME = "runTime";
    public static final String CHANNEL_DEVICE_RAIN_DELAY = "rainDelay";
    public static final String CHANNEL_DEVICE_RAIN_DELAY_UNTIL = "rainDelayUntil";
    public static final String CHANNEL_DEVICE_RAIN_SENSOR_TRIPPED = "rainSensorTripped";

    public static final String CHANNEL_SCHED_NAME = "scheduleName";
    public static final String CHANNEL_SCHED_INFO = "scheduleInfo";
    public static final String CHANNEL_SCHED_START = "scheduleStart";
    public static final String CHANNEL_SCHED_END = "scheduleEnd";

    // List of all Zone Channel ids
    public static final String CHANNEL_ZONE_NAME = "name";
    public static final String CHANNEL_ZONE_NUMBER = "number";
    public static final String CHANNEL_ZONE_ENABLED = "enabled";
    public static final String CHANNEL_ZONE_RUN = "run";
    public static final String CHANNEL_ZONE_RUN_TIME = "runTime";
    public static final String CHANNEL_ZONE_RUN_TOTAL = "runTotal";
    public static final String CHANNEL_ZONE_IMAGEURL = "imageUrl";
    public static final String CHANNEL_ZONE_IMAGE = "image";
    public static final String CHANNEL_ZONE_LAST_WATERED_DATE = "lastWateredDate";
    public static final String CHANNEL_ZONE_AVAILABLE_WATER = "availableWater";
    public static final String CHANNEL_ZONE_DEPTH_OF_WATER = "depthOfWater";
    public static final String CHANNEL_ZONE_DEPLETION_LEVEL = "depletionLevel";
    public static final String CHANNEL_ZONE_SATURATION_DEPTH = "saturationDepth";

    public static final String CHANNEL_VALVE_NAME = "name";
    public static final String CHANNEL_VALVE_IMAGEURL = "imageUrl";
    public static final String CHANNEL_VALVE_IMAGE = "image";
    public static final String CHANNEL_VALVE_LOW_BATTERY = "image";
    public static final String CHANNEL_VALVE_RUN = "run";
    public static final String CHANNEL_VALVE_RUN_TIME = "runTime";
    public static final String CHANNEL_VALVE_DEFAULT_RUN_TIME = "defaultRunTime";

    public static final String CHANNEL_LAST_UPDATE = "lastUpdate";
    public static final String CHANNEL_LAST_EVENT = "lastEvent";
    public static final String CHANNEL_LAST_EVENTTS = "lastEventTime";

    // Default for config options / thing settings
    public static int DEFAULT_POLLING_INTERVAL_SEC = 120;
    public static int DEFAULT_ZONE_RUNTIME_SEC = 300;
    public static final int HTTP_TIMOUT_MS = 15000;

    // --------------- Rachio Cloud API

    // notification types
    // Webhook event types
    /*
     * id:5, type=DEVICE_STATUS
     * id:6, type=RAIN_DELAY
     * id:7, type=WEATHER_INTELLIGENCE
     * id:8, type=WATER_BUDGET
     * id:9, type=SCHEDULE_STATUS
     * id:10, type=ZONE_STATUS
     * id:11, type=RAIN_SENSOR_DETECTION
     * id:12, type=ZONE_DELTA
     * id:14, type=DELTA
     */
    public static final String WHE_DEVICE_STATUS = "5"; // "Device status event has occurred"
    public static final String WHE_RAIN_DELAY = "6"; // "A rain delay event has occurred"
    public static final String WEATHER_INTELLIGENCE = "7"; // A weather intelligence event has has occurred
    public static final String WHE_WATER_BUDGET = "8"; // A water budget event has occurred
    public static final String WHE_SCHEDULE_STATUS = "9";
    public static final String WHE_ZONE_STATUS = "10";
    public static final String WHE_RAIN_SENSOR_DETECTION = "11"; // physical rain sensor event has coccurred
    public static final String WHE_ZONE_DELTA = "12"; // A physical rain sensor event has occurred
    public static final String WHE_DELTA = "14"; // "An entity has been inserted, updated, or deleted"

    public static final String SERVLET_WEBHOOK_PATH = "/rachio/webhook";
    public static final String SERVLET_WEBHOOK_APPLICATION_JSON = "application/json";
    public static final String SERVLET_WEBHOOK_CHARSET = "utf-8";
    public static final String SERVLET_WEBHOOK_USER_AGENT = "Mozilla/5.0";

    public static final String SERVLET_IMAGE_PATH = "/rachio/images";
    public static final String SERVLET_IMAGE_MIME_TYPE = "image/png";
    public static final String SERVLET_IMAGE_URL_BASE = "https://prod-media-photo.rach.io/";

    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String RACHIO_JSON_RATE_LIMIT = "X-RateLimit-Limit";
    public static final String RACHIO_JSON_RATE_REMAINING = "X-RateLimit-Remaining";
    public static final String RACHIO_JSON_RATE_RESET = "X-RateLimit-Reset";
    public static final int RACHIO_RATE_LIMIT_WARNING = 200; // slow down polling
    public static final int RACHIO_RATE_LIMIT_CRITICAL = 100; // stop polling
    public static final int RACHIO_RATE_LIMIT_BLOCK = 20; // block api access
    public static final int RACHIO_RATE_SKIP_CALLS = 5;

    public static final String AWS_IPADDR_DOWNLOAD_URL = "https://ip-ranges.amazonaws.com/ip-ranges.json";
    public static final String AWS_IPADDR_REGION_FILTER = "us-";
}
