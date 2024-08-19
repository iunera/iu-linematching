# iu-linematching
A stream processor utilizing GTFS data from the GTFS Microservice for stops and lines and then processes a data stream to label the data points for line and stop.
Basically, the matching is based on proximity of geo location and time for multiple stops and their times to estimate the most likely line/route and stop for each data point. The data points are expected to be in the TripWaypoint format.

Order to support large scale parallel processing (imagine to process historic data or a complete country with busses), the event processor is parameterized (see LineDetectorMasterData.java). Once can partition an event stream into geo regions or time intervals and then instantiate a line matching processor for each partition in parallel.

Ultimately, the output are waypoints which are enriched by lines and stops.

# Open Streetmap based line matching results
We tested line association for a few public transport providers by using detailed rout information available in Open Streetmap, by extracting the waypoints of a line form Overpass. The results were very good, because the matching could laso be done for each waypoint and not just for stops. However, not all route data is available in Open Streemap.   
For reference, we added some extraction logic that we used for Overpass in the overpassstops named package that interested parties can have an easier start than us.

# Note
There are also some limitations or features of this detection. Imagine a bus is an hour late and there is already the schedule of the next line. Clearly, this detector would associate it with the schedule of an hour later. 
We decided to code this detection form the passenger perspective, because the 10 am bus running at 11, would make it the 11am bus... Hence, when there is a high frequency this could also happen with 10 minute intervals what could be the case in cities.  
Therefore, one can change the "determination" of the time-geo interval matching by adjusting the BUCKET SIZE in the LineDetectorMasterData. The associated GTFS Microservice supports a dynamic time interval adjustment and then the complete matching runs with different parameters. Generally, we programmed that nearly all parameters can be adjusted via variables that later revisions of this matching can be run with different parameters.

# Further applications
## matching nearest public transport
Theoretically this processor can also be used to match generally all GPS data to the nearest line. E.g. imagine one wants to see which public transport lines are nearest to regular data from a car GPS to determine which public transport runs around the waypoints of the car ride.

## Associating passengers to lines
Another use case is to use the GPS data of a smartphone to match in which line a passenger is currently riding.

# Start Parameters
inputTopic
outputTopic

start class StopAndLineDetectionJob

# JVM
java 17 works when some parameters are set. 
JVM params:

--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED
-Dfile.encoding=UTF-8
-Dstdout.encoding=UTF-8
-Dstderr.encoding=UTF-8
-XX:+ShowCodeDetailsInExceptionMessages com.iunera.publictransport.StopAndLineDetectionJob


# License
[Open Compensation Token License, Version 0.20](https://github.com/open-compensation-token-license/license/blob/main/LICENSE.md)

