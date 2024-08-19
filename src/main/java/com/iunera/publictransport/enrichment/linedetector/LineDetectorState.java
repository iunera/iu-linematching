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

import com.iunera.publictransport.domain.stop.PlannedStop;
import com.iunera.publictransport.domain.trip.TripWaypoint;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** */
public class LineDetectorState implements Serializable {
  // the line detections in prior cycles with votes which line gets detected most
  // often
  Map<String, Integer> lineDetections = new HashMap<>();

  // the list of the last non line detected elements
  public List<TripWaypoint> notDetectedLastRideDetails = new ArrayList<>();
  // the detected line

  // how many iterations there were without a concrete matched line
  int nomatch = 0;
  // the value of the stop of the movement before

  // when there is a line detected it is listed as detected line
  String detectedLine = null;
  int lastDetectedLineSequence = 0;
  Collection<PlannedStop> lastStops = null;

  int detection = 0;

  int lineinvalid = 0;
  String lastgeoHash = "";

  public boolean stopsEqual(Collection<PlannedStop> stops) {

    return (this.lastStops == null && stops != null)
        || (this.lastStops != null && stops == null)
        || new HashSet<>(this.lastStops).equals(new HashSet<>(stops));
  }

  // resets the detection state
  public void skip(TripWaypoint currentvalue) {
    this.nomatch++;
    this.notDetectedLastRideDetails.add(currentvalue);
  }

  public void reset() {
    this.detectedLine = null;
    // this.notDetectedLastRideDetails.add(this.lastvalue);
    this.detection = 0;
    // lineValid = false;
    this.lineDetections.clear();
  }
}
