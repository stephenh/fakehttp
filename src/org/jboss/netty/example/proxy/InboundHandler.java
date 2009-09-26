package org.jboss.netty.example.proxy;

import java.net.InetSocketAddress;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;


@ChannelPipelineCoverage("all")
public class InboundHandler extends SimpleChannelUpstreamHandler {

	ClientSocketChannelFactory cf = null;

	ChannelFuture f = null;

	ClientBootstrap cb = null;
	
	private String priviousHost = "";

	private volatile Channel outboundChannel;
	
	static String CRLF = "\r\n";

	public InboundHandler(ClientSocketChannelFactory cf) {
		this.cf = cf;
	}

	@Override
	public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		// Suspend incoming traffic until connected to the remote host.
		// Start the connection attempt.
		cb = new ClientBootstrap(cf);
		cb.getPipeline()
				.addLast("outhandler", new OutboundHandler(e.getChannel()));
	}

	@Override
	public synchronized void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {


		final MessageObject msg = (MessageObject) e.getMessage();
		if(msg.getHost() == null){
			msg.setHost(getPriviousHost());
			msg.setPort(443);
		}
		if((msg.getHost()!= null && !msg.getHost().equals(getPriviousHost())) || msg.isConnect()){
			f = null;
		}

		setPriviousHost(msg.getHost());

		// Connect to remote host.
		Channel inboundChannel = e.getChannel();

		if (f == null && cb != null && !msg.isConnect()) {
			connectToServer(inboundChannel,msg.getHost(),msg.getPort());
		}
		
		//is SSL connect request.
		if (msg.isConnect()) {
			String resp = "HTTP/1.0 200 Connection established"+CRLF+CRLF+CRLF;
			ChannelBuffer respBuf = ChannelBuffers.wrappedBuffer(resp
					.getBytes());
			inboundChannel.write(respBuf);

		} else {
			boolean isRead = false;
			while (!isRead) {
				if (inboundChannel.isReadable() && outboundChannel.isOpen()) {
					outboundChannel.write(msg.getMessage());
					isRead = true;
				}
			}
		}

	}

	public void connectToServer(final Channel inboundChannel, String host, int port){
		inboundChannel.setReadable(false);
		f = cb.connect(new InetSocketAddress(host, port));

		outboundChannel = f.getChannel();

		f.addListener(new ChannelFutureListener() {
			public void operationComplete(ChannelFuture future)
					throws Exception {
				if (future.isSuccess()) {
					// Connection attempt succeeded:
					// Begin to accept incoming traffic.
					inboundChannel.setReadable(true);
				} else {
					// Close the connection if the connection attempt has
					// failed.
					if(inboundChannel.isOpen())
						inboundChannel.close();
				}
			}
		});
	}
	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		f = null;
		if (outboundChannel != null) {
			closeOnFlush(outboundChannel);
			outboundChannel.close().addListener(ChannelFutureListener.CLOSE);
		}
		if (e.getChannel() != null){
			closeOnFlush(e.getChannel());
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		//System.out.println("Exception in inbound handler");
		e.getCause().printStackTrace();
		//System.err.println(e.getCause().getMessage());
		closeOnFlush(outboundChannel);
	}

	/**
	 * Closes the specified channel after all queued write requests are flushed.
	 */
	static void closeOnFlush(Channel ch) {
		if (ch.isOpen()) {
			ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(
					ChannelFutureListener.CLOSE);
		}
	}

	/**
	 * @param priviousHost the priviousHost to set
	 */
	public void setPriviousHost(String priviousHost) {
		this.priviousHost = priviousHost;
	}

	/**
	 * @return the priviousHost
	 */
	public String getPriviousHost() {
		return priviousHost;
	}
}
