package com.expleague.xmpp.control.expleague.flame;

import com.expleague.server.services.GraphLoadService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Item;
import com.spbsu.flamestream.core.Graph;
import com.spbsu.flamestream.runtime.serialization.JacksonSerializer;
import com.spbsu.flamestream.runtime.serialization.KryoSerializer;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "query", namespace = GraphQuery.NS)
public class GraphQuery extends Item {
    public static final String NS = "http://expleague.com/scheme/graph";
    static {
        XMPPServices.register(NS, GraphLoadService.class, "graph");
    }
    @XmlAttribute
    private byte[] SerializeGraph;

    public GraphQuery(){}

    public GraphQuery(Graph graph) {
        SerializeGraph = new KryoSerializer().serialize(graph);
    }

    public byte[] getSerializeGraph() {
        return SerializeGraph;
    }
}
