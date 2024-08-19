package com.iunera.publictransport.enrichment.linedetector;

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

import ch.hsr.geohash.GeoHash;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.iunera.publictransport.domain.TransportProductDTO;
import com.iunera.publictransport.domain.route.Route;
import com.iunera.publictransport.domain.stop.PlannedStop;
import com.iunera.publictransport.domain.trip.TripWaypoint;
import com.iunera.publictransport.keys.RideKeysGeneration;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.flink.api.common.functions.GroupReduceFunction;
import org.apache.flink.util.Collector;
import org.locationtech.jts.geom.Coordinate;

/** Ensure that the final partitioning key key is the vehicle */
public class LineDetector implements GroupReduceFunction<TripWaypoint, TripWaypoint>, Serializable {

  public static void main(String[] args) throws Exception {

    LineDetector lineRide = new LineDetector("", "", ZoneId.of("Europe/Berlin"));
  }

  LineDetectorMasterData lineDetectorMasterData;

  private ZoneId zone;
  private Multimap<String, LocalTime> departureMap =
      MultimapBuilder.treeKeys().arrayListValues(100).build();

  public LineDetector(String filename, String routesFile, ZoneId zone) throws IOException {

    this.zone = zone;

    lineDetectorMasterData = new LineDetectorMasterData(filename, routesFile);
  }

  public void reduceorg(Iterable<TripWaypoint> values, Collector<TripWaypoint> out)
      throws Exception {

    // sort the rides according to time and o not manipulate the original value list
    List<TripWaypoint> valuelist = Lists.newArrayList(values);
    Collections.sort(
        valuelist,
        new Comparator<TripWaypoint>() {
          @Override
          public int compare(TripWaypoint value1, TripWaypoint value2) {
            Instant v1 = value1.time;
            Instant v2 = value2.time;
            return v1.compareTo(v2);
          }
        });

    // the detection state
    LineDetectorState lineDetectorState = new LineDetectorState();

    for (TripWaypoint value : valuelist) {
      // ensure we really work on one vehicle
      if (lineDetectorState.notDetectedLastRideDetails.size() > 0
          && lineDetectorState.notDetectedLastRideDetails.get(
                  lineDetectorState.notDetectedLastRideDetails.size() - 1)
              != null
          && !lineDetectorState
              .notDetectedLastRideDetails
              .get(lineDetectorState.notDetectedLastRideDetails.size() - 1)
              .vehicle
              .vehicle_uniqueId()
              .equals(value.vehicle.vehicle_uniqueId())) {
        throw new IllegalArgumentException("key violation");
      }

      Map<String, Integer> matchScoring = new HashMap<>();
      String detectedLine = null;

      // check for significant movements
      Collection<PlannedStop> nearstops = lineDetectorMasterData.getNearStops(value);
      boolean stopIsNear = nearstops != null && nearstops.size() > 0;

      // used to avoid counting the same stops twice in case of minimal movments
      boolean stopsdiffer = lineDetectorState.stopsEqual(nearstops);
      lineDetectorState.lastStops = nearstops;

      // only keypoints with a stop are included
      if (!stopIsNear) { // || !stopsdiffer) {
        lineDetectorState.skip(value);
        continue;
      }

      Map<String, Set<Integer>> potentiallines =
          lineDetectorMasterData.getLikliestDepartures(value, zone);

      // if there are no matching lines
      if (potentiallines == null || potentiallines.size() == 0) {
        lineDetectorState.skip(value);
        continue;
      }

      for (Entry<String, Set<Integer>> entry : potentiallines.entrySet()) {
        if (entry.getValue().contains(Integer.valueOf(0))) {
          // if there is an index 0- it can be the first stop of a new line
          if (lineDetectorState.detectedLine != null) {
            lineDetectorState.reset();
          }
          // credit the first stop of a line with 2
          matchScoring.merge(entry.getKey(), 2, Integer::sum);
        } else {
          matchScoring.merge(entry.getKey(), 1, Integer::sum);
        }
      }

      // in case the prior detected line is not detected anymore reset the state
      if (lineDetectorState.detectedLine != null
          && !potentiallines.keySet().contains(lineDetectorState.detectedLine)) {
        lineDetectorState.reset();
      }

      // add all prior matches
      for (Entry<String, Integer> line : lineDetectorState.lineDetections.entrySet()) {
        if (matchScoring.containsKey(line.getKey())) {
          // only preserve the lines of the cycle before that are also found now
          matchScoring.merge(line.getKey(), line.getValue(), Integer::sum);
        }
      }

      if (matchScoring.size() == 0) {
        lineDetectorState.skip(value);
        continue;
      }

      if (matchScoring.size() == 1) {
        String line = (String) matchScoring.keySet().toArray()[0];

        if (matchScoring.get(line) > 1) detectedLine = line;

      } else {

        List<Entry<String, Integer>> topvalues =
            matchScoring.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toList());
        // if it is a clear match compared to the second line, we can add the not so
        // good matching lines to the non matching lines
        if ((topvalues.get(0).getValue() > 3 && (topvalues.size() == 1)
            || (topvalues.get(0).getValue() > topvalues.get(1).getValue() + 2))) {
          detectedLine = topvalues.get(0).getKey();

          System.out.println("topvalues selection:" + lineDetectorState.detectedLine);

          value.stop_scheduleStop = getbestStop(value, lineDetectorState.detectedLine);
          if (value.stop_scheduleStop != null) value.stop_name = value.stop_scheduleStop.name;

          if (value.stop_name != null && value.stop_name.contains("Wiesen")) {
            System.out.println(value.stop_name);
          }
        }
      }

      // set the state
      lineDetectorState.detectedLine = detectedLine;
      lineDetectorState.lineDetections = matchScoring;

      if (detectedLine == null) {
        lineDetectorState.skip(value);
        continue;
      }

      Route detectedLineDetails =
          lineDetectorMasterData.lineMap.get(lineDetectorState.detectedLine);
      lineDetectorState.detection++;

      // debug
      System.out.print("matchinglines ");
      for (String s : matchScoring.keySet())
        System.out.print(" " + lineDetectorMasterData.lineMap.get(s).lineLabel + " ");
      System.out.println("detected Line " + detectedLineDetails.lineLabel);

      // what happens if there is no match?

      Coordinate thisrecord = new Coordinate(value.geo_longitude, value.geo_latitude);

      // set all the not detected lines

      for (TripWaypoint olddetails : lineDetectorState.notDetectedLastRideDetails) {

        olddetails.routeName = detectedLineDetails.lineLabel;
        olddetails.routeDetails = detectedLineDetails;

        olddetails.stop_scheduleStop = getbestStop(olddetails, lineDetectorState.detectedLine);
        if (olddetails.stop_scheduleStop != null)
          value.stop_name = "OLD " + olddetails.stop_scheduleStop.name;

        out.collect(olddetails);
      }

      lineDetectorState.notDetectedLastRideDetails.clear();

      value.routeName = detectedLineDetails.lineLabel;

      value.routeDetails = detectedLineDetails;

      value.stop_scheduleStop = getbestStop(value, lineDetectorState.detectedLine);

      if (value.stop_scheduleStop != null) value.stop_name = value.stop_scheduleStop.name;

      if (potentiallines.get(detectedLine).contains(Integer.valueOf(0))) {
        // seems like the beginning of a line
        value.stop_route_Index = 0;
      }

      // emit it asap
      out.collect(value);
      lineDetectorState.nomatch = 0;
    }

