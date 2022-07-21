package com.alexandrepokhodoun.nrf51_app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * show list of BLE devices
 */
public class DevicesFragment extends ListFragment {

    private final BluetoothAdapter bluetoothAdapter;
    private boolean mScanning;
    private Handler handler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothDevice nrf51;
    private String nrf51addr;
    private int duration;


    private ArrayList<BluetoothDevice> listItems = new ArrayList<>();
    private ArrayAdapter<BluetoothDevice> listAdapter;

    private Activity activity;

    public DevicesFragment() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        handler = new Handler();


    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);


        listAdapter = new ArrayAdapter<BluetoothDevice>(activity, 0, listItems) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                BluetoothDevice device = listItems.get(position);
                if (view == null)
                    view = activity.getLayoutInflater().inflate(R.layout.device_list_item, parent, false);

                TextView text1 = view.findViewById(R.id.text1);
                TextView text2 = view.findViewById(R.id.text2);
                if (device.getName() == null || device.getName().isEmpty())
                    text1.setText("<unnamed>");
                else
                    text1.setText(device.getName());
                text2.setText(device.getAddress());
                return view;
            }
        };



    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        nrf51addr = sp.getString(Constants.KEY_nrf51addr, "0");
        duration = sp.getInt(Constants.KEY_duration, 20);


        setListAdapter(null);
        View header = activity.getLayoutInflater().inflate(R.layout.device_list_header, null, false);
        getListView().addHeaderView(header, null, false);
        setEmptyText("initializing...");
        ((TextView) getListView().getEmptyView()).setTextSize(18);
        setListAdapter(listAdapter);

    }


    @Override
    public void onResume() {
        super.onResume();
        if (bluetoothAdapter == null || !activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            setEmptyText("bluetooth LE not supported");
        else if (!bluetoothAdapter.isEnabled())
            setEmptyText("bluetooth is disabled");
        else
            setEmptyText("scanning...");
        scanLeDevice(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_devices, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.forget) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
            SharedPreferences.Editor spe = sp.edit();
            spe.putString(Constants.KEY_nrf51addr, "0").apply();

            Fragment fragment = new DevicesFragment();
            getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "devices").commit();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        // ignore requestCode as there is only one in this fragment
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanLeDevice(true);
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(getText(R.string.location_denied_title));
            builder.setMessage(getText(R.string.location_denied_message));
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }


    private BluetoothAdapter.LeScanCallback leScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {

                    if (isAdded()) {

                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (listItems.indexOf(device) < 0) {

                                    boolean valid = device.getName() != null && !device.getName().isEmpty();

                                    if (valid) {

                                        if (nrf51addr.equals("0")) {

                                            if (device.getName().equals("NRF51")) {
                                                listItems.add(device);
                                                nrf51 = device;
                                                listAdapter.notifyDataSetChanged();
                                            }

                                            if (listItems.size() == 1) {


                                                Bundle args = new Bundle();
                                                args.putString("nrf51", nrf51.getAddress());
                                                args.putInt("duration", duration);
                                                handler.removeCallbacksAndMessages(null);

                                                Fragment fragment = new TerminalFragment();
                                                fragment.setArguments(args);
                                                getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").commit();

                                            }
                                        }
                                        else {

                                            if (device.getName().equals("NRF51") && device.getAddress().equals(nrf51addr)) {
                                                listItems.add(device);
                                                nrf51 = device;
                                                listAdapter.notifyDataSetChanged();
                                            }
                                            if (listItems.size() == 1) {
                                                Bundle args = new Bundle();
                                                args.putString("nrf51", nrf51.getAddress());
                                                args.putInt("duration", duration);
                                                handler.removeCallbacksAndMessages(null);

                                                Fragment fragment = new TerminalFragment();
                                                fragment.setArguments(args);
                                                getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "terminal").commit();

                                            }

                                        }
                                    }

                                }
                            }

                        });
                    }
                }
            };


    private void scanLeDevice(final boolean enable) {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && enable) {
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setCancelable(true);
                builder.setTitle(getText(R.string.location_permission_title));
                builder.setMessage(getText(R.string.location_permission_message));
                builder.setNegativeButton("ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int arg1) {
                        d.dismiss();
                        d.cancel();
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
                    }
                });
                builder.show();
                return;
            }
        }
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    bluetoothAdapter.stopLeScan(leScanCallback);
                    scanLeDevice(true);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            bluetoothAdapter.startLeScan(leScanCallback);
        } else {
            mScanning = false;
            bluetoothAdapter.stopLeScan(leScanCallback);
        }

    }


}
