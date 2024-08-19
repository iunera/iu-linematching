package com.iunera.publictransport.overpassstops;

/*-
 * #%L
 * iu-linematching
 * %%
 * Copyright (C) 2024 Tim Frey, Christian Schmitt
 * %%
 * Licensed under the OPEN COMPENSATION TOKEN LICENSE (the "License").
 *
 * You may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 * <https://github.com/open-compensation-token-license/license/blob/main/LICENSE.md>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @octl.sid: 1b6f7a5d-8dcf-44f1-b03a-77af04433496
 * #L%
 */

import com.iunera.publictransport.domain.TransportProductDTO;
import com.iunera.publictransport.domain.route.Route;
import com.iunera.publictransport.overpassstops.overpassmodel.Element;
import com.iunera.publictransport.overpassstops.overpassmodel.OverpassResponse;
import com.iunera.publictransport.overpassstops.overpassmodel.RawOverpassLine;
import com.iunera.publictransport.overpassstops.overpassmodel.Tags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

public class OverpassResponseConverter {
  public static class node {
    public double latitude;
    public double longitude;
  }

  public static class way {
    public List<String> nodes = new ArrayList<>();
  }

  private GeometryFactory geometryFactory = new GeometryFactory();

  // public List<Stop> convert

  // converts the busstops
  public List<Route> convertLine(OverpassResponse source) {
    Map<String, Coordinate> retrivedplaces = new HashMap<String, Coordinate>();
    Map<String, node> nodes = new HashMap<String, node>();
    Map<String, way> ways = new HashMap<String, way>();

    // the return value
    List<Route> lines = new ArrayList<>();

    //// Stage 1 read the file
    List<RawOverpassLine> rawOverpassLines = new ArrayList<>();

    for (Element elem : source.getElements()) {

      // extract the specific bus line and the references to the nodes and ways that
      // are part of this line
      if (elem.getType().equals("relation")) {

        if (elem.getTags() != null && elem.getTags().name != null) {
          Tags tags = elem.getTags();
          String[] lineRoute = tags.name.split(":", 2);

          String route = lineRoute[0];
          String line = null;
          if (lineRoute.length > 1) {
            line = lineRoute[0];
            route = lineRoute[1];
          } else {
            line = tags.lineRef;
          }

          RawOverpassLine currentLine = new RawOverpassLine();
          rawOverpassLines.add(currentLine);

          currentLine.lineLabel = line;
          currentLine.textRouteDirection = route;
          currentLine.network = tags.network;
          currentLine.operator = tags.operator;
          currentLine.from = tags.from;
          currentLine.to = tags.to;
          currentLine.vehicleType = tags.route;
          currentLine.lineRef = tags.lineRef;
          currentLine.color = tags.colour;
          if (currentLine.color == null) currentLine.color = tags.color;

          elem.getMembers().stream()
              .forEach(
                  value -> {
                    String type = value.getType();
                    if (type.equals("node")) {
                      String role = value.getRole();
                      if (role.equals("stop")) currentLine.stopNodes.add(value.getRef());
                      if (role.equals("platform")) currentLine.platformsNodes.add(value.getRef());
                    }
                    if (type.equals("way")) {
                      String role = value.getRole();
                      if (role.equals("platform")) currentLine.platformWays.add(value.getRef());

                      if (role.equals("")) currentLine.routeWays.add(value.getRef());
                    }
                  });
        }
      } else {
        // no relation
      }

      // get all nodes and ways now that we need to reslove a lines wayspoints

      if (elem.getType().equals("node")) {
        // it is just a waynode- so add it to the
        // hashmap;
        node n = new node();
        n.latitude = elem.getLat();
        n.longitude = elem.getLon();
        nodes.put(elem.getId().toString(), n);
      }

      if (elem.getType().equals("way")) {
        // it is just a waynode- so add it to the
        // hashmap;

        way w = new way();
        w.nodes =
            elem.getNodes().stream().map(value -> value.toString()).collect(Collectors.toList());

        ways.put(elem.getId().toString(), w);
      }
    }

    /// Stage 2: Fill the geopositions
    rawOverpassLines.stream()
        .forEach(
            overpassLine -> {
              // the result object
              // map the basic properties

              Route line = new Route();
              line.color = overpassLine.color;
              line.from = overpassLine.from;
              line.textRouteDirection = overpassLine.textRouteDirection;
              line.to = overpassLine.to;
              line.lineLabel = overpassLine.lineLabel;
              line.lineRef = overpassLine.lineRef;
              line.network = overpassLine.network;
              line.operator = overpassLine.operator;
              line.transportProduct =
                  TransportProductDTO.fromString(overpassLine.vehicleType.toUpperCase());

              line.stopNodes =
                  overpassLine.stopNodes.stream()
                      .map(
                          stop -> {
                            node n = nodes.get(stop);
                            if (n == null) return null;
                            return new Coordinate(n.longitude, n.latitude);
                          })
                      .filter(e -> e != null)
                      .collect(Collectors.toList());

              // map the platforms -
              // TODO: Map the platforms to the stops
              line.platformsNodes =
                  overpassLine.platformsNodes.stream()
                      .map(
                          platform -> {
                            node n = nodes.get(platform);
                            if (n == null) return null;
                            return new Coordinate(n.longitude, n.latitude);
                          })
                      .filter(e -> e != null)
                      .collect(Collectors.toList());

              line.platformWays =
                  overpassLine.platformWays.stream()
                      .map(
                          platformWay -> {
                            way w = ways.get(platformWay);
                            if (w == null) return null;
                            List<Coordinate> platformway =
                                w.nodes.stream()
                                    .map(
                                        platform -> {
                                          node n = nodes.get(platform);
                                          if (n == null) return null;
                                          return new Coordinate(n.longitude, n.latitude);
                                        })
                                    .filter(e -> e != null)
                                    .collect(Collectors.toList());

                            return geometryFactory.createLineString(
                                platformway.toArray(new Coordinate[platformway.size()]));
                          })
                      .collect(Collectors.toList());

              // map the route
              // List<Coordinate> polyLineRoute = new ArrayList<>();
              List<LineString> routeParts =
                  overpassLine.routeWays.stream()
                      .map(
                          routeWays -> {
                            way w = ways.get(routeWays);
                            if (w == null) return null;
                            List<Coordinate> platformway =
                                w.nodes.stream()
                                    .map(
                                        platform -> {
                                          node n = nodes.get(platform);
                                          if (n == null) return null;
                                          return new Coordinate(n.longitude, n.latitude);
                                        })
                                    .collect(Collectors.toList());
                            return geometryFactory.createLineString(
                                platformway.toArray(new Coordinate[platformway.size()]));
                          })
                      .filter(e -> e != null)
                      .collect(Collectors.toList());

              line.route =
                  geometryFactory.createMultiLineString(
                      routeParts.toArray(new LineString[routeParts.size()]));
              if (line.route.isClosed()) {
                System.out.println();
              }

              // overpassLine.stops

              //////////////////////////
              // filter invalid or incomplete lines
              if (line.lineRef == null
                  || line.stopNodes.size() == 0
                  || line.lineLabel == null
                  || line.transportProduct == null) {

                // throw new IllegalArgumentException("reqired parameters are not available");
              } else {
                // only add cleaned lines
                lines.add(line);
              }
            });

    return lines;
  }
}
