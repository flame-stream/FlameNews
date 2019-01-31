package com.expleague.xmpp.control.expleague.flame;

import com.expleague.xmpp.Item;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Arrays;

@XmlRootElement(name = "query", namespace = ConsumerQuery.NS)
public class ConsumerQuery extends Item {
    public static final String NS = "http://expleague.com/scheme/consumer";

    @XmlAttribute
    private byte[] serializedFront;
    @XmlAttribute
    private byte[] serializedRear;

    public ConsumerQuery() {}

    public ConsumerQuery(byte[] front, byte[] rear) {
        serializeFront = Arrays.copyOf(front, front.length);
        serializeRear = Arrays.copyOf(rear, rear.length);
    }

    public byte[] getSerializeFront() {
        return serializeFront;
    }

    public byte[] getSerializeRear() {
        return serializeRear;
    }
}
