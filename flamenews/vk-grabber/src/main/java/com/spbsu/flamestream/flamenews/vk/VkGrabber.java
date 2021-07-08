package com.spbsu.flamestream.flamenews.vk;

import com.spbsu.flamestream.flamenews.commons.utils.RpsMeasurer;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.ServiceActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.streaming.responses.GetServerUrlResponse;
import com.vk.api.sdk.streaming.clients.StreamingEventHandler;
import com.vk.api.sdk.streaming.clients.VkStreamingApiClient;
import com.vk.api.sdk.streaming.clients.actors.StreamingActor;
import com.vk.api.sdk.streaming.objects.StreamingCallbackMessage;
import com.vk.api.sdk.streaming.objects.StreamingRule;
import com.vk.api.sdk.streaming.objects.responses.StreamingGetRulesResponse;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketListener;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Unchecked;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class VkGrabber {
  public static void main(String[] args) throws Exception {
    if (args.length != 4) {
      throw new IllegalArgumentException(
        "Parameters number is invalid. Please set {VK app id} {VK access token} {Jabber JID} {Jabber password}");
    }

    final int appId = Integer.parseInt(args[0]);
    final String accessToken = args[1];
    //final OutputStrategy strategy = new XmppStrategy(args[2],  args[3]);
    // TODO: extract constants(port should be as in config -- bench-source-port)
    final OutputStrategy strategy = new SocketStrategy("localhost", 4567);

    processVkStream(appId, accessToken, strategy);
  }

  private static void processVkStream(int appId, String accessToken, OutputStrategy strategy)
          throws ClientException, ApiException, IOException {
    final Logger logger = Logger.getLogger(VkGrabber.class.getName());

    final TransportClient transportClient = new HttpTransportClient();
    final VkApiClient vkClient = new VkApiClient(transportClient);

    final ServiceActor actor = new ServiceActor(appId, accessToken);
    final GetServerUrlResponse getServerUrlResponse = vkClient.streaming().getServerUrl(actor).execute();
    final StreamingActor streamingActor = new StreamingActor(
            getServerUrlResponse.getEndpoint(),
            getServerUrlResponse.getKey()
    );

    final VkStreamingApiClient streamingClient = new VkStreamingApiClient(transportClient);
    try {
      final String[] keyWords = new String[]{"и", "в", "не", "на", "я", "быть", "с", "он", "что", "а"};
      final StreamingGetRulesResponse allRules = streamingClient.rules().get(streamingActor).execute();
      final List<StreamingRule> rules = allRules.getRules() != null ? allRules.getRules() : new ArrayList<>();
      final Set<String> ruleWords = rules.stream().map(StreamingRule::getValue).collect(Collectors.toSet());
      if (!ruleWords.equals(new HashSet<>(Arrays.asList(keyWords)))) {
        rules.forEach(
                Unchecked.consumer(rule -> streamingClient.rules().delete(streamingActor, rule.getTag()).execute())
        );
        Seq.seq(keyWords, 0, keyWords.length).zipWithIndex()
                .forEach(Unchecked.consumer(
                        tuple -> streamingClient.rules().add(streamingActor, String.valueOf(tuple.v2), tuple.v1).execute())
                );
      }

      final RpsMeasurer rpsMeasurer = new RpsMeasurer();
      while (!Thread.currentThread().isInterrupted()) {
        final CountDownLatch latch = new CountDownLatch(1);
        streamingClient.stream().get(streamingActor, new StreamingEventHandler() {
          @Override
          public void handle(StreamingCallbackMessage message) {
            logger.info("RECEIVED: " + message);
            strategy.processMessage(message.getEvent().getCreationTime(), message.getEvent().getText());

            rpsMeasurer.logRequest();
            logger.info("AVERAGE RPS: " + rpsMeasurer.currentAverageRps());
          }
        }).execute().addWebSocketListener(new WebSocketListener() {
          @Override
          public void onOpen(WebSocket webSocket) {
          }

          @Override
          public void onClose(WebSocket webSocket) {
            latch.countDown();
          }

          @Override
          public void onError(Throwable throwable) {
            latch.countDown();
          }
        });
        latch.await();
      }

      strategy.close();
    } catch (Exception e) {
      logger.warning(e.toString());
      streamingClient.getAsyncHttpClient().close();
    }
  }
}
