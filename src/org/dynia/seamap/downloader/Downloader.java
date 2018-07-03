package org.dynia.seamap.downloader;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.dynia.seamap.slipp.KapFile;

public class Downloader {

	private String title;
	private int zoom;
	private int x1, y1;
	private int x2, y2;
	
	private Double screen_dpi;

	List<SlippyTile> tiles = null;

	Map<String, SlippyTile> tilesMap;

	List<Color> palette; 


	public Downloader() {		
		tiles = new ArrayList<SlippyTile>();
		tilesMap = new HashMap<String, SlippyTile>();
	}
	
	
	/**
	 * Initialize all input parameters from configuration file 
	 * 
	 * @param propFileName
	 * @throws IOException
	 */
	public void init(String propFileName) throws IOException {
		
		Properties prop = loadProperties(propFileName);
		
		this.title = prop.getProperty("title").trim();
				
		this.zoom = Integer.parseInt(prop.getProperty("zoom"));

		Double north = Double.parseDouble(prop.getProperty("north"));
		Double west = Double.parseDouble(prop.getProperty("west"));
		Double south = Double.parseDouble(prop.getProperty("south"));
		Double east = Double.parseDouble(prop.getProperty("east"));
		
		screen_dpi = Double.parseDouble(prop.getProperty("dpi"));
		
		
		// north west
		this.x1 = getXTileNumber(north, west, zoom);
		this.y1 = getYTileNumber(north, west, zoom);

		// south east
		this.x2 = getXTileNumber(south, east, zoom);
		this.y2 = getYTileNumber(south, east, zoom);
	}

	

	public void downloadLayers() throws IOException {		
		// root folder for downloads
		String downloadFolder = title + "/src/";
		
		for (int x=x1; x<=x2; x++) {
			for (int y=y1; y<=y2; y++) {				
				SlippyTile tile = new SlippyTile(downloadFolder, x, y, zoom);
				tiles.add(tile);
				tilesMap.put(tile.getKey(), tile);			
				tile.download();
			}
		}		
	}

	public void flattenLayers() throws IOException {
		for (SlippyTile tile : tiles) {
			if (tile.isDownloaded()) {
				tile.flatten();
			} else {
				System.out.println("MISSING: " + tile.toString());
			}
		}
	}



	private List<String> mergeAll() throws IOException {
			
		return merge(x1, y1, x2, y2);
		
		/*
		int maxTilesMergedPerImage = 8;

		int chunk = Math.min(maxTilesMergedPerImage, (Math.min((x2-x1),(y2-y1))));

		int numberOfChunksX = 1 + (x2-x1) / chunk;
		int numberOfChunksY = 1 + (y2-y1) / chunk;

		for (int offsY = 0; offsY < numberOfChunksY; offsY ++ ) {
			for (int offsX = 0; offsX < numberOfChunksX; offsX ++ ) {
				int a1 = Math.min(x2, x1+offsX*chunk);
				int b1 = Math.min(y2, y1+offsY*chunk);
				int a2 = Math.min(x2, a1+chunk);
				int b2 = Math.min(y2, b1+chunk);				
						
				// merge selected area into single file
				merge(a1, b1, a2, b2);
			}
		}
		*/
		
	}

