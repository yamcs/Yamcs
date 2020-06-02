package org.yamcs.client.archive;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.yamcs.api.MethodHandler;
import org.yamcs.api.Observer;
import org.yamcs.client.AbstractPage;
import org.yamcs.client.FutureObserver;
import org.yamcs.client.Page;
import org.yamcs.client.StreamConsumer;
import org.yamcs.client.archive.ArchiveClient.ListOptions.AscendingOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.LimitOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.ListOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.NoRealtimeOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.NoRepeatOption;
import org.yamcs.client.archive.ArchiveClient.ListOptions.SourceOption;
import org.yamcs.client.archive.ArchiveClient.RangeOptions.MinimumGapOption;
import org.yamcs.client.archive.ArchiveClient.RangeOptions.RangeOption;
import org.yamcs.protobuf.AlarmData;
import org.yamcs.protobuf.Archive.GetParameterSamplesRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryResponse;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.CommandsApiClient;
import org.yamcs.protobuf.GetParameterRangesRequest;
import org.yamcs.protobuf.IndexGroup;
import org.yamcs.protobuf.IndexResponse;
import org.yamcs.protobuf.IndexesApiClient;
import org.yamcs.protobuf.ListCommandHistoryIndexRequest;
import org.yamcs.protobuf.ListCommandsRequest;
import org.yamcs.protobuf.ListCommandsResponse;
import org.yamcs.protobuf.ListCompletenessIndexRequest;
import org.yamcs.protobuf.ListEventIndexRequest;
import org.yamcs.protobuf.ListPacketIndexRequest;
import org.yamcs.protobuf.ListParameterIndexRequest;
import org.yamcs.protobuf.ParameterArchiveApiClient;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.Ranges;
import org.yamcs.protobuf.Pvalue.Ranges.Range;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Pvalue.TimeSeries.Sample;
import org.yamcs.protobuf.StreamCommandIndexRequest;
import org.yamcs.protobuf.StreamCompletenessIndexRequest;
import org.yamcs.protobuf.StreamEventIndexRequest;
import org.yamcs.protobuf.StreamPacketIndexRequest;
import org.yamcs.protobuf.StreamParameterIndexRequest;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.alarms.AlarmsApiClient;
import org.yamcs.protobuf.alarms.ListAlarmsRequest;
import org.yamcs.protobuf.alarms.ListAlarmsResponse;

import com.google.protobuf.Timestamp;

public class ArchiveClient {

    private String instance;
    private IndexesApiClient indexService;
    private CommandsApiClient commandService;
    private ParameterArchiveApiClient parameterArchiveService;
    private AlarmsApiClient alarmService;

    public ArchiveClient(MethodHandler handler, String instance) {
        this.instance = instance;
        indexService = new IndexesApiClient(handler);
        commandService = new CommandsApiClient(handler);
        parameterArchiveService = new ParameterArchiveApiClient(handler);
        alarmService = new AlarmsApiClient(handler);
    }

    public String getInstance() {
        return instance;
    }

    public CompletableFuture<Page<IndexGroup>> listCommandIndex(Instant start, Instant stop, ListOption... options) {
        ListCommandHistoryIndexRequest.Builder requestb = ListCommandHistoryIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new CommandIndexPage(requestb.build()).future();
    }

    public CompletableFuture<Page<IndexGroup>> listPacketIndex(Instant start, Instant stop, ListOption... options) {
        ListPacketIndexRequest.Builder requestb = ListPacketIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new PacketIndexPage(requestb.build()).future();
    }

    public CompletableFuture<Page<IndexGroup>> listParameterIndex(Instant start, Instant stop, ListOption... options) {
        ListParameterIndexRequest.Builder requestb = ListParameterIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new ParameterIndexPage(requestb.build()).future();
    }

    public CompletableFuture<Page<IndexGroup>> listEventIndex(Instant start, Instant stop, ListOption... options) {
        ListEventIndexRequest.Builder requestb = ListEventIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new EventIndexPage(requestb.build()).future();
    }

    public CompletableFuture<Page<IndexGroup>> listCompletenessIndex(Instant start, Instant stop,
            ListOption... options) {
        ListCompletenessIndexRequest.Builder requestb = ListCompletenessIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new CompletenessIndexPage(requestb.build()).future();
    }

    public void streamPacketIndex(StreamConsumer<ArchiveRecord> consumer, Instant start, Instant stop) {
        StreamPacketIndexRequest.Builder requestb = StreamPacketIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        indexService.streamPacketIndex(null, requestb.build(), consumer);
    }

