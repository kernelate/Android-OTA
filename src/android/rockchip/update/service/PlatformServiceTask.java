package android.rockchip.update.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import android.os.AsyncTask;
import android.util.Log;

public class PlatformServiceTask extends AsyncTask<Void, Void, Void> {
	private static final String TAG = "PlatformServiceTask";
	
	private static final int TCP_PORT = 5001;
	private static String command;
	
	PlatformServiceTask(String cmd){
		command = cmd;
	}

	@Override
	protected Void doInBackground(Void... params) {
		// TODO Auto-generated method stub
		Socket socket = new Socket();
		InetAddress inetAddress = null;
		SocketAddress socketAddr = new InetSocketAddress(inetAddress, TCP_PORT);
		PrintWriter out;
		try {
			inetAddress = InetAddress.getByName("127.0.0.1");
			socket.connect(socketAddr, 7000);
			out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
			out.println(command);
			socket.setSoTimeout(7000);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			String str = in.readLine();
			Log.d("TCP", "str : " + str);
			out.close();
			socket.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			Log.e(TAG,"Error PlatformService!");
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG,"Error PlatformService");
		}
		return null;
	}

}
