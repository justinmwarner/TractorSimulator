package com.wsu.simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.http.conn.util.InetAddressUtils;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public class MainActivity extends FragmentActivity implements OnClickListener, OnMapClickListener {
	static final LatLng HAMBURG = new LatLng(53.558, 9.927);
	static final LatLng KIEL = new LatLng(53.551, 9.993);
	protected static final String TAG = "MainActivity";
	LatLng currentLocation = new LatLng(0, 0);
	private GoogleMap map;

	private VideoView video;
	Runnable conn;
	Thread zapWait;
	ServerSocket server;
	private boolean isManual = false, isFailure = false, isOkayToZap = false, isVideoPlaying = true;
	private int lastCommand = 0;
	private Button bFire, bStopFire, bManual, bAuto, bZoomIn, bZoomOut;
	private ImageButton bLeft, bRight, bForward, bBackward, bScreenshot;
	private Handler guiHandler = new Handler();
	private VideoOverlay overlay;
	private Context poiContext;
	private ArrayList<Marker> markers = new ArrayList<Marker>();
	Thread progressBarThread = null;
	boolean isDialogShown = true, isProgressBar = true;;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		setupPCToDeviceCommunication();
		if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext()) != ConnectionResult.SUCCESS) {
			Toast.makeText(this, "Your Device Does Not Support This App", Toast.LENGTH_LONG).show();
		} else {
			promptUserIp();
			poiContext = this.getApplicationContext();
			bFire = (Button) findViewById(R.id.bZap);
			bLeft = (ImageButton) findViewById(R.id.bLeft);
			bRight = (ImageButton) findViewById(R.id.bRight);
			bForward = (ImageButton) findViewById(R.id.bForward);
			bBackward = (ImageButton) findViewById(R.id.bBackward);
			bScreenshot = (ImageButton) findViewById(R.id.bScreenshot);
			bStopFire = (Button) findViewById(R.id.bStop);
			bManual = (Button) findViewById(R.id.bManual);
			bAuto = (Button) findViewById(R.id.bAuto);
			bZoomIn = (Button) findViewById(R.id.bZoomIn);
			bZoomOut = (Button) findViewById(R.id.bZoomOut);
			bFire.setOnClickListener(this);
			bLeft.setOnClickListener(this);
			bRight.setOnClickListener(this);
			bForward.setOnClickListener(this);
			bBackward.setOnClickListener(this);
			bStopFire.setOnClickListener(this);
			bManual.setOnClickListener(this);
			bAuto.setOnClickListener(this);
			bZoomIn.setOnClickListener(this);
			bZoomOut.setOnClickListener(this);
			bScreenshot.setOnClickListener(this);
			bStopFire.getBackground().setColorFilter(0xFFFF0000, PorterDuff.Mode.MULTIPLY);
			video = (VideoView) findViewById(R.id.vvTop);
			video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.grassloop));
			video.start();
			video.setOnPreparedListener(new OnPreparedListener() {
				@Override
				public void onPrepared(MediaPlayer mp) {
					mp.setLooping(true);
				}
			});

			map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
			LocationManager locManager;
			locManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 500.0f, locationListener);
			Location location = locManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			if (location != null) {
				double latitude = location.getLatitude();
				double longitude = location.getLongitude();
				currentLocation = new LatLng(latitude, longitude);
				updateWithNewLocation(location);
			}
			map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
			map.setOnMapClickListener(this);
			Button bUndo = (Button) findViewById(R.id.bUndo);
			bUndo.setOnClickListener(this);
			Button bPOI = (Button) findViewById(R.id.bPOI);
			bPOI.setOnClickListener(this);

			overlay = (VideoOverlay) findViewById(R.id.svDraw);
			new Thread(new Runnable() {

				@Override
				public void run() {
					while (true) {

						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						guiHandler.post(new Runnable() {

							@Override
							public void run() {
								overlay.postInvalidate();
							}
						});
					}
				}

			}).start();
		}
	}

	private void setupPCToDeviceCommunication() {
		conn = new Runnable() {

			@Override
			public void run() {
				try {
					server = new ServerSocket(1337);
					Log.d(TAG, "Waiting for connections...");
					while (true) {
						Socket socket = server.accept();
						Log.d(TAG, "Connected! Yay");
						BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
						String command = "";
						while (socket.isConnected()) {
							while ((command = in.readLine()) != null) {
								Log.d(TAG, "SERVER: " + command);
								final String[] split = command.split(" ");
								lastCommand = Integer.parseInt(split[0]);
								if (split[0].equals("4") || split[0].equals("5")) {
								} else {
									// Check if the zap should be a failure.
									if (split[2].equals("1")) {
										isFailure = true;
									} else {
										isFailure = false;
									}

									// Update the CI and color.
									guiHandler.post(new Runnable() {
										@Override
										public void run() {
											overlay.setCI(Integer.parseInt(split[1]));
										}
									});
								}

								// What should be played.
								if (split[0].equals("1")) {
									// Play grass.
									guiHandler.post(new Runnable() {
										@Override
										public void run() {
											gotoGrassLoop();
										}
									});
								} else if (split[0].equals("2")) {
									// Play weed.
									guiHandler.post(new Runnable() {
										@Override
										public void run() {
											video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.weedarrive));
											video.setOnPreparedListener(new OnPreparedListener() {
												@Override
												public void onPrepared(MediaPlayer mp) {
													mp.setLooping(false);
												}
											});
											video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

												@Override
												public void onCompletion(MediaPlayer mp) {
													mp.stop();
													Bitmap temp = BitmapFactory.decodeResource(getResources(), R.raw.weed);
													overlay.setPicture(temp);
													Log.d(TAG, "User is now at the picture");
												}
											});
										}
									});
								} else if (split[0].equals("3")) {
									// Play distraction.
									guiHandler.post(new Runnable() {
										@Override
										public void run() {
											video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.distractarrive));
											video.setOnPreparedListener(new OnPreparedListener() {
												@Override
												public void onPrepared(MediaPlayer mp) {
													mp.setLooping(false);
												}
											});
											video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

												@Override
												public void onCompletion(MediaPlayer mp) {
													mp.stop();
													Bitmap temp = BitmapFactory.decodeResource(getResources(), R.raw.distract);
													overlay.setPicture(temp);
													Log.d(TAG, "User is now at the picture");
												}
											});
										}
									});
								} else if (split[0].equals("4")) {
									isOkayToZap = true;
									// Zap received.
									guiHandler.post(new Runnable() {
										@Override
										public void run() {
											if (overlay.getCI() < 33) {
												gotoGrassLoop();
											} else {
												startZap();
											}
										}

									});

								} else if (split[0].equals("5")) {
									// A toggle.
									guiHandler.post(new Runnable() {
										@Override
										public void run() {
											if (video.isPlaying()) {
												video.pause();
												Log.d(TAG, "Pausing");
												isVideoPlaying = false;
											} else {
												video.resume();
												Log.d(TAG, "Resuming");
												isVideoPlaying = true;
											}
										}
									});
								} else if (split[0].equals("1337")) {
									// Close and quit.
									in.close();
									socket.close();
									Log.d(TAG, "Server closing connections. 1337 was submitted.");
									return;
								} else {
									// Unknown command.
									Log.d(TAG, "Unknown command, ignoring received data.");
								}
							}
						}
						Log.d(TAG, "Closing at spot two... Hopefully this doesn't happen...");
						in.close();
						socket.close();
					}
				} catch (Exception e) {
					Log.d(TAG, "Error 2012: " + e.toString());
				}
			}

		};

		new Thread(conn).start();
	}

	private void gotoGrassLoop() {
		overlay.setPicture(null);
		overlay.setCI(0);
		overlay.setProgress(0);
		if (lastCommand == 2) {
			video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.weeddepart));
		} else if (lastCommand == 3) {
			video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.distractdepart));
		} else {
			video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.grassloop));
		}
		video.start();
		video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

			@Override
			public void onCompletion(MediaPlayer mp) {
				video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.grassloop));
				video.start();

				video.setOnPreparedListener(new OnPreparedListener() {
					@Override
					public void onPrepared(MediaPlayer mp) {
						mp.setLooping(true);
					}
				});
			}
		});
	}

	private void stopZap() {
		Log.d(TAG, "In stopZap()");
		isProgressBar = false;
		if (lastCommand == 2) {
			Bitmap temp = BitmapFactory.decodeResource(getResources(), R.raw.weed);
			overlay.setPicture(temp);
		} else if (lastCommand == 3) {
			Bitmap temp = BitmapFactory.decodeResource(getResources(), R.raw.distract);
			overlay.setPicture(temp);
		}
		promptStopZap();
	}

	private void startZap() {
		if (!isManual) {
			if (overlay.getCI() > 66) {
				startProgressBar(20);
			}
			// Check if they are sure they want to zap first if yellow.
			else if (overlay.getCI() < 66 && overlay.getCI() >= 33) {
				promptToZap();
				return;
			} else {
				// gotoGrassLoop();
				return;
			}
		}
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.bPOI) {
			VisibleRegion vr = map.getProjection().getVisibleRegion();
			double left = vr.latLngBounds.southwest.longitude;
			double top = vr.latLngBounds.northeast.latitude;
			double right = vr.latLngBounds.northeast.longitude;
			double bottom = vr.latLngBounds.southwest.latitude;
			markers.add(map.addMarker(new MarkerOptions().position(new LatLng((top + bottom) / 2, (right + left) / 2)).title("Placemarker")));
		} else if (v.getId() == R.id.bUndo) {
			if (markers.size() > 0) {
				markers.get(markers.size() - 1).remove();
				markers.remove(markers.size() - 1);
			}
		} else if (v.getId() == R.id.bZap) {
			Log.d(TAG, "User pressed zap.  Waiting for comfirmation from server.");
		} else if (v.getId() == R.id.bStop) {
			// Stop zapping.
			Log.d(TAG, "Stop button pushed1");
			stopZap();
			Log.d(TAG, "Stop button pushed2");
		} else if (v.getId() == R.id.bAuto) {
			ImageView iv = (ImageView) findViewById(R.id.ivAutoManual);
			iv.setImageDrawable(getResources().getDrawable(R.drawable.tractorthing));
			isManual = false;
		} else if (v.getId() == R.id.bManual) {
			ImageView iv = (ImageView) findViewById(R.id.ivAutoManual);
			iv.setImageDrawable(getResources().getDrawable(R.drawable.man));
			isManual = true;
		} else if (v.getId() == R.id.bScreenshot) {
			// Take screenshot of imageview.
			Toast.makeText(this, overlay.screenshot(), Toast.LENGTH_LONG).show();
		} else if (v.getId() == R.id.bZoomIn) {
			if (isManual) {
				// TODO: Check to make sure at image phase.
				overlay.setOffsets(10, 0, 0);
			}
		} else if (v.getId() == R.id.bZoomOut) {
			if (isManual) {
				overlay.setOffsets(10, 0, 0);
			}
		} else if (v.getId() == R.id.bLeft) {
			if (isManual) {
				overlay.setOffsets(0, 10, 0);
			}
		} else if (v.getId() == R.id.bRight) {
			if (isManual) {
				overlay.setOffsets(0, -10, 0);
			}
		} else if (v.getId() == R.id.bForward) {
			if (isManual) {
				overlay.setOffsets(0, 0, -10);
			}
		} else if (v.getId() == R.id.bBackward) {
			if (isManual) {
				overlay.setOffsets(0, 0, 10);
			}
		}

	}

	// Runs progress bar for i seconds.
	private void startProgressBar(final int i) {
		if (overlay.getCI() < 33) {
			return;
		}
		progressBarThread = new Thread(new Runnable() {

			@Override
			public void run() {
				// Wait until we get a command to start zapping from server.
				while (!isOkayToZap) {
					// Wait. Wait. Wait.
				}
				isOkayToZap = false; // Reset for next time.
				int time = 0;
				while (time < i) {
					try {
						if (!isProgressBar) {
							overlay.setProgress(0);
							time = 5000;
						}
						Thread.sleep(1000);
						time++;
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (time == (i / 2)) {
						if (lastCommand == 2) {
							Bitmap temp = BitmapFactory.decodeResource(getResources(), R.raw.weedzapped);
							overlay.setPicture(temp);
						} else if (lastCommand == 3) {
							Bitmap temp = BitmapFactory.decodeResource(getResources(), R.raw.distractzapped);
							overlay.setPicture(temp);
						}
					}
					overlay.setProgress(time);
				}
				if (isFailure) {
					guiHandler.post(new Runnable() {
						@Override
						public void run() {
							promptToRezapOnUnsuccessful();
						}
					});
				} else {
					guiHandler.post(new Runnable() {

						@Override
						public void run() {
							promptZapSuccessful();
							// Prompt for POI.
							if (lastCommand == 2) {
								video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.weeddepart));
							} else if (lastCommand == 3) {
								video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.distractdepart));
							}
							overlay.setPicture(null);
							video.start();
							video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

								@Override
								public void onCompletion(MediaPlayer mp) {
									video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.grassloop));
									video.start();
									// displayPOI();
								}
							});
							// reset lastCommand to the grass loop, meaning
							// we've
							// finished.
							lastCommand = 1;
							overlay.setProgress(0);
							overlay.setCI(0);
						}
					});
					promptStopZap();
				}
			}
		});
		progressBarThread.start();

	}

	@Override
	protected void onPause() {
		super.onPause();
		if (server != null) {
			try {
				server.close();
			} catch (IOException e) {
				e.printStackTrace();
				Log.d(TAG, e.toString());
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.mainoptions, menu);
		return true;
	}

	private void updateWithNewLocation(Location location) {
		if (location != null) {
			double lat = location.getLatitude();
			double lng = location.getLongitude();

			currentLocation = new LatLng(lat, lng);
			map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15));
		} else {
		}
	}

	private final LocationListener locationListener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
			updateWithNewLocation(location);
		}

		@Override
		public void onProviderDisabled(String provider) {
			updateWithNewLocation(null);
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	};

	private void promptZapSuccessful() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Your zap was successful! Congrats!").setCancelable(true).setPositiveButton("OK", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int id) {
				isDialogShown = false;
			}
		});
		AlertDialog alert = builder.create();
		if (!isDialogShown) {
			alert.show();
			isDialogShown = true;
		}
	}

	private void promptToRezapOnUnsuccessful() {
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					isFailure = false;
					overlay.setProgress(0);
					startProgressBar(20); // Rezap.
					Log.d(TAG, "User wants to zap again. Waiting for server to send a zap command");
					isDialogShown = false;
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					gotoGrassLoop();
					isDialogShown = false;
					break;
				}
			}
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if (!isDialogShown) {
			builder.setMessage("Your zap was unsuccessful.  Would you like to zap again?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();

			isDialogShown = true;
		}
	}

	private void promptUserIp() {
		String ip = MainActivity.getIPAddress(true);
		if (!ip.equals("")) {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Your ip for the server is: " + ip).setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					video.pause();
					isDialogShown = false;
				}
			});
			AlertDialog alert = builder.create();
			alert.show();
			isDialogShown = true;
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("Could not get IP.  Please use a valid device.  We will run the app normally, use Google to obtain IP manually and input that.").setCancelable(false).setPositiveButton("OK", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					video.pause();
					isDialogShown = false;
				}
			});
			AlertDialog alert = builder.create();
			if (!isDialogShown) {
				alert.show();
				isDialogShown = true;
			}
		}
	}

	private void promptStopZap() {
		Log.d(TAG, "Prompt Stop Zap");
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					Log.d(TAG, "Yes to stop zap");
					startProgressBar(20);
					isDialogShown = false;
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					Log.d(TAG, "No to stop zap");
					gotoGrassLoop();
					isDialogShown = false;
					break;
				}
			}
		};

		overlay.setProgress(0);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if (!isDialogShown) {
			builder.setMessage("Would you like to zap again?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();
			isDialogShown = true;
		}
	}

	private void promptToZap() {

		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					startProgressBar(20);
					isDialogShown = false;
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					gotoGrassLoop();
					isDialogShown = false;
					break;
				}
			}
		};

		overlay.setProgress(0);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if (!isDialogShown) {
			builder.setMessage("CI is yellow, would you like to zap?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();
			isDialogShown = true;
		}
	}

	public void displayPOI() {
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					VisibleRegion vr = map.getProjection().getVisibleRegion();
					double left = vr.latLngBounds.southwest.longitude;
					double top = vr.latLngBounds.northeast.latitude;
					double right = vr.latLngBounds.northeast.longitude;
					double bottom = vr.latLngBounds.southwest.latitude;
					map.addMarker(new MarkerOptions().position(new LatLng((top + bottom) / 2, (right + left) / 2)).title("Placemarker"));
					isDialogShown = false;
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					// No button clicked
					isDialogShown = false;
					break;
				}

			}
		};

		overlay.setProgress(0);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		if (!isDialogShown) {
			builder.setMessage("Set a Point of Interest on the map?").setPositiveButton("Yes", dialogClickListener).setNegativeButton("No", dialogClickListener).show();
			isDialogShown = true;
		}
	}

	@Override
	public void onMapClick(LatLng point) {
		markers.add(map.addMarker(new MarkerOptions().position(point).title("Placemarker")));
	}

	/**
	 * Get IP address from first non-localhost interface
	 * 
	 * @param ipv4
	 *            true=return ipv4, false=return ipv6
	 * @return address or empty string
	 */
	public static String getIPAddress(boolean useIPv4) {
		try {
			List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
			for (NetworkInterface intf : interfaces) {
				List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
				for (InetAddress addr : addrs) {
					if (!addr.isLoopbackAddress()) {
						String sAddr = addr.getHostAddress().toUpperCase();
						boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
						if (useIPv4) {
							if (isIPv4)
								return sAddr;
						} else {
							if (!isIPv4) {
								int delim = sAddr.indexOf('%'); // drop ip6 port
																// suffix
								return delim < 0 ? sAddr : sAddr.substring(0, delim);
							}
						}
					}
				}
			}
		} catch (Exception ex) {
		} // for now eat exceptions
		return "";
	}

}
