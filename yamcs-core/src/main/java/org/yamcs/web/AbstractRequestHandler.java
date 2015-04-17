package org.yamcs.web;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class AbstractRequestHandler {
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    static protected String JSON_MIME_TYPE="application/json";
    static protected String BINARY_MIME_TYPE="application/octet-stream";

    protected void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
	FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
		Unpooled.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));

	response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

	ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sets the Date header for the HTTP response
     * 
     * @param response
     *            HTTP response
     */
    protected void setDateHeader(HttpResponse response) {
	SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
	dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

	Calendar time = new GregorianCalendar();
	response.headers().set(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));
    }

    /**
     * Sets the content type header for the HTTP Response
     * 
     * @param response
     *            HTTP response
     * @param type
     *            content type of file to extract
     */
    protected void setContentTypeHeader(HttpResponse response, String type) {
	response.headers().set(HttpHeaders.Names.CONTENT_TYPE, type);
    }


    /**
     * Sets the Date and Cache headers for the HTTP Response
     * 
     * @param response
     *            HTTP response
     * @param lastModified
     *            the time when the file has been last mdified
     */
    protected void setDateAndCacheHeaders(HttpResponse response, Date lastModified) {
	SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
	dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

	// Date header
	Calendar time = new GregorianCalendar();
	response.headers().set(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));

	// Add cache headers
	//   time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
	//  response.setHeader(HttpHeaders.Names.EXPIRES, dateFormatter.format(time.getTime()));
	// response.setHeader(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
	response.headers().set(HttpHeaders.Names.LAST_MODIFIED, dateFormatter.format(lastModified));
    }
}
