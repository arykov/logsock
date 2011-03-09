/*******************************************************************************
 *
 *	Class Definition:
 *		TcpProxy
 *
 *	Package Name:
 *		com.ryaltech.netutils
 *
 *	Current Version:
 *		1.0
 *
 *	History:
 *		Version		|	Date		|	Author		|	Comments
 *		1.0		| 	03-Sep-2000	| 	Alex Rykov	| 	Original version
 *
 *	Purpose:
 *
 *	Copyright 2000, RyalTech., All Rights Reserved
 ******************************************************************************/

package com.ryaltech.tcpproxy;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.io.OutputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.FileOutputStream;

public class TcpProxy extends Thread
{
	private OutputStream logstream;
	private DataOutputStream datalogstream;
	private static final boolean debug = true;
	private int toport;
	private int fromport;
	private String tohost;	
	private String fromaddress;	
	private String lastinfostring;
	public static final String CRLF = "\n";
	
	public static void main(String args[])
	{
		int toport = -1, fromport = -1;
		String tohost = null, fromaddress=null;
		String log = null;

		for (int j = 0; j<args.length; j++)
		{
			String arg = args[j];			
			if(arg.equals("-fromport"))
			{
				j++;
				fromport = Integer.parseInt(args[j]);
			}
			if(arg.equals("-toport"))
			{
				j++;
				toport = Integer.parseInt(args[j]);
			}	
			if(arg.equals("-fromaddress"))
			{
				j++;
				fromaddress = args[j];
			}	
			if(arg.equals("-tohost"))
			{
				j++;
				tohost = args[j];
			}	
			if(arg.equals("-log"))
			{
				j++;
				log = args[j];
			}
			
		}
		if(toport < 0 || fromport < 0 || tohost == null)
		{
			System.out.println("Usage TcpProxy [-fromaddress address] -fromport portnumber -tohost host -toport portnumber [-log logfile]");			
			return;
		}
		FileOutputStream fos = null;
		try
		{
			if(log != null)fos = new FileOutputStream(log);
		}
		catch(Exception ex)
		{
			System.out.println("Unable to save to log file: " + log);
			ex.printStackTrace();
		}
		TcpProxy proxy = new TcpProxy(fromaddress, fromport, tohost, toport, fos);
		proxy.start();
	}
	
	protected synchronized void writelog(String infostring, byte [] data, int cbytes)
	{
		
		if(logstream == null)return;
		try
		{
			if(lastinfostring != infostring)
			{
				lastinfostring = infostring;
				datalogstream.writeBytes(CRLF);
				datalogstream.writeBytes(infostring);
				datalogstream.writeBytes(CRLF);
			}
			if(data != null)logstream.write(data, 0, cbytes);
			logstream.flush();
		}
		catch(IOException ex)
		{
			ex.printStackTrace();
		}
		
	} 
	protected void writelog(String infostring, byte data[])
	{
		writelog(infostring, data, data.length);
	}
	protected void writelog(String infostring)
	{
		writelog(infostring, null, 0);
	}
	public TcpProxy(String fromaddress, int fromport, String tohost, int toport, OutputStream logstream)
	{
	  this.fromaddress=fromaddress;
		this.logstream = logstream;
		this.fromport = fromport;
		this.toport = toport;
		this.tohost = tohost;
		if(logstream != null)		
			datalogstream = new DataOutputStream(logstream);
		
	}
	public TcpProxy(String fromaddress, int fromport, String tohost, int toport)
	{
		this(fromaddress, fromport, tohost, toport, null);
	}
	
	public void run()
	{
		ServerSocket server = null;
		try
		{
		  if(fromaddress == null)server = new ServerSocket(fromport);
		  else server = new ServerSocket(fromport, 10, InetAddress.getByName(fromaddress));
		}
		catch(IOException ex)
		{
			// unable to listen specified port
			System.out.println("Unable to listen port: "+fromport);
			ex.printStackTrace();
			return;
		}
		
		InetAddress address = null;
		try
		{
			address = InetAddress.getByName(tohost);
		}
		catch(UnknownHostException ex)
		{
			ex.printStackTrace();
			return;
		}
		long hostedConnections = 0;
		while(true)
		{
			Socket insocket = null;
			Socket outsocket = null;
			try
			{
				//listen to the server
				insocket = server.accept();
			}
			catch(IOException ex)
			{
				//unable to establish connection
				ex.printStackTrace();
				continue;
			}
			try
			{			
				//connect to the real server
				outsocket = new Socket(address, toport);
			}
			catch(IOException ex)
			{
				ex.printStackTrace();
				try
				{
					//connection not accepted for some reason
					insocket.close();
					
				}
				catch(IOException ex1)
				{
					//non fatal
					ex1.printStackTrace();
				}
				continue;
			}
			new SocketAdapter(insocket, outsocket).link();
			writelog("Connections since start: " + Long.toString(++hostedConnections));
		}
	}
	class SocketAdapter
	{
		Socket fromsocket, tosocket;
		class StreamRedirector extends Thread
		{
			InputStream in;
			OutputStream out;
			String infostring;
			
			public StreamRedirector(InputStream in,	OutputStream out, String infostring)
			{					
				this.infostring = infostring;
				this.in = in;
				this.out = out;
			}
			public void run()
			{
				while(true)
				{
					byte [] buffer = new byte[1024];
					try
					{
						int bytesread = in.read(buffer);
						if(bytesread < 1)
						{
							SocketAdapter.this.close();
							return;
						}						
						out.write(buffer, 0, bytesread);
						writelog(infostring, buffer, bytesread);						
					}
					catch(IOException ex)
					{
						SocketAdapter.this.close();
						return;
					}
				}
			}
		}
		public SocketAdapter(Socket fromsocket, Socket tosocket)
		{				
			this.fromsocket = fromsocket;
			this.tosocket = tosocket;
		}
		public void link()
		{
			try
			{
				String fromtomsg = "";
				String tofrommsg = "";
				if(logstream != null)
				{
					String fromremote = fromsocket.getInetAddress().getHostAddress()+":"+ new Integer(fromsocket.getPort()).toString();
					String toremote = tosocket.getInetAddress().getHostAddress()+":"+ new Integer(tosocket.getPort()).toString();
					String  sendtolog = "Connected " + fromremote;
						sendtolog+= " -> " +   fromsocket.getLocalAddress().getHostAddress()+":"+ new Integer(fromsocket.getLocalPort()).toString();
						sendtolog+= " -> " +   tosocket.getLocalAddress().getHostAddress()+":"+ new Integer(tosocket.getLocalPort()).toString();
						sendtolog+= " -> " +   toremote;
					
					writelog(sendtolog);										
					fromtomsg = fromremote + " -> " + toremote;
					tofrommsg = toremote + " -> " + fromremote;
				}
				
				new StreamRedirector(fromsocket.getInputStream(), tosocket.getOutputStream(), fromtomsg).start();
				new StreamRedirector(tosocket.getInputStream(), fromsocket.getOutputStream(), tofrommsg).start();
			}
			catch(IOException ex)
			{
				close();
			}
		}
		private void close()
		{
			try
			{
				fromsocket.close();
			}
			catch(IOException ex)
			{				
			}
			try
			{
				tosocket.close();
			}
			catch(IOException ex)
			{				
			}
		
		}	
	}
}
