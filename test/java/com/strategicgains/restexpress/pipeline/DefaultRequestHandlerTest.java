/*
    Copyright 2010, Strategic Gains, Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/
package com.strategicgains.restexpress.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.UpstreamMessageEvent;
import org.jboss.netty.channel.local.DefaultLocalServerChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.Before;
import org.junit.Test;

import com.strategicgains.restexpress.Format;
import com.strategicgains.restexpress.Request;
import com.strategicgains.restexpress.Response;
import com.strategicgains.restexpress.exception.BadRequestException;
import com.strategicgains.restexpress.response.DefaultResponseWrapper;
import com.strategicgains.restexpress.response.StringBufferHttpResponseWriter;
import com.strategicgains.restexpress.route.RouteDeclaration;
import com.strategicgains.restexpress.route.RouteResolver;
import com.strategicgains.restexpress.serialization.DefaultSerializationResolver;
import com.strategicgains.restexpress.serialization.json.DefaultJsonProcessor;
import com.strategicgains.restexpress.serialization.xml.DefaultXmlProcessor;


/**
 * @author toddf
 * @since Dec 15, 2010
 */
public class DefaultRequestHandlerTest
{
	private DefaultRequestHandler messageHandler;
	private DummyObserver observer;
	private Channel channel;
    private ChannelPipeline pl;
    private StringBuffer httpResponse;
	
	@Before
	public void initialize()
	throws Exception
	{
		DefaultSerializationResolver resolver = new DefaultSerializationResolver();
		resolver.put(Format.JSON, new DefaultJsonProcessor());
		DefaultXmlProcessor xmlProcessor = new DefaultXmlProcessor();
		xmlProcessor.alias("dated", Dated.class);
		resolver.put(Format.XML, xmlProcessor);
		resolver.setDefaultFormat(Format.JSON);
		
		messageHandler = new DefaultRequestHandler(new RouteResolver(new DummyRoutes().createRouteMapping()), resolver);
		observer = new DummyObserver();
		messageHandler.addMessageObserver(observer);
		messageHandler.setResponseWrapperFactory(new DefaultResponseWrapper());
		httpResponse = new StringBuffer();
		messageHandler.setResponseWriter(new StringBufferHttpResponseWriter(httpResponse));
		PipelineBuilder pf = new PipelineBuilder()
			.addRequestHandler(messageHandler);
	    pl = pf.getPipeline();
	    ChannelFactory channelFactory = new DefaultLocalServerChannelFactory();
	    channel = channelFactory.newChannel(pl);
	}

