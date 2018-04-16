//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.8-b130911.1802 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.12.11 at 11:34:34 PM MSK 
//


package com.expleague.xmpp.model.stanza.data;

import org.jetbrains.annotations.Nullable;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.namespace.QName;


/**
 * <p>Java class for anonymous complex type.
 *
 * <p>The following schema fragment specifies the expected content contained within this class.
 *
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;group ref="{urn:ietf:params:xml:ns:xmpp-stanzas}stanzaErrorGroup"/>
 *         &lt;element ref="{urn:ietf:params:xml:ns:xmpp-stanzas}text" minOccurs="0"/>
 *       &lt;/sequence>
 *       &lt;attribute name="by" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="type" use="required">
 *         &lt;simpleType>
 *           &lt;restriction base="{http://www.w3.org/2001/XMLSchema}NMTOKEN">
 *             &lt;enumeration value="auth"/>
 *             &lt;enumeration value="cancel"/>
 *             &lt;enumeration value="continue"/>
 *             &lt;enumeration value="modify"/>
 *             &lt;enumeration value="wait"/>
 *           &lt;/restriction>
 *         &lt;/simpleType>
 *       &lt;/attribute>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "error", namespace = "urn:ietf:params:xml:ns:xmpp-stanzas")
public class Err extends Item {
  @XmlElement(namespace = "urn:ietf:params:xml:ns:xmpp-stanzas")
  private Text text;

  @XmlAttribute(name = "by")
  private String by;

  @XmlAttribute(name = "type", required = true)
  @XmlJavaTypeAdapter(type = ErrType.class, value = ErrType.TypeAdapter.class)
  private ErrType type;

  @XmlAnyElement
  @XmlJavaTypeAdapter(type = JAXBElement.class, value = Cause.CauseAdapter.class)
  private Cause cause;

  public Err() {}

  public Err(Cause cause, ErrType type, @Nullable String message) {
    this.cause = cause;
    this.type = type;
    this.text = message != null ? new Text("en", message) : null;
  }

  @XmlEnum
  public enum ErrType {
    AUTH,
    CANCEL,
    CONTINUE,
    MODIFY,
    WAIT;

    static class TypeAdapter extends XmlAdapter<String, ErrType> {
      @Override
      public String marshal(ErrType v) throws Exception {
        return v.name().toLowerCase();
      }

      @Override
      public ErrType unmarshal(String v) throws Exception {
        return ErrType.valueOf(v.toUpperCase());
      }
    }
  }

  @XmlEnum
  public enum Cause {
    CONFLICT,
    NOT_ALLOWED,
    SERVICE_UNAVAILABLE,
    INTERNAL_SERVER_ERROR;
    public static class CauseAdapter extends XmlAdapter<JAXBElement<?>, Cause> {
      @Override
      public Cause unmarshal(JAXBElement<?> v) throws Exception {
        final String propName = v.getName().getLocalPart().toUpperCase().replace('-', '_');
        return Cause.valueOf(propName);
      }

      @Override
      public JAXBElement<?> marshal(Cause v) throws Exception {
        final QName qName = new QName("urn:ietf:params:xml:ns:xmpp-stanzas", v.name().toLowerCase().replace('_', '-'));
        //noinspection unchecked
        return new JAXBElement(qName, Cause.class, null);
      }
    }
  }

}
