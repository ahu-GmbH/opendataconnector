package de.ahu.opendata.ServiceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.ahu.opendata.OpenDataNrw.BboxDTO;
import de.ahu.opendata.OpenDataNrw.MetadataDTO;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MetadataParserService {

	public MetadataDTO parse(InputStream xmlStream)
			throws SAXException, IOException, XPathExpressionException, ParserConfigurationException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document doc = builder.parse(xmlStream);
		XPath xpath = XPathFactory.newInstance().newXPath();
		xpath.setNamespaceContext(new SimpleNamespaceContext());
		MetadataDTO metadata = new MetadataDTO();

		String refSystemCode = xpath.evaluate(
				"//gmd:referenceSystemInfo/gmd:MD_ReferenceSystem/gmd:referenceSystemIdentifier/gmd:RS_Identifier/gmd:code/gmx:Anchor/text()",
				doc);
		metadata.setReferenceSystemCode(refSystemCode);

		String abstractText = xpath.evaluate("//gmd:abstract/gco:CharacterString/text()", doc);
		metadata.setAbstractText(abstractText);

		String titel = xpath.evaluate("//gmd:CI_Citation/gmd:title/gco:CharacterString/text()", doc);
		metadata.setLabel(titel);

		NodeList keywordNodes = (NodeList) xpath.evaluate(
				"//gmd:descriptiveKeywords/gmd:MD_Keywords/gmd:keyword/gco:CharacterString/text()", doc,
				XPathConstants.NODESET);
		List<String> keywords = new ArrayList<>();
		for (int i = 0; i < keywordNodes.getLength(); i++) {
			keywords.add(keywordNodes.item(i).getNodeValue());
		}
		metadata.setKeywords(keywords);
		BboxDTO bbox = new BboxDTO();
		bbox.setWestBoundLongitude(
				parseDecimal(xpath, doc, "//gmd:EX_GeographicBoundingBox/gmd:westBoundLongitude/gco:Decimal/text()"));
		bbox.setEastBoundLongitude(
				parseDecimal(xpath, doc, "//gmd:EX_GeographicBoundingBox/gmd:eastBoundLongitude/gco:Decimal/text()"));
		bbox.setSouthBoundLatitude(
				parseDecimal(xpath, doc, "//gmd:EX_GeographicBoundingBox/gmd:southBoundLatitude/gco:Decimal/text()"));
		bbox.setNorthBoundLatitude(
				parseDecimal(xpath, doc, "//gmd:EX_GeographicBoundingBox/gmd:northBoundLatitude/gco:Decimal/text()"));

		metadata.setGeographicBoundingBox(bbox);
		return metadata;
	}

	private double parseDecimal(XPath xpath, Document doc, String expression) throws XPathExpressionException {
		String value = xpath.evaluate(expression, doc);
		return value.isEmpty() ? null : Double.parseDouble(value);
	}

	static class SimpleNamespaceContext implements NamespaceContext {
		public String getNamespaceURI(String prefix) {
			return switch (prefix) {
			case "gmd" -> "http://www.isotc211.org/2005/gmd";
			case "gco" -> "http://www.isotc211.org/2005/gco";
			case "gmx" -> "http://www.isotc211.org/2005/gmx";
			default -> null;
			};
		}

		public String getPrefix(String namespaceURI) {
			return null;
		}

		public Iterator<String> getPrefixes(String namespaceURI) {
			return null;
		}
	}

}
