package org.hl7.fhir.r5.utils;

/*-
 * #%L
 * org.hl7.fhir.r5
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


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.utilities.Utilities;

public class Unbundler {

  public static void main(String[] args) throws Exception {
    unbundle(args[0]);
  }

  private static void unbundle(String src) throws FHIRFormatError, FileNotFoundException, IOException {
    String folder = Utilities.getDirectoryForFile(src);
    Bundle bnd = (Bundle) new JsonParser().parse(new FileInputStream(src));
    for (BundleEntryComponent be : bnd.getEntry()) {
      Resource r = be.getResource();
      if (r != null) {
        String tgt = Utilities.path(folder, r.fhirType()+"-"+r.getId()+".json");
        new JsonParser().compose(new FileOutputStream(tgt), r);
      }
    }
  }

}
