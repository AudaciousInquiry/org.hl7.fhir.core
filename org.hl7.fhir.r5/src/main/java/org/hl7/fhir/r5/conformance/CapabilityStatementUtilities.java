package org.hl7.fhir.r5.conformance;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hl7.fhir.exceptions.DefinitionException;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.model.CapabilityStatement;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestResourceSearchParamComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.CapabilityStatementRestSecurityComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.ResourceInteractionComponent;
import org.hl7.fhir.r5.model.CapabilityStatement.RestfulCapabilityMode;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Element;
import org.hl7.fhir.r5.model.StructureDefinition;
import org.hl7.fhir.utilities.CommaSeparatedStringBuilder;
import org.hl7.fhir.utilities.MarkDownProcessor;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.MarkDownProcessor.Dialect;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity;
import org.hl7.fhir.utilities.validation.ValidationMessage.IssueType;
import org.hl7.fhir.utilities.validation.ValidationMessage.Source;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlComposer;
import org.hl7.fhir.utilities.xhtml.XhtmlDocument;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;
import org.hl7.fhir.utilities.xhtml.XhtmlParser;

public class CapabilityStatementUtilities {

  private IWorkerContext context;
  private String selfName;
  private String otherName;
  private List<ValidationMessage> output;
  private XhtmlDocument html;
  private MarkDownProcessor markdown = new MarkDownProcessor(Dialect.COMMON_MARK);
  private String folder;
//  private String css; 

  public CapabilityStatementUtilities(SimpleWorkerContext context, String folder) throws IOException {
    super();
    this.context = context;
    this.folder = folder;
    String f = Utilities.path(folder, "comparison.zip");
    download("http://fhir.org/archive/comparison.zip", f);
    unzip(f, folder);
  }
  
