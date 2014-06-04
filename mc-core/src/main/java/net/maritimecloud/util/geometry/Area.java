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

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import net.maritimecloud.core.message.Message;
import net.maritimecloud.core.message.MessageParser;
import net.maritimecloud.core.message.MessageReader;
import net.maritimecloud.core.message.MessageSerializable;
import net.maritimecloud.core.message.MessageSerializers;
import net.maritimecloud.core.message.MessageWriter;
import net.maritimecloud.util.function.Predicate;

/**
 * A shape has an area
 **/
public abstract class Area implements Element, Message {

    /** A parser of areas. */
    public static final MessageParser<Area> PARSER = new MessageParser<Area>() {
        /** {@inheritDoc} */
        @Override
        public Area parse(MessageReader reader) throws IOException {
            return readFrom(reader);
        }
    };

    /** serialVersionUID. */
    private static final long serialVersionUID = 1L;

    final CoordinateSystem cs = CoordinateSystem.CARTESIAN;

    final MessageSerializable areaWriter() {
        return new Writer();
    }

    public final Predicate<Element> contains() {
        return new Predicate<Element>() {
            public boolean test(Element element) {
                return contains(element);
            }
        };
    }

    /**
     * Returns <tt>true</tt> if the specified element is fully contained in the shape, otherwise <tt>false</tt>.
     *
     * @param element
     *            the element to test
     * @return true if the specified element is fully contained in the shape, otherwise false
     */
    public boolean contains(Element element) {
        throw new UnsupportedOperationException();
    }

    public abstract boolean contains(Position position);

    /** {@inheritDoc} */
    @Override
    public final double distanceTo(Element other, CoordinateSystem system) {
        return requireNonNull(system) == CoordinateSystem.CARTESIAN ? rhumbLineDistanceTo(other)
                : geodesicDistanceTo(other);
    }

    /** {@inheritDoc} */
    @Override
    public double geodesicDistanceTo(Element other) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a bounding box of the area.
     *
     * @return a bounding box of the area
     */
    public abstract BoundingBox getBoundingBox();

    public final CoordinateSystem getCoordinateSystem() {
        return cs;
    }

    /**
     * Returns a random position within the area.
     *
     * @return a random position within the area
     */
    public final Position getRandomPosition() {
        return getRandomPosition(ThreadLocalRandom.current());
    }

    /**
     * Returns a random position within the area using a specified random source.
     *
     * @param random
     *            the random source
     * @return a random position within the area
     */
    public abstract Position getRandomPosition(Random random);

    /** {@inheritDoc} */
    public Area immutable() {
        return this;
    }

    public abstract boolean intersects(Area other);

    @Override
    public double rhumbLineDistanceTo(Element other) {
        throw new UnsupportedOperationException();
    }

    /** Returns a JSON representation of this message */
    public String toJSON() {
        return MessageSerializers.writeToJSON(this);
    }

    public Area unionWith(Area other) {
        return new AreaUnion(this, other);
    }

    public static Area createUnion(Area... areas) {
        Area[] a = areas.clone();
        return new AreaUnion(a);
    }

    static double nextDouble(Random r, double least, double bound) {
        return r.nextDouble() * (bound - least) + least;
    }

    public static Area readFrom(MessageReader r) throws IOException {
        // Circle = 1;
        // Box = 2;
        // Polygon = 3;
        // Union = 4;
        if (r.isNext(1, "circle")) {
            return r.readMessage(1, "circle", Circle.PARSER);
        } else if (r.isNext(2, "box")) {
            return r.readMessage(2, "box", BoundingBox.PARSER);
        } else if (r.isNext(3, "polygon")) {
            return r.readMessage(3, "polygon", BoundingBox.PARSER);
        } else {
            List<AreaUnion> readList = r.readList(4, "areas", AreaUnion.PARSER);
            return new AreaUnion(readList.toArray(new AreaUnion[0]));
        }
    }

    class Writer implements MessageSerializable {

        /** {@inheritDoc} */
        @Override
        public void writeTo(MessageWriter w) throws IOException {
            Area a = Area.this;
            if (a instanceof Circle) {
                w.writeMessage(1, "circle", a);
            } else if (a instanceof BoundingBox) {
                throw new UnsupportedOperationException();
            } else if (a instanceof Polygon) {
                throw new UnsupportedOperationException();
            } else {
                w.writeMessage(4, "areas", a);
            }
        }
    }
}