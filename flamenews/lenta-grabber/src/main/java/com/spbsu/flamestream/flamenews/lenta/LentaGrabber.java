package com.spbsu.flamestream.flamenews.lenta;

import com.spbsu.flamestream.flamenews.lenta.model.Item;
import com.spbsu.flamestream.flamenews.lenta.model.News;
import com.spbsu.flamestream.flamenews.lenta.model.RSS;
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
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class LentaGrabber {

  private static Logger log = Logger.getLogger(LentaGrabber.class.getName());

  private static void addNewFile (String dirPath, News news) {
    int curNameOfFile = 0;
    File theDir = new File(dirPath);
    if (!theDir.exists()) {
      log.info("Creating directory: " + dirPath);
      try{
        theDir.mkdir();
        log.info("Directory created");
      }
      catch(SecurityException e){
        log.info(e.getMessage());
      }
    }

    File[] files = theDir.listFiles();
    for (File file : files) {
      String name = file.getName();
      int num;
      try {
        num = Integer.parseInt(name.substring(0, name.lastIndexOf(".")));
      } catch (NumberFormatException e) {
        continue;
      }
      if (num > curNameOfFile) {
        curNameOfFile = num;
      }
    }

    curNameOfFile++;
    String filePath = dirPath + String.valueOf(curNameOfFile) + ".xml";
    try {
      FileWriter writer = new FileWriter(filePath, false);
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      writer.append('\n');
      writer.write("<item>");
      writer.append('\n');
      writer.write("<title>" + news.getTitle() + "</title>");
      writer.append('\n');
      writer.write("<text>" + news.getText() + "</text>");
      writer.append('\n');
      writer.write("<category>" + news.getCategory() + "</category>");
      writer.append('\n');
      writer.write("</item>");
      writer.flush();
    }
    catch (IOException e) {
      System.out.println(e.getMessage());
    }
  }

  public static void main(String[] args) throws IOException, JAXBException, InterruptedException {

    final String URL_LENTA = args[0]; // "https://lenta.ru/rss/news";
    final String directory = args[1]; //  "../news/";

    List<News> news = new ArrayList<>();
    StreamSource xml = new StreamSource(new StringReader(sendRequest(URL_LENTA)));
    JAXBContext jaxbContext = JAXBContext.newInstance(RSS.class);
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    Item oldItem = null;
    for(;;) {
      RSS newrss = null;
      if (oldItem == null) {
        RSS rss = (RSS) jaxbUnmarshaller.unmarshal(xml);
        oldItem = rss.getChannel().getItems().get(0);
      } else {
        newrss = (RSS) jaxbUnmarshaller.unmarshal(xml);
        List<Item> newItems = newrss.getChannel().getItems();
        int num = 0;
        for (int i = 0; i < newItems.size(); i++) {
          if (newItems.get(i).getPubDate().equals(oldItem.getPubDate())
                  && newItems.get(i).getTitle().equals(oldItem.getTitle())) {
            num = i;
            break;
          }
        }
        if (num > 0) {
          log.info("news: " + num);
          for (int i = 0; i < num; i++) {
            log.info(newrss.getChannel().getItems().get(i).getTitle());
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
      for (News n : news) {
        addNewFile(directory, n);
      }
      news.clear();
      Thread.sleep(60 * 1000);
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