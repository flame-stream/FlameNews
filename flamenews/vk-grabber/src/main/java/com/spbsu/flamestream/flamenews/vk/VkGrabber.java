package com.spbsu.flamestream.flamenews.vk;

import com.spbsu.flamestream.flamenews.commons.JabberClient;
import com.spbsu.flamestream.flamenews.commons.utils.RpsMeasurer;
import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.ServiceActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.streaming.responses.GetServerUrlResponse;
import com.vk.api.sdk.objects.wall.WallPostFull;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    final String jid = args[2];
    final String password = args[3];
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

      final int dogIndex = jid.indexOf('@');
      final JabberClient client = new JabberClient(
        jid.substring(0, dogIndex),
        jid.substring(dogIndex + 1, jid.length()),
        password
      );
      client.online();

      final RpsMeasurer rpsMeasurer = new RpsMeasurer();
      while (!Thread.currentThread().isInterrupted()) {
        final CountDownLatch latch = new CountDownLatch(1);
        streamingClient.stream().get(streamingActor, new StreamingEventHandler() {
          @Override
          public void handle(StreamingCallbackMessage message) {
            logger.info("RECEIVED: " + message);
            final String type = message.getEvent().getEventType().name();
            String postText;
            String commentText = "";
            if (type.equals("COMMENT")) {
              try {
                commentText = "<comment:text>\n" + message.getEvent().getText() + "\n</comment:text>\n";
                final String id = message.getEvent().getEventId().getPostOwnerId() + "_" + message.getEvent().getEventId().getPostId();
                final List<WallPostFull> r = vkClient.wall().getById(actor, id).execute();
                postText = "<post:text>\n" + r.get(0).getText() + "\n</post:text>\n";
              } catch (ApiException | ClientException e) {
                logger.info("FAIL GET POST: " + message.getEvent().getEventId().getPostOwnerId() +
                        "_" + message.getEvent().getEventId().getPostId());
                return;
              }
            }
            else {
              postText = "<post:text>\n" + message.getEvent().getText() + "\n</post :text>\n";
            }
            final String textMessage = "<message  xmlns:post = \"flamestream/post\"\n" +
                    "    xmlns:comment = \"flamestream/comment\">\n" +
                    postText +
                    commentText +
                    "</message>";
            client.send(Instant.ofEpochSecond(message.getEvent().getCreationTime()), textMessage);
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

      client.offline();
    } catch (Exception e) {
      logger.warning(e.toString());
      streamingClient.getAsyncHttpClient().close();
    }
  }
}
