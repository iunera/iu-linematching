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
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.iunera.publictransport.domain.TransportProductDTO;
import com.iunera.publictransport.domain.route.Route;
import com.iunera.publictransport.domain.stop.PlannedStop;
import com.iunera.publictransport.domain.trip.TripWaypoint;
import com.iunera.publictransport.keys.RideKeysGeneration;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.net.URLEncoder;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class LineDetectorMasterData implements Serializable {

  private static String gtfsInputApi = System.getenv("GTFS_INPUT_API");

  private static String provider = System.getenv("PROVIDER");

  public LineDetectorMasterData(String filename, String routesFile)
      throws JsonParseException, JsonMappingException, IOException {

    if (gtfsInputApi == null) gtfsInputApi = "http://localhost:8080/apiv1/";

    if (provider == null) provider = "20220815_fahrplaene_gesamtdeutschland_gtfs";

    URL jsonUrlLine =
        new URL(
            gtfsInputApi
                + "lines?provider="
                + provider
                + "&agency="
                + URLEncoder.encode("Kahlgrund VG", "UTF-8"));
    URL jsonUrlStops =
        new URL(
            gtfsInputApi
                + "stops?provider="
                + provider
                + "&agency="
                + URLEncoder.encode("Kahlgrund VG", "UTF-8"));
    URL jsonUrlStopsMap =
        new URL(
            gtfsInputApi
                + "stops/all/geotimekeykey_stopids?bucketsize="
                + BUCKETSIZE
                + "&provider="
                + provider
                + "&agency="
                + URLEncoder.encode("Kahlgrund VG", "UTF-8"));

    URL jsonUrl =
        new URL(
            gtfsInputApi
                + "stops/all/geotimekeykey_lineids?bucketsize="
                + BUCKETSIZE
                + "&provider="
                + provider
                + "&agency="
                + URLEncoder.encode("Kahlgrund VG", "UTF-8"));
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);

    Map<String, String[]> obj =
        mapper.readValue(jsonUrl, new TypeReference<Map<String, String[]>>() {});
    System.out.println("mapper.readValue(jsonUrl, new TypeReference<Map<String, String[]>>(){}); ");
    obj.entrySet().stream()
        .forEach(
            e -> {
              for (String lineref : e.getValue()) {
                String key = e.getKey();
                // excluded the lower time bucket, because departures are never early and we match
                // only
                // because of departures
                if (key.contains("type=lower")) continue;
                String line_seq[] = lineref.split("#");
                timeStopGeo_line.put(
                    key.split("#")[1],
                    new LineSequence(line_seq[0], Integer.parseInt(line_seq[1])));
              }
            });
    System.out.println("	obj.entrySet().stream().forEach(e -> {");
    try {

      lineMap = mapper.readValue(jsonUrlLine, new TypeReference<Map<String, Route>>() {});
      System.out.println("mapper.readValue(jsonUrlLine, new TypeReference<Map<String, Line>>() ");

      List<PlannedStop> stops =
          mapper.readValue(jsonUrlStops, new TypeReference<List<PlannedStop>>() {});

      stops.stream()
          .forEach(
              e -> {
                String hash =
                    GeoHash.geoHashStringWithCharacterPrecision(
                        e.latitude, e.longitude, stopprecision);
                geohash_stop.put(hash, e);
              });

      this.iofpt_stop =
          stops.stream()
              .collect(
                  Collectors.toMap(
                      e -> e.IFOPT,
                      e -> e,
                      (oldValue, newValue) -> {
                        return oldValue;
                      }));

      Map<String, String[]> timeStopGeo_stop2 =
          mapper.readValue(jsonUrlStopsMap, new TypeReference<Map<String, String[]>>() {});

      timeStopGeo_stop2.entrySet().stream()
          .forEach(
              e -> {
                List<PlannedStop> stopRefs = new ArrayList<>(1);

                for (String stopref : e.getValue()) {
                  PlannedStop stop = LineDetectorMasterData.this.iofpt_stop.get(stopref);
                  this.timeStopGeo_stop.put(e.getKey(), stop);
                }
              });

    } catch (IOException e1) {
      e1.printStackTrace();
    }
  }

  Multimap<String, LineSequence> timeStopGeo_line =
      MultimapBuilder.hashKeys().hashSetValues().build();

  Map<String, Route> lineMap;

  Multimap<String, PlannedStop> geohash_stop = MultimapBuilder.hashKeys().hashSetValues().build();;

  Map<String, PlannedStop> iofpt_stop;
  Multimap<String, PlannedStop> timeStopGeo_stop =
      MultimapBuilder.hashKeys().hashSetValues().build();

  Multimap<String, Route> stop_lines = MultimapBuilder.hashKeys().hashSetValues().build();

  public static int BUCKETSIZE = 5;

  int stopprecision = 7;

  public Collection<PlannedStop> getNearStops(TripWaypoint value) {
    String currentGeoPositionHash =
        GeoHash.geoHashStringWithCharacterPrecision(
            value.geo_latitude, value.geo_longitude, this.stopprecision);
    return this.geohash_stop.get(currentGeoPositionHash);
  }

  public Map<String, Set<Integer>> getLikliestDepartures(TripWaypoint value, ZoneId zone) {
    LocalTime localtime = ZonedDateTime.ofInstant(value.time, zone).toLocalTime();

    // we need only the middle bucket to check if we have a match
    String nearestbucket =
        RideKeysGeneration.getNearestTimeBucketsKeys(
            RideKeysGeneration.CoordinateFunctionStop,
            value.geo_longitude,
            value.geo_latitude,
            this.stopprecision,
            localtime,
            null,
            TransportProductDTO.BUS,
            RideKeysGeneration.directionUNKNOWN,
            this.BUCKETSIZE)[1];

    return this.timeStopGeo_line.get(nearestbucket).stream()
        .collect(
            Collectors.toMap(
                e -> e.line,
                e -> Sets.newHashSet(e.sequence),
                (a, b) -> {
                  a.addAll(b);
                  return a;
                }));
  }
}
