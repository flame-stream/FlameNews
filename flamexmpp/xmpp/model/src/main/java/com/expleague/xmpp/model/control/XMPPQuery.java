package com.expleague.xmpp.model.control;

import com.expleague.xmpp.model.Item;
import com.expleague.xmpp.model.stanza.Iq;

import javax.xml.bind.annotation.XmlTransient;

/**
 * User: solar
 * Date: 14.12.15
 * Time: 18:35
 */
@XmlTransient
public abstract class XMPPQuery extends Item {
  public abstract Item reply(Iq.IqType type);
}
