package com.spbsu.flamestream.flamenews.lenta.model;

public class News {

  private String title;
  private String category;
  private String text;

  public News(String title, String category, String text) {
    this.text = text;
    this.category =  category;
    this.title = title;
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

  @Override
  public String toString() {
    return "News{" +
            "title='" + title + '\'' +
            ", category='" + category + '\'' +
            ", text='" + text + '\'' +
            '}';
  }
}