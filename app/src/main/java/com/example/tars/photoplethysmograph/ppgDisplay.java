package com.example.tars.photoplethysmograph;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;

import java.io.IOException;
import java.util.Calendar;
import java.util.Set;


public class ppgDisplay extends AppCompatActivity {

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothDevice ppgBluetooth;
    private String ppgBluetoothAddress;

    /**
     * Member object for the chat services
     */
    private BluetoothChatService mChatService = null;
//    private PeakDetector peakDetector = null;


    GraphView ppg;
    GraphView ppi;
    private StringBuilder recDataString;
    private double graphLastXValue;
    private double graphLastPeak;
    private double ppiLastXValue;
    private double graphLastReading;
    private int convertedData;
    private boolean dataProcessed;
    LineGraphSeries<DataPoint> ppgDatapoints;
    LineGraphSeries<DataPoint> ppiDatapoints;


    private int samplePointer;
    int lastBiggestSample;
    double lastBiggestSampleX;

    DatabaseHelper myDb;
    Button dbLink;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ppg_display);

        ppg= (GraphView) findViewById(R.id.graph);
        ppi= (GraphView) findViewById(R.id.graph2);

        samplePointer = 0;
        lastBiggestSample = 0;
        lastBiggestSampleX =0;

        recDataString = new StringBuilder();


        ppg.getViewport().setXAxisBoundsManual(true);
        ppg.getViewport().setMinX(0);
        ppg.getViewport().setMaxX(200);
        ppg.getViewport().setYAxisBoundsManual(true);
        ppg.getViewport().setMaxY(1024);
        ppg.getViewport().setMinY(0);
        ppg.setTitle("Raw PPG Data");
//        ppgDatapoints.setColor(Color.GREEN);

        ppi.getViewport().setXAxisBoundsManual(true);
        ppi.getViewport().setMinX(0);
        ppi.getViewport().setMaxX(200);
        ppi.setTitle("PP Intervals");

        ppi.getViewport().setYAxisBoundsManual(true);
        ppi.getViewport().setMaxY(80);
        ppi.getViewport().setMinY(0);

        ppgDatapoints = new LineGraphSeries<>();
        ppiDatapoints = new LineGraphSeries<>();


        ppg.addSeries(ppgDatapoints);
        ppi.addSeries(ppiDatapoints);

        graphLastXValue=0;
        graphLastPeak=0;
        graphLastReading=0;
        convertedData=0;

        myDb = DatabaseHelper.getInstance(getApplicationContext());
        dbLink = (Button)findViewById(R.id.button2);

        mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        Intent intent = getIntent();
        ppgBluetoothAddress = intent.getStringExtra("ppgBluetoothAddress");



    }

    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupComms();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
                mChatService.connect(ppgBluetooth,true);
            }
        }
    }

    private void setupComms() {
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(this, mHandler);
//        peakDetector = new PeakDetector();

        ppgBluetooth = mBluetoothAdapter.getRemoteDevice(ppgBluetoothAddress);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }


    public void ppi(View v){
        Intent intent = new Intent(getApplicationContext(), PPIData.class);
        startActivity(intent);
    }



    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
//            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
//                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
//                            Toast.makeText(ppgDisplay.this, "Connected to PPG", Toast.LENGTH_SHORT).show();
//                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
//                            setStatus(R.string.title_connecting);
                            Toast.makeText(ppgDisplay.this, "Connecting", Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothChatService.STATE_LISTEN:
//                            Toast.makeText(ppgDisplay.this, "Listening", Toast.LENGTH_SHORT).show();
                        case BluetoothChatService.STATE_NONE:
//                            setStatus(R.string.title_not_connected);
//                            Toast.makeText(ppgDisplay.this, "None", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
//                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
//                    String writeMessage = new String(writeBuf);
//                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:

                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    recDataString.append(readMessage);

                    int beginOfLineIndex = recDataString.indexOf("#");
                    int endOfLineIndex = recDataString.indexOf("~", beginOfLineIndex);
                    if ((endOfLineIndex != -1) && (beginOfLineIndex != -1)){

                        String dataInPrint = recDataString.substring(beginOfLineIndex+1,endOfLineIndex);

                        if((dataInPrint.indexOf("#")==-1) && (dataInPrint.indexOf("~")==-1))
                        {
                            graphLastXValue += 1d;
                            convertedData = Integer.parseInt(dataInPrint);
                            if(convertedData>500) {
                                new PeakDetector().execute();
                            }
                            ppgDatapoints.appendData(new DataPoint(graphLastXValue, convertedData), true, 200);
                        }

                        recDataString.delete(0, recDataString.length());
                    }


                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
//                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to "
                                + ppgBluetooth.getName(), Toast.LENGTH_SHORT).show();
                    break;
                case Constants.MESSAGE_TOAST:
//                    Toast.makeText(getApplicationContext(), msg.getData().getString(Constants.TOAST),
//                                Toast.LENGTH_SHORT).show();
//                    Toast.makeText(ppgDisplay.this, "Toast", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };



    public class PeakDetector extends AsyncTask<Void, Void, Double> {

        @Override
        protected void onPostExecute(Double result) {
            super.onPostExecute(result);
            if(result >-1){
                ppiDatapoints.appendData(new DataPoint(lastBiggestSampleX, result), true, 200);
                myDb.insertData(lastBiggestSampleX, result);
                lastBiggestSample = 0;



//                myDb = DatabaseHelper.getInstance(getApplicationContext());
//                Cursor result2 = myDb.getAllData();
//                result2.moveToFirst();
//                double point1=result2.getDouble(1);
            }
        }

        @Override
        protected Double doInBackground(Void... params) {
            double result = -1;

            if(convertedData>lastBiggestSample)
            {
                lastBiggestSample = convertedData;
                lastBiggestSampleX = graphLastXValue;
                samplePointer=0;

            }
            else if(convertedData<lastBiggestSample)
                    {
                        ppiLastXValue += 1d; //am I even using this?
                        if(samplePointer<=2)
                        {
                            samplePointer++;
                        }
                        else //peak found
                        {
                            result = lastBiggestSampleX-graphLastPeak;
                            samplePointer =0;
                            graphLastPeak = lastBiggestSampleX;
                        }
                    }

            graphLastReading = convertedData;
            return result;
        }

    }

}//end of file
