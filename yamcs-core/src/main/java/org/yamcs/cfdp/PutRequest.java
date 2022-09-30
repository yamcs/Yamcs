package org.yamcs.cfdp;

import org.yamcs.YConfiguration;
import org.yamcs.cfdp.pdu.*;
import org.yamcs.cfdp.OngoingCfdpTransfer.FaultHandlingAction;
import org.yamcs.utils.StringConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Put.request (destination CFDP entity ID,
 * [source file name],
 * [destination file name],
 * [segmentation control],
 * [fault handler overrides],
 * [flow label],
 * [transmission mode],
 * [closure requested],
 * [messages to user],
 * [filestore requests])
 */
public class PutRequest extends CfdpRequest{

    // Required fields
    private final long destinationCfdpEntityId;

    // Optional fields
    private String sourceFileName;
    private String destinationFileName;
    private SegmentationControl segmentationControl; // NOT IMPLEMENTED
    private Map<ConditionCode, FaultHandlingAction> faultHandlerOverride; // [[condition code, handler code],...] NOT IMPLEMENTED
    private String flowLabel; // NOT IMPLEMENTED
    private TransmissionMode transmissionMode;
    private boolean closureRequested = false;
    private List<MessageToUser> messagesToUser;
    private List<FileStoreRequest> fileStoreRequests; // NOT IMPLEMENTED

    public enum SegmentationControl {
        RECORD_BOUNDARIES_NOT_PRESERVED(0),
        RECORD_BOUNDARIES_PRESERVED(1);

        private final int value;

        SegmentationControl(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum TransmissionMode {
        ACKNOWLEDGED(0),
        UNACKNOWLEDGED(1);

        private final int value;

        TransmissionMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    protected PutRequest(long destinationCfdpEntityId) {
        super(CfdpRequestType.PUT);
        this.destinationCfdpEntityId = destinationCfdpEntityId;
    }

    protected PutRequest(long destinationCfdpEntityId, String sourceFileName, String destinationFileName,
            SegmentationControl segmentationControl, Map<ConditionCode, FaultHandlingAction> faultHandlerOverride,
            String flowLabel, TransmissionMode transmissionMode, boolean closureRequested,
            List<MessageToUser> messagesToUser, List<FileStoreRequest> fileStoreRequests) {
        this(destinationCfdpEntityId);
        this.sourceFileName = sourceFileName;
        this.destinationFileName = destinationFileName;
        this.segmentationControl = segmentationControl;
        this.faultHandlerOverride = faultHandlerOverride;
        this.flowLabel = flowLabel;
        this.transmissionMode = transmissionMode;
        this.closureRequested = closureRequested;
        this.messagesToUser = messagesToUser;
        this.fileStoreRequests = fileStoreRequests;
    }

    // Constructor for messages to user
    protected PutRequest(long destinationCfdpEntityId, TransmissionMode transmissionMode, List<MessageToUser> messagesToUser) {
        this(destinationCfdpEntityId);
        this.transmissionMode = transmissionMode;
        this.messagesToUser = messagesToUser;
    }

    public CfdpTransactionId process(long initiatorEntityId, long sequenceNumber, ChecksumType checksumType,
            YConfiguration config) {
        // Transaction Start Notification procedure
        CfdpTransactionId transactionId = new CfdpTransactionId(initiatorEntityId, sequenceNumber);
        // Copy File Procedure
            // fault handlers from PR
            // messages to user  & file store requests from PR
            // no source/destination = only metadata
            // transmission mode from PR if specified (Management Information Base otherwise)
            // closure requested from PR if specified (Management Information Base otherwise)

        // TODO: Generalise, only implemented for Message To User only transaction at the moment
        CfdpHeader header = new CfdpHeader(
                true, // file directive
                false, // towards receiver
                isAcknowledged(),
                false, // noCRC
                config.getInt("entityIdLength"),
                config.getInt("sequenceNrLength"),
                initiatorEntityId,
                destinationCfdpEntityId,
                sequenceNumber
        );

        MetadataPacket metadata = new MetadataPacket(
                closureRequested,
                checksumType,
                0,
                "",
                "",
                new ArrayList<>(messagesToUser),
                header
        );

        // TODO: send put request

        return transactionId;
    }

    public long getDestinationCfdpEntityId() {
        return destinationCfdpEntityId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public String getDestinationFileName() {
        return destinationFileName;
    }

    public SegmentationControl getSegmentationControl() {
        return segmentationControl;
    }

    public Map<ConditionCode, FaultHandlingAction> getFaultHandlerOverride() {
        return faultHandlerOverride;
    }

    public String getFlowLabel() {
        return flowLabel;
    }

    public TransmissionMode getTransmissionMode() {
        return transmissionMode;
    }

    public boolean isAcknowledged() {
        return transmissionMode == TransmissionMode.ACKNOWLEDGED;
    }

    public boolean isClosureRequested() {
        return closureRequested;
    }

    public List<MessageToUser> getMessagesToUser() {
        return messagesToUser;
    }

    public List<FileStoreRequest> getFileStoreRequests() {
        return fileStoreRequests;
    }

}
