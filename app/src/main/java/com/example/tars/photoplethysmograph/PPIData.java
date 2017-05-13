package com.example.tars.photoplethysmograph;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.IOException;

public class PPIData extends AppCompatActivity {

    DatabaseHelper myDb;
    Cursor cursor;
    PPIProvider ppiService;

    GraphView ppi;
    LineGraphSeries<DataPoint> ppiDatapoints;

    double ppiX;
    double ppiY;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ppidata);

        ppi= (GraphView) findViewById(R.id.graph);
        ppi.getViewport().setXAxisBoundsManual(true);
        ppi.getViewport().setMinX(0);
        ppi.getViewport().setMaxX(200);
//        ppi.getViewport().setScrollable(true);
        ppi.setTitle("PP Intervals");

        ppi.getViewport().setYAxisBoundsManual(true);
        ppi.getViewport().setMaxY(80);
        ppi.getViewport().setMinY(0);

        ppiDatapoints = new LineGraphSeries<>();
        ppi.addSeries(ppiDatapoints);
    }

    @Override
    public void onResume(){
        super.onResume();
//        new PPIFetcher().execute();
        myDb = DatabaseHelper.getInstance(getApplicationContext());
        ppiService = new PPIProvider(this, pHandler);
        ppiService.start();
    }

//    public class PPIFetcher extends AsyncTask<Void, Void, Cursor> {
//
//        @Override
//
//        protected void onPostExecute(Cursor result) {
////            super.onPostExecute(result);
//            if(result.getCount() == 0){
//                Toast.makeText(getApplicationContext(), "No saved data", Toast.LENGTH_SHORT).show();
//            }
//            else{
//                Toast.makeText(getApplicationContext(), "Fetching data...", Toast.LENGTH_LONG).show();
//                result.moveToFirst();
////                while(result.moveToNext()){
//                    ppiDatapoints.appendData(new DataPoint(result.getDouble(1), result.getDouble(2)), true, 10);
////                }
//            }
//        }
//
//        @Override
//        protected Cursor doInBackground(Void... params) {
//            myDb = DatabaseHelper.getInstance(getApplicationContext());
//            Cursor result = myDb.getAllData();
//            return result;
//        }
//
//    }


    public class PPIProvider extends Thread {
        private Handler pHandler;

        public PPIProvider(Context context, Handler handler) {
            pHandler = handler;
        }

        public void run() {
            cursor = myDb.getAllData();
            cursor.moveToFirst();
            while(cursor.moveToNext()) {
                ppiX = cursor.getDouble(1);
                ppiY = cursor.getDouble(2);
                pHandler.obtainMessage(1).sendToTarget();
                }
        }

    }

    private final Handler pHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if(msg.what == 1){
                ppiDatapoints.appendData(new DataPoint(ppiX, ppiY), true, 10);
            }
        }
    };

}// end of Activity class extension
