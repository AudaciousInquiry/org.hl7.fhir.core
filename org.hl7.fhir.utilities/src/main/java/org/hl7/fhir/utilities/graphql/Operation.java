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


import java.util.ArrayList;
import java.util.List;

public class  Operation {
  public enum OperationType {qglotQuery, qglotMutation}
  
  private String name;
  private Operation.OperationType operationType;
  private List<Selection> selectionSet = new ArrayList<Selection>();
  private List<Variable> variables = new ArrayList<Variable>();
  private List<Directive> directives = new ArrayList<Directive>();
  
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }
  public Operation.OperationType getOperationType() {
    return operationType;
  }
  public void setOperationType(Operation.OperationType operationType) {
    this.operationType = operationType;
  }
  public List<Selection> getSelectionSet() {
    return selectionSet;
  }
  public List<Variable> getVariables() {
    return variables;
  }
  public List<Directive> getDirectives() {
    return directives;
  }

}
