package org.yamcs.simulator.cfdp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cfdp.DataFile;
import org.yamcs.cfdp.DataFileSegment;
import org.yamcs.cfdp.FileDirective;
import org.yamcs.cfdp.pdu.*;
import org.yamcs.simulator.AbstractSimulator;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.FinishedPacket.FileStatus;
import org.yamcs.simulator.ColSimulator;
import org.yamcs.utils.StringConverter;

/**
 * Receives CFDP files.
 * <p>
 * It doesn't store them but just print a message at the end of the reception.
 * 
 * @author nm
 *
 */
public class CfdpReceiver {
    private static final Logger log = LoggerFactory.getLogger(CfdpReceiver.class);
    final AbstractSimulator simulator;
    final File dataDir;
    private DataFile cfdpDataFile = null;
    List<SegmentRequest> missingSegments;
    MetadataPacket metadata;

    public CfdpReceiver(AbstractSimulator simulator, File dataDir) {
        this.simulator = simulator;
        this.dataDir = dataDir;
    }

    public void processCfdp(ByteBuffer buffer) {
        CfdpPacket packet = CfdpPacket.getCFDPPacket(buffer);
        if (packet.getHeader().isFileDirective()) {
            processFileDirective(packet);
        } else {
            processFileData((FileDataPacket) packet);
        }
    }

    private void processFileDirective(CfdpPacket packet) {
        switch (((FileDirective) packet).getFileDirectiveCode()) {
        case EOF:
            // 1 in 2 chance that we did not receive the EOF packet
            if (Math.random() > 0.5) {
                log.warn("EOF CFDP packet received and dropped (data loss simulation)");
                break;
            }
            processEofPacket((EofPacket) packet);
            break;
        case FINISHED:
            log.info("Finished CFDP packet received");
            break;
        case ACK:
            log.info("ACK CFDP packet received");
            break;
        case METADATA:
            processMetadataPacket((MetadataPacket) packet);
            break;
        case NAK:
            log.info("NAK CFDP packet received");
            break;
        case PROMPT:
            log.info("Prompt CFDP packet received");
            break;
        case KEEP_ALIVE:
            log.info("KeepAlive CFDP packet received");
            break;
        default:
            log.error("CFDP packet of unknown type received");
            break;
        }
    }

    private void processMetadataPacket(MetadataPacket packet) {
        metadata = packet;
        log.info("Metadata CFDP packet received");
        long packetLength = metadata.getFileLength();
        cfdpDataFile = new DataFile(packetLength);
        missingSegments = null;

        ProxyPutRequest proxyPutRequest = null;
        ProxyTransmissionMode proxyTransmissionMode = null;
        ProxyClosureRequest proxyClosureRequest = null;

        if(metadata.getOptions() != null) {
            for (TLV option : metadata.getOptions()) {
                if (option.getType() == TLV.TYPE_MESSAGE_TO_USER) {
                    byte[] value = option.getValue();
                    if (value.length >= 5 && new String(Arrays.copyOfRange(value, 0, 4)).equals(
                            ReservedMessageToUser.MESSAGE_IDENTIFIER)) { // Reserved CFDP message: 32bits = "cfdp" + 8bits message type
                        ReservedMessageToUser reservedMessage = new ReservedMessageToUser(ByteBuffer.wrap(value));
                        switch (reservedMessage.getMessageType()) {
                        case PROXY_PUT_REQUEST:
                            if(proxyPutRequest == null) {
                                proxyPutRequest = new ProxyPutRequest(reservedMessage.getContent());
                            } else throw new PduDecodingException("Multiple Proxy Put Request messages found in metadata packet",
                                    reservedMessage.getContent());
                            break;
                        case PROXY_TRANSMISSION_MODE:
                            if(proxyTransmissionMode == null) {
                                proxyTransmissionMode = new ProxyTransmissionMode(reservedMessage.getContent());
                            } else throw new PduDecodingException("Multiple Proxy Transmission Mode messages found in metadata packet",
                                    reservedMessage.getContent());
                            break;
                        case PROXY_CLOSURE_REQUEST:
                                if(proxyClosureRequest == null) {
                                    proxyClosureRequest = new ProxyClosureRequest(reservedMessage.getContent());
                                } else throw new PduDecodingException("Multiple Proxy Closure Request messages found in metadata packet",
                                        reservedMessage.getContent());
                            break;
                        default:
                            log.warn(
                                    "Ignoring reserved message " + reservedMessage.getMessageType() + ": " + StringConverter.arrayToHexString(
                                            reservedMessage.getContent()));
                            break;
                        }
                    } else {
                        log.warn(
                                "Ignoring message to user TLV: " + StringConverter.arrayToHexString(option.getValue()));
                    }
                } else {
                    log.warn("Ignoring metadata option TLV: " + StringConverter.arrayToHexString(option.getValue()));
                }
            }
        }

        if(proxyPutRequest != null) {
            if(proxyTransmissionMode != null && proxyTransmissionMode.getTransmissionMode() == CfdpPacket.TransmissionMode.UNACKNOWLEDGED) {
                log.warn("Unacknowledged transmission requested but not implemented in simulator, defaulting to acknowledged");
            }
            if(proxyClosureRequest != null && proxyClosureRequest.isClosureRequested()) {
                log.warn("Closure requested but not implemented in simulator, defaulting to acknowledged transmission");
            }

            try {
                // WARNING: Only sends the file with the proxy put request, does not respond with correct messages
                CfdpSender sender = new CfdpSender(simulator, (int) proxyPutRequest.getDestinationEntityId(),
                        new File(dataDir, proxyPutRequest.getSourceFileName()), proxyPutRequest.getDestinationFileName(),
                        List.of(new OriginatingTransactionId(packet.getHeader().getSourceId(), packet.getHeader().getSequenceNumber())),
                        new int[0]);
                ((ColSimulator) simulator).setCfdpSender(sender); // WARNING Unchecked cast
                sender.start();
            } catch (FileNotFoundException e) {
                log.error("File '" + proxyPutRequest.getSourceFileName() + "' does not exist!");
            } catch (ClassCastException e) {
                log.error("Failed to cast " + simulator + " to ColSimulator");
            }
        } else {
            if(proxyTransmissionMode != null) log.warn("Ignoring Proxy Transmission Mode, no Proxy Put Request specified");
            if(proxyClosureRequest != null) log.warn("Ignoring Proxy Closure Request, no Proxy Put Request specified");
        }
    }

