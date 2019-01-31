package com.expleague.xmpp.control.expleague.flame;

import com.expleague.server.services.GraphLoadService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Item;
import com.spbsu.flamestream.core.Graph;
import com.spbsu.flamestream.runtime.serialization.KryoSerializer;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "query", namespace = GraphQuery.NS)
public class GraphQuery extends Item {
    public static final String NS = "http://expleague.com/scheme/graph";
    public static KrySer krySer = new KSer();

    static {
        XMPPServices.register(NS, GraphLoadService.class, "graph");

    }
    @XmlAttribute
    private byte[] serializedGraph;

    public GraphQuery(){}

    public GraphQuery(Graph graph) {
        serializeGraph = krySer.serialize(graph);
    }

  public Graph graph() {
    return krySer.deserialize(serializedGraph, Graph.class);
  }
}
