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
package org.openhab.binding.rachio.internal.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.types.Command;

/**
 * {@link RachioUtils} provides some helper functions
 *
 * @author Jeff James - Initial contribution
 */
@NonNullByDefault
public class RachioUtils {
    /**
     * {@link reconcileCollections} will compare 2 collections and call the provided consumer methods appropriate
     * 
     * @param <T> base type of the collections. Both collections must be of (or inherited from) the common type T
     * @param c1 collection 1
     * @param c2 collection 2
     * @param c1Exclusive method called for items which are unique to collection 1
     * @param c2Exclusive method called for items which are unique to collection 2
     * @param c1c2Inclusive method called for items which are included in both collection 1 & 2
     */
    public static <T> void reconcileCollections(Collection<T> c1, Collection<T> c2, Consumer<T> c1Exclusive,
            Consumer<T> c2Exclusive, Consumer<T> c1c2Inclusive) {
        Set<T> setC1 = (c1 instanceof Set<T> c1set) ? c1set : new HashSet<>(c1);
        Set<T> setC2 = (c2 instanceof Set<T> c2set) ? c2set : new HashSet<>(c2);

        // Processing elements in c1 (distinct)
        for (T element : setC1) {
            if (setC2.contains(element)) {
                c1c2Inclusive.accept(element); // Common elements
            } else {
                c1Exclusive.accept(element); // Elements exclusive to c1
            }
        }

        for (T element : setC2) {
            if (!setC1.contains(element)) {
                c2Exclusive.accept(element); // Elements exclusive to c2
            }
        }
    }

    /**
     * {@link getRuntimeFromCommand} translates openhab command of either QuantityType<Time> or DecimalType to number of
     * seconds
     * 
     * @param command either QuantityType<Time> or DecimalType
     * @return number of seconds, or 0 if QuantityType is not convertible to seconds or an incompatible command is given
     */
    public static int getRuntimeFromCommand(Command command) {
        int runTime = 0;
        if (command instanceof QuantityType<?> qtCommand) {
            @Nullable
            QuantityType<?> qtCommandSeconds = qtCommand.toUnit(Units.SECOND);
            runTime = (qtCommandSeconds != null) ? qtCommandSeconds.intValue() : 0;
        } else if (command instanceof DecimalType dtCommand) {
            runTime = dtCommand.intValue() * 60;
        }
        return runTime;
    }

    /**
     * Determines whether an update to the channel is required based on the provided parameters.
     *
     * @param channel The current channel as a non-null {@link String} which is being evaluated.
     * @param forceUpdate A boolean flag indicating if an update should be forced.
     * @param updateChannel An optional {@link String} the update channel. It may be {@code null} if update any/all
     *            channels should be updated.
     * @param oldValue An optional {@link Object} that represents the old value. It may be {@code null}.
     * @param newValue An optional {@link Object} that represents the new value. It may be {@code null}.
     * @return {@code true} if an update is required; {@code false} otherwise.
     *
     *         <p>
     *         The conditions under which an update is considered required are:
     *         </p>
     *         <ul>
     *         <li>If the specified {@code channel} matches the {@code updateChannel}.</li>
     *         <li>If updates are forced and the {@code updateChannel} is {@code null}.</li>
     *         <li>If the {@code oldValue} is {@code null}.</li>
     *         <li>If the {@code oldValue} does not equal the {@code newValue}.</li>
     *         </ul>
     */
    public static boolean isUpdateRequired(String channel, boolean forceUpdate, @Nullable String updateChannel,
            @Nullable Object oldValue, @Nullable Object newValue) {
        return (channel.equals(updateChannel) || //
                (forceUpdate && updateChannel == null) || //
                oldValue == null || //
                !oldValue.equals(newValue));
    }

    /**
     * Safely retrieves a value from the given supplier, returning {@code null} if a
     * {@link NullPointerException} is encountered during retrieval.
     *
     * @param <T> The type of the result supplied by the {@link Supplier}.
     * @param supplier A {@link Supplier} providing the object to retrieve. It is expected that the
     *            supplier could potentially throw a {@code NullPointerException}, which this
     *            method will handle gracefully.
     * @return The object provided by the supplier, or {@code null} if a {@code NullPointerException}
     *         occurs while retrieving the object.
     */
    @Nullable
    public static <T> T safeGet(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Safely retrieves a value from the given supplier, returning a specified default value if a
     * {@link NullPointerException} is encountered during retrieval or if the supplied value is
     * {@code null}.
     *
     * @param <T> The type of the result supplied by the {@link Supplier}.
     * @param supplier A {@link Supplier} providing the object to retrieve. It is expected that the
     *            supplier could potentially throw a {@code NullPointerException}, which this
     *            method will handle gracefully.
     * @param defaultValue The value to return if the retrieved value is {@code null} or if the
     *            supplier throws a {@code NullPointerException}.
     * @return The object provided by the supplier, or the specified {@code defaultValue} if a
     *         {@code NullPointerException} occurs or if the supplied value is {@code null}.
     */
    @Nullable
    public static <@Nullable T> T safeGet(Supplier<T> supplier, T defaultValue) {
        T value = safeGet(supplier);
        return (value != null) ? value : defaultValue;
    }

    public static String enocdeUri(String uri) {
        int startUserInfo = uri.indexOf("//") + 2; // skip the '//' at the start
        int endUserInfo = uri.lastIndexOf("@");

        if (startUserInfo < 0 || endUserInfo < 0 || startUserInfo >= endUserInfo) {
            throw new InvalidParameterException("Callback URI missing user info: " + uri);
        }

        String[] userInfo = uri.substring(startUserInfo, endUserInfo).split(":", 2);
        if (userInfo.length != 2) {
            throw new InvalidParameterException("User info must be in the form user:password: " + userInfo);
        }
        String encodedUserInfo = URLEncoder.encode(userInfo[0], StandardCharsets.UTF_8) + ":"
                + URLEncoder.encode(userInfo[1], StandardCharsets.UTF_8);
        return uri.substring(0, startUserInfo) + encodedUserInfo + uri.substring(endUserInfo);
    }

    /**
     * Given a string, return the MD5 hash of the String.
     *
     * @param unhashed The string contents to be hashed.
     * @return MD5 Hashed value of the String. Null if there is a problem hashing the String.
     */
    public static String getMD5Hash(String unhashed) {
        try {
            byte[] bytesOfMessage = unhashed.getBytes(StandardCharsets.UTF_8);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] hash = md5.digest(bytesOfMessage);
            StringBuilder sb = new StringBuilder(2 * hash.length);

            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }

            String digest = sb.toString();

            return digest;
        } catch (RuntimeException | NoSuchAlgorithmException e) {
            return "";
        }
    }
}
