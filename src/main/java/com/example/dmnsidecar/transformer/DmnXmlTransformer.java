package com.example.dmnsidecar.transformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Transforma XML generado por dmn-js al formato que Drools requiere.
 * 1. Normaliza typeRef no-FEEL a tipos FEEL válidos (integer -> number, etc.)
 * 2. Agrega elementos <variable> faltantes en <inputData> y <decision>.
 */
@Component
public class DmnXmlTransformer {

    private static final Logger log = LoggerFactory.getLogger(DmnXmlTransformer.class);
    private static final String DMN_NS = "https://www.omg.org/spec/DMN/20191111/MODEL/";

    // Tipos no-FEEL comunes que generan herramientas externas -> tipo FEEL correcto
    private static final Map<String, String> TYPE_NORMALIZATION = new HashMap<>();
    static {
        // Variantes de number
        TYPE_NORMALIZATION.put("integer",  "number");
        TYPE_NORMALIZATION.put("int",      "number");
        TYPE_NORMALIZATION.put("long",     "number");
        TYPE_NORMALIZATION.put("double",   "number");
        TYPE_NORMALIZATION.put("float",    "number");
        TYPE_NORMALIZATION.put("decimal",  "number");
        TYPE_NORMALIZATION.put("Number",   "number");
        TYPE_NORMALIZATION.put("Integer",  "number");
        TYPE_NORMALIZATION.put("Long",     "number");
        TYPE_NORMALIZATION.put("Double",   "number");
        TYPE_NORMALIZATION.put("Float",    "number");
        TYPE_NORMALIZATION.put("Decimal",  "number");
        // Variantes de boolean
        TYPE_NORMALIZATION.put("bool",     "boolean");
        TYPE_NORMALIZATION.put("Boolean",  "boolean");
        // Variantes de string
        TYPE_NORMALIZATION.put("String",   "string");
        TYPE_NORMALIZATION.put("text",     "string");
        TYPE_NORMALIZATION.put("varchar",  "string");
        // Variantes de dateTime
        TYPE_NORMALIZATION.put("datetime",      "dateTime");
        TYPE_NORMALIZATION.put("date-time",      "dateTime");
        TYPE_NORMALIZATION.put("date_time",      "dateTime");
        TYPE_NORMALIZATION.put("DateTime",       "dateTime");
        TYPE_NORMALIZATION.put("timestamp",      "dateTime");
        // Variantes de date
        TYPE_NORMALIZATION.put("Date",     "date");
        // Variantes de time
        TYPE_NORMALIZATION.put("Time",     "time");
    }

