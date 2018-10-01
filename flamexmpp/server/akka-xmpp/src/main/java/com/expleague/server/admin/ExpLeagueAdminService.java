package com.expleague.server.admin;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.IncomingConnection;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.headers.ContentDisposition;
import akka.http.javadsl.model.headers.ContentDispositionTypes;
import akka.http.javadsl.settings.ServerSettings;
import akka.pattern.Patterns;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.util.Timeout;
import com.expleague.model.OrderState;
import com.expleague.server.Roster;
import com.expleague.server.admin.dto.DumpItemDto;
import com.expleague.server.admin.dto.ExpertsProfileDto;
import com.expleague.server.admin.dto.OrderDto;
import com.expleague.server.admin.dto.OrdersGroupDto;
import com.expleague.server.admin.reports.db.ExpertWorkReportHandler;
import com.expleague.server.admin.reports.dump.quality_monitoring.QualityMonitoringReportHandler;
import com.expleague.server.admin.series.TimeSeriesChartDto;
import com.expleague.server.admin.series.TimeSeriesDto;
import com.expleague.server.agents.ExpLeagueOrder;
import com.expleague.server.agents.LaborExchange;
import com.expleague.server.agents.RoomAgent;
import com.expleague.server.agents.XMPP;
import com.expleague.util.akka.ActorAdapter;
import com.expleague.util.akka.ActorMethod;
import com.expleague.xmpp.Item;
import com.expleague.xmpp.JID;
import com.expleague.xmpp.stanza.Stanza;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Charsets;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.typesafe.config.Config;
import gnu.trove.map.hash.TLongDoubleHashMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.set.hash.TLongHashSet;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import scala.compat.java8.FutureConverters;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author vpdelta
 */
public class ExpLeagueAdminService extends ActorAdapter<AbstractActor> {
  private static final Logger log = Logger.getLogger(ExpLeagueAdminService.class.getName());

  private final static ObjectMapper mapper = new DefaultJsonMapper();

  private Config config;
  private Materializer materializer;

  public ExpLeagueAdminService(final Config config) {
    this.config = config;
  }

  @Override
  public void preStart() throws Exception {
    materializer = ActorMaterializer.create(context());
    final int port = config.getInt("port");
    final Source<IncomingConnection, CompletionStage<ServerBinding>> serverSource = Http.get(context().system()).bind(ConnectHttp.toHost("0.0.0.0", port), ServerSettings.create(context().system()));
    serverSource.to(Sink.actorRef(self(), PoisonPill.getInstance())).run(materializer);
    log.fine("Started on port: " + port);
  }

  @ActorMethod
  public void onReceive(Object o) throws Exception {
    if (o instanceof IncomingConnection) {
      final IncomingConnection connection = (IncomingConnection) o;
      log.fine("Accepted new connection from " + connection.remoteAddress());
      connection.handleWithAsyncHandler(httpRequest -> {
        final Future ask = Patterns.ask(context().actorOf(props(Handler.class)), httpRequest, Timeout.apply(Duration.create(10, TimeUnit.MINUTES)));
        //noinspection unchecked
        return FutureConverters.toJava((Future<HttpResponse>) ask);
      }, materializer);
    }
    else unhandled(o);
  }