  /**
   * Size of the buffer to read/write data
   */
  private static final int BUFFER_SIZE = 4096;
  /**
   * Extracts a zip file specified by the zipFilePath to a directory specified by
   * destDirectory (will be created if does not exists)
   * @param zipFilePath
   * @param destDirectory
   * @throws IOException
   */
  public void unzip(String zipFilePath, String destDirectory) throws IOException {
      File destDir = new File(destDirectory);
      if (!destDir.exists()) {
          destDir.mkdir();
      }
      ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
      ZipEntry entry = zipIn.getNextEntry();
      // iterates over entries in the zip file
      while (entry != null) {
          String filePath = destDirectory + File.separator + entry.getName();
          if (!entry.isDirectory()) {
              // if the entry is a file, extracts it
              extractFile(zipIn, filePath);
          } else {
              // if the entry is a directory, make the directory
              File dir = new File(filePath);
              dir.mkdir();
          }
          zipIn.closeEntry();
          entry = zipIn.getNextEntry();
      }
      zipIn.close();
  }
  /**
   * Extracts a zip entry (file entry)
   * @param zipIn
   * @param filePath
   * @throws IOException
   */
  private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
      BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
      byte[] bytesIn = new byte[BUFFER_SIZE];
      int read = 0;
      while ((read = zipIn.read(bytesIn)) != -1) {
          bos.write(bytesIn, 0, read);
      }
      bos.close();
  }

  public void saveToFile() throws IOException {
    String s = new XhtmlComposer(true, true).compose(html);
    TextFile.stringToFile(s, Utilities.path(folder, "index.html"));
  }
  
  /**
   * Compares 2 capability statements to see if self is a subset of other
   * 
   * e.g. is a system implementing "+otherName+" also an implementation of self?
   * 
   * the output is a series of messages identifying ways in which it is not, 
   * or warning messages where the algorithm is unable to determine the 
   * relationship
   * 
   * Note that there are aspects of this question that are not computable.
   * the 
   * 
   * @param self
   * @param other
   * @return
   * @throws IOException 
   * @throws FHIRFormatError 
   * @throws DefinitionException 
   */
  public List<ValidationMessage> isCompatible(String selfName, String otherName, CapabilityStatement self,  CapabilityStatement other) throws DefinitionException, FHIRFormatError, IOException {
    this.selfName = selfName;
    this.otherName = otherName;
    this.output = new ArrayList<>();
    XhtmlNode x = startHtml();

    information(x, IssueType.INVARIANT, self.getUrl(), "Comparing "+selfName+" to "+otherName+", to see if a server that implements "+otherName+" also implements "+selfName+"");
    information(x, IssueType.INVARIANT, self.getUrl(), "  "+selfName+": "+self.getUrl()+"|"+self.getVersion());
    information(x, IssueType.INVARIANT, self.getUrl(), "  "+otherName+": "+other.getUrl()+"|"+other.getVersion());

    if (self.getRest().size() != 1 || self.getRestFirstRep().getMode() != RestfulCapabilityMode.SERVER)
      fatal(x, IssueType.INVARIANT, self.getUrl()+"#rest", "The CapabilityStatement Comparison tool can only compare CapabilityStatements with a single server component");
    if (other.getRest().size() != 1 || other.getRestFirstRep().getMode() != RestfulCapabilityMode.SERVER)
      fatal(x, IssueType.INVARIANT, other.getUrl()+"#rest", "The CapabilityStatement Comparison tool can only compare CapabilityStatements with a single server component");

    if (self.getRest().size() == 1 && other.getRest().size() == 1) {
      XhtmlNode tbl = startTable(x, self, other);
      compareRest(tbl, self.getUrl(), self.getRest().get(0), other.getRest().get(0)); 
    }
    if (folder != null)
      saveToFile();
    return output;
  }

  private void compareRest(XhtmlNode tbl, String path, CapabilityStatementRestComponent self, CapabilityStatementRestComponent other) throws DefinitionException, FHIRFormatError, IOException {
    compareSecurity(tbl, path, self, other);

    // check resources
    List<CapabilityStatementRestResourceComponent> ol = new ArrayList<>();
    List<CapabilityStatementRestResourceComponent> olr = new ArrayList<>();
    ol.addAll(other.getResource());
    for (CapabilityStatementRestResourceComponent r : self.getResource()) {
      CapabilityStatementRestResourceComponent o = null;
      for (CapabilityStatementRestResourceComponent t : ol) {
        if (t.getType().equals(r.getType())) {
          o = t;
          break;
        }
      }
      XhtmlNode tr = tbl.tr();
      tr.style("background-color: #dddddd");
      tr.td().b().addText(r.getType());
      tr.td().tx("Present");
     

      if (o == null) {
        XhtmlNode p = tr.td().para("Absent");
        XhtmlNode td = tr.td();
        String s = getConfStatus(r);
        if (Utilities.existsInList(s,  "SHALL", "SHOULD")) {
          error(td, IssueType.NOTFOUND, path+".resource.where(type = '"+r.getType()+"')", selfName+" specifies the resource "+r.getType()+" as "+s+" but "+otherName+" does not cover it");
          p.style("background-color: #ffe6e6; border: 1px solid #ff1a1a; margin-width: 10px");
        }

        tr = tbl.tr();
        tr.td().para().tx(XhtmlNode.NBSP+" - Conformance");
        genConf(tr.td().para(), r, null);
        tr.td().style("background-color: #eeeeee");
        tr.td();

        tr = tbl.tr();
        tr.td().para().tx(XhtmlNode.NBSP+" - Profile");
        genProfile(tr.td().para(), r, null);
        tr.td().style("background-color: #eeeeee");
        tr.td();

        tr = tbl.tr();
        tr.td().para().tx(XhtmlNode.NBSP+" - Interactions");
        genInt(tr.td(), r, null, false);
        tr.td().style("background-color: #eeeeee");
        tr.td();

        tr = tbl.tr();
        tr.td().para().tx(XhtmlNode.NBSP+" - Search Parameters");
        genSP(tr.td(), r, null, false);
        tr.td().style("background-color: #eeeeee");
        tr.td();

      } else { 
        olr.add(o);
        tr.td().tx("Present");
        tr.td().nbsp();
        compareResource(path+".resource.where(type = '"+r.getType()+"')", r, o, tbl);
      }
    }
    for (CapabilityStatementRestResourceComponent t : ol) {
      XhtmlNode tr = tbl.tr();
      if (!olr.contains(t)) {
        tr.td().addText(t.getType());
        XhtmlNode td = tr.td();
        td.style("background-color: #eeeeee").para("Absent");
        td = tr.td();
        genConf(td, t, null);
        genProfile(td, t, null);
        genInt(td, t, null, false);
        genSP(td, t, null, false);
        if (isProhibited(t)) {
          error(td, IssueType.INVARIANT, path+".resource", selfName+" does not specify the resource "+t.getType()+" but "+otherName+" prohibits it");
        }
      }    
    }
    // check interactions
    // check search parameters
    // check operation
    // check compartments
  }

  private void genConf(XhtmlNode x, CapabilityStatementRestResourceComponent r, CapabilityStatementRestResourceComponent other) {
    String s = getConfStatus(r);
    String so = other != null ? getConfStatus(other) : null;
    x.add(same(s == null ? "(not specified)" : s, (s == null && so == null) || (s != null && s.equals(so))));   
  }

  private void genProfile(XhtmlNode x, CapabilityStatementRestResourceComponent r, CapabilityStatementRestResourceComponent other) {
    String s = getProfile(r);
    if (s == null)
      s = "(not specified)";
    String so = other == null ? null : getProfile(other) == null ? getProfile(other) : "(not specified)";
    x.add(same(s, (s == null && so == null) || (s != null && s.equals(so))));
  }
  
  private String getProfile(CapabilityStatementRestResourceComponent r) {
    if (r.hasSupportedProfile() && r.getSupportedProfile().size() == 1)
      return r.getSupportedProfile().get(0).asStringValue();
    return r.getProfile();
  }

  private void genInt(XhtmlNode td, CapabilityStatementRestResourceComponent r, CapabilityStatementRestResourceComponent other, boolean errorIfNoMatch) {
    boolean first = true;
    for (ResourceInteractionComponent i : r.getInteraction()) {
      if (first) first = false; else td.tx(", ");
      if (exists(other, i)) {
        td.code().span("background-color: #bbff99; border: 1px solid #44cc00; margin-width: 10px", null).tx(i.getCode().toCode());
      } else if (errorIfNoMatch){
        td.code().span("background-color: #ffe6e6; border: 1px solid #ff1a1a; margin-width: 10px", null).tx(i.getCode().toCode());
      } else {
        td.code(i.getCode().toCode());
      }
    }    
  }

  private boolean exists(CapabilityStatementRestResourceComponent other, ResourceInteractionComponent i) {
    if (other == null)
      return false;
    for (ResourceInteractionComponent t : other.getInteraction())
      if (t.getCode().equals(i.getCode()))
        return true;
    return false;
  }

  private void genSP(XhtmlNode td, CapabilityStatementRestResourceComponent r, CapabilityStatementRestResourceComponent other, boolean errorIfNoMatch) {
    boolean first = true;
    for (CapabilityStatementRestResourceSearchParamComponent i : r.getSearchParam()) {
      if (first) first = false; else td.tx(", ");
      if (exists(other, i)) {
        td.code().span("background-color: #bbff99; border: 1px solid #44cc00; margin-width: 10px", null).tx(i.getName());
      } else if (errorIfNoMatch){
        td.code().span("background-color: #ffe6e6; border: 1px solid #ff1a1a; margin-width: 10px", null).tx(i.getName());
      } else {
        td.code(i.getName());
      }
    }    
  }

  private boolean exists(CapabilityStatementRestResourceComponent other, CapabilityStatementRestResourceSearchParamComponent i) {
    if (other == null)
      return false;
    for (CapabilityStatementRestResourceSearchParamComponent t : other.getSearchParam())
      if (t.getName().equals(i.getName()))
        return true;
    return false;
  }

  public void compareSecurity(XhtmlNode tbl, String path, CapabilityStatementRestComponent self, CapabilityStatementRestComponent other) {
    XhtmlNode tr = tbl.tr();
    tr.td().b().addText("Security");
    tr.td().para(gen(self.getSecurity()));
    tr.td().para(gen(other.getSecurity()));
    XhtmlNode td = tr.td();
    
    if (self.hasSecurity() && !other.hasSecurity()) 
      error(td, IssueType.CONFLICT, path+".security", selfName+" specifies some security requirements ("+gen(self.getSecurity())+") but "+otherName+" doesn't");
    else if (!self.hasSecurity() && other.hasSecurity()) 
      error(td, IssueType.CONFLICT, path+".security", selfName+" does not specify security requirements but "+otherName+" does ("+gen(self.getSecurity())+")");
    else if (self.hasSecurity() && other.hasSecurity())
      compareSecurity(td, path+".security", self.getSecurity(), other.getSecurity());
  }  

  private void compareResource(String path, CapabilityStatementRestResourceComponent self, CapabilityStatementRestResourceComponent other, XhtmlNode tbl) throws DefinitionException, FHIRFormatError, IOException {
    XhtmlNode tr = tbl.tr();
    tr.td().para().tx(XhtmlNode.NBSP+" - Conformance");
    genConf(tr.td(), self, other);
    genConf(tr.td(), other, self);
    tr.td().nbsp();

    tr = tbl.tr();
    tr.td().para().tx(XhtmlNode.NBSP+" - Profile");
    genProfile(tr.td(), self, other);
    genProfile(tr.td(), other, self);
    compareProfiles(tr.td(), path, getProfile(self), getProfile(other), self.getType());
    
    // compare the interactions
    compareResourceInteractions(path, self, other, tbl);
    compareResourceSearchParams(path, self, other, tbl);
    // compare the search parameters
    // compare the operations

    // compare the profile?

  }

  private void compareProfiles(XhtmlNode td, String path, String urlL, String urlR, String type) throws DefinitionException, FHIRFormatError, IOException {
    if (urlL == null) {
      urlL = "http://hl7.org/fhir/StructureDefinition/"+type;
    }
    if (urlR == null) {
        urlR = "http://hl7.org/fhir/StructureDefinition/"+type; 
    }
    StructureDefinition sdL = context.fetchResource(StructureDefinition.class, urlL);
    StructureDefinition sdR = context.fetchResource(StructureDefinition.class, urlR);
    if (sdL == null)
      error(td, IssueType.NOTFOUND, path, "Unable to resolve "+urlL);
    if (sdR == null)
      error(td, IssueType.NOTFOUND, path, "Unable to resolve "+urlR);
      
    if (sdL != null && sdR != null && sdL != sdR) {
      // ok they are different... 
      if (sdR.getUrl().equals(sdL.getBaseDefinition())) {
        information(td, null, path, "The profile specified by "+selfName+" is inherited from the profile specified by "+otherName);
      } else if (folder != null) {
        try {
          ProfileComparer pc = new ProfileComparer(context);
          pc.setId("api-ep."+type);
          pc.setTitle("Comparison - "+selfName+" vs "+otherName);
          pc.setLeftName(selfName+": "+sdL.present());
          pc.setLeftLink(sdL.getUserString("path"));
          pc.setRightName(otherName+": "+sdR.present());
          pc.setRightLink(sdR.getUserString("path"));
          pc.compareProfiles(sdL, sdR);
          pc.generate(folder);
          td.ah(pc.getId()+".html").tx("Comparison...");
          td.tx(pc.getErrCount()+" "+Utilities.pluralize("error", pc.getErrCount()));
        } catch (Exception e) {
          e.printStackTrace();
          error(td, IssueType.EXCEPTION, path, "Error comparing profiles: "+e.getMessage());
        }
      } else {
        information(td, null, path, "Use the validator to compare the profiles");
      }
    }
  }

  private void compareResourceInteractions(String path, CapabilityStatementRestResourceComponent self, CapabilityStatementRestResourceComponent other, XhtmlNode tbl) {
    XhtmlNode tr = tbl.tr();
    tr.td().para().tx(XhtmlNode.NBSP+" - Interactions");
    genInt(tr.td(), self, other, true);
    genInt(tr.td(), other, self, false);
    XhtmlNode td = tr.td();
    List<ResourceInteractionComponent> ol = new ArrayList<>();
    List<ResourceInteractionComponent> olr = new ArrayList<>();
    ol.addAll(other.getInteraction());
    for (ResourceInteractionComponent r : self.getInteraction()) {
      ResourceInteractionComponent o = null;
      for (ResourceInteractionComponent t : ol) {
        if (t.getCode().equals(r.getCode())) {
          o = t;
          break;
        }
      }
      if (o == null) {
        error(td, IssueType.NOTFOUND, path+".interaction.where(code = '"+r.getCode()+"')", selfName+" specifies the interaction "+r.getCode()+" but "+otherName+" does not");        
      } else { 
        olr.add(o);
      }
    }
    for (ResourceInteractionComponent t : ol) {
      if (!olr.contains(t) && isProhibited(t))
        error(td, IssueType.CONFLICT, path+".interaction", selfName+" does not specify the interaction "+t.getCode()+" but "+otherName+" prohibits it");        
    }        
  }

  private void compareResourceSearchParams(String path, CapabilityStatementRestResourceComponent self, CapabilityStatementRestResourceComponent other, XhtmlNode tbl) {
    XhtmlNode tr = tbl.tr();
    tr.td().para().tx(XhtmlNode.NBSP+" - Search Params");
    genSP(tr.td(), self, other, true);
    genSP(tr.td(), other, self, false);
    XhtmlNode td = tr.td();

    List<CapabilityStatementRestResourceSearchParamComponent> ol = new ArrayList<>();
    List<CapabilityStatementRestResourceSearchParamComponent> olr = new ArrayList<>();
    ol.addAll(other.getSearchParam());
    for (CapabilityStatementRestResourceSearchParamComponent r : self.getSearchParam()) {
      CapabilityStatementRestResourceSearchParamComponent o = null;
      for (CapabilityStatementRestResourceSearchParamComponent t : ol) {
        if (t.getName().equals(r.getName())) {
          o = t;
          break;
        }
      }
      if (o == null) {
        error(td, IssueType.NOTFOUND, path+".searchParam.where(name = '"+r.getName()+"')", selfName+" specifies the search parameter "+r.getName()+" but "+otherName+" does not");        
      } else { 
        olr.add(o);
      }
    }
    for (CapabilityStatementRestResourceSearchParamComponent t : ol) {
      if (!olr.contains(t) && isProhibited(t))
        error(td, IssueType.CONFLICT, path+"", selfName+" does not specify the search parameter "+t.getName()+" but "+otherName+" prohibits it");        
    }    

  }

  private String getConfStatus(Element t) {
    return t.hasExtension("http://hl7.org/fhir/StructureDefinition/capabilitystatement-expectation")  ?  
        t.getExtensionString("http://hl7.org/fhir/StructureDefinition/capabilitystatement-expectation") : null;
  }

  private boolean isShouldOrShall(Element t) {
    return t.hasExtension("http://hl7.org/fhir/StructureDefinition/capabilitystatement-expectation") && 
        ("SHALL".equals(t.getExtensionString("http://hl7.org/fhir/StructureDefinition/capabilitystatement-expectation")) || "SHOULD".equals(t.getExtensionString("http://hl7.org/fhir/StructureDefinition/capabilitystatement-expectation")));
  }

  private boolean isProhibited(Element t) {
    return t.hasExtension("http://hl7.org/fhir/StructureDefinition/capabilitystatement-expectation") && "SHALL NOT".equals(t.getExtensionString("http://hl7.org/fhir/StructureDefinition/capabilitystatement-expectation"));
  }

  private void compareSecurity(XhtmlNode td, String path, CapabilityStatementRestSecurityComponent self, CapabilityStatementRestSecurityComponent other) {
    if (self.getCors() && !other.getCors()) 
      error(td, IssueType.CONFLICT, path+".security.cors", selfName+" specifies CORS but "+otherName+" doesn't");
    else if (!self.getCors() && other.getCors()) 
      error(td, IssueType.CONFLICT, path+".security.cors", selfName+" does not specify CORS but "+otherName+" does");

    List<CodeableConcept> ol = new ArrayList<>();
    List<CodeableConcept> olr = new ArrayList<>();
    ol.addAll(other.getService());
    for (CodeableConcept cc : self.getService()) {
      CodeableConcept o = null;
      for (CodeableConcept t : ol) {
        if (isMatch(t, cc)) {
          o = t;
          break;
        }
      }
      if (o == null) {
        error(td, IssueType.CONFLICT, path+".security.cors", selfName+" specifies the security option "+gen(cc)+" but "+otherName+" does not");        
      } else { 
        olr.add(o);
      }
    }
    for (CodeableConcept cc : ol) {
      if (!olr.contains(cc))
        error(td, IssueType.CONFLICT, path+".security.cors", selfName+" does not specify the security option "+gen(cc)+" but "+otherName+" does");        
    }    
  }

  private boolean isMatch(CodeableConcept self, CodeableConcept other) {
    for (Coding s : self.getCoding())
      for (Coding o : other.getCoding())
        if (isMatch(s, o))
          return true;
    return false;
  }

  private boolean isMatch(Coding s, Coding o) {
    return s.hasCode() && s.getCode().equals(o.getCode()) && s.hasSystem() && s.getSystem().equals(o.getSystem());
  }

  private String gen(CapabilityStatementRestSecurityComponent security) {
    CommaSeparatedStringBuilder b = new CommaSeparatedStringBuilder();
    for (CodeableConcept cc : security.getService()) 
      b.append(gen(cc));
    if (security.getCors())
      b.append("(CORS)");
    if (Utilities.noString(b.toString()))
      return "(none specified)";
    return b.toString();
  }

  private String gen(CodeableConcept cc) {
    if (cc.hasText())
      return cc.getText();
    if (cc.hasCoding())
      return gen(cc.getCoding().get(0));
    return "??";
  }

  private String gen(Coding coding) {
    if (coding.hasDisplay())
      return coding.getDisplay();
    if (coding.hasCode())
      return coding.getCode();
    return "???";
  }


  private XhtmlNode startHtml() {
    html = new XhtmlDocument();
    XhtmlNode doc = html.addTag("html");
    XhtmlNode head = doc.addTag("head");
    head.addTag("title").addText("Comparison of "+selfName+" to "+otherName);
    head.addTag("link").setAttribute("rel", "stylesheet").setAttribute("href", "fhir.css");
    XhtmlNode body = doc.addTag("body").style("background-color: white");

    body.h1().addText("Comparison of "+selfName+" to "+otherName);
    return body;    
  }

  private void addMarkdown(XhtmlNode x, String text) throws FHIRFormatError, IOException, DefinitionException {
    if (text != null) {
      // 1. custom FHIR extensions
      while (text.contains("[[[")) {
        String left = text.substring(0, text.indexOf("[[["));
        String link = text.substring(text.indexOf("[[[")+3, text.indexOf("]]]"));
        String right = text.substring(text.indexOf("]]]")+3);
        String url = link;
        String[] parts = link.split("\\#");
        StructureDefinition p = context.fetchResource(StructureDefinition.class, parts[0]);
        if (p == null)
          p = context.fetchTypeDefinition(parts[0]);
        if (p == null)
          p = context.fetchResource(StructureDefinition.class, link);
        if (p != null) {
          url = p.getUserString("path");
          if (url == null)
            url = p.getUserString("filename");
        } else
          throw new DefinitionException("Unable to resolve markdown link "+link);

        text = left+"["+link+"]("+url+")"+right;
      }

      // 2. markdown
      String s = markdown.process(Utilities.escapeXml(text), "narrative generator");
      XhtmlParser p = new XhtmlParser();
      XhtmlNode m;
      try {
        m = p.parse("<div>"+s+"</div>", "div");
      } catch (org.hl7.fhir.exceptions.FHIRFormatError e) {
        throw new FHIRFormatError(e.getMessage(), e);
      }
      x.getChildNodes().addAll(m.getChildNodes());
    }
  }


  private XhtmlNode startTable(XhtmlNode x, CapabilityStatement self, CapabilityStatement other) {
    XhtmlNode tbl = x.table("grid");    
    XhtmlNode tr = tbl.tr();
    tr.td().b().nbsp();
    tr.td().b().addText(selfName);
    tr.td().b().addText(otherName);
    tr.td().b().addText("Comparison");
    return tbl;
  }

  private void download(String address, String filename) throws IOException {
    URL url = new URL(address);
    URLConnection c = url.openConnection();
    InputStream s = c.getInputStream();
    FileOutputStream f = new FileOutputStream(filename);
    transfer(s, f, 1024);
    f.close();   
  }


  public static void transfer(InputStream in, OutputStream out, int buffer) throws IOException {
    byte[] read = new byte[buffer]; // Your buffer size.
    while (0 < (buffer = in.read(read)))
      out.write(read, 0, buffer);
  }

  private void fatal(XhtmlNode x, IssueType type, String path, String message) {
    output.add(new ValidationMessage(Source.ProfileComparer, type, path, message, IssueSeverity.FATAL));
    XhtmlNode ul;
    if ("ul".equals(x.getName())) {
      ul = x;
    } else {
      ul = null;
      for (XhtmlNode c : x.getChildNodes()) {
        if ("ul".equals(c.getName())) {
          ul = c;
        }
      }
      if (ul == null) {
        ul = x.ul();
      }
    } 
    ul.li().b().style("color: maroon").addText(message);
  }

  private void error(XhtmlNode x, IssueType type, String path, String message) {
    output.add(new ValidationMessage(Source.ProfileComparer, type, path, message, IssueSeverity.ERROR));
    XhtmlNode ul;
    if ("ul".equals(x.getName())) {
      ul = x;
    } else {
      ul = null;
      for (XhtmlNode c : x.getChildNodes()) {
        if ("ul".equals(c.getName())) {
          ul = c;
        }
      }
      if (ul == null) {
        ul = x.ul();
      }
    } 
    ul.li().b().style("color: maroon").addText(message);
  }

  private void information(XhtmlNode x, IssueType type, String path, String message) {
    if (type != null)
      output.add(new ValidationMessage(Source.ProfileComparer, type, path, message, IssueSeverity.INFORMATION));
    XhtmlNode ul;
    if ("ul".equals(x.getName())) {
      ul = x;
    } else {
      ul = null;
      for (XhtmlNode c : x.getChildNodes()) {
        if ("ul".equals(c.getName())) {
          ul = c;
        }
      }
      if (ul == null) {
        ul = x.ul();
      }
    } 
    ul.li().addText(message);
  }

  private XhtmlNode same(String text, boolean test) {
    XhtmlNode span = new XhtmlNode(NodeType.Element, "span");
    if (test)
      span.style("background-color: #bbff99; border: 1px solid #44cc00; margin-width: 10px");
    span.tx(text);
    return span;
  }
  
}
