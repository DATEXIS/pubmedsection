package cleaner;

import database.PubmedDatabase;
import de.datexis.common.ObjectSerializer;
import de.datexis.common.Resource;
import de.datexis.model.Annotation;
import de.datexis.model.Document;
import de.datexis.preprocess.DocumentFactory;
import de.datexis.sector.model.SectionAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PubMedCleaner {

    private static final Logger log = LoggerFactory.getLogger(PubMedCleaner.class);

    //TODO sentence splitter should not split "Fig. X" into "Fig." and "X"
    //TODO Do not split abbreviations


    private DocumentBuilder documentBuilder;
    private XPathFactory xpathFactory;
    private DocumentBuilderFactory documentBuilderFactory;
    private XPathExpression sectionQuery;
    private XPathExpression sectionHeadingQuery;
    private XPath xpath;
    private XmlCleaner xmlCleaner = new XmlCleaner();

    public PubMedCleaner() throws ParserConfigurationException, XPathExpressionException {

        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilder = documentBuilderFactory.newDocumentBuilder();
        XPathFactory xPathfactory = XPathFactory.newInstance();
        xpath = xPathfactory.newXPath();
        this.initXPathExpressions();

        sectionQuery = xpath.compile("//p/text()|//p[not(ancestor::sec)]/text()");
        sectionHeadingQuery = xpath.compile("//*/title/text()");


    }

    public String getArticleHeading(String document) {
        try {
            org.w3c.dom.Document doc = documentBuilder.parse(new InputSource(new StringReader(document)));
            return getArticleTitle(doc);
        } catch (Exception e) {
            System.out.println("Exception in doc creation");
            e.printStackTrace();
            return "";
        }
    }

    public static String checkPubMedID(Path p) {
        File nxmlFile = new File(p.toUri());
        DocumentBuilder documentBuilder = createDocumentBuilder();
        if (documentBuilder == null) {
            return "";
        }
        try {
            org.w3c.dom.Document doc = documentBuilder.parse(nxmlFile);
            // String pubMedId = getPubMedId(doc);
            return null;
        } catch (SAXException | IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static ArrayList<String> checkPubMedID_TREC2017(Path p) {
        ArrayList<String> results = new ArrayList<>();
        File nxmlFile = new File(p.toUri());
        DocumentBuilder documentBuilder = createDocumentBuilder();
        if (documentBuilder == null) {
            return results;
        }
        try {
            org.w3c.dom.Document doc = documentBuilder.parse(nxmlFile);
            results = getPMIDs_TREC2017(doc);
            return results;
        } catch (SAXException | IOException e) {
            e.printStackTrace();
        }
        return results;
    }

    public String parsePubmedDocStringToString(String doc_str) {
        try {
            org.w3c.dom.Document doc = documentBuilder.parse(new InputSource(new StringReader(doc_str)));
            // log.info("Importing " + path.toString());
            String pubMedId = getPubMedId(doc);
            String title = getArticleTitle(doc);

            doc = removeUnrequiredParts(doc);

            Document d = new Document();

            // fill doc sections
            fillDocumentSections(d, doc);
            d.setId(pubMedId);
            d.setTitle(title);
            return d.getText();
        } catch (Exception e) {
            System.out.println("Exception in doc creation");
            e.printStackTrace();
            return "";
        }
    }

    public ArrayList<String> getDocumentHeadings(String doc_str) {
        ArrayList<String> results = new ArrayList<>();
        try {
            org.w3c.dom.Document doc = documentBuilder.parse(new InputSource(new StringReader(doc_str)));
            // log.info("Importing " + path.toString());
            String pubMedId = getPubMedId(doc);
            String title = getArticleTitle(doc);

            doc = removeUnrequiredParts(doc);
            return getHeadings(doc);

        } catch (Exception e) {

        }
        return results;
    }

    public Document parsePubMedDocStringToDoc(String doc_str) {
        try {
            org.w3c.dom.Document doc = documentBuilder.parse(new InputSource(new StringReader(doc_str)));
            // log.info("Importing " + path.toString());
            String pubMedId = getPubMedId(doc);
            String title = getArticleTitle(doc);

            doc = removeUnrequiredParts(doc);

            Document d = new Document();

            // fill doc sections
            fillDocumentSections(d, doc);
            cleanSectionAnnotations(d);
            d.setId(pubMedId);
            d.setTitle(title);
            return d;
        } catch (Exception e) {
            System.out.println("Exception in doc creation");
            e.printStackTrace();
            return null;
        }
    }

    public Document parsePubMedFileToDocument(Path path) {
        //log.debug("Reading " + path.toString());
        File nxmlFile = new File(path.toUri());
        try {
            org.w3c.dom.Document doc = documentBuilder.parse(nxmlFile);
            // log.info("Importing " + path.toString());
            String pubMedId = getPubMedId(doc);
            String title = getArticleTitle(doc);

            doc = removeUnrequiredParts(doc);
            Document d = new Document();

            // fill doc sections
            fillDocumentSections(d, doc);
            cleanSectionAnnotations(d);
            d.setId(pubMedId);
            d.setTitle(title);
            return d;
        } catch (Exception e) {
            System.out.println("Exception in doc creation");
            e.printStackTrace();
            return null;
        }
    }

    public static ArrayList<String> getMeshTerms(Path p) {
        ArrayList<String> res = new ArrayList<>();
        DocumentBuilder documentBuilder = createDocumentBuilder();
        if (documentBuilder == null) {
            return res;
        }
        File nxmlFile = new File(p.toUri());
        try {
            org.w3c.dom.Document doc = documentBuilder.parse(nxmlFile);
            res = getMeshTerms(doc);
        } catch (SAXException | IOException e) {
            e.printStackTrace();
        }
        return res;
    }

    private void addSections(Document d, org.w3c.dom.Document doc, PubmedDatabase db) {
        // gets sections out of the xml and asks the db for the section-title and the wikisection-label
    }

    private void fillDocumentSections(Document d, org.w3c.dom.Document doc) {
        try {
            NodeList results;
            results = (NodeList) sectionQuery.evaluate(doc, XPathConstants.NODESET);

            for (int i = 0; i < results.getLength(); ++i) {
                Node n = results.item(i);
                String heading, content;
                heading = "";
                content = "";
                if (n.getParentNode() != null) {
                    if (n.getParentNode().getPreviousSibling() != null) {
                        Node prev = n.getParentNode().getPreviousSibling();
                        if (prev.getNodeName().equals("title")) {
                            heading = prev.getTextContent();
                        }
                    }
                }
                if (heading.equals("")) {
                    // check if there is a heading for the parent node
                    if (n.getParentNode() != null) {
                        if (n.getParentNode().getParentNode() != null) {
                            Node par = n.getParentNode().getParentNode();
                            if (par.getPreviousSibling() != null) {
                                Node prev = par.getPreviousSibling();
                                if (prev.getNodeName().equals("title")) {
                                    heading = prev.getTextContent();
                                }
                            }
                        }
                    }
                }
                content = n.getTextContent();
                content = removeTagsAndTransformToString(content);
                appendSectionToDocument(d, content, heading);
            }
        } catch (
                Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
    }

    private ArrayList<String> getHeadings(org.w3c.dom.Document doc) {
        ArrayList<String> headings = new ArrayList<>();
        try {

            NodeList results;
            results = (NodeList) sectionQuery.evaluate(doc, XPathConstants.NODESET);

            for (int i = 0; i < results.getLength(); ++i) {
                Node n = results.item(i);
                String heading, content;
                heading = "";
                content = "";
                if (n.getParentNode() != null) {
                    if (n.getParentNode().getPreviousSibling() != null) {
                        Node prev = n.getParentNode().getPreviousSibling();
                        if (prev.getNodeName().equals("title")) {
                            heading = prev.getTextContent();
                        }
                    }
                }
                if (heading.equals("")) {
                    // check if there is a heading for the parent node
                    if (n.getParentNode() != null) {
                        if (n.getParentNode().getParentNode() != null) {
                            Node par = n.getParentNode().getParentNode();
                            if (par.getPreviousSibling() != null) {
                                Node prev = par.getPreviousSibling();
                                if (prev.getNodeName().equals("title")) {
                                    heading = prev.getTextContent();
                                }
                            }
                        }
                    }
                }

                headings.add(heading);
            }
        } catch (
                Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
        return headings;
    }


    private void cleanSectionAnnotations(Document d) {

        List<SectionAnnotation> sectionAnnotationCollection = (List<SectionAnnotation>) d.getAnnotations(SectionAnnotation.class);
        sectionAnnotationCollection.sort((SectionAnnotation s1, SectionAnnotation s2) -> Integer.compare(s1.getBegin(), s2.getBegin()));
        String previousHeading = "";

        if (sectionAnnotationCollection.size() > 0) {
            if (sectionAnnotationCollection.get(0).getSectionHeading().equals("")) {
                sectionAnnotationCollection.get(0).setSectionHeading("Abstract");
            }
        }

        for (SectionAnnotation sa : sectionAnnotationCollection) {
            if (sa.getSectionHeading().equals("")) {
                sa.setSectionHeading(previousHeading);
            } else {
                previousHeading = sa.getSectionHeading();
            }
        }

    }

    private SectionAnnotation combineSectionAnnotations(List<SectionAnnotation> sectionAnnotations) {
        int begin = 0;
        int length = 0;
        SectionAnnotation newAnnotation;
        String heading = sectionAnnotations.get(0).getSectionHeading();
        begin = sectionAnnotations.get(0).getBegin();
        for (SectionAnnotation sa : sectionAnnotations) {
            length += sa.getLength();
        }
        newAnnotation = new SectionAnnotation(Annotation.Source.GOLD, "pubmed", heading);
        newAnnotation.setBegin(begin);
        newAnnotation.setLength(length);
        return newAnnotation;
    }


    private void appendSectionToDocument(Document d, String section, String sectionHeading) {
        int docLength = d.getLength();
        int sectionLength = section.length();
        d.append(DocumentFactory.fromText(section));
        SectionAnnotation sectionAnnotation = new SectionAnnotation(Annotation.Source.GOLD, "pubmed", sectionHeading);
        sectionAnnotation.setBegin(docLength);
        sectionAnnotation.setLength(section.length());
        d.addAnnotation(sectionAnnotation);
    }

    private String getSectionHeading(org.w3c.dom.Document doc, int index) {
        try {
            NodeList results;
            results = (NodeList) sectionHeadingQuery.evaluate(doc, XPathConstants.NODESET);
            if (results.getLength() <= index) {
                return "";
            } else {
                return results.item(index).getTextContent();
            }
        } catch (
                Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        }
        return "";
    }


    public void parsePubMedFileToDocument(Path path, String documentIdPrefix) throws
            IOException, SAXException, XPathExpressionException, ParserConfigurationException {
        log.debug("Reading " + path.toString());
        File nxmlFile = new File(path.toUri());
        DocumentBuilder documentBuilder = createDocumentBuilder();
        if (documentBuilder == null) {
            return;
        }

        org.w3c.dom.Document doc = documentBuilder.parse(nxmlFile);
        log.info("Importing " + path.toString());
        //String pubMedId = getPubMedId(doc);
        String pubMedId = "";
        String title = getArticleTitle(doc);


        doc = removeUnrequiredParts(doc);
        String plainText = getSection(doc);//removeTagsAndTransformToString(doc);
        plainText = removeTagsAndTransformToString(plainText);
        Document d = DocumentFactory.fromText("plainText");
        d.setId(pubMedId);
        d.setTitle(title);

        String outputPath = "/home/paul/DATEXIS/RelevantArticles/json/" + d.getId() + ".json";
        Resource targetPath = Resource.fromFile(outputPath);
        ObjectSerializer.writeJSON(d, targetPath);
        /*
        BufferedWriter bw = new BufferedWriter(new FileWriter(new File("/home/paul/DATEXIS/RelevantArticles/plain/" + documentIdPrefix + pubMedId + ".txt")));
        bw.write(title + "\n");
        bw.write(plainText);
        bw.close();*/
    }

    private static ArrayList<String> getMeshTerms(org.w3c.dom.Document doc) {
        ArrayList<String> resultList = new ArrayList<>();
        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = "//MeshHeading/DescriptorName/text()";
        try {
            XPathExpression exp = xpath.compile(expression);
            NodeList results;
            results = (NodeList) exp.evaluate(doc, XPathConstants.NODESET);
            Node previousOwner = null;
            for (int i = 0; i < results.getLength(); ++i) {
                Node n = results.item(i);
                resultList.add(n.getTextContent());
            }
            return resultList;
        } catch (XPathExpressionException e) {
            log.error("Error during PubMed ID extraction!");
            log.info(e.getMessage());
            e.printStackTrace();
        }
        return resultList;
    }

    private String getArticleTitle(org.w3c.dom.Document doc) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = "//article-title";
        try {
            return (String) xpath.compile(expression).evaluate(doc, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            log.error("Error during PubMed ID extraction!");
            log.info(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private static ArrayList<String> getPMIDs_TREC2017(org.w3c.dom.Document doc) {


        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = "//PMID/text()";
        ArrayList<String> res = new ArrayList<>();
        try {
            XPathExpression exp = xpath.compile(expression);
            NodeList results;
            results = (NodeList) exp.evaluate(doc, XPathConstants.NODESET);
            Node previousOwner = null;
            for (int i = 0; i < results.getLength(); ++i) {
                Node n = results.item(i);
                res.add(n.getTextContent());
            }
            return res;
        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
        return res;
    }

    private String getSection(org.w3c.dom.Document doc) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = "//*/title/text()|//sec/*/text()|//p[not(ancestor::sec)]/text()";
        String res = "";


        try {
            XPathExpression exp = xpath.compile(expression);
            NodeList results;
            results = (NodeList) exp.evaluate(doc, XPathConstants.NODESET);
            Node previousOwner = null;
            for (int i = 0; i < results.getLength(); ++i) {
                Node n = results.item(i);
                Node currentOwner = n.getParentNode();
                if (previousOwner == null)
                    previousOwner = currentOwner;


                if (previousOwner != currentOwner) {
                    res += "\n";
                    res += results.item(i).getTextContent();
                    previousOwner = currentOwner;
                } else {
                    res += results.item(i).getTextContent();
                }

            }
            return res;


        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

        return null;
    }


    private String getPubMedId(org.w3c.dom.Document doc) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        String expression = "//article-id[@pub-id-type='pmc']";
        try {
            return (String) xpath.compile(expression).evaluate(doc, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            log.error("Error during PubMed ID extraction!");
            log.info(e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private static DocumentBuilder createDocumentBuilder() {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        String loadDtdGrammar = "http://apache.org/xml/features/nonvalidating/load-dtd-grammar";
        String loadExternalDtd = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
        try {
            dbFactory.setFeature(loadDtdGrammar, false);
            dbFactory.setFeature(loadExternalDtd, false);
            return dbFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();  //TODO Remove exception handling since it makes no sence to continue at this point.
        }
        return null;
    }

    private XPathExpression tableExp, tableWrapExp, abbrgrp, abbr, supp, file, empty, figtext, figcap, suppltxt, inlinefrm, dispfrm, xref;

    private void initXPathExpressions() throws XPathExpressionException {
        tableExp = xpath.compile("//tbl");
        tableWrapExp = xpath.compile("//table-wrap");
        abbrgrp = xpath.compile("//abbrgrp");
        abbr = xpath.compile("//abbr");
        supp = xpath.compile("//suppl");
        file = xpath.compile("//file");
        empty = xpath.compile("//p[string-length(text()) = 0]");
        figtext = xpath.compile("//fig/text");
        figcap = xpath.compile("//fig/caption");
        suppltxt = xpath.compile("//suppl/text");
        inlinefrm = xpath.compile("//inline-formula");
        dispfrm = xpath.compile("//disp-formula");
        xref = xpath.compile("//xref");


    }

    private org.w3c.dom.Document removeUnrequiredParts(org.w3c.dom.Document doc) throws XPathExpressionException {
        final String emptyParagraphXpathExpression = "//p[string-length(text()) = 0]";

        //log.debug("removing tables");
        doc = xmlCleaner.removeByXPath(tableExp, doc);
        doc = xmlCleaner.removeByXPath(tableWrapExp, doc);
        //log.debug("removing abbrgrp");
        doc = xmlCleaner.removeByXPath(abbrgrp, doc);
        //log.debug("removing abbr");
        doc = xmlCleaner.removeByXPath(abbr, doc);
        //log.debug("removing suppl");
        doc = xmlCleaner.removeByXPath(supp, doc);
        //log.debug("removing file");
        doc = xmlCleaner.removeByXPath(file, doc);
        //log.debug("removing abbr");
        doc = xmlCleaner.removeByXPath(empty, doc);
        //log.debug("moving figure text to end of the document");
        doc = xmlCleaner.removeByXPath(figtext, doc);
        //log.debug("moving figure caption to end of the document");
        doc = xmlCleaner.removeByXPath(figcap, doc);
        //log.debug("moving suppl text to end of the document");
        doc = xmlCleaner.removeByXPath(suppltxt, doc);
        //log.debug("removing inline-formula");
        doc = xmlCleaner.removeByXPath(inlinefrm, doc);
        //log.debug("removing disp-formula");
        doc = xmlCleaner.removeByXPath(dispfrm, doc);
        //log.debug("removing xref (references)");
        doc = xmlCleaner.removeByXPath(xref, doc);
        //log.debug("striping tags from remaining paragraphs");
        //doc = xmlCleaner.getParagraphsAndSections(doc);
        return doc;
    }

    private String removeTagsAndTransformToString(String text) {


        final String emptyCitationReferencePattern = "(\\s\\[\\,*\\])|(\\s\\[\\])|(\\[\\,*\\])|(\\[\\])";
        final String xmlTagsPattern = "\\<.*?\\>";
        final String endOfParagraphPattern = "(</p>)|(</sec>)|(</title>)";
        final String moreThanOneWhitespacePattern = "[^\\S\\n]+";
        final String moreThanOneUnicodeSpacePattern = "\\p{Zs}+";
        final String peol = "\\(.\n";
        final String peol2 = "\\[.\n";
        final String peol3 = "\\).\n";
        final String peol4 = "].\n";
        final String peol5 = "\\(\n";
        final String peol6 = "\\[\n";
        final String peol7 = "]\n";
        final String peol8 = "\\)\n";
        final String emptyParantheses = "()";


        String plainText = text;//convertDocumentToString(doc);
        plainText = plainText.replaceAll(endOfParagraphPattern, "\n");
        plainText = plainText.replaceAll(xmlTagsPattern, " ");
        plainText = plainText.replaceAll(emptyCitationReferencePattern, "");
        plainText = plainText.replaceAll(moreThanOneWhitespacePattern, " ");
        plainText = plainText.replaceAll(moreThanOneUnicodeSpacePattern, " ");
        plainText = plainText.replaceAll(peol, ".\n");
        plainText = plainText.replaceAll(peol2, ".\n");
        plainText = plainText.replaceAll(peol3, ".\n");
        plainText = plainText.replaceAll(peol4, ".\n");
        plainText = plainText.replaceAll(peol5, ".\n");
        plainText = plainText.replaceAll(peol6, ".\n");
        plainText = plainText.replaceAll(peol7, ".\n");
        plainText = plainText.replaceAll(peol8, ".\n");
        plainText = plainText.replaceAll(emptyParantheses, "");


        plainText = plainText.trim();
        return plainText;
    }

    private String convertDocumentToString(org.w3c.dom.Document bmcFileToRead) {
        Transformer transformer = null;
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        } catch (TransformerConfigurationException e) {
            log.error(e.getMessage());
            return null;
        }
        StringWriter writer = new StringWriter();
        Element rootElement = bmcFileToRead.getDocumentElement();


        rootElement.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:xlink", "http://www.w3.org/2001/XMLSchema");
        try {
            transformer.transform(new DOMSource(bmcFileToRead), new StreamResult(writer));
        } catch (TransformerException e) {
            log.error("Error during stringification of document.");
            log.debug(e.getMessageAndLocation());
            e.printStackTrace();
        }
        return writer.getBuffer().toString();//.replaceAll("\n|\r|\t", " ");
    }
}