	/**
	 * Merge all tiles into bigger image files
	 * 
	 * @param a1 left
	 * @param b1 top
	 * @param a2 right
	 * @param b2 bottom
	 * @throws IOException
	 */
	private List<String> merge(int a1, int b1, int a2, int b2) throws IOException {
		
		List<String> resMaps = new ArrayList<>();
		int mergedWidth = 256*(1+a2-a1);
		int mergedHeight = 256*(1+b2-b1);

		BufferedImage combined = new BufferedImage(mergedWidth, mergedHeight, BufferedImage.TYPE_INT_ARGB);		
		Graphics g = combined.getGraphics();		

		for (int b=b1; b<=b2; b++) {
			for (int a=a1; a<=a2; a++) {
				String key = SlippyTile.getKey(zoom, a, b);
				SlippyTile tile = tilesMap.get(key);
				if (tile.isDownloaded()) {
					tile.draw(g, (tile.getX()-a1)*256, (tile.getY()-b1)*256);
				} else {
					System.out.println("MISSING: " + tile.toString());
				}
			}
		}

		String mapName = "combined_"+zoom+"_"+a1+"_"+b1;
		resMaps.add(mapName);
		
		String outputDir = title+"/src/COMBINED/";
		
		// Save as new image
		File mergedFile = new File(outputDir + mapName +".png");
		mergedFile.getParentFile().mkdirs();
		ImageIO.write(combined, "PNG", mergedFile);

		//save bounding Boxfile
		FileOutputStream fos = new FileOutputStream(new File(outputDir + mapName+".txt"));		
		fos.write("BoundignBox".getBytes());		

		String keyFirst = SlippyTile.getKey(zoom, a1, b1);
		String keyLast = SlippyTile.getKey(zoom, a2, b2);
		SlippyTile firstTile = tilesMap.get(keyFirst);		
		SlippyTile lastTile = tilesMap.get(keyLast);

		//double scale = 4200.0;
		
		double resolution = 156543.03 * Math.cos(Math.toRadians(firstTile.getBoundingBox().north)) / Math.pow(2 , zoom);
		System.out.println("Resolution: " + resolution);
		System.out.println("Screen DPI: " + screen_dpi);
		double scale = Math.round(screen_dpi * 39.37 * resolution);
		System.out.println("Scale: " + scale);
		
		fos.write(("\n"+keyFirst+"").getBytes());
		fos.write(("\n"+firstTile.getBoundingBox().north+","+firstTile.getBoundingBox().west+"").getBytes());
		fos.write(("\n"+keyLast+"").getBytes());
		fos.write(("\n"+lastTile.getBoundingBox().south+","+lastTile.getBoundingBox().east+"").getBytes());
		fos.write(("\n"+scale+"").getBytes());
		fos.close();

		return resMaps;

	}

	public static int getXTileNumber(final double lat, final double lon, final int zoom) {
		int xtile = (int)Math.floor( (lon + 180) / 360 * (1<<zoom) ) ;
		int ytile = (int)Math.floor( (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1<<zoom) ) ;
		if (xtile < 0)
			xtile=0;
		if (xtile >= (1<<zoom))
			xtile=((1<<zoom)-1);
		if (ytile < 0)
			ytile=0;
		if (ytile >= (1<<zoom))
			ytile=((1<<zoom)-1);
		return xtile;
	}

	public static int getYTileNumber(final double lat, final double lon, final int zoom) {
		int xtile = (int)Math.floor( (lon + 180) / 360 * (1<<zoom) ) ;
		int ytile = (int)Math.floor( (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1<<zoom) ) ;
		if (xtile < 0)
			xtile=0;
		if (xtile >= (1<<zoom))
			xtile=((1<<zoom)-1);
		if (ytile < 0)
			ytile=0;
		if (ytile >= (1<<zoom))
			ytile=((1<<zoom)-1);
		return ytile;
	}



	public void saveKapFile() throws IOException {
		
		List<String> fileNames = mergeAll();

		System.out.println("-- START (processing kap) --");
		for (String mapName: fileNames) {			
			System.out.println("- Processing ["+mapName+"] -");			
			
			String definitionFile = "/src/COMBINED/"+mapName+".txt";
			KapFile kap = new KapFile(title, definitionFile, title, screen_dpi);		
			
			kap.parseInputSlippy();
			
			String outputDirectory = title;
			kap.dump(outputDirectory);

			kap.verify();
		}
		
		System.out.println("-- END (processing kap) --");
		
	}



	private Properties loadProperties(String propFileName) throws IOException {
		Properties prop = new Properties();
		InputStream inputStream;

		File f = new File(propFileName);
		
		System.out.println("Loading configuration from: " + f.getAbsolutePath());
				
		inputStream = new FileInputStream(f);
		
		
		prop.load(inputStream);
		
		return prop;	
	}



}
