package com.iunera.publictransport.overpassstops.overpassmodel;

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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonAnyGetter;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonAnySetter;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.ALWAYS)
public class Element {

  @JsonProperty("type")
  private String type;

  @JsonProperty("id")
  private BigInteger id;

  @JsonProperty("lat")
  private Double lat;

  @JsonProperty("lon")
  private Double lon;

  @JsonProperty("tags")
  private Tags tags;

  @JsonProperty("nodes")
  private List<BigInteger> nodes = new ArrayList<BigInteger>();

  @JsonProperty("members")
  private List<Members> members = new ArrayList<Members>();

  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  @JsonProperty("type")
  public String getType() {
    return this.type;
  }

  @JsonProperty("type")
  public void setType(String type) {
    this.type = type;
  }

  @JsonProperty("id")
  public BigInteger getId() {
    return this.id;
  }

  @JsonProperty("id")
  public void setId(BigInteger id) {
    this.id = id;
  }

  @JsonProperty("lat")
  public Double getLat() {
    return this.lat;
  }

  @JsonProperty("lat")
  public void setLat(Double lat) {
    this.lat = lat;
  }

  @JsonProperty("lon")
  public Double getLon() {
    return this.lon;
  }

  @JsonProperty("lon")
  public void setLon(Double lon) {
    this.lon = lon;
  }

  @JsonProperty("tags")
  public Tags getTags() {
    return this.tags;
  }

  @JsonProperty("tags")
  public void setTags(Tags tags) {
    this.tags = tags;
  }

  @JsonProperty("nodes")
  public List<BigInteger> getNodes() {
    return this.nodes;
  }

  @JsonProperty("members")
  public void setMembers(List<Members> members) {
    this.members = members;
  }

  @JsonProperty("members")
  public List<Members> getMembers() {
    return this.members;
  }

  @JsonProperty("nodes")
  public void setNodes(List<BigInteger> nodes) {
    this.nodes = nodes;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperties(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Element [type=");
    builder.append(type);
    builder.append(", id=");
    builder.append(id);
    builder.append(", lat=");
    builder.append(lat);
    builder.append(", lon=");
    builder.append(lon);
    builder.append(", tags=");
    builder.append(tags);
    builder.append(", nodes=");
    builder.append(nodes);
    builder.append(", members=");
    builder.append(members);
    builder.append(", additionalProperties=");
    builder.append(additionalProperties);
    builder.append("]\n");
    return builder.toString();
  }
}
