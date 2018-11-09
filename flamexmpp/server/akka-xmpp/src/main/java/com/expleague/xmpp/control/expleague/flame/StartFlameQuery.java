package com.expleague.xmpp.control.expleague.flame;

import com.expleague.server.services.FlameConfigService;
import com.expleague.server.services.XMPPServices;
import com.expleague.xmpp.Item;
import com.spbsu.flamestream.runtime.WorkerApplication;
import com.spbsu.flamestream.runtime.utils.DumbInetSocketAddress;

import javax.xml.bind.annotation.XmlAttribute;

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
        return socketAddress;
    }

    public WorkerApplication.Guarantees getGuarantee() {
        return guarantee;
    }
}