    private void processEofPacket(EofPacket packet) {
        ConditionCode code = packet.getConditionCode();
        log.info("EOF CFDP packet received code={}, sending back ACK (EOF) packet", code);

        CfdpHeader header = new CfdpHeader(
                true,
                true,
                false,
                false,
                packet.getHeader().getEntityIdLength(),
                packet.getHeader().getSequenceNumberLength(),
                packet.getHeader().getSourceId(),
                packet.getHeader().getDestinationId(),
                packet.getHeader().getSequenceNumber());
        AckPacket EofAck = new AckPacket(
                FileDirectiveCode.EOF,
                FileDirectiveSubtypeCode.FINISHED_BY_WAYPOINT_OR_OTHER,
                code,
                TransactionStatus.ACTIVE,
                header);
        transmitCfdp(EofAck);
        if (code != ConditionCode.NO_ERROR) {
            return;
        }
        log.info("ACK (EOF) sent, delaying a bit and sending Finished packet");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // checking the file completeness;
        missingSegments = cfdpDataFile.getMissingChunks();
        if (missingSegments.isEmpty()) {
            if(metadata.getFileLength() > 0 || header.isLargeFile()) {
                saveFile();
            }

            log.info("Sending back finished PDU");
            header = new CfdpHeader(
                    true, // file directive
                    true, // towards sender
                    false, // not acknowledged
                    false, // no CRC
                    packet.getHeader().getEntityIdLength(),
                    packet.getHeader().getSequenceNumberLength(),
                    packet.getHeader().getSourceId(),
                    packet.getHeader().getDestinationId(),
                    packet.getHeader().getSequenceNumber());

            FinishedPacket finished = new FinishedPacket(ConditionCode.NO_ERROR,
                    true, // data complete
                    FileStatus.SUCCESSFUL_RETENTION,
                    null,
                    header);

            transmitCfdp(finished);
        } else {
            header = new CfdpHeader(
                    true, // file directive
                    true, // towards sender
                    false, // not acknowledged
                    false, // no CRC
                    packet.getHeader().getEntityIdLength(),
                    packet.getHeader().getSequenceNumberLength(),
                    packet.getHeader().getSourceId(),
                    packet.getHeader().getDestinationId(),
                    packet.getHeader().getSequenceNumber());

            NakPacket nak = new NakPacket(
                    missingSegments.get(0).getSegmentStart(),
                    missingSegments.get(missingSegments.size() - 1).getSegmentEnd(),
                    missingSegments,
                    header);

            log.info("File not complete ({} segments missing), sending NAK", missingSegments.size());
            transmitCfdp(nak);
        }
    }

    private void saveFile() {
        try {
            File f = new File(dataDir, sanitize(metadata.getDestinationFilename()));
            try (FileOutputStream fw = new FileOutputStream(f)) {
                fw.write(cfdpDataFile.getData());
                log.info("CFDP file saved in {}", f.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    String sanitize(String filename) {
        return filename.replace("/", "_");
    }

    private void processFileData(FileDataPacket packet) {
        if (missingSegments == null || missingSegments.isEmpty()) {
            // we're not in "resending mode"
            // 1 in 5 chance to 'lose' the packet
            if (Math.random() > 0.8) {
                log.warn("Received and dropped (data loss simulation) {}", packet);
            } else {
                log.info("Received {}", packet);
                cfdpDataFile.addSegment(new DataFileSegment(packet.getOffset(), packet.getData()));
            }
        } else {
            // we're in resending mode, no more data loss
            cfdpDataFile.addSegment(new DataFileSegment(packet.getOffset(), packet.getData()));
            missingSegments = cfdpDataFile.getMissingChunks();
            log.info("Received missing data: {} still missing: {}", packet, missingSegments.size());
            if (missingSegments.isEmpty()) {
                saveFile();

                CfdpHeader header = new CfdpHeader(
                        true, // file directive
                        true, // towards sender
                        false, // not acknowledged
                        false, // no CRC
                        packet.getHeader().getEntityIdLength(),
                        packet.getHeader().getSequenceNumberLength(),
                        packet.getHeader().getSourceId(),
                        packet.getHeader().getDestinationId(),
                        packet.getHeader().getSequenceNumber());

                FinishedPacket finished = new FinishedPacket(
                        ConditionCode.NO_ERROR,
                        true, // data complete
                        FileStatus.SUCCESSFUL_RETENTION,
                        null,
                        header);

                transmitCfdp(finished);
            }
        }

    }

    protected void transmitCfdp(CfdpPacket packet) {
        simulator.transmitCfdp(packet);
    }
}
