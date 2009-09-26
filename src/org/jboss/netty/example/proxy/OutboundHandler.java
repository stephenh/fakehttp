package org.jboss.netty.example.proxy;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

@ChannelPipelineCoverage("all")
public class OutboundHandler extends SimpleChannelHandler {

	private final Channel inboundChannel;

	public OutboundHandler(Channel inboundChannel) {
		this.inboundChannel = inboundChannel;
	}

	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
			throws Exception {
		final ChannelBuffer msg = (ChannelBuffer)e.getMessage();

		if (inboundChannel.isConnected())
			inboundChannel.write(msg);
		else{
			
			System.err.println("Inbound channel closed.............DATA loss");
		}
	}

	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e)
			throws Exception {
		if (inboundChannel != null){
			closeOnFlush(inboundChannel);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
			throws Exception {
		//System.out.println("Exception in outbound handler");
		e.getCause().printStackTrace();
		//System.err.println(e.getCause().getMessage());
		closeOnFlush(inboundChannel);
	}

	/**
	 * Closes the specified channel after all queued write requests are flushed.
	 */
	static void closeOnFlush(Channel ch) {
		if (ch.isConnected()) {
			ch.write(ChannelBuffers.EMPTY_BUFFER).addListener(
					ChannelFutureListener.CLOSE);
		}
	}
}