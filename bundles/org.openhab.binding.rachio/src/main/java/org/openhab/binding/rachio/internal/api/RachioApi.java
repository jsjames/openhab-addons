/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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

import static org.openhab.binding.rachio.internal.RachioBindingConstants.CONTENT_TYPE_JSON;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.rachio.internal.api.dto.RachioApiBaseStation;
import org.openhab.binding.rachio.internal.api.dto.RachioApiCurrentSchedule;
import org.openhab.binding.rachio.internal.api.dto.RachioApiDevice;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEvent;
import org.openhab.binding.rachio.internal.api.dto.RachioApiEventType;
import org.openhab.binding.rachio.internal.api.dto.RachioApiNotificationWebhook;
import org.openhab.binding.rachio.internal.api.dto.RachioApiNotificationWebhookEventType;
import org.openhab.binding.rachio.internal.api.dto.RachioApiPerson;
import org.openhab.binding.rachio.internal.api.dto.RachioApiScheduleRule;
import org.openhab.binding.rachio.internal.api.dto.RachioApiValve;
import org.openhab.binding.rachio.internal.api.dto.RachioApiWebhook;
import org.openhab.binding.rachio.internal.api.dto.RachioApiZone;
import org.openhab.binding.rachio.internal.api.dto.RachioApiZoneRun;
import org.openhab.binding.rachio.internal.utils.ArrayToMapDeserializer;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager;
import org.openhab.binding.rachio.internal.utils.ClientRateLimitManager.RateLimitThrottleException;
import org.openhab.core.library.types.RawType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link RachioApi} implements the interface to the Rachio cloud service (using http).
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class RachioApi {
    private final Logger logger = Objects.requireNonNull(LoggerFactory.getLogger(RachioApi.class));

    private record NotificationEventTypeId(String id) {
    }

    private record NotificationDeviceRef(RachioId.Device id) {
    }

    private record NotificationCreateBody(NotificationDeviceRef device, String externalId, String url,
            List<NotificationEventTypeId> eventTypes) {
    }

    private static final String URL_BASE = "https://api.rach.io/1/public/";
    private static final String URL_BASE2 = "https://cloud-rest.rach.io/";
    public static final String API_PHOTO_BASE = "https://prod-media-photo.rach.io/";

    private static final String URL_GET_PERSONID = URL_BASE + "person/info"; // obtain personId
    private static final String URL_GET_PERSON = URL_BASE + "person/%s"; // obtain personId
    private static final String URL_GET_DEVICE = URL_BASE + "device/%s"; // get device details, needs /<device id>
    private static final String URL_GET_DEVICE_CURRENT_SCHEDULE = URL_BASE + "device/%s/current_schedule";
    private static final String URL_GET_DEVICE_EVENT = URL_BASE + "device/%s/event";

    private static final String URL_PUT_DEVICE_ON = URL_BASE + "device/on";
    private static final String URL_PUT_DEVICE_OFF = URL_BASE + "device/off";
    private static final String URL_PUT_DEVICE_STOP = URL_BASE + "device/stop_water";
    private static final String URL_PUT_DEVICE_RAIN_DELAY = URL_BASE + "device/rain_delay";
    private static final String URL_PUT_DEVICE_PAUSE_RUN = URL_BASE + "device/pause_zone_run";
    private static final String URL_PUT_DEVICE_RESUME_RUN = URL_BASE + "device/resume_zone_run";

    private static final String URL_GET_NOTIFICATION_WEBHOOK_EVENT_TYPES = URL_BASE + "notification/webhook_event_type";
    private static final String URL_GET_NOTIFICATION_DEVICE_WEBHOOK = URL_BASE + "notification/%s/webhook";
    private static final String URL_GET_NOTIFICATION_WEBHOOK = URL_BASE + "notification/webhook/%s";
    private static final String URL_DEL_NOTIFICATION_WEBHOOK = URL_BASE + "notification/webhook/%s";
    private static final String URL_POST_NOTIFICATION_WEBHOOK = URL_BASE + "notification/webhook";

    private static final String URL_POST_CREATE_WEBHOOK = URL_BASE2 + "webhook/createWebhook";
    private static final String URL_GET_LIST_WEBHOOKS_DEVICE = URL_BASE2
            + "webhook/listWebhooks?resource_id.irrigation_controller_id=%s";
    private static final String URL_GET_LIST_WEBHOOKS_PROGRAM = URL_BASE2
            + "webhook/listWebhooks?resource_id.program_id=%s";
    private static final String URL_GET_LIST_WEBHOOKS_VALVE = URL_BASE2
            + "webhook/listWebhooks?resource_id.valve_id=%s";
    private static final String URL_DELETE_WEBHOOK = URL_BASE2 + "webhook/deleteWebhook/%s";
    private static final String URL_DELETE_WEBHOOK_DEVICE = URL_BASE2
            + "webhook/deleteAllWebhooks/resource_id.irrigation_controller_id=%s";
    private static final String URL_DELETE_WEBHOOK_PROGRAM = URL_BASE2
            + "webhook/deleteAllWebhooks/resource_id.program_id=%s";
    private static final String URL_DELETE_WEBHOOK_VALVE = URL_BASE2
            + "webhook/deleteAllWebhooks/resource_id.valve_id=%s";
    private static final String URL_GET_LIST_WEBHOOK_EVENT_TYPES = URL_BASE2 + "webhook/listWebhookEventTypes";

    private static final String URL_GET_ZONE = URL_BASE + "zone/%s";
    private static final String URL_PUT_ZONE_START = URL_BASE + "zone/start";
    private static final String URL_PUT_ZONE_MULTIPLE_START = URL_BASE + "zone/start_multiple";
    private static final String URL_PUT_ZONE_MOISTURE_LEVEL = URL_BASE + "zone/setMoistureLevel";
    private static final String URL_PUT_ZONE_MOISTURE_PERCENT = URL_BASE + "zone/setMoisturePercent";
    private static final String URL_PUT_ZONE_ENABLE = URL_BASE + "zone/enable";
    private static final String URL_PUT_ZONE_DISABLE = URL_BASE + "zone/disable";

    private static final String URL_GET_BASE_STATION = URL_BASE2 + "valve/getBaseStation/%s";
    private static final String URL_GET_BASE_STATIONS = URL_BASE2 + "valve/listBaseStations/%s";
    private static final String URL_GET_VALVE = URL_BASE2 + "valve/getValve/%s";
    private static final String URL_GET_VALVES = URL_BASE2 + "valve/listValves/%s";
    private static final String URL_PUT_VALVE_START_WATERING = URL_BASE2 + "valve/startWatering";
    private static final String URL_PUT_VALVE_STOP_WATERING = URL_BASE2 + "valve/stopWatering";
    private static final String URL_PUT_VALVE_DEFAULT_RUNTIME = URL_BASE2 + "valve/setDefaultRuntime";

    private static final String URL_PUT_SCHEDULE_SKIP_FORWARD = URL_BASE + "schedulerule/skip_forward_zone_run";

    public static final String RACHIO_JSON_RATE_LIMIT = "X-RateLimit-Limit";
    public static final String RACHIO_JSON_RATE_REMAINING = "X-RateLimit-Remaining";
    public static final String RACHIO_JSON_RATE_RESET = "X-RateLimit-Reset";

    private static final int API_TIMEOUT = 15;

    protected String apiKey = "";

    private ClientRateLimitManager rateLimitManager = new ClientRateLimitManager(5, Duration.ofMinutes(1));

    private final JsonDeserializer<@Nullable Date> dateDeserializer = (@Nullable JsonElement json,
            @Nullable Type typeOfT,
            @Nullable JsonDeserializationContext context) -> json == null ? null : new Date(json.getAsLong());

    private final JsonDeserializer<@Nullable Instant> instantDeserializer = (@Nullable JsonElement json,
            @Nullable Type typeOfT,
            @Nullable JsonDeserializationContext context) -> json == null ? null : Instant.parse(json.getAsString());

    // custom deserializers for API objects to handle missing fields and provide better error handling
    private final Gson gson = Objects.requireNonNull(new GsonBuilder() //
            .registerTypeAdapter(Date.class, dateDeserializer) //
            .registerTypeAdapter(Instant.class, instantDeserializer) //
            .registerTypeAdapterFactory(new RachioId.RachioIdTypeAdapterFactory()) //
            .registerTypeAdapter(RachioApiEvent.class, new RachioApiEvent.GsonAdapter()) //
            .registerTypeAdapter(RachioApiWebhook.class, new RachioApiWebhook.GsonAdapter()) //
            .registerTypeAdapter(new TypeToken<Map<RachioId.Device, RachioApiDevice>>() {
            }.getType(), ArrayToMapDeserializer.forArrayToMap(RachioApiDevice::id, RachioApiDevice.class)) //
            .registerTypeAdapter(new TypeToken<Map<RachioId.Zone, RachioApiZone>>() {
            }.getType(), ArrayToMapDeserializer.forArrayToMap(RachioApiZone::id, RachioApiZone.class)) //
            .registerTypeAdapter(new TypeToken<Map<RachioId.Schedule, RachioApiScheduleRule>>() {
            }.getType(), ArrayToMapDeserializer.forArrayToMap(RachioApiScheduleRule::id, RachioApiScheduleRule.class)) //
            .registerTypeAdapter(new TypeToken<Map<RachioId.Valve, RachioApiValve>>() {
            }.getType(), ArrayToMapDeserializer.forArrayToMap(RachioApiValve::id, RachioApiValve.class)) //
            .registerTypeAdapter(new TypeToken<Map<RachioId.BaseStation, RachioApiBaseStation>>() {
            }.getType(), ArrayToMapDeserializer.forArrayToMap(RachioApiBaseStation::id, RachioApiBaseStation.class)) //
            .create());
    private HttpClient httpClient;

    public RachioApi(final HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public Gson getGson() {
        return gson;
    }

    public void initialize(String apiKey) throws RachioApiException {
        this.apiKey = apiKey;
    }

    /*
     * PERSON SERVICE API
     */

    public RachioId.Person getPersonId() throws InterruptedException, TimeoutException, ExecutionException,
            RachioApiException, RateLimitThrottleException {
        Request request = httpClient.newRequest(URL_GET_PERSONID);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        JsonObject jsonResponse = JsonParser.parseString(response.getContentAsString()).getAsJsonObject();
        String idString = jsonResponse.has("id") ? jsonResponse.get("id").getAsString() : null;

        if (idString == null || idString.isEmpty()) {
            throw new RachioApiException("No person ID returned", false);
        }

        return new RachioId.Person(idString);
    }

    public RachioApiPerson getPerson(RachioId.Person personId) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        String url = String.format(URL_GET_PERSON, personId.idString());
        Request request = httpClient.newRequest(url);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.LOW);

        return apiRequireNonNull(gson.fromJson(response.getContentAsString(), RachioApiPerson.class));
    }

    /*
     * DEVICE SERVICE APIS
     */

    public RachioApiDevice getDevice(RachioId.Device deviceId) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        String url = String.format(URL_GET_DEVICE, deviceId.idString());
        Request request = httpClient.newRequest(url);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.LOW);

        return apiRequireNonNull(gson.fromJson(response.getContentAsString(), RachioApiDevice.class));
    }

    public RachioApiCurrentSchedule getDeviceCurrentSchedule(RachioId.Device deviceId) throws InterruptedException,
            TimeoutException, ExecutionException, RachioApiException, RateLimitThrottleException {
        String url = String.format(URL_GET_DEVICE_CURRENT_SCHEDULE, deviceId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.GET);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.MED);

        return apiRequireNonNull(gson.fromJson(response.getContentAsString(), RachioApiCurrentSchedule.class));
    }

    public List<RachioApiEvent> getDeviceEvents(RachioId.Device deviceId, Instant startTime, Instant endTime)
            throws InterruptedException, TimeoutException, ExecutionException, RachioApiException,
            RateLimitThrottleException {
        logger.trace("getDeviceEvent for device '{}'", deviceId.idString());
        String url = String.format(URL_GET_DEVICE_EVENT, deviceId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.GET)
                .param("startTime", String.valueOf(startTime.toEpochMilli()))
                .param("endTime", String.valueOf(endTime.toEpochMilli()));
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.LOW);

        return apiRequireNonNull(gson.fromJson(response.getContentAsString(), new TypeToken<List<RachioApiEvent>>() {
        }.getType()));
    }

    public void putStopWatering(RachioId.Device deviceId) throws RachioApiException, InterruptedException,
            TimeoutException, ExecutionException, RateLimitThrottleException {
        logger.debug("Stop watering for device '{}'", deviceId.idString());
        JsonObject bodyParamJson = new JsonObject();
        bodyParamJson.addProperty("id", deviceId.idString());
        Request request = httpClient.newRequest(URL_PUT_DEVICE_STOP).method(HttpMethod.PUT).content(
                new StringContentProvider(CONTENT_TYPE_JSON, bodyParamJson.toString(), StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void putDeviceRainDelay(RachioId.Device deviceId, long duration) throws RachioApiException,
            InterruptedException, TimeoutException, ExecutionException, RateLimitThrottleException {
        logger.debug("Start rain relay for device '{}'.", deviceId.idString());
        JsonObject bodyParamJson = new JsonObject();
        bodyParamJson.addProperty("id", deviceId.idString());
        bodyParamJson.addProperty("duration", duration);
        Request request = httpClient.newRequest(URL_PUT_DEVICE_RAIN_DELAY).method(HttpMethod.PUT).content(
                new StringContentProvider(CONTENT_TYPE_JSON, bodyParamJson.toString(), StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void enableDevice(RachioId.Device deviceId, boolean enable) throws RachioApiException, InterruptedException,
            TimeoutException, ExecutionException, RateLimitThrottleException {
        logger.debug("enable device '{}' - {}.", deviceId.idString(), enable);
        String url = (enable) ? URL_PUT_DEVICE_ON : URL_PUT_DEVICE_OFF;
        JsonObject bodyParamJson = new JsonObject();
        bodyParamJson.addProperty("id", deviceId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.PUT).content(
                new StringContentProvider(CONTENT_TYPE_JSON, bodyParamJson.toString(), StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void pauseZoneRun(RachioId.Device deviceId, int duration, boolean pause) throws InterruptedException,
            TimeoutException, ExecutionException, RachioApiException, RateLimitThrottleException {
        logger.debug("Pause device '{}' for {} sec - {}.", deviceId.idString(), duration, pause);
        String url = (pause) ? URL_PUT_DEVICE_PAUSE_RUN : URL_PUT_DEVICE_RESUME_RUN;
        JsonObject bodyParamJson = new JsonObject();
        bodyParamJson.addProperty("id", deviceId.idString());
        if (pause) {
            bodyParamJson.addProperty("duration", duration);
        }
        Request request = httpClient.newRequest(url).method(HttpMethod.PUT).content(
                new StringContentProvider(CONTENT_TYPE_JSON, bodyParamJson.toString(), StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    // Legacy Notification Webhooks
    public List<RachioApiNotificationWebhookEventType> getNotificationWebhookEventTypes() throws InterruptedException,
            TimeoutException, ExecutionException, RachioApiException, RateLimitThrottleException {
        logger.debug("getNotificationWebhookEventTypes");
        Request request = httpClient.newRequest(URL_GET_NOTIFICATION_WEBHOOK_EVENT_TYPES).method(HttpMethod.GET);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        return apiRequireNonNull(gson.fromJson(response.getContentAsString(),
                new TypeToken<List<RachioApiNotificationWebhookEventType>>() {
                }.getType()));
    }

    public List<RachioApiNotificationWebhook> getNotificationDeviceWebhook(RachioId.Device deviceId)
            throws InterruptedException, TimeoutException, ExecutionException, RachioApiException,
            RateLimitThrottleException {
        logger.debug("getNotificationDeviceWebhook for device '{}'", deviceId.idString());
        String url = String.format(URL_GET_NOTIFICATION_DEVICE_WEBHOOK, deviceId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.GET);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.LOW);

        return apiRequireNonNull(
                gson.fromJson(response.getContentAsString(), new TypeToken<List<RachioApiNotificationWebhook>>() {
                }.getType()));
    }

    public RachioApiNotificationWebhook getNotificationWebhook(RachioId.NotificationWebhook webhookId)
            throws InterruptedException, TimeoutException, ExecutionException, RachioApiException,
            RateLimitThrottleException {
        logger.debug("getNotificationWebhook for webhook '{}'", webhookId.idString());
        String url = String.format(URL_GET_NOTIFICATION_WEBHOOK, webhookId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.GET);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.LOW);

        return apiRequireNonNull(gson.fromJson(response.getContentAsString(), RachioApiNotificationWebhook.class));
    }

    public void deleteNotificationWebhook(RachioId.NotificationWebhook webhookId) throws InterruptedException,
            TimeoutException, ExecutionException, RachioApiException, RateLimitThrottleException {
        logger.debug("deleteNotificationWebhook for webhook '{}'", webhookId.idString());
        String url = String.format(URL_DEL_NOTIFICATION_WEBHOOK, webhookId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.DELETE);
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public RachioId.NotificationWebhook postNotificationWebhook(RachioId.Device deviceId, String callbackUrl,
            String externalId, List<String> eventTypes) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        logger.debug("postNotificationWebhook for device '{}'", deviceId.idString());

        @SuppressWarnings("null")
        List<NotificationEventTypeId> eventTypeIds = eventTypes.stream().map(NotificationEventTypeId::new).toList();
        String bodyParamJson = gson.toJson(new NotificationCreateBody(new NotificationDeviceRef(deviceId), externalId,
                callbackUrl, Objects.requireNonNull(eventTypeIds)));

        Request request = httpClient.newRequest(URL_POST_NOTIFICATION_WEBHOOK).method(HttpMethod.POST)
                .content(new StringContentProvider(CONTENT_TYPE_JSON, bodyParamJson, StandardCharsets.UTF_8));
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        RachioApiNotificationWebhook apiWebhookResponse = apiRequireNonNull(
                gson.fromJson(response.getContentAsString(), RachioApiNotificationWebhook.class));
        return apiRequireNonNull(apiWebhookResponse.id());
    }

    /*
     * ZONE SERVICE API
     */

    public RachioApiZone getZone(RachioId.Zone zoneId) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {

        String url = String.format(URL_GET_ZONE, zoneId.idString());
        Request request = httpClient.newRequest(url);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.LOW);

        return apiRequireNonNull(gson.fromJson(response.getContentAsString(), RachioApiZone.class));
    }

    public void putZoneStartWatering(RachioId.Zone zoneId, int duration) throws RachioApiException,
            InterruptedException, TimeoutException, ExecutionException, RateLimitThrottleException {
        logger.debug("Start watering Zone '{}' for {} sec.", zoneId.idString(), duration);

        JsonObject bodyParamJson = new JsonObject();
        bodyParamJson.addProperty("id", zoneId.idString());
        bodyParamJson.addProperty("duration", duration);
        Request request = httpClient.newRequest(URL_PUT_ZONE_START).method(HttpMethod.PUT)
                .param("id", zoneId.idString()).content(
                        new StringContentProvider(CONTENT_TYPE_JSON, bodyParamJson.toString(), StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void putZoneStartMultiple(List<RachioId.Zone> zoneList, int duration) throws RachioApiException,
            InterruptedException, TimeoutException, ExecutionException, RateLimitThrottleException {
        logger.debug("Start multiple zones '{}'.", zoneList.toString());

        List<RachioApiZoneRun> apiZoneRunList = new ArrayList<>();
        for (int i = 0; i < zoneList.size(); i++) {
            apiZoneRunList.add(new RachioApiZoneRun(zoneList.get(i), duration, i));
        }

        Request request = httpClient.newRequest(URL_PUT_ZONE_MULTIPLE_START).method(HttpMethod.PUT).content(
                new StringContentProvider(CONTENT_TYPE_JSON, gson.toJson(apiZoneRunList), StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void putZoneStartMultiple(List<RachioApiZoneRun> zoneRunList) throws RachioApiException,
            InterruptedException, TimeoutException, ExecutionException, RateLimitThrottleException {
        if (zoneRunList.isEmpty()) {
            logger.debug("No zones to start.");
            return;
        }
        logger.debug("Start multiple zones '{}'.", zoneRunList.toString());
        JsonObject bodyParamJson = new JsonObject();
        bodyParamJson.add("zones", gson.toJsonTree(zoneRunList));

        Request request = httpClient.newRequest(URL_PUT_ZONE_MULTIPLE_START).method(HttpMethod.PUT).content(
                new StringContentProvider(CONTENT_TYPE_JSON, gson.toJson(bodyParamJson), StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void putZoneMoistureLevel(RachioId.Zone zoneId, int level) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        logger.debug("Set moisture level zone '{}' to {}.", zoneId.idString(), level);
        Request request = httpClient.newRequest(URL_PUT_ZONE_MOISTURE_LEVEL).method(HttpMethod.PUT)
                .param("id", zoneId.idString()).param("level", String.valueOf(level));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void putZoneMoisturePercent(RachioId.Zone zoneId, int percent) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        logger.debug("Set moisture level zone '{}' to {}%.", zoneId.idString(), percent);
        Request request = httpClient.newRequest(URL_PUT_ZONE_MOISTURE_PERCENT).method(HttpMethod.PUT)
                .param("id", zoneId.idString()).param("percent", String.valueOf(percent));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void putZoneEnable(RachioId.Zone zoneId) throws InterruptedException, TimeoutException, ExecutionException,
            RachioApiException, RateLimitThrottleException {
        String jsonBody = "{\"id\":\"" + zoneId.idString() + "\"}";
        Request request = httpClient.newRequest(URL_PUT_ZONE_ENABLE).method(HttpMethod.PUT)
                .content(new StringContentProvider(CONTENT_TYPE_JSON, jsonBody, StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void putZoneDisable(RachioId.Zone zoneId) throws InterruptedException, TimeoutException, ExecutionException,
            RachioApiException, RateLimitThrottleException {
        String jsonBody = "{\"id\":\"" + zoneId.idString() + "\"}";
        Request request = httpClient.newRequest(URL_PUT_ZONE_DISABLE).method(HttpMethod.PUT)
                .content(new StringContentProvider(CONTENT_TYPE_JSON, jsonBody, StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    @Nullable
    public RawType getImageFromURL(String url) {
        Request request = httpClient.newRequest(url).method(HttpMethod.GET);
        ContentResponse response;
        try {
            response = request.send();
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.error("Unable to retrieve image from {}: {}", url, e.getLocalizedMessage());
            return null;
        }

        if (response.getStatus() != HttpStatus.OK_200) {
            logger.error("Unable to retrieve image from {}", url);
            return null;
        }

        byte content[] = response.getContent();
        if (content == null || content.length == 0) {
            logger.error("No image content retrieved from {}", url);
            return null;
        }

        String mediaType = response.getMediaType();
        return new RawType(content, mediaType != null ? mediaType : "application/octet-stream");
    }

    /*
     * SCHEDULE RULE API
     */

    public void putScheduleSkipForward(RachioId.Device deviceId) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        String jsonBody = "{\"id\":\"" + deviceId.idString() + "\"}";
        Request request = httpClient.newRequest(URL_PUT_SCHEDULE_SKIP_FORWARD).method(HttpMethod.PUT)
                .content(new StringContentProvider(CONTENT_TYPE_JSON, jsonBody, StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    /*
     * WEBHOOK SERVICE API
     */
    public RachioApiWebhook createWebhook(RachioId.Id id, String url, String externalId, List<String> eventTypes)
            throws JsonSyntaxException, RachioApiException, InterruptedException, TimeoutException, ExecutionException,
            RateLimitThrottleException {
        record ResourceRef(@SerializedName("valve_id") RachioId.@Nullable Valve valveId,
                @SerializedName("irrigation_controller_id") RachioId.@Nullable Device irrigationControllerId,
                @SerializedName("program_id") RachioId.@Nullable Program programId) {
        }
        record CreateBody(@SerializedName("resource_id") ResourceRef resourceId,
                @SerializedName("external_id") String externalId, String url,
                @SerializedName("event_types") List<String> eventTypes) {
        }

        ResourceRef resourceRef = switch (id) {
            case RachioId.Device deviceId -> new ResourceRef(null, deviceId, null);
            case RachioId.Valve valveId -> new ResourceRef(valveId, null, null);
            case RachioId.Program programId -> new ResourceRef(null, null, programId);
            default -> throw new IllegalArgumentException("Unsupported RachioId type: " + id.getClass());
        };

        String body = gson.toJson(new CreateBody(resourceRef, externalId, url, Objects.requireNonNull(eventTypes)));
        logger.trace("REQUEST BODY: {}", body);

        Request request = httpClient.newRequest(URL_POST_CREATE_WEBHOOK).method(HttpMethod.POST)
                .content(new StringContentProvider(CONTENT_TYPE_JSON, body, StandardCharsets.UTF_8));
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        JsonElement jsonWebhook = JsonParser.parseString(response.getContentAsString()).getAsJsonObject()
                .get("webhook");

        return apiRequireNonNull(gson.fromJson(jsonWebhook, RachioApiWebhook.class));
    }

    public RachioApiNotificationWebhook createNotificationWebhook(RachioId.Device deviceId, String callbackUrl,
            String externalId, List<RachioApiNotificationWebhookEventType> eventTypes) throws InterruptedException,
            TimeoutException, ExecutionException, RachioApiException, RateLimitThrottleException {
        record EventTypeId(String id) {
        }
        record DeviceRef(RachioId.Device id) {
        }
        record CreateBody(DeviceRef device, String externalId, String url, List<EventTypeId> eventTypes) {
        }

        @SuppressWarnings("null")
        List<EventTypeId> eventTypeIds = eventTypes.stream().map(et -> new EventTypeId(et.id())).toList();
        String body = gson.toJson(
                new CreateBody(new DeviceRef(deviceId), externalId, callbackUrl, Objects.requireNonNull(eventTypeIds)));

        Request request = httpClient.newRequest(URL_POST_NOTIFICATION_WEBHOOK).method(HttpMethod.POST)
                .content(new StringContentProvider(CONTENT_TYPE_JSON, body, StandardCharsets.UTF_8));
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        return apiRequireNonNull(gson.fromJson(response.getContentAsString(), RachioApiNotificationWebhook.class));
    }

    /*
     * public RachioApiWebhook createWebhook2(RachioId.Id id, String callbackUri, String externalId)
     * throws JsonSyntaxException, RachioApiException, InterruptedException, TimeoutException, ExecutionException,
     * RateLimitThrottleException {
     * // RachioApiWebhookResourceID resourceId = new RachioApiWebhookResourceID(id);
     * // RachioApiWebhook webhook = new RachioApiWebhook(null, externalId, resourceId, callbackUri, eventTypes);
     * 
     * String jsonData = "{ " + "\"device\":{\"id\":\"" + id.idString() + "\"}, " + "\"externalId\" : \"" + externalId
     * + "\", " + "\"url\" : \"" + callbackUri + "\", " + "\"eventTypes\" : [" + "{\"id\" : \""
     * + WHE_DEVICE_STATUS + "\"}, " + "{\"id\" : \"" + WHE_RAIN_DELAY + "\"}, " + "{\"id\" : \""
     * + WEATHER_INTELLIGENCE + "\"}, " + "{\"id\" : \"" + WHE_WATER_BUDGET + "\"}, " + "{\"id\" : \""
     * + WHE_ZONE_DELTA + "\"}, " + "{\"id\" : \"" + WHE_SCHEDULE_STATUS + "\"}, " + "{\"id\" : \""
     * + WHE_ZONE_STATUS + "\"}, " + "{\"id\" : \"" + WHE_RAIN_SENSOR_DETECTION + "\"}, " + "{\"id\" : \""
     * + WHE_DELTA + "\"} " + "]" + "}";
     * 
     * logger.trace("REQUEST BODY: {}", jsonData);
     * 
     * Request request = httpClient.newRequest("https://api.rach.io/1/public/notification/webhook")
     * .method(HttpMethod.POST)
     * .content(new StringContentProvider(CONTENT_TYPE_JSON, jsonData, StandardCharsets.UTF_8));
     * ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
     * 
     * JsonElement jsonWebhook = JsonParser.parseString(response.getContentAsString()).getAsJsonObject()
     * .get("webhook");
     * 
     * return apiRequireNonNull(gson.fromJson(jsonWebhook, RachioApiWebhook.class));
     * }
     */

    public List<RachioApiWebhook> getListWebhook(RachioId.Id rachioId) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        String url;
        if (rachioId instanceof RachioId.Device) {
            url = URL_GET_LIST_WEBHOOKS_DEVICE;
        } else if (rachioId instanceof RachioId.Valve) {
            url = URL_GET_LIST_WEBHOOKS_VALVE;
        } else if (rachioId instanceof RachioId.Program) {
            url = URL_GET_LIST_WEBHOOKS_PROGRAM;
        } else {
            throw new IllegalArgumentException("Unsupported RachioId type: " + rachioId.getClass());
        }
        url = String.format(url, rachioId.idString());

        Request request = httpClient.newRequest(url).method(HttpMethod.GET);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        JsonElement jsonWebhooksArray = JsonParser.parseString(response.getContentAsString()).getAsJsonObject()
                .get("webhooks");

        return apiRequireNonNull(gson.fromJson(jsonWebhooksArray, new TypeToken<List<RachioApiWebhook>>() {
        }.getType()));
    }

    public void deleteWebhook(RachioId.Webhook webhookId) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        String url = String.format(URL_DELETE_WEBHOOK, webhookId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.DELETE);
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void deleteAllWebhooks(RachioId.Id rachioId) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        String url;
        if (rachioId instanceof RachioId.Device) {
            url = URL_DELETE_WEBHOOK_DEVICE;
        } else if (rachioId instanceof RachioId.Valve) {
            url = URL_DELETE_WEBHOOK_VALVE;
        } else if (rachioId instanceof RachioId.Program) {
            url = URL_DELETE_WEBHOOK_PROGRAM;
        } else {
            throw new IllegalArgumentException("Unsupported RachioId type: " + rachioId.getClass());
        }

        url = String.format(url, rachioId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.DELETE);
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public List<RachioApiEventType> getListWebhookEventTypes() throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        Request request = httpClient.newRequest(URL_GET_LIST_WEBHOOK_EVENT_TYPES).method(HttpMethod.GET);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        JsonElement eventTypes = JsonParser.parseString(response.getContentAsString()).getAsJsonObject()
                .get("eventTypes");

        return apiRequireNonNull(gson.fromJson(eventTypes, new TypeToken<List<RachioApiEventType>>() {
        }.getType()));
    }

    /*
     * Smart Hose Timer API
     */

    public RachioApiBaseStation getBaseStation(RachioId.BaseStation rachioIdBaseStation) throws InterruptedException,
            TimeoutException, ExecutionException, RachioApiException, RateLimitThrottleException {
        String url = String.format(URL_GET_BASE_STATION, rachioIdBaseStation.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.GET);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        JsonElement jsonBaseStation = JsonParser.parseString(response.getContentAsString()).getAsJsonObject()
                .get("baseStation");

        return apiRequireNonNull(gson.fromJson(jsonBaseStation, RachioApiBaseStation.class));
    }

    public Map<RachioId.BaseStation, RachioApiBaseStation> getBaseStations(RachioId.Person personId)
            throws InterruptedException, TimeoutException, ExecutionException, RachioApiException,
            RateLimitThrottleException {
        String url = String.format(URL_GET_BASE_STATIONS, personId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.GET);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        JsonElement jsonBaseStations = JsonParser.parseString(response.getContentAsString()).getAsJsonObject()
                .get("baseStations");

        return apiRequireNonNull(
                gson.fromJson(jsonBaseStations, new TypeToken<Map<RachioId.BaseStation, RachioApiBaseStation>>() {
                }.getType()));
    }

    public RachioApiValve getValve(RachioId.Valve valveId) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        String url = String.format(URL_GET_VALVE, valveId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.GET);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        JsonElement jsonValve = JsonParser.parseString(response.getContentAsString()).getAsJsonObject().get("valve");

        return apiRequireNonNull(gson.fromJson(jsonValve, RachioApiValve.class));
    }

    public Map<RachioId.Valve, RachioApiValve> getValves(RachioId.BaseStation baseStationId)
            throws InterruptedException, TimeoutException, ExecutionException, RachioApiException,
            RateLimitThrottleException {
        String url = String.format(URL_GET_VALVES, baseStationId.idString());
        Request request = httpClient.newRequest(url).method(HttpMethod.GET);
        ContentResponse response = sendRequest(request, ClientRateLimitManager.PRIORITY.HI);

        JsonArray jsonValves = JsonParser.parseString(response.getContentAsString()).getAsJsonObject().get("valves")
                .getAsJsonArray();

        return apiRequireNonNull(gson.fromJson(jsonValves, new TypeToken<Map<RachioId.Valve, RachioApiValve>>() {
        }.getType()));
    }

    public void putValveStartWatering(RachioId.Valve valveId, long durationSeconds) throws InterruptedException,
            TimeoutException, ExecutionException, RachioApiException, RateLimitThrottleException {
        JsonObject bodyParamJson = new JsonObject();
        bodyParamJson.addProperty("valveId", valveId.idString());
        bodyParamJson.addProperty("durationSeconds", (int) durationSeconds);
        Request request = httpClient.newRequest(URL_PUT_VALVE_START_WATERING).method(HttpMethod.PUT).content(
                new StringContentProvider(CONTENT_TYPE_JSON, bodyParamJson.toString(), StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void putValveStopWatering(RachioId.Valve valveId) throws InterruptedException, TimeoutException,
            ExecutionException, RachioApiException, RateLimitThrottleException {
        JsonObject bodyParamJson = new JsonObject();
        bodyParamJson.addProperty("valveId", valveId.idString());
        Request request = httpClient.newRequest(URL_PUT_VALVE_STOP_WATERING).method(HttpMethod.PUT).content(
                new StringContentProvider(CONTENT_TYPE_JSON, bodyParamJson.toString(), StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    public void putValveDefaultRunTime(RachioId.Valve valveId, long durationSeconds) throws InterruptedException,
            TimeoutException, ExecutionException, RachioApiException, RateLimitThrottleException {
        JsonObject bodyParamJson = new JsonObject();
        bodyParamJson.addProperty("valveId", valveId.idString());
        bodyParamJson.addProperty("defaultRuntimeSeconds", durationSeconds);
        Request request = httpClient.newRequest(URL_PUT_VALVE_DEFAULT_RUNTIME).method(HttpMethod.PUT).content(
                new StringContentProvider(CONTENT_TYPE_JSON, bodyParamJson.toString(), StandardCharsets.UTF_8));
        sendRequest(request, ClientRateLimitManager.PRIORITY.HI);
    }

    /*
     * Utility functions
     */

    private void setHeaders(Request request) {
        request.timeout(API_TIMEOUT, TimeUnit.SECONDS);
        request.header(HttpHeader.AUTHORIZATION, "Bearer " + apiKey);
        request.header(HttpHeader.ACCEPT, CONTENT_TYPE_JSON);
    }

    private synchronized ContentResponse sendRequest(@Nullable Request request,
            ClientRateLimitManager.PRIORITY priority) throws InterruptedException, TimeoutException, ExecutionException,
            RachioApiException, RateLimitThrottleException {

        if (request == null) {
            throw new RachioApiException("Request is null", false);
        }

        rateLimitManager.tryThrottle(priority);

        setHeaders(request);

        logger.trace("REQUEST: {}", request.toString());
        ContentResponse response = request.send();
        logger.trace("RESPONSE: {}", response.getContentAsString());

        HttpFields httpFields = response.getHeaders();
        if (httpFields.containsKey(RACHIO_JSON_RATE_LIMIT)) {
            int rateLimitCap = Integer.valueOf(httpFields.get(RACHIO_JSON_RATE_LIMIT));
            int rateRemaining = Integer.valueOf(httpFields.get(RACHIO_JSON_RATE_REMAINING));
            String rateResetString = httpFields.get(RACHIO_JSON_RATE_RESET); // new API adds [UTC] to this string
            Instant rateReset = Instant.parse(rateResetString.substring(0, rateResetString.indexOf("Z") + 1));

            rateLimitManager.updateRateLimit(rateLimitCap, rateRemaining, rateReset);
        } else {
            rateLimitManager.logRequest();
        }

        switch (response.getStatus()) {
            case HttpStatus.OK_200:
            case HttpStatus.NO_CONTENT_204:
                break;
            case HttpStatus.BAD_REQUEST_400: // API responses with 400 when user credentials are invalid
                throw new RachioApiException("@text/api.bad-request", true);
            case HttpStatus.UNAUTHORIZED_401:
                throw new RachioApiException("@text/api.invalid-user-credentials", true);
            case HttpStatus.TOO_MANY_REQUESTS_429:
                throw new RachioApiException("@text/api.rate-limit-exceeded", false);
            default:
                throw new RachioApiException("Unexpected API error: " + response.getReason(), false);
        }

        return response;
    }

    private static <T> T apiRequireNonNull(@Nullable T obj) throws RachioApiException {
        if (obj == null) {
            throw new RachioApiException("@text/api.invalid-response", false);
        } else {
            return obj;
        }
    }
}