    public void streamParameterIndex(StreamConsumer<ArchiveRecord> consumer, Instant start, Instant stop) {
        StreamParameterIndexRequest.Builder requestb = StreamParameterIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        indexService.streamParameterIndex(null, requestb.build(), consumer);
    }

    public void streamCommandIndex(StreamConsumer<ArchiveRecord> consumer, Instant start, Instant stop) {
        StreamCommandIndexRequest.Builder requestb = StreamCommandIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        indexService.streamCommandIndex(null, requestb.build(), consumer);
    }

    public void streamEventIndex(StreamConsumer<ArchiveRecord> consumer, Instant start, Instant stop) {
        StreamEventIndexRequest.Builder requestb = StreamEventIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        indexService.streamEventIndex(null, requestb.build(), consumer);
    }

    public void streamCompletenessIndex(StreamConsumer<ArchiveRecord> consumer, Instant start, Instant stop) {
        StreamCompletenessIndexRequest.Builder requestb = StreamCompletenessIndexRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        indexService.streamCompletenessIndex(null, requestb.build(), consumer);
    }

    public CompletableFuture<Page<CommandHistoryEntry>> listCommands() {
        return listCommands(null, null);
    }

    public CompletableFuture<Page<CommandHistoryEntry>> listCommands(Instant start, Instant stop) {
        ListCommandsRequest.Builder requestb = ListCommandsRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        return new CommandPage(requestb.build()).future();
    }

    public CompletableFuture<List<AlarmData>> listAlarms() {
        // TODO add pagination on server
        return listAlarms(null, null);
    }

    public CompletableFuture<List<AlarmData>> listAlarms(Instant start, Instant stop) {
        // TODO add pagination on server
        ListAlarmsRequest.Builder requestb = ListAlarmsRequest.newBuilder()
                .setInstance(instance);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        CompletableFuture<ListAlarmsResponse> f = new CompletableFuture<>();
        alarmService.listAlarms(null, requestb.build(), new FutureObserver<>(f));
        return f.thenApply(response -> response.getAlarmsList());
    }

    public CompletableFuture<Page<ParameterValue>> listValues(String parameter, ListOption... options) {
        return listValues(parameter, null, null, options);
    }

