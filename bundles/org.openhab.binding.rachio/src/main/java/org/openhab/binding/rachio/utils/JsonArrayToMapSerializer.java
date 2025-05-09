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
package org.openhab.binding.rachio.utils;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * The {@link JsonArrayToMapSerializer}
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class JsonArrayToMapSerializer<K, V extends Id<?>> implements JsonDeserializer<Map<K, V>> {
    private final Function<V, K> keyFunc;
    private final Class<V> clazzV;

    public JsonArrayToMapSerializer(Function<V, K> keyFunc, Class<V> clazzV) {
        this.keyFunc = keyFunc;
        this.clazzV = clazzV;
    }

    @Override
    public @Nullable Map<K, V> deserialize(@Nullable JsonElement json, @Nullable Type typeOfT,
            @Nullable JsonDeserializationContext context) throws JsonParseException {
        if (json == null || !json.isJsonArray() || context == null) {
            throw new JsonParseException("Expected JSON array");
        }

        HashMap<K, V> hashMap = new HashMap<>();

        for (JsonElement element : json.getAsJsonArray()) {
            @Nullable
            V v = context.deserialize(element, clazzV);
            if (v != null && v.getId() != null) {
                K k;
                try {
                    k = keyFunc.apply(v);
                } catch (IllegalArgumentException | SecurityException e) {
                    throw new JsonParseException(e.getCause());
                }
                hashMap.put(k, v);
            }
        }

        return hashMap;
    }
}
