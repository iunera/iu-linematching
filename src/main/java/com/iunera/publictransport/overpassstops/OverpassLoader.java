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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iunera.publictransport.domain.stop.PlannedStop;
import com.iunera.publictransport.overpassstops.overpassmodel.Element;
import com.iunera.publictransport.overpassstops.overpassmodel.OverpassResponse;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OverpassLoader {

  // just for quick testing
  public static void main(String[] args) throws IOException {
    // the stops names are duplicate because of their direction - right now we
    // ignore that and do not link the stops
    // so it is ok to have two stops with the same name
    List<PlannedStop> o = getStops(exampleQuery);
    for (PlannedStop s : o) System.out.println(s.name);
  }

  public static String exampleQuery =
      "[out:json][timeout:25];(node[\"highway\"=\"bus_stop\"](LATMIN,LONMIN,LATMAX,LONMAX);node[\"public_transport\"=\"platform\"][\"bus\"=\"yes\"](48.648335117999,11.586456298828,49.28124430625,12.745513916016););out;>;out skel qt;";

  // use this method in the mapper and create geohases via geohash (LATITUDe-
  // longitude)
  public static List<PlannedStop> getStops(String query) throws IOException {

    URL jsonUrl = new URL("https://overpass-api.de/api/interpreter?data=" + query);

    ObjectMapper mapper = new ObjectMapper();

    OverpassResponse response = mapper.readValue(jsonUrl, OverpassResponse.class);
    List<PlannedStop> stops = new ArrayList<>(response.getElements().size());

    Set<String> seennames = new HashSet<>();
    // skip the ones without a name
    for (Element e : response.getElements()) {
      if (e.getTags().name == null || !e.getTags().name.isEmpty()) {
        String name = e.getTags().ref_name;
        if (name == null || name.isEmpty()) name = e.getTags().name;

        String direction = "a";
        if (seennames.contains(name)) {

          direction = "b";
        } else {
          seennames.add(name);
        }
        PlannedStop stop =
            new PlannedStop(
                e.getTags().name,
                e.getLon(),
                e.getLat(),
                e.getTags().IFOPT,
                direction,
                e.getTags().ref_name);

        stops.add(stop);
      }
    }
    return stops;
  }
}
