package com.spbsu.flamestream.flamenews.vk;

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
import com.vk.api.sdk.streaming.exceptions.StreamingApiException;
import com.vk.api.sdk.streaming.exceptions.StreamingClientException;
import com.vk.api.sdk.streaming.objects.StreamingCallbackMessage;
import com.vk.api.sdk.streaming.objects.StreamingRule;
import com.vk.api.sdk.streaming.objects.responses.StreamingGetRulesResponse;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketListener;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Unchecked;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LongSummaryStatistics;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Application {
    public static void main(String[] args) throws ClientException, ApiException, StreamingApiException, StreamingClientException, ExecutionException, InterruptedException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Parameters number is invalid. Please set {app id} {access token}");
        }

        final int appId = Integer.parseInt(args[0]);
        final String accessToken = args[1];

        final TransportClient transportClient = new HttpTransportClient();
        final VkApiClient vkClient = new VkApiClient(transportClient);
        final VkStreamingApiClient streamingClient = new VkStreamingApiClient(transportClient);

        final ServiceActor actor = new ServiceActor(appId, accessToken);
        final GetServerUrlResponse getServerUrlResponse = vkClient.streaming().getServerUrl(actor).execute();
        final StreamingActor streamingActor = new StreamingActor(
                getServerUrlResponse.getEndpoint(),
                getServerUrlResponse.getKey()
        );

        final String[] keyWords = new String[]{"и", "в", "не", "на", "я", "быть", "с", "он", "что", "а"};
        final StreamingGetRulesResponse allRules = streamingClient.rules().get(streamingActor).execute();
        final Set<String> ruleWords = allRules.getRules().stream().map(StreamingRule::getValue).collect(Collectors.toSet());
        if (!ruleWords.equals(new HashSet<>(Arrays.asList(keyWords)))) {
            allRules.getRules().forEach(
                    Unchecked.consumer(rule -> streamingClient.rules().delete(streamingActor, rule.getTag()).execute())
            );
            Seq.seq(keyWords, 0, keyWords.length).zipWithIndex()
                    .forEach(Unchecked.consumer(
                            tuple -> streamingClient.rules().add(streamingActor, String.valueOf(tuple.v2), tuple.v1))
                    );
        }

        final Logger logger = Logger.getLogger(Application.class.getName());
        final LongSummaryStatistics rpsStat = new LongSummaryStatistics();
        final long[] lastReceivedTs = {-1};

        while (!Thread.currentThread().isInterrupted()) {
            final CountDownLatch latch = new CountDownLatch(1);
            streamingClient.stream().get(streamingActor, new StreamingEventHandler() {
                @Override
                public void handle(StreamingCallbackMessage message) {
                    logger.info("RECEIVED: " + message);
                    if (lastReceivedTs[0] != -1) {
                        rpsStat.accept(System.nanoTime() - lastReceivedTs[0]);
                        logger.info("TIME DIFF STAT: " + rpsStat);
                    }
                    lastReceivedTs[0] = System.nanoTime();
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
            lastReceivedTs[0] = -1;
        }
    }
}
