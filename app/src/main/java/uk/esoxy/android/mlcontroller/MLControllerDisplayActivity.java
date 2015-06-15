package uk.esoxy.android.mlcontroller;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Vector;

import uk.esoxy.android.mlcontroller.driver.ApiDevice;
import uk.esoxy.android.mlcontroller.driver.IDeviceDriver;
import uk.esoxy.android.mlcontroller.driver.IDeviceDriver.InvalidResult;
import uk.esoxy.android.mlcontroller.driver.NativeDevice;
import uk.esoxy.android.mlcontroller.ptp.PtpDeviceDescriptor;
import uk.esoxy.android.mlcontroller.ptp.PtpResult;
import uk.esoxy.android.mlcontroller.ptp.PtpTransaction;
import uk.esoxy.android.mlcontroller.ptp.ml.MlDeviceDescriptor;
import uk.esoxy.android.mlcontroller.ptp.ml.MlMenu;
import uk.esoxy.android.mlcontroller.utils.BitmapView;
import uk.esoxy.android.mlcontroller.utils.DpiUtils;
import android.app.ActionBar;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.TextView;

public class MLControllerDisplayActivity extends Activity {
	private Object mDevice;
	private Object mUsbManager;
	private Object mConnection;
	private IDeviceDriver mDriver;
	private LiveViewRefresher mLr;
	private BitmapView bmpview;
	private ExpandableListView listView;
	private MlMenuExpandableListAdapter listViewAdapter;

