package com.expleague.xmpp.model.stanza.data;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;

/**
 * User: solar
 * Date: 12.12.15
 * Time: 15:03
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType
public class Text {
  @XmlValue
  protected String value;
  @XmlAttribute(name = "lang", namespace = "http://www.w3.org/XML/1998/namespace")
  @XmlSchemaType(name = "language")
  protected String lang;

  public Text() {}

  public Text(String lang, String message) {
    this.lang = lang;
    this.value = message;
  }
}