    public CompletableFuture<Page<ParameterValue>> listValues(String parameter, Instant start, Instant stop,
            ListOption... options) {
        ListParameterHistoryRequest.Builder requestb = ListParameterHistoryRequest.newBuilder()
                .setInstance(instance)
                .setName(parameter);
        if (start != null) {
            requestb.setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()));
        }
        if (stop != null) {
            requestb.setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        }
        for (ListOption option : options) {
            if (option instanceof AscendingOption) {
                requestb.setOrder(((AscendingOption) option).ascending ? "asc" : "desc");
            } else if (option instanceof NoRepeatOption) {
                requestb.setNorepeat(((NoRepeatOption) option).noRepeat);
            } else if (option instanceof NoRealtimeOption) {
                requestb.setNorealtime(((NoRealtimeOption) option).noRealtime);
            } else if (option instanceof LimitOption) {
                requestb.setLimit(((LimitOption) option).limit);
            } else if (option instanceof SourceOption) {
                requestb.setSource(((SourceOption) option).source);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        return new ValuePage(requestb.build()).future();
    }

    public CompletableFuture<List<Sample>> getSamples(String parameter, Instant start, Instant stop) {
        GetParameterSamplesRequest.Builder requestb = GetParameterSamplesRequest.newBuilder()
                .setInstance(instance)
                .setName(parameter)
                .setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()))
                .setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        CompletableFuture<TimeSeries> f = new CompletableFuture<>();
        parameterArchiveService.getParameterSamples(null, requestb.build(), new FutureObserver<>(f));
        return f.thenApply(response -> response.getSampleList());
    }

    public CompletableFuture<List<Range>> getRanges(String parameter, Instant start, Instant stop,
            RangeOption... options) {
        GetParameterRangesRequest.Builder requestb = GetParameterRangesRequest.newBuilder()
                .setInstance(instance)
                .setName(parameter)
                .setStart(Timestamp.newBuilder().setSeconds(start.getEpochSecond()).setNanos(start.getNano()))
                .setStop(Timestamp.newBuilder().setSeconds(stop.getEpochSecond()).setNanos(stop.getNano()));
        for (RangeOption option : options) {
            if (option instanceof MinimumGapOption) {
                requestb.setMinGap(((MinimumGapOption) option).millis);
            } else {
                throw new IllegalArgumentException("Usupported option " + option.getClass());
            }
        }
        CompletableFuture<Ranges> f = new CompletableFuture<>();
        parameterArchiveService.getParameterRanges(null, requestb.build(), new FutureObserver<>(f));
        return f.thenApply(ranges -> ranges.getRangeList());
    }

    private class CommandIndexPage extends AbstractPage<ListCommandHistoryIndexRequest, IndexResponse, IndexGroup> {

        public CommandIndexPage(ListCommandHistoryIndexRequest request) {
            super(request, "group");
        }

        @Override
        protected void fetch(ListCommandHistoryIndexRequest request, Observer<IndexResponse> observer) {
            indexService.listCommandHistoryIndex(null, request, observer);
        }
    }

    private class CommandPage extends AbstractPage<ListCommandsRequest, ListCommandsResponse, CommandHistoryEntry> {

        public CommandPage(ListCommandsRequest request) {
            super(request, "entry");
        }

        @Override
        protected void fetch(ListCommandsRequest request, Observer<ListCommandsResponse> observer) {
            commandService.listCommands(null, request, observer);
        }
    }

    private class PacketIndexPage extends AbstractPage<ListPacketIndexRequest, IndexResponse, IndexGroup> {

        public PacketIndexPage(ListPacketIndexRequest request) {
            super(request, "group");
        }

        @Override
        protected void fetch(ListPacketIndexRequest request, Observer<IndexResponse> observer) {
            indexService.listPacketIndex(null, request, observer);
        }
    }

    private class ParameterIndexPage extends AbstractPage<ListParameterIndexRequest, IndexResponse, IndexGroup> {

        public ParameterIndexPage(ListParameterIndexRequest request) {
            super(request, "group");
        }

        @Override
        protected void fetch(ListParameterIndexRequest request, Observer<IndexResponse> observer) {
            indexService.listParameterIndex(null, request, observer);
        }
    }

    private class EventIndexPage extends AbstractPage<ListEventIndexRequest, IndexResponse, IndexGroup> {

        public EventIndexPage(ListEventIndexRequest request) {
            super(request, "group");
        }

        @Override
        protected void fetch(ListEventIndexRequest request, Observer<IndexResponse> observer) {
            indexService.listEventIndex(null, request, observer);
        }
    }

    private class CompletenessIndexPage extends AbstractPage<ListCompletenessIndexRequest, IndexResponse, IndexGroup> {

        public CompletenessIndexPage(ListCompletenessIndexRequest request) {
            super(request, "group");
        }

        @Override
        protected void fetch(ListCompletenessIndexRequest request, Observer<IndexResponse> observer) {
            indexService.listCompletenessIndex(null, request, observer);
        }
    }

    private class ValuePage
            extends AbstractPage<ListParameterHistoryRequest, ListParameterHistoryResponse, ParameterValue> {

        public ValuePage(ListParameterHistoryRequest request) {
            super(request, "parameter");
        }

        @Override
        protected void fetch(ListParameterHistoryRequest request, Observer<ListParameterHistoryResponse> observer) {
            parameterArchiveService.listParameterHistory(null, request, observer);
        }
    }

    public static final class ListOptions {

        public static interface ListOption {
        }

        public static ListOption ascending(boolean ascending) {
            return new AscendingOption(ascending);
        }

        public static ListOption limit(int limit) {
            return new LimitOption(limit);
        }

        public static ListOption noRepeat(boolean noRepeat) {
            return new NoRepeatOption(noRepeat);
        }

        public static ListOption noRealtime(boolean noRealtime) {
            return new NoRealtimeOption(noRealtime);
        }

        public static ListOption source(String source) {
            return new SourceOption(source);
        }

        static final class AscendingOption implements ListOption {
            final boolean ascending;

            public AscendingOption(boolean ascending) {
                this.ascending = ascending;
            }
        }

        static final class LimitOption implements ListOption {
            final int limit;

            public LimitOption(int limit) {
                this.limit = limit;
            }
        }

        static final class NoRepeatOption implements ListOption {
            final boolean noRepeat;

            public NoRepeatOption(boolean noRepeat) {
                this.noRepeat = noRepeat;
            }
        }

        static final class NoRealtimeOption implements ListOption {
            final boolean noRealtime;

            public NoRealtimeOption(boolean noRealtime) {
                this.noRealtime = noRealtime;
            }
        }

        static final class SourceOption implements ListOption {
            final String source;

            public SourceOption(String source) {
                this.source = source;
            }
        }
    }

    public static final class RangeOptions {

        public static interface RangeOption {
        }

        public static RangeOption minimumGap(long millis) {
            return new MinimumGapOption(millis);
        }

        static final class MinimumGapOption implements RangeOption {
            final long millis;

            public MinimumGapOption(long millis) {
                this.millis = millis;
            }
        }
    }
}