package org.jboss.netty.example.proxy;

import java.util.HashMap;
import java.util.Iterator;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

@ChannelPipelineCoverage("all")
public class RequestHandler extends SimpleChannelHandler {

	static MessageObject msgObj = new MessageObject();
	static String CRLF = "\r\n";

	@Override
	public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
			throws Exception {
		if (e instanceof MessageEvent) {
			ChannelBuffer msg = (ChannelBuffer) ((MessageEvent) e).getMessage();
			ChannelBuffer dummy = msg.copy();
			HashMap<String, String> headers = readHeaders(dummy);
			msgObj.setMessage(msg);

			// Set SSL info
			if (headers != null && headers.get("Command").contains("CONNECT")) {
				msgObj.setSsl(true);
				msgObj.setConnect(true);
				msgObj.setPort(Integer.parseInt(headers.get("Command").split(
						":")[1].split(" ")[0].trim()));
			} else {
				msgObj.setConnect(false);
			}
			// Get host details
			if (headers != null && (headers.get("Command").contains("CONNECT")
					|| headers.get("Command").contains("GET")
					|| headers.get("Command").contains("POST"))) {
				String hostLine = headers.get("Host");

				msgObj.setHost(hostLine.split(":")[0].trim());
				if (!msgObj.isSsl()) {
					String[] chops = hostLine.split(":");
					// For HTTP request, we will get HTTP string in
					// the GET/POST HEADER
					if (chops != null && chops.length == 2) {
						msgObj.setPort(Integer.parseInt(hostLine.split(":")[1]
								.trim()));
					} // For HTTP request, for default port (80)
					// we'll not get any PORT
					else {
						msgObj.setPort(80);
					}
				}

			}

			if (headers != null && (headers.get("Command").startsWith("GET")
					|| headers.get("Command").startsWith("POST"))) {

				String req = buildRequest(msg);
				// if (firstLine.contains("http")) {
				// reqStr = reqStr.replaceFirst("http://.*?/", "/");
				// }
				req = req.replaceFirst("Proxy-", "");
				//req = req.replaceFirst("Proxy-.*live", "Connection: open");
				ChannelBuffer reqBuf = ChannelBuffers.wrappedBuffer(req
						.getBytes());
				msgObj.setMessage(reqBuf);
			}
			Channels.fireMessageReceived(ctx, msgObj);
		} else {
			super.handleUpstream(ctx, e);
		}
	}

	private String buildRequest(ChannelBuffer msg) {
		HashMap<String, String> headers = readHeaders(msg);
		String req = headers.get("Command");
		headers.remove("Command");
		Iterator<String> itrKey = headers.keySet().iterator();
		while (itrKey.hasNext()) {
			String key = itrKey.next();
			req += CRLF + key + ": " + headers.get(key);
		}
		req += CRLF+CRLF;

		while (msg.readable()) {
			req += readLine(msg);
		}
		return req;
	}

	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		msgObj = new MessageObject();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		// System.out.println("Exception in Request handler");
		e.getCause().printStackTrace();
		//System.err.println(e.getCause().getMessage());
		// TODO Auto-generated method stub
		super.exceptionCaught(ctx, e);
	}


	public HashMap<String, String> readHeaders(ChannelBuffer msg) {
		HashMap<String, String> headers = null;
		String command = readLine(msg).trim();
		if (command.startsWith("HTTP") || command.startsWith("GET") || command.startsWith("POST") || command.startsWith("CONNECT")) {
			//setTransferedBytes(0);
			headers = new HashMap<String, String>();
			headers.put("Command", command);
			System.out.println(command);
			String line = readLine(msg).trim();
			while (line.length() != 0) {
				System.out.println(line);
				if (line.indexOf(':') > 0) {
					String[] values = line.split(":");
					headers.put(values[0], values[1]);
					line = readLine(msg).trim();
				} else {
					break;
				}
			}
			System.out.println("______________________________________________________________");
		}
		return headers;

	}

	public String readLine(ChannelBuffer msg) {
		String line = "";
		while (msg.readable()) {
			char inchar = (char) msg.readByte();
			if (inchar != '\n') {
				line += inchar;
			} else {
				break;
			}
		}
		return line;
	}

}
