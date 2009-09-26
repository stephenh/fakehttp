package org.jboss.netty.example.proxy;

import java.net.InetSocketAddress;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import static org.jboss.netty.channel.Channels.pipeline;
public class ProxyServer {
	public static void main(String[] args) throws Exception {
		// Parse command line options.
    int localPort = 8080;

		System.err.println("Proxying *: " + localPort + " ...");

		ThreadPoolExecutor exec = new ThreadPoolExecutor(10,1000,60,TimeUnit.SECONDS,new SynchronousQueue<Runnable>());
		// Configure the bootstrap.
		//Executor executor = Executors.new();
		ServerBootstrap sb = new ServerBootstrap(
				new NioServerSocketChannelFactory(exec, exec));

		ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(
				exec, exec);
		// Set up the event pipeline factory.

		
        ChannelPipeline p = pipeline(); 
        p.addLast("requesthandler", new RequestHandler());
        p.addLast("handler", new InboundHandler(cf));
       // Timer timer = new HashedWheelTimer(60, TimeUnit.MILLISECONDS, 60); 
        //p.addLast("readTimeout", new ReadTimeoutHandler(timer, 2, TimeUnit.MINUTES)); 
        //p.addLast("writeTimeout", new WriteTimeoutHandler(timer, 2, TimeUnit.MINUTES)); 

		sb.setPipeline(p);
		sb.setOption("child.tcpNoDelay", true);
		sb.setOption("child.keepAlive", true);
		sb.setOption("backlog", 2048);

		// Start up the server.
		sb.bind(new InetSocketAddress(localPort));
		
	}
}
