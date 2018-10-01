package com.expleague.xmpp;

import com.expleague.model.Operations;
import com.expleague.xmpp.stanza.Stanza;
import com.expleague.commons.system.RuntimeUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * User: solar
 * Date: 06.12.15
 * Time: 17:17
 */
@SuppressWarnings("unused")
@XmlRootElement
public class Stream {
  private static final Logger log = Logger.getLogger(Stream.class.getName());
  public static final String NS = "http://etherx.jabber.org/streams";
  @XmlAttribute
  private String version;
  @XmlAttribute(namespace = "http://www.w3.org/XML/1998/namespace")
  private String lang;
  @XmlAnyElement(lax = true)
  private List<Stanza> contents = new ArrayList<>();

  public String version() {
    return version;
  }

  public String lang() {
    return lang;
  }

  public void append(Stanza stanza) {
    contents.add(stanza);
  }

  private static final JAXBContext context;
  static {
    try {
      final List<Class> classesInPackage = Arrays.asList(
          RuntimeUtils.packageResourcesList(
              Stream.class.getPackage().getName(),
              Operations.class.getPackage().getName()
          ))
          .stream()
          .filter(p -> p.endsWith(".class"))
          .map(resource -> {
            try {
              final String name = resource.substring(0, resource.length() - ".class".length()).replace('/', '.').replace('\\', '.');
//              log.finest("Loading " + name + " to JAXB context");
              return Class.forName(name);
            }
            catch (ClassNotFoundException e) {
              throw new RuntimeException(e);
            }
          })
          .filter(c -> !c.isInterface())
          .filter(c -> !c.isAnonymousClass())
          .collect(Collectors.toList());
      context = JAXBContext.newInstance(classesInPackage.toArray(new Class[classesInPackage.size()]));
    }
    catch (JAXBException | IOException | URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
  public static JAXBContext jaxb() {
    return context;
  }

}
