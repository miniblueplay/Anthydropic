package com.stust.anthydropic;

import static android.os.SystemClock.sleep;

import static java.lang.reflect.Array.getLength;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Toast;

import com.stust.anthydropic.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final int REQUEST_PERMISSION_BT = 2;
    private String TAG = "MainActivity";

    private View view; // 視圖
    private ActivityMainBinding binding; // MVVM 架構宣告
    private Handler mHandler; // 宣告 Handler
    private boolean isBlePostRunning = false; // (旗標)用於控制 Thread 是否繼續執行

    //藍芽連線宣告*********************************
    private static final int BLUETOOTH_REQUEST_CODE = 1;
    private boolean connect = false;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice device = null;
    private String deviceName,deviceAddress;
    private BluetoothSocket socket;
    private ParcelUuid[] deviceUUid;
    private OutputStream os;
    private InputStream is;
    //******************************************

    //藍芽數據暫存********************************
    //接收-------------------
    private static final int CHUNK_SIZE = 4096;
    //發送-------------------
    private int bleSendText[] = {0,0,90,0,1,16};
    //******************************************



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // 使螢幕保持在喚醒狀態
        // setContentView(R.layout.activity_main);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        view = binding.getRoot();
        setContentView(view);
        hideNav();

        // 視圖初始化
        initView();

        // 建立 Handler
        mHandler = new Handler();

        // 藍芽訊息監控 Thread 初始化
        receiveBlePost.start();
        sendBlePost.start();

        // BlueTooth Adapter
        bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // BlueTooth 抓到設備，發送廣播
        IntentFilter filter = new IntentFilter("android.bluetooth.devicepicker.action.DEVICE_SELECTED");
        if(receiver!=null) {
            registerReceiver(receiver, filter); // 廣播
        }

    }

    private void initView() {
        // 獲取按鈕的實例及監聽器
        Button btnBluetooth = binding.btnBluetooth;
        btnBluetooth.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        //InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        switch (v.getId()) {
            // 藍芽連線按鈕
            case R.id.btn_bluetooth:
                blueToothStartUsing();
                break;
        }
    }

    /**藍芽訊息接收(多線程)*/
    Thread receiveBlePost = new Thread(new Runnable() {
        @Override
        public void run() {
            while (true)
                while (isBlePostRunning){
                    try {
                        if (is.available() > 0){
                            receiveMessages();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    
                                }
                            });
                            connect = true;
                        }
                    }catch (IOException e) {
                        e.printStackTrace();
                    }
                }
        }
    });

    /**藍芽訊息發送(多線程)*/
    Thread sendBlePost = new Thread(new Runnable() {
        @Override
        public void run() {
            while (true)
                while (isBlePostRunning && bleSendText[4] == 1){
                    sendMessage(formatIntArray(bleSendText));
                    bleSendText[4] = 0;
                    sleep(50);
                }
        }
    });

    /**藍芽連線監控(多線程)*/
    private Runnable bleConnectionState=new Runnable(){
        @Override
        public void run() {
            // 檢測到沒有收到資料
            if (!connect){
                isBlePostRunning = false; // 關閉藍芽訊息監控
                Toast.makeText(getApplicationContext(),"裝置連線中斷",Toast.LENGTH_SHORT).show();
                Log.d(TAG + " BleConnect","裝置連線中斷");
                //binding.textBleStatus.setText("BlueTooth：Disconnect");
                // 清除藍芽設備資訊
                device = null;
                socket = null;
                is = null;
                os = null;
                sleep(1000); // 等待一會
                blueToothStartUsing(); // 重新連線
            }else{
                Log.v(TAG + " BleConnect","ok");
                connect = false;
                mHandler.postDelayed(bleConnectionState,3000); // 等待一會
            }
        }
    };

    /**藍芽初始化**/
    private void blueToothStartUsing(){
        // 檢查設備是否支援藍芽
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }else{
            // 檢查藍芽權限
            if (checkBluetoothPermission()){
                // 檢查設備是否支援低功耗藍芽(BLE)
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                    Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
                    finish();
                }else{
                    // 檢查是否開啟藍芽
                    if (!bluetoothAdapter.isEnabled()) {
                        // 彈出藍芽視窗(詢問是否打開藍芽)
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        enableBtIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300);
                        startActivity(enableBtIntent);
                    }else{
                        // 開啟藍芽 Scanner 配對介面
                        Toast.makeText(view.getContext(),"配對設備",Toast.LENGTH_SHORT).show();
                        Intent bluetoothPicker = new Intent("android.bluetooth.devicepicker.action.LAUNCH");
                        startActivity(bluetoothPicker);
                    }
                }
            } else {
                Toast.makeText(view.getContext(),"無藍芽權限",Toast.LENGTH_SHORT).show();
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // 权限未授予，请求权限
                    //ActivityCompat.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_BLUETOOTH_SCAN_PERMISSION);
                } else {
                    if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ) {
                        String[] permissions = {Manifest.permission.BLUETOOTH_CONNECT};
                        ActivityCompat.requestPermissions(this, permissions, BLUETOOTH_REQUEST_CODE);
                        Log.d(TAG, "API31_正在請求藍芽權限");
                    } else {
                        requestPermissions(new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN},
                                REQUEST_PERMISSION_BT);
                        Log.d(TAG, "正在請求藍芽權限");
                    }
                }
            }
        }
    }

    /**藍芽廣播回傳**/
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // 檢查GPS權限
            if ( ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
            String action = intent.getAction();
            device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device==null)Toast.makeText(getApplicationContext(),"配對失敗或已離開配對介面 請重新配對設備",Toast.LENGTH_LONG).show();
            else{
                Log.v(TAG + " taggg",""+action);
                deviceName = device.getName(); // Device Name
                deviceAddress = device.getAddress(); // MAC Address
                try {
                    // 回傳選擇裝置進行配對
                    device.createBond();
                    // 與設備進行連線
                    blueConnection();
                    Log.v(TAG + " BleDeviceReceiver", "配對裝置:" + deviceName + " 位址:" + deviceAddress);
                } catch (Exception e) {
                    Log.e(TAG + " CreateBondError", e.getMessage());
                }
            }
        }
    };

    /**藍芽連線**/
    private void blueConnection (){
        if ( ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED);
        try {
            deviceUUid = device.getUuids();
            Log.v(TAG + " BleUUid",""+deviceUUid[0].getUuid());
            Log.v(TAG + " BleUUidSize",""+deviceUUid.length);
            if(socket==null){
                // 連線方法1(不安全的連線)
                //socket=device.createInsecureRfcommSocketToServiceRecord(deviceUUid[0].getUuid());
                // 連線方法2(安全的連線)
                socket=device.createRfcommSocketToServiceRecord(deviceUUid[0].getUuid());
                // 迴圈嘗試進行連線
                while(!socket.isConnected()){
                    try {
                        socket.connect();
                        Log.d(TAG + " BleConnectState",""+socket.isConnected());
                        if(socket.isConnected()){
                            Toast.makeText(getApplicationContext(),"連線成功",Toast.LENGTH_SHORT).show();
                            //binding.textBleStatus.setText("BlueTooth：Connected " + deviceAddress);
                            os=socket.getOutputStream(); // 輸入流
                            is=socket.getInputStream(); // 輸出流
                            isBlePostRunning = true; // 啟動藍芽訊息接收監控器
                            connect = true; // 變更連線狀態
                            mHandler.post(bleConnectionState); // 啟動藍芽連線狀態監控
                            /*
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    
                                }
                            });
                            */
                        }
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(),"裝置連線錯誤 請重新配對設備",Toast.LENGTH_LONG).show();
                        socket = null;
                        break;
                    }
                    // sleep(2000);
                }
            }
        }
        catch (Exception e){
            Log.d(TAG + " BleSocket Error",""+e);
        }
    }

    /**向藍芽裝置發送信息**/
    private void sendMessage(String message) {
        byte[] send = message.getBytes();
        try {
            os.write(send);
            Log.i(TAG, " SendMessages: " + message);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //binding.textSendOut.setText("Send Out：" + message);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, " Problem sending message.", e);
        }
    }

    /**接收藍芽裝置信息並格式化**/
    private void receiveMessages() {
        int num_bytes;
        byte[] buffer = new byte[CHUNK_SIZE];
        try {
            num_bytes = is.read(buffer);
            Log.v(TAG, " ReceivingMessages NumBytes: " + num_bytes);

            if (num_bytes > 0) {
                byte[] message_bytes = new byte[num_bytes];
                System.arraycopy(buffer, 0, message_bytes, 0, num_bytes);
                String message = new String(message_bytes);
                /*
                receiveStrArray = message.split(","); // 將數值轉換成陣列
                if( Integer.parseInt(receiveStrArray[receiveStrArray.length-1]) == num_bytes){
                    Log.i(TAG, " Message received: " + message);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            binding.textSendIn.setText("Send In：" + message);
                        }
                    });
                }else{
                    //Arrays.fill(receiveStrArray, "0"); // 清除陣列元素
                    Log.i(TAG, " Message received: 數值異常");
                }
                */

            }
        } catch (IOException e) {
            Log.e(TAG, " No longer connected.");
        }

    }

    /**確認藍芽權限**/
    private boolean checkBluetoothPermission() {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    /**將陣列轉為字串**/
    private static String formatIntArray(int[] intArray) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < intArray.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            int length = getLength(i);
            sb.append(String.format("%0" + length + "d", intArray[i]));
        }
        return sb.toString();
    }

    /**隱藏導航欄**/
    public void hideNav() {
        Window window = getWindow();
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        Log.d(TAG, "隱藏導航欄");
    }

    /**將在程式關閉前運行**/
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清除裝置資料
        socket = null;
        is = null;
        os = null;
        Toast.makeText(getApplicationContext(),"藍芽已斷開",Toast.LENGTH_SHORT).show();
        // 清除 Handler 避免產生記憶體溢出
        if ( mHandler != null ) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
    }
}