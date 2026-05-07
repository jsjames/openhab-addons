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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * The {@link RachioId} used to support type-checking for Rachio IDs
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class RachioId {
    public interface Id {
        String idString();
    }

    public record Device(@NonNull String idString) implements Id {
        public static final Device EMPTY = new Device("");
    }

    public record Zone(@NonNull String idString) implements Id {
        public static final Zone EMPTY = new Zone("");
    }

    public record Schedule(@NonNull String idString) implements Id {
        public static final Schedule EMPTY = new Schedule("");
    }

    public record Event(@NonNull String idString) implements Id {
        public static final Event EMPTY = new Event("");
    }

    public record Person(@NonNull String idString) implements Id {
        public static final Person EMPTY = new Person("");
    }

    public record Webhook(@NonNull String idString) implements Id {
        public static final Webhook EMPTY = new Webhook("");
    }

    public record NotificationWebhook(@NonNull String idString) implements Id {
        public static final NotificationWebhook EMPTY = new NotificationWebhook("");
    }

    public record Program(@NonNull String idString) implements Id {
        public static final Program EMPTY = new Program("");
    }

    public record Valve(@NonNull String idString) implements Id {
        public static final Valve EMPTY = new Valve("");
    }

    public record BaseStation(@NonNull String idString) implements Id {
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
            case "NOTIFICATIONWEBHOOK" -> new NotificationWebhook(idString);
            default -> throw new IllegalArgumentException("Unknown Rachio ID type: " + type);
        };
    }

    public static class RachioIdTypeAdapterFactory implements TypeAdapterFactory {
        @NonNullByDefault({})
        @SuppressWarnings({ "unchecked", "null" })
        @Override
        public <T> @Nullable TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            Class<? super T> rawType = typeToken.getRawType();
            if (!Id.class.isAssignableFrom(rawType) || rawType == Id.class) {
                return null;
            }
            return (TypeAdapter<T>) new TypeAdapter<Id>() {
                @Override
                public void write(JsonWriter out, Id value) throws IOException {
                    out.value(value.idString());
                }

                @Override
                public Id read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        throw new JsonParseException("Unexpected null for " + rawType.getSimpleName());
                    }
                    String idString = in.nextString();
                    try {
                        return Objects.requireNonNull(
                                (Id) rawType.getDeclaredConstructor(String.class).newInstance(idString));
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                            | NoSuchMethodException e) {
                        throw new JsonParseException(
                                "Failed to deserialize " + rawType.getSimpleName() + ": " + e.getMessage(), e);
                    }
                }
            };
        }
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
                T result = clazz.getDeclaredConstructor(String.class).newInstance(json.getAsString());
                return Objects.requireNonNull(result, "Deserialized RachioId cannot be null");
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                    | NoSuchMethodException e) {
                throw new JsonParseException(
                        "Failed to deserialize " + clazz.getSimpleName() + " from JSON: " + e.getMessage(), e);
            }
        }

        @Override
        public JsonElement serialize(T src, @Nullable Type typeOfSrc, @Nullable JsonSerializationContext context) {
            if (context == null) {
                throw new JsonParseException("Unable to serialize JSON");
            }

            JsonElement jsonElement = context.serialize(src.idString());

            return (jsonElement != null) ? jsonElement : Objects.requireNonNull(JsonNull.INSTANCE);
        }
    }
}
