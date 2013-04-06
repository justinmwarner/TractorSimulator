package com.wsu.simulator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class VideoOverlay extends View {

	String TAG = "VideoOverlay";
	Bitmap currentPicture;
	int width = 0, height = 0, offsetZoom = 0, offsetHorizontal = 0, offsetVertical = 0, progress = 0;
	Paint p = new Paint();
	int ci;
	Canvas pictureCanvas;
	int translateX = 0, translateY = 0, scaleX = 0, scaleY = 0;

	public VideoOverlay(Context context) {
		super(context);
	}

	public VideoOverlay(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public VideoOverlay(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		width = MeasureSpec.getSize(widthMeasureSpec);
		height = MeasureSpec.getSize(heightMeasureSpec);
		width = width * 2;
		this.setMeasuredDimension(width / 2, height);
	}

	public void setCI(int c) {
		ci = c;
	}

	public int getCI() {
		return ci;
	}

	public void setPicture(Bitmap bm) {
		currentPicture = bm;
	}

	public void setOffsets(int zoom, int hor, int vert) {
		offsetZoom += zoom;
		offsetHorizontal += hor;
		offsetVertical = vert;
	}

	public void setProgress(int p) {
		if (p > 500) {
			progress = 0;
		} else {
			progress = p;
		}
	}

	public String screenshot() {
		if (pictureCanvas != null) {
			Bitmap toDisk = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			Canvas temp = new Canvas(toDisk);
			this.draw(temp);
			try {
				int id = 0;

				String files[] = new File("/mnt/sdcard/").list();
				for (String file : files) {
					if (file.startsWith("SIMULATOR_")) {
						int tempId = Integer.parseInt(file.substring(10, file.length() - 4));
						if (id < tempId) {
							id = tempId;
						}
					}
				}
				id++;
				toDisk.compress(Bitmap.CompressFormat.JPEG, 100, new FileOutputStream(new File("/mnt/sdcard/SIMULATOR_" + id + ".jpg")));
				return "/mnt/sdcard/SIMULATOR_" + id + ".jpg";
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		return "File Not Saved!";
	}

	@SuppressLint("DrawAllocation")
	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		int myColor = 0;
		// p.setColor(Color.TRANSPARENT);
		// canvas.drawRect(0, 0, width, height, p);
		if (currentPicture != null) {
			// p.setColor(Color.RED);
			canvas.drawBitmap(currentPicture, offsetHorizontal, offsetVertical, p);
			// canvas.translate(offsetHorizontal, offsetVertical);
			// canvas.scale(offsetZoom, offsetZoom);
			// canvas.drawRect(0, 0, width, 200, p);
			// canvas.drawBitmap(currentPicture, 0, 0, p);
			// canvas.translate(translateX, translateY);
			// canvas.scale(scaleX, scaleY);
			pictureCanvas = canvas;
			// canvas.drawRect(0, 100, width, 200, p);
			// canvas.drawRect(0, 0, width, height, p);
		}
		if (ci >= 66) {
			myColor = (Color.RED);
		} else if (ci >= 33) {
			myColor = (Color.YELLOW);
		} else {
			myColor = (Color.GREEN);
		}
		p.setColor(myColor);
		// Progress bar stuff.
		canvas.drawRect(0, height - 100, (progress * width / 40), height, p);
		// backdrop for textview.
		p.setTextSize(100);
		p.setColor(Color.BLACK);
		canvas.drawText(ci + "%", -1, height - 19, p);
		canvas.drawText(ci + "%", +1, height - 21, p);
		// Draw CI.
		p.setColor(myColor);
		canvas.drawText(ci + "%", 0, height - 20, p);
		invalidate();
	}
}
