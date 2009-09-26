package org.jboss.netty.example.proxy;

import java.io.Serializable;

import org.jboss.netty.buffer.ChannelBuffer;

public class MessageObject implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ChannelBuffer message;
	private String host;
	private int port;
	private boolean connect;
	private boolean ssl;
	public ChannelBuffer getMessage() {
		return message;
	}
	public void setMessage(ChannelBuffer message) {
		this.message = message;
	}
	public String getHost() {
		return host;
	}
	public void setHost(String host) {
		this.host = host;
	}
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public boolean isConnect() {
		return connect;
	}
	public void setConnect(boolean connect) {
		this.connect = connect;
	}
	public boolean isSsl() {
		return ssl;
	}
	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}
	


}
