package org.dynia.seamap.slipp;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import javax.imageio.ImageIO;

public class KapFile {

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
	
	private String title;
	
	private Double dpi;
	
	private String scale;

	List<Color> palette; 

	FileOutputStream output;
	private Long outputOffset;

	private BoundingBox boundingBox;

	private String rootPath;
	private String defFile;

	String mapID;

	File kapFile = null;

	BufferedImage image = null;


	public KapFile(String rootPath, String defFile, String title, Double dpi) throws IOException {
		this.rootPath = rootPath;
		this.defFile = defFile;
		this.dpi = dpi;
		this.title = title;

		readInDef();
		readInPalette();
	}



	private void readInDef() throws IOException {
		File f = new File(rootPath + "/" + defFile);

		BufferedReader br = new BufferedReader(new FileReader(f));

		// BoundignBox
		br.readLine();

		//13-4584-2621
		String p1Slippy = br.readLine();

		//54.239550531561775,21.4453125
		String p1Geo = br.readLine();

		//13-4595-2648
		String p2Slippy = br.readLine();

		//53.54030739150021,21.9287109375
		String p2Geo = br.readLine();

		mapID = p1Slippy + "_" +p2Slippy;

		//12312.0
		scale = br.readLine();		

		StringTokenizer stP1 = new StringTokenizer(p1Geo, ",");
		StringTokenizer stP2 = new StringTokenizer(p2Geo, ",");
		boundingBox = new BoundingBox();
		boundingBox.north = Double.valueOf(stP1.nextToken());
		boundingBox.west = Double.valueOf(stP1.nextToken());
		boundingBox.south = Double.valueOf(stP2.nextToken());
		boundingBox.east = Double.valueOf(stP2.nextToken());

		br.close();
	}

	// read palette from extenal file
	private void readInPalette() throws IOException {

		palette = new ArrayList<>();
		
		File f = new File("reference_palette.txt");
		
		System.out.println("Reading palette from: " + f.getAbsolutePath());

		BufferedReader br = new BufferedReader(new FileReader(f));

		String line;
		while ((line = br.readLine())!=null) {
			if (line.startsWith("RGB/")){
				line = line.substring(4);
				StringTokenizer st = new StringTokenizer(line,",");
				st.nextToken();
				int r = Integer.valueOf(st.nextToken());
				int g = Integer.valueOf(st.nextToken());
				int b = Integer.valueOf(st.nextToken());

				Color c = new Color(r, g, b);
				palette.add(c);				
			}
		}

		br.close();
	}

	// read image 
	public void parseInputSlippy() throws IOException {

		String pngFile = defFile.substring(0, defFile.length()-4);
		File f = new File(rootPath + pngFile + ".png");		

		System.out.println("Reading map from ["+f.getAbsolutePath()+"]");
		image = ImageIO.read(f);

		System.out.println("Image ["+image.getWidth()+"x"+image.getHeight()+"] ");

	}


	public void dump(String homeOut) throws IOException {
		kapFile = new File(homeOut + "/"+title+"_L" + mapID + ".kap"); 

		kapFile.getParentFile().mkdirs();

		output = new FileOutputStream(kapFile);
		outputOffset = 0L;

		System.out.println("* Write header");
		dumpHeader();

		System.out.println("* Compress image and write");
		ImageCompress compress = new ImageCompress(image, palette, output);
		compress.comressImage(outputOffset);

		output.flush();
		output.close();
	
		System.out.println("* Done.");
	}


