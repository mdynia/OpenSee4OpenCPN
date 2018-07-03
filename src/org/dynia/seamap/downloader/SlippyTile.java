package org.dynia.seamap.downloader;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import javax.imageio.ImageIO;

public class SlippyTile {	

	private String rootPath;

	private int slippyX,slippyY,slippyZoom;

	class BoundingBox {
		double north;
		double south;
		double east;
		double west;   
		@Override
		public String toString() {		
			return "Bounding Box: [north: "+north+", south: " + south +", west: " + west + ", east: " + east + "]";
		}
	}

	private BoundingBox boundingBox;

	/**
	 * 
	 * @param type Map type (0 for base, 1 for sea marks)
	 * @param x
	 * @param y
	 * @param zoom
	 */
	public SlippyTile(String rootPath, int x, int y, int zoom) {
		this.slippyX = x;
		this.slippyY = y;
		this.slippyZoom = zoom;
		this.rootPath = rootPath;

		this.boundingBox = tile2boundingBox(x, y, zoom);
	}

	public int getX() {
		return slippyX;
	}

	public int getY() {
		return slippyY;
	}

	public int getZoom() {
		return slippyZoom;
	}


	public String getKey() {
		return getKey(slippyZoom, slippyX, slippyY);	
	}
	
	public static String getKey(int zoom, int x, int y) {
		return zoom+"-"+x+"-"+y;	
	}
	
	
	public BoundingBox getBoundingBox() {
		return boundingBox;
	}

	// check if the file has been already downloaded
	public boolean isDownloaded() {
		File f1 = new File(rootPath +  getPathFragmentBasic());
		File f2 = new File(rootPath +  getPathFragmentSeaMark());
		return f1.exists() && f2.exists();
	}

	private String getPathFragmentBasic() {
		return "BASIC"+getPathFragment();
	}

	private String getPathFragmentSeaMark() {
		return "SEA"+getPathFragment();
	}

	private String getPathFragmentFlatt() {
		return "FLAT"+getPathFragment();
	}

	private String getPathFragment() {
		StringBuffer sb = new StringBuffer();
		sb.append("/");
		sb.append(slippyZoom);
		sb.append("/");
		sb.append(slippyX);
		sb.append("/");
		sb.append(slippyY);
		sb.append(".png");

		return sb.toString();
	}

	private String getURLSeaMark() {
		return "http://t1.openseamap.org/seamark/" + getPathFragment();
	}

	private String getURLBasic() {
		return "http://tile.openstreetmap.org/" + getPathFragment();
	}

	public void download() throws IOException {

		if (!isDownloaded()) {
			System.out.println("Downloading ["+getPathFragmentBasic()+"]");
			// download basic
			URL website = new URL(getURLBasic());
			ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			File f = new File(rootPath + getPathFragmentBasic());
			f.getParentFile().mkdirs();
			FileOutputStream fos = new FileOutputStream(f);
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			fos.close();

			// download overlay
			website = new URL(getURLSeaMark());
			rbc = Channels.newChannel(website.openStream());
			f = new File(rootPath + getPathFragmentSeaMark());
			f.getParentFile().mkdirs();
			fos = new FileOutputStream(f);
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			fos.close();
		} else {
			System.out.println("Skipping download for ["+getPathFragmentBasic()+"] (already downloaded)");
		}
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("[x=").append(slippyX);
		sb.append(", y=").append(slippyY);
		sb.append(", zoom=").append(slippyZoom);
		sb.append("] (").append(boundingBox.north);
		sb.append(",").append(boundingBox.west);
		sb.append(")");
		return sb.toString();
	}

	public void flatten() throws IOException {

		File flattenFile = new File(rootPath + getPathFragmentFlatt());
		if (!flattenFile.exists()) {
			System.out.println("Flat file: " + this.toString());	

			File f1 = new File(rootPath + getPathFragmentBasic());
			File f2 = new File(rootPath + getPathFragmentSeaMark());


			BufferedImage image = ImageIO.read(f1);
			BufferedImage overlay = ImageIO.read(f2);

			// create the new image, canvas size is the max. of both image sizes
			int w = Math.max(image.getWidth(), overlay.getWidth());
			int h = Math.max(image.getHeight(), overlay.getHeight());
			BufferedImage combined = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

			// paint both images, preserving the alpha channels
			Graphics g = combined.getGraphics();
			g.drawImage(image, 0, 0, null);
			g.drawImage(overlay, 0, 0, null);

			// Save as new image

			flattenFile.getParentFile().mkdirs();
			ImageIO.write(combined, "PNG", flattenFile);
		}
	}

	public BoundingBox tile2boundingBox(final int x, final int y, final int zoom) {
		BoundingBox bb = new BoundingBox();
		bb.north = tile2lat(y, zoom);
		bb.south = tile2lat(y + 1, zoom);
		bb.west = tile2lon(x, zoom);
		bb.east = tile2lon(x + 1, zoom);
		return bb;
	}

	static private double tile2lon(int x, int z) {
		return x / Math.pow(2.0, z) * 360.0 - 180;
	}

	static private double tile2lat(int y, int z) {
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}

	public void draw(Graphics g, int offsetX, int offsetY) throws IOException {
		File f1 = new File(rootPath + getPathFragmentFlatt());
		BufferedImage image = ImageIO.read(f1);
		g.drawImage(image, offsetX, offsetY, null);		
	}





}
