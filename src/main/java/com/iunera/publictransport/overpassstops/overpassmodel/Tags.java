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

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.HashMap;
import java.util.Map;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Tags {

  public String name;
  private String tourism;

  @JsonProperty("website")
  private String website;

  private String amenity;

  public String from;
  public String to;
  public String network;
  public String operator;

  private String cuisine;

  @JsonProperty("type")
  public String type;

  @JsonProperty("phone")
  public String phone;

  @JsonProperty("contact:email")
  public String contactEmail;

  @JsonProperty("addr:city")
  public String addressCity;

  @JsonProperty("addr:postcode")
  public String addressPostCode;

  @JsonProperty("addr:street")
  public String addressStreet;

  @JsonProperty("addr:housenumber")
  public String addressHouseNumber;

  @JsonProperty("wheelchair")
  public String wheelchair;

  @JsonProperty("wheelchair:description")
  public String wheelchairDescription;

  @JsonProperty("opening_hours")
  public String openingHours;

  @JsonProperty("internet_access")
  public String internetAccess;

  @JsonProperty("fee")
  public String fee;

  @JsonProperty("ref:IFOPT")
  public String IFOPT;

  public String ref_name;

  private String opening_hours;

  @JsonProperty("opening_hours")
  public String getOpening_hours() {
    return this.opening_hours;
  }

  public void setOpening_hours(String opening_hours) {
    this.opening_hours = opening_hours;
  }

  @JsonProperty("cuisine")
  public String getCuisine() {
    return this.cuisine;
  }

  public void setCuisine(String cuisine) {
    this.cuisine = cuisine;
  }

  @JsonProperty("contact:website")
  private String contactwebsite;

  private Map<String, Object> additionalProperties = new HashMap<String, Object>();
  public String route;

  @JsonProperty("ref")
  public String lineRef;

  public String colour;
  public String color;

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
    builder.append("Tags [name=");
    builder.append(name);
    builder.append(", tourism=");
    builder.append(tourism);
    builder.append(", website=");
    builder.append(website);
    builder.append(", amenity=");
    builder.append(amenity);
    builder.append(", cuisine=");
    builder.append(cuisine);
    builder.append(", type=");
    builder.append(type);
    builder.append(", phone=");
    builder.append(phone);
    builder.append(", contactEmail=");
    builder.append(contactEmail);
    builder.append(", addressCity=");
    builder.append(addressCity);
    builder.append(", addressPostCode=");
    builder.append(addressPostCode);
    builder.append(", addressStreet=");
    builder.append(addressStreet);
    builder.append(", addressHouseNumber=");
    builder.append(addressHouseNumber);
    builder.append(", wheelchair=");
    builder.append(wheelchair);
    builder.append(", wheelchairDescription=");
    builder.append(wheelchairDescription);
    builder.append(", openingHours=");
    builder.append(openingHours);
    builder.append(", internetAccess=");
    builder.append(internetAccess);
    builder.append(", fee=");
    builder.append(fee);
    builder.append(", operator=");
    builder.append(operator);
    builder.append(", opening_hours=");
    builder.append(opening_hours);
    builder.append(", contactwebsite=");
    builder.append(contactwebsite);
    builder.append(", additionalProperties=");
    builder.append(additionalProperties);
    builder.append("]\n");
    return builder.toString();
  }
}