	private void dumpHeader() throws IOException {
		Date now = new Date();
		SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd");
		String dateString = format.format(now);
		
		
		write("! - KAP file (see details in http://opencpn.org/ocpn/kap_format) \r\n".getBytes());

		// version
		writeHeaderLine( "VER", "3.0");

		// copyright
		writeHeaderLine( "CRR", "map.openseamap.org");

		// CHT - General parameters
		//  - NA - Chart name given to the BSB chart (can represent more than one .KAP)
		//  - NU - Chart number.
		writeHeaderLine( "CHT", "NA="+ title +",NU="+ mapID + "");

		// CHF Chart format (e.g. Overview, General, Coastal, Approach, River, Harbour or Berthing)
		writeHeaderLine( "CHF", "Overview");

		
		
		//CED - Chart edition parameters - optional
		//	SE - Source edition / number of paper chart
		//	RE - Raster edition / number
		//	ED - Chart edition date/number
		writeHeaderLine( "CED", "SE=1,RE=2,ED="+dateString);

		//BSB (or NOS for older GEO/NOS or GEO/NO1 files) - General parameters
		//	NA - Pane name
		//	NU - Pane number. If chart is 123 and contains a plan A, the plan should be numbered 123_A
		//	RA - width, height - width and height of raster image data in pixels
		//	DU - Drawing Units in pixels/inch (same as DPI resolution) e.g. 50, 150, 175, 254, 300
		writeHeaderLine( "BSB", "NA="+title + ",NU="+mapID + ",RA="+image.getWidth()+","+image.getHeight()+",DU="+dpi);

		//ORG - Producing agency identifier - OpenSeaMap for us
		writeHeaderLine( "ORG", "OpenSeaMap");

		//MFR - Manufacturer of the RNC chart - OpenSeaMap for us
		writeHeaderLine( "MFR", "OpenSeaMap");

		// KNP - Detailed chart parameters
		//	 	SC - Scale e.g. 25000 means 1:25000
		//	 	GD - Geodetic Datum e.g. WGS84 for us
		//	 	PR - Projection e.g. MERCATOR for us. Other known values are TRANSVERSE MERCATOR or LAMBERT CONFORMAL CONIC or POLYCONIC. This must be one of those listed, as the value determines how PP etc. are interpreted. Only MERCATOR and TRANSVERSE MERCATOR are supported by OpenCPN.
		//	 	PP - Projection parameter. For Mercator charts this is where the scale is valid, i.e. +lat_ts - use average latitude of the chart. For transverse Mercator it is the +lon_0 value.
		//	 	PI - Projection interval ? e.g. 0.0, 0.033333, 0.083333, 2.0
		//	 	SP -? - Unknown is valid
		//	 	SK - Skew angle e.g. 0.0 for us. Angle of rotation of the chart
		//	 	TA - text angle e.g. 90 for us
		//	 	UN - Depth units (for depths and heights) e.g. METRES, FATHOMS, FEET
		//	 	SD - Sounding Datum e.g. MEAN LOWER LOW WATER, HHWLT or normally LAT
		//	 	DX - X resolution, distance (meters) covered by one pixel in X direction. OpenCPN ignores this and DY
		//	 	DY - Y resolution, distance covered by one pixel in Y direction
		
			
		writeHeaderLine( "KNP", "PR=MERCATOR,GD=WGS84,SC="+scale+",SD=LAT,UN=METRES,SK=0.0,TA=90.0,PI=UNKNOWN,SP=UNKNOWN,PP=3.9713005970690602");

		//REF - Reference point record, gives point n, then position x, y in pixels, then in longitude, latitude - optional
		writeHeaderLine("REF", "1,0,0,"+boundingBox.north+","+boundingBox.west);
		writeHeaderLine("REF", "2,"+(image.getWidth()-1)+",0,"+boundingBox.north+","+boundingBox.east);
		writeHeaderLine("REF", "3,"+(image.getWidth()-1)+","+(image.getHeight()-1)+","+boundingBox.south+","+boundingBox.east);
		writeHeaderLine("REF", "4,0,"+(image.getHeight()-1)+","+boundingBox.south+","+boundingBox.west);

		// PLY - Border polygon record - coordinates of the panel within the raster image, given in chart datum lat/long. Any shape polygon
		writeHeaderLine("PLY", "1,"+boundingBox.north+","+boundingBox.west);
		writeHeaderLine("PLY", "2,"+boundingBox.north+","+boundingBox.east);
		writeHeaderLine("PLY", "3,"+boundingBox.south+","+boundingBox.east);
		writeHeaderLine("PLY", "4,"+boundingBox.south+","+boundingBox.west);

		// DTM - Datum shift parameters - Datum's northing and easting in floating point seconds to go between chart datum and WGS84 (omitted or 0,0 for WGS84 charts)
		writeHeaderLine("DTM", "0.0,0.0");

		// CPH - Phase shift value - optional
		writeHeaderLine("CPH", "0.0");

		// OST - Offset STrip image lines (number of image rows per entry in the index table) e.g. 1. Generated by imgkap.
		writeHeaderLine("OST", "1");

		// IFM - Depth of the colormap (bits per pixel). BSB supports 1 through 7 (2 through 127 max colors). Or compression type? Generated by imgkap.
		writeHeaderLine("IFM", "7");

		//RGB - Default colour palette - entries in the raster colormap of the form index,red,green,blue (index 0 is not used in BSB). Generated by imgkap.
		dumpRgbHeader();		

		// text header is terminated with a <Control-Z><NUL> sequence (ASCII characters 26 and 0)
		write(26);
		write(0);		

	}

