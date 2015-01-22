package org.baobabhealth.myusb.myusb;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.baobabhealth.myusb.myusb.util.SystemUiHider;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 *
 * @see SystemUiHider
 */
public class USBActivity extends Activity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */

    private final String TAG = USBActivity.class.getSimpleName();

    private static UsbSerialPort sPort = null;

    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * If set, will toggle the system UI visibility upon interaction. Otherwise,
     * will show the system UI visibility upon interaction.
     */
    private static final boolean TOGGLE_ON_CLICK = true;

    /**
     * The flags to pass to {@link SystemUiHider#getInstance}.
     */
    private static final int HIDER_FLAGS = SystemUiHider.FLAG_HIDE_NAVIGATION;

    /**
     * The instance of the {@link SystemUiHider} for this activity.
     */
    private SystemUiHider mSystemUiHider;

    private UsbDevice mDevice;
    private Byte[] bytes;
    private static int TIMEOUT = 0;
    private boolean forceClaim = true;
    private UsbManager mUsbManager;
    private UsbInterface mIntf;
    private Button mBtnClick;

    private UsbDeviceConnection mConnection;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private String mURL = "http://192.168.15.104:3000/";

    private WebView myWebView;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    mDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (mDevice != null) {
                            //call method to set up device communication
                            mIntf = mDevice.getInterface(0);
                            UsbEndpoint endpoint = mIntf.getEndpoint(0);
                            mConnection = mUsbManager.openDevice(mDevice);
                            mConnection.claimInterface(mIntf, forceClaim);

                            String label = "XYZ123";

                            String command =
                                    "N\n" +
                                            "A50,50,0,2,2,2,N,\"" + label + "\"\n" +
                                            "B50,100,0,1,2,2,170,B,\"" + label + "\"\n" +
                                            "A50,310,0,3,1,1,N,\"" + label + "\"\n" +
                                            "P1\n";

                            byte[] data;
                            // data = command.getBytes(StandardCharsets.US_ASCII);

                            data = command.getBytes();

                            mConnection.bulkTransfer(endpoint, data, data.length, TIMEOUT);
                        }
                    } else {
                        Log.d("INFO", "permission denied for device " + mDevice);
                    }
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                // UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (mDevice != null) {
                    // call your method that cleans up and closes communication with the device
                    mConnection.releaseInterface(mIntf);
                }
            }

        }
    };

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private PendingIntent mPermissionIntent;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

                @Override
                public void onRunError(Exception e) {
                    Log.d(TAG, "Runner stopped.");
                }

                @Override
                public void onNewData(final byte[] data) {
                    USBActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            USBActivity.this.updateReceivedData(data);
                        }
                    });
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // remove title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_usb);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        final View contentView = findViewById(R.id.fullscreen_content);

        mBtnClick = (Button) findViewById(R.id.btnClick);

        mBtnClick.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                if (sPort != null && mConnection != null) {

                    String label = "XXYYZZ";

                    String czas = new SimpleDateFormat("d MMMMM yyyy'r.' HH:mm s's.'").format(new Date());
                    String command =
                            "N\n" +
                                    "B50,180,0,1,2,10,120,N,\"" + label + "\"\n" +
                                    "A35,30,0,2,2,2,N,\"" + label + "\"\n" +
                                    "A35,76,0,2,2,2,N,\"" + czas + "\"\n" +
                                    "P1\n";

                    byte[] data;

                    data = command.getBytes();

                    try {
                        sPort.write(data, 100);
                    } catch (IOException e) {
                        Log.d(this.getClass().getSimpleName(), "Timeout error!");
                    }

                }

            }
        });

        myWebView = (WebView) findViewById(R.id.webview);

        myWebView.getSettings().setJavaScriptEnabled(true); // enable javascript

        myWebView.addJavascriptInterface(new WebAppInterface(this, this), "Android");

        final Activity activity = USBActivity.this;

        myWebView.setWebViewClient(new WebViewClient() {
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Toast.makeText(activity, description, Toast.LENGTH_SHORT).show();

                showDialog();
            }
        });

        myWebView.loadUrl(mURL);

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

    }

    public void showDialog() {

        // get prompts.xml view
        LayoutInflater li = LayoutInflater.from(USBActivity.this);
        View promptsView = li.inflate(R.layout.prompts, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                USBActivity.this);

        // set prompts.xml to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        final EditText userInput = (EditText) promptsView
                .findViewById(R.id.editTextDialogUserInput);

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // get user input and set it to
                        // result
                        // edit text
                        mURL = userInput.getText().toString();

                        myWebView.loadUrl(mURL);

                    }
                })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.my_options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.about:
                showDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
        // respond to menu item selection

    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            // mTitleTextView.setText("No serial device.");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            mConnection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (mConnection == null) {
                // mTitleTextView.setText("Opening device failed");
                return;
            }

            try {
                sPort.open(mConnection);
                sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                // mTitleTextView.setText("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
            // mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());
        }
        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        final String message = "Read " + data.length + " bytes: \n"
                + HexDump.dumpHexString(data) + "\n\n";
        // mDumpTextView.append(message);
        // mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context // @param driver
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, USBActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

    static void reOpen(Context context) {
        final Intent intent = new Intent(context, USBActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK){
            //Fetch data as above thru Intent(data)
            //and set the value to your edittext
        }
    }

    public void printBarcode(String name, String npid, String datetime, String ward, String test, String barcode) {

        if (sPort == null) {

            DeviceListActivity.show(this);

        }


        if (sPort != null) {

            String command =
                    "N\n" +
                            "q456\n" +
                            "Q151,025\n" +
                            "ZT\n" +
                            "A20,3,0,3,1,1,N,\"" + name + "\"\n" +
                            "A20,35,0,3,1,1,N,\"" + npid + "\"\n" +
                            "A20,63,0,3,1,1,N,\"" + datetime + "\"\n" +
                            "A20,90,0,3,1,1,N,\"" + ward + "\"\n" +
                            "A20,115,0,3,1,1,R,\"" + test + "\"\n" +
                            "B270,65,0,1,2,4,42,B,\"" + barcode + "\"\n" +
                            "P2\n";

            byte[] data;

            data = command.getBytes();

            try {

                sPort.write(data, 100);

                Toast.makeText(USBActivity.this, command, Toast.LENGTH_SHORT).show();

            } catch (IOException e) {

                Toast.makeText(USBActivity.this, e.toString(), Toast.LENGTH_SHORT).show();

                Log.d(this.getClass().getSimpleName(), "Timeout error!");
            }

        }


    }

}