    public String transform(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            normalizeTypeRefs(doc);
            Map<String, String> inputTypeMap = buildInputTypeMap(doc);

            addVariablesToInputData(doc, inputTypeMap);
            addVariablesToDecisions(doc);

            String result = serializeDocument(doc);
            log.debug("XML transformado OK. inputData mapeados: {}", inputTypeMap);
/*  Bloque para generar el archivo XML transformado para pruebas.
            try {
                java.nio.file.Files.writeString(
                    java.nio.file.Path.of("C:\\www\\xml-transformado.xml"),
                    result,
                    StandardCharsets.UTF_8
                );
                log.info("XML transformado volcado en C:\\www\\xml-transformado.xml");
            } catch (Exception ex) {
                log.warn("No se pudo escribir xml-transformado.xml: {}", ex.getMessage());
            }
             */
            return result;

        } catch (Exception e) {
            log.warn("Transformacion XML fallo, se usa el original: {}", e.getMessage());
            return xml;
        }
    }

    // Recorre todos los elementos del documento y normaliza el atributo typeRef
    private void normalizeTypeRefs(Document doc) {
        NodeList all = doc.getElementsByTagNameNS("*", "*");
        for (int i = 0; i < all.getLength(); i++) {
            Node node = all.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) node;
            if (el.hasAttribute("typeRef")) {
                String original = el.getAttribute("typeRef");
                String normalized = TYPE_NORMALIZATION.getOrDefault(original, original);
                if (!normalized.equals(original)) {
                    el.setAttribute("typeRef", normalized);
                    log.info("typeRef normalizado: '{}' -> '{}' en <{}>", original, normalized, el.getLocalName());
                }
            }
        }
    }

    // Construye mapa: nombre del inputData -> typeRef
    // Busca en <inputExpression> cuyo <text> coincide con el nombre
    private Map<String, String> buildInputTypeMap(Document doc) {
        Map<String, String> map = new HashMap<>();
        NodeList expressions = doc.getElementsByTagNameNS(DMN_NS, "inputExpression");
        for (int i = 0; i < expressions.getLength(); i++) {
            Element expr = (Element) expressions.item(i);
            String typeRef = expr.getAttribute("typeRef");
            NodeList texts = expr.getElementsByTagNameNS(DMN_NS, "text");
            if (texts.getLength() > 0) {
                String varName = texts.item(0).getTextContent().trim();
                if (!varName.isEmpty()) {
                    map.put(varName, typeRef.isEmpty() ? "string" : typeRef);
                }
            }
        }
        return map;
    }

    private void addVariablesToInputData(Document doc, Map<String, String> inputTypeMap) {
        NodeList inputDataList = doc.getElementsByTagNameNS(DMN_NS, "inputData");
        for (int i = 0; i < inputDataList.getLength(); i++) {
            Element inputData = (Element) inputDataList.item(i);
            String name    = inputData.getAttribute("name");
            String id      = inputData.getAttribute("id");
            String typeRef = inputTypeMap.getOrDefault(name, "string");

            Element existing = findChildElement(inputData, "variable");
            if (existing != null) {
                if (!name.equals(existing.getAttribute("name"))) {
                    log.info("variable.name corregido en inputData '{}': '{}' -> '{}'", id, existing.getAttribute("name"), name);
                    existing.setAttribute("name", name);
                }
                continue;
            }

            Element variable = doc.createElementNS(DMN_NS, "variable");
            variable.setAttribute("id", id + "_var");
            variable.setAttribute("name", name);
            variable.setAttribute("typeRef", typeRef);
            inputData.appendChild(variable);
            log.info("variable agregado a inputData '{}' typeRef='{}'", name, typeRef);
        }
    }

    private void addVariablesToDecisions(Document doc) {
        NodeList decisions = doc.getElementsByTagNameNS(DMN_NS, "decision");
        for (int i = 0; i < decisions.getLength(); i++) {
            Element decision = (Element) decisions.item(i);
            String name    = decision.getAttribute("name");
            String id      = decision.getAttribute("id");

            Element existing = findChildElement(decision, "variable");
            if (existing != null) {
                if (!name.equals(existing.getAttribute("name"))) {
                    log.info("variable.name corregido en decision '{}': '{}' -> '{}'", id, existing.getAttribute("name"), name);
                    existing.setAttribute("name", name);
                }
                continue;
            }

            String typeRef = getDecisionOutputTypeRef(decision);

            Element variable = doc.createElementNS(DMN_NS, "variable");
            variable.setAttribute("id", id + "_var");
            variable.setAttribute("name", name);
            variable.setAttribute("typeRef", typeRef);

            // Insertar antes del primer elemento hijo (informationRequirement, etc.)
            Node firstElement = firstElementChild(decision);
            if (firstElement != null) {
                decision.insertBefore(variable, firstElement);
            } else {
                decision.appendChild(variable);
            }
            log.info("variable agregado a decision '{}' typeRef='{}'", name, typeRef);
        }
    }

    private String getDecisionOutputTypeRef(Element decision) {
        NodeList outputs = decision.getElementsByTagNameNS(DMN_NS, "output");
        if (outputs.getLength() > 0) {
            String typeRef = ((Element) outputs.item(0)).getAttribute("typeRef");
            return typeRef.isEmpty() ? "string" : typeRef;
        }
        return "string";
    }

    private Element findChildElement(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return (Element) child;
            }
        }
        return null;
    }

    private Node firstElementChild(Element parent) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return children.item(i);
            }
        }
        return null;
    }

    private String serializeDocument(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
