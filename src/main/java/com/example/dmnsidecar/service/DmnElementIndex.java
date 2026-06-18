package com.example.dmnsidecar.service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DmnElementIndex {

    private static final Set<String> DRG_ELEMENT_NAMES = Set.of(
            "decision", "inputData", "businessKnowledgeModel", "knowledgeSource", "decisionService"
    );

    public record DrgRef(String drgElementId, String drgElementName) {}

    private final Map<String, DrgRef> index;

    private DmnElementIndex(Map<String, DrgRef> index) {
        this.index = index;
    }

    public static DmnElementIndex from(String xml) throws SAXException, IOException, ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        Map<String, DrgRef> index = new HashMap<>();
        NodeList children = doc.getDocumentElement().getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element child)) continue;
            if (!DRG_ELEMENT_NAMES.contains(child.getLocalName())) continue;

            String drgId = child.getAttribute("id");
            if (drgId == null || drgId.isBlank()) continue;

            String drgName = child.getAttribute("name");
            DrgRef ref = new DrgRef(drgId, drgName.isBlank() ? null : drgName);
            indexSubtree(child, ref, index);
        }

        return new DmnElementIndex(index);
    }

    public Optional<DrgRef> resolve(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(index.get(id));
    }

    private static void indexSubtree(Element element, DrgRef ref, Map<String, DrgRef> index) {
        String id = element.getAttribute("id");
        if (id != null && !id.isBlank()) {
            index.put(id, ref);
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                indexSubtree(child, ref, index);
            }
        }
    }
}