package com.spbsu.flamestream.flamenews.lenta.model;

import com.spbsu.flamestream.flamenews.lenta.model.Channel;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

@XmlRootElement(name = "rss")
public class RSS {

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