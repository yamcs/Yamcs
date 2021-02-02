package org.yamcs.tctm;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.ConfigurationException;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.TmPacket;
import org.yamcs.YConfiguration;
import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.cmdhistory.CommandHistoryPublisher.AckStatus;
import static org.yamcs.cmdhistory.CommandHistoryPublisher.AcknowledgeSent;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.time.Instant;
import org.yamcs.utils.YObjectLoader;
import org.yamcs.utils.TimeEncoding;

import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class TcpTcTmDataLink extends AbstractTmDataLink implements TcDataLink, Runnable {

	// Stuff copied from AbstractTcDataLink
	protected CommandHistoryPublisher commandHistoryPublisher;
	protected AtomicLong dataOutCount = new AtomicLong();
	protected CommandPostprocessor cmdPostProcessor;
	private AggregatedDataLink parent = null;
	
	// MARK: - Copied and modified from AbstractTmDataLink
	
	protected Socket tmSocket;
	protected String host;
	protected int port;
	protected long initialDelay;
	
	String packetInputStreamClassName;
	YConfiguration packetInputStreamArgs;
	PacketInputStream packetInputStream;
	OutputStream outputStream;
	
	@Override
	public void init(String instance, String name, YConfiguration config) throws ConfigurationException {
		// Read arguments
		super.init(instance, name, config);
		if (config.containsKey("tmHost")) { // this is when the config is specified in tcp.yaml
			host = config.getString("tmHost");
			port = config.getInt("tmPort");
		} else {
			host = config.getString("host");
			port = config.getInt("port");
		}
		initialDelay = config.getLong("initialDelay", -1);
		// Input stream defaults to GenericPacketInputStream
		if (config.containsKey("packetInputStreamClassName")) {
			this.packetInputStreamClassName = config.getString("packetInputStreamClassName");
			this.packetInputStreamArgs = config.getConfig("packetInputStreamArgs");
		} else {
			this.packetInputStreamClassName = GenericPacketInputStream.class.getName();
			HashMap<String, Object> m = new HashMap<>();
			m.put("maxPacketLength", 1000);
			m.put("lengthFieldOffset", 5);
			m.put("lengthFieldLength", 2);
			m.put("lengthAdjustment", 7);
			m.put("initialBytesToStrip", 0);
			this.packetInputStreamArgs = YConfiguration.wrap(m);
		}
		
		// Setup tc postprocessor
		initPostprocessor(yamcsInstance, config);
		
		// Subscribe to tc stream to receive telecommands
		XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
		YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
		String tcStreamName = config.getString("tcStream", "tc_realtime");
		Stream tcStream = ydb.getStream(tcStreamName);
		if (tcStream == null) {
			throw new ConfigurationException("Cannot find stream '" + tcStreamName + "'");
		}
		tcStream.addSubscriber(new StreamSubscriber() {
			@Override
			public void onTuple(Stream s, Tuple tuple) {
				sendTc(PreparedCommand.fromTuple(tuple, xtcedb));
			}
			
			@Override
			public void streamClosed(Stream s) {
				stopAsync();
			}
		});
		
		// Connect with telemetry stream
		String tmStreamName = config.getString("tmStream", "tm_realtime");
		Stream tmStream = ydb.getStream(tmStreamName);
		if (tmStream == null) {
			throw new ConfigurationException("Cannot find stream '" + tmStreamName + "'");
		}
		setTmSink(pwrt -> processTmPacket(pwrt, tmStream));
	}
	
	// Method copied and modified from LinkManager
	protected void processTmPacket(TmPacket pwrt, Stream stream) {
		if (pwrt.isInvalid()) {
			return;
		}
		
		Instant ertime = pwrt.getEarthReceptionTime();
		Tuple t = null;
		if (ertime == Instant.INVALID_INSTANT) {
			ertime = null;
		}
		Long obt = pwrt.getObt() == Long.MIN_VALUE ? null : pwrt.getObt();
		Object[] columns = new Object[] { pwrt.getGenerationTime(), pwrt.getSeqCount(), pwrt.getReceptionTime(), pwrt.getStatus(), pwrt.getPacket(), ertime, obt, getName() };
		assert(columns.length == StandardTupleDefinitions.TM.size());
		t = new Tuple(StandardTupleDefinitions.TM, columns);
		stream.emitTuple(t);
	}
	
	protected synchronized void checkAndOpenSocket() throws IOException {
		if (tmSocket != null) {
			return;
		}
		InetAddress address = InetAddress.getByName(host);
		tmSocket = new Socket();
		tmSocket.setKeepAlive(true);
		tmSocket.connect(new InetSocketAddress(address, port), 1000);
		try {
			packetInputStream = YObjectLoader.loadObject(packetInputStreamClassName);
			outputStream = tmSocket.getOutputStream();
		} catch (ConfigurationException e) {
			log.error("Cannot instantiate the packetInput: " + e);
			try {
				tmSocket.close();
			} catch (IOException e2) {
			}
			tmSocket = null;
			outputStream = null;
			packetInputStream = null;
			throw e;
		}
		packetInputStream.init(tmSocket.getInputStream(), packetInputStreamArgs);
		log.info("Link established to {}:{}", host, port);
	}
	
	protected synchronized boolean isSocketOpen() {
		return tmSocket != null;
	}
	
	protected synchronized void sendBuffer(byte[] data) throws IOException {
		if (outputStream == null) {
			throw new IOException(String.format("No connection to %s:%d", host, port));
		}
		outputStream.write(data);
	}
	
	protected synchronized void closeSocket() {
		if (tmSocket != null) {
			try {
				tmSocket.close();
			} catch (IOException e) {
			}
			tmSocket = null;
			outputStream = null;
			packetInputStream = null;
		}
	}
	
	@Override
	public void run() {
		if (initialDelay > 0) {
			try {
				Thread.sleep(initialDelay);
				initialDelay = -1;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		
		while (isRunningAndEnabled()) {
			TmPacket tmpkt = getNextPacket();
			if (tmpkt == null) {
				break;
			}
			processPacket(tmpkt);
		}
	}

	public TmPacket getNextPacket() {
		TmPacket pwt = null;
		while (isRunningAndEnabled()) {
			try {
				checkAndOpenSocket();
				byte[] packet = packetInputStream.readPacket();
				updateStats(packet.length);
				TmPacket pkt = new TmPacket(timeService.getMissionTime(), packet);
				pkt.setEarthRceptionTime(timeService.getHresMissionTime());
				pwt = packetPreprocessor.process(pkt);
				if (pwt != null) {
					break;
				}
			} catch (EOFException e) {
				log.warn("TM Connection closed");
				closeSocket();
			} catch (IOException e) {
				if (isRunningAndEnabled()) {
					String exc = (e instanceof ConnectException) ? ((ConnectException) e).getMessage() : e.toString();
					log.info("Cannot open or read TM socket {}:{} {}'. Retrying in 10s", host, port, exc);
				}
				closeSocket();
				for (int i = 0; i < 10; i++) {
					if (!isRunningAndEnabled()) {
						break;
					}
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						Thread.currentThread().interrupt();
						return null;
					}
				}
			} catch (PacketTooLongException e) {
				log.warn(e.toString());
				closeSocket();
			}
		}
		return pwt;
	}
	
	// MARK: - Stuff copied from AbstractTcDataLink

	protected void initPostprocessor(String instance, YConfiguration config) throws ConfigurationException {
		String commandPostprocessorClassName = GenericCommandPostprocessor.class.getName();
		YConfiguration commandPostprocessorArgs = null;
		
		// The GenericCommandPostprocessor class does nothing if there are no arguments, which is what we want.
		if (config != null) {
			commandPostprocessorClassName = config.getString("commandPostprocessorClassName", GenericCommandPostprocessor.class.getName());
			if (config.containsKey("commandPostprocessorArgs")) {
				commandPostprocessorArgs = config.getConfig("commandPostprocessorArgs");
			}
		}

		// Instantiate
		try {
			if (commandPostprocessorArgs != null) {
				cmdPostProcessor = YObjectLoader.loadObject(commandPostprocessorClassName, instance, commandPostprocessorArgs);
			} else {
				cmdPostProcessor = YObjectLoader.loadObject(commandPostprocessorClassName, instance);
			}
		} catch (ConfigurationException e) {
			log.error("Cannot instantiate the command postprocessor", e);
			throw e;
		}
	}
	
	// MARK: - TcDataLink
	
	@Override
	public void sendTc(PreparedCommand pc) {
		String reason;

		byte[] binary = cmdPostProcessor.process(pc);
		if (binary != null) {

			try {
				sendBuffer(binary);
				dataOutCount.getAndIncrement();
				ackCommand(pc.getCommandId());
				return;
			} catch (IOException e) {
				reason = String.format("Error writing to TC socket to %s:%d; %s", host, port, e.getMessage());
				log.warn(reason);
			}

		} else {
			reason = "Command postprocessor did not process the command";
		}

		failedCommand(pc.getCommandId(), reason);
	}

	@Override
	public void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryListener) {
		this.commandHistoryPublisher = commandHistoryListener;
		cmdPostProcessor.setCommandHistoryPublisher(commandHistoryListener);
	}
	
	/**Send to command history the failed command */
	protected void failedCommand(CommandId commandId, String reason) {
		log.debug("Failing command {}: {}", commandId, reason);
		long currentTime = getCurrentTime();
		commandHistoryPublisher.publishAck(commandId, AcknowledgeSent, currentTime, AckStatus.NOK, reason);
		commandHistoryPublisher.commandFailed(commandId, currentTime, reason);
	}
	
	/**
	 * send an ack in the command history that the command has been sent out of the link
	 * @param commandId
	 */
	protected void ackCommand(CommandId commandId) {
		commandHistoryPublisher.publishAck(commandId, AcknowledgeSent, getCurrentTime(), AckStatus.OK);
	}

	// MARK: - AbstractService (com.google.common.util.concurrent.AbstractService)
	
	@Override
	public void doStart() {
		if (!isDisabled()) {
			new Thread(this).start();
		}
		notifyStarted();
	}
	
	@Override
	public void doStop() {
		closeSocket();
		notifyStopped();
	}

	// MARK: - AbstractLink
	
	@Override
	protected long getCurrentTime() {
		if (timeService != null) {
			return timeService.getMissionTime();
		}
		return TimeEncoding.getWallclockTime();
	}

	@Override
	public long getDataOutCount() {
		return dataOutCount.get();
	}

	@Override
	public void resetCounters() {
		super.resetCounters();
		dataOutCount.set(0);
	}

	@Override
	public AggregatedDataLink getParent() {
		return parent;
	}
	
	@Override
	public void setParent(AggregatedDataLink parent) {
		this.parent = parent;
	}

	@Override
	public void doDisable() {
		closeSocket();
	}
	
	@Override
	public void doEnable() {
		new Thread(this).start();
	}
	
	// MARK: - Link
	
	@Override
	public String getDetailedStatus() {
		if (isDisabled()) {
			return String.format("DISABLED (should connect to %s:%d)", host, port);
		}
		if (isSocketOpen()) {
			return String.format("Not connected to %s:%d", host, port);
		} else {
			return String.format("OK, connected to %s:%d, received %d packets", host, port, packetCount.get());
		}
	}
	
	@Override
	protected Status connectionStatus() {
		return !isSocketOpen() ? Status.UNAVAIL : Status.OK;
	}

}
