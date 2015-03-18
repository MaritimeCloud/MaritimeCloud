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
package net.maritimecloud.mms.server.endpoints;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import net.maritimecloud.internal.mms.messages.services.AbstractServices;
import net.maritimecloud.mms.server.connectionold.ServerConnection;
import net.maritimecloud.mms.server.targets.Target;
import net.maritimecloud.mms.server.targets.TargetManager;
import net.maritimecloud.net.MessageHeader;
import net.maritimecloud.util.geometry.Area;
import net.maritimecloud.util.geometry.Position;
import net.maritimecloud.util.geometry.PositionTime;

/**
 * Manages services for all connected targets.
 *
 * @author Kasper Nielsen
 */
public class ServerServices extends AbstractServices {

    final TargetManager tracker;

    // Metrics
    final Meter endpointRegistrationsMeter;
    final Meter serviceLocatesMeter;

    public ServerServices(TargetManager tm, MetricRegistry metrics) {
        this.tracker = requireNonNull(tm);

        endpointRegistrationsMeter = metrics.meter("endpointRegistrations");
        serviceLocatesMeter = metrics.meter("serviceLocates");
    }

    /**
     * Finds services in proximity to the specified target.
     *
     * @param target
     *            the target that is trying to find the service
     * @param request
     *            the find service request
     * @return a sorted list of the targets that was found sorted by distance to the target doing the search
     */
    List<Entry<Target, PositionTime>> findServices(Target target, String endpointName, Position pos, double m, int max) {
        double meters = m <= 0 ? Double.MAX_VALUE : m;

        final ConcurrentHashMap<Target, PositionTime> map = new ConcurrentHashMap<>();

        if (pos == null) {
            tracker.forEachTarget((tt, r) -> {
                if (tt.getEndpointManager().hasService(endpointName)) {
                    map.put(tt, r);
                }
            });
        } else {
            // Find all services with the area
            tracker.forEachTarget((tt, pt) -> {
                if (tt.getEndpointManager().hasService(endpointName)) {
                    if (pt.geodesicDistanceTo(pos) <= meters) {
                        map.put(tt, pt);
                    }
                }
            });
        }
        // We remove ourself
        map.remove(target);

        // Sort by distance
        List<Entry<Target, PositionTime>> l = new ArrayList<>(map.entrySet());
        if (pos != null) {
            Collections.sort(
                    l,
                    (o1, o2) -> Double.compare(o1.getValue().geodesicDistanceTo(pos),
                            o2.getValue().geodesicDistanceTo(pos)));
        }

        // If we have a maximum number of results, filter the list
        if (l.size() > max) {
            l = new ArrayList<>(l.subList(0, max));
        }

        return l;
    }

    /** {@inheritDoc} */
    @Override
    protected List<String> locate(MessageHeader header, String endpointName, Integer meters, Integer max) {
        ServerConnection con = ServerEndpointManager.connection(header);
        List<Entry<Target, PositionTime>> findService = findServices(con.getTarget(), endpointName,
                header.getSenderPosition(), meters, max);
        List<String> result = new ArrayList<>();
        for (Entry<Target, PositionTime> e : findService) {
            result.add(e.getKey().getId().toString());
        }

        // Update metrics
        serviceLocatesMeter.mark();

        return result;
    }

    /** {@inheritDoc} */
    @Override
    protected void registerEndpoint(MessageHeader header, String endpointName) {
        ServerConnection con = ServerEndpointManager.connection(header);
        TargetEndpointManager services = con.getTarget().getEndpointManager();
        services.registerEndpoint(endpointName);

        // Update metrics
        endpointRegistrationsMeter.mark();
    }

    /** {@inheritDoc} */
    @Override
    protected void subscribe(MessageHeader header, List<String> name, Area area) {}

    /** {@inheritDoc} */
    @Override
    protected void unregisterEndpoint(MessageHeader header, String endpointName) {}
}