  public static class Handler extends ActorAdapter<AbstractActor> {
    @ActorMethod
    public void onReceive(final HttpRequest request) throws Exception {
      final String path = request.getUri().path();
      log.fine(request.method() + " " + path);
      final LaborExchange.Board board = LaborExchange.board();
      HttpResponse response = HttpResponse.create().withStatus(404).withEntity("Page not found");
      if (request.method() == HttpMethods.GET) {
        try {
          if (path.isEmpty() || "/".equals(path)) {
            final File file = new File("admin/static/index.html");
            response = HttpResponse.create().withStatus(200).withEntity(HttpEntities.create(ContentTypes.TEXT_HTML_UTF8, file));
          }
          else if (path.startsWith("/static")) {
            final File file = new File("admin/static", path.substring(path.indexOf("static") + 7));
            if (file.isFile()) {
              response = HttpResponse.create().withStatus(200).withEntity(HttpEntities.create(getContentType(file), file));
            }
            else {
              response = HttpResponse.create().withStatus(404).withEntity("File not found");
            }
          }
          else if ("/open".equals(path)) {
            try (final Stream<ExpLeagueOrder> open = board.open()) {
              response = getOrders(open);
            }
          }
          else if ("/replay".equals(path)) {
            final Optional<String> room = request.getUri().query().get("room");
            if (room.isPresent()) {
              board.replay(room.get(), context());
              response = HttpResponse.create().withStatus(200).withEntity("Replayed room " + room.get());
            }
            else
              response = HttpResponse.create().withStatus(404).withEntity("File not found");

          }
          else if ("/closed/without/feedback".equals(path)) {
            try (final Stream<ExpLeagueOrder> orders = board.orders(
                new LaborExchange.OrderFilter(true, EnumSet.of(OrderState.DONE))
            )) {
              response = getOrders(orders);
            }
          }
          else if ("/closed".equals(path)) {
            try (final Stream<ExpLeagueOrder> orders = board.orders(
                new LaborExchange.OrderFilter(false, EnumSet.of(OrderState.DONE))
            )) {
              response = getOrders(orders);
            }
          }
          else if ("/top/experts".equals(path)) {
            try (final Stream<JID> topExperts = board.topExperts()) {
              final List<ExpertsProfileDto> experts = topExperts.map(JID::local)
                  .map(Roster.instance()::profile)
                  .map(ExpertsProfileDto::new)
                  .collect(Collectors.toList());
              response = getJsonResponse("experts", experts);
            }
          }
          else if (path.startsWith("/history/")) {
            final String roomId = path.substring("/history/".length());
            try (final Stream<ExpLeagueOrder> history = board.history(roomId)) {
              response = getOrders(history);
            }
          }
          else if (path.startsWith("/active/")) {
            final String roomId = path.substring("/active/".length());
            response = getOrders(Stream.of(board.active(roomId)));
          }
          else if (path.startsWith("/related/")) {
            final JID jid = JID.parse(path.substring("/related/".length()));
            try (final Stream<ExpLeagueOrder> related = board.related(jid)) {
              response = getOrders(related);
            }
          }
          else if (path.startsWith("/dump/")) {
            final Timeout timeout = new Timeout(Duration.create(Long.parseLong(request.getUri().query().get("timeout").orElse("2")), TimeUnit.SECONDS));
            final JID jid = JID.parse(path.substring("/dump/".length()));
            final Future<Object> ask = Patterns.ask(XMPP.register(jid, context()), new RoomAgent.DumpRequest(), timeout);
            //noinspection unchecked
            final List<Stanza> result = (List<Stanza>) Await.result(ask, timeout.duration());
            final List<DumpItemDto> messages = result.stream().map(DumpItemDto::new).collect(Collectors.toList());
            response = getJsonResponse("messages", messages);
          }
          else if (path.startsWith("/xml-dump/")) {
            final Timeout timeout = new Timeout(Duration.create(Long.parseLong(request.getUri().query().get("timeout").orElse("2")), TimeUnit.SECONDS));
            final JID jid = JID.parse(path.substring("/xml-dump/".length()));
            final Future<Object> ask = Patterns.ask(XMPP.register(jid, context()), new RoomAgent.DumpRequest(), timeout);
            //noinspection unchecked
            final List<Stanza> result = (List<Stanza>) Await.result(ask, timeout.duration());
            final StringBuilder builder = new StringBuilder();
            result.stream().map(stanza -> stanza.xmlString() + "\n").forEach(builder::append);
            response = HttpResponse.create().withStatus(200).withEntity(Item.XMPP_START + "\n" + builder.toString() + Item.XMPP_END);
          }
          else if (path.startsWith("/rebuild-archive")) {
            final List<JID> failed = new ArrayList<>();
            final List<JID> success = new ArrayList<>();
            try (final Stream<ExpLeagueOrder> orders = LaborExchange.board()
                .orders(new LaborExchange.OrderFilter(false, EnumSet.noneOf(OrderState.class)))) {
              orders.map(ExpLeagueOrder::room)
                  .forEach(roomJid -> {
                    final Future<Object> result = Patterns.ask(XMPP.register(roomJid, context()), new RoomAgent.Replay(), Timeout.apply(10, TimeUnit.HOURS));
                    try {
                      final RoomAgent.Replay replay = (RoomAgent.Replay) Await.result(result, Duration.Inf());
                      if (replay.success) {
                        success.add(roomJid);
                      }
                      else {
                        failed.add(roomJid);
                      }
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                    XMPP.whisper(roomJid, new RoomAgent.DumpRequest("archive"), context());
                    success.add(roomJid);
                  });
            }
            response = HttpResponse.create().withStatus(200).withEntity("Archive restored for " + success.size() + ", failed for: " + failed.toString());
          }
          else if ("/kpi".equals(path)) {
            response = getJsonResponse("charts", prepareKpiCharts(board));
          }
          else if ("/report-experts".equals(path)) {
            final Optional<String> startParam = request.getUri().query().get("start");
            final Optional<String> endParam = request.getUri().query().get("end");
            final Optional<String> expertIdParam = request.getUri().query().get("expert");
            if (!startParam.isPresent() || !endParam.isPresent()) {
              response = HttpResponse.create().withStatus(400).withEntity("Parameters are not defined");
            }
            else {
              final SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
              final long start = formatter.parse(startParam.get()).getTime();
              final long end = formatter.parse(endParam.get()).getTime();
              final String expertId = expertIdParam.orElse(null);

              final ActorRef handler = context().actorOf(ActorAdapter.props(ExpertWorkReportHandler.class));
              final Timeout timeout = Timeout.apply(Duration.create(2, TimeUnit.HOURS));
              final Future<Object> ask = Patterns.ask(handler, new ExpertWorkReportHandler.ReportRequest(start, end, expertId), timeout);
              final String result = (String) Await.result(ask, timeout.duration());
              response = fileResponse(result, "report-expert.csv");
            }
          }
          else if ("/report-quality".equals(path)) {
            final Optional<String> startParam = request.getUri().query().get("start");
            final Optional<String> endParam = request.getUri().query().get("end");
            final Optional<String> clientIdParam = request.getUri().query().get("client");

            if (!startParam.isPresent() || !endParam.isPresent()) {
              response = HttpResponse.create().withStatus(400).withEntity("Parameters are not defined");
            }
            else {
              final SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
              final long start = formatter.parse(startParam.get()).getTime();
              final long end = formatter.parse(endParam.get()).getTime();
              final String clientId = clientIdParam.orElse(null);

              final ActorRef handler = context().actorOf(ActorAdapter.props(QualityMonitoringReportHandler.class));
              final Timeout timeout = Timeout.apply(Duration.create(2, TimeUnit.HOURS));
              final Future<Object> ask = Patterns.ask(handler, new QualityMonitoringReportHandler.ReportRequest(start, end, clientId), timeout);
              final String result = (String) Await.result(ask, timeout.duration());
              response = fileResponse(result, "report-quality.csv");
            }
          }
        } catch (Exception e) {
          final ByteArrayOutputStream out = new ByteArrayOutputStream();
          e.printStackTrace(new PrintStream(out));
          final String stacktrace = new String(out.toByteArray(), Charsets.UTF_8);
          response = HttpResponse.create().withStatus(500).withEntity(stacktrace);
          log.warning(stacktrace);
        }
      }
      sender().tell(response, self());
    }

    @NotNull
    protected List<TimeSeriesChartDto> prepareKpiCharts(final LaborExchange.Board board) {
      final List<TimeSeriesChartDto> charts = new ArrayList<>();
      final TLongIntHashMap timestamp2TasksCount = new TLongIntHashMap();
      final Set<String> knownUsers = new HashSet<>();
      final SetMultimap<Long, String> timestamp2UniqueUsers = HashMultimap.create();
      final SetMultimap<Long, String> timestamp2NewUsers = HashMultimap.create();

      final TLongIntHashMap timestamp2ClosedTaskCount = new TLongIntHashMap();
      final TLongIntHashMap timestamp2TasksWithFeedbackCount = new TLongIntHashMap();

      final TLongLongHashMap timestamp2TaskDuration = new TLongLongHashMap();
      final TLongDoubleHashMap timestamp2Feedback = new TLongDoubleHashMap();

      try (final Stream<ExpLeagueOrder> orders = board.orders(new LaborExchange.OrderFilter(false, EnumSet.allOf(OrderState.class)))) {
        orders.forEach(
            order -> {
              final long startTimestamp = (long) (order.offer().started() * 1000);
              final DateTime startDay = new DateTime(startTimestamp).dayOfMonth().roundFloorCopy();
              timestamp2TasksCount.adjustOrPutValue(startDay.getMillis(), 1, 1);
              final String user = order.offer().client().bare().getAddr();
              timestamp2UniqueUsers.put(startDay.getMillis(), user);
              if (knownUsers.add(user)) {
                timestamp2NewUsers.put(startDay.getMillis(), user);
              }

              if (order.state() == OrderState.DONE) {
                //noinspection ConstantConditions
                final long closeTimestamp = order.statusHistoryRecords()
                    .filter(statusHistoryRecord -> statusHistoryRecord.getStatus() == OrderState.DONE)
                    .findFirst().get()
                    .getDate().getTime();

                final DateTime closeDay = new DateTime(closeTimestamp).dayOfMonth().roundFloorCopy();
                timestamp2ClosedTaskCount.adjustOrPutValue(closeDay.getMillis(), 1, 1);

                final long durationMinutes = (closeTimestamp - startTimestamp) / (60 * 1000L);
                timestamp2TaskDuration.adjustOrPutValue(closeDay.getMillis(), durationMinutes, durationMinutes);

                final double feedback = order.feedback();
                if (feedback != -1) {
                  timestamp2TasksWithFeedbackCount.adjustOrPutValue(closeDay.getMillis(), 1, 1);
                  timestamp2Feedback.adjustOrPutValue(closeDay.getMillis(), feedback, feedback);
                }
              }
            }
        );
      }

      {
        final List<TimeSeriesDto.PointDto> allTasks = new ArrayList<>();
        final List<TimeSeriesDto.PointDto> uniqueUsers = new ArrayList<>();
        final List<TimeSeriesDto.PointDto> newUsers = new ArrayList<>();
        final List<TimeSeriesDto.PointDto> closedTasks = new ArrayList<>();
        final List<TimeSeriesDto.PointDto> closedWithFeedbackTasks = new ArrayList<>();
        final TLongHashSet keys = new TLongHashSet();
        keys.addAll(timestamp2TasksCount.keys());
        keys.addAll(timestamp2ClosedTaskCount.keys());
        final long[] sortedKeys = keys.toArray();
        Arrays.sort(sortedKeys);
        for (long ts : sortedKeys) {
          if (timestamp2TasksCount.containsKey(ts)) {
            allTasks.add(new TimeSeriesDto.PointDto(ts, timestamp2TasksCount.get(ts)));
          }
          uniqueUsers.add(new TimeSeriesDto.PointDto(ts, timestamp2UniqueUsers.get(ts).size()));
          newUsers.add(new TimeSeriesDto.PointDto(ts, timestamp2NewUsers.get(ts).size()));
          if (timestamp2ClosedTaskCount.containsKey(ts)) {
            closedTasks.add(new TimeSeriesDto.PointDto(ts, timestamp2ClosedTaskCount.get(ts)));
          }
          if (timestamp2TasksWithFeedbackCount.containsKey(ts)) {
            closedWithFeedbackTasks.add(new TimeSeriesDto.PointDto(ts, timestamp2TasksWithFeedbackCount.get(ts)));
          }
        }
        charts.add(new TimeSeriesChartDto(
            "Number of tasks",
            "Time",
            "Tasks",
            Lists.newArrayList(
                new TimeSeriesDto("Received tasks", allTasks),
                new TimeSeriesDto("Closed tasks", closedTasks),
                new TimeSeriesDto("Tasks with feedback", closedWithFeedbackTasks)
            )
        ));

        charts.add(new TimeSeriesChartDto(
            "Number of users",
            "Time",
            "Users",
            Lists.newArrayList(
                new TimeSeriesDto("Unique users", uniqueUsers),
                new TimeSeriesDto("New users", newUsers)
            )
        ));
      }

      {
        final List<TimeSeriesDto.PointDto> points = new ArrayList<>();
        final long[] keys = timestamp2TaskDuration.keys();
        Arrays.sort(keys);
        for (long ts : keys) {
          points.add(new TimeSeriesDto.PointDto(ts, timestamp2TaskDuration.get(ts) / timestamp2ClosedTaskCount.get(ts)));
        }
        charts.add(new TimeSeriesChartDto(
            "Average task duration",
            "Time",
            "Minutes",
            Lists.newArrayList(new TimeSeriesDto(
                "Task duration",
                points
            ))
        ));
      }

      {
        final List<TimeSeriesDto.PointDto> points = new ArrayList<>();
        final long[] keys = timestamp2Feedback.keys();
        Arrays.sort(keys);
        for (long ts : keys) {
          points.add(new TimeSeriesDto.PointDto(ts, timestamp2Feedback.get(ts) / timestamp2TasksWithFeedbackCount.get(ts)));
        }
        charts.add(new TimeSeriesChartDto(
            "Average task feedback",
            "Time",
            "Score",
            Lists.newArrayList(new TimeSeriesDto(
                "Task feedback",
                points
            ))
        ));
      }
      return charts;
    }

    protected HttpResponse getOrders(Stream<ExpLeagueOrder> stream) throws JsonProcessingException {
      final List<OrderDto> orders = stream.map(OrderDto::new).collect(Collectors.toList());
      Collections.reverse(orders);

      final DateTimeFormatter formatter = DateTimeFormat.forPattern("dd-MM-yyyy");
      final Map<String, List<OrderDto>> history = orders
          .stream()
          .collect(Collectors.groupingBy(
              orderDto -> {
                final long startedMs = orderDto.getOffer().getStartedMs();
                return formatter.print(startedMs);
              }
          ));
      final List<OrdersGroupDto> result = new ArrayList<>();
      for (Map.Entry<String, List<OrderDto>> entry : history.entrySet()) {
        result.add(new OrdersGroupDto(
            entry.getKey(),
            entry.getValue()
        ));
      }
      result.sort((o1, o2) -> {
        final DateTime d2 = DateTime.parse(o2.getGroupName(), formatter);
        final DateTime d1 = DateTime.parse(o1.getGroupName(), formatter);
        return d2.compareTo(d1);
      });
      return getJsonResponse("orderGroups", result);
    }

    protected HttpResponse getJsonResponse(final String name, final Object value) throws JsonProcessingException {
      final Map<String, Object> map = new HashMap<>();
      map.put(name, value);
      try (final Stream<JID> topExperts = LaborExchange.board().topExperts()) {
        final List<ExpertsProfileDto> experts = topExperts.map(JID::local)
            .map(Roster.instance()::profile)
            .map(ExpertsProfileDto::new)
            .collect(Collectors.toList());
        map.put("experts", experts);
        return HttpResponse.create().withStatus(200).withEntity(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(map));
      }
    }

    @NotNull
    protected ContentType getContentType(final File file) {
      final String fileName = file.getName();
      return fileName.endsWith(".js")
          ? ContentTypes.create(MediaTypes.APPLICATION_JSON)
          : fileName.endsWith(".css")
          ? ContentTypes.create(MediaTypes.TEXT_CSS, HttpCharsets.UTF_8)
          : fileName.endsWith(".png")
          ? ContentTypes.create(MediaTypes.IMAGE_PNG)
          : ContentTypes.create(MediaTypes.APPLICATION_OCTET_STREAM);
    }
  }

  private static HttpResponse fileResponse(String result, String fileName) {
    final Map<String, String> params = new HashMap<>();
    params.put("filename", fileName);
    return HttpResponse.create().addHeader(ContentDisposition.create(ContentDispositionTypes.ATTACHMENT, params)).withStatus(200).withEntity(result);
  }

  public static class DefaultJsonMapper extends ObjectMapper {
    public DefaultJsonMapper() {
      this.setTimeZone(TimeZone.getDefault());
      this.configure(SerializationFeature.INDENT_OUTPUT, false);
      this.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
      this.configure(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS, false);
      this.configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true);
      this.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
      this.configure(SerializationFeature.INDENT_OUTPUT, true);
      this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
  }
}
