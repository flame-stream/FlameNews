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
import java.util.Map;


public class LentaGrabber {
  public static void main(String[] args) throws IOException, JAXBException, InterruptedException {
    final Map<String, String> env = System.getenv();
    final String urlLenta = env.getOrDefault("LENTA_URL", "https://lenta.ru/rss/news");
    final String directory = env.getOrDefault("OUT_DIR", "../news/");

    final JAXBContext jaxbContext = JAXBContext.newInstance(RSS.class);
    final Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    final HttpClient client = new DefaultHttpClient();
    final HttpGet request = new HttpGet(urlLenta);
    Item oldItem = null;

    final NewsSaver saveNews = new NewsSaver(directory, -1);
    while (!Thread.currentThread().isInterrupted()) {
      final HttpResponse response = client.execute(request);
      final BufferedReader rd = new BufferedReader
        (new InputStreamReader(
          response.getEntity().getContent()));
      final StringBuilder resp = new StringBuilder();
      String line;
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
          saveNews.save(new News(i.getTitle(), i.getCategory(), builder.toString()));
        }
      }
      oldItem = rss.getChannel().getItems().get(0);
      Thread.sleep(60 * 1000);
    }
  }
}