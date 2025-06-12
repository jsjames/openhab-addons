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

import static org.openhab.binding.rachio.internal.RachioBindingConstants.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.rachio.internal.handler.RachioCloudConnectorHandler;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * {@link RachioWebhookServlet} implements the callback for the Rachio Cloud event API.
 *
 * @author Markus Michels - Initial contribution
 */
@NonNullByDefault
public class RachioWebhookServlet extends HttpServlet {
    private static final long serialVersionUID = 8706067059503620493L;
    private final Logger logger = LoggerFactory.getLogger(RachioWebhookServlet.class);
    private final Gson gson;

    private final HttpService httpService;
    private final RachioCloudConnectorHandler rachioBridgeHandler;

    public RachioWebhookServlet(HttpService httpService, RachioCloudConnectorHandler rachioBridgeHandler,
            RachioApi api) {
        this.httpService = httpService;
        this.rachioBridgeHandler = rachioBridgeHandler;
        this.gson = api.getGson();
        try {
            httpService.registerServlet(SERVLET_WEBHOOK_PATH, this, null, httpService.createDefaultHttpContext());
            logger.debug("RachioWebhook: Started servlet at {}", SERVLET_WEBHOOK_PATH);
        } catch (ServletException | NamespaceException e) {
            logger.warn("RachioWebhook: Could not start Rachio Webhook servlet", e);
        }
    }

    public void dispose() {
        httpService.unregister(SERVLET_WEBHOOK_PATH);
        logger.debug("RachioWebhook: Servlet stopped");
    }

    @Override
    protected void doGet(@Nullable HttpServletRequest request, @Nullable HttpServletResponse resp)
            throws ServletException, IOException {
        logger.debug("RachioWebhook: doGet called");
    }

    @Override
    protected void doPost(@Nullable HttpServletRequest request, @Nullable HttpServletResponse resp)
            throws ServletException, IOException {
        if (request == null) {
            logger.debug("RachioWebhook: doPost called with null request");
            return;
        }

        JsonElement jsonContent = JsonParser.parseReader(request.getReader());
        logger.debug("RachioWebhook: doPost called: {}", jsonContent.toString());

        if (resp != null) {
            setHeaders(resp);
            resp.getWriter().write("");
        }
    }

    /*
     * @Override
     * protected void service(@Nullable HttpServletRequest request, @Nullable HttpServletResponse resp)
     * throws ServletException, IOException {
     * if (request == null) {
     * logger.debug("RachioWebhook: doPost called with null request");
     * return;
     * }
     * 
     * JsonElement jsonContent = JsonParser.parseReader(request.getReader());
     * logger.debug("RachioWebhook: doPost called: {}", jsonContent.toString());
     * 
     * if(resp != null) {
     * setHeaders(resp);
     * resp.getWriter().write("");
     * }
     * 
     * String data = inputStreamToString(request);
     * try {
     * String ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
     * ipAddress = (ipAddress != null) ? ipAddress : request.getRemoteAddr();
     * String path = request.getRequestURI();
     * 
     * if (path == null) {
     * logger.debug("RachioWebhook: invalid request URI");
     * return;
     * }
     * 
     * logger.trace("RachioWebhook: Reqeust from {}:{}{} ({}:{}, {})", ipAddress, request.getRemotePort(), path,
     * request.getRemoteHost(), request.getServerPort(), request.getProtocol());
     * if (!path.equalsIgnoreCase(SERVLET_WEBHOOK_PATH)) {
     * logger.debug("RachioWebhook: Invalid request received - path = {}", path);
     * return;
     * }
     * 
     * // Fix malformed API v3 Event JSON
     * // TODO - do we still need this?
     * // data = data.replace("\"{", "{");
     * // data = data.replace("}\"", "}");
     * // data = data.replace("\\", "");
     * // data = data.replace("\"?\"", "'?'"); // fix json for"summary" : "<Device> has turned off and back on.
     * // This is usually not a problem. If power cycles continue, tap "?"/ above to
     * // contact Rachio Support.",
     * logger.trace("RachioWebhook: Data='{}'", data);
     * RachioApiEvent event = gson.fromJson(data, RachioApiEvent.class);
     * 
     * if (event == null) {
     * logger.error("Invalid event JSON");
     * return;
     * }
     * 
     * // logger.trace("RachioEvent {}.{} for device '{}': {}", event.category(), event.type(), event.deviceId(),
     * // event.summary());
     * // TODO
     * // event.apiResult.setRateLimit(request.getHeader(RACHIO_JSON_RATE_LIMIT),
     * // request.getHeader(RACHIO_JSON_RATE_REMAINING), request.getHeader(RACHIO_JSON_RATE_RESET));
     * 
     * if (!rachioBridgeHandler.webhookEvent(event)) {
     * logger.debug("RachioWebhook: Event-JSON='{}'", data);
     * }
     * return;
     * } catch (RuntimeException e) {
     * logger.debug("RachioWebhook: Exception processing callback: {}, data='{}'", e.getMessage(), data);
     * } finally {
     * if (resp != null) {
     * setHeaders(resp);
     * resp.getWriter().write("");
     * }
     * }
     * }
     */

    private void setHeaders(HttpServletResponse response) {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(CONTENT_TYPE_JSON);
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
    }
}
