package com.expleague.util.xml.performance;

import com.expleague.xmpp.model.Item;
import com.expleague.xmpp.model.JID;
import com.expleague.xmpp.model.stanza.Message;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.math.Quantiles.percentiles;

public class JaxbPerformanceTest {

  @Test
  public void performanceTest() {
    final List<Long> times = new ArrayList<>();
    final JID from = JID.parse("from@test");
    final JID to = JID.parse("to@test");

    final int iterations = 100000;
    final int inners = 20;

    for (int i = 0; i < iterations; i++) {
      final Message[] innerMessages = new Message[inners];
      for (int j = 0; j < inners; j++) {
        innerMessages[j] = new Message(from, to, RandomStringUtils.randomAlphanumeric(100));
      }
      final Message message = new Message(from, to, innerMessages);
      final String xmlMessage = message.xmlString();

      final long start = System.nanoTime();
      final Item item = Item.create(xmlMessage);
      times.add(System.nanoTime() - start);

      Assert.assertTrue(item != null);
      Assert.assertEquals(xmlMessage, item.xmlString());
    }

    final long[] dataset = times.stream().skip(10000).mapToLong(l -> l).toArray();
    final int[] percentiles = {50, 75, 95, 99};
    for (int percentile : percentiles) {
      System.out.println(percentile + " %ntile: " + percentiles().index(percentile).compute(dataset));
    }
  }
}
