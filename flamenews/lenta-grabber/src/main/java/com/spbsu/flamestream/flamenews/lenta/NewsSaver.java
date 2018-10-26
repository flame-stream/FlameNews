package com.spbsu.flamestream.flamenews.lenta;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;
import com.spbsu.flamestream.flamenews.lenta.model.News;

public class NewsSaver implements SaveToFile {

  private static Logger log = Logger.getLogger(NewsSaver.class.getName());

  final private String dirPath;
  private int prevName;

  NewsSaver(String dirPath, int prevName) {
    this.dirPath = dirPath;
    this.prevName = prevName;
  }

  private void setPrevName(int prevName) {
    this.prevName = prevName;
  }

  private int getPrevName() {
    return this.prevName;
  }

  private String getDirPath() {
    return this.dirPath;
  }

  public void save(News news) {
    final File theDir = new File(this.getDirPath());
    if (!theDir.exists()) {
        log.info("Creating directory: " + dirPath);
        try{
            theDir.mkdir();
            log.info("Directory created");
        }
        catch(SecurityException e){
            log.info(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    int curNameOfFile = 0;
    File[] files = theDir.listFiles();
    if (this.getPrevName() < 0) {
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
    } else {
      curNameOfFile = prevName;
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
      throw new RuntimeException(e);
    }

    this.setPrevName(curNameOfFile);
  }
}