	@Test
	public void shouldNotifyObserverOnSuccess()
	throws Exception
	{
		sendGetEvent("/foo");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\"}", httpResponse.toString());
	}

	@Test
	public void shouldNotifyObserverOnError()
	throws Exception
	{
		sendGetEvent("/bar");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getExceptionCount());
		assertEquals(0, observer.getSuccessCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":400,\"status\":\"error\",\"message\":\"foobar'd\"}", httpResponse.toString());
	}

	@Test
	public void shouldParseTimepointJson()
	{
		sendGetEvent("/date.json", "{\"at\":\"2010-12-17T120000Z\"}");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\",\"data\":{\"at\":\"2010-12-17T12:00:00.000Z\"}}", httpResponse.toString());
	}

	@Test
	public void shouldParseTimepointJsonUsingQueryString()
	{
		sendGetEvent("/date?format=json", "{\"at\":\"2010-12-17T120000Z\"}");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertEquals("{\"code\":200,\"status\":\"success\",\"data\":{\"at\":\"2010-12-17T12:00:00.000Z\"}}", httpResponse.toString());
	}

	@Test
	public void shouldParseTimepointXml()
	{
		sendGetEvent("/date.xml", "<com.strategicgains.restexpress.pipeline.Dated><at>2010-12-17T120000Z</at></com.strategicgains.restexpress.pipeline.Dated>");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertTrue(httpResponse.toString().startsWith("<response>"));
		assertTrue(httpResponse.toString().contains("<code>200</code>"));
		assertTrue(httpResponse.toString().contains("<data class=\"dated\">"));
		assertTrue(httpResponse.toString().contains("<at>2010-12-17T12:00:00.000Z</at>"));
		assertTrue(httpResponse.toString().contains("</data>"));
		assertTrue(httpResponse.toString().endsWith("</response>"));
	}

	@Test
	public void shouldParseTimepointXmlUsingQueryString()
	{
		sendGetEvent("/date?format=xml", "<dated><at>2010-12-17T120000Z</at></dated>");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(1, observer.getSuccessCount());
		assertEquals(0, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertTrue(httpResponse.toString().startsWith("<response>"));
		assertTrue(httpResponse.toString().contains("<code>200</code>"));
		assertTrue(httpResponse.toString().contains("<data class=\"dated\">"));
		assertTrue(httpResponse.toString().contains("<at>2010-12-17T12:00:00.000Z</at>"));
		assertTrue(httpResponse.toString().contains("</data>"));
		assertTrue(httpResponse.toString().endsWith("</response>"));
	}

	@Test
	public void shouldThrowExceptionOnInvalidUrl()
	{
		sendGetEvent("/xyzt.xml");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(0, observer.getSuccessCount());
		assertEquals(1, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertTrue(httpResponse.toString().startsWith("<response>"));
		assertTrue(httpResponse.toString().contains("<code>404</code>"));
		assertTrue(httpResponse.toString().contains("<status>error</status>"));
		assertTrue(httpResponse.toString().contains("<message>Unresolvable URL: http://null/xyzt.xml</message>"));
		assertTrue(httpResponse.toString().endsWith("</response>"));
	}

	@Test
	public void shouldThrowExceptionOnInvalidUrlWithXmlFormatQueryString()
	{
		sendGetEvent("/xyzt?format=xml");
		assertEquals(1, observer.getReceivedCount());
		assertEquals(1, observer.getCompleteCount());
		assertEquals(0, observer.getSuccessCount());
		assertEquals(1, observer.getExceptionCount());
//		System.out.println(httpResponse.toString());
		assertTrue(httpResponse.toString().startsWith("<response>"));
		assertTrue(httpResponse.toString().contains("<code>404</code>"));
		assertTrue(httpResponse.toString().contains("<status>error</status>"));
		assertTrue(httpResponse.toString().contains("<message>Unresolvable URL: http://null/xyzt?format=xml</message>"));
		assertTrue(httpResponse.toString().endsWith("</response>"));
	}

	private void sendGetEvent(String path)
    {
	    pl.sendUpstream(new UpstreamMessageEvent(
	    	channel,
	    	new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path),
	    	new InetSocketAddress(1)));
    }

	private void sendGetEvent(String path, String body)
    {
		HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, path);
		request.setContent(ChannelBuffers.copiedBuffer(body, Charset.defaultCharset()));

	    pl.sendUpstream(new UpstreamMessageEvent(
	    	channel,
	    	request,
	    	new InetSocketAddress(1)));
    }
	
	public class DummyRoutes
	extends RouteDeclaration
	{
		private Object controller = new FooBarController();

        @Override
        protected void defineRoutes()
        {
        	uri("/foo.{format}", controller)
        		.action("fooAction", HttpMethod.GET);

        	uri("/bar.{format}", controller)
        		.action("barAction", HttpMethod.GET);

        	uri("/date.{format}", controller)
    			.action("dateAction", HttpMethod.GET);
        }
	}
	
	public class FooBarController
	{
		public void fooAction(Request request, Response response)
		{
			// do nothing.
		}
		
		public void barAction(Request request, Response response)
		{
			throw new BadRequestException("foobar'd");
		}

		public Object dateAction(Request request, Response response)
		{
			return request.getBodyAs(Dated.class);
		}
	}

	public class DummyObserver
	extends MessageObserver
	{
		private int receivedCount = 0;
		private int exceptionCount = 0;
		private int successCount = 0;
		private int completeCount = 0;

		@Override
        protected void onReceived(Request request, Response response)
        {
			++receivedCount;
        }

		@Override
        protected void onException(Throwable exception, Request request, Response response)
        {
			++exceptionCount;
        }

		@Override
        protected void onSuccess(Request request, Response response)
        {
			++successCount;
        }

		@Override
        protected void onComplete(Request request, Response response)
        {
			++completeCount;
        }

		public int getReceivedCount()
        {
        	return receivedCount;
        }

		public int getExceptionCount()
        {
        	return exceptionCount;
        }

		public int getSuccessCount()
        {
        	return successCount;
        }

		public int getCompleteCount()
        {
        	return completeCount;
        }
	}
}
