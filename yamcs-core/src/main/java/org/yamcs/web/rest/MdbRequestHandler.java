package org.yamcs.web.rest;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map.Entry;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.protobuf.Rest.RestDumpRawMdbRequest;
import org.yamcs.protobuf.Rest.RestDumpRawMdbResponse;
import org.yamcs.protobuf.Rest.RestListAvailableParametersRequest;
import org.yamcs.protobuf.Rest.RestListAvailableParametersResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.ByteString;

/**
 * Handles incoming requests related to the Mission Database (offset /mdb).
 */
public class MdbRequestHandler extends AbstractRestRequestHandler {
    private static final Logger log = LoggerFactory.getLogger(MdbRequestHandler.class);

    @Override
    public void handleRequest(ChannelHandlerContext ctx, FullHttpRequest httpRequest, String yamcsInstance, String remainingUri) throws RestException {
        QueryStringDecoder qsDecoder = new QueryStringDecoder(remainingUri);
        if ("parameters".equals(qsDecoder.path())) {
            RestListAvailableParametersRequest request = readMessage(httpRequest, SchemaRest.RestListAvailableParametersRequest.MERGE).build();
            RestListAvailableParametersResponse responseMsg = listAvailableParameters(request, yamcsInstance);
            writeMessage(ctx, httpRequest, qsDecoder, responseMsg, SchemaRest.RestListAvailableParametersResponse.WRITE);
        } else if ("dump".equals(qsDecoder.path())) {
            RestDumpRawMdbRequest request = readMessage(httpRequest, SchemaRest.RestDumpRawMdbRequest.MERGE).build();
            RestDumpRawMdbResponse responseMsg = dumpRawMdb(request, yamcsInstance);
            writeMessage(ctx, httpRequest, qsDecoder, responseMsg, SchemaRest.RestDumpRawMdbResponse.WRITE);
        } else {
            log.debug("No match for '" + qsDecoder.path() + "'");
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
        }
    }

    /**
     * Sends the XTCEDB for the requested yamcs instance.
     */
    private RestListAvailableParametersResponse listAvailableParameters(RestListAvailableParametersRequest request, String yamcsInstance) throws RestException {
        XtceDb mdb = loadMdb(yamcsInstance);
        RestListAvailableParametersResponse.Builder responseb = RestListAvailableParametersResponse.newBuilder();
        if (request.getNamespacesCount() == 0) { // Send all, if no namespace specified
            for(Parameter parameter : mdb.getParameters()) {
                for (Entry<String,String> entry : parameter.getAliasSet().getAliases().entrySet()) {
                    responseb.addIds(NamedObjectId.newBuilder().setNamespace(entry.getKey()).setName(entry.getValue()));
                }
            }
        } else {
            for (Parameter p : mdb.getParameters()) {
                for (String namespace : request.getNamespacesList()) {
                    String alias = p.getAlias(namespace);
                    if (alias != null) {
                        responseb.addIds(NamedObjectId.newBuilder().setNamespace(namespace).setName(alias));
                    }
                }
            }
        }
        return responseb.build();
    }

    private RestDumpRawMdbResponse dumpRawMdb(RestDumpRawMdbRequest request, String yamcsInstance) throws RestException {
        RestDumpRawMdbResponse.Builder responseb = RestDumpRawMdbResponse.newBuilder();

        // TODO TEMP would prefer if we don't send java-serialized data.
        // TODO this limits our abilities to send, say, json
        // TODO and makes clients too dependent
        XtceDb mdb = loadMdb(yamcsInstance);
        ByteString.Output bout = ByteString.newOutput();
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(bout);
            oos.writeObject(mdb);
        } catch (IOException e) {
            throw new InternalServerErrorException("Could not serialize MDB", e);
        } finally {
            if(oos != null) {
                try { oos.close(); } catch (IOException e) {}
            }
        }
        responseb.setRawMdb(bout.toByteString());
        return responseb.build();
    }

    private XtceDb loadMdb(String yamcsInstance) throws RestException {
        try {
            return XtceDbFactory.getInstance(yamcsInstance);
        } catch(ConfigurationException e) {
            log.error("Could not get MDB for instance '" + yamcsInstance + "'", e);
            throw new InternalServerErrorException("Could not get MDB for instance '" + yamcsInstance + "'", e);
        }
    }
}