	private static final String ACTION_USB_PERMISSION = "uk.esoxy.android.mlcontroller.USB_PERMISSION";
	private BroadcastReceiver mUsbReceiver = null;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
				requestWindowFeature(Window.FEATURE_NO_TITLE);
		        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
		} else {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
	        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		
		bmpview = new BitmapView(this);
		setContentView(R.layout.main);
		bmpview = (BitmapView) findViewById(R.id.bmpview);
		mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
		mDevice = null;
		mLr = null;
		bmpview.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (listView!=null) {
					if (listView.getVisibility() == View.VISIBLE) {
						listView.setVisibility(View.GONE);
					} else {
						listView.setVisibility(View.VISIBLE);
					}
					if (Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.HONEYCOMB) {
						ActionBar ab = getActionBar();
						if (ab!=null) {
							if (listView.getVisibility() == View.VISIBLE) {
								ab.show();
							} else {
								ab.hide();
							}
						}
					}
				}
			}
		});
		listView = (ExpandableListView) findViewById(R.id.mlmenu);
		listViewAdapter = new MlMenuExpandableListAdapter();
		listView.setAdapter(listViewAdapter);
		listView.setOnChildClickListener(new OnChildClickListener() {
			public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
				MlMenu m = (MlMenu) listViewAdapter.getChild(groupPosition, childPosition);
				m.pressSelect(mDriver);
				listViewAdapter.notifyDataSetChanged();
				return true;
			}
		});
	}

	private void setUSBReceiver() {
		mUsbReceiver = new BroadcastReceiver() {
			public void onReceive(Context context, Intent intent) {
				String action = intent.getAction();
				if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
					finish();
				} else if (ACTION_USB_PERMISSION.equals(action)) {
					synchronized (this) {
						UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
						if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
							if (device != null) {
								setApiDevice(device);
								if (mDevice == null) {
									finish();
								}
							} else {
								finish();
							}
						} else {
							finish();
						}
					}
				}
			}
		};
	}

	@Override
	public void onResume() {
		super.onResume();
		Intent intent = getIntent();
		String action = intent.getAction();
		if (MLControllerActivity.SELECT_USB_DEVICE_KERNEL.equals(action)) {
			Log.d("USB", "Main menu intent received: kernel driver");
			NativeDevice device = (NativeDevice) intent.getSerializableExtra(MLControllerActivity.KERNEL_DEVICE);
			setKernelDevice(device);
			if (mDevice == null) {
				finish();
			}
		}
		if (Integer.parseInt(Build.VERSION.SDK) >= Build.VERSION_CODES.HONEYCOMB) {
			if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				Log.d("USB", "Broadcast intent received");
				UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				setApiDevice(device);
				IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
				setUSBReceiver();
				registerReceiver(mUsbReceiver, filter);
				if (mDevice == null) {
					finish();
				}
			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				Log.d("USB", "Disconnect intent received");
				finish();
			} else if (MLControllerActivity.SELECT_USB_DEVICE_API.equals(action)) {
				Log.d("USB", "Main menu intent received: api driver");
				PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0,
						new Intent(ACTION_USB_PERMISSION), 0);
				IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
				filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
				setUSBReceiver();
				registerReceiver(mUsbReceiver, filter);
				UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				((UsbManager) mUsbManager).requestPermission(device, mPermissionIntent);
			}
		} else {
			finish();
		}
	}

	@Override
	protected void onPause() {
		if (mLr != null) mLr.cancel(false);
		mLr = null;
		if (mUsbReceiver != null) {
			unregisterReceiver(mUsbReceiver);
			mUsbReceiver = null;
		}
		super.onPause();
	}

	private void setKernelDevice(NativeDevice device) {
		if (device.open(false)) {
			if (!device.checkPtp()) {
				Log.d("USB", "Invalid endpoints");
				device.close();
				mDevice = null;
				return;
			}
			if (!device.claimInterface()) {
				Log.d("USB", "Couldn't open interface");
				device.close();
				mDevice = null;
				return;
			}
			mDriver = device;
			Log.d("USB", "Connected to device");
			PtpDeviceDescriptor d;
			try {
				d = mDriver.getDeviceDescriptor();
			} catch (InvalidResult e) {
				Log.d("USB", String.format("getID Invalid result id: %04x", e.getResultCode()));
				device.close();
				mDevice = null;
				return;
			}
			setTitle(d.getModel() + " (S/N: " + d.getSerialNumber() + " API)");

			try {
				mDriver.startSession();
			} catch (InvalidResult e) {
				Log.d("USB", String.format("startSession Invalid result id: %04x", e.getResultCode()));
				device.close();
				mDevice = null;
				return;
			}
			mDevice = device;
			mLr = new LiveViewRefresher();
			mLr.execute(mDriver);
		} else {
			Log.d("USB", "Could not open device!");
			mDevice = null;
		}
	}

	private void setApiDevice(UsbDevice device) {
		if (device == null) {
			Log.d("USB", "No device!");
			mDevice = null;
			return;
		}
		if (device.getInterfaceCount() != 1) {
			Log.d("USB", "Invalid interface count!");
			mDevice = null;
			return;
		}
		UsbInterface intf = device.getInterface(0);
		if (intf.getEndpointCount() < 2) {
			Log.d("USB", "Not enough endpoints");
			mDevice = null;
			return;
		}
		UsbEndpoint epin = intf.getEndpoint(0);
		if (epin.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK || epin.getDirection() != UsbConstants.USB_DIR_IN) {
			Log.d("USB", "Invalid endpoint no. 1");
			mDevice = null;
			return;
		}
		UsbEndpoint epout = intf.getEndpoint(1);
		if (epout.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK || epout.getDirection() != UsbConstants.USB_DIR_OUT) {
			Log.d("USB", "Invalid endpoint no. 2");
			mDevice = null;
			return;
		}
		mDevice = device;
		if (device != null) {
			UsbDeviceConnection connection = ((UsbManager) mUsbManager).openDevice(device);
			if (connection != null && connection.claimInterface(intf, true)) {
				mConnection = connection;
				mDriver = new ApiDevice((UsbDeviceConnection) mConnection, epin, epout,
						(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("hw_accel", false)));
				Log.d("USB", "Connected to device");

				PtpDeviceDescriptor d;
				try {
					d = mDriver.getDeviceDescriptor();
				} catch (InvalidResult e) {
					Log.d("USB", String.format("getID Invalid result id: %04x", e.getResultCode()));
					connection.close();
					mDevice = null;
					return;
				}
				setTitle(d.getModel() + " (S/N: " + d.getSerialNumber() + " API)");

				try {
					mDriver.startSession();
				} catch (InvalidResult e) {
					Log.d("USB", String.format("startSession Invalid result id: %04x", e.getResultCode()));
					connection.close();
					mDevice = null;
					return;
				}

				mLr = new LiveViewRefresher();
				mLr.execute(mDriver);
			} else {
				Log.d("USB", "Could not connect to device");
				mConnection = null;
			}
		}
	}

	private class LiveViewRefresher extends AsyncTask<IDeviceDriver, Object, String> {

		@Override
		protected String doInBackground(IDeviceDriver... params) {
			IDeviceDriver dev = params[0];
			PtpResult r;
			publishProgress(new MlMenu(0,"Version",-1));
			PtpDeviceDescriptor d;
			boolean found = false;
			try {
				d = dev.getDeviceDescriptor();
				for (int operation : d.getOperations()) {
					if (operation == -24088) {
						MlDeviceDescriptor m = dev.getMlDescriptor();
						found = true;
						publishProgress(new MlMenu(-1, "USB: " + m.getUsbMajor() + "." + m.getUsbMinor(), 0));
						publishProgress(new MlMenu(-1, "Build Version: " + m.getBuildVersion(), 0));
						publishProgress(new MlMenu(-1, "Build ID: " + m.getBuildId(), 0));
						publishProgress(new MlMenu(-1, "Build Date: " + m.getBuildDate(), 0));
						publishProgress(new MlMenu(-1, "Build User: " + m.getBuildUser(), 0));
					}
				}
			} catch (InvalidResult e1) {
			}
			if (!found) {
				publishProgress(new MlMenu(-1, "ML not found!", 0));
			} else {
				Vector<Integer> v = new Vector<Integer>();
				r = dev.doTransaction(-24088, new int[] { 4 }, PtpTransaction.DATA_RECV, null);
				if (r.getResult() == 0x2001) {
					ByteBuffer b = ByteBuffer.wrap(r.getData());
					b.order(ByteOrder.LITTLE_ENDIAN);
					while (b.hasRemaining()) {
						int id = b.getInt();
						int size = b.getInt();
						byte[] text = new byte[size];
						b.get(text);
						String s = new String(text);
						publishProgress(new MlMenu(0,s,id));
						v.add(id);
					}
				}
				for (Integer groupId : v) {
					r = dev.doTransaction(-24088, new int[] { 5, groupId }, PtpTransaction.DATA_RECV, null);
					if (r.getResult() == 0x2001) {
						ByteBuffer b = ByteBuffer.wrap(r.getData());
						b.order(ByteOrder.LITTLE_ENDIAN);
						while (b.hasRemaining()) {
							int id = b.getInt();
							int min = b.getInt();
							int max = b.getInt();
							int flags = b.getInt();
							b.getInt();
							int data = b.getInt();
							int size = b.getInt();
							byte[] text = new byte[size];
							b.get(text);
							String s = new String(text);
							String[] choices = null;
							if ((flags & 16) != 0) {
								choices = new String[max+1];
								for (int i=0; i<=max; i++) {
									size = b.getInt();
									text = new byte[size];
									b.get(text);
									choices[i] = new String(text);
								}
							}
							MlMenu m = new MlMenu(groupId,s,id,min, max, flags, data, choices);
							publishProgress(m);
						}
					}
				}
			}
			r = dev.doTransaction(-28396, new int[] { 0x1 }, PtpTransaction.DATA_NONE, null);
			r = dev.doTransaction(-28395, new int[] { 0x1 }, PtpTransaction.DATA_NONE, null);
			r = dev.doTransaction(-28390, new int[] { 0x1D02914, 0x1000, 0x1 }, PtpTransaction.DATA_NONE, null);
			byte[] data = new byte[4096];
			int errorcount = 0;
			Bitmap bmp = null;
			while (!isCancelled()) {
				try {
					r = dev.doTransaction(-28394, new int[] {}, PtpTransaction.DATA_RECV, data);
					if (r.getResult() < 0) {
						errorcount++;
					} else {
						errorcount -= 8;
						if (errorcount < 0) errorcount = 0;
					}
					bmp = dev.getLVImage(bmp, false);
					if (bmp != null) publishProgress(bmp);
					if (errorcount > 64) cancel(false);
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
					}
				} catch (Exception e) {
					StackTraceElement[] s = e.getStackTrace();
					for (StackTraceElement se : s)
						Log.e("USB", se.toString());

					Log.d("USB", e.toString());
					errorcount += 16;
				}
			}
			/*
			r = dev.doTransaction(-28395, new int[] { 0x0 }, PtpTransaction.DATA_NONE, null);
			r = dev.doTransaction(-28396, new int[] { 0x0 }, PtpTransaction.DATA_NONE, null);
			*/
			return null;
		}

		@Override
		protected void onProgressUpdate(Object... values) {
			super.onProgressUpdate(values);
			if (values[0] instanceof MlMenu) {
				MlMenu m = (MlMenu)values[0];
				if (m.getGroupId()==0) {
					listViewAdapter.AddGroup(m);
				} else {
					listViewAdapter.AddChild(m);
				}
			} else if (values[0] instanceof Bitmap) {
				bmpview.setActiveBitmap((Bitmap) values[0]);
				bmpview.invalidate();
			}
		}

		@Override
		protected void onCancelled() {
			mLr = null;
		}

		@Override
		protected void onPostExecute(String result) {
			mLr = null;
		}

	}

	public class MlMenuExpandableListAdapter extends BaseExpandableListAdapter {

		private Vector<Pair<MlMenu, Vector<MlMenu>>> groups;

		public MlMenuExpandableListAdapter() {
			groups = new Vector<Pair<MlMenu, Vector<MlMenu>>>();
		}

		public void AddGroup(MlMenu group) {
			groups.add(new Pair<MlMenu, Vector<MlMenu>>(group, new Vector<MlMenu>()));
			notifyDataSetChanged();
		}

		public void AddChild(MlMenu child) {
			for (Pair<MlMenu, Vector<MlMenu>> p : groups) {
				if (p.first.getId() == child.getGroupId()) {
					p.second.add(child);
					return;
				}
			}
			notifyDataSetChanged();
		}

		public void AddChild(int groupPosition, MlMenu child) {
			groups.get(groupPosition).second.add(child);
			notifyDataSetChanged();
		}

		public Object getChild(int groupPosition, int childPosition) {
			return groups.get(groupPosition).second.get(childPosition);
		}

		public long getChildId(int groupPosition, int childPosition) {
			return childPosition;
		}

		public int getChildrenCount(int groupPosition) {
			return groups.get(groupPosition).second.size();
		}

		public TextView getGenericView() {
			// Layout parameters for the ExpandableListView
			AbsListView.LayoutParams lp = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
					DpiUtils.getDpi(MLControllerDisplayActivity.this, 64));

			TextView textView = new TextView(MLControllerDisplayActivity.this);
			textView.setLayoutParams(lp);
			textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
			textView.setPadding(DpiUtils.getDpi(MLControllerDisplayActivity.this, 16), 8, 16, 8);
			return textView;
		}

		public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView,
				ViewGroup parent) {
			TextView textView = getGenericView();
			MlMenu m = (MlMenu) getChild(groupPosition, childPosition);
			textView.setText(m.toString()+": "+m.getData());
			return textView;
		}

		public Object getGroup(int groupPosition) {
			return groups.get(groupPosition);
		}

		public int getGroupCount() {
			return groups.size();
		}

		public long getGroupId(int groupPosition) {
			return groupPosition;
		}

		@SuppressWarnings("unchecked")
		public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
			TextView textView = getGenericView();
			textView.setText(((Pair<MlMenu, Vector<MlMenu>>)getGroup(groupPosition)).first.toString());
			textView.setPadding(DpiUtils.getDpi(MLControllerDisplayActivity.this, 32), 8, 16, 8);
			//textView.setCompoundDrawablePadding(DpiUtils.getDpi(MLControllerDisplayActivity.this, 8));
			/*if (isExpanded) {
				textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.collapse, 0, 0, 0);
			} else {
				textView.setCompoundDrawablesWithIntrinsicBounds(R.drawable.expand, 0, 0, 0);
			}*/
			return textView;
		}

		public boolean isChildSelectable(int groupPosition, int childPosition) {
			return groups.get(groupPosition).second.get(childPosition).getId()>0;
		}

		public boolean hasStableIds() {
			return true;
		}
	}
}
