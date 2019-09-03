package org.hl7.fhir.utilities.graphql;

/*-
 * #%L
 * org.hl7.fhir.utilities
 * %%
 * Copyright (C) 2014 - 2019 Health Level 7
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


public class NameValue extends Value {
  private String value;

  public NameValue(java.lang.String value) {
    super();
    this.value = value;
  }

  public NameValue(boolean asBoolean) {
    super();
    if (asBoolean) 
      this.value = "true";
    else
      this.value = "false";
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public void write(StringBuilder b, int indent) {
    b.append(value);      
  }

  public boolean isValue(String v) {
    return v.equals(value);      
  }

  public String toString() {
    return value;
  }
}