    // when it comes to here all line points should be detected
    final String dl = lineDetectorState.detectedLine;
    lineDetectorState.notDetectedLastRideDetails.forEach(
        value -> {
          out.collect(value);
        });
  }

  @Override
  public void reduce(Iterable<TripWaypoint> values, Collector<TripWaypoint> out) throws Exception {}

  private PlannedStop getbestStop(TripWaypoint rideevent, String detectedLine) {

    if (rideevent.time_end != null)
      System.out.println(" to:" + ZonedDateTime.ofInstant(rideevent.time_end, zone).toLocalTime());
    LocalTime localtime = ZonedDateTime.ofInstant(rideevent.time, zone).toLocalTime();
    String nearestbucketOld =
        RideKeysGeneration.getNearestTimeBucketsKeys(
            RideKeysGeneration.CoordinateFunctionStop,
            rideevent.geo_longitude,
            rideevent.geo_latitude,
            lineDetectorMasterData.stopprecision,
            localtime,
            null,
            TransportProductDTO.BUS,
            RideKeysGeneration.directionUNKNOWN,
            lineDetectorMasterData.BUCKETSIZE)[1];

    if (!lineDetectorMasterData.timeStopGeo_stop.containsKey(nearestbucketOld)) {
      System.out.println("no stop bucket contained");
      // return null;
    }
    Collection<PlannedStop> stops = lineDetectorMasterData.timeStopGeo_stop.get(nearestbucketOld);
    // geohash_stop
    stops =
        lineDetectorMasterData.geohash_stop.get(
            GeoHash.geoHashStringWithCharacterPrecision(
                rideevent.geo_latitude,
                rideevent.geo_longitude,
                lineDetectorMasterData.stopprecision));

    // filter for the stops which qualify and which are actually on the line
    List<PlannedStop> qualifyingstops = new ArrayList<>(stops);

    if ((rideevent.i_occupancy_doorEntryExitActivity != null
        && rideevent.i_occupancy_doorEntryExitActivity.size() > 0)) {
      System.out.println("NOSTOP");
    }

    qualifyingstops =
        qualifyingstops.stream()
            .filter(
                e ->
                    e.plannedDepartures != null
                        && e.plannedDepartures.stream()
                                .filter(d -> d.line_id.equals(detectedLine))
                                .collect(Collectors.toList())
                                .size()
                            > 0)
            .collect(Collectors.toList());
    if (rideevent.i_occupancy_doorEntryExitActivity != null)
      System.out.println("Door activity:" + rideevent.geo_latitude + "," + rideevent.geo_longitude);
    if (qualifyingstops.size() > 1) {
      System.out.println("multiple stops detected..." + stops.size());
      stops.forEach(e -> System.out.print(e.name + ","));
      System.out.println(" Eventtype:" + rideevent.event_Type + ":" + rideevent.event_Subtype);
      Coordinate thisrecord = new Coordinate(rideevent.geo_longitude, rideevent.geo_latitude);
      String thisrecordhash =
          GeoHash.geoHashStringWithCharacterPrecision(
              rideevent.geo_latitude,
              rideevent.geo_longitude,
              lineDetectorMasterData.stopprecision);
      qualifyingstops =
          qualifyingstops.stream()
              .filter(
                  e ->
                      GeoHash.geoHashStringWithCharacterPrecision(
                              e.latitude, e.longitude, lineDetectorMasterData.stopprecision)
                          .equals(thisrecordhash))
              .collect(Collectors.toList());
      qualifyingstops.sort(
          new Comparator<PlannedStop>() {

            @Override
            public int compare(PlannedStop o1, PlannedStop o2) {
              return Double.compare(
                  thisrecord.distance(new Coordinate(o1.longitude, o1.latitude)),
                  thisrecord.distance(new Coordinate(o2.longitude, o2.latitude)));
            }
          });
    }

    if (qualifyingstops.isEmpty()) {
      return null;
    }

    PlannedStop value = qualifyingstops.iterator().next();

    return value;
  }
}
