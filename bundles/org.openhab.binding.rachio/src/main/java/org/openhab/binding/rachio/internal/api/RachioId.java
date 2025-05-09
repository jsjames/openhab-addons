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

/**
 * The {@link RachioId} used to in type-checking the Rachio IDs
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class RachioId {
    public interface Id {
        String id();
    }

    public record Device(String id) implements Id {
    }

    public record Zone(String id) implements Id {
    }

    public record Schedule(String id) implements Id {
    }

    public record Event(String id) implements Id {
    }

    public record Person(String id) implements Id {
        static public final Person EMPTY = new Person("");
    }

    public record Webhook(String id) implements Id {
        static public final Webhook EMPTY = new Webhook("");
    }

    public record Program(String id) implements Id {
    }

    public record Valve(String id) implements Id {
    }

    public record BaseStation(String id) implements Id {
    }

    public static class RachioIdDeserializer<T> implements JsonDeserializer<T> {
        private final Class<T> clazz;

        public RachioIdDeserializer(Class<T> clazz) {
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
    }
}
