package com.expleague.xmpp.control.expleague.flame;

import com.expleague.xmpp.Item;
import com.spbsu.flamestream.runtime.edge.akka.AkkaFront;
import com.spbsu.flamestream.runtime.edge.akka.AkkaRear;
import com.spbsu.flamestream.runtime.serialization.KryoSerializer;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "query", namespace = ConsumerQuery.NS)
public class ConsumerQuery extends Item {
    public static final String NS = "http://expleague.com/scheme/consumer";

    @XmlAttribute
    private byte[] serializeFront;
    @XmlAttribute
    private byte[] serializeRier;


    public ConsumerQuery() {}

    public ConsumerQuery(AkkaFront.FrontHandle<Object> front, AkkaRear.Handle<String> rier) {
        serializeFront = new KryoSerializer().serialize(front);
        serializeRier = new KryoSerializer().serialize(rier);
    }

    public byte[] getSerializeFront() {
        return serializeFront;
    }
    public byte[] getSerializeRier() {
        return serializeRier;
    }
}
