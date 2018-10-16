package com.spbsu.flamestream.flamenews.lenta;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LentaGrabber {

    private static final String URL_LENTA = "https://lenta.ru/rss/news";

    public static void main(String[] args) throws IOException, JAXBException, InterruptedException {
        Item oldItem = null;
        for(;;) {
            List<News> news = new ArrayList<>();
            StreamSource xml = new StreamSource(new StringReader(sendRequest(URL_LENTA)));
            JAXBContext jaxbContext = JAXBContext.newInstance(RSS.class);
            Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
            RSS newrss = null;
            if (oldItem == null) {
                RSS rss = (RSS) jaxbUnmarshaller.unmarshal(xml);
                oldItem = rss.getChannel().getItems().get(0);
            } else {
                newrss = (RSS) jaxbUnmarshaller.unmarshal(xml);
                List<Item> newItems = newrss.getChannel().getItems();
                int num = 0;
                for (int i = 0; i < newItems.size(); i++) {
                    if (newItems.get(i).getPubDate().equals(oldItem.getPubDate())) {
                        num = i;
                        break;
                    }
                }
//                for (Item i : newrss.getChannel().getItems().subList(0, num)) {
//                    System.out.println(i.getPubDate());
//                }
                if (num > 0) {
                    System.out.println("news: " + num);
                    for (int i = 0; i < num; i++) {
                        System.out.println(newrss.getChannel().getItems().get(i).getTitle());
                    }
                }
                for (Item i : newrss.getChannel().getItems().subList(0, num)) {
                    Document doc = Jsoup.connect(i.getLink()).get();
                    Element item = doc.getElementById("root");
                    Elements links = item.getElementsByTag("p");
                    StringBuilder builder = new StringBuilder();
                    for (Element link : links) {
                        builder.append(link.text()).append(" ");
                    }
                news.add(new News(i.getTitle(), i.getCategory(), builder.toString()));
                }
                oldItem = newrss.getChannel().getItems().get(0);
            }
            Thread.sleep(60 * 1000);

            for (News n : news) {
                System.out.println(n.toString());
            }
        }

    }

    static String sendRequest(String url) throws IOException {
        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(url);
        HttpResponse response = client.execute(request);
        BufferedReader rd = new BufferedReader
                (new InputStreamReader(
                        response.getEntity().getContent()));
        StringBuilder resp = new StringBuilder();
        String line = "";
        while ((line = rd.readLine()) != null) {
            resp.append(line);
        }
        return resp.toString();
    }
}



class News {

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

@XmlRootElement(name = "rss")
class RSS {

    @XmlElement(name = "channel")
    private Channel channel;

    @XmlTransient
    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }
}



@XmlRootElement(name = "channel")
class Channel {

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


@XmlRootElement(name = "item")
class Item {

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