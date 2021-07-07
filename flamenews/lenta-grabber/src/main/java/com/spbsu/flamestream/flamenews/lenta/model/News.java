package com.spbsu.flamestream.flamenews.lenta.model;

import java.time.LocalDateTime;

public class News {

  private String title;
  private String category;
  private String text;
  private LocalDateTime pubDate;

  public News(String title, String category, String text, LocalDateTime pubDate) {
    this.text = text;
    this.category =  category;
    this.title = title;
    this.pubDate = pubDate;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public LocalDateTime getPubDate() {
    return pubDate;
  }

  public void setPubDate(LocalDateTime pubDate) {
    this.pubDate = pubDate;
  }

  @Override
  public String toString() {
    return "News{" +
            "title='" + title + '\'' +
            ", category='" + category + '\'' +
            ", text='" + text + '\'' +
            '}';
  }
}