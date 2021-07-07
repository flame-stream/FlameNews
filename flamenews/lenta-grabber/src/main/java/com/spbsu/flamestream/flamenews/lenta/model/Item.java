package com.spbsu.flamestream.flamenews.lenta.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement(name = "item")
public class Item {

  @XmlElement(name = "title")
  private String title;
  @XmlElement(name = "link")
  private String link;
  @XmlElement(name = "description")
  private String description;
  @XmlElement(name = "pubDate")
  private String pubDate;
  @XmlElement(name = "category")
  private String category;

  @XmlTransient
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @XmlTransient
  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  @XmlTransient
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @XmlTransient
  public String getPubDate() {
    return pubDate;
  }

  public void setPubDate(String pubDate) {
    this.pubDate = pubDate;
  }

  @XmlTransient
  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

}
