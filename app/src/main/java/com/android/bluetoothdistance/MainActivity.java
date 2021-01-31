package com.android.bluetoothdistance;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static android.provider.Settings.Global.DEVICE_NAME;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity
{
    protected static final String TAG = "TAG";
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    Button mScan;
    BluetoothAdapter mBluetoothAdapter;
    private UUID applicationUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    public void onCreate(Bundle mSavedInstanceState)
    {
        super.onCreate(mSavedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        mScan = (Button) findViewById(R.id.btnGet);
        mScan.setOnClickListener(new View.OnClickListener()
        {
            @SuppressLint("WrongConstant")
            public void onClick(View mView)
            {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (mBluetoothAdapter == null)
                {
                    Toast.makeText(MainActivity.this, "DeviceHasNoSupport", 2000).show();
                }
                else
                {
                    if (!mBluetoothAdapter.isEnabled())
                    {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    }
                    else
                    {
                        ListPairedDevices();
                        Intent connectIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                        startActivityForResult(connectIntent, REQUEST_CONNECT_DEVICE);
                    }
                }
            }
        });
    }// onCreate

    @SuppressLint("WrongConstant")
    public void onActivityResult(int mRequestCode, int mResultCode, Intent mDataIntent)
    {
        super.onActivityResult(mRequestCode, mResultCode, mDataIntent);

        switch (mRequestCode)
        {
            case REQUEST_CONNECT_DEVICE:
                if (mResultCode == Activity.RESULT_OK)
                {
                    Bundle mExtra = mDataIntent.getExtras();
                    String mDeviceAddress = mExtra.getString("DeviceAddress");
                    Log.v(TAG, "Coming incoming address " + mDeviceAddress);
                    BluetoothDevice mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
                    pairToDevice(mBluetoothDevice);
                    Log.v(TAG, "pairToDeviceCalled");
                }
                break;

            case REQUEST_ENABLE_BT:
                if (mResultCode == Activity.RESULT_OK)
                {
                    ListPairedDevices();
                    Intent connectIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                    startActivityForResult(connectIntent, REQUEST_CONNECT_DEVICE);
                }
                else
                {
                    Toast.makeText(MainActivity.this, "BTNotEnabled", 2000).show();
                }
                break;
        }
    }

    private void ListPairedDevices()
    {
        Set<BluetoothDevice> mPairedDevices = mBluetoothAdapter.getBondedDevices();
        if (mPairedDevices.size() > 0)
        {
            for (BluetoothDevice mDevice : mPairedDevices)
            {
                Log.v(TAG, "PairedDevices: " + mDevice.getName() + " " + mDevice.getAddress());
            }
        }
    }

    private void pairToDevice(BluetoothDevice nBluetoothDevice)
    {
        Log.v(TAG, "InsidepairToDeviceCalled");
        openSocket(nBluetoothDevice);
        Log.v(TAG, "LeavingpairToDeviceCalled");
    }

    private void openSocket(BluetoothDevice nBluetoothDevice)
    {
        try
        {
            Log.v(TAG, "InsideOpenSockedCalled");
            final ProgressDialog dialog = new ProgressDialog(this);
            final ConnectRunnable connector = new ConnectRunnable(nBluetoothDevice, dialog);
            Log.v(TAG, "InsideOpenSockedConnecterCalled");
            ProgressDialog.show(this, "Connecting...", nBluetoothDevice.getName() + " : " + nBluetoothDevice.getAddress(),
                    true, true,
                    new OnCancelListener()
                    {
                        public void onCancel(DialogInterface dialog)
                        {
                            connector.cancel();
                        }
                    });
            new Thread(connector).start();
        }
        catch (IOException ex)
        {
            Log.d(TAG, "Could not open bluetooth socket", ex);
        }
    }

    private class ConnectRunnable implements Runnable
    {
        private final ProgressDialog dialog;
        private final BluetoothSocket socket;

        public ConnectRunnable(BluetoothDevice device, ProgressDialog dialog) throws IOException
        {
            socket = device.createRfcommSocketToServiceRecord(applicationUUID);
            this.dialog = dialog;
        }

        public void run()
        {
            try
            {
                Log.v(TAG, "InsideRunnableCalled");
                mBluetoothAdapter.cancelDiscovery();
                socket.connect();
                Log.v(TAG, "InsideRunnableSocketConnectCalled");
            }
            catch (IOException connectException)
            {
                Log.d(TAG, "Could not connect to socket", connectException);
                closeSocket(socket);
                return;
            }
            Log.v(TAG, "Connected");
            dismissDialog(dialog);
            closeSocket(socket);
        }

        public void cancel()
        {
            try
            {
                socket.close();
            }
            catch (IOException e)
            {
                Log.d(TAG, "Canceled connection", e);
            }
        }
    }

    private void dismissDialog(final Dialog dialog)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                dialog.dismiss();
                Log.v(TAG, "DialogClosed");
            }
        });
    }

    private void closeSocket(BluetoothSocket nOpenSocket)
    {
        try
        {
            nOpenSocket.close();
            Log.v(TAG, "SockectClosed");
        }
        catch (IOException ex)
        {
            Log.d(TAG, "Could not close exisiting socket", ex);
        }
    }
    @Override
    public boolean onKeyDown(int mKeyCode, KeyEvent mKeyEvent)
    {
        if ((!(android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.DONUT)
                && mKeyCode == KeyEvent.KEYCODE_BACK && mKeyEvent.getRepeatCount() == 0))
        {
            onBackPressed();
        }
        return super.onKeyDown(mKeyCode, mKeyEvent);
    }

    public void onBackPressed()
    {
        finish();
    }
}