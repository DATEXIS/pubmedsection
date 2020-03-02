package cleaner;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;

/**
 * Class for manipulating the xml representation of BMC documents.
 * Created by rudolf on 12.09.16.
 */
public class XmlCleaner {

    private static final String LAST_SECTION_XPATH_EXPRESSION = "art/bdy/sec[last()]";
    Logger logger = LoggerFactory.getLogger(this.getClass());

    private XPathExpression paragraphsAndSections;
    private XPath xpath = XPathFactory.newInstance().newXPath();
    private XPathExpression expressionNodesToRemove;

    public XmlCleaner() {

    }

    public XmlCleaner(String expressionToRemove) throws XPathExpressionException {
        expressionNodesToRemove = xpath.compile(expressionToRemove);
    }

    /**
     * removes the matched nodes from the targeted xml.
     *
     * @param expressionToRemove XPath expression for the nodes which should be deleted.
     * @param targetedXml        targeted xml document.
     * @return document without the targeted nodes.
     * @throws XPathExpressionException thrown when the XPath expression is malformed.
     */
    public Document removeByXPath(String expressionToRemove, Document targetedXml)
            throws
            XPathExpressionException {
        //logger.debug("removing XPath: {} in {}", expressionToRemove, targetedXml.getDocumentURI());


        NodeList matchedNodesToRemove;


        matchedNodesToRemove = (NodeList) expressionNodesToRemove.evaluate(targetedXml,
                XPathConstants.NODESET);


        //logger.debug("removing {} matched nodes in {}", matchedNodesToRemove.getLength(),
        //        targetedXml.getDocumentURI());
        for (int i = 0; i < matchedNodesToRemove.getLength(); i++) {
            Node currentNode = matchedNodesToRemove.item(i);
            Node parentNode = currentNode.getParentNode();
            parentNode.removeChild(currentNode);
        }

        return targetedXml;
    }

    public Document removeByXPath(XPathExpression exp, Document targetedXml) throws XPathExpressionException {
        XPathExpression expressionNodesToRemove = exp;
        NodeList matchedNodesToRemove = (NodeList) expressionNodesToRemove.evaluate(targetedXml,
                XPathConstants.NODESET);


        //logger.debug("removing {} matched nodes in {}", matchedNodesToRemove.getLength(),
        //        targetedXml.getDocumentURI());
        for (int i = 0; i < matchedNodesToRemove.getLength(); i++) {
            Node currentNode = matchedNodesToRemove.item(i);
            Node parentNode = currentNode.getParentNode();
            parentNode.removeChild(currentNode);
        }

        return targetedXml;
    }

    /**
     * Moves the tag matched by expressionToMove to the last section in an BMC-Document.
     *
     * @param expressionToMove xpath expression to match the node to move.
     * @param targetedXml      xml file to manipulate.
     * @return the manipulated xml file.
     * @throws XPathExpressionException error in xpath expression.
     */
    public Document moveMatchingTagsToEnd(String expressionToMove, Document targetedXml)
            throws
            XPathExpressionException {
        //logger.debug("moving XPath: {} to the end of {}", expressionToMove,
        //        targetedXml.getDocumentURI());
        XPath xpath = XPathFactory.newInstance().newXPath();

        XPathExpression expressionNodesToMove = xpath.compile(expressionToMove);
        NodeList matchedNodesToMove;
        matchedNodesToMove = (NodeList) expressionNodesToMove.evaluate(targetedXml,
                XPathConstants.NODESET);

        //logger.debug("moving {} matched nodes in {}", matchedNodesToMove.getLength(),
        //        targetedXml.getDocumentURI());
        for (int i = 0; i < matchedNodesToMove.getLength(); i++) {
            Node currentNode = matchedNodesToMove.item(i);
            targetedXml = moveNodeToTheEndOfSelectedElement(
                    currentNode, LAST_SECTION_XPATH_EXPRESSION, targetedXml);
        }

        return targetedXml;
    }

    /**
     * Fetches all "p" and "sec" tags (and all childs) from an BMC document and creates a new
     * DOM Document containing them.
     *
     * @param targetedXml bmc xml document from which the "p tags" should be fetched.
     * @return new DOM Document containig only the "p tags" of targetedXml and their children.
     * @throws XPathExpressionException     error in xpath expression.
     * @throws ParserConfigurationException error in xpath expression.
     */
    public Document getParagraphsAndSections(Document targetedXml) throws XPathExpressionException,
            ParserConfigurationException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        XPathExpression expressionParagraphs = xpath.compile("//sec/title|//sec/p|//p[not(ancestor::sec)]");
        NodeList matchedParagraphNodes = (NodeList) expressionParagraphs.evaluate(
                targetedXml, XPathConstants.NODESET);

        Document newXmlDocument = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().newDocument();
        newXmlDocument.setDocumentURI(targetedXml.getDocumentURI());
        Element root = newXmlDocument.createElement("root");
        newXmlDocument.appendChild(root);

        for (int i = 0; i < matchedParagraphNodes.getLength(); i++) {
            Node node = matchedParagraphNodes.item(i);
            Node copyNode = newXmlDocument.importNode(node, true);
            root.appendChild(copyNode);
        }

        return newXmlDocument;
    }

    private Document moveNodeToTheEndOfSelectedElement(Node nodeToMove,
                                                       String expressionToPlaceNodeAfter,
                                                       Document targetedXml)
            throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        XPathExpression expressionNodesToMove = xpath.compile(expressionToPlaceNodeAfter);
        Node selectedNode = (Node) expressionNodesToMove.evaluate(targetedXml,
                XPathConstants.NODE);

        Node tmpCopyOfCurrentNode = nodeToMove.cloneNode(true);
        String textContent = tmpCopyOfCurrentNode.getTextContent();
        String newText = appendDotAsLastChar(textContent);
        tmpCopyOfCurrentNode.setTextContent(newText);
        if (selectedNode != null) {
            selectedNode.appendChild(tmpCopyOfCurrentNode);
        }
        nodeToMove.getParentNode().removeChild(nodeToMove);
        return targetedXml;
    }

    private String appendDotAsLastChar(String text) {
        text = stripWhiteSpaceAndLineSeparators(text);
        int length = text.length();
        char lastCharacter = text.charAt(length - 1);
        if (lastCharacter != '.') {
            text = text + '.';
        }
        return text;
    }

    private String stripWhiteSpaceAndLineSeparators(String text) {
        text = text.replaceAll("\\r\\n|\\r|\\n", " ");
        text = StringUtils.strip(text);
        return text;
    }
}
