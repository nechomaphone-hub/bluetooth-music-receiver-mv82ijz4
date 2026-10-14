package com.generated.app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Set;

public class MainActivity extends Activity {

    // BluetoothProfile.A2DP_SINK hidden constant is 11 in Android SDK
    private static final int PROFILE_A2DP_SINK = 11;
    private static final int REQ_PERMISSIONS = 101;
    private static final int REQ_DISCOVERABLE = 102;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothProfile mA2dpSinkProxy;

    private TextView mTvSupportTitle;
    private TextView mTvSupportDetail;
    private View mViewSupportIndicator;
    private View mCardSupportStatus;

    private TextView mTvLocalName;
    private TextView mTvBtState;
    private TextView mTvDiscoverableState;

    private TextView mTvRemoteName;
    private TextView mTvRemoteMac;
    private TextView mTvConnectionState;
    private View mViewConnectionIndicator;

    private Button mBtnMakeDiscoverable;
    private Button mBtnOpenSettings;
    private Button mBtnCheckSupport;

    private boolean mReceiverRegistered = false;

    private final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                updateLocalDeviceUI();
                if (state == BluetoothAdapter.STATE_ON) {
                    checkA2dpSinkSupport();
                }
            } else if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                updateScanModeUI();
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                handleDeviceConnected(device);
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                handleDeviceDisconnected(device);
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                refreshBondedDevices();
            }
        }
    };

    private final BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == PROFILE_A2DP_SINK) {
                mA2dpSinkProxy = proxy;
                setSupportStatus(true,
                        getString(R.string.support_supported_title),
                        getString(R.string.support_supported_msg));
                inspectConnectedSinkDevices();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == PROFILE_A2DP_SINK) {
                mA2dpSinkProxy = null;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            setSupportStatus(false,
                    "אין תמיכה ב-Bluetooth",
                    "מכשיר זה אינו מכיל רכיב חומרה של Bluetooth.");
            mBtnMakeDiscoverable.setEnabled(false);
            mBtnOpenSettings.setEnabled(false);
            mBtnCheckSupport.setEnabled(false);
            return;
        }

        requestNeededPermissions();
        setupListeners();
        updateLocalDeviceUI();
        registerBtReceivers();
        checkA2dpSinkSupport();
    }

    private void initViews() {
        mTvSupportTitle = findViewById(R.id.tv_support_title);
        mTvSupportDetail = findViewById(R.id.tv_support_detail);
        mViewSupportIndicator = findViewById(R.id.view_support_indicator);
        mCardSupportStatus = findViewById(R.id.card_support_status);

        mTvLocalName = findViewById(R.id.tv_local_name);
        mTvBtState = findViewById(R.id.tv_bt_state);
        mTvDiscoverableState = findViewById(R.id.tv_discoverable_state);

        mTvRemoteName = findViewById(R.id.tv_remote_name);
        mTvRemoteMac = findViewById(R.id.tv_remote_mac);
        mTvConnectionState = findViewById(R.id.tv_connection_state);
        mViewConnectionIndicator = findViewById(R.id.view_connection_indicator);

        mBtnMakeDiscoverable = findViewById(R.id.btn_make_discoverable);
        mBtnOpenSettings = findViewById(R.id.btn_open_settings);
        mBtnCheckSupport = findViewById(R.id.btn_check_support);
    }

    private void setupListeners() {
        mBtnMakeDiscoverable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                makeDeviceDiscoverable();
            }
        });

        mBtnOpenSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "לא ניתן לפתוח הגדרות", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mBtnCheckSupport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkA2dpSinkSupport();
            }
        });
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            String[] permissions = new String[]{
                    "android.permission.BLUETOOTH_CONNECT",
                    "android.permission.BLUETOOTH_SCAN",
                    "android.permission.BLUETOOTH_ADVERTISE"
            };
            boolean needRequest = false;
            for (String perm : permissions) {
                if (checkCallingOrSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }
            if (needRequest) {
                requestPermissions(permissions, REQ_PERMISSIONS);
            }
        }
    }

    private void registerBtReceivers() {
        if (mReceiverRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBtReceiver, filter);
        mReceiverRegistered = true;
    }

    private void updateLocalDeviceUI() {
        if (mBluetoothAdapter == null) return;

        try {
            String name = mBluetoothAdapter.getName();
            if (name != null && !name.isEmpty()) {
                mTvLocalName.setText(name);
            }
        } catch (SecurityException ignored) {
        }

        if (mBluetoothAdapter.isEnabled()) {
            mTvBtState.setText(R.string.state_on);
            mTvBtState.setTextColor(0xFF15803D);
        } else {
            mTvBtState.setText(R.string.state_off);
            mTvBtState.setTextColor(0xFFB91C1C);
        }

        updateScanModeUI();
    }

    private void updateScanModeUI() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            mTvDiscoverableState.setText(R.string.state_not_discoverable);
            return;
        }
        try {
            int scanMode = mBluetoothAdapter.getScanMode();
            if (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                mTvDiscoverableState.setText(R.string.state_discoverable);
                mTvDiscoverableState.setTextColor(0xFF0284C7);
            } else {
                mTvDiscoverableState.setText(R.string.state_not_discoverable);
                mTvDiscoverableState.setTextColor(0xFF64748B);
            }
        } catch (SecurityException ignored) {
        }
    }

    private void makeDeviceDiscoverable() {
        if (mBluetoothAdapter == null) return;
        try {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
            startActivityForResult(discoverableIntent, REQ_DISCOVERABLE);
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בפתיחת מצב גילוי: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Checks if the device's firmware and Android stack support A2DP Sink (Audio Receiver).
     */
    private void checkA2dpSinkSupport() {
        if (mBluetoothAdapter == null) {
            setSupportStatus(false,
                    getString(R.string.support_unsupported_title),
                    getString(R.string.support_unsupported_msg));
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            setSupportStatus(null,
                    "Bluetooth כבוי",
                    "יש להפעיל את ה-Bluetooth כדי לבדוק תמיכת מקלט שמע.");
            return;
        }

        mTvSupportTitle.setText(R.string.support_checking);
        mViewSupportIndicator.setBackgroundResource(R.drawable.circle_status_amber);
        mCardSupportStatus.setBackgroundResource(R.drawable.bg_card);

        try {
            // Profile 11 is hidden constant BluetoothProfile.A2DP_SINK
            boolean success = mBluetoothAdapter.getProfileProxy(this, mProfileListener, PROFILE_A2DP_SINK);
            if (!success) {
                // If getProfileProxy returns false, the Android ROM does not implement A2DP Sink profile
                setSupportStatus(false,
                        getString(R.string.support_unsupported_title),
                        getString(R.string.support_unsupported_msg));
            }
        } catch (Exception e) {
            setSupportStatus(false,
                    getString(R.string.support_unsupported_title),
                    getString(R.string.support_unsupported_msg) + " (" + e.getMessage() + ")");
        }
    }

    private void inspectConnectedSinkDevices() {
        if (mA2dpSinkProxy == null) return;
        try {
            Method getConnectedDevices = mA2dpSinkProxy.getClass().getMethod("getConnectedDevices");
            @SuppressWarnings("unchecked")
            java.util.List<BluetoothDevice> devices = (java.util.List<BluetoothDevice>) getConnectedDevices.invoke(mA2dpSinkProxy);
            if (devices != null && !devices.isEmpty()) {
                BluetoothDevice dev = devices.get(0);
                handleDeviceConnected(dev);
            }
        } catch (Exception ignored) {
        }
    }

    private void setSupportStatus(Boolean supported, String title, String detail) {
        mTvSupportTitle.setText(title);
        mTvSupportDetail.setText(detail);

        if (supported == null) {
            mViewSupportIndicator.setBackgroundResource(R.drawable.circle_status_amber);
            mCardSupportStatus.setBackgroundResource(R.drawable.bg_card);
            mTvSupportTitle.setTextColor(0xFF0F172A);
        } else if (supported) {
            mViewSupportIndicator.setBackgroundResource(R.drawable.circle_status_green);
            mCardSupportStatus.setBackgroundResource(R.drawable.bg_card_success);
            mTvSupportTitle.setTextColor(0xFF15803D);
        } else {
            mViewSupportIndicator.setBackgroundResource(R.drawable.circle_status_red);
            mCardSupportStatus.setBackgroundResource(R.drawable.bg_card_warning);
            mTvSupportTitle.setTextColor(0xFFB91C1C);
        }
    }

    private void handleDeviceConnected(BluetoothDevice device) {
        if (device == null) return;
        try {
            String name = device.getName();
            mTvRemoteName.setText(name != null ? name : "מכשיר לא ידוע");
            mTvRemoteMac.setText(device.getAddress());
            mTvConnectionState.setText(R.string.state_connected);
            mTvConnectionState.setTextColor(0xFF15803D);
            mViewConnectionIndicator.setBackgroundResource(R.drawable.circle_status_green);
        } catch (SecurityException ignored) {
        }
    }

    private void handleDeviceDisconnected(BluetoothDevice device) {
        mTvRemoteName.setText(R.string.none);
        mTvRemoteMac.setText(R.string.none);
        mTvConnectionState.setText(R.string.state_disconnected);
        mTvConnectionState.setTextColor(0xFFB91C1C);
        mViewConnectionIndicator.setBackgroundResource(R.drawable.circle_status_red);
    }

    private void refreshBondedDevices() {
        if (mBluetoothAdapter == null) return;
        try {
            Set<BluetoothDevice> bonded = mBluetoothAdapter.getBondedDevices();
            if (bonded != null && !bonded.isEmpty()) {
                // If a device is bonded, show its info if nothing is connected yet
                if (mTvRemoteName.getText().toString().equals(getString(R.string.none))) {
                    BluetoothDevice first = bonded.iterator().next();
                    mTvRemoteName.setText(first.getName() != null ? first.getName() : "טלפון מצומד");
                    mTvRemoteMac.setText(first.getAddress());
                }
            }
        } catch (SecurityException ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLocalDeviceUI();
        refreshBondedDevices();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiverRegistered) {
            unregisterReceiver(mBtReceiver);
            mReceiverRegistered = false;
        }
        if (mA2dpSinkProxy != null && mBluetoothAdapter != null) {
            try {
                mBluetoothAdapter.closeProfileProxy(PROFILE_A2DP_SINK, mA2dpSinkProxy);
            } catch (Exception ignored) {
            }
        }
    }
}
