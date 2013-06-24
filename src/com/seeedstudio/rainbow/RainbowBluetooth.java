package com.seeedstudio.rainbow;

import com.seeedstudio.bluetooth.BTkitService;

import java.util.ArrayList;
import java.util.List;

import com.seeedstudio.bluetooth.BTkitDeviceList;
import com.seeedstudio.bluetooth.BTkitService;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class RainbowBluetooth extends Activity {
	// Debugging
	private static final String TAG = "RainbowBluetooth";
	private static final boolean D = true;

	// Message types sent from the BTkitService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BTkitService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;

	private static boolean isSend = false;
	private static boolean seekSend = true;

	// Layout Views
	private TextView mTitle, textView1, textView2, textView3, textView4,
			textView_xyz, textView_auto, textView_R, textView_G, textView_B;
	private CheckBox checkBox1, checkBox2, checkBox3, checkBox4, checkBoxAuto;
	private Button sendButton;
	private EditText sendText;
	private ToggleButton openToggleButton;
	private Spinner spinnerXYZ, spinnerPattern;
	private SeekBar seekbarR, seekbarG, seekbarB;

	private List<String> xyzList = new ArrayList<String>();
	private List<String> patternList = new ArrayList<String>();
	private ArrayAdapter xyzAdapter = null;
	private ArrayAdapter patternAdapter = null;
	// Name of the connected device, 閾炬帴鐨勮澶囩殑鍚嶇О
	private String mConnectedDeviceName = null;
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services, 鑱婂ぉ鏈嶅姟鐨勫璞�	
	private BTkitService mKitService = null;

	Thread send = null;

	private static byte[] temp = new byte[] { 0x52, 0x53, 0x52, 0x42, 0, 0, 0,
			0, 0, 0, 0 };

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (D)
			Log.e(TAG, "+++ ON CREATE +++");

		// Set up the window layout
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.main);
		getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE,
				R.layout.custom_title);

		// Set up the custom title
		mTitle = (TextView) findViewById(R.id.title_left_text);
		mTitle.setText(R.string.app_name);
		mTitle = (TextView) findViewById(R.id.title_right_text);

		// Setting text view
		textView1 = (TextView) this.findViewById(R.id.textView1);
		textView2 = (TextView) this.findViewById(R.id.textView2);
		textView3 = (TextView) this.findViewById(R.id.textView3);
		textView4 = (TextView) this.findViewById(R.id.textView4);
		textView_xyz = (TextView) this.findViewById(R.id.text_xyz);
		textView_auto = (TextView) this.findViewById(R.id.textView_auto);
		textView_R = (TextView) this.findViewById(R.id.textView_R);
		textView_G = (TextView) this.findViewById(R.id.textView_G);
		textView_B = (TextView) this.findViewById(R.id.textView_B);

		sendText = (EditText) this.findViewById(R.id.edit_text_out);

		xyzList.add("X");
		xyzList.add("Y");
		xyzList.add("Z");

		patternList.add("Pattern1");
		patternList.add("Pattern2");
		patternList.add("Pattern3");
		patternList.add("Pattern4");
		patternList.add("Pattern5");
		patternList.add("Pattern6");

		xyzAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, xyzList);
		xyzAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		patternAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, patternList);
		patternAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		spinnerXYZ = (Spinner) this.findViewById(R.id.spinner_xyz);
		spinnerXYZ.setAdapter(xyzAdapter);
		spinnerXYZ.setOnItemSelectedListener(new SpinnerEvent());
		spinnerPattern = (Spinner) this.findViewById(R.id.spinner_pattern);
		spinnerPattern.setAdapter(patternAdapter);
		spinnerPattern.setOnItemSelectedListener(new SpinnerEvent());

		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG)
					.show();
			finish();
			return;
		}
	}

	// after initialize the UI and bluetooth adapter,
	// then turn the bluetooth on and setup up the session.
	@Override
	protected void onStart() {
		super.onStart();
		if (D)
			Log.e(TAG, "++ ON START ++");

		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		} else {
			if (mKitService == null)
				setupSession();
		}
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		if (D)
			Log.e(TAG, "+ ON RESUME +");

		// Performing this check in onResume() covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity
		// returns.
		if (mKitService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't
			// started already
			if (mKitService.getState() == BTkitService.STATE_NONE) {
				// Start the Bluetooth chat services
				mKitService.start();
			}
		}
	}

	private void setupSession() {
		Log.d(TAG, "setupSession()");

		checkBox1 = (CheckBox) this.findViewById(R.id.checkBox1);
		checkBox1.setOnClickListener(new ClickEvent());
		checkBox2 = (CheckBox) this.findViewById(R.id.checkBox2);
		checkBox2.setOnClickListener(new ClickEvent());
		checkBox3 = (CheckBox) this.findViewById(R.id.checkBox3);
		checkBox3.setOnClickListener(new ClickEvent());
		checkBox4 = (CheckBox) this.findViewById(R.id.checkBox4);
		checkBox4.setOnClickListener(new ClickEvent());
		checkBoxAuto = (CheckBox) this.findViewById(R.id.checkBox_auto);
		checkBoxAuto.setOnClickListener(new ClickEvent());

		seekbarR = (SeekBar) this.findViewById(R.id.seekBar_R);
		seekbarR.setMax(255);
		seekbarR.setProgress(200);
		seekbarR.setOnSeekBarChangeListener(new SeekBarEvent());
		seekbarG = (SeekBar) this.findViewById(R.id.seekBar_G);
		seekbarG.setMax(255);
		seekbarG.setProgress(20);
		seekbarG.setOnSeekBarChangeListener(new SeekBarEvent());
		seekbarB = (SeekBar) this.findViewById(R.id.seekBar_B);
		seekbarB.setMax(255);
		seekbarB.setProgress(0);
		seekbarB.setOnSeekBarChangeListener(new SeekBarEvent());

		openToggleButton = (ToggleButton) this.findViewById(R.id.toggle_open);
		openToggleButton.setOnClickListener(new OnClickListener() {

			public void onClick(View v) {

				if (openToggleButton.isChecked()) {
					isSend = true;
					openToggleButton.setText(R.string.open);
				} else {
					isSend = false;
					openToggleButton.setText(R.string.close);
				}
			}
		});

		sendButton = (Button) this.findViewById(R.id.button_send);
		sendButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				sendByte(sendText.getText().toString().getBytes());
			}
		});

		// Initialize the BTkitService to perform bluetooth connections
		mKitService = new BTkitService(this, mHandler);
	}

	class ClickEvent implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			seekSend = true;
			toSendData();
		}
	}

	class SeekBarEvent implements SeekBar.OnSeekBarChangeListener {

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			seekSend = false;
			toSendData();
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {

		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			seekSend = false;
			toSendData();
		}
	}

	class SpinnerEvent implements Spinner.OnItemSelectedListener {

		@Override
		public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			seekSend = true;
			toSendData();
		}

		@Override
		public void onNothingSelected(AdapterView<?> arg0) {
			// TODO Auto-generated method stub

		}

	}

	private void toSendData() {
		byte[] buff = new byte[] { 0x53, 0x53, 0x52, 0x42, 0, 0, 0, 0, 0, 0, 0 }; // "SSRB + ..."
		if (checkBoxAuto.isChecked()) {
			buff[4] = 0x00;// address
			buff[5] = 0x00;// auto on
			if (spinnerPattern.getSelectedItem().toString() == "Pattern1")
				buff[6] = 0; // Rain0000000
			else if (spinnerPattern.getSelectedItem().toString() == "Pattern2")
				buff[6] = 1; // Rain0010000
			else if (spinnerPattern.getSelectedItem().toString() == "Pattern3")
				buff[6] = 2; // .....
			else if (spinnerPattern.getSelectedItem().toString() == "Pattern4")
				buff[6] = 3;
			else if (spinnerPattern.getSelectedItem().toString() == "Pattern5")
				buff[6] = 4;
			else if (spinnerPattern.getSelectedItem().toString() == "Pattern6")
				buff[6] = 5; // Rain0050000
		} else {
			buff[4] = 0x00;// address
			buff[5] = 0x01;// auto off

			if (checkBox1.isChecked()) {
				buff[6] += 1; // Rain0 0 1 0 000
			}
			if (checkBox2.isChecked()) {
				buff[6] += 2; // Rain0 0 1 0 000
			}
			if (checkBox3.isChecked()) {
				buff[6] += 4; // Rain0 0 1 0 000
			}
			if (checkBox4.isChecked()) {
				buff[6] += 8; // Rain00 8 0000
			}

			// x y z control
			if (spinnerXYZ.getSelectedItem().toString() == "X")
				buff[7] = 0x58; // Rain01?X000
			else if (spinnerXYZ.getSelectedItem().toString() == "Y")
				buff[7] = 0x59; // Rain01?Y000
			else if (spinnerXYZ.getSelectedItem().toString() == "Z")
				buff[7] = 0x5a; // Rain01?Z000

			// RGB control
			buff[8] = (byte) seekbarR.getProgress(); // R
			buff[9] = (byte) seekbarG.getProgress(); // G
			buff[10] = (byte) seekbarB.getProgress(); // B
		}

		if (isSend) {
			if (D) {
				Log.d(TAG, "R: " + seekbarR.getProgress());
				for (int i = 0; i < 11; i++) {
					Log.d(TAG, "buff[" + i + "]" + buff[i]);
				}
			}
			sendByte(buff);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.scan:
			// Launch the DeviceListActivity to see devices and do scan
			Intent serverIntent = new Intent(this, BTkitDeviceList.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			return true;
		case R.id.menu_more:
			return true;
		}
		return false;
	}

	/**
	 * Sends a message.
	 * 
	 * @param message
	 *            A string of text to send.
	 */
	private void sendByte(byte[] message) {
		if (mKitService.getState() != BTkitService.STATE_CONNECTED) {
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
					.show();
			return;
		}

		if (message.length > 0) {
			if (D)
				Log.d(TAG, message.length + " " + message);
			mKitService.write(message, false);
		}
	}

	// The Handler that gets information back from the BTkitService
	// 绾跨▼鐨勪氦娴侊紝鏇存柊 UI 绾跨▼銆�
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				if (D)
					Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BTkitService.STATE_CONNECTED:
					mTitle.setText(R.string.title_connected_to);
					mTitle.append(mConnectedDeviceName);
					break;
				case BTkitService.STATE_CONNECTING:
					mTitle.setText(R.string.title_connecting);
					break;
				case BTkitService.STATE_LISTEN:
				case BTkitService.STATE_NONE:
					mTitle.setText(R.string.title_not_connected);
					break;
				}
				break;
			case MESSAGE_WRITE:
				// Toast.makeText(getApplicationContext(), msg.obj.toString(),
				// Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				String message = new String(readBuf, 0, msg.arg1);
				if (D)
					Log.d(TAG + "Read", message);
				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(),
						"Connected to " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}
	};

	// Called when an activity you launched exits(back to this activity).
	// 涓�埇鏄�Intent 鍙戝嚭 requestCode 鍚庯紝杩斿洖杩欎釜 requestCode 鎵�瑙﹀彂鐨勪簨浠躲�
	// e.g :
	// Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
	// startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (D)
			Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras().getString(
						BTkitDeviceList.EXTRA_DEVICE_ADDRESS);
				// Get the BLuetoothDevice object
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
				// Attempt to connect to the device
				mKitService.connect(device);
			}
			break;
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled, so set up a chat session
				setupSession();
			} else {
				// User did not enable Bluetooth or an error occured
				Log.d(TAG, "BT not enabled");
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}

	@Override
	public synchronized void onPause() {
		super.onPause();
		if (D)
			Log.e(TAG, "- ON PAUSE -");
	}

	@Override
	public void onStop() {
		super.onStop();
		if (D)
			Log.e(TAG, "-- ON STOP --");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Stop the Bluetooth chat services
		if (mKitService != null)
			mKitService.stop();
		if (D)
			Log.e(TAG, "--- ON DESTROY ---");
	}

	@Override
	public void finish() {
		super.finish();
		if (D)
			Log.e(TAG, "---- Finish ----");
	}

}