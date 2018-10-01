package com.expleague.model;

import com.expleague.xmpp.JID;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Experts League
 * Created by solar on 04/03/16.
 */
@XmlRootElement(name = "experts-filter")
public class Filter extends Attachment {

  @XmlElement(namespace = Operations.NS)
  private List<JID> reject;

  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection", "unused"})
  @XmlElement(namespace = Operations.NS)
  private List<JID> accept;

  @XmlElement(namespace = Operations.NS)
  private List<JID> prefer;

  public Filter() {
  }

  public Filter(List<JID> accept, List<JID> reject, List<JID> prefer) {
    this.accept = accept;
    this.reject = reject;
    this.prefer = prefer;
  }

  public boolean fit(JID who) {
    if (accept != null)
      return accept.contains(who);
    else if (reject != null)
      return !reject.contains(who);
    return true;
  }

  public void reject(JID slacker) {
    if (reject == null)
      reject = new ArrayList<>();
    if (accept != null) {
      accept.remove(slacker);
      if (accept.isEmpty())
        accept = null;
    }
    if (prefer != null) {
      prefer.remove(slacker);
      if (prefer.isEmpty())
        prefer = null;
    }
    if (!reject.contains(slacker))
      reject.add(slacker);
  }

  public void prefer(JID... worker) {
    if (prefer == null)
      prefer = new ArrayList<>();
    for (int i = 0; i < worker.length; i++) {
      if (prefer.contains(worker[i]) || (accept != null && accept.contains(worker[i])))
        continue;
      prefer.add(worker[i]);
    }
  }

  public boolean isPrefered(JID jid) {
    if (accept != null)
      return accept.contains(jid);
    return prefer != null && prefer.contains(jid) && (reject == null || !reject.contains(jid));
  }

  public Stream<JID> rejected() {
    return reject != null ? reject.stream() : Stream.empty();
  }

  public Stream<JID> accepted() {
    return accept != null ? accept.stream() : Stream.empty();
  }

  public Stream<JID> preferred() {
    return prefer != null ? prefer.stream() : Stream.empty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Filter filter = (Filter) o;

    if (reject != null ? !reject.equals(filter.reject) : filter.reject != null) return false;
    if (accept != null ? !accept.equals(filter.accept) : filter.accept != null) return false;
    return prefer != null ? prefer.equals(filter.prefer) : filter.prefer == null;
  }

  @Override
  public int hashCode() {
    int result = reject != null ? reject.hashCode() : 0;
    result = 31 * result + (accept != null ? accept.hashCode() : 0);
    result = 31 * result + (prefer != null ? prefer.hashCode() : 0);
    return result;
  }
}