	private void dumpRgbHeader() throws IOException {
		int idx = 1;
		for (Color c: palette) {
			//RGB - Default colour palette - entries in the raster colormap of the form index,red,green,blue (index 0 is not used in BSB). Generated by imgkap.
			writeHeaderLine("RGB", idx+","+c.getRed()+","+c.getGreen()+","+c.getBlue());
			idx++;
		}

	}

	private void writeHeaderLine(String param, String value) throws IOException {
		write(param.getBytes());
		write("/".getBytes());
		write(value.getBytes());
		write("\r\n".getBytes());
	}


	private void write(int b) throws IOException {		
		output.write(b);
		outputOffset++;
	}

	private void write(byte[] bytes) throws IOException {		
		output.write(bytes);
		outputOffset+= bytes.length;
	}



/*
	private void checkKapFileAndWriteIndex() throws IOException
	{
		List<Long> indexPos = new ArrayList<Long>();

		FileInputStream fileInputStream = null;
		byte[] bFile = new byte[(int) kapFile.length()];

		//convert file into array of bytes
		fileInputStream = new FileInputStream(kapFile);
		fileInputStream.read(bFile);
		fileInputStream.close();

		

		System.out.println("BINARY AT ["+i+", 0x"+Integer.toHexString(i)+"]");

		indexPos.add((long)0);
		indexPos.add((long)i);
		int line = 0;
		while (line < image.getHeight()) {
			System.out.println("LINE: "+line + "/"+image.getHeight()+" @ pos ["+i+", 0x"+Integer.toHexString(i)+"]");			
			i = readLine(bFile, i);
			indexPos.add((long)i);
			line++;
		}


		//WRITE INDEX		
		FileOutputStream fos = new FileOutputStream(kapFile, true);

		System.out.println("ROW INDEX AT ["+kapFile.length()+", 0x"+Long.toHexString(kapFile.length())+"]");
		for (Long l: indexPos) {
			byte a = (byte)((l>>24) & 0xFF);
			byte b = (byte)((l>>16) & 0xFF);
			byte c = (byte)((l>>8) & 0xFF);
			byte d = (byte)((l) & 0xFF);

			fos.write(a);
			fos.write(b);
			fos.write(c);
			fos.write(d);
		}
		System.out.println("==END INDEX==");
		fos.flush();
		fos.close();

		return;
	}
*/


	public void verify() throws IOException {
		

		System.out.println("Verification report");
		
		FileInputStream fileInputStream = null;
		byte[] bFile = new byte[(int) kapFile.length()];

		//convert file into array of bytes
		fileInputStream = new FileInputStream(kapFile);
		fileInputStream.read(bFile);
		fileInputStream.close();
		
		System.out.println("File size ["+kapFile.length()+"]");
		
		Integer offset = 0;
		
		// FIND START OF BINARY SECTION (last bytes of header section are "26" and "0")		
		for (; offset < bFile.length; offset++)
		{				
			if ((byte) bFile[offset] == 26 && (byte) bFile[offset+1] == 0) break;
		}
		offset += 2;
		
		System.out.println("\tPosition ["+offset+", 0x"+Long.toHexString(offset)+"] (start)");
		
	}


	
}
