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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

/**
 * The {@link RachioId} used to in type-checking the Rachio IDs
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class RachioId {
    public interface Id {
        String idString();
    }

    public record Device(String idString) implements Id {
        public static final Device EMPTY = new Device("");
    }

    public record Zone(String idString) implements Id {
        public static final Zone EMPTY = new Zone("");
    }

    public record Schedule(String idString) implements Id {
        public static final Schedule EMPTY = new Schedule("");
    }

    public record Event(String idString) implements Id {
        public static final Event EMPTY = new Event("");
    }

    public record Person(String idString) implements Id {
        public static final Person EMPTY = new Person("");
    }

    public record Webhook(String idString) implements Id {
        public static final Webhook EMPTY = new Webhook("");
    }

    public record Program(String idString) implements Id {
        public static final Program EMPTY = new Program("");
    }

    public record Valve(String idString) implements Id {
        public static final Valve EMPTY = new Valve("");
    }

    public record BaseStation(String idString) implements Id {
        public static final BaseStation EMPTY = new BaseStation("");
    }

    public static RachioId.Id create(String type, String idString) {
        return switch (type.toUpperCase().trim()) {
            case "DEVICE" -> new Device(idString);
            case "ZONE" -> new Zone(idString);
            case "SCHEDULE" -> new Schedule(idString);
            case "EVENT" -> new Event(idString);
            case "PERSON" -> new Person(idString);
            case "WEBHOOK" -> new Webhook(idString);
            case "PROGRAM" -> new Program(idString);
            case "VALVE" -> new Valve(idString);
            case "BASESTATION" -> new BaseStation(idString);
            default -> throw new IllegalArgumentException("Unknown Rachio ID type: " + type);
        };
    }

    public static class RachioIdGsonAdapter<T extends Id> implements JsonDeserializer<T>, JsonSerializer<T> {
        private final Class<T> clazz;

        public RachioIdGsonAdapter(Class<T> clazz) {
            this.clazz = Objects.requireNonNull(clazz, "Class cannot be null");
        }

        @Override
        public T deserialize(@Nullable JsonElement json, @Nullable Type typeOfT,
                @Nullable JsonDeserializationContext context) throws JsonParseException {
            if (json == null || !json.isJsonPrimitive()) {
                throw new JsonParseException(
                        "Expected JSON primitive, got: " + (json == null ? "null" : json.getClass().getSimpleName()));
            }

            try {
                return clazz.getDeclaredConstructor(String.class).newInstance(json.getAsString());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                    | NoSuchMethodException e) {
                throw new JsonParseException(
                        "Failed to deserialize " + clazz.getSimpleName() + " from JSON: " + e.getMessage(), e);
            }
        }

        @Override
        @Nullable
        public JsonElement serialize(T src, @Nullable Type typeOfSrc, @Nullable JsonSerializationContext context) {
            if (context == null) {
                throw new JsonParseException("Unable to serialize JSON");
            }

            return context.serialize(src.idString());
        }
    }
}
