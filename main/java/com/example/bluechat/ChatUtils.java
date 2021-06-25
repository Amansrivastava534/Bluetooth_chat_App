package com.example.bluechat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;


public class ChatUtils {
    private static final String TAG="BluetoothchatService";
        private Context context;
    private final Handler handler;

    private ConnectedThread connectedThread;
    private ConnectThread connectThread;

    private AcceptThread acceptThread;
    private  final String APP_NAME="BlueChat";

    private BluetoothAdapter bluetoothAdapter;

    private final UUID APP_UUID=UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    public static final int STATE_NONE=0;
    public static final int STATE_LISTEN=1;
    public  static final  int STATE_CONNECTING=2;
    public static final int STATE_CONNECTED=3;

    private int state;

    public ChatUtils(Context context,Handler handler){
        this.context=context;
        this.handler=handler;
        state=STATE_NONE;
        bluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
    }





    public int getState() {
        return state;
    }

    public synchronized void setState(int state) {
        this.state = state;
        handler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE,state,-1).sendToTarget();
    }
    private synchronized void start(){
        if (connectedThread!=null){
            connectedThread.cancel();
            connectedThread=null;
        }
        if(acceptThread==null){
            acceptThread=new AcceptThread();
            acceptThread.start();
        }
        if (connectThread!=null){
            connectThread.cancel();
            connectThread=null;
        }

        setState(STATE_LISTEN);
    }
   public synchronized void stop(){
        if (connectedThread!=null){
            connectedThread.cancel();
            connectedThread=null;
        }
        if (acceptThread==null){
            acceptThread.cancel();
            acceptThread=null;
        }
       if (connectThread!=null){
           connectThread.cancel();
           connectThread=null;
       }
setState(STATE_NONE);
    }
    public synchronized void connect(BluetoothDevice device){


        if(state==STATE_CONNECTING) {
            if (connectedThread != null) {
                connectedThread.cancel();
                connectedThread = null;
            }
            connectedThread = new ConnectedThread(device);
            connectedThread.start();
        }

        if (connectThread!=null){
            connectThread.cancel();
            connectThread=null;
        }

        setState(STATE_CONNECTING);
    }

    public void write(byte[] buffer){
        ConnectThread connThread;
        synchronized (this){
            if (state!=STATE_CONNECTED){
                return;
            }
            connThread=connectThread;
        }
        connThread.write(buffer);
    }


    private class  AcceptThread extends Thread{
        private BluetoothServerSocket serverSocket;
        public AcceptThread(){
            BluetoothServerSocket tmp=null;
            try {
                tmp=bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME,APP_UUID);

                }catch (IOException e){
                Log.e("Accept->Constructor",e.toString());
            }
            serverSocket=tmp;
            }
            public void run(){
            BluetoothSocket socket=null;
            try {
                socket=serverSocket.accept();
            }catch(IOException e){
                Log.e("Connect->Run",e.toString());
                try {
                    serverSocket.close();
                }catch (IOException e1){
                    Log.e("Connect->Close",e.toString());
                }
            }
            if (socket!=null){
                switch (state){
                    case STATE_LISTEN:
                    case STATE_CONNECTING:
                        connect(socket.getRemoteDevice());
                        break;
                    case STATE_NONE:
                    case STATE_CONNECTED:
                        try{
                            socket.close();

                        }catch (IOException e){
                            Log.e("Accept->CloseSocket",e.toString());
                        }break;
                }
            }
        }
        public void cancel(){
            try {
                serverSocket.close();
            }catch (IOException e){
                Log.e("Accept-->CloseServer",e.toString());
            }
        }
    }



    private class ConnectedThread extends Thread{
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectedThread(BluetoothDevice device) {
            this.device=device;
            BluetoothSocket temp=null;
            try {
                temp=device.createRfcommSocketToServiceRecord(APP_UUID);
            }catch (IOException e){
                Log.e("Connect->Constructor",e.toString());
            }
            socket=temp;
        }



        public void run(){
            try{
                socket.connect();
            }catch (IOException e){
                Log.e("Connect->Run",e.toString());
                try {
                    socket.close();
                }catch (IOException e1){
                    Log.e("Connect->CloseSocket",e.toString());

                }
                connectionFailed();
                return;
            }
            synchronized (ChatUtils.this){
                connectedThread=null;
            }
          connect(device);
        }
        public void cancel(){
            try {
                socket.close();
            }catch (IOException e){
                Log.e("Connect->Cancel",e.toString());
            }
        }
    }
    private  class ConnectThread extends Thread{
       private  final BluetoothSocket socket;
       private final InputStream inputStream;
       private  final OutputStream outputStream;


        private ConnectThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tempIn=null;
            OutputStream tempOut=null;

            try {
                tempIn=socket.getInputStream();
                tempOut=socket.getOutputStream();
            }catch (IOException e){

            }
            inputStream=tempIn;
            outputStream=tempOut;
        }
        public void run(){
            byte[] buffer=new byte[1024];
            int bytes;
            try {
                bytes=inputStream.read(buffer);
                handler.obtainMessage(MainActivity.MESSAGE_READ,bytes,-1,buffer).sendToTarget();
            }catch (IOException e){
                connectionLost();

            }

        }
        public void write(byte[] buffer){
            try {
                outputStream.write(buffer);
                handler.obtainMessage(MainActivity.MESSAGE_WRITE,-1,-1,buffer).sendToTarget();
            }catch (IOException e){

            }
        }
        public void cancel(){
            try {
                socket.close();
            }catch (IOException e){

            }
        }
    }
    private void connectionLost(){
        Message message=handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle=new Bundle();
        bundle.putString(MainActivity.TOAST,"ConnectionLost");
        message.setData(bundle);
        handler.sendMessage(message);
        ChatUtils.this.start();
    }




    private synchronized void connectionFailed(){
        Message message=handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle=new Bundle();
        bundle.putString(MainActivity.TOAST,"Can't Connect to the Device");
        message.setData(bundle);
        handler.sendMessage(message);
        ChatUtils.this.start();
    }
    private synchronized void connected(BluetoothDevice device,BluetoothSocket socket){
        if (connectedThread != null){
            connectedThread.cancel();
            connectedThread=null;
        }
        if (connectThread!=null){
            connectThread.cancel();
            connectThread=null;
        }
        connectThread=new ConnectThread(socket);
        connectThread.start();

        Message message=handler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
        Bundle bundle=new Bundle();
        bundle.putString(MainActivity.DEVICE_NAME,device.getName());
        message.setData(bundle);
        handler.sendMessage(message);
        setState(STATE_CONNECTED);

    }
}

