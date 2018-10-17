package com.spbsu.flamestream.flamenews.lenta.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import java.util.LinkedList;
import java.util.List;

@XmlRootElement(name = "channel")
public class Channel {

  @XmlElement(name = "language")
  private String language;
  @XmlElement(name = "title")
  private String title;
  @XmlElement(name = "description")
  private String description;
  @XmlElement(name = "link")
  private String link;
  @XmlElement(name = "item")
  private List<Item> items = new LinkedList<>();

  @XmlTransient
  public String getLanguage() {
    return language;
  }

  public void setLanguage(String language) {
    this.language = language;
  }

  @XmlTransient
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @XmlTransient
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
  @XmlTransient
  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  @XmlTransient
  public List<Item> getItems() {
    return items;
  }
}