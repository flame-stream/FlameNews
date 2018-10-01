package com.expleague.server;

import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.settings.ServerSettings;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.StreamConverters;
import akka.util.Timeout;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.commons.io.StreamTools;
import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.fileupload.ParameterParser;
import org.apache.commons.io.output.ByteArrayOutputStream;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Future;

import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User: solar
 * Date: 24.11.15
 * Time: 17:42
 */
public class ImageStorage extends ActorAdapter<AbstractActor> {
  private static final Logger log = Logger.getLogger(ImageStorage.class.getName());
  private static final String BUCKET_NAME = "tbts-image-storage-main-chunk";
  final AmazonS3Client s3Client;
  private Materializer materializer;

  private ImageStorage() {
    final BasicAWSCredentials credentials = new BasicAWSCredentials("AKIAJPLJBHVNFAWY3S4A", "UEnvfQ2ver5mlOu7IJsjxRH3G9uF3/f0WNLFZ9c6");
    System.setProperty("com.amazonaws.services.s3.disableGetObjectMD5Validation", "true");
    s3Client = new AmazonS3Client(credentials);
    if (s3Client.listBuckets().stream().noneMatch(bucket -> BUCKET_NAME.equals(bucket.getName()))) {
      s3Client.createBucket(BUCKET_NAME);
    }
  }

  @Override
  public void preStart() throws Exception {
    materializer = ActorMaterializer.create(context());
    final ConnectHttp connectHttp = ConnectHttp.toHost("0.0.0.0", 8067);
    final ServerSettings serverSettings = ServerSettings.create(context().system());
    final Source<IncomingConnection, CompletionStage<ServerBinding>> serverSource = Http.get(context().system()).bind(connectHttp, serverSettings);
    serverSource.to(Sink.actorRef(self(), PoisonPill.getInstance())).run(materializer);
  }

  @ActorMethod
  public void onReceive(Object o) throws Exception {
    if (o instanceof IncomingConnection) {
      final IncomingConnection connection = (IncomingConnection) o;

      log.fine("Accepted new connection from " + connection.remoteAddress());
      connection.handleWithAsyncHandler(httpRequest -> {
        //noinspection unchecked
        return FutureConverters.<HttpResponse>toJava((Future) Patterns.ask(context().actorOf(props(RequestHandler.class, s3Client)), httpRequest, Timeout.apply(10, TimeUnit.MINUTES)));
      }, materializer);
    }
    else unhandled(o);
  }

  public static void main(String[] args) {
    final ActorSystem system = ActorSystem.create("TBTS_Light_XMPP");
    system.actorOf(props(ImageStorage.class));
  }

  private static class RequestHandler extends ActorAdapter<AbstractActor> {
    private final AmazonS3Client s3Client;

    public RequestHandler(AmazonS3Client s3Client) {
      this.s3Client = s3Client;
    }

    @ActorMethod
    public void onReceive(final HttpRequest request) throws Exception {
      Uri uri = request.getUri();
      final HttpResponse response;

      if (request.method() == HttpMethods.GET && uri.path().equals("/")) {
        response = HttpResponse.create().withEntity(
            MediaTypes.TEXT_HTML.toContentType(HttpCharsets.UTF_8),
            "<html><body><form enctype=\"multipart/form-data\" action=\".\" method=\"post\" type=><input name=\"id\" type=\"text\"><input name=\"image\" accept=\"image/jpeg\" type=\"file\" alt=\"Submit\"><input type=\"submit\"></form></body></html>");
      }
      else if (request.method() == HttpMethods.GET) {
        final String id = uri.path().substring(1); // skip first slash
        S3Object image;
        try {
          image = s3Client.getObject(BUCKET_NAME, id);
        }
        catch(Exception e) {
          image = null;
          log.log(Level.INFO, "Unable to get image from S3", e);
        }
        if (image != null) {
          final String contentType = image.getObjectMetadata().getContentType();
          final byte[] contents = StreamTools.readByteStream(image.getObjectContent());
          final Optional<MediaType> lookup = MediaTypes.lookup(contentType.split("/")[0], contentType.split("/")[1]);
          response = HttpResponse.create().withEntity(ContentTypes.create((MediaType.Binary)lookup.get()), contents);
        }
        else
          response = HttpResponse.create().withStatus(404).withEntity("Page not found");
      }
      else if (request.method() == HttpMethods.POST) {
        log.info("Receiving image");
        final ActorMaterializer materializer = ActorMaterializer.create(context());
        final RequestEntity entity = request.entity();
        MediaType.Multipart mediaType = (MediaType.Multipart) entity.getContentType().mediaType();
        final InputStream is = entity.getDataBytes().runWith(StreamConverters.asInputStream(Duration.of(10, ChronoUnit.MINUTES)), materializer);
        final String boundary = mediaType.toRange().getParams().get("boundary");
        final MultipartStream multipartStream = new MultipartStream(is, boundary.getBytes(), 4096, null);

        boolean nextPart = multipartStream.skipPreamble();
        final ByteArrayOutputStream contents = new ByteArrayOutputStream();
        String id = null;
        String name = null;
        String mime = null;
        try {
          while (nextPart) {
            final String header = multipartStream.readHeaders();
            for (final String line : header.split("\n")) {
              if (line.startsWith("Content-Disposition: ")) {
                final ParameterParser parser = new ParameterParser();
                final Map<String, String> params = parser.parse(line.substring("Content-Disposition: ".length()), ';');
                if (params.containsKey("name"))
                  name = params.get("name");
              } else if (line.startsWith("Content-Type: ")) {
                mime = line.substring("Content-Type: ".length());
              }
            }
            if ("image".equals(name)) {
              multipartStream.readBodyData(contents);
            } else if ("id".equals(name)) {
              final ByteArrayOutputStream out = new ByteArrayOutputStream();
              multipartStream.readBodyData(out);
              id = new String(out.toByteArray(), StreamTools.UTF);
              log.info("\t" + id);
            }
            nextPart = multipartStream.readBoundary();
          }
        }
        catch (Exception e) {
          log.log(Level.WARNING, "Failed to upload attachment", e);
          sender().tell(HttpResponse.create().withStatus(504).withEntity("Multipart message is incomplete"), self());
          return;
        }
        try {
          final File tempFile = File.createTempFile("asdasd", "adsasd");
          StreamTools.writeBytes(contents.toByteArray(), tempFile);
//            final String streamMD5 = new String(Base64.encodeBase64(DigestUtils.md5(img.contents)));
//            metadata.setContentMD5(streamMD5);

          log.info("Uploading image " + id + " to S3");
          final PutObjectRequest putRequest = new PutObjectRequest(BUCKET_NAME, id, tempFile);
          putRequest.setMetadata(new ObjectMetadata());
          assert mime != null;
          putRequest.getMetadata().setContentType(mime.trim());
          s3Client.putObject(putRequest);
          //noinspection ResultOfMethodCallIgnored
          tempFile.delete();
          log.info("Success");
        }
        catch(Exception e) {
          log.log(Level.WARNING, "Failed to store attachment", e);
          sender().tell(HttpResponse.create().withStatus(504).withEntity("Unable to upload image to s3: " + e.getMessage()), self());
          return;
        }
        response = HttpResponse.create().withEntity(
            ContentTypes.TEXT_HTML_UTF8,
            "<html><body>Done</body></html>");
      }
      else response = HttpResponse.create().withStatus(404).withEntity("Page not found");

      sender().tell(response, self());
    }
  }
}
