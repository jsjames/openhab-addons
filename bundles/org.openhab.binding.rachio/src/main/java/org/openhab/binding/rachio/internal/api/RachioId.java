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

import org.eclipse.jdt.annotation.NonNullByDefault;

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
    }

    public record Zone(String idString) implements Id {
        static public final Zone EMPTY = new Zone("");
    }

    public record Schedule(String idString) implements Id {
    }

    public record Event(String idString) implements Id {
    }

    public record Person(String idString) implements Id {
        static public final Person EMPTY = new Person("");
    }

    public record Webhook(String idString) implements Id {
        static public final Webhook EMPTY = new Webhook("");
    }

    public record Program(String idString) implements Id {
    }

    public record Valve(String idString) implements Id {
    }

    public record BaseStation(String idString) implements Id {
    }

    public static RachioId.Id create(String type, String idString) {
        return switch (type.toUpperCase()) {
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

    public static class RachioIdGsonAdpater<T> implements JsonDeserializer<T>, JsonSerializer<T> {
        private final Class<T> clazz;

        public RachioIdGsonAdpater(Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                return (T) clazz.getDeclaredConstructor(String.class).newInstance(json.getAsString());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                    | NoSuchMethodException e) {
                throw new JsonParseException("Unable to parse JSON");
            }
        }

        @Override
        public JsonElement serialize(T src, Type typeOfSrc, JsonSerializationContext context) {
            if (src instanceof Id id) {
                return context.serialize(id.idString());
            } else {
                throw new JsonParseException("Unable to serialize JSON");
            }
        }
    }
}
