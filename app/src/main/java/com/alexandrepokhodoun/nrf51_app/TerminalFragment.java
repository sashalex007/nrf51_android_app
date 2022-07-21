package com.alexandrepokhodoun.nrf51_app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.content.LocalBroadcastManager;


import com.anychart.AnyChart;
import com.anychart.AnyChartView;
import com.anychart.chart.common.dataentry.DataEntry;
import com.anychart.chart.common.dataentry.ValueDataEntry;
import com.anychart.charts.Cartesian;
import com.anychart.core.cartesian.series.Line;
import com.anychart.core.utils.OrdinalZoom;

import org.apache.poi.ss.usermodel.Chart;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.charts.AxisCrosses;
import org.apache.poi.ss.usermodel.charts.AxisPosition;
import org.apache.poi.ss.usermodel.charts.ChartAxis;
import org.apache.poi.ss.usermodel.charts.ChartDataSource;
import org.apache.poi.ss.usermodel.charts.ChartLegend;
import org.apache.poi.ss.usermodel.charts.DataSources;
import org.apache.poi.ss.usermodel.charts.LegendPosition;
import org.apache.poi.ss.usermodel.charts.LineChartData;
import org.apache.poi.ss.usermodel.charts.LineChartSeries;
import org.apache.poi.ss.usermodel.charts.ValueAxis;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTBoolean;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTLineSer;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTPlotArea;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private enum Connected {False, Pending, True}

    private String nrf51;
    private int duration;
    private String newline = "\r\n";
    private SerialSocket socket;
    private SerialService service;
    private boolean initialStart = true;
    private Connected connected = Connected.False;

    private TextView text;
    private TextView charge;

    private TextView roll;
    private TextView accel;

    private static final String DIALOG_SAVE = "DialogSave";
    private static final String DIALOG_SETTINGS = "DialogSettings";

    private Button capture_button;
    private Button generate_button;
    private Button calibrate_button;
    private Button save_button;
    private AnyChartView anyChartView;
    private boolean capture = false;
    private boolean calibrate = false;
    private boolean measure = false;
    private double calibrateValue1 = 0;

    private ArrayList<Double> data = new ArrayList<Double>();

    private ArrayList<Double> dataA = new ArrayList<Double>();

    private boolean first = true;
    private BroadcastReceiver localBroadcastReceiver;


    private int connectCount = 0;
    private Handler handler = new Handler();
    private Handler handler2;
    private long stopTime;
    private long startTime;
    private int startDelay = 500;
    private boolean delayCalibrate = false;
    private boolean delayCapture = false;

    private Activity activity;


    public TerminalFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        nrf51 = getArguments().getString("nrf51");
        duration = getArguments().getInt("duration");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        activity.stopService(new Intent(activity, SerialService.class));
        super.onDestroy();
    }


    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            activity.startService(new Intent(activity, SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if (service != null && !activity.isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
        activity.bindService(new Intent(activity, SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(activity).unregisterReceiver(localBroadcastReceiver);
        super.onPause();
    }

    @Override
    public void onDetach() {
        try {
            activity.unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null) {
            initialStart = false;
            activity.runOnUiThread(this::connect);
        }

        LocalBroadcastManager.getInstance(activity).registerReceiver(
                localBroadcastReceiver,
                new IntentFilter("file_edit")
        );

        LocalBroadcastManager.getInstance(activity).registerReceiver(
                localBroadcastReceiver,
                new IntentFilter("settings")
        );

    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        if (initialStart && isResumed()) {
            initialStart = false;
            activity.runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        text = view.findViewById(R.id.connection);// TextView performance decreases with number of spans
        charge = view.findViewById(R.id.charge);

        roll = (TextView) view.findViewById(R.id.roll);
        accel = (TextView) view.findViewById(R.id.accel);

        calibrate_button = (Button) view.findViewById(R.id.calibrate);
        capture_button = (Button) view.findViewById(R.id.capture);
        generate_button = (Button) view.findViewById(R.id.generate);
        save_button = (Button) view.findViewById(R.id.save);
        generate_button.setEnabled(false);
        save_button.setEnabled(false);
        capture_button.setEnabled(false);
        calibrate_button.setEnabled(false);

        anyChartView = (AnyChartView) view.findViewById(R.id.any_chart_view);

        System.setProperty("org.apache.poi.javax.xml.stream.XMLInputFactory", "com.fasterxml.aalto.stax.InputFactoryImpl");
        System.setProperty("org.apache.poi.javax.xml.stream.XMLOutputFactory", "com.fasterxml.aalto.stax.OutputFactoryImpl");
        System.setProperty("org.apache.poi.javax.xml.stream.XMLEventFactory", "com.fasterxml.aalto.stax.EventFactoryImpl");
        SharedPreferences mSharedPref = PreferenceManager.getDefaultSharedPreferences(this.activity);
        String cal1string = mSharedPref.getString(Constants.KEY_cal1, "0");
        String cal2string = mSharedPref.getString(Constants.KEY_cal2, "0");
        calibrateValue1 = Double.valueOf(cal1string);

        localBroadcastReceiver = new LocalBroadcastReceiver();

        capture_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                capture();
                handler2 = new Handler();
                handler2.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        generate();
                    }
                }, (duration * 1000) + startDelay);

            }
        });

        calibrate_button.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                send("2");
                startTime = System.nanoTime();
                Toast.makeText(activity, "Calibrating...stay still for 10 seconds", Toast.LENGTH_LONG).show();

                final Handler handler2 = new Handler();
                handler2.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        data.clear();
                        delayCalibrate = false;
                        calibrate = true;
                    }
                }, startDelay);

                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Do something after 5s = 5000ms
                        send("0");
                        generate_button.setText("Stop");
                        calibrate = false;
                        calibrateValue1 = getAverage(data);

                        //save
                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                        SharedPreferences.Editor spe = sp.edit();
                        spe.putString(Constants.KEY_cal1, Double.toString(calibrateValue1)).apply();

                        data.clear();

                        Toast.makeText(activity, "Calibration complete", Toast.LENGTH_LONG).show();
                    }
                }, 10000);
                return false;
            }
        });

        calibrate_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!calibrate) {
                    Toast.makeText(activity, "Long-press to calibrate. Stay still for 10 seconds", Toast.LENGTH_LONG).show();
                }
            }
        });

        generate_button.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                handler2.removeCallbacksAndMessages(null);
                generate();
                return false;
            }
        });

        generate_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeText("Long-press to stop");
            }
        });

        save_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                        return;
                    }
                }

                FragmentManager manager = ((MainActivity) v.getContext()).getSupportFragmentManager();
                saveDialog dialog = saveDialog
                        .newInstance();
                dialog.show(manager, DIALOG_SAVE);


            }
        });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.settings) {

            if (!measure) {
                Bundle args = new Bundle();
                args.putInt("duration", duration);

                FragmentManager manager = ((MainActivity) getContext()).getSupportFragmentManager();
                settingsDialog dialog = settingsDialog.newInstance();
                dialog.setArguments(args);
                dialog.show(manager, DIALOG_SETTINGS);
            } else {
                makeText("Data capture in progress");
            }

            return true;
        } else if (id == R.id.forget) {

            if (!measure) {
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                SharedPreferences.Editor spe = sp.edit();
                spe.putString(Constants.KEY_nrf51addr, "0").apply();
                spe.putString(Constants.KEY_cal1, "0").apply();

                Fragment fragment = new DevicesFragment();
                getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "devices").commit();
            } else {
                makeText("Data capture in progress");
            }

            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(nrf51);

            String nrf51 = device.getName() != null ? device.getName() : device.getAddress();

            status("connecting...");
            connected = Connected.Pending;
            socket = new SerialSocket();
            service.connect(this, "Connected to " + nrf51);
            socket.connect(getContext(), service, device);

        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }


    private void disconnect() {

        connected = Connected.False;
        service.disconnect();
        if (socket != null) {
            socket.disconnect();
        }


        socket = null;
        connectCount = 0;
        handler.removeCallbacks(runnableCode);
        capture_button.setEnabled(false);
        calibrate_button.setEnabled(false);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // reconnect after 1/2 second
                connect();
            }
        }, 1500);

    }

    private void send(String str) {
        if (connected != Connected.True) {
            //Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            byte[] data = (str + newline).getBytes();
            socket.write(data);

            if (str.equals("1")) {
                ((MainActivity) activity).supressScreen();
                measure = true;
                delayCapture = true;
                capture_button.setEnabled(false);
                calibrate_button.setEnabled(false);
                save_button.setEnabled(false);
                generate_button.setEnabled(true);
            }
            if (str.equals("2")) {
                //if calibrate
                ((MainActivity) activity).supressScreen();
                measure = true;
                delayCalibrate = true;
                capture_button.setEnabled(false);
                calibrate_button.setEnabled(false);
                save_button.setEnabled(false);
                generate_button.setEnabled(false);
            }
            if (str.equals("0")) {
                stopTime = System.nanoTime();
                ((MainActivity) activity).unSupressScreen();
                measure = false;
                capture_button.setEnabled(true);
                calibrate_button.setEnabled(true);
                generate_button.setEnabled(false);
            }

        } catch (Exception e) {
            onSerialIoError(e);
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void receive(byte[] data) {
        Display(new String(data));
    }

    private void status(String str) {
        text.setText(str);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        connectCount++;

        if (connectCount == 1) {
            text.setText("connected");
            connected = Connected.True;

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
            String nrf51addr = sp.getString(Constants.KEY_nrf51addr, "0");

            if (nrf51addr.equals("0")) {
                SharedPreferences.Editor spe = sp.edit();
                spe.putString(Constants.KEY_nrf51addr, nrf51).apply();
            }

            send("11");
            handler.post(runnableCode);
            capture_button.setEnabled(true);
            calibrate_button.setEnabled(true);

            service.connect(this, "Modules connected");
        }


    }

    @Override
    public void onSerialConnectError(Exception e) {
        //status("connecting...");
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        //status("connecting...");
        disconnect();
    }

    /*
     * Functions
     */
    private void capture() {
        data.clear();
        dataA.clear();
        send("1");

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startTime = System.nanoTime();
                delayCapture = false;
                capture = true;
            }
        }, startDelay);
    }

    private void generate() {
        generate_button.setText("Stop");
        send("0");
        capture = false;
        int samples = 0;

        List<DataEntry> dataChart2 = new ArrayList<>();
        List<DataEntry> accelChart2 = new ArrayList<>();

        samples = data.size();

        for (int i = 0; i < samples; i++) {
            double temp = data.get(i);
            double tempA = dataA.get(i);
            dataChart2.add(new ValueDataEntry(i, temp));

            if (temp < 0) {
                tempA = -tempA;
                dataA.set(i, tempA);
            }

            accelChart2.add(new ValueDataEntry(i, tempA));

        }

        if (!first) {
            anyChartView.clear();
        }

        Cartesian cartesian = AnyChart.line();
        cartesian.isVertical(true);
        cartesian.yScale().inverted(true);

        cartesian.xScroller(true);
        cartesian.xScroller().height(35);
        cartesian.xScroller().orientation("left");
        cartesian.xAxis(false);


        OrdinalZoom xZoom = cartesian.xZoom();
        xZoom.setToPointsCount(6, false, null);
        xZoom.getStartRatio();
        xZoom.getEndRatio();



        Line roll = cartesian.line(dataChart2);
        roll.name("Roll");
        roll.color("#388E3C");


        Line accel = cartesian.line(accelChart2);
        accel.name("Acceleration");
        accel.color("#303F9F");

        cartesian.legend().enabled(true);
        cartesian.yGrid(0);

//                cartesian.lineMarker(10)
//                        .value(10)
//                        .axis(cartesian.yAxis(10));

        anyChartView.setChart(cartesian);
        dataChart2.clear();
        accelChart2.clear();

        first = false;
        save_button.setEnabled(true);
    }

    private void saveExcelFile(ArrayList<Double> d1, String name, ArrayList<Double> d1A) {

        try {

            Date c = Calendar.getInstance().getTime();
            SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy");
            String formattedDate = df.format(c);

            String csv = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();

            if (name.equals("")) {
                SimpleDateFormat df2 = new SimpleDateFormat("HH.mm.ss");
                name = df2.format(c);
            }

            String filename = name + "_" + formattedDate + ".xlsx";
            String filePath = csv + "/Download" + File.separator + filename;
            List<double[]> writeData = convertForExcelWrite(d1, d1A);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet(filename);

            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Samples");
            headerRow.createCell(1).setCellValue("Roll");
            headerRow.createCell(2).setCellValue("Accel");
            headerRow.createCell(5).setCellValue("Duration(ms)");

            for (int i = 0; i < writeData.size(); i++) {
                double[] rowData = writeData.get(i);
                Row rowhead = sheet.createRow((short) i+1);

                for (int j = 0; j < rowData.length; j++) {
                    rowhead.createCell(j).setCellValue(rowData[j]);
                    if (i == 0) {
                        rowhead.createCell(5).setCellValue(timeElapsedMS(startTime));
                    }
                }

            }


            Drawing drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 5, 5, 20, 30);

            Chart chart = drawing.createChart(anchor);
            ChartLegend legend = chart.getOrCreateLegend();
            legend.setPosition(LegendPosition.RIGHT);


            LineChartData data = chart.getChartDataFactory().createLineChartData();

            ChartAxis bottomAxis = chart.getChartAxisFactory().createCategoryAxis(AxisPosition.BOTTOM);
            ValueAxis leftAxis = chart.getChartAxisFactory().createValueAxis(AxisPosition.LEFT);
            leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);


            ChartDataSource<Number> xs = DataSources.fromNumericCellRange(sheet, new CellRangeAddress(1, writeData.size(), 0, 0));
            ChartDataSource<Number> ys1 = DataSources.fromNumericCellRange(sheet, new CellRangeAddress(1, writeData.size(), 1, 1));
            ChartDataSource<Number> ys2 = DataSources.fromNumericCellRange(sheet, new CellRangeAddress(1, writeData.size(), 2, 2));



            LineChartSeries series1 = data.addSeries(xs, ys1);
            series1.setTitle("Roll");
            LineChartSeries series2 = data.addSeries(xs, ys2);
            series2.setTitle("Accel");


            chart.plot(data, bottomAxis, leftAxis);

            XSSFChart xssfChart = (XSSFChart) chart;
            CTPlotArea plotArea = xssfChart.getCTChart().getPlotArea();
            plotArea.getLineChartArray()[0].getSmooth();
            CTBoolean ctBool = CTBoolean.Factory.newInstance();
            ctBool.setVal(false);
            plotArea.getLineChartArray()[0].setSmooth(ctBool);
            for (CTLineSer ser : plotArea.getLineChartArray()[0].getSerArray()) {
                ser.setSmooth(ctBool);
            }

            FileOutputStream fileOut = new FileOutputStream(filePath, false);
            workbook.write(fileOut);
            fileOut.close();
            workbook.close();
            Toast.makeText(activity, "Saved in downloads folder", Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(activity, e.toString(), Toast.LENGTH_LONG).show();
            Log.d("write", e.toString());
        }

    }

    private List<double[]> convertForExcelWrite(ArrayList<Double> array1, ArrayList<Double> array3) {
        List<double[]> data = new ArrayList<double[]>();
        int samples = array1.size();


        for (int i = 0; i < samples; i++) {
            double temp = array1.get(i);
            double temp4 = array3.get(i);
            double temp3 = i;

            data.add(new double[]{temp3, temp, temp4});

        }

        return data;
    }

    private double getAverage(ArrayList<Double> array) {
        double sum = 0.0;
        for (double val : array) {
            sum += val;
        }

        double avg = array.size() > 0 ? sum / array.size() : 0.0d;

        return avg;
    }

    private double round(double num) {
        double val;
        val = num * 100;
        val = Math.round(val);
        val = val / 100;

        return val;
    }

    private String timeElapsed(long startTime) {
        String formated;
        long elapsed = ((duration * 1000) - (System.nanoTime() - startTime)) * -1;

        elapsed = duration - Math.round(elapsed / 1000000000);
        formated = Long.toString(elapsed);
        return formated;
    }

    private int timeElapsedMS(long startTime) {
        double elapsed = (stopTime - startTime)/1000000;
        int x = (int) elapsed;
        return x;
    }

    private String timeElapsedCalibrate(long startTime) {
        String formated;
        long elapsed = ((10 * 1000) - (System.nanoTime() - startTime)) * -1;

        elapsed = 10 - Math.round(elapsed / 1000000000);
        formated = Long.toString(elapsed);
        return formated;
    }

    private double getMax(ArrayList<Double> data) {
        double max = 0;
        for (double val : data) {
            val = Math.abs(val);
            if (val > max) {
                max = val;
            }
        }
        return max;
    }

    private class LocalBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            // safety check
            if (intent == null || intent.getAction() == null) {
                Log.d("", "intent null");
                return;
            }
            if (intent.getAction().equals("file_edit")) {
                String name = intent.getStringExtra("name");
                saveExcelFile(data, name, dataA);
            }
            if (intent.getAction().equals("settings")) {
                duration = intent.getIntExtra("duration", 20);
                makeText("Duration changed");
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
                SharedPreferences.Editor spe = sp.edit();
                spe.putInt(Constants.KEY_duration, duration).apply();

            }

        }
    }

    public void Display(final String s) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (capture) {
                    generate_button.setText(timeElapsed(startTime));
                }
                if (delayCalibrate || calibrate) {
                    generate_button.setText(timeElapsedCalibrate(startTime));
                }

                String direction = s.substring(0, 1);
                String type = s.substring(1, 2);

                if (direction.equals("r") && !type.equals("v")) {
                    Double value = Double.valueOf(formatString(s));
                    double valueCalibrated = ((round(value - calibrateValue1)) * -1);
                    double acceleration = formatStringA(s);

                    if (capture) {
                        data.add(valueCalibrated);
                        dataA.add(acceleration);
                    }
                    if (calibrate) {
                        data.add(value);
                    }
                    roll.setText("Roll: " + valueCalibrated);
                    accel.setText("Accel: " + acceleration);
                }

                if (direction.equals("r") && type.equals("v")) {
                    Double value = Double.valueOf(formatStringV(s));
                    charge.setText("Battery: " + getBatteryPercentage(value) + "%");

                }

            }
        });
    }

    private String formatString(String str) {
        String formatted;
        formatted = str.substring(2,8);
        formatted = formatted.trim();

        return formatted;
    }

    private Double formatStringA(String str) {
        Double value;
        String formatted;
        formatted = str.substring(9,15);
        formatted = formatted.trim();

        value = Double.valueOf(formatted) -1;

        if (value < 0) {
            return 0.0;
        }

        return value*8.0;
    }

    private String formatStringV(String str) {
        String formatted;
        formatted = str.substring(3);
        formatted = formatted.trim();

        return formatted;
    }

    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            if (!measure) {
                send("11");
            }
            handler.postDelayed(this, 10000);
        }
    };

    private long getBatteryPercentage(double voltage) {
        double percentage;
        percentage = (voltage - 2.9 + 0.5) * 200;

        if (percentage > 100) {
            percentage = 100;
        }
        if (percentage < 0) {
            percentage = 0;
        }

        return Math.round(percentage);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        // ignore requestCode as there is only one in this fragment
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            FragmentManager manager = ((MainActivity) getContext()).getSupportFragmentManager();
            saveDialog dialog = saveDialog
                    .newInstance();
            dialog.show(manager, DIALOG_SAVE);

        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("Folder access denied");
            builder.setMessage("This application needs folder access to save files.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }

    private void makeText(String string) {
        Toast.makeText(activity, string, Toast.LENGTH_LONG).show();
    }


}
