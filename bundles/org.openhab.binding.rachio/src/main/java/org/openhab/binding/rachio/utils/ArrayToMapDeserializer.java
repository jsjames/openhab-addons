package org.openhab.binding.rachio.utils;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * Utility class providing functional-style JSON array to Map deserializers for Gson.
 * 
 * <p>
 * Example usage:
 * 
 * <pre>{@code
 * GsonBuilder builder = new GsonBuilder().registerTypeAdapter(new TypeToken<Map<RachioId.Zone, RachioApiZone>>() {
 * }.getType(), ArrayToMapDeserializer.forArrayToMap(RachioApiZone::id, RachioApiZone.class));
 * }</pre>
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class ArrayToMapDeserializer {

    private ArrayToMapDeserializer() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a JsonDeserializer that converts JSON arrays to Maps
     * and duplicate key strategy.
     *
     * @param <K> the type of the map keys
     * @param <V> the type of the map values
     * @param keyExtractor function to extract the key from each value
     * @param valueClass the class of the values to deserialize
     * @return a JsonDeserializer that converts JSON arrays to Maps
     */
    public static <K, V> JsonDeserializer<Map<K, V>> forArrayToMap(Function<V, K> keyExtractor, Class<V> valueClass) {

        return (json, typeOfT, context) -> deserializeArrayToMap(json, typeOfT, context, keyExtractor, valueClass);
    }

    private static <K, V> Map<K, V> deserializeArrayToMap(@Nullable JsonElement json, @Nullable Type typeOfT,
            @Nullable JsonDeserializationContext context, Function<V, K> keyExtractor, Class<V> valueClass)
            throws JsonParseException {

        if (json == null || !json.isJsonArray()) {
            throw new JsonParseException(
                    "Expected JSON array, got: " + (json == null ? "null" : json.getClass().getSimpleName()));
        }

        if (context == null) {
            throw new JsonParseException("Deserialization context cannot be null");
        }

        try {
            @SuppressWarnings("null")
            @NonNull
            Map<K, V> result = json.getAsJsonArray().asList().stream()
                    .map(element -> context.<V> deserialize(element, valueClass)).filter(Objects::nonNull)
                    .filter(value -> {
                        try {
                            return keyExtractor.apply(value) != null;
                        } catch (RuntimeException e) {
                            // Log and skip invalid values rather than failing entirely
                            return false;
                        }
                    }).collect(Collectors.toMap(keyExtractor, Function.identity(), (oldValue, newValue) -> newValue));
            return result;
        } catch (RuntimeException e) {
            throw new JsonParseException("Failed to deserialize JSON array to map: " + e.getMessage(), e);
        }
    }
}
