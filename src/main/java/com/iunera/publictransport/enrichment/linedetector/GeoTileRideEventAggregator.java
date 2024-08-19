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
import ch.hsr.geohash.WGS84Point;
import com.iunera.publictransport.domain.trip.EntryExitActivity;
import com.iunera.publictransport.domain.trip.EventType;
import com.iunera.publictransport.domain.trip.TripWaypoint;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This aggregator consolidates elements based on their geo tile(geohash) */
public class GeoTileRideEventAggregator implements ReduceFunction<TripWaypoint> {

  private static final long serialVersionUID = -8635066528836060509L;

  Logger logger = LoggerFactory.getLogger(GeoTileRideEventAggregator.class);

  @Override
  public TripWaypoint reduce(TripWaypoint current, TripWaypoint next) throws Exception {

    if (next == null) {
      return current;
    }
    if (current == null) {
      return next;
    }

    Instant start = current.time;
    Instant end = current.time;

    if (next.time.isBefore(start)) start = next.time;

    if (end.isBefore(next.time)) end = next.time;

    if (current.time_end != null && end.isBefore(current.time_end)) end = current.time;

    if (next.time_end != null && end.isBefore(next.time_end)) end = next.time;

    next.time = start;
    next.time_end = end;

    if (next.i_occupancy_doorEntryExitActivity == null)
      next.i_occupancy_doorEntryExitActivity = new HashMap<>();

    // add the passengers of the second event to the consolidated event value 1
    if (current.i_occupancy_doorEntryExitActivity != null)
      for (Map.Entry<String, EntryExitActivity> e :
          current.i_occupancy_doorEntryExitActivity.entrySet()) {
        EntryExitActivity res = new EntryExitActivity();
        if (next.i_occupancy_doorEntryExitActivity.containsKey(e.getKey())) {
          res = next.i_occupancy_doorEntryExitActivity.get(e.getKey());
        }
        // now we merge value1 and original
        res.boarding = res.boarding + e.getValue().boarding;
        res.exiting = res.exiting + e.getValue().exiting;
        next.i_occupancy_doorEntryExitActivity.put(e.getKey(), res);
      }

    // if one event is another one then a stopevent the stopevent shall prevail
    // imagine a bus driving just a bit or turning the ignition on and then another
    // passenger hops into the bus
    // - this shall still be counted as a stop
    if (next.event_Type.equals(EventType.PLANNEDSTOP)
        || current.event_Type.equals(EventType.PLANNEDSTOP)) {
      next.event_Type = EventType.PLANNEDSTOP;
    } else {
    }

    next.stop_name =
        Stream.of(next.stop_name, current.stop_name)
            .filter(s -> s != null && !s.isEmpty())
            .collect(Collectors.joining(","));

    // erase the properties which shall not exist in the reduced event anymore
    next.event_Subtype = null;
    next.meta_ingestionPartition = null;

    String geohashvalue1 = next.geo_Hash;
    if (geohashvalue1 == null)
      geohashvalue1 =
          GeoHash.geoHashStringWithCharacterPrecision(next.geo_latitude, next.geo_longitude, 12);

    String geohashvalue2 = current.geo_Hash;
    if (geohashvalue2 == null)
      geohashvalue2 =
          GeoHash.geoHashStringWithCharacterPrecision(
              current.geo_latitude, current.geo_longitude, 12);

    // set the geohash that it contains both reduced points
    next.geo_Hash = charactermatches(geohashvalue2, geohashvalue1);

    // get the center of the geohash and use this as coordinates
    // WGS84Point
    WGS84Point point = GeoHash.fromGeohashString(next.geo_Hash).getBoundingBoxCenter();
    next.geo_longitude = point.getLongitude();
    next.geo_latitude = point.getLatitude();

    return next;
  }

  private static String charactermatches(String a, String b) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < a.length(); i++) {
      char ca = a.charAt(i);

      if (b.length() == i) break;

      char cb = b.charAt(i);
      if (ca == cb) sb.append(ca);
      else break;
    }
    return sb.toString();
  }
}
