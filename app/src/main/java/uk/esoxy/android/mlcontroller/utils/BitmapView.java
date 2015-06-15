package uk.esoxy.android.mlcontroller.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class BitmapView extends View {

	private Bitmap activeBitmap;
	
	public BitmapView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		activeBitmap = null;
	}

	public BitmapView(Context context, AttributeSet attrs) {
		super(context, attrs);
		activeBitmap = null;
	}
	
	public BitmapView(Context context) {
		super(context);
		activeBitmap = null;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawARGB(255,0,0,0);
		if (activeBitmap != null) {
			float ratio = (float)activeBitmap.getWidth() / (float)activeBitmap.getHeight();
			int width = getWidth();
			int height = (int) Math.floor(getWidth() / ratio);
			if (height>getHeight()) {
				width = (int)Math.floor(getHeight() * ratio);
				height = getHeight();
			}
			Rect dstRect = new Rect(0,0,width,height);
			canvas.drawBitmap(activeBitmap, null, dstRect, null);
		}
	}

	public Bitmap getActiveBitmap() {
		return activeBitmap;
	}

	public void setActiveBitmap(Bitmap activeBitmap) {
		this.activeBitmap = activeBitmap;
	}
}
