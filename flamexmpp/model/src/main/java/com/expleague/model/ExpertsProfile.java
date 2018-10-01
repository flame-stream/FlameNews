package com.expleague.model;

import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;

import javax.xml.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 27/01/16.
 */

@SuppressWarnings("unused")
@XmlRootElement(name = "expert")
public class ExpertsProfile extends Item {
  @XmlAttribute
  private JID jid;

  @XmlAttribute
  private String login;

  @XmlAttribute
  private String name;

  @XmlAttribute
  private Integer tasks;

  @XmlAttribute
  private Education education = Education.MEDIUM;

  @XmlAttribute
  private Boolean available;

  @XmlAttribute
  private Authority authority;

  @XmlElementWrapper(name = "tags", namespace = Operations.NS)
  @XmlElements({@XmlElement(name = "tag", namespace = Operations.NS, type = Tag.class)})
  List<Tag> tags;

  @XmlAttribute
  double rating = 0;

  @XmlAttribute
  int basedOn = 0;

  @XmlElement(namespace = Operations.NS)
  private String avatar;

  public ExpertsProfile() {}

  public JID jid() {
    return jid;
  }

  private ExpertsProfile(JID jid) {
    this.jid = jid;
    login = jid.local();
  }

  public String login() {
    return login;
  }

  public String avatar() {
    return avatar;
  }

  public String name() {
    return name;
  }

  public Integer tasks() {
    return tasks;
  }

  public Education education() {
    return education;
  }

  public Boolean isAvailable() {
    return available;
  }

  public void available(boolean value) {
    this.available = value;
  }

  public Stream<Tag> tags() {
    return tags != null ? tags.stream() : Stream.empty();
  }

  public double rating() {
    return rating;
  }

  public int basedOn() {
    return basedOn;
  }

  public Authority authority() {
    return authority != null ? authority : Authority.NONE;
  }

  public ExpertsProfile shorten() {
    final ExpertsProfile profile = new ExpertsProfile();
    profile.jid = this.jid;
    profile.name = this.name;
    profile.authority = this.authority;
    profile.login = this.login;
    return profile;
  }

  @SuppressWarnings("unused")
  public static class Builder {
    private final ExpertsProfile result;
    private final Map<String, Stat> tags = new HashMap<>();
    public Builder(JID id) {
      result = new ExpertsProfile(id);
    }

    public Builder name(String name) {
      result.name = name;
      return this;
    }

    public Builder tasks(int tasks) {
      result.tasks = tasks;
      return this;
    }

    public Builder score(double score) {
      if (score > 0) {
        result.rating += score;
        result.basedOn++;
      }
      return this;
    }

    public Builder authority(Authority authority) {
      result.authority = authority;
      return this;
    }

    public Builder tag(String name, double score) {
      final Stat stat = tags.getOrDefault(name, new Stat());
      stat.weight ++;
      stat.scoreSum += score;
      tags.put(name, stat);
      return this;
    }

    public Builder avatar(String ava) {
      result.avatar = ava;
      return this;
    }

    public Builder available(boolean online) {
      result.available = online;
      return this;
    }

    public Builder education(Education degree) {
      result.education = degree;
      return this;
    }

    public ExpertsProfile build() {
      result.tags = tags.isEmpty() ? null : tags.entrySet().stream().map(
          entry -> new Tag(entry.getKey(), entry.getValue().scoreSum / (50 + entry.getValue().scoreSum))
      ).collect(Collectors.toList());
      if (result.basedOn > 0) {
        result.rating /= result.basedOn;
      }
      return result;
    }

    private static class Stat {
      double scoreSum;
      double weight;
    }
  }

  @XmlEnum
  public enum Education {
    @XmlEnumValue("doctor") WELL_DONE,
    @XmlEnumValue("phd") MEDIUM_WELL,
    @XmlEnumValue("high") MEDIUM,
    @XmlEnumValue("undergrad") MEDIUM_RARE,
    @XmlEnumValue("school") RARE,
    @XmlEnumValue("preschool") BLOODY,
  }

  @XmlEnum
  public enum Authority {
    @XmlEnumValue("admin") ADMIN(0),
    @XmlEnumValue("expert") EXPERT(1),
    @XmlEnumValue("newbie") NEWBIE(2),
    @XmlEnumValue("none") NONE(10),
    ;

    final int priority;

    Authority(int priority) {
      this.priority = priority;
    }

    public int priority() {
      return priority;
    }

    public static Authority valueOf(int index) {
      return Stream.of(Authority.values()).filter(s -> s.priority == index).findAny().orElse(null);
    }
  }
}
