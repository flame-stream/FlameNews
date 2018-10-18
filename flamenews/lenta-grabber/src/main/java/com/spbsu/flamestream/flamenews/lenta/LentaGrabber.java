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
      log.info(e.getMessage());
    }
  }

  public static void main(String[] args) throws IOException, JAXBException, InterruptedException {

    final String urlLenta = args[0]; // "https://lenta.ru/rss/news";
    final String directory = args[1]; //  "../news/";

    final JAXBContext jaxbContext = JAXBContext.newInstance(RSS.class);
    final Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    final HttpClient client = new DefaultHttpClient();
    final HttpGet request = new HttpGet(urlLenta);
    Item oldItem = null;

    while(!Thread.currentThread().isInterrupted()) {
      final HttpResponse response = client.execute(request);
      final BufferedReader rd = new BufferedReader
              (new InputStreamReader(
                      response.getEntity().getContent()));
      final StringBuilder resp = new StringBuilder();
      String line = "";
      while ((line = rd.readLine()) != null) {
        resp.append(line);
      }
      final StreamSource xml = new StreamSource(new StringReader(resp.toString()));
      final RSS rss = (RSS) jaxbUnmarshaller.unmarshal(xml);
      if (oldItem != null) {
        for (Item i : rss.getChannel().getItems()) {
          if (i.getPubDate().equals(oldItem.getPubDate())
                  && i.getTitle().equals(oldItem.getTitle())) {
            break;
          }
          final Document doc = Jsoup.connect(i.getLink()).get();
          final Element item = doc.getElementById("root");
          final Elements links = item.getElementsByTag("p");
          final StringBuilder builder = new StringBuilder();
          for (Element link : links) {
            builder.append(link.text()).append(" ");
          }
          addNewFile(directory, new News(i.getTitle(),
                  i.getCategory(),
                  builder.toString()));
        }
      }
      oldItem = rss.getChannel().getItems().get(0);
      Thread.sleep(60 * 1000);
    }
  }
}