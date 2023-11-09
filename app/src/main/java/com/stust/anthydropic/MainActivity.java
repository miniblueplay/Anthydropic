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
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;

import com.stust.anthydropic.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private static final int REQUEST_PERMISSION_BT = 2;
    private String TAG = "MainActivity";

    private View view; // 視圖
    private ActivityMainBinding binding; // MVVM 架構宣告
    private Handler mHandler; // 宣告 Handler
    private boolean isBlePostRunning = false; // (旗標)用於控制 Thread 是否繼續執行
    private boolean setViewSeekBar = true; // 用於控制 SeekBar 是否顯示

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
    private String receiveStrArray[] = {"00.0", "00.0", "00.0", "00.0", "00.0", "00.0","00"};
    //發送-------------------
    private int bleSendText[] = {0,0,0,0,0,1};
                                                // "小指,名指,中指,食指,拇指,始能"
    //******************************************


    @Override
    protected void onResume() {
        super.onResume();
        //隱藏導航欄
        hideNav();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // 使螢幕保持在喚醒狀態
        // setContentView(R.layout.activity_main);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        view = binding.getRoot();
        setContentView(view);

        //隱藏導航欄
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
        // 模式切換
        binding.switchAuto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked){
                    binding.switchAuto.setText("自動");
                    setViewSeekBar = true;
                    if (connect)
                        viewSeekBar(true);
                }else {
                    binding.switchAuto.setText("手動");
                    setViewSeekBar = false;
                    if (connect)
                        viewSeekBar(false);
                }
            }
        });

        // 獲取按鈕的實例及監聽器
        Button btnBluetooth = binding.btnBluetooth;
        btnBluetooth.setOnClickListener(this);

        //小指
        binding.mSeekBar1.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                bleSendText[1-1] = progress;
                bleSendText[5] = 1; // 提示有訊息要發送
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar){}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        //名指
        binding.mSeekBar2.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                bleSendText[2-1] = progress;
                bleSendText[5] = 1; // 提示有訊息要發送
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar){}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        //中指
        binding.mSeekBar3.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                bleSendText[3-1] = progress;
                bleSendText[5] = 1; // 提示有訊息要發送
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar){}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        //食指
        binding.mSeekBar4.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                bleSendText[4-1] = progress;
                bleSendText[5] = 1; // 提示有訊息要發送
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar){}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        //拇指
        binding.mSeekBar5.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                bleSendText[5-1] = progress;
                bleSendText[5] = 1; // 提示有訊息要發送
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar){}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void viewSeekBar(boolean nw){
        if(nw && setViewSeekBar){
            binding.mSeekBar1.setVisibility(View.VISIBLE);
            binding.mSeekBar2.setVisibility(View.VISIBLE);
            binding.mSeekBar3.setVisibility(View.VISIBLE);
            binding.mSeekBar4.setVisibility(View.VISIBLE);
        } else {
            binding.mSeekBar1.setVisibility(View.INVISIBLE);
            binding.mSeekBar2.setVisibility(View.INVISIBLE);
            binding.mSeekBar3.setVisibility(View.INVISIBLE);
            binding.mSeekBar4.setVisibility(View.INVISIBLE);
        }

    }

    @Override
    public void onClick(View v) {
        //InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        switch (v.getId()) {
            // 藍芽連線按鈕
            case R.id.btn_bluetooth:
                if (connect){
                    connect = false;
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
                }
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
                                    binding.INFOR.setText(String.format("%04.1f", Float.parseFloat(receiveStrArray[0])));
                                    binding.OUT1.setText(String.format("%04.1f", Float.parseFloat(receiveStrArray[1])));
                                    binding.OUT2.setText(String.format("%04.1f", Float.parseFloat(receiveStrArray[2])));
                                    binding.OUT3.setText(String.format("%04.1f", Float.parseFloat(receiveStrArray[3])));
                                    binding.OUT4.setText(String.format("%04.1f", Float.parseFloat(receiveStrArray[4])));
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
                while (isBlePostRunning && bleSendText[5] == 1){
                    sendMessage(formatIntArray(bleSendText));
                    bleSendText[5] = 0;
                    sleep(50);
                }
        }
    });

    /**藍芽連線監控(多線程)x*/
    private Runnable bleConnectionState=new Runnable(){
        @Override
        public void run() {
            // 檢測到沒有收到資料
            if (!connect){
                isBlePostRunning = false; // 關閉藍芽訊息監控
                Toast.makeText(getApplicationContext(),"裝置連線中斷",Toast.LENGTH_SHORT).show();
                Log.d(TAG + " BleConnect","裝置連線中斷");
                //binding.textBleStatus.setText("BlueTooth：Disconnect");
                viewSeekBar(false);
                binding.linearLayout.setVisibility(View.INVISIBLE);
                binding.linearLayout2.setVisibility(View.INVISIBLE);
                binding.btnBluetooth.setVisibility(View.VISIBLE);
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
            // 檢查設備是否支援低功耗藍芽(BLE)
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                // 檢查權限
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION);
                }
                // 檢查是否開啟藍芽
                if (!bluetoothAdapter.isEnabled()) {
                    // 彈出藍芽視窗(詢問是否打開藍芽)
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    enableBtIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                    startActivity(enableBtIntent);
                } else {
                    // 開啟藍芽 Scanner 配對介面
                    Toast.makeText(view.getContext(), "配對設備", Toast.LENGTH_SHORT).show();
                    Intent bluetoothPicker = new Intent("android.bluetooth.devicepicker.action.LAUNCH");
                    startActivity(bluetoothPicker);
                }
            }
        }
    }

    /**藍芽廣播回傳**/
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // 檢查權限
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

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    viewSeekBar(true);
                                    //binding.linearLayout.setVisibility(View.VISIBLE);
                                    //binding.linearLayout2.setVisibility(View.VISIBLE);
                                    binding.btnBluetooth.setVisibility(View.INVISIBLE);
                                }
                            });

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

                receiveStrArray = message.split(","); // 將數值轉換成陣列
                if( Integer.parseInt(receiveStrArray[receiveStrArray.length-1]) == num_bytes){
                    Log.i(TAG, " Message received: " + message);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //binding.textSendIn.setText("Send In：" + message);
                        }
                    });
                }else{
                    Arrays.fill(receiveStrArray, "0"); // 清除陣列元素
                    Log.i(TAG, " Message received: 數值異常");
                    Log.i(TAG, " Message received: " + message);
                }

            }
        } catch (IOException e) {
            Log.e(TAG, " No longer connected.");
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

    /**決定字串每個元素的長度**/
    private static int getLength(int index) {
        switch (index) {
            case 5:
                return 1;
            default:
                return 3;
        }
    }

    /**隱藏導航欄**/
    public void hideNav() {
        Window window = getWindow();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        //| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        //| View.SYSTEM_UI_FLAG_LOW_PROFILE
                        //| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        //| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        //Log.d(TAG, "隱藏導航欄");
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