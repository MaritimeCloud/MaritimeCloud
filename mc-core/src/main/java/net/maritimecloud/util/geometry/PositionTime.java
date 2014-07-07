/* Copyright (c) 2011 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.maritimecloud.util.geometry;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.util.Objects.requireNonNull;
import static net.maritimecloud.util.geometry.CoordinateConverter.compass2cartesian;

import java.io.IOException;

import net.maritimecloud.core.serialization.MessageReader;
import net.maritimecloud.core.serialization.MessageSerializer;
import net.maritimecloud.core.serialization.MessageWriter;

/**
 * A position couple with a timestamp.
 *
 * @author Kasper Nielsen
 */
public class PositionTime extends Position {

    /** A parser of PositionTime messages. */
    public static final MessageSerializer<PositionTime> SERIALIZER = new MessageSerializer<PositionTime>() {

        /** {@inheritDoc} */
        @Override
        public PositionTime read(MessageReader reader) throws IOException {
            return readFrom(reader);
        }

        public void write(PositionTime message, MessageWriter writer) throws IOException {
            message.writeTo(writer);
        }
    };

    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    private final long time;

    /**
     * @param latitude
     * @param longitude
     * @param time
     */
    PositionTime(double latitude, double longitude, long time) {
        super(latitude, longitude);
        this.time = time;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        return other instanceof PositionTime && equals((PositionTime) other);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Position other) {
        return other instanceof PositionTime && equals((PositionTime) other);
    }

    // We probably want another function that also takes a precision.
    public boolean equals(PositionTime other) {
        return super.equals(other) && time == other.time;
    }

    /**
     * Calculate - using linear extrapolation (or dead reckoning) - a position based on known speed and course from this
     * position.
     *
     * @see <a href="http://en.wikipedia.org/wiki/Dead_reckoning">Dead reckoning</a>
     *
     * @param cog
     *            is the course over ground.
     * @param sog
     *            is the speed over ground in knots.
     * @param time
     *            the absolute time (in millis since Epoch) to extrapolate to.
     * @return a new PositionTime instance containing the dead reckoned position at time t.
     */
    public PositionTime extrapolatePosition(float cog, float sog, long time) {
        if (time < getTime()) {
            throw new IllegalArgumentException("'time' arguments cannot be earlier than 'pt1'. " + time + " "
                    + getTime());
        }

        final CoordinateConverter coordinateConverter = new CoordinateConverter(getLongitude(), getLatitude());
        final double x0 = coordinateConverter.lon2x(getLongitude(), getLatitude());
        final double y0 = coordinateConverter.lat2y(getLongitude(), getLatitude());
        final int dt = (int) ((time - getTime()) / 1000); // dt the time sailed in seconds
        final double dist = dt * sog * 0.5144; // The distance sailed in dt seconds
        final double angle = compass2cartesian(cog); // COG converted to cartesian angle
        final double dx = cos(angle / 180 * Math.PI) * dist; // Distance sailed horisontal
        final double dy = sin(angle / 180 * Math.PI) * dist; // Distance sailed vertical
        final double x1 = x0 + dx;
        final double y1 = y0 + dy;
        final double lon1 = coordinateConverter.x2Lon(x1, y1); // The new longitude
        final double lat1 = coordinateConverter.y2Lat(x1, y1); // The new latitude

        return create(lat1, lon1, time);
    }

    public long getTime() {
        return time;
    }

    /**
     * Hash code for the location
     */
    @Override
    public int hashCode() {
        // If we need to use this as a key somewhere we can use the same hash
        // code technique as java.lang.String
        long latLong = Double.doubleToLongBits(latitude);
        long lonLong = Double.doubleToLongBits(longitude);
        return (int) (time ^ latLong ^ latLong >>> 32) ^ (int) (time ^ lonLong ^ lonLong >>> 32);
    }

    /**
     * Calculate - using linear interpolation - a position between this position and a specified. Assuming constant
     * speed between the two positions.
     *
     * @param laterPosition
     *            the later position to use in the interpolation.
     * @param time
     *            the time at which the interpolated position should be calculated.
     * @return a new PositionTime instance containing the interpolated position at absolute time t.
     */
    public PositionTime interpolatedPosition(PositionTime laterPosition, long time) {
        requireNonNull(laterPosition);
        if (laterPosition.getTime() < getTime()) {
            throw new IllegalArgumentException("Provided position 1 must be earlier than position 2." + getTime() + " "
                    + laterPosition.getTime());
        }
        if (time < getTime()) {
            throw new IllegalArgumentException("time parameter must be later than position 1's. " + time + " "
                    + getTime());
        }
        if (time > laterPosition.getTime()) {
            throw new IllegalArgumentException("time parameter must be earlier than position 2's. " + time + " "
                    + laterPosition.getTime());
        }

        double interpolatedLatitude = linearInterpolation(getLatitude(), getTime(), laterPosition.getLatitude(),
                laterPosition.getTime(), time);
        double interpolatedLongitude = linearInterpolation(getLongitude(), getTime(), laterPosition.getLongitude(),
                laterPosition.getTime(), time);

        return create(interpolatedLatitude, interpolatedLongitude, time);
    }

    public boolean positionEquals(Position other) {
        return super.equals(other);
    }

    @Override
    public String toString() {
        return "(" + getLatitude() + ", " + getLongitude() + ", time= " + time + ")";
    }

    /**
     * Writes this position to the specified MSDL output stream.
     *
     * @param os
     *            the output stream
     * @throws IOException
     *             the position failed to be written
     */
    public void writeTo(MessageWriter w) throws IOException {
        // if (w.isCompact()) {
        // writeToPacked(w, 1, "latitude", 2, "longitude");
        // } else {
        w.writeDouble(1, "latitude", latitude);
        w.writeDouble(2, "longitude", longitude);
        w.writeInt64(3, "time", time);
        // }
    }

    /**
     * Creates a new position from the specified latitude and longitude.
     *
     * @param latitude
     *            the latitude
     * @param longitude
     *            the longitude
     * @return the new position
     * @throws IllegalArgumentException
     *             if the
     */
    public static PositionTime create(double latitude, double longitude, long time) {
        return new PositionTime(latitude, longitude, time);
    }

    public static PositionTime create(Position position, long time) {
        return create(position.latitude, position.longitude, time);
    }

    static final double linearInterpolation(double y1, long x1, double y2, long x2, long x) {
        return y1 + (y2 - y1) / (x2 - x1) * (x - x1);
    }

    public static PositionTime readFrom(MessageReader r) throws IOException {
        // if (r.isCompact()) {
        // int lat = r.readInt32(1, "latitude");
        // int lon = r.readInt32(2, "longitude");
        // return Position.create(lat / 10_000_000d, lon / 10_000_000d);
        // } else {
        double lat = r.readDouble(1, "latitude");
        double lon = r.readDouble(2, "longitude");
        long time = r.readInt64(3, "time", 0L);

        return PositionTime.create(lat, lon, time);
        // }
    }
}
