package uk.esoxy.android.mlcontroller.ptp;

import uk.esoxy.android.mlcontroller.driver.IDeviceDriver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.util.Log;

public class PtpTransaction {
	static int transactionId = 1;

	static ByteBuffer basebuffer = null;

	static public final int DATA_NONE = 0;
	static public final int DATA_SEND = 1;
	static public final int DATA_RECV = 2;

	private IDeviceDriver mDevice;
	private int mOpcode;
	private int mTrId;
	private int[] mParams;
	private int mDirection;
	private byte[] mData;

	public PtpTransaction(IDeviceDriver c, int opcode, int[] params, int direction, byte[] data) {
		mDevice = c;
		mOpcode = opcode;
		synchronized (getClass()) {
			mTrId = transactionId;
			transactionId++;
			if (basebuffer == null) {
				basebuffer = ByteBuffer.allocateDirect(16384);
			}
		}
		mParams = params;
		mDirection = direction;
		mData = data;
	}

	public PtpTransaction(IDeviceDriver d, int opcode, int[] params, int direction) {
		this(d, opcode, params, direction, null);
	}

	public PtpTransaction(IDeviceDriver d, int opcode, int[] params) {
		this(d, opcode, params, DATA_NONE);
	}

	public PtpTransaction(IDeviceDriver d, int opcode) {
		this(d, opcode, new int[] {});
	}

	public PtpResult runTransaction() {
		try {
		synchronized (basebuffer) {
			byte[] inData = null;
			int[] outParam = null;
			int size = 12 + mParams.length * 4;
			int type = 0;
			int opcode = 0;
			int trid = 0;
			int done = 0;

			ByteBuffer b = basebuffer;
			b.rewind();
			b.order(ByteOrder.LITTLE_ENDIAN);
			b.putInt(size);
			b.putShort((short) 1);
			b.putShort((short) mOpcode);
			b.putInt(mTrId);
			for (int i : mParams)
				b.putInt(i);

			int data = mDevice.bulkTransferOut(b.array(), size, 1000);
			if (data < size) return new PtpResult(data);

			if (mDirection == DATA_SEND) {
				b = basebuffer;
				b.rewind();
				b.order(ByteOrder.LITTLE_ENDIAN);
				b.putInt(mData.length + 12);
				b.putShort((short) 2);
				b.putShort((short) mOpcode);
				b.putInt(mTrId);
				data = mDevice.bulkTransferOut(b.array(), 12, 1000);
				if (data != 12) return new PtpResult(data);
				data = mDevice.bulkTransferOut(mData, mData.length, 1000);
				if (data < 0) return new PtpResult(data);
			} else if (mDirection == DATA_RECV) {
				b = basebuffer;
				b.rewind();				
				b.order(ByteOrder.LITTLE_ENDIAN);
				type = 0;
				opcode = 0;
				trid = 0;
				int remaining = 0;
				while (!((type == 2 || type == 3) && (opcode == mOpcode || type == 3) && trid == mTrId)) {
					data = mDevice.bulkTransferIn(b.array(), b.limit(), 1000);
					if (data < 12) return new PtpResult(data);
					size = b.getInt(0);
					type = b.getShort(4);
					opcode = b.getShort(6);
					trid = b.getShort(8);
					remaining = size - data;
					done = data - 12;
				}
				// we might have got a result dataset already, skip reading the input
				if (type == 2) {
					// we need to read more data
					if (mData != null && mData.length >= size) {
						inData = mData;
					} else {
						inData = new byte[size - 12];
						mData = null;
					}
					System.arraycopy(b.array(), 12, inData, 0, data - 12);
					while (remaining > 0) {
						b = basebuffer;
						b.rewind();
						b.order(ByteOrder.LITTLE_ENDIAN);
						data = mDevice.bulkTransferIn(b.array(), b.limit(), 1000);
						if (data < 0) return new PtpResult(data);
						System.arraycopy(b.array(), 0, inData, done, data);
						remaining -= data;
						done += data;
					}
					if (mData!=null) inData = null;
				}
			}

			if (type != 3) {
				b = basebuffer;
				b.rewind();
				b.order(ByteOrder.LITTLE_ENDIAN);
				type = 0;
				opcode = 0;
				trid = 0;
				while (type != 3 || trid != mTrId) {
					data = mDevice.bulkTransferIn(b.array(), b.limit(), 1000);
					if (data < 12) return new PtpResult(data);
					size = b.getInt(0);
					type = b.getShort(4);
					opcode = b.getShort(6);
					trid = b.getInt(8);
				}
			}
			outParam = new int[(size - 12) / 4];
			for (int i = 0; i < outParam.length; i++) {
				outParam[i] = b.getInt(12 + i * 4);
			}
			if (inData != null) {
				return new PtpResult(opcode, outParam, inData);
			} else {
				return new PtpResult(opcode, outParam, mData, done);
			}
		}
		} catch (Exception e) {
			StackTraceElement[] s = e.getStackTrace();
			for(StackTraceElement se : s)
				Log.e("USB",se.toString());
			
			return new PtpResult(-0xdead);
		}
		
	}
}
