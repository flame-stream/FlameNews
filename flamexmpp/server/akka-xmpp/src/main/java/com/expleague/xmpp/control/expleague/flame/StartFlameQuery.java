package com.expleague.xmpp.control.expleague.flame;

import com.expleague.server.services.FlameConfigService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.control.expleague.BestAnswerQuery;
import com.spbsu.flamestream.runtime.WorkerApplication;
import com.spbsu.flamestream.runtime.utils.DumbInetSocketAddress;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "query", namespace = StartFlameQuery.NS)
public class StartFlameQuery extends Item {
    public static final String NS = "http://expleague.com/scheme/start-stream";
    static {
        XMPPServices.register(NS, FlameConfigService.class, "start-stream");
    }

    @XmlAttribute
    private String zkString;

    @XmlAttribute
    private String id;

    @XmlAttribute
    private String snapshotPath;

    @XmlAttribute
    private WorkerApplication.Guarantees guarantee;

    @XmlAttribute
    private String host;

    @XmlAttribute
    private int port;

    private DumbInetSocketAddress socketAddress;

    public String getId() {
        return id;
    }

    public String getZkString() {
        return zkString;
    }

    public String getSnapshotPath() {
        return snapshotPath;
    }

    public DumbInetSocketAddress getSocketAddress() {
        if (socketAddress == null)
            socketAddress = new DumbInetSocketAddress(host, port);
        return socketAddress;
    }

    public WorkerApplication.Guarantees getGuarantee() {
        return guarantee;
    }

    public StartFlameQuery() {
    }

    public StartFlameQuery(String id, String zkString, String host, int port, WorkerApplication.Guarantees guarantee) {
        this.id = id;
        this.zkString = zkString;
        this.host = host;
        this.port = port;
        this.guarantee = guarantee;
    }
}
