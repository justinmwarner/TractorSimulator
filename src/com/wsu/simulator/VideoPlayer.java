package com.wsu.simulator;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

public class VideoPlayer  extends VideoView {
	private int width;
	private int height;

	public VideoPlayer (Context c)
	{
		super(c);
	}
	
	public VideoPlayer (Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
	}
	
	public VideoPlayer (Context context, int width, int height) {
		super(context);
		this.width = width;
		this.height = height;
	}

	public VideoPlayer(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(width, height);
	}
}
