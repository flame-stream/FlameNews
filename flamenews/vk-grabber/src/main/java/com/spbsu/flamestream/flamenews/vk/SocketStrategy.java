package com.spbsu.flamestream.flamenews.vk;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.locks.LockSupport;

// see ZkFlameClientTest(Front) and BenchStand(producer)
public class SocketStrategy implements OutputStrategy {
  private static final Logger LOG = LoggerFactory.getLogger(SocketStrategy.class);
  private final Queue<String> messagesToSend = new ConcurrentLinkedDeque<>();
  private final Server producer;

  public SocketStrategy(String host, int port) {
    producer = new Server(1_000_000, 1000);
    ((Kryo.DefaultInstantiatorStrategy) producer.getKryo().getInstantiatorStrategy())
            .setFallbackInstantiatorStrategy(new StdInstantiatorStrategy());

    final Connection[] connection = new Connection[1];
    new Thread(() -> {
      synchronized (connection) {
        try {
          connection.wait();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }

      while (true) {
        if (!messagesToSend.isEmpty()) {
          synchronized (connection) {
            String message = messagesToSend.poll();
            connection[0].sendTCP(message);
            LOG.info("Sending: {}", message);
            LockSupport.parkNanos((long) (50 * 1.0e6));
          }
        }
      }
    }).start();

    producer.addListener(new Listener() {
      @Override
      public void connected(Connection newConnection) {
        synchronized (connection) {
          LOG.info("There is new connection: {}", newConnection.getRemoteAddressTCP());
          //first condition for local testing
          //if (connection[0] == null && newConnection.getRemoteAddressTCP().getAddress().equals(InetAddress.getByName(host))) {
          if (connection[0] == null) {
            LOG.info("Accepting connection: {}", newConnection.getRemoteAddressTCP());
            connection[0] = newConnection;
            connection.notify();
          } else {
            LOG.info("Closing connection {}", newConnection.getRemoteAddressTCP());
            newConnection.close();
          }
        }
      }
    });

    producer.start();
    try {
      producer.bind(port);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void processMessage(Integer creationTime, String message) {
    LOG.info("Message {} added to the queue with creation time {}", message, creationTime);
    messagesToSend.add(message);
  }

  @Override
  public void close() {
    producer.stop();
  }
}
