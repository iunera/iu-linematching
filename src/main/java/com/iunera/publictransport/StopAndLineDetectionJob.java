package com.iunera.publictransport;

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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iunera.flink.helpers.windows.EventTimeAndProcessingTimeTriggerSessionWindows;
import com.iunera.publictransport.domain.trip.TripWaypoint;
import com.iunera.publictransport.enrichment.linedetector.GeoTileRideEventAggregator;
import com.iunera.publictransport.enrichment.linedetector.LineDetector;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Properties;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.ProcessingTimeSessionWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer.Semantic;
import org.apache.flink.streaming.connectors.kafka.KafkaSerializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StopAndLineDetectionJob {

  static boolean local = true;

  static String kafkaBroker = System.getenv("KAFKA_BROKER");
  static String inputTopic = System.getenv("INPUT_TOPIC");
  static String consumerGroupId = System.getenv("CONSUMER_GROUP_ID");
  static String outputTopic = System.getenv("OUTPUT_TOPIC");

  static ZoneId timeZone = ZoneId.of("Europe/Berlin");

  // when real time information is processed how long maximum to wait for late events
  // see also the
  static int busPostionGapTimeMinutes = 5;

  private static Logger logger = LoggerFactory.getLogger(StopAndLineDetectionJob.class);

  /** Assumptions for this job: The events are in each partition are in order. */
  public static void main(String[] args) throws Exception {

    if (kafkaBroker == null) kafkaBroker = "localhost:9092";

    if (inputTopic == null)
      inputTopic = "iu-fahrbar-prod-ingest-mgelectronics-flatfilecountdata-v1";

    if (consumerGroupId == null)
      consumerGroupId = "iu-fahrbar-prod-reduce-mgelectronics-groupid-v1ff23cfflffddflgffddffffg";

    if (outputTopic == null) outputTopic = "iu-fahrbar-prod-with-lines";

    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

    // Kafka consumer - reading from Kafka
    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", kafkaBroker);
    properties.setProperty("group.id", consumerGroupId);

    FlinkKafkaConsumer<String> myConsumer =
        new FlinkKafkaConsumer<>(inputTopic, new SimpleStringSchema(), properties);

    myConsumer.setStartFromEarliest();

    DataStream<String> stream = env.addSource(myConsumer);

    // associate the watermarks
    WatermarkStrategy<TripWaypoint> rideEventWMStrategy =
        WatermarkStrategy.<TripWaypoint>forBoundedOutOfOrderness(Duration.ofMillis(10 * 30))
            .withIdleness(Duration.ofMinutes(1))
            .withTimestampAssigner(
                (ev, timestamp) -> {
                  return ev.time.toEpochMilli();
                });

    // now we read the ordered file - this is changed to kafka
    DataStream<TripWaypoint> sortedMgFileEventStream =
        stream.map(
            s -> {
              ObjectMapper om = new ObjectMapper();
              om.registerModule(new JavaTimeModule());
              return om.readValue(s, TripWaypoint.class);
            });

    DataStream<TripWaypoint> consolidatedStopEvents =
        sortedMgFileEventStream

            // NOTE: in case we have an unsorted stream, we could ensure the order by
            // .windowAll(TumblingProcessingTimeWindows.of(Time.seconds(10)))
            // .apply(new
            // ProcessingTimeToEventTimeOrderingWindow()).assignTimestampsAndWatermarks(rideEventWMStrategy)

            .assignTimestampsAndWatermarks(rideEventWMStrategy)
            .filter(e -> e.geo_longitude != null && e.geo_latitude != null)
            // add a geohash to each rideevent
            .map(
                element -> {
                  element.geo_Hash =
                      GeoHash.geoHashStringWithCharacterPrecision(
                          element.geo_latitude, element.geo_longitude, 12);
                  return element;
                })
            // key by the unique bus and the geolocation
            .keyBy(element -> element.vehicle.vehicle_uniqueId())
            .window(
                EventTimeAndProcessingTimeTriggerSessionWindows.withGap(
                    Time.minutes(busPostionGapTimeMinutes), Time.seconds(30)))
            .process(
                new ProcessWindowFunction<TripWaypoint, TripWaypoint, String, TimeWindow>() {

                  // public static final long serialVersionUID = 8766015936072990896L;

                  // is there a contradiction for Event Time session to Process Window Function
                  GeoTileRideEventAggregator aggregator = new GeoTileRideEventAggregator();
                  private int iteration = 0;

                  @Override
                  public void process(
                      String arg0,
                      ProcessWindowFunction<TripWaypoint, TripWaypoint, String, TimeWindow>.Context
                          arg1,
                      Iterable<TripWaypoint> windowedElements,
                      Collector<TripWaypoint> out)
                      throws Exception {

                    logger.debug("WINDOW" + iteration);
                    // here we can check which inputs go into the aggregation check the iterator
                    Iterator<TripWaypoint> iter = windowedElements.iterator();

                    TripWaypoint current = iter.next();

                    String currentgeo =
                        GeoHash.geoHashStringWithCharacterPrecision(
                            current.geo_latitude, current.geo_longitude, 8);
                    String lastgeo = currentgeo;

                    while (iter.hasNext()) {
                      TripWaypoint next = iter.next();
                      currentgeo =
                          GeoHash.geoHashStringWithCharacterPrecision(
                              next.geo_latitude, next.geo_longitude, 8);
                      if (!currentgeo.equals(lastgeo)) {

                        out.collect(current);
                        lastgeo =
                            GeoHash.geoHashStringWithCharacterPrecision(
                                next.geo_latitude, next.geo_longitude, 8);
                        current = next;
                        continue;
                      } else {

                        lastgeo =
                            GeoHash.geoHashStringWithCharacterPrecision(
                                next.geo_latitude, next.geo_longitude, 8);
                        current = aggregator.reduce(current, next);
                      }
                    }
                    iteration++;
                    if (current != null) out.collect(current);
                  }
                });
    // start line detection
    DataStream<TripWaypoint> stream2 =
        consolidatedStopEvents
            .keyBy(e -> e.vehicle.vehicle_uniqueId())
            .window(ProcessingTimeSessionWindows.withGap(Time.minutes(1)))
            .process(
                new ProcessWindowFunction<TripWaypoint, TripWaypoint, String, TimeWindow>() {
                  // private static final long serialVersionUID = 1L;
                  LineDetector ld = new LineDetector(null, null, timeZone);

                  @Override
                  public void process(
                      String arg0,
                      ProcessWindowFunction<TripWaypoint, TripWaypoint, String, TimeWindow>.Context
                          arg1,
                      Iterable<TripWaypoint> arg2,
                      Collector<TripWaypoint> arg3)
                      throws Exception {
                    ld.reduceorg(arg2, arg3);
                  }
                });

    KafkaSerializationSchema<TripWaypoint> schema =
        new KafkaSerializationSchema<TripWaypoint>() {

          private static final long serialVersionUID = -4225343696563271056L;

          @Override
          public ProducerRecord<byte[], byte[]> serialize(TripWaypoint element, Long timestamp) {
            ObjectMapper om = new ObjectMapper();
            om.registerModule(new JavaTimeModule());
            om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            try {
              ProducerRecord<byte[], byte[]> rec =
                  new ProducerRecord<byte[], byte[]>(outputTopic, om.writeValueAsBytes(element));
              return rec;
            } catch (JsonProcessingException e) {
              e.printStackTrace();
            }
            return null;
          }
          //
        };

    FlinkKafkaProducer<TripWaypoint> kafkaProducer =
        new FlinkKafkaProducer<TripWaypoint>(outputTopic, schema, properties, Semantic.NONE);
    stream2.addSink(kafkaProducer);

    env.execute();
  }
}
