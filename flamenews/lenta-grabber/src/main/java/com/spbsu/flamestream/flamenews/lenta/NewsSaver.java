package com.spbsu.flamestream.flamenews.lenta;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import com.spbsu.flamestream.flamenews.lenta.model.News;

public class NewsSaver implements SaveToFile {
  private static Logger log = Logger.getLogger(NewsSaver.class.getName());

  private final String dirPath;
  private final String lastSavedNews;
  private int prevName;

  NewsSaver(String dirPath, String lastSavedNews, int prevName) {
    this.dirPath = dirPath;
    this.lastSavedNews = lastSavedNews;
    this.prevName = prevName;
  }

  public void save(News news) {
    final File theDir = new File(dirPath);
    if (!theDir.exists()) {
      log.info("Creating directory: " + dirPath);
      try {
        if (!theDir.mkdir()) {
          throw new RuntimeException("Can't create dir: " + dirPath);
        }
        log.info("Directory created");
      } catch (SecurityException e) {
        log.info(e.getMessage());
        throw new RuntimeException(e);
      }
    }

    final File saveConfig = new File(lastSavedNews);
    if (!saveConfig.exists()) {
      log.info("Creating file: " + lastSavedNews);
      try {
        if (!saveConfig.createNewFile()) {
          throw new RuntimeException("Can't create file: " + dirPath);
        }
        log.info("File created");
      } catch (IOException e) {
        log.info(e.getMessage());
        throw new RuntimeException(e);
      }
    }

    try {
      final FileReader fr = new FileReader(lastSavedNews);
      final BufferedReader reader = new BufferedReader(fr);
      LocalDateTime pubDate = null;
      String pubTitle = null;
      try {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        pubDate = LocalDateTime.parse(reader.readLine(), formatter);
        pubTitle = reader.readLine();
      } catch (IOException e) {
        log.info(e.getMessage());
      } catch (NullPointerException e) {
        log.info("pubDate or pubTitle is null");
      }

      if (pubDate == null || pubTitle == null ||
              (pubDate.isBefore(news.getPubDate()) ||
                      (pubDate.isEqual(news.getPubDate()) && !pubTitle.equals(news.getTitle())))) {
        int curNameOfFile = 0;
        final File[] files = theDir.listFiles();
        if (files == null) {
          throw new RuntimeException("Can't get list files");
        }

        if (prevName < 0) {
          for (File file : files) {
            String name = file.getName();
            int num;
            try {
              num = Integer.parseInt(name.substring(0, name.lastIndexOf(".")));
            } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
              continue;
            }
            if (num > curNameOfFile) {
              curNameOfFile = num;
            }
          }
        } else {
          curNameOfFile = prevName;
        }

        curNameOfFile++;
        try {
          final String filePath = dirPath + String.valueOf(curNameOfFile) + ".xml";
          final FileWriter writer = new FileWriter(filePath, false);
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
        } catch (IOException e) {
          log.info(e.getMessage());
          throw new RuntimeException(e);
        }

        try {
          final FileWriter writer = new FileWriter(lastSavedNews, false);
          writer.write(news.getPubDate().toString());
          writer.append('\n');
          writer.write(news.getTitle());
          writer.append('\n');
          writer.flush();
        } catch (IOException e) {
          log.info(e.getMessage());
          throw new RuntimeException(e);
        }

        prevName = curNameOfFile;
      }
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }


  }
}