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

import java.util.HashMap;
import java.util.Map;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Members {

  private String type;
  private String role;
  private String ref;

  private Map<String, Object> additionalProperties = new HashMap<String, Object>();

  public String getType() {
    return this.type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getRef() {
    return this.ref;
  }

  public void setRef(String ref) {
    this.ref = ref;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getRole() {
    return this.role;
  }

  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  public void setAdditionalProperties(String name, Object value) {
    this.additionalProperties.put(name, value);
  }
}
